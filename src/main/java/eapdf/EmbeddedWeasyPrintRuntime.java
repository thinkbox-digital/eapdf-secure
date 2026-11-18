package eapdf;

import com.google.gson.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;

/** Installs trusted JAR resources once into a private cache; never reads PDF data. */
final class EmbeddedWeasyPrintRuntime {
  private static final long MAX_BYTES = 512L * 1024 * 1024;
  private static volatile Path installed;
  private EmbeddedWeasyPrintRuntime() {}

  static synchronized Path directory() throws Exception {
    if (installed != null) return installed;
    JsonObject manifest;
    try (var input = EmbeddedWeasyPrintRuntime.class.getResourceAsStream("/eapdf/runtime/bundle.json")) {
      if (input == null) return null;
      byte[] bytes = input.readNBytes(2 * 1024 * 1024 + 1);
      if (bytes.length > 2 * 1024 * 1024) throw new IOException("Embedded renderer manifest exceeds limit");
      manifest = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
    }
    String actual = switch (System.getProperty("os.arch")) {
      case "amd64", "x86_64" -> "linux-x86_64";
      case "aarch64", "arm64" -> "linux-aarch64";
      default -> "unsupported";
    };
    if (!actual.equals(manifest.get("platform").getAsString()))
      throw Common.fail("This JAR's included renderer is for " + manifest.get("platform").getAsString() + ". Use a matching platform build.");
    Path cache = Path.of(System.getProperty("user.home"), ".cache", "eapdf", "weasyprint");
    try (var archive = EmbeddedWeasyPrintRuntime.class.getResourceAsStream("/eapdf/runtime/runtime.zip")) {
      if (archive == null) throw new IOException("Embedded renderer archive is missing");
      installed = install(cache, manifest, archive);
    }
    return installed;
  }

  static synchronized Path install(Path cache, JsonObject manifest, InputStream archive) throws Exception {
    String hash = manifest.get("archiveSha256").getAsString(), version = manifest.get("version").getAsString();
    if (!hash.matches("[0-9a-f]{64}") || !version.matches("[A-Za-z0-9._-]{1,80}")) throw new IOException("Invalid renderer cache identity");
    var files = manifest.getAsJsonObject("files");
    if (files == null || files.isEmpty() || files.size() > 8192) throw new IOException("Invalid renderer file count");
    long total = 0;
    for (var entry : files.entrySet()) {
      safe(entry.getKey()); long length = entry.getValue().getAsJsonObject().get("size").getAsLong();
      if (length < 0 || length > MAX_BYTES || total > MAX_BYTES - length) throw new IOException("Renderer size limit");
      total += length;
      if (!entry.getValue().getAsJsonObject().get("sha256").getAsString().matches("[0-9a-f]{64}")) throw new IOException("Invalid renderer file checksum");
    }
    Files.createDirectories(cache.getParent()); privateDirectory(cache);
    Path target = cache.resolve(version + "-" + hash);
    Path lock = cache.resolve(hash + ".lock");
    if (Files.isSymbolicLink(lock)) throw new IOException("Renderer cache lock must not be a symbolic link");
    try (var channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
      Files.setPosixFilePermissions(lock, PosixFilePermissions.fromString("rw-------"));
      try (var ignored = channel.lock()) {
        if (valid(target, files)) return target;
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) Common.deleteTree(target);
        Path staging = Files.createTempDirectory(cache, ".extract-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        try {
          System.out.println("[renderer] Preparing the included renderer for first use...");
          Path zipped = Files.createTempFile(cache, ".renderer-", ".zip");
          try {
            var digest = MessageDigest.getInstance("SHA-256"); long count = 0;
            try (var output = Files.newOutputStream(zipped)) {
              byte[] buffer = new byte[65536];
              for (int n; (n = archive.read(buffer)) != -1;) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Renderer extraction cancelled");
                count += n; if (count > MAX_BYTES) throw new IOException("Embedded renderer archive exceeds limit");
                digest.update(buffer, 0, n); output.write(buffer, 0, n);
              }
            }
            if (!hash.equals(HexFormat.of().formatHex(digest.digest()))) throw new IOException("Embedded renderer archive checksum mismatch");
            var seen = new HashSet<String>();
            try (var zip = new ZipFile(zipped.toFile())) {
              var entries = zip.entries();
              while (entries.hasMoreElements()) {
                var entry = entries.nextElement(); String name = entry.getName(); safe(name);
                if (entry.isDirectory() || !seen.add(name) || !files.has(name)) throw new IOException("Unexpected renderer archive entry");
                var expected = files.getAsJsonObject(name); Path output = staging.resolve(name);
                Files.createDirectories(output.getParent());
                var checksum = MessageDigest.getInstance("SHA-256"); long length = 0;
                try (var input = zip.getInputStream(entry); var sink = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW)) {
                  byte[] buffer = new byte[65536];
                  for (int n; (n = input.read(buffer)) != -1;) {
                    length += n; if (length > expected.get("size").getAsLong()) throw new IOException("Renderer entry exceeds declared size");
                    checksum.update(buffer, 0, n); sink.write(buffer, 0, n);
                  }
                }
                if (length != expected.get("size").getAsLong() || !expected.get("sha256").getAsString().equals(HexFormat.of().formatHex(checksum.digest())))
                  throw new IOException("Renderer file checksum mismatch: " + name);
                Files.setPosixFilePermissions(output, PosixFilePermissions.fromString(expected.get("executable").getAsBoolean() ? "rwx------" : "rw-------"));
              }
            }
            if (seen.size() != files.size()) throw new IOException("Embedded renderer files are missing");
          } finally { Files.deleteIfExists(zipped); }
          try (var paths = Files.walk(staging)) {
            for (Path directory : paths.filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)).toList())
              Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
          }
          Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
          if (!valid(target, files)) throw new IOException("Extracted renderer verification failed");
          return target;
        } finally { Common.deleteTree(staging); }
      }
    }
  }

  private static void safe(String value) throws IOException {
    Path path = Path.of(value);
    if (value.isBlank() || value.indexOf('\\') >= 0 || path.isAbsolute() || !path.normalize().equals(path)
        || path.startsWith("..")) throw new IOException("Unsafe renderer archive path");
  }
  private static void privateDirectory(Path path) throws IOException {
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Renderer cache is not a private directory");
    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
  }
  private static boolean valid(Path root, JsonObject files) throws Exception {
    if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) return false;
    try (var paths = Files.walk(root)) {
      for (Path path : paths.toList()) {
        if (Files.isSymbolicLink(path)) return false;
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !files.has(root.relativize(path).toString().replace('\\', '/'))) return false;
      }
    }
    for (var entry : files.entrySet()) {
      Path file = root.resolve(entry.getKey());
      for (Path part = file; part.startsWith(root); part = part.getParent()) if (Files.isSymbolicLink(part)) return false;
      var expected = entry.getValue().getAsJsonObject();
      if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) != expected.get("size").getAsLong()) return false;
      try (var input = Files.newInputStream(file)) {
        var hash = MessageDigest.getInstance("SHA-256"); byte[] bytes = new byte[65536];
        for (int n; (n = input.read(bytes)) != -1;) hash.update(bytes, 0, n);
        if (!expected.get("sha256").getAsString().equals(HexFormat.of().formatHex(hash.digest()))) return false;
      }
      if (expected.get("executable").getAsBoolean() && !Files.isExecutable(file)) return false;
      var mode = Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS);
      if (mode.stream().anyMatch(p -> p.name().startsWith("GROUP_") || p.name().startsWith("OTHERS_"))) return false;
    }
    return true;
  }
}
