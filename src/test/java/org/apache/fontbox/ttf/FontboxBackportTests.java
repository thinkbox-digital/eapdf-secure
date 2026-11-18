package org.apache.fontbox.ttf;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.fontbox.cff.Type1CharStringParser;
import org.apache.fontbox.cff.Type2CharStringParser;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Malicious parsers run in killable JVMs; a stuck worker cannot stall the test suite. */
class FontboxBackportTests {
  @ParameterizedTest @ValueSource(strings = {"cmap12", "cmap13", "recursion", "sequential", "boundaries"})
  void isolatedWorkerFinishes(String mode) throws Exception {
    var process = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-Xmx256m", "-Xss512k",
        "-Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.NoOpLog", "-cp",
        System.getProperty("java.class.path"), getClass().getName(), mode).inheritIO().start();
    try {
      assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Font worker exceeded deadline: " + mode);
      assertEquals(0, process.exitValue(), mode);
    } finally { process.destroyForcibly().waitFor(); }
  }

  public static void main(String[] args) throws Exception {
    switch (args[0]) {
      case "cmap12", "cmap13" -> {
        int format = args[0].equals("cmap12") ? 12 : 13;
        int[][] groups = new int[100_000][];
        java.util.Arrays.fill(groups, new int[]{0, 65535, format == 12 ? 0 : 1});
        CmapSubtable table = cmap(format, 65536, groups);
        assertEquals(format == 12 ? 65 : 1, table.getGlyphId(65));
        assertEquals(0, table.getGlyphId(65536));
      }
      case "boundaries" -> {
        for (int format : new int[]{12, 13}) {
          assertThrows(IOException.class, () -> cmap(format, 2, 0x110000));
          var table = cmap(format, 4, new int[][]{{0, 0, 1}, {65, 66, 2}, {64, 67, 1}});
          assertEquals(1, table.getGlyphId(0)); // U+0000 must not be skipped.
          assertEquals(2, table.getGlyphId(65));
          assertEquals(0, table.getGlyphId(67)); // Overlapping groups must not overwrite earlier mappings.
        }
        assertEquals(0, cmap(13, 2, new int[][]{{65, 65, 2}}).getGlyphId(65));
      }
      case "recursion" -> {
        byte[] t1 = {(byte) 139, 10};
        assertThrows(IOException.class, () -> new Type1CharStringParser("loop").parse(t1, List.of(t1), "a"));
        assertThrows(IOException.class, () -> new Type1CharStringParser("mutual").parse(t1,
            List.of(new byte[]{(byte) 140, 10}, t1), "a"));
        for (int operator : new int[]{10, 29}) {
          byte[] loop = {32, (byte) operator};
          assertThrows(IOException.class, () -> new Type2CharStringParser("loop").parse(loop,
              operator == 29 ? new byte[][]{loop} : null, operator == 10 ? new byte[][]{loop} : null, "a"));
        }
        assertThrows(IOException.class, () -> new Type2CharStringParser("mixed").parse(new byte[]{32, 10},
            new byte[][]{{32, 10}}, new byte[][]{{32, 29}}, "a"));
        // Legitimate mixed local/global nesting still works below the limit.
        assertFalse(new Type2CharStringParser("nested").parse(new byte[]{32, 10, 14},
            new byte[][]{{(byte) 239, (byte) 239, 21, 11}}, new byte[][]{{32, 29, 11}}, "a").isEmpty());
        for (int depth : new int[]{20, 21}) {
          byte[][] type2 = new byte[depth][];
          var type1 = new java.util.ArrayList<byte[]>();
          for (int i = 0; i < depth - 1; i++) {
            type2[i] = new byte[]{(byte) (33 + i), 10, 11};
            type1.add(new byte[]{(byte) (140 + i), 10, 11});
          }
          type2[depth - 1] = new byte[]{(byte) 139, (byte) 139, 21, 11};
          type1.add(type2[depth - 1]);
          if (depth == 20) {
            assertFalse(new Type2CharStringParser("limit").parse(new byte[]{32, 10, 14}, null, type2, "a").isEmpty());
            assertFalse(new Type1CharStringParser("limit").parse(new byte[]{(byte) 139, 10, 14}, type1, "a").isEmpty());
          } else {
            assertThrows(IOException.class, () -> new Type2CharStringParser("over-limit")
                .parse(new byte[]{32, 10, 14}, null, type2, "a"));
            assertThrows(IOException.class, () -> new Type1CharStringParser("over-limit")
                .parse(new byte[]{(byte) 139, 10, 14}, type1, "a"));
          }
        }
      }
      case "sequential" -> {
        var bytes = new ByteArrayOutputStream();
        for (int i = 0; i < 100; i++) bytes.write(new byte[]{32, 10, 32, 29});
        bytes.write(14);
        byte[][] subrs = {{(byte) 139, (byte) 139, 21, 11}};
        var sequence = new Type2CharStringParser("sequential").parse(bytes.toByteArray(), subrs, subrs, "a");
        assertEquals(601, sequence.size());
        bytes.reset();
        for (int i = 0; i < 100; i++) bytes.write(new byte[]{(byte) 139, 10});
        bytes.write(14);
        assertEquals(301, new Type1CharStringParser("sequential").parse(bytes.toByteArray(),
            List.of(subrs[0]), "a").size());
      }
      default -> throw new IllegalArgumentException(args[0]);
    }
  }

  private static CmapSubtable cmap(int format, int glyphs, int[][] groups) throws Exception {
    var bytes = new ByteArrayOutputStream();
    var data = new DataOutputStream(bytes);
    data.writeInt(groups.length);
    for (int[] group : groups) for (int value : group) data.writeInt(value);
    return cmap(format, glyphs, bytes.toByteArray());
  }

  private static CmapSubtable cmap(int format, int glyphs, int count) throws Exception {
    var bytes = new ByteArrayOutputStream();
    new DataOutputStream(bytes).writeInt(count);
    return cmap(format, glyphs, bytes.toByteArray());
  }

  private static CmapSubtable cmap(int format, int glyphs, byte[] bytes) throws Exception {
    var table = new CmapSubtable();
    try (var stream = new RandomAccessReadDataStream(new RandomAccessReadBuffer(bytes))) {
      if (format == 12) table.processSubtype12(stream, glyphs);
      else table.processSubtype13(stream, glyphs);
    }
    return table;
  }
}
