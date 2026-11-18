package eapdf;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WeasyPrintConfigurationTests {
  @TempDir Path temporary;

  @Test void defaultAndExplicitEngineRemainDistinct() throws Exception {
    Path config = temporary.resolve("config.json");
    Files.writeString(config, "{}");
    assertEquals("weasyprint", EapdfConfig.load(config).rendering.engine);
    Files.writeString(config, "{\"rendering\":{\"engine\":\"openhtmltopdf\"}}");
    assertEquals("openhtmltopdf", EapdfConfig.load(config).rendering.engine);
    for (String json : new String[]{"{\"rendering\":{\"engine\":\"typo\"}}", "{\"rendering\":{\"maxProcesses\":0}}",
        "{\"rendering\":{\"timeoutSeconds\":0}}", "{\"rendering\":{\"memoryMiB\":1}}", "{\"rendering\":{\"maxOutputMiB\":0}}"}) {
      Files.writeString(config, json);
      assertThrows(RuntimeException.class, () -> EapdfConfig.load(config));
    }
  }

  @Test void pythonResolutionHonorsExplicitConfigThenEnvironmentThenLocalVenv() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"));
    Path configured = temporary.resolve("configured-python"), environment = temporary.resolve("environment-python");
    Path local = temporary.resolve(".venv-weasyprint/bin/python");
    Files.createDirectories(local.getParent());
    for (Path executable : new Path[]{configured, environment, local}) {
      Files.writeString(executable, "#!/bin/sh\nexit 0\n");
      Files.setPosixFilePermissions(executable, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
    }
    assertEquals(configured, WeasyPrintRenderer.resolvePython(configured.toString(), environment.toString(), temporary));
    assertEquals(environment, WeasyPrintRenderer.resolvePython("", environment.toString(), temporary));
    assertEquals(local, WeasyPrintRenderer.resolvePython("", "", temporary));
    assertThrows(Common.UserFacingException.class, () -> WeasyPrintRenderer.resolvePython("relative-python", environment.toString(), temporary));
    assertThrows(Common.UserFacingException.class, () -> WeasyPrintRenderer.resolvePython("invalid\u0000python", environment.toString(), temporary));
    assertThrows(Common.UserFacingException.class, () -> WeasyPrintRenderer.resolvePython(temporary.resolve("missing").toString(), environment.toString(), temporary));
    Files.delete(local);
    assertThrows(Common.UserFacingException.class, () -> WeasyPrintRenderer.resolvePython("", "", temporary));
  }

  @Test void packagedWorkerIsResolvedBesideTheApplicationAndRejectsStalePayloads() throws Exception {
    Path app = temporary.resolve("relocated application");
    Path runtime = app.resolve("runtime/weasyprint/linux-x86_64");
    assertNull(WeasyPrintRenderer.bundledWorker(app, "amd64"));
    Files.createDirectories(runtime);
    Path executable = runtime.resolve("eapdf-weasyprint-worker");
    Files.writeString(executable, "test executable"); executable.toFile().setExecutable(true);
    String digest;
    try (var source = WeasyPrintRenderer.class.getResourceAsStream("/eapdf/weasyprint_worker.py")) {
      digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(source.readAllBytes()));
    }
    var manifest = new com.google.gson.JsonObject();manifest.addProperty("weasyprint", "70.0");
    manifest.addProperty("platform", "linux-x86_64");manifest.addProperty("workerSha256", digest);
    Files.writeString(runtime.resolve("runtime-manifest.json"), manifest.toString());
    assertEquals(executable, WeasyPrintRenderer.bundledWorker(app, "amd64"));
    manifest.addProperty("workerSha256", "0".repeat(64));
    Files.writeString(runtime.resolve("runtime-manifest.json"), manifest.toString());
    assertThrows(Common.UserFacingException.class, () -> WeasyPrintRenderer.bundledWorker(app, "amd64"));
  }

  @Test @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
  void wallTimeoutKillsWorkerAndRemovesItsStaging() throws Exception {
    Path executable = temporary.resolve("trusted-test-python"), observed = temporary.resolve("worker-path.txt");
    String quoted = "'" + observed.toString().replace("'", "'\"'\"'") + "'";
    Files.writeString(executable, "#!/bin/sh\nprintf '%s\\n%s\\n' \"$$\" \"$2\" > " + quoted + "\nexec /bin/sleep 30\n");
    Files.setPosixFilePermissions(executable, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
    var config = EapdfConfig.get(); var previous = config.rendering;
    config.rendering = new EapdfConfig.RenderingConfig(); config.rendering.engine = "weasyprint";
    config.rendering.pythonExecutable = executable.toString(); config.rendering.timeoutSeconds = 3;
    config.rendering.memoryMiB = 128;
    try {
      var record = AttachmentProfilePolicyTests.record("Timeout boundary");
      var failure = assertThrows(Common.UserFacingException.class, () -> PdfMailWriter.renderHtmlPdf(
          "<html><body>Timeout boundary</body></html>", "", TestSupport.fontPath(), record, false));
      assertTrue(failure.getMessage().contains("timeoutSeconds"));
      assertTrue(Files.exists(observed), "The trusted test worker must start before the timeout");
      if (Files.exists(observed)) {
        var lines = Files.readAllLines(observed);
        assertFalse(ProcessHandle.of(Long.parseLong(lines.getFirst())).map(ProcessHandle::isAlive).orElse(false));
        assertFalse(Files.exists(Path.of(lines.get(1)).getParent()));
      }
      assertTrue(record.conversionWarnings.stream().noneMatch(w -> w.contains("Reduced rendering")));
    } finally { config.rendering = previous; }
  }

  @Test void missingPythonFailsWithoutPlainTextFallback() throws Exception {
    var config = EapdfConfig.get(); var previous = config.rendering;
    var record = AttachmentProfilePolicyTests.record("Strict renderer failure");
    config.rendering = new EapdfConfig.RenderingConfig(); config.rendering.engine = "weasyprint";
    config.rendering.pythonExecutable = temporary.resolve("missing-python").toString();
    try {
      var error = assertThrows(Common.UserFacingException.class, () -> PdfMailWriter.renderHtmlPdf(
          "<html><body>Retain HTML</body></html>", "", TestSupport.fontPath(), record, false));
      assertTrue(error.getMessage().contains("pythonExecutable") || error.getMessage().contains("Linux"));
      assertTrue(record.conversionWarnings.stream().noneMatch(w -> w.contains("Reduced rendering")));
    } finally { config.rendering = previous; }
  }
}
