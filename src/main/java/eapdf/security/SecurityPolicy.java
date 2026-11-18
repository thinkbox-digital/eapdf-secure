package eapdf.security;

import com.google.gson.*;
import digital.thinkbox.pdfsecurity.CertificatePolicy;
import java.io.*;
import java.nio.file.*;
import java.security.cert.*;
import java.time.Instant;
import java.util.*;

/** Operator-selected, offline trust and revocation evidence; never selected by an input PDF. */
public final class SecurityPolicy {
  private SecurityPolicy() {}
  public static CertificatePolicy load(String configured) throws Exception {
    if (configured == null || configured.isBlank()) return CertificatePolicy.untrusted(Instant.now());
    Path path = Path.of(configured).toAbsolutePath().normalize();
    var json = JsonParser.parseString(new String(read(path, 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
    return load(json, path.getParent());
  }
  public static CertificatePolicy load(JsonObject selectedEvidence) throws Exception {
    return load(selectedEvidence, Path.of("").toAbsolutePath());
  }
  public static void validate(JsonObject json) throws IOException {
    // A companion signing JSON can be reused as trust evidence. Its signing/tool
    // settings never select this validator's policy or cause credentials to be read.
    var allowed = Set.of("trustCertificates", "supportingCertificates", "crls", "ocsps", "verificationTime",
        "protection", "pkcs12", "passphraseFile", "alias", "verapdf", "tsaUrl", "tsaPolicyOid");
    var unknown = new TreeSet<>(json.keySet()); unknown.removeAll(allowed);
    if (!unknown.isEmpty()) throw new IOException("Unknown security policy setting(s): " + String.join(", ", unknown));
  }
  private static CertificatePolicy load(JsonObject json, Path parent) throws Exception {
    validate(json);
    var factory = CertificateFactory.getInstance("X.509");
    Set<TrustAnchor> anchors = new HashSet<>(); List<X509Certificate> supporting = new ArrayList<>();
    for (String name : names(json, "trustCertificates")) try (var input = new ByteArrayInputStream(read(parent.resolve(name), 1024 * 1024))) {
      for (var certificate : factory.generateCertificates(input)) anchors.add(new TrustAnchor((X509Certificate) certificate, null));
    }
    for (String name : names(json, "supportingCertificates")) try (var input = new ByteArrayInputStream(read(parent.resolve(name), 1024 * 1024))) {
      for (var certificate : factory.generateCertificates(input)) supporting.add((X509Certificate) certificate);
    }
    List<X509CRL> crls = new ArrayList<>(); List<byte[]> ocsps = new ArrayList<>();
    for (String name : names(json, "crls")) try (var input = new ByteArrayInputStream(read(parent.resolve(name), 16 * 1024 * 1024))) {
      for (var crl : factory.generateCRLs(input)) crls.add((X509CRL) crl);
    }
    for (String name : names(json, "ocsps")) ocsps.add(read(parent.resolve(name), 1024 * 1024));
    if (anchors.size() > 256 || supporting.size() > 256 || crls.size() > 256) throw new IOException("Security evidence count limit");
    Instant at = json.has("verificationTime") ? Instant.parse(json.get("verificationTime").getAsString()) : Instant.now();
    return new CertificatePolicy(anchors, crls, at, ocsps, supporting);
  }
  private static List<String> names(JsonObject json, String key) throws IOException {
    if (!json.has(key)) return List.of();
    var array = json.getAsJsonArray(key); if (array.size() > 256) throw new IOException("Security evidence count limit");
    List<String> names = new ArrayList<>();
    for (var item : array) { String name = item.getAsString(); if (name.isBlank()) throw new IOException("Empty evidence path"); names.add(name); }
    return names;
  }
  private static byte[] read(Path path, int limit) throws IOException {
    try (var input = Files.newInputStream(path)) { byte[] bytes = input.readNBytes(limit + 1); if (bytes.length > limit) throw new IOException("Security evidence byte limit"); return bytes; }
  }
}
