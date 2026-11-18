"""Optional integration checks: EAPDF_WEASYPRINT_PYTHON=/path/to/venv/bin/python.

These checks exercise the real pinned runtime and kernel guards, without network
access or reading private mail. Missing optional runtime is an explicit skip.
"""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKER = ROOT / "src/main/resources/eapdf/weasyprint_worker.py"
PYTHON = os.environ.get("EAPDF_WEASYPRINT_PYTHON")


@unittest.skipUnless(PYTHON, "Optional WeasyPrint runtime is not configured")
class WeasyPrintWorkerTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="eapdf-weasy-security-")
        self.job = Path(self.temporary.name)
        self.manifest = {
            "version": 1, "memoryMiB": 512, "maxOutputMiB": 32,
            "timeoutSeconds": 15, "maxPages": 30, "fonts": [], "assets": {},
            "headerTagMarker": "EA-PDF-TEST:", "fitWideContent": True,
        }

    def tearDown(self):
        self.temporary.cleanup()

    def render(self, html, isolated=True):
        (self.job / "request.json").write_text(json.dumps(self.manifest), encoding="utf-8")
        (self.job / "in.html").write_text(html, encoding="utf-8")
        return subprocess.run(
            [PYTHON, *(["-I"] if isolated else []), str(WORKER), "request.json"],
            cwd=self.job, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=25,
        )

    def test_real_pdf_is_tagged_sandboxed_and_memory_accounted(self):
        result = self.render("<html lang='en'><title>Sandboxed render</title><p>Test café Ελληνικά</p></html>")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue((self.job / "out.pdf").read_bytes().startswith(b"%PDF-2.0"))
        metrics = json.loads((self.job / "metrics.json").read_text())
        self.assertEqual(metrics["version"], "70.0")
        self.assertGreaterEqual(metrics["landlockAbi"], 3)
        self.assertTrue(metrics["seccomp"])
        self.assertGreater(metrics["peakRssKiB"], 0)
        self.assertEqual(metrics["memoryLimitBytes"], 512 * 1024 * 1024)
        self.assertEqual(metrics["pages"], 1)

    def test_kernel_guards_block_file_reads_network_and_process_creation(self):
        # Probe direct native/Python operations as well as the renderer fetcher.
        code = '''
import ctypes,importlib.util,json,os,socket,threading
from pathlib import Path
spec=importlib.util.spec_from_file_location("worker", %r)
worker=importlib.util.module_from_spec(spec);spec.loader.exec_module(worker)
libc=ctypes.CDLL(None,use_errno=True)
worker.sandbox(Path.cwd().resolve())
def queued_signal():
 if libc.sigqueue(os.getppid(),0,ctypes.c_int(0)) != 0: raise PermissionError(ctypes.get_errno(),"Queued signal denied")
results={}
for name,operation in [("file",lambda:open("/etc/passwd").read()),("network",lambda:socket.socket()),("fork",os.fork),("signal",lambda:os.kill(os.getppid(),0)),("queued_signal",queued_signal)]:
 try: operation();results[name]="ALLOWED"
 except PermissionError: results[name]="BLOCKED"
thread=threading.Thread(target=lambda:results.update(thread="ALLOWED"));thread.start();thread.join()
print(json.dumps(results))
''' % str(WORKER)
        result = subprocess.run([PYTHON, "-I", "-B", "-c", code], cwd=self.job,
                                capture_output=True, text=True, timeout=15)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout), {"file": "BLOCKED", "network": "BLOCKED", "fork": "BLOCKED", "signal": "BLOCKED", "queued_signal": "BLOCKED", "thread": "ALLOWED"})

    def test_preexisting_threads_make_sandbox_fail_closed(self):
        code = """
import importlib.util,threading
from pathlib import Path
spec=importlib.util.spec_from_file_location("worker", %r)
worker=importlib.util.module_from_spec(spec);spec.loader.exec_module(worker)
ready=threading.Event(); stop=threading.Event()
def wait(): ready.set();stop.wait()
thread=threading.Thread(target=wait);thread.start();ready.wait()
try:
 try: worker.sandbox(Path.cwd().resolve())
 except RuntimeError as error:
  assert "single-threaded" in str(error)
 else: raise AssertionError("Pre-existing thread was accepted")
finally: stop.set();thread.join()
""" % str(WORKER)
        result = subprocess.run([PYTHON, "-I", "-B", "-c", code], cwd=self.job,
                                capture_output=True, text=True, timeout=15)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_missing_requested_style_is_fatal_instead_of_silent_regular_text(self):
        candidates = [Path("/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"),
                      Path("/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf"),
                      Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")]
        font = next((path for path in candidates if path.is_file()), None)
        if font is None:
            self.skipTest("Normal TrueType face is not installed")
        (self.job / "normal.ttf").write_bytes(font.read_bytes())
        self.manifest["fonts"] = [{"file": "normal.ttf", "family": "ProbeFont", "weight": 400, "style": "normal"}]
        result = self.render("<html lang='en'><title>Style guard</title><style>body{font-family:ProbeFont}</style><b>Bold must remain bold</b></html>")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("lacks a requested bold/italic face", result.stderr)

    def test_missing_image_placeholders_keep_geometry_links_and_source_alt(self):
        from pypdf import PdfReader
        result = self.render("""<html lang='en'><title>Missing images</title><style>img{height:auto}</style>
<body><a href='https://example.invalid/social'><img width='19' height='30' alt='Long social icon description'></a>
<a href='https://example.invalid/logo'><img width='75' height='30' alt='Original footer logo'></a>
<a href='https://example.invalid/header'><img width='122' height='50' alt='Original header logo'></a>
<a href='https://example.invalid/button'><img width='130' height='40' alt='Original button'></a>
<img alt='Unknown image dimensions'><img width='1' height='1' alt=''><img width='80' height='15' alt=''>
</body></html>""")
        self.assertEqual(result.returncode, 0, result.stderr)
        metrics = json.loads((self.job / "metrics.json").read_text())
        self.assertEqual(metrics["missingImagePlaceholderCount"], 5)
        reader = PdfReader(self.job / "out.pdf")
        annotations = [item.get_object() for page in reader.pages for item in page.get("/Annots", [])]
        uris = {item.get("/A", {}).get("/URI") for item in annotations}
        self.assertEqual(uris, {"https://example.invalid/" + name for name in ("social", "logo", "header", "button")})
        for uri, width, height in (("social", 19, 30), ("logo", 75, 30), ("header", 122, 50), ("button", 130, 40)):
            boxes = [item["/Rect"] for item in annotations if item.get("/A", {}).get("/URI", "").endswith(uri)]
            self.assertTrue(any(abs(abs(float(box[2] - box[0])) - width * .75) < .1
                                and abs(abs(float(box[3] - box[1])) - height * .75) < .1 for box in boxes), boxes)
        alternatives, seen = [], set()

        def visit(node):
            node = node.get_object() if hasattr(node, "get_object") else node
            if isinstance(node, list):
                for item in node:
                    visit(item)
            elif isinstance(node, dict) and id(node) not in seen:
                seen.add(id(node))
                if node.get("/S") == "/Figure":
                    alternatives.append(str(node.get("/Alt", "")))
                if "/K" in node:
                    visit(node["/K"])

        visit(reader.trailer["/Root"]["/StructTreeRoot"])
        self.assertCountEqual(alternatives, ["Long social icon description", "Original footer logo", "Original header logo", "Original button", "Unknown image dimensions"])
        self.assertNotIn("Long social icon description", "".join(page.extract_text() for page in reader.pages))

    def test_missing_image_marker_is_small_within_the_original_link_box(self):
        import fitz
        result = self.render("""<html lang='en'><title>Small missing-image marker</title>
<a href='https://example.invalid/image'><img width='122' height='50' alt='Unavailable graphic'></a></html>""")
        self.assertEqual(result.returncode, 0, result.stderr)
        with fitz.open(self.job / "out.pdf") as document:
            image_links = [link for page in document for link in page.get_links()
                           if link.get("uri") == "https://example.invalid/image"]
            self.assertTrue(any(abs(link["from"].width - 91.5) < .1
                                and abs(link["from"].height - 37.5) < .1 for link in image_links))
            drawings = [drawing["rect"] for page in document for drawing in page.get_drawings()]
            self.assertTrue(drawings)
            painted = fitz.Rect(drawings[0])
            for bounds in drawings[1:]:
                painted |= bounds
            self.assertGreater(painted.width, 4)
            self.assertLess(painted.width, 10)
            self.assertLess(painted.height, 10)

    def test_missing_image_placeholder_never_embeds_source_alt_in_svg(self):
        import importlib.util
        import xml.etree.ElementTree as ET
        spec = importlib.util.spec_from_file_location("placeholder_worker", WORKER)
        worker = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(worker)
        root = ET.fromstring('<div><img alt="&lt;script&gt;private text&lt;/script&gt;"/><img src="eapdf://approved" alt="Retained"/></div>')
        approved = root[1].attrib.copy()
        self.assertEqual(worker.replace_missing_images(root), 1)
        self.assertEqual(root[0].get("alt"), "<script>private text</script>")
        self.assertEqual(root[0].get("src"), worker.MISSING_IMAGE)
        self.assertEqual(root[1].attrib, approved)
        for dimension in ("inf", "nan", "19201", "1e100"):
            with self.subTest(dimension=dimension):
                with self.assertRaises(ValueError):
                    worker.missing_image_uri(ET.fromstring(f'<img width="{dimension}" alt="Missing"/>'))

    def test_remote_image_is_fatal_and_never_fetched(self):
        result = self.render("<html lang='en'><title>Denied image</title><img src='https://127.0.0.1:9/private.png'></html>")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("not explicitly staged", result.stderr)
        self.assertFalse((self.job / "metrics.json").exists())

    def test_arbitrary_file_and_manifest_escape_are_rejected(self):
        result = self.render("<html lang='en'><title>Denied file</title><img src='file:///etc/passwd'></html>")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("not explicitly staged", result.stderr)
        self.manifest["assets"] = {"eapdf://escape": "../secret"}
        result = self.render("<p>Manifest path escape</p>")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("relative to the render job", result.stderr)

    def test_staged_symlink_is_rejected(self):
        (self.job / "linked").symlink_to("/etc/passwd")
        self.manifest["assets"] = {"eapdf://image": "linked"}
        result = self.render("<img src='eapdf://image'>")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Symlinks are forbidden", result.stderr)

    def test_staged_image_never_reopens_its_original_file_uri(self):
        import struct
        import zlib
        def chunk(kind, body):
            return struct.pack("!I", len(body)) + kind + body + struct.pack("!I", zlib.crc32(kind + body))
        png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack("!IIBBBBB", 1, 1, 8, 2, 0, 0, 0))
               + chunk(b"IDAT", zlib.compress(b"\x00\xff\x00\x00")) + chunk(b"IEND", b""))
        (self.job / "image.bin").write_bytes(png)
        with tempfile.TemporaryDirectory(prefix="eapdf-original-outside-worker-") as original:
            original_path = Path(original) / "source.png"
            original_path.write_bytes(png)
            self.manifest["assets"] = {original_path.as_uri(): "image.bin"}
            result = self.render(f"<html lang='en'><title>Approved image</title><img alt='Red sample' src='{original_path.as_uri()}'></html>")
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(original_path.read_bytes(), png)

    def test_svg_cannot_fetch_an_unstaged_nested_resource(self):
        (self.job / "image.svg").write_text(
            '<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20"><image href="file:///etc/passwd" width="20" height="20"/></svg>')
        self.manifest["assets"] = {"eapdf://image.svg": "image.svg"}
        result = self.render("<html lang='en'><title>Denied nested resource</title><img alt='Nested SVG' src='eapdf://image.svg'></html>")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("not explicitly staged", result.stderr)

    def test_authored_attachment_directives_cannot_embed_payloads(self):
        import re
        import zlib
        result = self.render("""<html lang="en"><head><title>No authored attachments</title>
<link rel="attachment" href="data:text/plain;base64,U0VDUkVU" title="Hidden payload"></head>
<body><a rel="nofollow ATTACHMENT" href="data:text/plain;base64,U0VDUkVU">Visible anchor text</a></body></html>""")
        self.assertEqual(result.returncode, 0, result.stderr)
        pdf = (self.job / "out.pdf").read_bytes()
        objects = [pdf]
        for match in re.finditer(rb"stream\r?\n(.*?)\r?\nendstream", pdf, re.DOTALL):
            try: objects.append(zlib.decompress(match.group(1)))
            except zlib.error: pass
        decoded = b"\n".join(objects)
        self.assertNotIn(b"/EmbeddedFiles", decoded)
        self.assertIsNone(re.search(rb"/Type\s*/(?:EmbeddedFile|Filespec)\b", decoded))

    def test_nonisolated_python_is_rejected(self):
        result = self.render("<p>Rejected startup</p>", isolated=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Python -I", result.stderr)

    def test_overwide_layout_is_fitted_once_and_bounded(self):
        result = self.render("<html lang='en'><title>Wide table</title><table style='width:1400px'><tr><td>Left</td><td style='text-align:right'>Right</td></tr></table></html>")
        self.assertEqual(result.returncode, 0, result.stderr)
        metrics = json.loads((self.job / "metrics.json").read_text())
        self.assertGreater(metrics["initialOverflowCssPx"], 0)
        self.assertTrue(metrics["fitWideContent"])
        result = self.render("<table style='width:30000px'><tr><td>Excessive width</td></tr></table>")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("supported page width", result.stderr)

    def test_data_resource_limits_are_checked_before_decoding(self):
        code = """
import importlib.util
spec=importlib.util.spec_from_file_location("worker", %r)
worker=importlib.util.module_from_spec(spec);spec.loader.exec_module(worker)
def forbidden_decode(*args,**kwargs): raise AssertionError("Decoder allocated before size rejection")
worker.base64.b64decode=forbidden_decode;worker.unquote_to_bytes=forbidden_decode
fetcher=worker.resource_fetcher({},2,[])
for uri in ["data:image/png;base64,AAAA","data:image/png;base64,A A A A","data:image/png,%%41%%41A","data:image/png,éé"]:
 try: fetcher.fetch(uri)
 except ValueError as error: assert "processing limit" in str(error)
 else: raise AssertionError("Oversized resource accepted")
""" % str(WORKER)
        result = subprocess.run([PYTHON, "-I", "-B", "-c", code], cwd=self.job,
                                capture_output=True, text=True, timeout=15)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_page_and_memory_limits_fail_loudly(self):
        self.manifest["maxPages"] = 1
        result = self.render("<div style='page-break-after:always'>One</div><p>Two</p>")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("page count", result.stderr)
        self.manifest["memoryMiB"] = 1
        result = self.render("<p>Cannot import/render within 1 MiB</p>")
        self.assertNotEqual(result.returncode, 0)


if __name__ == "__main__":
    unittest.main()
