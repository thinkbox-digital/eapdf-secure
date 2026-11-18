package eapdf;

import digital.thinkbox.pdfsecurity.*;
import eapdf.security.EaPdfC2paProfile;
import eapdf.security.SecurityPolicy;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.apache.pdfbox.Loader;

/** Application preservation/publication boundary around the shared security coordinator. */
public final class EaPdfSecurity {
  private EaPdfSecurity() {}

  static boolean active() {
    var cfg = EapdfConfig.get().signing;
    return cfg.c2pa || cfg.enabled && (!"CMS_AND_PDF".equals(cfg.protection)
        || cfg.trustPolicy != null || !Common.nz(cfg.policyPath).isBlank() || !Common.nz(cfg.tsaUrl).isBlank());
  }

  static void preflight() throws Exception {
    var cfg = EapdfConfig.get().signing;
    var level = PdfSecurity.Protection.valueOf(cfg.protection);
    var policy = configuredPolicy();
    if (!cfg.enabled) {
      if (level != PdfSecurity.Protection.CMS_AND_PDF || !Common.nz(cfg.tsaUrl).isBlank())
        throw Common.fail("Timestamped protection requires PDF signing; no downgrade was performed.");
      return;
    }
    if ((level != PdfSecurity.Protection.CMS_AND_PDF) != !Common.nz(cfg.tsaUrl).isBlank())
      throw Common.fail("The timestamp service must match signing.protection; no downgrade was performed.");
    if (level != PdfSecurity.Protection.CMS_AND_PDF && policy.anchors().isEmpty())
      throw Common.fail("Trusted timestamps require independently selected trust certificates in signing.policyPath.");
    char[] password = password();
    try (var identity = identity(password)) {
      if (cfg.c2pa) C2pa.requireSigningCredential(identity);
      var checks = policy.check(identity.certificates().getFirst(), identity.certificates());
      if (checks.values().stream().anyMatch(c -> c.status() == SecurityReport.Status.FAIL))
        throw Common.fail("Signing certificate policy failed: " + checks);
      if (level == PdfSecurity.Protection.PADES_B_LTA
          && (checks.get("certificateTrust").status() != SecurityReport.Status.PASS
          || !Set.of(SecurityReport.Status.PASS, SecurityReport.Status.NOT_APPLICABLE).contains(checks.get("revocation").status())))
        throw Common.fail("PAdES B-LTA requires verified signer trust and fresh revocation evidence.");
    } finally { Arrays.fill(password, '\0'); }
  }

  private static char[] password() {
    try { return PdfSigning.password(EapdfConfig.get().signing, System.getenv(PdfSigning.PASSWORD_ENV)); }
    catch (IOException error) { throw Common.fail("Cannot read signing passphrase file: " + error.getMessage()); }
  }

  private static SigningIdentity identity(char[] password) throws Exception {
    var cfg = EapdfConfig.get().signing;
    if (Common.nz(cfg.pkcs12Path).isBlank()) throw Common.fail("Signing requires a PKCS#12 credential.");
    return SigningIdentity.load(Path.of(cfg.pkcs12Path), password, cfg.alias, Instant.now());
  }

  static void prepareConfigured(Path path) throws Exception {
    if (!EapdfConfig.get().signing.c2pa) return;
    Common.writeAtomically(path, prepared -> {
      try (var document = IndexedPdfLoader.load(path, org.apache.pdfbox.io.IOUtils.createTempFileOnlyStreamCache())) {
        var files = C2pa.files(document);
        if (C2pa.hasCredentials(document) || files.containsKey(EaPdfC2paProfile.CREDENTIALS) || files.containsKey(EaPdfC2paProfile.POLICY))
          throw Common.fail("Output already contains reserved C2PA names; existing content cannot be replaced.");
        C2pa.prepare(document, new EaPdfC2paProfile(Common.projectVersion()));
        // Isolated profiles can have no ordinary embedded files. PDF/A-4f still
        // needs the name tree when catalog AF metadata is introduced; keep it empty.
        var catalog = document.getDocumentCatalog();
        var names = catalog.getNames();
        if (names == null) names = new org.apache.pdfbox.pdmodel.PDDocumentNameDictionary(catalog);
        if (names.getEmbeddedFiles() == null) {
          var tree = new org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode();
          tree.setNames(new TreeMap<>()); names.setEmbeddedFiles(tree); catalog.setNames(names);
        }
        document.save(prepared.toFile());
      }
    });
  }

  static void protectConfigured(Path path) throws Exception {
    preflight();
    var cfg = EapdfConfig.get().signing;
    var profile = cfg.c2pa ? new EaPdfC2paProfile(Common.projectVersion()) : null;
    if (!cfg.enabled) {
      C2pa.complete(path, null, null, profile);
      requireC2pa(path, profile);
      return;
    }
    char[] password = password();
    try (var identity = identity(password)) {
      var policy = configuredPolicy();
      var timestamp = timestamp(policy);
      protect(path, identity, policy, timestamp, Clock.systemUTC(), profile);
    } finally { Arrays.fill(password, '\0'); }
  }

  static void protect(Path path, SigningIdentity identity, CertificatePolicy policy, Rfc3161Client timestamp,
      Clock clock, EaPdfC2paProfile profile) throws Exception {
    var cfg = EapdfConfig.get().signing;
    Path work = Files.createTempDirectory(path.toAbsolutePath().getParent(), ".eapdf-security-");
    try {
      Path secured = work.resolve("secured.pdf");
      var options = new PdfSecurity.Options(identity, policy, timestamp, clock, profile, cfg.reason);
      var result = PdfSecurity.sign(path, secured, PdfSecurity.Protection.valueOf(cfg.protection), options);
      if (!result.valid()) throw new IOException("Requested PDF security protection did not verify");
      ProcessingLimits.checkOutput(Files.size(secured));
      PdfCreationValidator.validate(secured);
      Common.preserveOutputPermissions(path, secured);
      Files.move(secured, path, StandardCopyOption.REPLACE_EXISTING);
    } finally { Common.deleteTree(work); }
  }

  private static void requireC2pa(Path path, EaPdfC2paProfile profile) throws IOException {
    var checks = C2pa.verify(path, profile);
    if (!checks.containsKey("c2pa.binding") || checks.values().stream().anyMatch(c -> c.status() == SecurityReport.Status.FAIL))
      throw new IOException("C2PA export credentials did not verify: " + checks);
  }

  private static Rfc3161Client timestamp(CertificatePolicy policy) {
    var cfg = EapdfConfig.get().signing;
    return Common.nz(cfg.tsaUrl).isBlank() ? null : Rfc3161Client.endpoint(java.net.URI.create(cfg.tsaUrl),
        policy, Clock.systemUTC(), Common.nz(cfg.tsaPolicyOid).isBlank() ? null : cfg.tsaPolicyOid);
  }

  private static CertificatePolicy configuredPolicy() throws Exception {
    var cfg = EapdfConfig.get().signing;
    return cfg.trustPolicy == null ? SecurityPolicy.load(cfg.policyPath) : SecurityPolicy.load(cfg.trustPolicy);
  }

  /** Append verified archival evidence to a new destination; the input is never rewritten. */
  public static void renew(Path input, Path output) throws Exception {
    preflight();
    var cfg = EapdfConfig.get().signing;
    if (!cfg.enabled || !"PADES_B_LTA".equals(cfg.protection)) throw Common.fail("Renewal requires PADES_B_LTA signing settings.");
    char[] password = password();
    try (var identity = identity(password)) {
      var policy = configuredPolicy();
      renew(input, output, identity, policy, timestamp(policy), Clock.systemUTC());
    } finally { Arrays.fill(password, '\0'); }
  }

  static void renew(Path input, Path output, SigningIdentity identity, CertificatePolicy policy,
      Rfc3161Client timestamp, Clock clock) throws Exception {
    if (Files.exists(output, LinkOption.NOFOLLOW_LINKS) || input.toRealPath().equals(output.toAbsolutePath().normalize()))
      throw new IOException("Renewal requires a new destination");
    PdfCreationValidator.validate(input);
    EaPdfC2paProfile profile;
    try (var document = Loader.loadPDF(input.toFile())) {
      profile = C2pa.hasCredentials(document) ? EaPdfC2paProfile.FORMAT : null;
      if (profile != null) EaPdfC2paProfile.artifacts(document);
    }
    Files.createDirectories(output.toAbsolutePath().getParent());
    Path work = Files.createTempDirectory(output.toAbsolutePath().getParent(), ".eapdf-renewal-");
    try {
      Path renewed = work.resolve("renewed.pdf");
      var result = PdfSecurity.renew(input, renewed, new PdfSecurity.Options(identity, policy, timestamp, clock, profile));
      if (!result.valid() || !result.lta()) throw new IOException("Archival renewal did not verify");
      PdfCreationValidator.validate(renewed);
      ProcessingLimits.checkOutput(Files.size(renewed));
      if (Files.getFileStore(renewed).supportsFileAttributeView("posix"))
        Files.setPosixFilePermissions(renewed, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
      Files.createLink(output, renewed);
    } finally { Common.deleteTree(work); }
  }
}
