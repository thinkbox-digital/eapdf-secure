package eapdf;

import static org.junit.jupiter.api.Assertions.*;
import digital.thinkbox.pdfsecurity.*;
import digital.thinkbox.pdfsecurity.testing.*;
import eapdf.security.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EaPdfSecurityTests {
  @TempDir Path directory;

  static Path example(String profile) {
    return Path.of("examples/eml/eml_Agenda_and_timeline_slides" + (profile.startsWith("s") ? "" : "_archive") + "_1" + profile + ".pdf");
  }

  @Test void allProfilesPreservePagesMetadataAndEmbeddedSourcesAcrossEveryProtectionLevel() throws Exception {
    var config = EapdfConfig.get(); var original = config.signing;
    var institution = SecurityFixtures.institution(directory.resolve("identity"), false, true);
    var timestamp = TestTimestampService.client(institution, TestTimestampService.service(institution, ""));
    String evidence = System.getProperty("eapdf.securityFixtures");
    if (evidence != null) {
      Path destination = Path.of(evidence); Files.createDirectories(destination);
      Files.writeString(destination.resolve("test-root.pem"), SecurityFixtures.pem(institution.root()));
      Files.write(destination.resolve("test-root.crl"), SecurityFixtures.crl(institution, false).getEncoded());
      Files.writeString(destination.resolve("test-policy.json"), "{\"trustCertificates\":[\"test-root.pem\"],\"crls\":[\"test-root.crl\"],\"verificationTime\":\"" + institution.now() + "\"}");
    }
    try (var identity = institution.identity()) {
      config.signing = new EapdfConfig.SigningConfig(); config.signing.c2pa = true;
      for (var level : PdfSecurity.Protection.values()) for (String profile : List.of("s", "si", "m", "mi", "c", "ci")) {
        Path input = example(profile), output = directory.resolve(level + "_1" + profile + ".pdf");
        byte[] source = Files.readAllBytes(input); Files.copy(input, output);
        EaPdfSecurity.prepareConfigured(output); config.signing.protection = level.name();
        EaPdfSecurity.protect(output, identity, institution.policy(false),
            level == PdfSecurity.Protection.CMS_AND_PDF ? null : timestamp,
            Clock.fixed(institution.now(), ZoneOffset.UTC), EaPdfC2paProfile.FORMAT);
        var result = PdfSecurity.verify(output, level, institution.policy(false), EaPdfC2paProfile.FORMAT, identity.certificateSha256());
        assertTrue(result.valid(), () -> output + ": " + result.checks());
        assertEquals(SecurityReport.Status.PASS, result.checks().get("c2pa.binding").status());
        PdfCreationValidator.validate(output);
        try (var before = Loader.loadPDF(input.toFile()); var after = Loader.loadPDF(output.toFile())) {
          assertEquals(before.getNumberOfPages(), after.getNumberOfPages());
          assertEquals(new PDFTextStripper().getText(before), new PDFTextStripper().getText(after));
          assertArrayEquals(before.getDocumentCatalog().getMetadata().toByteArray(), after.getDocumentCatalog().getMetadata().toByteArray());
          assertEquals(2, EaPdfC2paProfile.artifacts(after).size());
          var a = C2pa.files(before); var b = C2pa.files(after);
          assertEquals(a.size() + 2, b.size());
          for (var entry : a.entrySet()) {
            assertTrue(b.containsKey(entry.getKey()));
            assertArrayEquals(entry.getValue().getEmbeddedFile().toByteArray(), b.get(entry.getKey()).getEmbeddedFile().toByteArray());
          }
        }
        assertArrayEquals(source, Files.readAllBytes(input));
        if (level == PdfSecurity.Protection.PADES_B_LTA && profile.equals("c")) {
          byte[] prefix = Files.readAllBytes(output); Path renewed = directory.resolve("renewed_1c.pdf");
          EaPdfSecurity.renew(output, renewed, identity, institution.policy(false), timestamp, Clock.fixed(institution.now(), ZoneOffset.UTC));
          if (Files.getFileStore(renewed).supportsFileAttributeView("posix"))
            assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(renewed));
          assertArrayEquals(prefix, Arrays.copyOf(Files.readAllBytes(renewed), prefix.length));
          assertTrue(PdfSecurity.verify(renewed, level, institution.policy(false), EaPdfC2paProfile.FORMAT, identity.certificateSha256()).valid());
          if (evidence != null) Files.copy(renewed, Path.of(evidence).resolve("renewed_1c.pdf"), StandardCopyOption.REPLACE_EXISTING);
          Path tampered = directory.resolve("tampered_1c.pdf"); byte[] changed = prefix.clone(); changed[12] ^= 1;
          Files.write(tampered, changed);
          assertEquals("FAIL", SecurityVerification.inspect(tampered, "", "").get("status").getAsString(),
              "Missing external trust must not mask a changed signed byte");
        }
        if (evidence != null) { Files.createDirectories(Path.of(evidence)); Files.copy(output, Path.of(evidence).resolve(output.getFileName()), StandardCopyOption.REPLACE_EXISTING); }
      }
    } finally { config.signing = original; }
  }

  @Test void localCredentialsUsePublicationPipelineAndNeverAssertTrustedSourceIdentity() throws Exception {
    var config = EapdfConfig.get(); var original = config.signing;
    try {
      config.signing = new EapdfConfig.SigningConfig(); config.signing.c2pa = true;
      Path source = Path.of("in/Agenda and timeline slides.eml");
      Path second;
      try (var inputs = Files.list(Path.of("in"))) { second = inputs.filter(p -> p.toString().endsWith(".eml") && !p.equals(source)).sorted().findFirst().orElseThrow(); }
      var records = new ArrayList<EmailRecord>();
      for (Path path : List.of(source, second)) records.add(EmlReader.readRfc822Bytes(Files.readAllBytes(path), path.toString(), path.toString(), "", List.of(), false));
      Path output = directory.resolve("local_1m.pdf");
      PdfMailWriter.writeEaPdfArchive(output, records, TestSupport.fontPath(), TestSupport.iccPath(), false, directory, "m");
      PdfCreationValidator.validate(output, PreservationInventory.capture(records, "m"));
      if (Files.getFileStore(output).supportsFileAttributeView("posix"))
        assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(output));
      var result = SecurityVerification.inspect(output, "", "");
      assertEquals("UNKNOWN", result.get("status").getAsString(), result.toString());
      assertEquals("NOT_ESTABLISHED", result.get("sourceAuthentication").getAsString());
      assertEquals("PASS", result.getAsJsonObject("checks").getAsJsonObject("c2pa.binding").get("status").getAsString());
      try (var document = Loader.loadPDF(output.toFile())) { assertTrue(document.getSignatureDictionaries().isEmpty()); }
      byte[] bytes = Files.readAllBytes(output); bytes[12] ^= 1; Files.write(output, bytes);
      assertEquals("FAIL", SecurityVerification.inspect(output, "", "").get("status").getAsString());
    } finally { config.signing = original; }
  }

  @Test void failedTimestampLeavesStagedInputUnchangedAndDoesNotPublishRenewal() throws Exception {
    var config = EapdfConfig.get(); var original = config.signing;
    var institution = SecurityFixtures.institution(directory.resolve("identity"), false, true);
    try (var identity = institution.identity()) {
      config.signing = new EapdfConfig.SigningConfig(); config.signing.c2pa = true;
      config.signing.protection = "PADES_B_LTA";
      Path staged = directory.resolve("staged_1s.pdf"); Files.copy(example("s"), staged);
      EaPdfSecurity.prepareConfigured(staged); byte[] bytes = Files.readAllBytes(staged);
      var unavailable = new Rfc3161Client(request -> { throw new java.io.IOException("Synthetic timestamp failure"); },
          institution.policy(false), Clock.fixed(institution.now(), ZoneOffset.UTC), null);
      assertThrows(Exception.class, () -> EaPdfSecurity.protect(staged, identity, institution.policy(false), unavailable,
          Clock.fixed(institution.now(), ZoneOffset.UTC), EaPdfC2paProfile.FORMAT));
      assertArrayEquals(bytes, Files.readAllBytes(staged));
      try (var paths = Files.list(directory)) { assertFalse(paths.anyMatch(p -> p.getFileName().toString().startsWith(".eapdf-security-"))); }
      Path output = directory.resolve("renewal_1s.pdf");
      assertThrows(Exception.class, () -> EaPdfSecurity.renew(staged, output, identity, institution.policy(false), unavailable, Clock.systemUTC()));
      assertFalse(Files.exists(output));
    } finally { config.signing = original; }
  }

  @Test void securityMetadataCannotHideAdditionalPayloadsOrReplaceNativeAttachments() throws Exception {
    var config = EapdfConfig.get(); var original = config.signing;
    try {
      config.signing = new EapdfConfig.SigningConfig(); config.signing.c2pa = true;
      Path path = directory.resolve("prepared_1c.pdf"); Files.copy(example("c"), path);
      EaPdfSecurity.prepareConfigured(path);
      try (var document = Loader.loadPDF(path.toFile())) {
        assertEquals(2, EaPdfC2paProfile.artifacts(document).size());
        var spec = C2pa.files(document).get(EaPdfC2paProfile.CREDENTIALS).getCOSObject();
        var ef = spec.getCOSDictionary(org.apache.pdfbox.cos.COSName.EF);
        ef.setItem(org.apache.pdfbox.cos.COSName.getPDFName("DOS"), ef.getDictionaryObject(org.apache.pdfbox.cos.COSName.F));
        assertThrows(java.io.IOException.class, () -> EaPdfC2paProfile.artifacts(document));
        assertThrows(Exception.class, () -> PdfCreationValidator.validate(document));
      }
    } finally { config.signing = original; }
  }
}
