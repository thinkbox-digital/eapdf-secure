package eapdf;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Isolated default page renderer; final metadata, tagging and publication remain in Java. */
final class WeasyPrintRenderer {
  static final String VERSION = "70.0";
  private static final Gson JSON = new Gson();
  private static final Semaphore SLOTS = new Semaphore(EapdfConfig.get().rendering.maxProcesses, true);
  private static final AtomicLong RESERVED = new AtomicLong();
  private WeasyPrintRenderer() {}

  static byte[] render(String xhtml, String baseUri, String primaryFont, boolean fitWideContent) throws Exception {
    var config = EapdfConfig.get().rendering;
    if (!System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("linux"))
      throw Common.fail("WeasyPrint requires Linux Landlock and libseccomp; OpenHTMLtoPDF remains available.");
    String environment = System.getenv("EAPDF_WEASYPRINT_PYTHON");
    Path bundled = null;
    if (Common.nz(config.pythonExecutable).isBlank() && Common.nz(environment).isBlank()) {
      Path embedded = EmbeddedWeasyPrintRuntime.directory();
      bundled = bundledWorker(embedded == null ? applicationDirectory() : embedded, System.getProperty("os.arch"));
    }
    Path python = bundled == null ? resolvePython(config.pythonExecutable, environment, Path.of("")) : bundled;
    long started = System.nanoTime(), timeout = Duration.ofSeconds(config.timeoutSeconds).toNanos();
    boolean acquired = false;
    long reservation = config.memoryMiB * 1024L * 1024;
    try {
      while (!(acquired = SLOTS.tryAcquire(100, TimeUnit.MILLISECONDS))) {
        ProcessingLimits.checkCancelled();
        if (System.nanoTime() - started >= timeout) throw Common.fail("WeasyPrint timed out waiting for a renderer slot.");
      }
      // Python's native address-space/RSS is separate from the Java heap. Reserve
      // its full allowance before launch, including uncommitted future Java heap.
      synchronized (RESERVED) {
        long futureJavaHeap = Runtime.getRuntime().maxMemory()
            - (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        long available = ConversionMemory.availableBytes();
        if (available >= 0 && reservation + RESERVED.get() + futureJavaHeap > available)
          throw Common.fail("Insufficient memory for the WeasyPrint subprocess allowance; reduce rendering.memoryMiB, rendering.maxProcesses or the Java heap.");
        RESERVED.addAndGet(reservation);
      }
      try (AutoCloseable globalSlot = jobSlot(config.maxProcesses, started, timeout)) {
        return renderReserved(xhtml, baseUri, primaryFont, fitWideContent, started, timeout, config, python, bundled != null);
      }
      finally { RESERVED.addAndGet(-reservation); }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw Common.fail("WeasyPrint rendering was cancelled.");
    } catch (Common.UserFacingException failure) {
      throw failure;
    } catch (Exception error) {
      var failure = Common.fail("WeasyPrint rendering failed: " + Common.nz(error.getMessage()));
      failure.initCause(error);
      throw failure;
    } finally { if (acquired) SLOTS.release(); }
  }

  static Path resolvePython(String configured, String environment, Path workingDirectory) {
    String selected = configured == null || configured.isBlank() ? environment : configured;
    Path python;
    try {
      python = selected == null || selected.isBlank()
          ? workingDirectory.toAbsolutePath().resolve(".venv-weasyprint/bin/python") : Path.of(selected);
    } catch (java.nio.file.InvalidPathException invalid) {
      throw Common.fail("WeasyPrint rendering.pythonExecutable or EAPDF_WEASYPRINT_PYTHON contains an invalid executable path.");
    }
    if (!python.isAbsolute() || !Files.isRegularFile(python) || !Files.isExecutable(python))
      throw Common.fail("WeasyPrint runtime is missing. Use the platform's creator JAR with the included renderer. Developer overrides: rendering.pythonExecutable or EAPDF_WEASYPRINT_PYTHON. See docs/WEASYPRINT.md.");
    return python.normalize();
  }

  private static Path applicationDirectory() throws Exception {
    Path location = Path.of(WeasyPrintRenderer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    return Files.isDirectory(location) ? location : location.getParent();
  }

  static Path bundledWorker(Path applicationDirectory, String architecture) throws Exception {
    String platform = switch (architecture) {
      case "amd64", "x86_64" -> "linux-x86_64";
      case "aarch64", "arm64" -> "linux-aarch64";
      default -> throw Common.fail("No bundled WeasyPrint runtime for this architecture: " + architecture);
    };
    Path directory = applicationDirectory.resolve("runtime/weasyprint/" + platform);
    if (!Files.exists(directory)) return null;
    Path manifest = directory.resolve("runtime-manifest.json");
    if (!Files.isRegularFile(manifest) || Files.size(manifest) > 65536)
      throw Common.fail("Bundled WeasyPrint runtime is incomplete; reinstall the matching application bundle.");
    var info = JSON.fromJson(Files.readString(manifest), JsonObject.class);
    if (info == null || !info.has("weasyprint") || !info.has("platform") || !info.has("workerSha256"))
      throw Common.fail("Bundled WeasyPrint runtime manifest is invalid; reinstall the matching application bundle.");
    String expected;
    try (var source = WeasyPrintRenderer.class.getResourceAsStream("/eapdf/weasyprint_worker.py")) {
      if (source == null) throw new IOException("Missing renderer worker source");
      expected = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(source.readAllBytes()));
    }
    Path executable = directory.resolve("eapdf-weasyprint-worker");
    if (!VERSION.equals(info.get("weasyprint").getAsString()) || !platform.equals(info.get("platform").getAsString())
        || !expected.equals(info.get("workerSha256").getAsString()) || !Files.isRegularFile(executable)
        || !Files.isExecutable(executable))
      throw Common.fail("Bundled WeasyPrint runtime does not match this JAR; reinstall the matching application bundle.");
    return executable.toAbsolutePath().normalize();
  }

  private static byte[] renderReserved(String xhtml, String baseUri, String primaryFont, boolean fitWideContent,
      long started, long timeout, EapdfConfig.RenderingConfig config, Path python, boolean bundled) throws Exception {
    Path directory = Files.createTempDirectory("eapdf-weasy-");
    try (AutoCloseable cleanup = () -> Common.deleteTree(directory)) {
      EmailRecord record = MailRenderPolicy.ACTIVE_RENDER_RECORD.get();
      // Reuse authored font-family normalization and Unicode coverage diagnostics.
      String normalized = FontSupport.configure(new PdfRendererBuilder(), primaryFont, xhtml, record);
      var document = org.jsoup.Jsoup.parse(normalized, "", org.jsoup.parser.Parser.xmlParser());
      document.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml).prettyPrint(false);
      var htmlRoot = document.selectFirst("html");
      if (htmlRoot != null && htmlRoot.attr("lang").isBlank()) {
        String language = Common.nz(EapdfConfig.get().accessibility.language);
        htmlRoot.attr("lang", language.isBlank() ? "en" : language);
      }
      if (document.selectFirst("title") == null)
        document.head().appendElement("title").text(record == null || Common.nz(record.subject).isBlank() ? "Email" : Common.stripInvalidXml(record.subject));
      for (var image : document.select("img[src]")) {
        String resolved = MailRenderPolicy.REMOTE_ASSET_URI_RESOLVER.resolveURI(baseUri, image.attr("src"));
        if (resolved == null) image.removeAttr("src");
        else {
          image.attr("src", resolved);
          // WeasyPrint tags images even when optional Accessibility is off.
          // Its Figure tags require nonempty Alt, including for HTML alt="".
          if (image.attr("alt").isBlank()) {
            image.attr("alt", "Image from the original email");
            Common.warn(record, "An email image has no source alternative text; a generic description is used and should be reviewed for accessibility.");
          }
        }
      }
      Files.writeString(directory.resolve("in.html"), document.outerHtml(), StandardCharsets.UTF_8);
      var assets = new LinkedHashMap<String, String>();
      if (record != null) for (var asset : record.renderAssets.entrySet()) {
        MailRenderPolicy.checkRenderImage(asset.getValue());
        String name = "asset-" + assets.size() + ".bin";
        Files.write(directory.resolve(name), asset.getValue()); assets.put(asset.getKey(), name);
      }
      List<Map<String, Object>> fonts = stageFonts(directory, primaryFont,
          bundled ? python.getParent().resolve("_internal/fonts") : null);
      Map<String, Object> request = new LinkedHashMap<>();
      request.put("version", 1); request.put("memoryMiB", config.memoryMiB);
      request.put("timeoutSeconds", config.timeoutSeconds); request.put("maxOutputMiB", config.maxOutputMiB);
      request.put("maxResourceBytes", ProcessingLimits.messageBytes());
      request.put("maxPages", 10000); request.put("fitWideContent", fitWideContent);
      request.put("assets", assets); request.put("fonts", fonts);
      request.put("baseUri", Common.nz(baseUri));
      request.put("headerTagMarker", record == null ? "" : Common.nz(record.headerTagMarker));
      Files.writeString(directory.resolve("request.json"), JSON.toJson(request), StandardCharsets.UTF_8);
      try (var script = WeasyPrintRenderer.class.getResourceAsStream("/eapdf/weasyprint_worker.py")) {
        if (script == null) throw new IOException("Bundled WeasyPrint worker is missing");
        Files.copy(script, directory.resolve("worker.py"));
      }
      var command = bundled ? List.of(python.toString(), "request.json")
          : List.of(python.toString(), "-I", directory.resolve("worker.py").toString(), "request.json");
      var builder = new ProcessBuilder(command).directory(directory.toFile());
      builder.environment().clear();
      builder.environment().putAll(Map.of("PATH", "/usr/bin:/bin", "LANG", "C.UTF-8", "HOME", directory.toString(),
          "TMPDIR", directory.toString(), "XDG_CACHE_HOME", directory.toString()));
      Path log = directory.resolve("worker.log");
      builder.redirectErrorStream(true).redirectOutput(log.toFile());
      checkDeadline(started, timeout);
      Process process = builder.start();
      long peakRss = 0;
      try {
        while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
          ProcessingLimits.checkCancelled();
          if (System.nanoTime() - started >= timeout) throw Common.fail("WeasyPrint exceeded rendering.timeoutSeconds.");
          try { peakRss = Math.max(peakRss, residentBytes(process.pid())); }
          // /proc can report ESRCH as a plain IOException during process exit.
          catch (IOException ended) { if (!process.waitFor(100, TimeUnit.MILLISECONDS)) throw ended; }
          if (peakRss > config.memoryMiB * 1024L * 1024) throw Common.fail("WeasyPrint exceeded its Python memory allowance.");
          if (Files.size(log) > 1024 * 1024) throw Common.fail("WeasyPrint diagnostic output exceeded 1 MiB.");
        }
        checkDeadline(started, timeout);
        if (process.exitValue() != 0) throw Common.fail("WeasyPrint failed (exit " + process.exitValue() + "): " + readDiagnostic(log));
      } finally { ConversionSupervisor.stopTree(process); }
      Path pdf = directory.resolve("out.pdf"), metrics = directory.resolve("metrics.json");
      if (!Files.isRegularFile(pdf) || !Files.isRegularFile(metrics)) throw Common.fail("WeasyPrint produced incomplete output.");
      long bytes = Files.size(pdf);
      if (bytes > config.maxOutputMiB * 1024L * 1024) throw Common.fail("WeasyPrint output exceeded rendering.maxOutputMiB.");
      ProcessingLimits.checkOutput(bytes);
      long javaAvailable = Runtime.getRuntime().maxMemory()
          - (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
      if (bytes > javaAvailable / 3) throw Common.fail("WeasyPrint PDF exceeds the available Java buffer allowance.");
      if (Files.size(metrics) > 1024 * 1024) throw Common.fail("WeasyPrint metrics exceeded 1 MiB.");
      JsonObject measured = JSON.fromJson(Files.readString(metrics), JsonObject.class);
      if (!VERSION.equals(measured.get("version").getAsString())) throw Common.fail("WeasyPrint worker version mismatch.");
      System.out.println("[weasyprint] " + measured);
      if (record != null && measured.has("missingImagePlaceholderCount")
          && measured.get("missingImagePlaceholderCount").getAsInt() > 0)
        Common.warn(record, "Unavailable images use neutral placeholders; source alternative text and link targets are retained.");
      try (var rendered = IndexedPdfLoader.load(pdf)) { ProcessingLimits.accountPages(rendered.getNumberOfPages()); }
      checkDeadline(started, timeout);
      return Files.readAllBytes(pdf);
    }
  }

  private static List<Map<String, Object>> stageFonts(Path directory, String primary, Path bundledFonts) throws Exception {
    var paths = new LinkedHashMap<String, Path>(); paths.put("ArchiveFont", Path.of(primary));
    int index = 1;
    for (Path font : FontSupport.fallbacks(primary)) paths.put("ArchiveFallback" + index++, font);
    for (var entry : Map.of("ArchiveMono", "DejaVuSansMono.ttf", "ArchiveSerif", "LiberationSerif-Regular.ttf",
        "ArchiveSans", "DejaVuSans.ttf").entrySet()) {
      Path font = Common.bundledAssetPath("assets/" + entry.getValue(), ".ttf");
      if (font != null) paths.put(entry.getKey(), font);
    }
    var fonts = new ArrayList<Map<String, Object>>();
    var staged = new LinkedHashMap<Path, String>();
    for (var font : paths.entrySet()) {
      for (var face : WeasyFontFaces.variants(font.getValue(), bundledFonts)) {
        Path path = face.path().toAbsolutePath().normalize();
        String name = staged.get(path);
        if (name == null) {
          ProcessingLimits.checkFont(path);
          name = "font-" + staged.size() + ".ttf";
          Files.copy(path, directory.resolve(name)); staged.put(path, name);
        }
        fonts.add(Map.of("file", name, "family", font.getKey(), "weight", face.weight(), "style", face.style()));
      }
    }
    return fonts;
  }

  /** File locks share the process allowance across MBOX/PST child JVMs. */
  @SuppressWarnings("BusyWait") // FileLock has no timed wait-any API; polling preserves cancellation and the shared deadline.
  private static AutoCloseable jobSlot(int limit, long started, long timeout) throws Exception {
    String shared = System.getenv("EAPDF_WEASYPRINT_SLOT_ROOT");
    if (shared == null || shared.isBlank()) return () -> {};
    Path root = Path.of(shared);
    while (true) {
      ProcessingLimits.checkCancelled();
      for (int slot = 0; slot < limit; slot++) {
        var channel = java.nio.channels.FileChannel.open(root.resolve("slot-" + slot),
            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE);
        try {
          var lock = channel.tryLock();
          if (lock != null) return () -> { try { lock.release(); } finally { channel.close(); } };
        } catch (java.nio.channels.OverlappingFileLockException busy) {
          // Another renderer thread in this JVM owns the shared slot.
        } catch (Exception failure) { channel.close(); throw failure; }
        channel.close();
      }
      if (System.nanoTime() - started >= timeout) throw Common.fail("WeasyPrint timed out waiting for the whole-job process allowance.");
      Thread.sleep(50);
    }
  }

  static long residentBytes(long pid) throws IOException {
    for (String line : Files.readAllLines(Path.of("/proc", Long.toString(pid), "status")))
      if (line.startsWith("VmRSS:")) return Long.parseLong(line.substring(6).trim().split("\\s+")[0]) * 1024;
    return 0;
  }

  private static void checkDeadline(long started, long timeout) {
    ProcessingLimits.checkCancelled();
    if (System.nanoTime() - started >= timeout) throw Common.fail("WeasyPrint exceeded rendering.timeoutSeconds.");
  }

  private static String readDiagnostic(Path log) throws IOException {
    try (var input = Files.newInputStream(log)) {
      return new String(input.readNBytes(8192), StandardCharsets.UTF_8).strip();
    }
  }
}
