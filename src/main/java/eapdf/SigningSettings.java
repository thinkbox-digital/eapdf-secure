package eapdf;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Import only the operator-selected file; retain its values, never its filename. */
final class SigningSettings {
  private SigningSettings() {}
  static EapdfConfig.SigningConfig read(Path selected, EapdfConfig.SigningConfig current) throws Exception {
    Path source = selected.toAbsolutePath().normalize(); Path parent = source.getParent();
    JsonObject root = json(source), data = root.has("signing") ? root.getAsJsonObject("signing") : root;
    var allowed = Set.of("enabled", "pkcs12", "pkcs12Path", "passphraseFile", "alias", "reason", "c2pa",
        "protection", "policyPath", "trustPolicy", "tsaUrl", "tsaPolicyOid", "verapdf",
        "trustCertificates", "supportingCertificates", "crls", "ocsps", "verificationTime");
    var unknown = new TreeSet<>(data.keySet()); unknown.removeAll(allowed);
    if (!unknown.isEmpty()) throw new IOException("Unknown signing setting(s): " + String.join(", ", unknown));
    var result = new Gson().fromJson(new Gson().toJson(current), EapdfConfig.SigningConfig.class);
    if (data.has("pkcs12Path") || data.has("pkcs12")) result.pkcs12Path = path(parent, string(data, data.has("pkcs12Path") ? "pkcs12Path" : "pkcs12"));
    if (data.has("passphraseFile")) result.passphraseFile = path(parent, string(data, "passphraseFile"));
    if (data.has("alias")) result.alias = string(data, "alias");
    if (data.has("reason")) result.reason = string(data, "reason");
    if (data.has("protection")) result.protection = string(data, "protection");
    digital.thinkbox.pdfsecurity.PdfSecurity.Protection.valueOf(result.protection);
    if (data.has("tsaUrl")) result.tsaUrl = string(data, "tsaUrl");
    if (data.has("tsaPolicyOid")) result.tsaPolicyOid = string(data, "tsaPolicyOid");
    if (data.has("c2pa")) result.c2pa = bool(data, "c2pa");
    if ("PADES_B_LTA".equals(result.protection)) result.c2pa = true;
    if (data.has("enabled")) result.enabled = bool(data, "enabled");
    if (!"CMS_AND_PDF".equals(result.protection) && Common.nz(result.tsaUrl).isBlank()) result.tsaUrl = EapdfConfig.SigningConfig.DEFAULT_TSA_URL;
    JsonObject evidence = data;
    if (data.has("trustPolicy")) evidence = data.getAsJsonObject("trustPolicy");
    else if (data.has("policyPath") && !string(data, "policyPath").isBlank()) {
      Path policy = parent.resolve(string(data, "policyPath")).normalize(); evidence = json(policy); parent = policy.getParent();
    }
    var imported = new JsonObject();
    for (String key : List.of("trustCertificates", "supportingCertificates", "crls", "ocsps")) if (evidence.has(key)) {
      var entries = evidence.getAsJsonArray(key); if (entries.size() > 256) throw new IOException("Security evidence count limit");
      var resolved = new JsonArray();
      for (var entry : entries) {
        if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) throw new IOException("Evidence paths must be strings");
        resolved.add(path(parent, entry.getAsString()));
      }
      imported.add(key, resolved);
    }
    if (evidence.has("verificationTime")) imported.addProperty("verificationTime", string(evidence, "verificationTime"));
    if (!imported.isEmpty()) { result.trustPolicy = imported; result.policyPath = ""; }
    return result;
  }
  private static String path(Path parent, String value) { return value.isBlank() ? "" : parent.resolve(value).toAbsolutePath().normalize().toString(); }
  private static String string(JsonObject object, String key) throws IOException {
    var value = object.get(key);
    if (value == null || value.isJsonNull()) return "";
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IOException(key + " must be a string");
    return value.getAsString();
  }
  private static boolean bool(JsonObject object, String key) throws IOException {
    var value = object.get(key);
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IOException(key + " must be a boolean");
    return value.getAsBoolean();
  }
  private static JsonObject json(Path path) throws IOException {
    try (var input = Files.newInputStream(path)) {
      byte[] bytes = input.readNBytes(1024 * 1024 + 1);
      if (bytes.length > 1024 * 1024) throw new IOException("Signing JSON exceeds the size limit");
      return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }
  }
}
