package eapdf;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import digital.thinkbox.pdfsecurity.C2pa;
import eapdf.security.EaPdfC2paProfile;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.CancellationException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecurityValidatorTests {
  @TempDir Path temporary;

  @Test void securityWorkerReportsC2paWithoutConformanceAndPreservesBytes() throws Exception {
    Path input = c2paFile();
    byte[] before = Files.readAllBytes(input);
    var report = new SecurityValidatorEngine().verify(input, "", "");
    assertEquals("export-security", SecurityValidation.text(report, "validationTarget"));
    assertEquals("UNKNOWN", SecurityValidation.text(report, "status"));
    assertFalse(report.has("pdfa")); assertFalse(report.has("summary"));
    assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(before)),
        SecurityValidation.text(report, "fileSha256"));
    assertArrayEquals(before, Files.readAllBytes(input));
    var binding = report.getAsJsonObject("checks").getAsJsonObject("c2pa.binding");
    assertEquals("PASS", SecurityValidation.text(binding, "status"));
    assertEquals("C2PA", SecurityValidation.text(binding, "category"));
  }

  @Test void conformanceWindowWorkerSkipsSecurityEvenForC2pa() throws Exception {
    var result = new JavaValidatorEngine().validate(c2paFile());
    try {
      assertEquals("PASS", result.status(), result.json());
      assertEquals("PASS", result.pdfaStatus());
      Path saved = temporary.resolve("conformance.json"); result.save(saved);
      assertFalse(JsonParser.parseString(Files.readString(saved)).getAsJsonObject().has("security"));
      assertFalse(result.findings().stream().anyMatch(f -> "AF_IN_NAME_TREE".equals(f.code()) && "FAIL".equals(f.level())));
    } finally { result.close(); }
  }

  @Test void unsignedMalformedAndRequiredProtectionNeverBecomeSecurityPasses() throws Exception {
    Path input = temporary.resolve("unsigned.pdf");
    try (var document = new PDDocument()) { document.addPage(new PDPage()); document.save(input.toFile()); }
    var engine = new SecurityValidatorEngine();
    assertEquals("NOT_APPLICABLE", SecurityValidation.text(engine.verify(input, "", ""), "status"));
    assertNotEquals("PASS", SecurityValidation.text(engine.verify(input, "", "PADES_B_LTA"), "status"));
    Files.writeString(input, "This is not a PDF");
    assertEquals("ERROR", SecurityValidation.text(engine.verify(input, "", ""), "status"));
  }

  @Test void stoppingBeforeLaunchDoesNotPoisonTheNextRun() throws Exception {
    var engine = new SecurityValidatorEngine();
    Path input = c2paFile();
    engine.cancel();
    assertThrows(CancellationException.class, () -> engine.verify(input, "", ""));
    assertEquals("UNKNOWN", SecurityValidation.text(engine.verify(input, "", ""), "status"));
  }

  private Path c2paFile() throws Exception {
    Path input = temporary.resolve("secured_1si.pdf");
    try (var document = Loader.loadPDF(Path.of("../examples/eml/eml_Agenda_and_timeline_slides_1si.pdf").toFile())) {
      C2pa.prepare(document, EaPdfC2paProfile.FORMAT);
      document.save(input.toFile());
    }
    C2pa.complete(input, null, null, EaPdfC2paProfile.FORMAT);
    return input;
  }
}
