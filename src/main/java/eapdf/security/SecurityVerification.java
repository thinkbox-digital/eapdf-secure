package eapdf.security;

import com.google.gson.*;
import digital.thinkbox.pdfsecurity.*;
import java.nio.file.Path;
import java.util.*;
import org.apache.pdfbox.Loader;

/** Read-only security assessment, separate from EA-PDF and PDF/A conformance. */
public final class SecurityVerification {
  private SecurityVerification() {}
  public static boolean unsuccessful(JsonObject result) {
    return result != null && (Set.of("FAIL", "ERROR").contains(result.get("status").getAsString())
        || result.has("requestedProtectionMet") && !result.get("requestedProtectionMet").getAsBoolean());
  }
  public static JsonObject inspect(Path path) {
    String policy = System.getProperty("eapdf.security.policy", System.getenv("EAPDF_SECURITY_POLICY"));
    String required = System.getProperty("eapdf.security.requiredProtection", System.getenv("EAPDF_SECURITY_REQUIRED_PROTECTION"));
    return inspect(path, policy, required);
  }
  public static JsonObject inspect(Path path, String policyPath, String required) {
    var out = new JsonObject(); out.addProperty("status", "ERROR");
    out.addProperty("scope", "Export signature and provenance; email-source authentication is not established");
    try {
      boolean c2pa, signed, archival, supported;
      try (var document = Loader.loadPDF(path.toFile(), org.apache.pdfbox.io.IOUtils.createTempFileOnlyStreamCache())) {
        c2pa = C2pa.hasCredentials(document);
        var signatures = document.getSignatureDictionaries(); signed = !signatures.isEmpty();
        archival = signatures.stream().anyMatch(s -> "DocTimeStamp".equals(s.getCOSObject().getNameAsString(org.apache.pdfbox.cos.COSName.TYPE)));
        supported = !c2pa || !EaPdfC2paProfile.artifacts(document).isEmpty();
      }
      if (!signed && !c2pa && (required == null || required.isBlank())) {
        out.addProperty("status", "NOT_APPLICABLE"); out.addProperty("message", "No export signature or C2PA credentials."); return out;
      }
      if (!supported) {
        if (required != null && !required.isBlank()) out.addProperty("requestedProtectionMet", false);
        out.addProperty("status", "UNKNOWN"); out.addProperty("message", "C2PA is present with an unsupported application profile; no security pass is asserted."); return out;
      }
      var policy = SecurityPolicy.load(policyPath);
      Map<String, SecurityReport.Check> checks;
      if (!signed && c2pa && (required == null || required.isBlank())) checks = C2pa.verify(path, EaPdfC2paProfile.FORMAT);
      else {
        var protection = required == null || required.isBlank() ? archival ? PdfSecurity.Protection.PADES_B_LTA
            : PdfSecurity.Protection.CMS_AND_PDF : PdfSecurity.Protection.valueOf(required);
        out.addProperty("requiredProtection", protection.name());
        var result = PdfSecurity.verify(path, protection, policy, c2pa ? EaPdfC2paProfile.FORMAT : null, null);
        checks = result.checks(); out.addProperty("requestedProtectionMet", result.valid());
        out.addProperty("padesBLta", result.lta());
        if (result.bestSignatureTime() != null) out.addProperty("bestSignatureTime", result.bestSignatureTime().toString());
      }
      boolean failed = checks.values().stream().anyMatch(c -> c.status() == SecurityReport.Status.FAIL);
      boolean unknown = checks.values().stream().anyMatch(c -> c.status() == SecurityReport.Status.UNKNOWN);
      out.addProperty("status", failed ? "FAIL" : unknown ? "UNKNOWN" : "PASS");
      // A missing local trust policy cannot erase a failed digest, signature or revision check.
      boolean integrityFailed = checks.entrySet().stream().anyMatch(e -> e.getValue().status() == SecurityReport.Status.FAIL
          && (e.getKey().startsWith("c2pa.") || Set.of("pdf.cryptographicSignature", "pdf.allowedChanges",
              "pdf.coverage", "pdf.structure").contains(e.getKey())));
      if (archival && policy.anchors().isEmpty() && !integrityFailed) {
        out.addProperty("status", "UNKNOWN");
        out.addProperty("message", "PAdES B-LTA requires an independently selected trust/revocation policy; inspect individual checks.");
      }
      var json = new Gson().toJsonTree(checks); out.add("checks", json);
      out.addProperty("sourceAuthentication", "NOT_ESTABLISHED");
    } catch (Exception error) { out.addProperty("message", "Security verification could not complete: " + error.getMessage()); }
    return out;
  }
}
