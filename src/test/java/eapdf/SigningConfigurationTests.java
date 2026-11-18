package eapdf;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import digital.thinkbox.pdfsecurity.testing.SecurityFixtures;
import eapdf.security.SecurityPolicy;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SigningConfigurationTests {
  @TempDir Path directory;

  @Test void explicitlySelectedJsonPrefillsValuesWithoutReadingSecretsOrRetainingItsPath() throws Exception {
    Path source = directory.resolve("selected-settings.json");
    Files.writeString(source, """
        {"protection":"PADES_B_LTA","pkcs12":"private/signer.p12",
         "passphraseFile":"private/password.txt","alias":"operator","tsaUrl":"",
         "trustCertificates":["root.pem"],"crls":["root.crl"],"ocsps":[],"supportingCertificates":[]}
        """);
    var imported = SigningSettings.read(source, new EapdfConfig.SigningConfig());
    assertEquals(directory.resolve("private/signer.p12").toString(), imported.pkcs12Path);
    assertEquals(directory.resolve("private/password.txt").toString(), imported.passphraseFile);
    assertEquals("operator", imported.alias); assertEquals("PADES_B_LTA", imported.protection);
    assertTrue(imported.c2pa);
    assertEquals("http://timestamp.digicert.com", imported.tsaUrl); assertEquals("", imported.policyPath);
    assertEquals(directory.resolve("root.pem").toString(), imported.trustPolicy.getAsJsonArray("trustCertificates").get(0).getAsString());
    Files.delete(source);
    String serialized = new Gson().toJson(imported);
    assertFalse(serialized.contains("selected-settings.json"));
    var defaults = new EapdfConfig(); imported.enabled = true;
    Path run = GuiRunConfiguration.write(defaults, false, false, imported);
    try {
      var loaded = EapdfConfig.load(run); assertNotNull(loaded.signing.trustPolicy);
      assertFalse(loaded.accessibility.addPdfUaFeatures); assertFalse(loaded.searchIndex.enabled);
    } finally { Files.delete(run); }
  }

  @Test void protectionLabelsUsePlainEnglishAndKeepStableCodes() {
    var labels = EapdfGui.protectionChoices();
    assertEquals("Sign the PDF (CMS_AND_PDF)", labels[0].toString());
    assertEquals("Sign with a trusted timestamp (CMS_AND_PDF_TIMESTAMPED)", labels[1].toString());
    assertEquals("Sign for long-term preservation (PADES_B_LTA)", labels[2].toString());
  }

  @Test void timestampedProtectionDefaultsToDigiCertAndRetainsAnExplicitOverride() {
    for (String level : List.of("CMS_AND_PDF", "CMS_AND_PDF_TIMESTAMPED", "PADES_B_LTA")) {
      var config = new EapdfConfig(); config.signing.enabled = true; config.signing.protection = level;
      EapdfConfig.normalize(config);
      assertEquals(level.equals("PADES_B_LTA"), config.signing.c2pa);
      assertEquals(level.equals("CMS_AND_PDF") ? "" : "http://timestamp.digicert.com", config.signing.tsaUrl);
      if (!level.equals("CMS_AND_PDF")) {
        config.signing.tsaUrl = "https://tsa.example.invalid/custom";
        assertEquals("https://tsa.example.invalid/custom", EapdfConfig.normalize(config).signing.tsaUrl);
      }
    }
  }

  @Test void companionSigningJsonCanSupplyTrustWithoutOpeningItsCredentialOrPassphrase() throws Exception {
    var institution = SecurityFixtures.institution(directory.resolve("identity"), false, true);
    Files.writeString(directory.resolve("root.pem"), SecurityFixtures.pem(institution.root()));
    var json = JsonParser.parseString("""
        {"protection":"PADES_B_LTA","pkcs12":"missing-secret.p12",
         "passphraseFile":"missing-secret.passphrase","alias":"institution",
         "trustCertificates":["root.pem"],"supportingCertificates":[],"crls":[],"ocsps":[],
         "verapdf":"missing-external-program","tsaUrl":"http://timestamp.digicert.com","tsaPolicyOid":""}
        """).getAsJsonObject();
    Path path = directory.resolve("combined.json"); Files.writeString(path, json.toString());
    assertEquals(1, SecurityPolicy.load(path.toString()).anchors().size());
    json.addProperty("trustCertificate", "typo.pem"); Files.writeString(path, json.toString());
    assertTrue(assertThrows(java.io.IOException.class, () -> SecurityPolicy.load(path.toString())).getMessage().contains("trustCertificate"));
  }

  @Test void passphraseFilePreservesSpacesAndHandlesUtf8AndFinalLineEndings() throws Exception {
    var cfg = new EapdfConfig.SigningConfig(); Path file = directory.resolve("test.passphrase"); cfg.passphraseFile = file.toString();
    for (String ending : List.of("", "\n", "\r\n")) {
      Files.writeString(file, "  synthetic-密碼  " + ending);
      char[] password = PdfSigning.password(cfg, null);
      try { assertArrayEquals("  synthetic-密碼  ".toCharArray(), password); }
      finally { Arrays.fill(password, '\0'); }
    }
    Files.write(file, new byte[0]); assertEquals(0, PdfSigning.password(cfg, null).length);
  }

  @Test void environmentAndPromptOverrideTheFileWithoutReadingIt() throws Exception {
    var cfg = new EapdfConfig.SigningConfig(); cfg.passphraseFile = directory.resolve("absent.passphrase").toString();
    assertArrayEquals("override".toCharArray(), PdfSigning.password(cfg, "override"));
    assertEquals(0, PdfSigning.password(cfg, "").length);
  }

  @Test void oversizeOrInvalidUtf8PassphrasesAreRejectedWithoutPrintingTheirContents() throws Exception {
    var cfg = new EapdfConfig.SigningConfig(); Path file = directory.resolve("test.passphrase"); cfg.passphraseFile = file.toString();
    Files.write(file, new byte[4097]);
    assertThrows(java.io.IOException.class, () -> PdfSigning.password(cfg, null));
    Files.write(file, new byte[]{(byte) 0xc3, 0x28});
    assertThrows(java.nio.charset.CharacterCodingException.class, () -> PdfSigning.password(cfg, null));
  }

  @Test void filePasswordOpensTheSamePkcs12ForLegacyAndCoordinatedSigning() throws Exception {
    var institution = SecurityFixtures.institution(directory.resolve("identity"), false, true);
    var cfg = new EapdfConfig.SigningConfig(); cfg.pkcs12Path = institution.pkcs12().toString();
    Path file = directory.resolve("test.passphrase"); cfg.passphraseFile = file.toString();
    Files.writeString(file, new String(SecurityFixtures.PASSWORD) + "\r\n");
    char[] password = PdfSigning.password(cfg, null);
    try {
      assertNotNull(PdfSigning.load(cfg, password));
      try (var identity = digital.thinkbox.pdfsecurity.SigningIdentity.load(institution.pkcs12(), password, null, institution.now())) {
        assertEquals(institution.leaf(), identity.certificates().getFirst());
      }
    } finally { Arrays.fill(password, '\0'); }
  }

  @Test void privateGuiRunConfigKeepsThePathAndNeverCopiesThePassphrase() throws Exception {
    Path file = directory.resolve("test.passphrase"); String secret = "SYNTHETIC-SECRET-NEVER-IN-CONFIG";
    Files.writeString(file, secret);
    var defaults = new EapdfConfig(); defaults.signing.enabled = true; defaults.signing.protection = "PADES_B_LTA";
    defaults.signing.passphraseFile = file.toString();
    Path run = GuiRunConfiguration.write(defaults, true, true, defaults.signing);
    try {
      assertFalse(Files.readString(run).contains(secret));
      var loaded = EapdfConfig.load(run);
      assertEquals(file.toString(), loaded.signing.passphraseFile);
      assertEquals("http://timestamp.digicert.com", loaded.signing.tsaUrl);
    } finally { Files.deleteIfExists(run); }
  }
}
