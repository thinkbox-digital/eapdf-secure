package eapdf;

import static org.junit.jupiter.api.Assertions.*;
import digital.thinkbox.pdfsecurity.C2pa;
import eapdf.security.EaPdfC2paProfile;
import java.nio.file.*;
import java.util.TreeMap;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecurityMetadataTests {
  @TempDir Path directory;

  @Test void isolatedEaPdfWithOnlyExportSecurityMetadataRetainsConformanceAndUnknownTrust() throws Exception {
    Path output = directory.resolve("secured_1si.pdf");
    try (var document = Loader.loadPDF(Path.of("../examples/eml/eml_Agenda_and_timeline_slides_1si.pdf").toFile())) {
      C2pa.prepare(document, EaPdfC2paProfile.FORMAT);
      var names = document.getDocumentCatalog().getNames();
      if (names == null) names = new PDDocumentNameDictionary(document.getDocumentCatalog());
      var tree = new PDEmbeddedFilesNameTreeNode(); tree.setNames(new TreeMap<>());
      names.setEmbeddedFiles(tree); document.getDocumentCatalog().setNames(names);
      document.save(output.toFile());
    }
    C2pa.complete(output, null, null, EaPdfC2paProfile.FORMAT);
    var report = JavaValidation.validate(output, new JavaValidation.Children(), 0);
    assertEquals("PASS", report.getAsJsonObject("summary").get("status").getAsString(), report.toString());
    assertEquals("PASS", report.getAsJsonObject("pdfa").get("status").getAsString());
    var security = report.getAsJsonObject("security");
    assertEquals("UNKNOWN", security.get("status").getAsString());
    assertEquals("PASS", security.getAsJsonObject("checks").getAsJsonObject("c2pa.binding").get("status").getAsString());
  }
}
