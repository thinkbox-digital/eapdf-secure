package eapdf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

/** Run with an external process deadline. Embeds original font bytes without parsing them first. */
public final class FontboxProbe {
  public static void main(String[] args) throws Exception {
    Path input = Path.of(args[1]);
    if (args[0].equals("parse")) {
      try (var font = new TTFParser().parseEmbedded(new ByteArrayInputStream(Files.readAllBytes(input)))) {
        font.getUnicodeCmapLookup();
        System.out.println("PARSED");
      } catch (IOException rejected) { System.out.println("REJECTED: " + rejected.getMessage()); }
    } else if (args[0].equals("embed")) {
      boolean cff = args.length > 3 && args[3].equals("cff");
      try (var pdf = new PDDocument()) {
        var page = new PDPage(); pdf.addPage(page); page.setResources(new PDResources());
        var font = new COSDictionary();
        font.setItem(COSName.TYPE, COSName.FONT);
        font.setName(COSName.SUBTYPE, cff ? "Type1" : "TrueType");
        font.setName(COSName.BASE_FONT, "FontProbe"); font.setName(COSName.ENCODING, "WinAnsiEncoding");
        font.setInt(COSName.FIRST_CHAR, 0); font.setInt(COSName.LAST_CHAR, 255);
        var widths = new COSArray();
        for (int i = 0; i < 256; i++) widths.add(org.apache.pdfbox.cos.COSInteger.get(500));
        font.setItem(COSName.WIDTHS, widths);
        var descriptor = new COSDictionary(); descriptor.setItem(COSName.TYPE, COSName.FONT_DESC);
        descriptor.setName(COSName.FONT_NAME, "FontProbe"); descriptor.setInt(COSName.FLAGS, 32);
        for (String key : new String[]{"Ascent", "CapHeight", "StemV"}) descriptor.setInt(COSName.getPDFName(key), 700);
        descriptor.setInt(COSName.DESCENT, -200); descriptor.setInt(COSName.ITALIC_ANGLE, 0);
        descriptor.setItem(COSName.FONT_BBOX, new org.apache.pdfbox.pdmodel.common.PDRectangle(1000, 1000));
        var stream = new PDStream(pdf, new ByteArrayInputStream(Files.readAllBytes(input)));
        if (cff) stream.getCOSObject().setName(COSName.SUBTYPE, "Type1C");
        descriptor.setItem(cff ? COSName.FONT_FILE3 : COSName.FONT_FILE2, stream);
        font.setItem(COSName.FONT_DESC, descriptor);
        var fonts = new COSDictionary(); fonts.setItem(COSName.getPDFName("F0"), font);
        page.getResources().getCOSObject().setItem(COSName.FONT, fonts);
        page.setContents(new PDStream(pdf, new ByteArrayInputStream("BT /F0 12 Tf 20 20 Td (A) Tj ET".getBytes(StandardCharsets.US_ASCII))));
        pdf.save(args[2]);
      }
    } else if (args[0].equals("extract")) {
      try (var pdf = Loader.loadPDF(input.toFile())) {
        String text = new PDFTextStripper().getText(pdf);
        if (!text.contains("A")) throw new AssertionError("Lost text: " + text);
        new PDFRenderer(pdf).renderImage(0);
        System.out.println("EXTRACTED AND RENDERED");
      }
    } else throw new IllegalArgumentException(args[0]);
  }
}
