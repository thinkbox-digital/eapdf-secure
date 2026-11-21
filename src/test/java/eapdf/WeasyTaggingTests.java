package eapdf;

import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class WeasyTaggingTests {
  private static final String MARKER = "EA-PDF-bridge:";

  @Test void arrayDocumentRootsKeepRenderedContentAndParentTreeOwners() throws Exception {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(); document.addPage(page);
      COSDictionary original = tree();
      COSDictionary renderedDocument = PdfMailWriter.structElem("Document", original, null);
      COSDictionary paragraph = PdfMailWriter.structElem("P", renderedDocument, page);
      paragraph.setInt(COSName.K, 0);
      renderedDocument.setItem(COSName.K, array(paragraph));
      original.setItem(COSName.K, array(renderedDocument));
      parentTree(original, 0, array(paragraph));
      page.getCOSObject().setInt(COSName.STRUCT_PARENTS, 0);
      markedText(document, page, "P", 0, "Retained rendered text");

      COSDictionary output = tree(); output.setInt(COSName.getPDFName("ParentTreeNextKey"), 7);
      COSDictionary contentSet = PdfMailWriter.structElem("Mail_ContentSet", output, page);
      SemanticStructure.attach(document, output, contentSet, original, 0, 0);

      COSArray content = (COSArray) contentSet.getDictionaryObject(COSName.K);
      assertSame(paragraph, content.getObject(0));
      assertSame(contentSet, paragraph.getDictionaryObject(COSName.P));
      assertEquals(7, page.getCOSObject().getInt(COSName.STRUCT_PARENTS));
      COSArray numbers = (COSArray) ((COSDictionary) output.getDictionaryObject(COSName.getPDFName("ParentTree"))).getDictionaryObject(COSName.NUMS);
      assertEquals(7, numbers.getInt(0));
      assertSame(paragraph, ((COSArray) numbers.getObject(1)).getObject(0));
      assertEquals(0, paragraph.getInt(COSName.K));
    }
  }

  @Test void bridgeMarkersBecomeEaPdfFieldsAndMarkedContentTags() throws Exception {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(); document.addPage(page);
      COSDictionary original = tree();
      COSDictionary renderedDocument = PdfMailWriter.structElem("Document", original, null);
      COSDictionary table = PdfMailWriter.structElem("Table", renderedDocument, page);
      COSDictionary row = marked("TR", table, page, "field:From");
      COSDictionary name = marked("TD", row, page, "name:From");
      COSDictionary value = marked("TD", row, page, "value:From");
      name.setInt(COSName.K, 0); value.setInt(COSName.K, 1);
      row.setItem(COSName.K, array(name, value)); table.setItem(COSName.K, array(row));
      renderedDocument.setItem(COSName.K, array(table)); original.setItem(COSName.K, array(renderedDocument));
      parentTree(original, 0, array(name, value)); page.getCOSObject().setInt(COSName.STRUCT_PARENTS, 0);
      document.getDocumentCatalog().getCOSObject().setItem(COSName.STRUCT_TREE_ROOT, original);
      markedText(document, page, "TD", 0, "From:");
      markedText(document, page, "TD", 1, "Sender");

      SemanticStructure.headerFields(document, MARKER);
      assertEquals("Mail_Field", row.getNameAsString(COSName.S));
      assertEquals("Mail_FieldName", name.getNameAsString(COSName.S));
      assertEquals("Mail_FieldValue", value.getNameAsString(COSName.S));
      assertFalse(row.containsKey(COSName.ALT)); assertFalse(name.containsKey(COSName.ALT));
      var streams = page.getContentStreams();
      int operands = 0;
      while (streams.hasNext()) {
        List<Object> tokens = new PDFStreamParser(streams.next().toByteArray()).parse();
        for (int index = 2; index < tokens.size(); index++) {
          if (tokens.get(index) instanceof Operator operator && operator.getName().equals("BDC")) {
            String role = ((COSName) tokens.get(index - 2)).getName();
            assertTrue(List.of("Mail_FieldName", "Mail_FieldValue").contains(role)); operands++;
          }
        }
      }
      assertEquals(2, operands);
      COSDictionary output = tree(), contentSet = PdfMailWriter.structElem("Mail_ContentSet", output, page);
      SemanticStructure.attach(document, output, contentSet, original, 0, 0);
      assertSame(table, ((COSArray) contentSet.getDictionaryObject(COSName.K)).getObject(0));
      assertSame(row, ((COSArray) table.getDictionaryObject(COSName.K)).getObject(0));
    }
  }

  @Test void arrayRootImportRetainsLinkAnnotationOwnership() throws Exception {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(); document.addPage(page);
      COSDictionary original = tree(), renderedDocument = PdfMailWriter.structElem("Document", original, null);
      PDAnnotationLink annotation = new PDAnnotationLink(); annotation.setContents("Open example");
      annotation.getCOSObject().setInt(COSName.STRUCT_PARENT, 1); page.getAnnotations().add(annotation);
      COSDictionary link = PdfMailWriter.structElem("Link", renderedDocument, page);
      COSDictionary reference = new COSDictionary(); reference.setName(COSName.TYPE, "OBJR");
      reference.setItem(COSName.OBJ, annotation); reference.setItem(COSName.PG, page);
      link.setItem(COSName.K, array(reference)); renderedDocument.setItem(COSName.K, array(link));
      original.setItem(COSName.K, array(renderedDocument)); parentTree(original, 1, link);
      COSDictionary output = tree(); output.setInt(COSName.getPDFName("ParentTreeNextKey"), 5);
      COSDictionary contentSet = PdfMailWriter.structElem("Mail_ContentSet", output, page);
      SemanticStructure.attach(document, output, contentSet, original, 0, 0);
      assertEquals(5, annotation.getCOSObject().getInt(COSName.STRUCT_PARENT));
      assertSame(link, ((COSArray) contentSet.getDictionaryObject(COSName.K)).getObject(0));
      assertSame(annotation.getCOSObject(), reference.getDictionaryObject(COSName.OBJ));
      assertSame(contentSet, link.getDictionaryObject(COSName.P));
    }
  }

  @Test void transparentNonStructInlineRunsBecomeParagraphsWithoutReplacingMcids() throws Exception {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(); document.addPage(page);
      COSDictionary original = tree(), renderedDocument = PdfMailWriter.structElem("Document", original, null);
      COSDictionary wrapper = PdfMailWriter.structElem("NonStruct", renderedDocument, page);
      COSDictionary span = PdfMailWriter.structElem("Span", wrapper, page); span.setInt(COSName.K, 0);
      wrapper.setItem(COSName.K, array(span)); renderedDocument.setItem(COSName.K, array(wrapper));
      original.setItem(COSName.K, array(renderedDocument)); parentTree(original, 0, array(span));
      page.getCOSObject().setInt(COSName.STRUCT_PARENTS, 0);
      markedText(document, page, "Span", 0, "Preformatted text");
      COSDictionary output = tree(), contentSet = PdfMailWriter.structElem("Mail_ContentSet", output, page);
      SemanticStructure.attach(document, output, contentSet, original, 0, 0);
      assertEquals("P", wrapper.getNameAsString(COSName.S));
      assertSame(span, ((COSArray) wrapper.getDictionaryObject(COSName.K)).getObject(0));
      assertEquals(0, span.getInt(COSName.K));
      assertSame(wrapper, span.getDictionaryObject(COSName.P));
      COSArray numbers = (COSArray) ((COSDictionary) output.getDictionaryObject(COSName.getPDFName("ParentTree"))).getDictionaryObject(COSName.NUMS);
      assertSame(span, ((COSArray) numbers.getObject(1)).getObject(0));
    }
  }

  @ParameterizedTest @CsvSource({"NonStruct, Span", "Div, Span", "NonStruct, Figure", "Div, Figure"})
  void transparentWrapperInsideLinksRemainsInline(String wrapperRole, String contentRole) throws Exception {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(); document.addPage(page);
      COSDictionary original = tree(), renderedDocument = PdfMailWriter.structElem("Document", original, null);
      COSDictionary link = PdfMailWriter.structElem("Link", renderedDocument, page);
      COSDictionary wrapper = PdfMailWriter.structElem(wrapperRole, link, page);
      COSDictionary inner = PdfMailWriter.structElem("Div", wrapper, page);
      COSDictionary span = PdfMailWriter.structElem(contentRole, inner, page); span.setInt(COSName.K, 0);
      span.setString(COSName.ALT, "Linked content");
      PDAnnotationLink annotation = new PDAnnotationLink(); annotation.setContents("Nested link text");
      annotation.getCOSObject().setInt(COSName.STRUCT_PARENT, 1); page.getAnnotations().add(annotation);
      COSDictionary reference = new COSDictionary(); reference.setName(COSName.TYPE, "OBJR");
      reference.setItem(COSName.OBJ, annotation); reference.setItem(COSName.PG, page);
      PDAnnotationLink imageLink = new PDAnnotationLink(); imageLink.setContents("Linked content");
      imageLink.getCOSObject().setInt(COSName.STRUCT_PARENT, 2); page.getAnnotations().add(imageLink);
      COSDictionary imageReference = new COSDictionary(); imageReference.setName(COSName.TYPE, "OBJR");
      imageReference.setItem(COSName.OBJ, imageLink); imageReference.setItem(COSName.PG, page);
      inner.setItem(COSName.K, array(imageReference, span)); wrapper.setItem(COSName.K, array(inner));
      link.setItem(COSName.K, array(reference, wrapper));
      renderedDocument.setItem(COSName.K, array(link)); original.setItem(COSName.K, array(renderedDocument));
      parentTree(original, 0, array(span)); page.getCOSObject().setInt(COSName.STRUCT_PARENTS, 0);
      COSArray originalParents = (COSArray) ((COSDictionary) original.getDictionaryObject(COSName.getPDFName("ParentTree"))).getDictionaryObject(COSName.NUMS);
      originalParents.add(COSInteger.ONE); originalParents.add(link);
      originalParents.add(COSInteger.get(2)); originalParents.add(inner);
      markedText(document, page, "Span", 0, "Nested link text");
      COSDictionary output = tree(), contentSet = PdfMailWriter.structElem("Mail_ContentSet", output, page);
      SemanticStructure.attach(document, output, contentSet, original, 0, 0);
      assertEquals("Span", wrapper.getNameAsString(COSName.S));
      assertEquals("Span", inner.getNameAsString(COSName.S));
      assertSame(reference, ((COSArray) link.getDictionaryObject(COSName.K)).getObject(0));
      assertSame(wrapper, ((COSArray) link.getDictionaryObject(COSName.K)).getObject(1));
      assertSame(inner, ((COSArray) wrapper.getDictionaryObject(COSName.K)).getObject(0));
      assertSame(imageReference, ((COSArray) inner.getDictionaryObject(COSName.K)).getObject(0));
      assertSame(span, ((COSArray) inner.getDictionaryObject(COSName.K)).getObject(1));
      assertEquals(0, span.getInt(COSName.K));
      assertEquals(contentRole, span.getNameAsString(COSName.S));
      assertEquals("Linked content", span.getString(COSName.ALT));
      COSArray numbers = (COSArray) ((COSDictionary) output.getDictionaryObject(COSName.getPDFName("ParentTree"))).getDictionaryObject(COSName.NUMS);
      assertSame(span, ((COSArray) numbers.getObject(1)).getObject(0));
      assertSame(link, numbers.getObject(3));
      assertSame(inner, numbers.getObject(5));
      assertSame(annotation.getCOSObject(), reference.getDictionaryObject(COSName.OBJ));
      assertSame(imageLink.getCOSObject(), imageReference.getDictionaryObject(COSName.OBJ));
      assertSame(link, wrapper.getDictionaryObject(COSName.P));
      assertTrue(new org.apache.pdfbox.text.PDFTextStripper().getText(document).contains("Nested link text"));
    }
  }

  @Test void groupingLinkRetainsRealBlockParagraphs() throws Exception {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(); document.addPage(page);
      COSDictionary original = tree(), renderedDocument = PdfMailWriter.structElem("Document", original, null);
      COSDictionary link = PdfMailWriter.structElem("Link", renderedDocument, page);
      COSDictionary wrapper = PdfMailWriter.structElem("Div", link, page);
      COSDictionary paragraph = PdfMailWriter.structElem("P", wrapper, page); paragraph.setInt(COSName.K, 0);
      wrapper.setItem(COSName.K, array(paragraph)); link.setItem(COSName.K, array(wrapper));
      renderedDocument.setItem(COSName.K, array(link)); original.setItem(COSName.K, array(renderedDocument));
      parentTree(original, 0, array(paragraph)); page.getCOSObject().setInt(COSName.STRUCT_PARENTS, 0);
      markedText(document, page, "P", 0, "Block link content");
      COSDictionary output = tree(), contentSet = PdfMailWriter.structElem("Mail_ContentSet", output, page);
      SemanticStructure.attach(document, output, contentSet, original, 0, 0);
      assertEquals("Div", wrapper.getNameAsString(COSName.S));
      assertEquals("P", paragraph.getNameAsString(COSName.S));
    }
  }

  private static COSDictionary tree() {
    COSDictionary tree = new COSDictionary(); tree.setName(COSName.TYPE, "StructTreeRoot"); return tree;
  }

  private static COSDictionary marked(String role, COSDictionary parent, PDPage page, String marker) {
    COSDictionary element = PdfMailWriter.structElem(role, parent, page); element.setString(COSName.ALT, MARKER + marker); return element;
  }

  private static COSArray array(COSBase... values) {
    COSArray array = new COSArray(); for (COSBase value : values) array.add(value); return array;
  }

  private static void parentTree(COSDictionary tree, int key, COSBase value) {
    COSDictionary parents = new COSDictionary(); parents.setItem(COSName.NUMS, array(COSInteger.get(key), value));
    tree.setItem(COSName.getPDFName("ParentTree"), parents);
  }

  private static void markedText(PDDocument document, PDPage page, String role, int mcid, String text) throws IOException {
    try (PDPageContentStream stream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, false)) {
      COSDictionary properties = new COSDictionary(); properties.setInt(COSName.MCID, mcid);
      stream.beginMarkedContent(COSName.getPDFName(role), PDPropertyList.create(properties));
      stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12); stream.showText(text); stream.endText();
      stream.endMarkedContent();
    }
  }
}
