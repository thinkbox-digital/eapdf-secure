package eapdf;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real-engine check; ordinary builds do not require an optional Python installation. */
class WeasyIntegrationTests {
  @TempDir Path directory;

  @Test void imagesWithoutDescriptionsRenderWithAccessibilityOnOrOff() throws Exception {
    String python = System.getenv("EAPDF_WEASYPRINT_PYTHON");
    assumeTrue(python != null && Files.isExecutable(Path.of(python)), "Optional pinned WeasyPrint environment is not configured");
    EapdfConfig config = EapdfConfig.get();
    var previous = config.rendering;
    boolean previousAccessibility = config.accessibility.addPdfUaFeatures;
    config.rendering = new EapdfConfig.RenderingConfig(); config.rendering.pythonExecutable = python;
    try {
      for (boolean accessibility : List.of(false, true)) for (String alternative : List.of("", "alt=''", "alt='  '")) {
        config.accessibility.addPdfUaFeatures = accessibility;
        byte[] source = new String(complexEmail(), StandardCharsets.UTF_8).replace("alt='Four colored pixels'", alternative).getBytes(StandardCharsets.UTF_8);
        EmailRecord record = EmlReader.readRfc822Bytes(source, "image.eml", "image.eml", "", List.of(), false);
        byte[] pdf = PdfMailWriter.buildSingleMessagePdf(directory.resolve("eml_image_" + accessibility + "_1s.pdf"), record,
            TestSupport.fontPath(), TestSupport.iccPath(), false, directory, 1, "s").pdfBytes;
        try (PDDocument document = Loader.loadPDF(pdf)) {
          assertTrue(new PDFTextStripper().getText(document).contains("Right edge"));
          int images = 0;
          for (var page : document.getPages()) for (var name : page.getResources().getXObjectNames())
            if (page.getResources().getXObject(name) instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) images++;
          assertTrue(images > 0, "Source image must remain rendered");
        }
        assertArrayEquals(source, record.sourceBytes);
        assertTrue(record.conversionWarnings.stream().anyMatch(w -> w.contains("generic description")));
        assertTrue(record.conversionWarnings.stream().noneMatch(w -> w.contains("Reduced rendering")));
      }
    } finally { config.rendering = previous; config.accessibility.addPdfUaFeatures = previousAccessibility; }
  }

  @Test void complexEmailKeepsTagsUnicodeLinksFontsAndIsolationPolicy() throws Exception {
    String python = System.getenv("EAPDF_WEASYPRINT_PYTHON");
    assumeTrue(python != null && Files.isExecutable(Path.of(python)), "Optional pinned WeasyPrint environment is not configured");
    EapdfConfig config = EapdfConfig.get();
    var previous = config.rendering;
    boolean accessibility = config.accessibility.addPdfUaFeatures;
    var selected = new EapdfConfig.RenderingConfig();
    selected.engine = "weasyprint"; selected.pythonExecutable = python;
    selected.memoryMiB = 512; selected.maxProcesses = 1; selected.maxOutputMiB = 32;
    selected.timeoutSeconds = 60;
    config.rendering = selected; config.accessibility.addPdfUaFeatures = true;
    try {
      byte[] source = complexEmail();
      String evidenceDirectory = System.getenv("EAPDF_WEASY_EVIDENCE_DIR");
      Path evidence = evidenceDirectory == null || evidenceDirectory.isBlank() ? null : Path.of(evidenceDirectory);
      if (evidence != null) {
        Files.createDirectories(evidence);
        Files.write(evidence.resolve("eml_rich.eml"), source);
        Files.writeString(evidence.resolve("source-manifest.json"), new com.google.gson.Gson().toJson(Map.of(
            "fixture", "Synthetic renderer edge-case fixture; not part of the real-input benchmark",
            "source", "eml_rich.eml", "sha256", Common.sha256Hex(source), "weasyprint", WeasyPrintRenderer.VERSION,
            "javaHeapMiB", Runtime.getRuntime().maxMemory() / 1024 / 1024, "pythonAllowanceMiB", selected.memoryMiB)));
      }
      for (String profile : List.of("s", "si")) {
        EmailRecord record = EmlReader.readRfc822Bytes(source, "complex.eml", "complex.eml", "", List.of(), false);
        var expected = PreservationInventory.capture(List.of(record), profile);
        byte[] pdf = PdfMailWriter.buildSingleMessagePdf(directory.resolve("eml_complex_1" + profile + ".pdf"), record,
            TestSupport.fontPath(), TestSupport.iccPath(), false, directory, 1, profile).pdfBytes;
        if (evidence != null) Files.write(evidence.resolve("eml_rich_1" + profile + ".pdf"), pdf);
        try (PDDocument document = Loader.loadPDF(pdf)) {
          PdfCreationValidator.validate(document, expected);
          String text = new PDFTextStripper().getText(document);
          assertTrue(text.contains("café Ελληνικά 日本語"), text);
          assertTrue(text.contains("Right edge"), text);
          assertTrue(document.getPage(0).getMediaBox().getWidth() > 595, "Wide authored table must fit inside widened pages");
          Map<String, Integer> roles = new HashMap<>();
          collect(document.getDocumentCatalog().getCOSObject().getDictionaryObject(COSName.STRUCT_TREE_ROOT), roles, record.headerTagMarker);
          assertTrue(roles.getOrDefault("Mail_Field", 0) >= 4, roles.toString());
          assertTrue(roles.getOrDefault("Mail_FieldName", 0) >= 4, roles.toString());
          assertTrue(roles.getOrDefault("Mail_FieldValue", 0) >= 4, roles.toString());
          boolean externalLink = false;
          for (var page : document.getPages()) {
            for (var name : page.getResources().getFontNames()) assertTrue(page.getResources().getFont(name).isEmbedded());
            for (var annotation : page.getAnnotations()) {
              if (annotation instanceof PDAnnotationLink link && link.getAction() instanceof PDActionURI action
                  && "https://example.org/reference".equals(action.getURI())) externalLink = true;
            }
          }
          assertTrue(externalLink, "Authored external link must remain actionable");
          COSArray missingBounds = figureBounds(document.getDocumentCatalog().getCOSObject()
              .getDictionaryObject(COSName.STRUCT_TREE_ROOT), "Missing footer graphic");
          assertNotNull(missingBounds, "Missing source image must retain Figure alternative text");
          float[] bounds = missingBounds.toFloatArray();
          assertEquals(75d / 30, Math.abs((bounds[2] - bounds[0]) / (bounds[3] - bounds[1])), .01,
              "Authored intrinsic image ratio must survive Java normalization and height:auto");
          var files = AttachmentProfilePolicyTests.files(document);
          if (profile.equals("s")) {
            assertEquals(1, files.size());
            assertArrayEquals(source, AttachmentProfilePolicyTests.bytes(files.values().iterator().next()));
          } else assertTrue(files.isEmpty(), "Isolated profile must omit source and native attachment bytes");
        }
        assertArrayEquals(source, record.sourceBytes);
        assertTrue(record.conversionWarnings.stream().noneMatch(warning -> warning.startsWith("Reduced rendering:")));
      }
    } finally { config.rendering = previous; config.accessibility.addPdfUaFeatures = accessibility; }
  }

  private static void collect(COSBase value, Map<String, Integer> roles, String marker) {
    if (value instanceof COSObject object) value = object.getObject();
    if (value instanceof COSArray array) for (COSBase child : array) collect(child, roles, marker);
    else if (value instanceof COSDictionary element) {
      if (element.containsKey(COSName.S)) roles.merge(element.getNameAsString(COSName.S), 1, Integer::sum);
      assertFalse(element.getString(COSName.ALT, "").startsWith(marker), "Internal header marker must not remain in alternate text");
      collect(element.getDictionaryObject(COSName.K), roles, marker);
    }
  }

  private static COSArray figureBounds(COSBase value, String alternative) {
    if (value instanceof COSObject object) value = object.getObject();
    if (value instanceof COSArray array) {
      for (COSBase child : array) {
        COSArray found = figureBounds(child, alternative);
        if (found != null) return found;
      }
    } else if (value instanceof COSDictionary element) {
      if (alternative.equals(element.getString(COSName.ALT))
          && element.getDictionaryObject(COSName.A) instanceof COSDictionary attributes)
        return attributes.getCOSArray(COSName.BBOX);
      return figureBounds(element.getDictionaryObject(COSName.K), alternative);
    }
    return null;
  }

  private static byte[] complexEmail() throws Exception {
    String image = Base64.getEncoder().encodeToString(CidUrlTests.image());
    String html = "<html><head><link rel='attachment' href='data:text/plain;base64,bm90LWEtdXNlci1hdHRhY2htZW50' title='Ignore authored PDF attachment'>"
        + "<style>table.content {width:960px;border-collapse:collapse}td{border:1px solid #777}img{height:auto}</style></head><body>"
        + "<h2>Complex interoperability fixture</h2><p>café Ελληνικά 日本語</p>"
        + "<table class='content'><tr><td>Left column</td><td>Middle column</td><td>Right edge</td></tr>"
        + "<tr><td colspan='2'>Merged cells</td><td><a rel='attachment' href='https://example.org/reference'><small>Reference link</small></a></td></tr></table>"
        + "<pre>first line\n    indented monospace</pre><img alt='Four colored pixels' src='cid:diagram'>"
        + "<a href='https://example.org/missing'><img width='75' height='30' alt='Missing footer graphic'></a></body></html>";
    return ("From: Sender <sender@example.org>\r\nTo: Recipient <recipient@example.org>\r\n"
        + "Date: Fri, 16 Oct 2026 12:00:00 +0000\r\nSubject: Complex renderer fixture\r\nMessage-ID: <complex-renderer@example.org>\r\n"
        + "MIME-Version: 1.0\r\nContent-Type: multipart/mixed; boundary=parts\r\n\r\n"
        + "--parts\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Transfer-Encoding: 8bit\r\n\r\n" + html
        + "\r\n--parts\r\nContent-Type: image/png\r\nContent-ID: <diagram>\r\nContent-Disposition: inline; filename=diagram.png\r\n"
        + "Content-Transfer-Encoding: base64\r\n\r\n" + image
        + "\r\n--parts\r\nContent-Type: text/plain\r\nContent-Disposition: attachment; filename=note.txt\r\n"
        + "Content-Transfer-Encoding: base64\r\n\r\n" + Base64.getEncoder().encodeToString("Original attachment bytes".getBytes(StandardCharsets.UTF_8))
        + "\r\n--parts--\r\n").getBytes(StandardCharsets.UTF_8);
  }
}
