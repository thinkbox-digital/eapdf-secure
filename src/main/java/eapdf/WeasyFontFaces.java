package eapdf;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.fontbox.ttf.OTFParser;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;

/** Trusted font-face discovery; variants must belong to the selected typeface. */
final class WeasyFontFaces {
  private WeasyFontFaces() {}
  record Face(Path path, int weight, String style) {}
  private record Stamp(Path path, long size, FileTime modified) {}
  private record Metadata(String family, int width, int weight, String style) {}
  private static final Map<Stamp, Metadata> METADATA = new LinkedHashMap<>(32, .75f, true) {
    @Override protected boolean removeEldestEntry(Map.Entry<Stamp, Metadata> eldest) { return size() > 128; }
  };

  static List<Face> variants(Path normal) throws IOException {
    return variants(normal, null);
  }

  static List<Face> variants(Path normal, Path bundledFonts) throws IOException {
    Stamp selected = stamp(normal);
    Metadata baseline = metadata(selected);
    Map<String, Face> faces = new LinkedHashMap<>();
    faces.put(key(baseline), new Face(selected.path(), baseline.weight(), baseline.style()));
    var candidates = new LinkedHashSet<Path>();
    String stem = selected.path().getFileName().toString().replaceFirst("(?i)\\.(ttf|otf)$", "")
        .replaceFirst("(?i)[-_]?(Regular|Book|Roman|BoldItalic|BoldOblique|Bold|Italic|Oblique)$", "");
    candidates(candidates, selected.path().getParent(), stem);
    String family = baseline.family();
    String systemStem = switch (family) {
      case "liberation sans" -> "LiberationSans";
      case "liberation serif" -> "LiberationSerif";
      case "liberation mono" -> "LiberationMono";
      case "dejavu sans" -> "DejaVuSans";
      case "dejavu sans mono" -> "DejaVuSansMono";
      case "dejavu serif" -> "DejaVuSerif";
      case "nanumgothic", "nanum gothic" -> "NanumGothic";
      default -> null;
    };
    if (systemStem != null && bundledFonts != null) candidates(candidates, bundledFonts, systemStem);
    if (systemStem != null) for (String directory : List.of(
        "/usr/share/fonts/truetype/liberation", "/usr/share/fonts/truetype/liberation2",
        "/usr/share/fonts/truetype/dejavu", "/usr/share/fonts/truetype/nanum")) {
      Path root = Path.of(directory);
      if (Files.isDirectory(root)) candidates(candidates, root, systemStem);
    }
    for (Path candidate : candidates) {
      if (!Files.isRegularFile(candidate)) continue;
      try {
        Stamp file = stamp(candidate); Metadata variant = metadata(file);
        if (baseline.family().equals(variant.family()) && baseline.width() == variant.width())
          faces.putIfAbsent(key(variant), new Face(file.path(), variant.weight(), variant.style()));
      } catch (IOException invalidCandidate) {
        // Unrelated/malformed siblings are not a substitute for the selected font.
        // The caller reports any missing requested style explicitly.
      }
    }
    // Recheck the finite candidate set each call so new/replaced sibling files
    // are noticed; stamped metadata caching avoids repeated native font loads.
    return List.copyOf(faces.values());
  }

  private static String key(Metadata metadata) { return metadata.weight() + ":" + metadata.style(); }

  private static void candidates(LinkedHashSet<Path> paths, Path directory, String stem) {
    for (String extension : List.of(".ttf", ".otf")) {
      paths.add(directory.resolve(stem + extension));
      for (String suffix : List.of("Regular", "Book", "Bold", "Italic", "Oblique", "BoldItalic", "BoldOblique")) {
        paths.add(directory.resolve(stem + "-" + suffix + extension));
        paths.add(directory.resolve(stem + suffix + extension));
      }
    }
  }

  private static Stamp stamp(Path path) throws IOException {
    Path real = path.toRealPath();
    if (!Files.isRegularFile(real)) throw new IOException("Font is not a regular file: " + path);
    ProcessingLimits.checkFont(real);
    return new Stamp(real, Files.size(real), Files.getLastModifiedTime(real));
  }

  private static Metadata metadata(Stamp stamp) throws IOException {
    synchronized (METADATA) {
      Metadata cached = METADATA.get(stamp);
      if (cached != null) return cached;
    }
    String family;
    try {
      family = Font.createFont(Font.TRUETYPE_FONT, stamp.path().toFile()).getFamily(Locale.ROOT)
          .strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    } catch (FontFormatException invalid) { throw new IOException("Unsupported font face", invalid); }
    Metadata result;
    try (var input = new RandomAccessReadBufferedFile(stamp.path().toFile());
         var font = new OTFParser().parse(input)) {
      var metrics = font.getOS2Windows();
      if (metrics == null) throw new IOException("Font lacks required weight/style metadata");
      int weight = metrics.getWeightClass();
      if (weight < 100 || weight > 900 || weight % 100 != 0)
        throw new IOException("WeasyPrint requires a supported static font weight (100..900 in steps of 100)");
      int selection = metrics.getFsSelection();
      // OpenType italic/oblique bits classify actual outlines, never filenames.
      String style = (selection & 1) != 0 ? "italic" : (selection & 512) != 0 ? "oblique" : "normal";
      result = new Metadata(family, metrics.getWidthClass(), weight, style);
    }
    synchronized (METADATA) { METADATA.put(stamp, result); }
    return result;
  }
}
