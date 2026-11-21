package eapdf;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Security parsing runs in a disposable JVM, independently of either desktop window. */
final class SecurityValidatorEngine implements SecurityValidatorGui.Engine {
  private static final class Run {
    final AtomicBoolean stopped = new AtomicBoolean();
    volatile Process process;
    void check() { if (stopped.get()) throw new CancellationException("Security verification stopped."); }
  }
  private final AtomicReference<Run> current = new AtomicReference<>(new Run());

  @Override public JsonObject verify(Path input, String policy, String protection) throws Exception {
    Run run = current.get();
    Path directory = null;
    try {
      run.check(); directory = ValidationRuntime.temporaryDirectory();
      Path output = directory.resolve("security.json"), diagnostic = directory.resolve("diagnostics.txt");
      long heap = Math.min(Runtime.getRuntime().maxMemory() / (1024 * 1024), ValidationRuntime.workerHeapMiB());
      var command = List.of(Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString(),
          "-Xmx" + heap + "m", "-XX:MaxDirectMemorySize=128m", "-XX:MaxMetaspaceSize=256m",
          "-XX:ReservedCodeCacheSize=128m", "-XX:ActiveProcessorCount=4", "-Djava.awt.headless=true",
          "-Djava.io.tmpdir=" + directory, "-cp", System.getProperty("java.class.path"),
          SecurityValidation.class.getName(), input.toRealPath().toString(), output.toString());
      var builder = new ProcessBuilder(ValidationRuntime.boundedCommand(command, heap));
      builder.environment().put("EAPDF_SECURITY_POLICY", policy);
      builder.environment().put("EAPDF_SECURITY_REQUIRED_PROTECTION", protection);
      builder.redirectOutput(diagnostic.toFile()).redirectErrorStream(true);
      run.check(); run.process = builder.start();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(ValidationRuntime.timeoutSeconds(Files.size(input)));
      while (!run.process.waitFor(200, TimeUnit.MILLISECONDS)) {
        run.check();
        if (System.nanoTime() > deadline) throw new IOException("Security verification exceeded its time limit.");
        if (ValidationRuntime.scratchExceeded(directory, output)) throw new IOException("Security verification exceeded its working-space budget.");
      }
      run.check();
      if (ValidationRuntime.scratchExceeded(directory, output))
        throw new IOException("Security verification exceeded its working-space budget.");
      if (!Files.isRegularFile(output) || !List.of(0, 2).contains(run.process.exitValue())) {
        String detail;
        try (var stream = Files.newInputStream(diagnostic)) { detail = new String(stream.readNBytes(4096), java.nio.charset.StandardCharsets.UTF_8); }
        throw new IOException("Security worker could not finish. " + detail);
      }
      byte[] bytes;
      try (var stream = Files.newInputStream(output)) { bytes = stream.readNBytes(4 * 1024 * 1024 + 1); }
      if (bytes.length > 4 * 1024 * 1024) throw new IOException("Security report exceeds its byte limit.");
      var result = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
      if (!List.of("PASS", "FAIL", "UNKNOWN", "ERROR", "NOT_APPLICABLE").contains(SecurityValidation.text(result, "status"))
          || !"export-security".equals(SecurityValidation.text(result, "validationTarget")))
        throw new IOException("Security worker returned an unrecognized report.");
      if (run.process.exitValue() != ("PASS".equals(SecurityValidation.text(result, "status")) ? 0 : 2))
        throw new IOException("Security worker exit status contradicts its report.");
      return result;
    } finally {
      try {
        Process process = run.process;
        if (process != null) {
          var children = process.descendants().toList(); children.forEach(ProcessHandle::destroy);
          if (process.isAlive()) { process.destroy(); if (!process.waitFor(2, TimeUnit.SECONDS)) { process.destroyForcibly(); process.waitFor(); } }
          children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        }
      } finally {
        try { ValidationRuntime.removeDirectory(directory); }
        finally { current.compareAndSet(run, new Run()); }
      }
    }
  }

  @Override public void cancel() {
    Run run = current.get(); run.stopped.set(true);
    Process process = run.process; if (process != null) process.destroy();
  }
}
