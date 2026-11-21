package eapdf;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import eapdf.security.SecurityVerification;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/** Read-only security worker. It does not run EA-PDF or PDF/A conformance checks. */
public final class SecurityValidation {
  private SecurityValidation() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 2) throw new IllegalArgumentException("Security worker requires an input and report path");
    JsonObject report = inspect(Path.of(args[0]), System.getenv("EAPDF_SECURITY_POLICY"),
        System.getenv("EAPDF_SECURITY_REQUIRED_PROTECTION"));
    Files.writeString(Path.of(args[1]), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    System.exit("PASS".equals(text(report, "status")) ? 0 : 2);
  }

  static JsonObject inspect(Path input, String policy, String required) throws Exception {
    Path source = input.toRealPath();
    var before = Files.readAttributes(source, java.nio.file.attribute.BasicFileAttributes.class);
    var digest = MessageDigest.getInstance("SHA-256");
    try (var stream = Files.newInputStream(source)) {
      byte[] bytes = new byte[1024 * 1024];
      for (int count; (count = stream.read(bytes)) != -1;) digest.update(bytes, 0, count);
    }
    JsonObject report = SecurityVerification.inspect(source, policy, required);
    var after = Files.readAttributes(source, java.nio.file.attribute.BasicFileAttributes.class);
    if (before.size() != after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime())
        || !java.util.Objects.equals(before.fileKey(), after.fileKey()))
      throw new java.io.IOException("The PDF changed during verification. Run verification again.");
    report.addProperty("schemaVersion", 1);
    report.addProperty("validationTarget", "export-security");
    report.addProperty("filename", source.getFileName().toString());
    report.addProperty("fileSize", after.size());
    report.addProperty("fileSha256", HexFormat.of().formatHex(digest.digest()));
    report.addProperty("checkedAt", Instant.now().toString());
    if ("PASS".equals(text(report, "status")) && SecurityVerification.unsuccessful(report))
      report.addProperty("status", "FAIL");
    if (report.has("checks")) for (var entry : report.getAsJsonObject("checks").entrySet())
      entry.getValue().getAsJsonObject().addProperty("category", category(entry.getKey(), report));
    return report;
  }

  static String category(String code, JsonObject report) {
    if (code.startsWith("c2pa.")) return "C2PA";
    if (code.startsWith("pdf.") || code.equals("requestedProtection"))
      return "PADES_B_LTA".equals(text(report, "requiredProtection")) ? "PAdES" : "PDF signature / CMS";
    return "Other checks";
  }

  static String text(JsonObject object, String name) {
    return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : "";
  }
}
