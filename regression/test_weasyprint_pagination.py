"""Real-worker pagination checks; set EAPDF_WEASYPRINT_PYTHON to the pinned runtime.

Synthetic table padding reproduces the newsletter's stranded body row without
using correspondence, external resources or authored layout rewrites.
"""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

try:
    import fitz
except ImportError:
    fitz = None

ROOT = Path(__file__).resolve().parents[1]
WORKER = ROOT / "src/main/resources/eapdf/weasyprint_worker.py"
PYTHON = os.environ.get("EAPDF_WEASYPRINT_PYTHON")


def table_fixture(row_style="", cell_style="", extra_css=""):
    # All 19 lines fit before bottom padding is added. Without reserving that
    # padding, WeasyPrint rejects the completed row and moves its start to page 2.
    paragraphs = "<p>BODY_START</p>" + "".join(
        f"<p>Row {index:02d} content</p>" for index in range(1, 19))
    return f"""<!DOCTYPE html><html lang="en"><title>Padding fragmentation</title>
<style>
@page {{size:320px 400px; margin:20px}}
body {{margin:0; font:12px/14px sans-serif}}
.mail-header {{height:40px}}
table {{width:100%; border-collapse:collapse}}
td {{padding:0; vertical-align:top}}
h1 {{font:20px/28px sans-serif; margin:0}}
p {{margin:0; line-height:14px}}
.body-cell {{padding:20px 0}}
{extra_css}
</style><body><div class="mail-header">MAIL_HEADER</div><div id="email-root">
<table><tr><td><h1>NEWSLETTER_TITLE</h1></td></tr>
<tr style="{row_style}"><td class="body-cell" style="{cell_style}">{paragraphs}</td></tr>
</table></div></body></html>"""


@unittest.skipUnless(PYTHON and fitz is not None,
                     "Pinned WeasyPrint runtime and PyMuPDF are required")
class WeasyPrintPaginationTests(unittest.TestCase):
    def render_pages(self, html):
        with tempfile.TemporaryDirectory(prefix="eapdf-pagination-test-", dir="/tmp") as temporary:
            job = Path(temporary)
            manifest = {
                "version": 1, "memoryMiB": 512, "maxOutputMiB": 8,
                "timeoutSeconds": 15, "maxPages": 8, "fonts": [], "assets": {},
                "headerTagMarker": "", "fitWideContent": True,
            }
            (job / "request.json").write_text(json.dumps(manifest), encoding="utf-8")
            (job / "in.html").write_text(html, encoding="utf-8")
            result = subprocess.run(
                [PYTHON, "-I", str(WORKER), "request.json"], cwd=job,
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=25)
            self.assertEqual(result.returncode, 0, result.stderr)
            metrics = json.loads((job / "metrics.json").read_text())
            self.assertGreaterEqual(metrics["landlockAbi"], 3)
            self.assertTrue(metrics["seccomp"])
            with fitz.open(job / "out.pdf") as document:
                pages = [page.get_text() for page in document]
            self.assertEqual(len(pages), 2)
            combined = "\n".join(pages)
            self.assertEqual(combined.count("BODY_START"), 1)
            for index in range(1, 19):
                self.assertEqual(combined.count(f"Row {index:02d} content"), 1)
            return pages

    def test_padding_reservation_keeps_title_and_body_start_on_first_page(self):
        pages = self.render_pages(table_fixture())
        self.assertIn("NEWSLETTER_TITLE", pages[0])
        self.assertIn("BODY_START", pages[0])
        self.assertIn("Row 18 content", pages[1])

    def test_authored_row_avoid_still_keeps_the_complete_row_together(self):
        pages = self.render_pages(table_fixture(row_style="break-inside:avoid"))
        self.assertNotIn("BODY_START", pages[0])
        self.assertIn("BODY_START", pages[1])

    def test_authored_inline_slice_still_overrides_the_user_default(self):
        pages = self.render_pages(table_fixture(cell_style="box-decoration-break:slice"))
        self.assertNotIn("BODY_START", pages[0])
        self.assertIn("BODY_START", pages[1])

    def test_native_worker_author_stylesheet_slice_still_overrides_the_default(self):
        # This exercises the worker cascade, not the separate Java CSS parser.
        pages = self.render_pages(table_fixture(extra_css=".body-cell{box-decoration-break:slice}"))
        self.assertNotIn("BODY_START", pages[0])
        self.assertIn("BODY_START", pages[1])


if __name__ == "__main__":
    unittest.main()
