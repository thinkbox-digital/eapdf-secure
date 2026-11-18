package eapdf;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs(OS.LINUX)
class EmbeddedWeasyPrintRuntimeTests {
  @TempDir Path directory;
  record Bundle(byte[] zip, JsonObject manifest) {}
  static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
  static Bundle bundle(Map<String, byte[]> data) throws Exception {
    var bytes = new ByteArrayOutputStream(); var files = new JsonObject();
    try (var zip = new ZipOutputStream(bytes)) {
      for (var item : data.entrySet()) {
        zip.putNextEntry(new ZipEntry(item.getKey())); zip.write(item.getValue()); zip.closeEntry();
        var file = new JsonObject(); file.addProperty("size", item.getValue().length); file.addProperty("sha256", sha(item.getValue())); file.addProperty("executable", item.getKey().endsWith("worker")); files.add(item.getKey(), file);
      }
    }
    byte[] archive = bytes.toByteArray(); var manifest = new JsonObject();
    manifest.addProperty("archiveSha256", sha(archive)); manifest.addProperty("version", "test-1"); manifest.add("files", files);
    return new Bundle(archive, manifest);
  }
  @Test void extractionIsPrivateExecutableVerifiedAndReusedWithoutReadingTheArchiveAgain() throws Exception {
    var bundle = bundle(Map.of("runtime/worker", "#!/bin/sh\nexit 0\n".getBytes(), "runtime/data", "data".getBytes()));
    Path cache = directory.resolve("cache"); Path installed = EmbeddedWeasyPrintRuntime.install(cache, bundle.manifest(), new ByteArrayInputStream(bundle.zip()));
    assertArrayEquals("data".getBytes(), Files.readAllBytes(installed.resolve("runtime/data")));
    assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(installed.resolve("runtime/worker")));
    assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(installed.resolve("runtime/data")));
    assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(installed));
    var stamp = Files.getLastModifiedTime(installed.resolve("runtime/worker"));
    var forbidden = new InputStream() { @Override public int read() { throw new AssertionError("Unnecessary extraction"); } };
    assertEquals(installed, EmbeddedWeasyPrintRuntime.install(cache, bundle.manifest(), forbidden));
    assertEquals(stamp, Files.getLastModifiedTime(installed.resolve("runtime/worker")));
  }
  @Test void checksumFailurePublishesNoCacheAndRemovesStaging() throws Exception {
    var bundle = bundle(Map.of("worker", "data".getBytes())); bundle.manifest().addProperty("archiveSha256", "0".repeat(64));
    Path cache = directory.resolve("cache");
    assertThrows(IOException.class, () -> EmbeddedWeasyPrintRuntime.install(cache, bundle.manifest(), new ByteArrayInputStream(bundle.zip())));
    try (var entries = Files.list(cache)) { assertTrue(entries.allMatch(p -> p.toString().endsWith(".lock"))); }
  }
  @Test void unsafeOrUndeclaredEntriesNeverEscapeTheCache() throws Exception {
    var traversal = bundle(Map.of("../outside", "data".getBytes()));
    assertThrows(IOException.class, () -> EmbeddedWeasyPrintRuntime.install(directory.resolve("cache"), traversal.manifest(), new ByteArrayInputStream(traversal.zip())));
    assertFalse(Files.exists(directory.resolve("outside")));
    var extra = bundle(Map.of("worker", "data".getBytes(), "extra", "data".getBytes())); extra.manifest().getAsJsonObject("files").remove("extra");
    assertThrows(IOException.class, () -> EmbeddedWeasyPrintRuntime.install(directory.resolve("cache"), extra.manifest(), new ByteArrayInputStream(extra.zip())));
  }
  @Test void aCorruptedOrLinkedCacheIsRepairedWithoutFollowingLinks() throws Exception {
    var bundle = bundle(Map.of("worker", "data".getBytes())); Path cache = directory.resolve("cache");
    Path installed = EmbeddedWeasyPrintRuntime.install(cache, bundle.manifest(), new ByteArrayInputStream(bundle.zip()));
    Path external = directory.resolve("outside"); Files.writeString(external, "outside");
    Files.delete(installed.resolve("worker")); Files.createSymbolicLink(installed.resolve("worker"), external);
    assertEquals(installed, EmbeddedWeasyPrintRuntime.install(cache, bundle.manifest(), new ByteArrayInputStream(bundle.zip())));
    assertEquals("outside", Files.readString(external)); assertFalse(Files.isSymbolicLink(installed.resolve("worker")));
    Files.writeString(installed.resolve("extra"), "unexpected");
    EmbeddedWeasyPrintRuntime.install(cache, bundle.manifest(), new ByteArrayInputStream(bundle.zip()));
    assertFalse(Files.exists(installed.resolve("extra")));
  }
}
