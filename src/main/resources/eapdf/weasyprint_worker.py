#!/usr/bin/env python3
"""Pinned, fail-closed Linux WeasyPrint worker. Invoked only by WeasyPrintRenderer.

The manifest and staged assets are trusted creator output. Email HTML, CSS, SVG and
image bytes remain untrusted. No system URL fetcher is ever called.
"""
import base64
import ctypes
import ctypes.util
import errno
import json
import logging
import math
import os
from pathlib import Path
import platform
import resource
import signal
import sys
import sysconfig
import time
import traceback
from urllib.parse import unquote_to_bytes, urlsplit

VERSION = "70.0"
MIB = 1024 * 1024
# User-origin pagination defaults: source author declarations retain priority.
# Clone cell padding/borders on fragments so the engine reserves their space
# instead of rejecting an otherwise fitting row after its content is laid out.
PAGINATION_CSS = "#email-root td, #email-root th { box-decoration-break: clone; }"


def missing_image_svg(width, height):
    # Keep the authored canvas, but draw only a small centered pictogram.
    size = min(12, width, height)
    x, y, scale = (width - size) / 2, (height - size) / 2, size / 12
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width:g}" height="{height:g}" '
        f'viewBox="0 0 {width:g} {height:g}">'
        f'<g transform="translate({x:g} {y:g}) scale({scale:g})">'
        '<rect x="0.75" y="2" width="10.5" height="8.5" rx="0.75" fill="#f5f5f5" stroke="#888888" stroke-width="0.7"/>'
        '<circle cx="8.5" cy="4.5" r="1" fill="#888888"/>'
        '<path d="M2 9L4.5 6L7 8.5L8.5 7L10 9" fill="none" stroke="#888888" stroke-width="0.7"/>'
        '</g></svg>'
    ).encode("ascii")


MISSING_IMAGE = "data:image/svg+xml;base64," + base64.b64encode(missing_image_svg(24, 24)).decode("ascii")


def missing_image_uri(element):
    dimensions = []
    for attribute in ("width", "height"):
        try:
            value = float(element.get(attribute, "24"))
        except ValueError:
            value = 24
        if not math.isfinite(value) or value > 19200:
            raise ValueError("Missing image dimensions exceed the supported layout bounds")
        dimensions.append(max(1, value) if value > 0 else 24)
    # Preserve known intrinsic aspect ratios for common img{height:auto} rules.
    # Only bounded numeric values enter generated XML; source Alt never does.
    svg = missing_image_svg(*dimensions)
    return "data:image/svg+xml;base64," + base64.b64encode(svg).decode("ascii")


def fix_inline_style_precedence():
    """Job-local repair for the pinned engine's inline/ID specificity collision."""
    import inspect
    import weasyprint
    import weasyprint.css as css
    if weasyprint.__version__ != VERSION:
        raise RuntimeError("Inline style compatibility requires pinned WeasyPrint " + VERSION)
    original = css.find_style_attributes
    if getattr(original, "_eapdf_inline_precedence", False):
        return
    if tuple(inspect.signature(original).parameters) != ("tree", "presentational_hints", "base_url"):
        raise RuntimeError("Unsupported WeasyPrint inline style API")

    def corrected(tree, presentational_hints=False, base_url=None):
        for item in original(tree, presentational_hints, base_url):
            if len(item) != 4:
                raise RuntimeError("Unsupported WeasyPrint inline style result")
            specificity, element, declarations, url = item
            if specificity == (1, 0, 0) and element.get("style"):
                # Origin/importance and layer weights remain unchanged. Inline
                # declarations beat every selector within those same weights.
                specificity = (math.inf, 0, 0)
            elif specificity != (0, 0, 0):
                raise RuntimeError("Unsupported WeasyPrint inline style specificity")
            yield specificity, element, declarations, url

    corrected._eapdf_inline_precedence = True
    css.find_style_attributes = corrected


def replace_missing_images(root):
    """Keep missing-image link geometry and source Alt, without wrapped labels."""
    count = 0
    for element in root.iter("img"):
        if not (element.get("src") or "").strip() and (element.get("alt") or "").strip():
            # The outer element retains authored CSS/dimensions and its Alt.
            # No remote URL is fetched and no source text enters generated SVG.
            element.set("src", missing_image_uri(element))
            count += 1
    return count


def positive_integer(manifest, name, default):
    value = manifest.get(name, default)
    if type(value) is not int or value <= 0:
        raise ValueError(f"{name} must be a positive integer")
    return value


def staged_file(job, value):
    """No manifest-controlled absolute paths or symlinks, even before Landlock."""
    relative = Path(value)
    if relative.is_absolute() or ".." in relative.parts:
        raise ValueError("Staged resource must be relative to the render job")
    path = job / relative
    if any(part.is_symlink() for part in (path, *path.parents)):
        raise ValueError("Symlinks are forbidden in staged renderer resources")
    resolved = path.resolve(strict=True)
    if not resolved.is_relative_to(job) or not resolved.is_file():
        raise ValueError("Staged resource is not a regular file in the render job")
    return resolved


def sandbox(job):
    """Landlock filesystem allowlist plus seccomp; never silently run unsandboxed."""
    if sys.platform != "linux" or platform.machine() not in ("x86_64", "aarch64"):
        raise RuntimeError("WeasyPrint requires Linux x86_64/aarch64 with Landlock and libseccomp")
    # Both restrictions apply to the calling thread and subsequently created
    # threads. Refuse an environment that imported a native library which already
    # started an unconfined worker before this point.
    if len(os.listdir("/proc/self/task")) != 1:
        raise RuntimeError("WeasyPrint sandbox must start in a single-threaded process")
    libc = ctypes.CDLL(None, use_errno=True)
    abi = libc.syscall(444, 0, 0, 1)
    if abi < 3:
        raise RuntimeError("WeasyPrint requires Landlock ABI 3 or later")
    frozen = getattr(sys, "_MEIPASS", None)
    seccomp_name = str(Path(frozen) / "libseccomp.so.2") if frozen else ctypes.util.find_library("seccomp")
    if not seccomp_name:
        raise RuntimeError("WeasyPrint requires libseccomp")
    seccomp = ctypes.CDLL(seccomp_name, use_errno=True)
    # These rights cover execute, read/write, deletion, creation, rename and truncate.
    handled = (1 << 15) - 1
    if abi >= 5:
        handled |= 1 << 15  # IOCTL_DEV: deny device ioctls too.

    class Ruleset(ctypes.Structure):
        _fields_ = [("handled_access_fs", ctypes.c_uint64)]

    class PathBeneath(ctypes.Structure):
        _pack_ = 1
        _fields_ = [("allowed_access", ctypes.c_uint64), ("parent_fd", ctypes.c_int32)]

    attributes = Ruleset(handled)
    descriptor = libc.syscall(444, ctypes.byref(attributes), ctypes.sizeof(attributes), 0)
    if descriptor < 0:
        raise OSError(ctypes.get_errno(), "Cannot create Landlock ruleset")
    read = (1 << 2) | (1 << 3)
    writable = read | (1 << 1) | (1 << 4) | (1 << 5) | (1 << 7) | (1 << 8) | (1 << 13) | (1 << 14)
    # Dependencies and font fallback are allowed read-only. Home, source mail,
    # /etc/passwd, the repository, and arbitrary temporary directories are absent.
    runtime = {
        Path(path).resolve() for path in (
            sysconfig.get_path("stdlib"), sysconfig.get_path("platstdlib"),
            sysconfig.get_path("purelib"), sysconfig.get_path("platlib"),
            "/usr/lib", "/lib", "/lib64", "/etc/fonts", "/usr/share/fonts",
            "/usr/local/share/fonts", "/var/cache/fontconfig", "/dev/urandom",
        ) if path and Path(path).exists()
    }
    if frozen:
        runtime.add(Path(frozen).resolve())
        # PyInstaller lazily reads Python bytecode from this exact executable.
        runtime.add(Path(sys.executable).resolve())
    try:
        for path, rights in [(job, writable), *((path, read) for path in runtime)]:
            fd = os.open(path, os.O_PATH | os.O_CLOEXEC)
            try:
                allowed = rights if path.is_dir() else rights & ~(1 << 3)
                rule = PathBeneath(allowed, fd)
                if libc.syscall(445, descriptor, 1, ctypes.byref(rule), 0) != 0:
                    raise OSError(ctypes.get_errno(), "Cannot add Landlock resource rule")
            finally:
                os.close(fd)
        if libc.prctl(38, 1, 0, 0, 0) != 0:  # PR_SET_NO_NEW_PRIVS
            raise OSError(ctypes.get_errno(), "Cannot set no_new_privs")
        if libc.syscall(446, descriptor, 0) != 0:
            raise OSError(ctypes.get_errno(), "Cannot restrict Landlock filesystem access")
    finally:
        os.close(descriptor)
    seccomp.seccomp_init.argtypes = [ctypes.c_uint32]
    seccomp.seccomp_init.restype = ctypes.c_void_p
    seccomp.seccomp_syscall_resolve_name.argtypes = [ctypes.c_char_p]
    seccomp.seccomp_rule_add.argtypes = [ctypes.c_void_p, ctypes.c_uint32, ctypes.c_int, ctypes.c_uint]
    class Argument(ctypes.Structure):
        _fields_ = [("arg", ctypes.c_uint), ("op", ctypes.c_int),
                    ("datum_a", ctypes.c_uint64), ("datum_b", ctypes.c_uint64)]
    seccomp.seccomp_rule_add_array.argtypes = [ctypes.c_void_p, ctypes.c_uint32, ctypes.c_int,
                                              ctypes.c_uint, ctypes.POINTER(Argument)]
    seccomp.seccomp_load.argtypes = [ctypes.c_void_p]
    seccomp.seccomp_release.argtypes = [ctypes.c_void_p]
    context = seccomp.seccomp_init(0x7FFF0000)  # SCMP_ACT_ALLOW
    if not context:
        raise RuntimeError("Cannot initialize seccomp")
    try:
        for name in (
            "socket", "socketpair", "connect", "bind", "listen", "accept", "accept4",
            "sendto", "sendmsg", "sendmmsg", "recvfrom", "recvmsg", "recvmmsg",
            "execve", "execveat", "fork", "vfork", "ptrace",
            "kill", "tkill", "tgkill", "rt_sigqueueinfo", "rt_tgsigqueueinfo",
            "pidfd_send_signal", "pidfd_getfd", "process_madvise",
            "process_vm_readv", "process_vm_writev", "open_by_handle_at", "mount",
            "umount2", "pivot_root", "chroot", "unshare", "setns", "bpf",
            "io_uring_setup", "io_uring_enter", "io_uring_register",
        ):
            number = seccomp.seccomp_syscall_resolve_name(name.encode("ascii"))
            if number >= 0 and seccomp.seccomp_rule_add(context, 0x00050000 | errno.EPERM, number, 0) != 0:
                raise RuntimeError(f"Cannot install seccomp rule: {name}")
        # Pango uses a native worker thread. Process creation is still forbidden:
        # clone3 gets ENOSYS so glibc falls back to clone with inspectable flags.
        clone3 = seccomp.seccomp_syscall_resolve_name(b"clone3")
        if clone3 >= 0 and seccomp.seccomp_rule_add(context, 0x00050000 | errno.ENOSYS, clone3, 0) != 0:
            raise RuntimeError("Cannot block clone3")
        clone = seccomp.seccomp_syscall_resolve_name(b"clone")
        process_clone = Argument(0, 7, 0x00010000, 0)  # SCMP_CMP_MASKED_EQ; no CLONE_THREAD.
        if clone < 0 or seccomp.seccomp_rule_add_array(context, 0x00050000 | errno.EPERM, clone, 1, ctypes.byref(process_clone)) != 0:
            raise RuntimeError("Cannot restrict process clone")
        if seccomp.seccomp_load(context) != 0:
            raise RuntimeError("Cannot activate seccomp")
    finally:
        seccomp.seccomp_release(context)
    return {"landlockAbi": abi, "seccomp": True}


def bridge_header_tags(marker):
    """Pinned private API bridge: preserve header identity for Java retagging."""
    import pydyf
    import weasyprint.pdf.tags as tags
    original = tags._build_box_tree
    emitted = set()

    def with_header_identity(box, *args, **kwargs):
        for element in original(box, *args, **kwargs):
            title = box.element.attrib.get("title", "") if box.element is not None else ""
            if marker and title.startswith(marker) and element.get("S") in ("/TR", "/TD", "/TH"):
                element["Alt"] = pydyf.String(title)
                emitted.add(title)
            yield element

    tags._build_box_tree = with_header_identity
    return emitted


def font_css(fonts, job):
    declarations = []
    approved = {}
    for font in fonts:
        path = staged_file(job, font["file"])
        family = font["family"]
        if not family or any(character not in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_ " for character in family):
            raise ValueError("Invalid staged font family")
        weight, style = font.get("weight", 400), font.get("style", "normal")
        if weight not in (100, 200, 300, 400, 500, 600, 700, 800, 900) or style not in ("normal", "italic", "oblique"):
            raise ValueError("Invalid staged font style")
        uri = path.as_uri()
        approved[uri] = path
        declarations.append(f'@font-face {{font-family:"{family}";src:url("{uri}");font-weight:{weight};font-style:{style};}}')
    return "\n".join(declarations), approved


def resource_fetcher(assets, maximum, denied):
    from weasyprint.urls import URLFetcher, URLFetcherResponse

    class StagedFetcher(URLFetcher):
        def __init__(self):
            super().__init__(allowed_protocols=(), fail_on_errors=True)

        def fetch(self, url, headers=None):
            if url in assets:
                path = assets[url]
                if path.stat().st_size > maximum:
                    raise ValueError("Staged render resource exceeds the processing limit")
                body = path.read_bytes()
                # WeasyPrint's lazy raster path reopens file:// response URLs.
                # Always identify approved bytes with a synthetic URI so the
                # original Java asset path is never reopened outside this job.
                response_url = "eapdf-staged://resource/" + path.name
                suffix = urlsplit(url).path.lower()
                mime = "text/css" if suffix.endswith(".css") else "application/octet-stream"
            elif url.lower().startswith("data:"):
                metadata, separator, payload = url.partition(",")
                if not separator or len(payload) > maximum * 4 + 4096:
                    raise ValueError("Inline data resource exceeds the processing limit or is malformed")
                if metadata.lower().endswith(";base64"):
                    # Check the actual decoded-byte bound before constructing
                    # either the compacted input or the decoded buffer.
                    alphabet = 0
                    for character in payload:
                        if character.isspace() or character == "=":
                            continue
                        if character not in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/":
                            raise ValueError("Malformed inline base64 resource")
                        alphabet += 1
                        if alphabet * 6 // 8 > maximum:
                            raise ValueError("Inline data resource exceeds the processing limit")
                    body = base64.b64decode("".join(payload.split()), validate=True)
                else:
                    # unquote_to_bytes encodes unescaped non-ASCII text as UTF-8.
                    decoded = index = 0
                    while index < len(payload):
                        character = payload[index]
                        if character == "%" and index + 2 < len(payload) and all(
                                digit in "0123456789abcdefABCDEF" for digit in payload[index + 1:index + 3]):
                            decoded += 1
                            index += 3
                        else:
                            point = ord(character)
                            decoded += 1 if point <= 0x7f else 2 if point <= 0x7ff else 3 if point <= 0xffff else 4
                            index += 1
                        if decoded > maximum:
                            raise ValueError("Inline data resource exceeds the processing limit")
                    body = unquote_to_bytes(payload)
                if len(body) > maximum:
                    raise ValueError("Inline data resource exceeds the processing limit")
                mime = metadata[5:].split(";", 1)[0] or "text/plain"
                response_url = url
            else:
                if len(denied) < 100:
                    denied.append(url[:256])
                raise ValueError("Renderer resource is not explicitly staged")
            return URLFetcherResponse(response_url, body, {"Content-Type": mime})

    return StagedFetcher()


class ErrorLog(logging.Handler):
    def __init__(self):
        super().__init__(logging.WARNING)
        self.errors = []
        self.warnings = []

    def emit(self, record):
        message = record.getMessage()[:1024]
        target = self.errors if record.levelno >= logging.ERROR else self.warnings
        if len(target) < 100:
            target.append(message)


def right_overflow(document):
    overflow = 0
    for page in document.pages:
        limit = page.width - page._page_box.margin_right
        for box in page._page_box.descendants():
            # Page/root boxes span their margins by construction.
            if box is page._page_box or box.element_tag in (None, "html", "body"):
                continue
            right = box.position_x + box.margin_width()
            if not math.isfinite(right):
                raise ValueError("Email layout has a non-finite extent")
            overflow = max(overflow, right - limit)
    return max(0, overflow)


def fit_document(html, stylesheets, font_config, maximum_pages, fit_wide):
    from weasyprint import CSS
    document = html.render(stylesheets=stylesheets, font_config=font_config, presentational_hints=True)
    if not document.pages or len(document.pages) > maximum_pages:
        raise ValueError("WeasyPrint page count exceeds the processing limit")
    overflow = right_overflow(document)
    if fit_wide and overflow > 1:
        width = math.ceil(document.pages[0].width + overflow + 2)
        height = document.pages[0].height
        if width > 19200:
            raise ValueError("Email layout exceeds the supported page width")
        fitted = CSS(string=f"@page {{ size: {width}px {height}px !important; }}", font_config=font_config)
        document = html.render(stylesheets=[*stylesheets, fitted], font_config=font_config, presentational_hints=True)
        if not document.pages or len(document.pages) > maximum_pages:
            raise ValueError("WeasyPrint fitted page count exceeds the processing limit")
        if right_overflow(document) > 1:
            raise ValueError("Email content still overflows after fitting the page width")
    return document, overflow


def check_staged_font_styles(document, fonts):
    # WeasyPrint cannot synthesize PDF bold/italic from a lone regular face.
    # Check actually rendered text styles, not unused stylesheet declarations.
    supplied = {}
    for face in fonts:
        supplied.setdefault(face["family"].lower(), set()).add(
            (int(face.get("weight", 400)), face.get("style", "normal")))
    for page in document.pages:
        for box in page._page_box.descendants():
            if not getattr(box, "text", ""):
                continue
            family = next((name.lower() for name in box.style["font_family"]
                           if name.lower() in supplied), None)
            if family is None:
                continue
            bold = box.style["font_weight"] >= 600
            italic = box.style["font_style"] in ("italic", "oblique")
            if (bold or italic) and not any(
                    (weight >= 600) == bold and (style in ("italic", "oblique")) == italic
                    for weight, style in supplied[family]):
                raise RuntimeError(
                    "The configured font family lacks a requested bold/italic face; "
                    "install matching family faces as described in docs/WEASYPRINT.md")


def run(request):
    if not sys.flags.isolated:
        raise RuntimeError("WeasyPrint worker must be launched with Python -I")
    job = Path.cwd().resolve(strict=True)
    request = staged_file(job, request)
    manifest = json.loads(request.read_text(encoding="utf-8"))
    if manifest.get("version") != 1:
        raise ValueError("Unsupported renderer request version")
    memory = positive_integer(manifest, "memoryMiB", 512) * MIB
    maximum_output = positive_integer(manifest, "maxOutputMiB", 128) * MIB
    timeout = positive_integer(manifest, "timeoutSeconds", 120)
    maximum_pages = positive_integer(manifest, "maxPages", 10000)
    maximum_resource = min(positive_integer(manifest, "maxResourceBytes", 64 * MIB), memory // 8)
    resource.setrlimit(resource.RLIMIT_AS, (memory, memory))
    resource.setrlimit(resource.RLIMIT_FSIZE, (maximum_output, maximum_output))
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
    resource.setrlimit(resource.RLIMIT_CPU, (timeout, timeout + 1))
    signal.alarm(timeout)
    # Prevent native font/cache configuration from reaching inherited user folders.
    os.environ["HOME"] = str(job)
    os.environ["XDG_CACHE_HOME"] = str(job / "cache")
    os.environ["TMPDIR"] = str(job)
    os.environ.pop("FONTCONFIG_FILE", None)
    os.environ.pop("FONTCONFIG_PATH", None)
    frozen = getattr(sys, "_MEIPASS", None)
    if frozen:
        os.environ["FONTCONFIG_FILE"] = str(Path(frozen) / "fontconfig/fonts.conf")
    import weasyprint
    from weasyprint import CSS, HTML
    from weasyprint.text.fonts import FontConfiguration
    if weasyprint.__version__ != VERSION:
        raise RuntimeError(f"WeasyPrint {VERSION} is required; found {weasyprint.__version__}")
    fix_inline_style_precedence()
    in_file = staged_file(job, "in.html")
    assets = {uri: staged_file(job, relative) for uri, relative in manifest.get("assets", {}).items()}
    fonts, font_assets = font_css(manifest.get("fonts", []), job)
    assets.update(font_assets)
    logger = ErrorLog()
    logging.getLogger("weasyprint").addHandler(logger)
    logging.getLogger("weasyprint").setLevel(logging.WARNING)
    marker = manifest.get("headerTagMarker", "")
    emitted_header_tags = bridge_header_tags(marker)
    # Imports/version verification happen before this point; untrusted HTML parsing
    # and font processing happen only after both kernel guards are active.
    guards = sandbox(job)
    font_config = FontConfiguration()
    denied = []
    fetcher = resource_fetcher(assets, maximum_resource, denied)
    html = HTML(string=in_file.read_text(encoding="utf-8"), base_url=manifest.get("baseUri", "eapdf://message/"), url_fetcher=fetcher)
    placeholders = replace_missing_images(html.etree_element)
    # Java alone decides which source/attachment bytes belong to each profile.
    # WeasyPrint interprets rel=attachment on links and anchors as PDF embedding;
    # remove that token before layout without deleting visible anchor content.
    for element in html.etree_element.iter():
        relations = element.attrib.get("rel", "").split()
        if any(relation.lower() == "attachment" for relation in relations):
            remaining = [relation for relation in relations if relation.lower() != "attachment"]
            if remaining:
                element.attrib["rel"] = " ".join(remaining)
            else:
                element.attrib.pop("rel", None)
    expected_header_tags = {
        element.attrib["title"] for element in html.etree_element.iter()
        if marker and element.tag in ("tr", "td", "th")
        and element.attrib.get("title", "").startswith(marker)
        and "".join(element.itertext()).strip()
    }
    stylesheet = CSS(string=fonts + "\n" + PAGINATION_CSS, url_fetcher=fetcher, font_config=font_config)
    started = time.monotonic()
    document, overflow = fit_document(html, [stylesheet], font_config, maximum_pages, manifest.get("fitWideContent", True))
    # Defense in depth against metadata populated independently of rel tokens.
    document.metadata.attachments.clear()
    check_staged_font_styles(document, manifest.get("fonts", []))
    document.write_pdf(job / "out.pdf", pdf_variant="pdf/a-4f", pdf_tags=True, output_intent="srgb")
    if not expected_header_tags <= emitted_header_tags:
        raise RuntimeError("WeasyPrint failed to preserve required EA-PDF header identities")
    if logger.errors:
        detail = "; ".join(logger.errors[:4])
        if denied:
            detail += "; resource not explicitly staged: " + json.dumps(denied)
        raise RuntimeError("WeasyPrint reported rendering errors: " + detail)
    result = {
        "version": VERSION, "pages": len(document.pages), "elapsedSeconds": time.monotonic() - started,
        "peakRssKiB": resource.getrusage(resource.RUSAGE_SELF).ru_maxrss,
        "memoryLimitBytes": memory, "maxOutputBytes": maximum_output,
        "deniedResources": denied, "warnings": logger.warnings,
        "headerIdentityCount": len(emitted_header_tags),
        "missingImagePlaceholderCount": placeholders,
        "initialOverflowCssPx": overflow, "fitWideContent": manifest.get("fitWideContent", True), **guards,
        "bundledRuntime": bool(getattr(sys, "frozen", False)),
    }
    (job / "metrics.json").write_text(json.dumps(result, ensure_ascii=True) + "\n", encoding="utf-8")
    signal.alarm(0)


if __name__ == "__main__":
    try:
        if len(sys.argv) != 2:
            raise ValueError("Worker requires one staged request filename")
        if sys.argv[1] == "--runtime-info":
            import weasyprint
            if weasyprint.__version__ != VERSION:
                raise RuntimeError("Bundled renderer version mismatch")
            print(json.dumps({"weasyprint": VERSION, "python": platform.python_version(),
                              "frozen": bool(getattr(sys, "frozen", False)),
                              "platform": platform.machine(), "glibc": platform.libc_ver()[1]}))
        else:
            run(sys.argv[1])
    except BaseException as error:
        # No fallback renderer and no partially published PDF on any worker failure.
        print(f"WeasyPrint worker failed: {type(error).__name__}: {error}", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        raise SystemExit(1)
