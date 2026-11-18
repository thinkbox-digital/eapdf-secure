package eapdf.security;

import com.upokecenter.cbor.CBORObject;
import digital.thinkbox.pdfsecurity.C2pa;
import digital.thinkbox.pdfsecurity.C2paProfile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;

/** Document-level export provenance, separate from email attachments and collection members. */
public final class EaPdfC2paProfile implements C2paProfile {
  public static final EaPdfC2paProfile FORMAT = new EaPdfC2paProfile("1");
  public static final String CREDENTIALS = "eapdf-content-credentials.c2pa";
  public static final String POLICY = "eapdf-c2pa-policy.json";
  private static final byte[] POLICY_BYTES = "{\"schemaVersion\":1,\"scope\":\"EMAIL_EXPORT\",\"specification\":\"2.2\",\"profile\":\"eapdf-1\"}\n".getBytes(StandardCharsets.UTF_8);
  private final String version;

  public EaPdfC2paProfile(String version) { this.version = Objects.requireNonNull(version); }
  @Override public boolean associatedFilesOnly() { return true; }
  @Override public String credentialsFile() { return CREDENTIALS; }
  @Override public String policyFile() { return POLICY; }
  @Override public byte[] policyBytes() { return POLICY_BYTES.clone(); }
  @Override public String assertionLabel() { return "digital.thinkbox.eapdf"; }
  @Override public String generatorName() { return "EA-PDF"; }
  @Override public String generatorVersion() { return version; }

  @Override public CBORObject createAssertion(PDDocument document, boolean local) throws Exception {
    return CBORObject.NewMap().Add("schemaVersion", 1).Add("scope", "EMAIL_EXPORT")
        .Add("metadataSha256", metadataDigest(document)).Add("inventorySha256", inventoryDigest(document))
        .Add("identity", local ? "LOCAL_EPHEMERAL_UNTRUSTED" : "CONFIGURED_CERTIFICATE")
        .Add("sourceAuthentication", "NOT_ASSERTED");
  }

  @Override public void verifyAssertion(PDDocument document, CBORObject assertion) throws Exception {
    if (assertion.get("schemaVersion").AsInt32() != 1 || !"EMAIL_EXPORT".equals(assertion.get("scope").AsString())
        || !"NOT_ASSERTED".equals(assertion.get("sourceAuthentication").AsString())
        || !Set.of("LOCAL_EPHEMERAL_UNTRUSTED", "CONFIGURED_CERTIFICATE").contains(assertion.get("identity").AsString())
        || !MessageDigest.isEqual(assertion.get("metadataSha256").GetByteString(), metadataDigest(document))
        || !MessageDigest.isEqual(assertion.get("inventorySha256").GetByteString(), inventoryDigest(document)))
      throw new IOException("EA-PDF export assertion does not match its metadata and preserved files");
  }

  private static byte[] metadataDigest(PDDocument document) throws Exception {
    var metadata = document.getDocumentCatalog().getMetadata();
    if (metadata == null) throw new IOException("EA-PDF provenance requires document XMP");
    try (var input = metadata.createInputStream()) { return digest(input); }
  }

  private static byte[] inventoryDigest(PDDocument document) throws Exception {
    var hash = MessageDigest.getInstance("SHA-256");
    for (var entry : C2pa.files(document).entrySet()) {
      if (Set.of(CREDENTIALS, POLICY).contains(entry.getKey())) continue;
      var file = entry.getValue();
      for (String value : List.of(entry.getKey(), Objects.toString(file.getFileUnicode(), ""),
          Objects.toString(file.getCOSObject().getNameAsString(COSName.getPDFName("AFRelationship")), ""))) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        hash.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); hash.update(bytes);
      }
      var embedded = file.getEmbeddedFileUnicode(); if (embedded == null) embedded = file.getEmbeddedFile();
      if (embedded == null) throw new IOException("Missing preserved-file payload");
      try (var input = embedded.createInputStream()) { hash.update(digest(input)); }
    }
    return hash.digest();
  }

  private static byte[] digest(java.io.InputStream input) throws Exception {
    var hash = MessageDigest.getInstance("SHA-256"); byte[] bytes = new byte[65536];
    for (int n; (n = input.read(bytes)) != -1;) {
      if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Security verification cancelled");
      hash.update(bytes, 0, n);
    }
    return hash.digest();
  }

  /** Recognize only this profile's bounded catalog AF metadata, never arbitrary hidden files. */
  public static Set<COSDictionary> artifacts(PDDocument document) throws IOException {
    var result = Collections.newSetFromMap(new IdentityHashMap<COSDictionary, Boolean>());
    var catalog = document.getDocumentCatalog();
    var af = catalog.getCOSObject().getCOSArray(COSName.getPDFName("AF"));
    if (af == null) return result;
    COSDictionary credentials = null, policy = null;
    for (int i = 0; i < af.size(); i++) {
      if (!(af.getObject(i) instanceof COSDictionary spec)) continue;
      String name = spec.getString(COSName.UF, spec.getString(COSName.F));
      if (CREDENTIALS.equals(name) && "C2PA_Manifest".equals(spec.getNameAsString(COSName.getPDFName("AFRelationship")))) {
        if (credentials != null) throw new IOException("Ambiguous EA-PDF C2PA credentials"); credentials = spec;
      } else if (POLICY.equals(name)) {
        if (policy != null) throw new IOException("Ambiguous EA-PDF C2PA policy"); policy = spec;
      }
    }
    if (credentials == null) return result;
    if (policy == null) throw new IOException("Incomplete EA-PDF C2PA declaration");
    var names = catalog.getNames();
    if (names != null && names.getEmbeddedFiles() != null) checkMembership(names.getEmbeddedFiles(), credentials, policy, 0);
    checkArtifact(credentials, CREDENTIALS, "C2PA_Manifest", "application/c2pa", C2pa.CAPACITY, null);
    checkArtifact(policy, POLICY, "Supplement", "application/json", POLICY_BYTES.length, POLICY_BYTES);
    result.add(credentials); result.add(policy); return result;
  }

  private static void checkMembership(org.apache.pdfbox.pdmodel.common.PDNameTreeNode<PDComplexFileSpecification> tree,
      COSDictionary credentials, COSDictionary policy, int depth) throws IOException {
    if (depth > 16) throw new IOException("Invalid embedded-file name tree");
    if (tree.getNames() != null) for (var file : tree.getNames().values())
      if (file.getCOSObject() == credentials || file.getCOSObject() == policy)
        throw new IOException("EA-PDF C2PA metadata must not become collection members or email attachments");
    if (tree.getKids() != null) for (var kid : tree.getKids()) checkMembership(kid, credentials, policy, depth + 1);
  }

  private static void checkArtifact(COSDictionary spec, String filename, String relationship, String mime, int capacity, byte[] expected) throws IOException {
    if (!relationship.equals(spec.getNameAsString(COSName.getPDFName("AFRelationship"))) || spec.containsKey(COSName.getPDFName("CI")))
      throw new IOException("Invalid EA-PDF security metadata relationship");
    var ef = spec.getCOSDictionary(COSName.EF);
    if (!filename.equals(spec.getString(COSName.F)) || !filename.equals(spec.getString(COSName.UF))
        || ef == null || !ef.keySet().equals(Set.of(COSName.F, COSName.UF))
        || ef.getDictionaryObject(COSName.F) != ef.getDictionaryObject(COSName.UF))
      throw new IOException("EA-PDF security metadata must use one unambiguous embedded payload");
    var file = new PDComplexFileSpecification(spec); var stream = file.getEmbeddedFileUnicode();
    if (stream == null) stream = file.getEmbeddedFile();
    if (stream == null || !mime.equals(stream.getSubtype()) || stream.getModDate() == null)
      throw new IOException("Invalid EA-PDF security embedded stream");
    try (var input = stream.createInputStream()) {
      byte[] bytes = input.readNBytes(capacity + 1);
      if (bytes.length != capacity || expected != null && !MessageDigest.isEqual(bytes, expected))
        throw new IOException("EA-PDF security metadata differs from the selected profile");
    }
  }
}
