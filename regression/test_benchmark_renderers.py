"""Meaningful process-accounting/cleanup regressions for renderer comparisons."""
import json
import os
from pathlib import Path
import sys
import tempfile
import threading
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import benchmark_renderers as BENCH


@unittest.skipUnless(sys.platform.startswith("linux") and Path("/proc").is_dir(), "Linux /proc accounting")
class BenchmarkProcessTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="eapdf-benchmark-test-", dir="/tmp")
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)

    def execute(self, code, timeout=5, memory=128 * BENCH.MIB):
        return BENCH.execute([sys.executable, "-c", code], self.directory / "process.log",
                             timeout, os.environ.copy(), memory, .01)

    def test_python_child_creating_a_new_session_is_included(self):
        result = self.execute("import subprocess,sys; p=subprocess.Popen([sys.executable,'-c',"
                              "'import time; data=bytearray(24*1024*1024); time.sleep(.25)'],"
                              "start_new_session=True); p.wait()")
        self.assertEqual(0, result["exit_code"])
        self.assertGreater(result["peak_python_rss_bytes"], 24 * BENCH.MIB)
        self.assertGreaterEqual(result["peak_python_processes"], 2)
        self.assertGreaterEqual(result["peak_tree_rss_bytes"], result["peak_python_rss_bytes"])

    def test_timeout_kills_child_even_after_it_creates_another_session(self):
        result = self.execute("import subprocess,sys,time; p=subprocess.Popen([sys.executable,'-c',"
                              "'import time; time.sleep(60)'],start_new_session=True);"
                              "print(p.pid,flush=True);time.sleep(60)", timeout=.15)
        self.assertEqual("timeout", result["stopped_reason"])
        child = int((self.directory / "process.log").read_text().strip())
        stat = Path(f"/proc/{child}/stat")
        if stat.exists():
            # An adopted zombie has terminated and has no resident memory.
            self.assertEqual("Z", stat.read_text().split(")", 1)[1].split()[0])

    def test_cancellation_stops_running_measurement(self):
        timer = threading.Timer(.15, BENCH.CANCEL.set)
        timer.start()
        try:
            result = self.execute("import time;time.sleep(60)")
            self.assertEqual("cancelled", result["stopped_reason"])
            self.assertNotEqual(0, result["exit_code"])
        finally:
            timer.cancel()
            BENCH.CANCEL.clear()

    def test_sampled_memory_ceiling_is_enforced(self):
        result = self.execute("import time; data=bytearray(32*1024*1024);time.sleep(60)", memory=16 * BENCH.MIB)
        self.assertEqual("sampled process-tree RSS limit exceeded", result["stopped_reason"])
        self.assertNotEqual(0, result["exit_code"])

    def test_missing_or_incomplete_validation_is_never_pass(self):
        report = self.directory / "report.json"
        self.assertEqual("UNAVAILABLE", BENCH.validation_summary(report)["status"])
        report.write_text(json.dumps({"summary": {"status": "INCOMPLETE"}, "pdfa": {"status": "PASS"}}))
        self.assertEqual("INCOMPLETE", BENCH.validation_summary(report)["status"])
        self.assertEqual("PASS", BENCH.validation_summary(report)["pdfa_status"])


if __name__ == "__main__":
    unittest.main()
