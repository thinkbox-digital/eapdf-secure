package eapdf;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.fontbox.ttf.OTFParser;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WeasyFontFacesTests {
  @TempDir Path temporary;
  private static final Path NORMAL = Path.of("assets/LiberationSans-Regular.ttf");

  private static Path knownBold() {
    for (String root : List.of("/usr/share/fonts/truetype/liberation", "/usr/share/fonts/truetype/liberation2")) {
      Path font = Path.of(root, "LiberationSans-Bold.ttf");
      if (Files.isRegularFile(font)) return font;
    }
    return null;
  }

  private static String style(WeasyFontFaces.Face face) { return face.weight() + ":" + face.style(); }

  @Test void discoversActualMatchingRegularBoldItalicAndBoldItalicFaces() throws Exception {
    assumeTrue(knownBold() != null, "Optional matching Liberation font styles are not installed");
    var faces = WeasyFontFaces.variants(NORMAL);
    assertEquals(NORMAL.toRealPath(), faces.getFirst().path());
    var styles = faces.stream().map(WeasyFontFacesTests::style).toList();
    assertTrue(styles.containsAll(List.of("400:normal", "700:normal", "400:italic", "700:italic")), styles.toString());
    assertEquals(styles.size(), styles.stream().distinct().count());
    assertEquals(faces, WeasyFontFaces.variants(NORMAL));
  }

  @Test void filenameCannotRelabelRegularOutlinesAsBoldOrItalic() throws Exception {
    Path normal = temporary.resolve("Custom-Regular.ttf"), fakeBold = temporary.resolve("Custom-Bold.ttf");
    Files.copy(NORMAL, normal); Files.copy(NORMAL, fakeBold);
    var faces = WeasyFontFaces.variants(normal);
    assertEquals("400:normal", style(faces.getFirst()));
    assertTrue(faces.stream().noneMatch(face -> face.path().equals(fakeBold.toAbsolutePath())));
  }

  @Test void unrelatedFontFamilyIsNeverUsedAsAStyledSibling() throws Exception {
    Path normal = temporary.resolve("Custom-Regular.ttf"), fakeBold = temporary.resolve("Custom-Bold.ttf");
    Files.copy(NORMAL, normal);
    Files.copy(Path.of("src/main/resources/assets/DejaVuSans.ttf"), fakeBold);
    assertTrue(WeasyFontFaces.variants(normal).stream().noneMatch(face -> face.path().equals(fakeBold.toAbsolutePath())));
  }

  @Test void replacingAnInspectedFaceInvalidatesItsMetadataCache() throws Exception {
    Path bold = knownBold(); assumeTrue(bold != null, "Optional matching Liberation bold font is not installed");
    Path normal = temporary.resolve("Custom-Regular.ttf"), sibling = temporary.resolve("Custom-Bold.ttf");
    Files.copy(NORMAL, normal); Files.copy(NORMAL, sibling);
    WeasyFontFaces.variants(normal);
    Files.copy(bold, sibling, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    assertTrue(WeasyFontFaces.variants(normal).stream().anyMatch(face -> face.path().equals(sibling.toAbsolutePath()) && face.weight() == 700));
  }

  @Test void stagedVariantsActuallyEmbedAndPaintTheRequestedFontStyles() throws Exception {
    String python = System.getenv("EAPDF_WEASYPRINT_PYTHON");
    assumeTrue(python != null && !python.isBlank(), "Optional WeasyPrint runtime is not configured");
    assumeTrue(knownBold() != null, "Optional matching Liberation font styles are not installed");
    var faces = WeasyFontFaces.variants(NORMAL);
    var fonts = new ArrayList<Map<String, Object>>();
    for (var face : faces) {
      String filename = "font-" + fonts.size() + ".ttf";
      Files.copy(face.path(), temporary.resolve(filename));
      fonts.add(Map.of("file", filename, "family", "ArchiveFont", "weight", face.weight(), "style", face.style()));
    }
    var request = Map.of("version", 1, "memoryMiB", 512, "maxOutputMiB", 128,
        "timeoutSeconds", 30, "maxPages", 10, "assets", Map.of(), "fonts", fonts);
    Files.writeString(temporary.resolve("request.json"), new Gson().toJson(request));
    Files.writeString(temporary.resolve("in.html"), """
        <html lang="en"><head><title>Real font styles</title>
        <style>body{font-family:ArchiveFont;font-size:18pt}</style></head><body>
        <p>Normal sample</p><p><b>Bold sample</b></p><p><i>Italic sample</i></p>
        <p><b><i>Bold italic sample</i></b></p></body></html>
        """, StandardCharsets.UTF_8);
    Path worker = Path.of("src/main/resources/eapdf/weasyprint_worker.py").toAbsolutePath();
    Path log = temporary.resolve("worker.log");
    var process = new ProcessBuilder(python, "-I", worker.toString(), "request.json")
        .directory(temporary.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    try {
      assertTrue(process.waitFor(40, TimeUnit.SECONDS), "Styled font render timed out");
      assertEquals(0, process.exitValue(), Files.readString(log));
    } finally { if (process.isAlive()) process.destroyForcibly(); }
    try (var pdf = Loader.loadPDF(temporary.resolve("out.pdf").toFile())) {
      Map<String, String> observed = new HashMap<>();
      Map<String, String> expected = Map.of("Normal sample", "400:normal", "Bold sample", "700:normal",
          "Italic sample", "400:italic", "Bold italic sample", "700:italic");
      var extractor = new PDFTextStripper() {
        @Override protected void writeString(String text, List<TextPosition> positions) throws IOException {
          String label = text.strip();
          if (expected.containsKey(label)) {
            for (var position : positions) assertEquals(expected.get(label), embeddedStyle(position.getFont()), label);
            observed.put(label, embeddedStyle(positions.getFirst().getFont()));
          }
          super.writeString(text, positions);
        }
      };
      extractor.getText(pdf);
      assertEquals(expected, observed, "Requested styles must use real matching embedded outlines");
    }
  }

  private static String embeddedStyle(PDFont font) throws IOException {
    var program = font.getFontDescriptor().getFontFile2();
    assertNotNull(program, "Expected embedded TrueType outlines");
    try (var input = program.createInputStream(); var data = new RandomAccessReadBuffer(input.readAllBytes());
         var embedded = new OTFParser().parse(data)) {
      var metrics = embedded.getOS2Windows();
      return metrics.getWeightClass() + ":" + ((metrics.getFsSelection() & 1) != 0 ? "italic" : "normal");
    }
  }
}
