#!/usr/bin/env python3
"""Compare configured creator renderers with real sources and Java validation.

Run evidence contains private source paths and rendered mail: keep it local.
Conversion timings include cold JVM/Python startup; validation and raster analysis
are timed separately. Linux /proc sampling includes live descendant RSS, including
WeasyPrint Python processes. Shared resident pages may be counted more than once.
"""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
from contextlib import suppress
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import signal
import statistics
import subprocess
import sys
import tempfile
import threading
import time

ROOT = Path(__file__).resolve().parents[1]
MIB = 1024 * 1024
KINDS = {"eml", "msg", "pst"}
BACKENDS = ("openhtmltopdf", "weasyprint")
CANCEL = threading.Event()


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def save(path, value):
    Path(path).write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n")


def process_snapshot(session, known=None):
    """Read one process-session snapshot; tolerate concurrent exits and execs."""
    all_processes = {}
    for folder in Path("/proc").iterdir():
        if not folder.name.isdigit():
            continue
        try:
            stat = (folder / "stat").read_text()
            fields = stat[stat.rindex(")") + 2:].split()
            all_processes[(int(folder.name), fields[19])] = {
                "parent": int(fields[1]), "session": int(fields[3]), "state": fields[0],
                "cpu_ticks": int(fields[11]) + int(fields[12]),
            }
        except (OSError, ValueError, IndexError):
            continue
    known = known or set()
    selected = {identity for identity, item in all_processes.items()
                if item["session"] == session or identity in known}
    while True:
        parents = {identity[0] for identity in selected}
        descendants = {identity for identity, item in all_processes.items() if item["parent"] in parents}
        if descendants.issubset(selected):
            break
        selected.update(descendants)
    result = {}
    for identity in selected:
        folder = Path("/proc") / str(identity[0])
        try:
            status = (folder / "status").read_text()
            match = re.search(r"^VmRSS:\s+(\d+) kB", status, re.M)
            command = (folder / "cmdline").read_bytes().split(b"\0")
            executable = Path(os.fsdecode(command[0])).name.lower() if command and command[0] else ""
            result[identity] = dict(all_processes[identity], rss=int(match.group(1)) * 1024 if match else 0,
                                    python="python" in executable)
        except (OSError, ValueError, IndexError):
            continue
    return result


def stop_session(process, known=None):
    snapshot = process_snapshot(process.pid, known)
    with suppress(ProcessLookupError):
        os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=3)
    except subprocess.TimeoutExpired:
        with suppress(ProcessLookupError):
            os.killpg(process.pid, signal.SIGKILL)
        process.wait()
    # Include descendants that survived group termination; guard against PID reuse.
    current = process_snapshot(process.pid, snapshot.keys())
    for identity in snapshot.keys() & current.keys():
        with suppress(ProcessLookupError):
            os.kill(identity[0], signal.SIGKILL)
    # Signal delivery is asynchronous. Do not return while a separately
    # sessioned Python worker is still running after the supervisor was reaped.
    deadline = time.monotonic() + 2
    while True:
        remaining = process_snapshot(process.pid, snapshot.keys())
        live = [identity for identity, item in remaining.items() if item["state"] != "Z"]
        if not live:
            break
        if time.monotonic() >= deadline:
            raise RuntimeError("benchmark subprocess descendants did not terminate")
        time.sleep(.01)


def execute(command, log, timeout, environment, memory_limit, sample_seconds):
    start = time.perf_counter()
    peak = python_peak = python_process_peak = samples = 0
    cpu = {}
    sample_times = []
    reason = None
    with Path(log).open("w") as stream:
        process = subprocess.Popen(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT,
                                   env=environment, start_new_session=True)
        try:
            while True:
                sample_times.append(time.perf_counter())
                snapshot = process_snapshot(process.pid, cpu.keys())
                rss = sum(item["rss"] for item in snapshot.values())
                python_rss = sum(item["rss"] for item in snapshot.values() if item["python"])
                peak, python_peak = max(peak, rss), max(python_peak, python_rss)
                python_process_peak = max(python_process_peak, sum(item["python"] and item["rss"] > 0 for item in snapshot.values()))
                for identity, item in snapshot.items():
                    cpu[identity] = max(cpu.get(identity, 0), item["cpu_ticks"])
                samples += 1
                if process.poll() is not None:
                    break
                if CANCEL.is_set():
                    reason = "cancelled"
                elif time.perf_counter() - start > timeout:
                    reason = "timeout"
                elif rss > memory_limit:
                    reason = "sampled process-tree RSS limit exceeded"
                if reason:
                    stop_session(process, cpu.keys())
                    break
                time.sleep(sample_seconds)
        finally:
            if process.poll() is None:
                stop_session(process, cpu.keys())
    return {"exit_code": process.returncode, "seconds": round(time.perf_counter() - start, 4),
            "peak_tree_rss_bytes": peak, "peak_python_rss_bytes": python_peak, "peak_python_processes": python_process_peak,
            "sampled_cpu_seconds": round(sum(cpu.values()) / os.sysconf("SC_CLK_TCK"), 4),
            "samples": samples, "sampling_seconds": sample_seconds,
            "maximum_observed_sample_gap_seconds": round(max((right - left for left, right in zip(sample_times, sample_times[1:])), default=0), 4),
            "stopped_reason": reason}


def inspect_document(pdf, destination, max_pages, child_limit):
    """Runs only in a monitored child process; imports optional native tools here."""
    import fitz
    destination.mkdir(parents=True, exist_ok=True)
    metadata = {"pymupdf_version": fitz.VersionBind, "documents": []}

    def inspect_one(document, label):
        row = {"label": label, "page_count": len(document), "inspected_pages": min(len(document), max_pages),
               "pages": [], "fonts": [], "link_uri_hashes": []}
        fonts, links, combined = set(), set(), []
        for index in range(min(len(document), max_pages)):
            page = document[index]
            text = re.sub(r"\s+", " ", page.get_text()).strip()
            combined.append(text)
            fonts.update(str(font[3]) for font in page.get_fonts())
            links.update(hashlib.sha256(link["uri"].encode()).hexdigest()
                         for link in page.get_links() if link.get("uri"))
            scale = min(96 / 72, 1600 / max(page.rect.width, page.rect.height))
            pixmap = page.get_pixmap(matrix=fitz.Matrix(scale, scale), colorspace=fitz.csRGB, alpha=False)
            image = destination / f"{label}-page-{index + 1:03d}.png"
            pixmap.save(image)
            row["pages"].append({"number": index + 1, "width_points": page.rect.width,
                                 "height_points": page.rect.height, "text": text,
                                 "text_sha256": hashlib.sha256(text.encode()).hexdigest(),
                                 "raster": image.name, "width_pixels": pixmap.width, "height_pixels": pixmap.height})
        row["text_sha256"] = hashlib.sha256("\n".join(combined).encode()).hexdigest()
        row["fonts"], row["link_uri_hashes"] = sorted(fonts), sorted(links)
        # This is a structural sample, not an EA-PDF/UA conformance check.
        tags = {name: 0 for name in ("Mail_Field", "Mail_FieldName", "Mail_FieldValue")}
        if document.xref_length() <= 100_000:
            for xref in range(1, document.xref_length()):
                try:
                    value = document.xref_object(xref, compressed=True)
                except RuntimeError as error:
                    if "cannot find object in xref" not in str(error):
                        raise
                    # Valid sparse xref tables contain unallocated object numbers.
                    row["unallocated_xref_slots"] = row.get("unallocated_xref_slots", 0) + 1
                    continue
                for name in re.findall(r"/S\s*/(Mail_Field(?:Name|Value)?)(?=[/\s>])", value):
                    tags[name] += 1
        row["mail_tags"] = tags
        metadata["documents"].append(row)

    with fitz.open(pdf) as outer:
        inspect_one(outer, "outer")
        seen = set()
        for name in sorted(outer.embfile_names()):
            if not name.lower().endswith(".pdf"):
                continue
            info = outer.embfile_info(name)
            if int(info.get("size", child_limit + 1)) > child_limit:
                metadata.setdefault("omitted_children", []).append({"reason": "child exceeds raster-analysis byte allowance"})
                continue
            data = outer.embfile_get(name)
            checksum = hashlib.sha256(data).hexdigest()
            if checksum in seen:
                continue
            seen.add(checksum)
            with fitz.open(stream=data, filetype="pdf") as child:
                inspect_one(child, f"child-{len(seen):03d}")
            if len(seen) >= 3:
                break
    save(destination / "inspection.json", metadata)


def compare_rasters(first, second, destination):
    from PIL import Image, ImageChops, ImageDraw
    destination.mkdir(parents=True, exist_ok=True)
    results = []
    a = json.loads((first / "inspection.json").read_text())
    b = json.loads((second / "inspection.json").read_text())
    from difflib import SequenceMatcher
    for left, right in zip(a["documents"], b["documents"]):
        row = {"label": left["label"], "paired_labels": [left["label"], right["label"]], "page_counts": [left["page_count"], right["page_count"]],
               "text_equal": left["text_sha256"] == right["text_sha256"],
               "uri_targets_equal": left["link_uri_hashes"] == right["link_uri_hashes"],
               "mail_tags": [left["mail_tags"], right["mail_tags"]], "pages": []}
        for page_a, page_b in zip(left["pages"], right["pages"]):
            with Image.open(first / page_a["raster"]) as image_a, Image.open(second / page_b["raster"]) as image_b:
                width, height = max(image_a.width, image_b.width), max(image_a.height, image_b.height)
                padded_a, padded_b = Image.new("RGB", (width, height), "white"), Image.new("RGB", (width, height), "white")
                padded_a.paste(image_a, (0, 0)); padded_b.paste(image_b, (0, 0))
                difference = ImageChops.difference(padded_a, padded_b)
                histogram = difference.histogram()
                rmse = math.sqrt(sum((index % 256) ** 2 * count for index, count in enumerate(histogram)) / (width * height * 3))
                channels = difference.split()
                maximum = ImageChops.lighter(ImageChops.lighter(channels[0], channels[1]), channels[2])
                changed = maximum.point(lambda value: 255 if value > 8 else 0).histogram()[255] / (width * height)
                preview = Image.new("RGB", (width * 3, height + 25), "white")
                preview.paste(padded_a, (0, 25)); preview.paste(padded_b, (width, 25)); preview.paste(difference, (width * 2, 25))
                draw = ImageDraw.Draw(preview)
                for column, title in enumerate(("OpenHTMLtoPDF", "WeasyPrint", "Absolute pixel difference")):
                    draw.text((column * width + 5, 5), title, fill="black")
                filename = f"{left['label']}-page-{page_a['number']:03d}-comparison.png"
                preview.save(destination / filename)
                row["pages"].append({"page": page_a["number"], "rmse_rgb_0_255": round(rmse, 4),
                    "changed_pixel_fraction_threshold_8": round(changed, 6),
                    "dimensions_equal": (page_a["width_points"], page_a["height_points"]) == (page_b["width_points"], page_b["height_points"]),
                    "text_similarity": round(SequenceMatcher(None, page_a["text"], page_b["text"]).ratio(), 5),
                    "comparison_image": filename})
        results.append(row)
    save(destination / "comparison.json", {"documents": results, "document_counts": [len(a["documents"]), len(b["documents"])],
         "document_labels_equal": [row["label"] for row in a["documents"]] == [row["label"] for row in b["documents"]],
         "note": "Pixel differences measure layout/raster change, not visual quality. Only the configured first pages and up to three distinct collection children were inspected."})
    return results


def validation_summary(report):
    brief = Path(str(report) + ".summary.json")
    selected = brief if brief.is_file() else report
    if not selected.is_file():
        return {"status": "UNAVAILABLE", "pdfa_status": "UNAVAILABLE"}
    data = json.loads(selected.read_text())
    return {"status": data.get("summary", {}).get("status", "UNAVAILABLE"),
            "pdfa_status": data.get("pdfa", {}).get("status", "UNAVAILABLE"),
            "warnings": data.get("summary", {}).get("warn"), "failure_codes": data.get("failure_codes", [])}


def run_case(case, backend, repetition, args):
    folder = args.evidence / "runs" / case["label"] / backend / f"run-{repetition:02d}"
    folder.mkdir(parents=True)
    result = {"case": case["label"], "kind": case["kind"], "backend": backend, "run": repetition}
    config = args.openhtml_config if backend == BACKENDS[0] else args.weasyprint_config
    environment = dict(os.environ, EAPDF_CONFIG=str(config))
    environment.setdefault("EAPDF_WEASYPRINT_SLOT_ROOT", str(args.evidence / "worker-slots"))
    environment.setdefault("EAPDF_WORKER_HEAP_BYTES", str(args.java_heap_mib * MIB))
    # The standalone Java validator accepts one or four bounded child workers.
    environment["VALIDATOR_CHILD_PROCESSES"] = "4"
    try:
        if CANCEL.is_set():
            result["status"] = "CANCELLED"
            return result
        with tempfile.TemporaryDirectory(prefix="eapdf-renderer-benchmark-", dir="/tmp") as temporary:
            scratch = Path(temporary)
            generated = scratch / "generated"; generated.mkdir()
            filename = f"{case['label']}_run{repetition:02d}_1c.pdf"
            output = generated / filename
            command = [args.java, f"-Xmx{args.java_heap_mib}m", f"-XX:ActiveProcessorCount={args.jobs}",
                "-Djava.io.tmpdir=" + str(scratch), "-jar", str(args.creator_jar), "--jobs", str(args.jobs),
                "--out", str(generated), "--archive-profile", "c", "--archive-output", str(output), case["source"]]
            result["conversion"] = execute(command, folder / "conversion.log", args.timeout, environment,
                                           args.memory_limit_mib * MIB, args.sample_seconds)
            worker_rows = []
            for line in (folder / "conversion.log").read_text(errors="replace").splitlines():
                if line.startswith("[weasyprint] {"):
                    with suppress(json.JSONDecodeError):
                        worker_rows.append(json.loads(line[len("[weasyprint] "):]))
            if worker_rows:
                result["weasyprint_workers"] = worker_rows
                result["worker_reported_peak_rss_bytes"] = max(row.get("peakRssKiB", 0) * 1024 for row in worker_rows)
            if (generated / "logs").exists(): shutil.copytree(generated / "logs", folder / "creator-file-logs")
            if (generated / "progress.json").exists(): shutil.copy2(generated / "progress.json", folder / "progress.json")
            if result["conversion"]["exit_code"] != 0 or not output.is_file():
                result["status"] = "REJECTED"
                return result
            result["pdf_size_bytes"] = output.stat().st_size
            result["pdf_sha256"] = digest(output)
            report = folder / "java-validation.json"
            result["validation_execution"] = execute([args.java, f"-Xmx{args.java_heap_mib}m",
                "-Djava.io.tmpdir=" + str(scratch), "-cp", str(args.validator_jar), "eapdf.JavaValidation", str(output), str(report)],
                folder / "java-validation.log", args.validation_timeout, environment, args.memory_limit_mib * MIB, args.sample_seconds)
            result["validation"] = validation_summary(report)
            result["status"] = "PASS" if (result["validation_execution"]["exit_code"] == 0
                and result["validation"]["status"] == "PASS" and result["validation"]["pdfa_status"] == "PASS") else "VALIDATION_FAILED"
            published = args.documents / backend / case["kind"] / filename
            published.parent.mkdir(parents=True, exist_ok=True); shutil.copy2(output, published)
            result["pdf"] = str(published)
            if repetition == 1:
                inspection = folder / "inspection"
                result["inspection_execution"] = execute([sys.executable, str(Path(__file__).resolve()), "--inspect-document",
                    str(published), str(inspection), str(args.visual_pages), str(args.child_analysis_mib * MIB)],
                    folder / "inspection.log", args.timeout, environment, args.memory_limit_mib * MIB, args.sample_seconds)
                result["inspection_available"] = (inspection / "inspection.json").is_file()
    except Exception as error:
        result["status"] = "ERROR"; result["error"] = f"{type(error).__name__}: {error}"
    finally:
        save(folder / "result.json", result)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--creator-jar", type=Path, required=True)
    parser.add_argument("--validator-jar", type=Path, required=True)
    parser.add_argument("--openhtml-config", type=Path, required=True)
    parser.add_argument("--weasyprint-config", type=Path, required=True)
    parser.add_argument("--sample", action="append", required=True, metavar="TYPE=PATH", help="Real source under in/; repeat for EML, MSG and PST")
    parser.add_argument("--evidence", type=Path, required=True, help="New local run directory under output/; contains private evidence")
    parser.add_argument("--documents", type=Path, required=True, help="New explicit directory for generated PDFs, normally under /tmp")
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--workers", type=int, choices=(1, 2), default=1)
    parser.add_argument("--jobs", type=int, default=2)
    parser.add_argument("--java-heap-mib", type=int, default=1024)
    parser.add_argument("--memory-limit-mib", type=int, default=4096)
    parser.add_argument("--timeout", type=float, default=600)
    parser.add_argument("--validation-timeout", type=float, default=900)
    parser.add_argument("--sample-seconds", type=float, default=.025)
    parser.add_argument("--visual-pages", type=int, default=3)
    parser.add_argument("--child-analysis-mib", type=int, default=32)
    parser.add_argument("--java", default="java")
    args = parser.parse_args()
    for interrupt in (signal.SIGINT, signal.SIGTERM):
        signal.signal(interrupt, lambda *_: CANCEL.set())
    if not sys.platform.startswith("linux") or not Path("/proc").is_dir():
        parser.error("Linux /proc is required for full process-tree/Python memory accounting")
    if any(value <= 0 for value in (args.repeats, args.jobs, args.java_heap_mib, args.memory_limit_mib,
                                   args.timeout, args.validation_timeout, args.sample_seconds, args.visual_pages, args.child_analysis_mib)):
        parser.error("all limits and repeat counts must be positive")
    if args.sample_seconds > .1:
        parser.error("--sample-seconds must be at most 0.1 for meaningful Python process sampling")
    for name in ("creator_jar", "validator_jar", "openhtml_config", "weasyprint_config", "evidence", "documents"):
        setattr(args, name, getattr(args, name).resolve())
    if not args.evidence.is_relative_to(ROOT / "output"):
        parser.error("--evidence must be under the repository output/ directory")
    for name, engine in (("openhtml_config", "openhtmltopdf"), ("weasyprint_config", "weasyprint")):
        config = json.loads(getattr(args, name).read_text())
        if config.get("rendering", {}).get("engine", "weasyprint") != engine:
            parser.error(f"{name} must select {engine}")
        if config.get("accessibility", {}).get("addPdfUaFeatures") is not True:
            parser.error(f"{name} must explicitly enable accessibility.addPdfUaFeatures")
        if config.get("premis", {}).get("enabled") is True:
            parser.error("PREMIS must remain disabled for benchmark conversions")
    # Bound concurrent jobs against current host/cgroup availability; each command
    # additionally enforces its own sampled process-tree RSS ceiling.
    from run_real_world_corpus import available_memory
    if args.workers * args.memory_limit_mib * MIB > available_memory() * .8:
        parser.error("requested concurrent memory ceilings exceed 80% of available memory; reduce --workers or --memory-limit-mib")
    cases, counts = [], dict.fromkeys(KINDS, 0)
    for sample in args.sample:
        kind, separator, filename = sample.partition("=")
        if not separator or kind not in KINDS:
            parser.error("--sample must have eml=PATH, msg=PATH or pst=PATH form")
        source = Path(filename).resolve()
        if not source.is_file() or not source.is_relative_to(ROOT / "in") or source.suffix.lower() != "." + kind:
            parser.error("samples must be matching real source files under in/")
        counts[kind] += 1
        cases.append({"kind": kind, "label": f"{kind}_case{counts[kind]:02d}", "source": str(source),
                      "source_sha256_before": digest(source), "source_size_bytes": source.stat().st_size})
    args.evidence.mkdir(parents=True, exist_ok=False); args.documents.mkdir(parents=True, exist_ok=False)
    # Rendering experiments must not race a developer rebuilding either JAR.
    # Snapshot both versioned artifacts and verify the source remained stable
    # throughout the copy before launching any measured process.
    tools = args.evidence / "tools"; tools.mkdir()
    for name in ("creator_jar", "validator_jar", "openhtml_config", "weasyprint_config"):
        source = getattr(args, name)
        checksum = digest(source)
        snapshot = tools / name / source.name
        snapshot.parent.mkdir()
        shutil.copy2(source, snapshot)
        if digest(snapshot) != checksum or digest(source) != checksum:
            parser.error(f"{name} changed during snapshot; wait for the build to finish")
        setattr(args, name, snapshot)
    import zipfile
    with zipfile.ZipFile(args.creator_jar) as archive:
        manifest_text = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
        if "Main-Class: eapdf.EmailToPDFA" not in manifest_text:
            parser.error("creator JAR is not the completed executable creator package; wait for mvn package")
    (args.evidence / "working-tree.patch").write_bytes(subprocess.check_output(
        ["git", "diff", "--binary", "HEAD", "--", "src/main", "pom.xml", "scripts/benchmark_renderers.py"], cwd=ROOT))
    manifest = {"base_commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "creator_jar_sha256": digest(args.creator_jar), "validator_jar_sha256": digest(args.validator_jar),
        "config_sha256": {engine: digest(config) for engine, config in zip(BACKENDS, (args.openhtml_config, args.weasyprint_config))},
        "production_source_sha256": {str(path.relative_to(ROOT)): digest(path) for path in sorted((ROOT / "src/main").rglob("*")) if path.is_file() and path.suffix in (".java", ".py")},
        "cases": cases, "repeats": args.repeats, "workers": args.workers, "creator_jobs": args.jobs,
        "memory_measurement": "25ms/default sampled simultaneous descendant-process RSS; Python is a subset, already included in tree RSS; shared pages may be counted twice; short-lived processes/spikes can be missed",
        "timing_scope": "cold complete creator invocation, including supervised JVM and Python startup, excluding validation/raster analysis",
        "visual_scope": "first configured pages of outer PDF and up to three distinct collection children; no claim of visual equivalence"}
    save(args.evidence / "manifest.json", manifest)
    tasks = []
    for repetition in range(1, args.repeats + 1):
        for case in cases:
            order = BACKENDS if repetition % 2 else tuple(reversed(BACKENDS))
            tasks.extend((case, backend, repetition) for backend in order)
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        results = list(pool.map(lambda task: run_case(*task, args), tasks))
    aggregates = []
    for case in cases:
        case["source_sha256_after"] = digest(case["source"])
        case["source_unchanged"] = case["source_sha256_before"] == case["source_sha256_after"]
        for backend in BACKENDS:
            rows = [row for row in results if row["case"] == case["label"] and row["backend"] == backend]
            successful = [row for row in rows if row.get("status") == "PASS"]
            completed = [row for row in rows if "pdf_size_bytes" in row and row.get("conversion", {}).get("exit_code") == 0]
            aggregates.append({"case": case["label"], "kind": case["kind"], "backend": backend,
                "passed": len(successful), "converted": len(completed), "attempted": len(rows),
                "median_seconds": statistics.median(row["conversion"]["seconds"] for row in completed) if completed else None,
                "median_peak_tree_rss_mib": statistics.median(row["conversion"]["peak_tree_rss_bytes"] / MIB for row in completed) if completed else None,
                "maximum_peak_python_rss_mib": max((row["conversion"]["peak_python_rss_bytes"] / MIB for row in completed), default=0),
                "maximum_observed_python_processes": max((row["conversion"].get("peak_python_processes", 0) for row in completed), default=0),
                "maximum_worker_reported_rss_mib": max((row.get("worker_reported_peak_rss_bytes", 0) / MIB for row in completed), default=0),
                "median_pdf_size_bytes": statistics.median(row["pdf_size_bytes"] for row in rows if "pdf_size_bytes" in row) if any("pdf_size_bytes" in row for row in rows) else None})
        first = args.evidence / "runs" / case["label"] / BACKENDS[0] / "run-01" / "inspection"
        second = args.evidence / "runs" / case["label"] / BACKENDS[1] / "run-01" / "inspection"
        if (first / "inspection.json").is_file() and (second / "inspection.json").is_file():
            try: compare_rasters(first, second, args.evidence / "visual-comparisons" / case["label"])
            except Exception as error: save(args.evidence / f"{case['label']}-visual-unavailable.json", {"error": str(error)})
    manifest["cases"] = cases; save(args.evidence / "manifest.json", manifest)
    save(args.evidence / "results.json", {"aggregates": aggregates, "runs": results})
    print(json.dumps({"aggregates": aggregates, "source_hashes_unchanged": all(case["source_unchanged"] for case in cases)}, indent=2))
    return 0 if all(row.get("status") == "PASS" for row in results) and all(case["source_unchanged"] for case in cases) else 1


if __name__ == "__main__":
    if len(sys.argv) == 6 and sys.argv[1] == "--inspect-document":
        inspect_document(Path(sys.argv[2]), Path(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5]))
    else:
        raise SystemExit(main())
