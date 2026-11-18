"""Optional pinned-runtime cascade regressions, executed behind the worker guards."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
WORKER = ROOT / "src/main/resources/eapdf/weasyprint_worker.py"
PYTHON = os.environ.get("EAPDF_WEASYPRINT_PYTHON")

BOOTSTRAP = r'''
import hashlib, importlib.util, json, os, resource, signal, sys
from pathlib import Path
job = Path.cwd().resolve()
os.environ.update(HOME=str(job), TMPDIR=str(job), XDG_CACHE_HOME=str(job / "cache"))
resource.setrlimit(resource.RLIMIT_AS, (512 * 1024 * 1024,) * 2)
resource.setrlimit(resource.RLIMIT_FSIZE, (4 * 1024 * 1024,) * 2)
resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
resource.setrlimit(resource.RLIMIT_CPU, (10, 11))
signal.alarm(15)
spec = importlib.util.spec_from_file_location("worker", sys.argv[1])
worker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(worker)
import weasyprint
from weasyprint import HTML, CSS
from weasyprint.text.fonts import FontConfiguration
import weasyprint.css as cascade
assert weasyprint.__version__ == "70.0"
package_source = Path(cascade.__file__)
package_sha_before = hashlib.sha256(package_source.read_bytes()).hexdigest()
payload = json.loads(sys.argv[2])
'''

STYLE_HELPER = r'''
fetcher = worker.resource_fetcher({}, 1024 * 1024, [])
font_config = FontConfiguration()
def computed_color(case):
    markup = case.get("markup")
    if markup is None:
        markup = ("<html><head><style>" + case["author"] +
                  "</style></head><body><div id='wrap'><a id='probe' style='" +
                  case["inline"] + "'>Probe</a></div></body></html>")
    html = HTML(string=markup, url_fetcher=fetcher)
    user = [CSS(string=case["user"], url_fetcher=fetcher, font_config=font_config)] if case.get("user") else []
    styles = cascade.get_all_computed_styles(html, user_stylesheets=user,
        presentational_hints=True, font_config=font_config)
    target = next(element for element in html.etree_element.iter() if element.get("id") == "probe")
    color = styles(target)["color"]
    return [round(component, 6) for component in color.coordinates]
'''


@unittest.skipUnless(PYTHON, "Optional WeasyPrint runtime is not configured")
class WeasyPrintCascadeTests(unittest.TestCase):
    def run_guarded(self, body, payload):
        with tempfile.TemporaryDirectory(prefix="eapdf-weasy-cascade-") as temporary:
            environment = {"PATH": "/usr/bin:/bin", "LANG": "C.UTF-8",
                           "HOME": temporary, "TMPDIR": temporary, "XDG_CACHE_HOME": temporary}
            result = subprocess.run(
                [PYTHON, "-I", "-B", "-c", BOOTSTRAP + body, str(WORKER), json.dumps(payload)],
                cwd=temporary, env=environment, capture_output=True, text=True, timeout=20,
            )
        self.assertEqual(result.returncode, 0, result.stderr)
        return json.loads(result.stdout)

    def assert_cases(self, cases):
        body = r'''
worker.fix_inline_style_precedence()
guards = worker.sandbox(job)
''' + STYLE_HELPER + r'''
results = {case["name"]: computed_color(case) for case in payload}
print(json.dumps({"colors": results, "guards": guards,
    "package_unchanged": package_sha_before == hashlib.sha256(package_source.read_bytes()).hexdigest()}))
'''
        result = self.run_guarded(body, cases)
        self.assertGreaterEqual(result["guards"]["landlockAbi"], 3)
        self.assertTrue(result["guards"]["seccomp"])
        self.assertTrue(result["package_unchanged"], "Installed package source changed")
        for case in cases:
            with self.subTest(case=case["name"]):
                self.assertEqual(result["colors"][case["name"]], case["expected"])

    def test_inline_origin_importance_layer_and_source_order_matrix(self):
        blue, red = [0.0, 0.0, 1.0], [1.0, 0.0, 0.0]
        cases = [
            ("inline_over_id_type", "#wrap a{color:red}", "color:blue", "", blue),
            ("inline_over_repeated_ids", "#wrap#wrap#wrap a{color:red}", "color:blue", "", blue),
            ("author_important_over_normal_inline", "#wrap a{color:red!important}", "color:blue", "", red),
            ("important_inline_over_author_important", "#wrap a{color:red!important}", "color:blue!important", "", blue),
            ("normal_user_below_inline", "", "color:blue", "a{color:red}", blue),
            ("important_user_over_important_inline", "", "color:blue!important", "a{color:red!important}", red),
            ("normal_layer_below_inline", "@layer first{#wrap a{color:red}}", "color:blue", "", blue),
            ("important_layer_over_normal_inline", "@layer first{#wrap a{color:red!important}}", "color:blue", "", red),
            ("important_inline_over_important_layer", "@layer first{#wrap a{color:red!important}}", "color:blue!important", "", blue),
            ("duplicate_inline_last_wins", "", "color:red;color:blue", "", blue),
            ("inline_important_beats_later_normal", "", "color:red!important;color:blue", "", red),
        ]
        self.assert_cases([dict(name=name, author=author, inline=inline, user=user, expected=expected)
                           for name, author, inline, user, expected in cases])

    def test_inheritance_and_presentational_hints_keep_their_scope(self):
        self.assert_cases([
            {"name": "direct_child_rule_over_inherited_inline", "markup":
             "<style>a{color:blue}</style><div style='color:red'><a id='probe'>Probe</a></div>",
             "expected": [0.0, 0.0, 1.0]},
            {"name": "stylesheet_over_presentational_color", "markup":
             "<style>font{color:blue}</style><font id='probe' color='red'>Probe</font>",
             "expected": [0.0, 0.0, 1.0]},
        ])

    def test_genuine_hover_rules_remain_inactive_in_static_output(self):
        self.assert_cases([
            {"name": "hover_does_not_override_normal_link", "markup":
             "<style>a{color:blue}#wrap a:hover{color:red}</style><div id='wrap'><a id='probe'>Probe</a></div>",
             "expected": [0.0, 0.0, 1.0]},
            {"name": "hover_does_not_override_inline_link", "markup":
             "<style>#wrap a:hover{color:red!important}</style><div id='wrap'><a id='probe' style='color:blue'>Probe</a></div>",
             "expected": [0.0, 0.0, 1.0]},
        ])

    def test_unexpected_upstream_generator_shape_fails_closed(self):
        body = r'''
original = cascade.find_style_attributes
def changed(tree, presentational_hints=False, base_url=None):
    for specificity, element, declarations, base in original(tree, presentational_hints, base_url):
        if payload == "unexpected_specificity":
            yield (2, 0, 0), element, declarations, base
        else:
            yield specificity, element, declarations, base, "unexpected field"
cascade.find_style_attributes = changed
failure = None
try:
    worker.fix_inline_style_precedence()
except RuntimeError as error:
    failure = str(error)
if failure is None:
    worker.sandbox(job)
''' + "\n".join("    " + line for line in STYLE_HELPER.splitlines()) + r'''
    try:
        computed_color({"author": "", "inline": "color:blue", "name": "shape"})
    except RuntimeError as error:
        failure = str(error)
assert failure, "Unexpected upstream cascade generator was accepted"
print(json.dumps({"rejected": True, "message": failure,
    "package_unchanged": package_sha_before == hashlib.sha256(package_source.read_bytes()).hexdigest()}))
'''
        for shape in ("unexpected_specificity", "unexpected_field"):
            with self.subTest(shape=shape):
                result = self.run_guarded(body, shape)
                self.assertTrue(result["rejected"])
                self.assertTrue(result["package_unchanged"])


if __name__ == "__main__":
    unittest.main()
