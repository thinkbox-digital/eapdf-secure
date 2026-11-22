package eapdf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AuditRepairTests {
  @TempDir Path directory;

  @Test void filenameBoundariesDoNotSplitSupplementaryCharacters() {
    for (String input : List.of("x".repeat(119) + "😀tail", "x".repeat(118) + "😀tail")) {
      for (String name : List.of(Common.safeFilename(input), Common.safeCollectionLabel(input, "Messages"))) {
        assertTrue(name.length() <= 120);
        assertEquals(name, new String(name.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
      }
    }
    assertEquals("Messages", Common.safeCollectionLabel("\0", "Messages"));
    assertFalse(Common.safeCollectionLabel("", "bad\0folder").contains("\0"));
  }

  @Test void encodedSubjectNulDoesNotReachOutputPathsOrAlterOriginalSource() throws Exception {
    byte[] source = ("Date: Fri, 16 Oct 2026 12:00:00 +0000\r\n"
        + "Subject: =?UTF-8?Q?a=00b?=\r\n\r\nBody").getBytes(StandardCharsets.UTF_8);
    EmailRecord record = EmlReader.readRfc822Bytes(source, "sample.eml", "sample.eml", "", List.of(), false);
    assertTrue(record.subject.contains("\0"));
    String filename = EmailToPDFA.uniqueFileName(directory.toString(), "input", Common.safeFileStem(record.subject), 1, 1, false);
    assertDoesNotThrow(() -> Path.of(filename));
    assertDoesNotThrow(() -> Path.of(PdfCollectionSupport.collectionMemberFilename(record.subject, "s", 1)));
    assertFalse(Common.safeFileStem("a\u007fb").contains("\u007f"));
    assertArrayEquals(source, record.sourceBytes);
  }

  @Test void malformedInlineImageDoesNotDiscardHtmlFormatting() throws Exception {
    EmailRecord record = AttachmentProfilePolicyTests.record("Malformed inline image");
    record.htmlBody = "<p>Before <b>formatted</b></p><img alt='broken' src='data:image/png;base64,A'><p>After</p>";
    var built = PdfMailWriter.buildSingleMessagePdf(directory.resolve("message.pdf"), record,
        TestSupport.fontPath(), TestSupport.iccPath(), false, directory, 1, "s");
    try (var document = Loader.loadPDF(built.pdfBytes)) {
      String text = new PDFTextStripper().getText(document);
      assertTrue(text.contains("Before")); assertTrue(text.contains("After"));
    }
    assertTrue(record.conversionWarnings.stream().anyMatch(w -> w.contains("malformed inline data image")));
    assertTrue(record.conversionWarnings.stream().noneMatch(w -> w.contains("Reduced rendering")));
    assertFalse(MailRenderPolicy.isAllowedRenderResource("data:image/png,%Q0"));
    assertFalse(MailRenderPolicy.isAllowedRenderResource("data:image/png,%"));
  }

  @Test void inlineSizeLimitIsFatalAndCheckedBeforeMalformedBase64Decode() {
    String property = "eapdf.maxMessageBytes";
    String previous = System.getProperty(property);
    try {
      System.setProperty(property, "4");
      assertThrows(Common.UserFacingException.class,
          () -> MailRenderPolicy.decodeRenderImageUri("data:image/png;base64,AAAAAAAAAAAAA"));
      assertArrayEquals(new byte[4], MailRenderPolicy.decodeRenderImageUri("data:image/png;base64,AAAAAA=="));
      assertArrayEquals(new byte[4], MailRenderPolicy.decodeRenderImageUri("data:image/png,%00%00%00%00"));
    } finally { restoreProperty(property, previous); }
  }

  @Test void weakMimeHintsDoNotRelabelArbitraryBinaryOrText() {
    assertEquals("", Common.sniffMime("BMxx".getBytes(StandardCharsets.UTF_8)));
    assertEquals("", Common.sniffMime("Discuss the subject: next week".getBytes(StandardCharsets.UTF_8)));
    assertEquals("", Common.sniffMime("subject: heading\r\nThis is ordinary text".getBytes(StandardCharsets.UTF_8)));
    assertEquals("message/rfc822", Common.sniffMime("subject: heading\r\nfrom: sender@example.org\r\n\r\nBody".getBytes(StandardCharsets.UTF_8)));
    byte[] bmp = new byte[54];
    ByteBuffer header = ByteBuffer.wrap(bmp).order(ByteOrder.LITTLE_ENDIAN);
    bmp[0] = 'B'; bmp[1] = 'M'; header.putInt(2, 58); header.putInt(10, 54); header.putInt(14, 40);
    assertEquals("image/bmp", Common.sniffMime(bmp));
    header.putInt(10, 1);
    assertEquals("", Common.sniffMime(bmp));
  }

  @Test void libraryWritingDoesNotReplaceStreamsOrSetCompressionProperty() throws Exception {
    var out = System.out; var err = System.err;
    String key = "org.apache.pdfbox.filter.deflatelevel", previous = System.getProperty(key);
    try (var capture = Common.beginPerFileLog(directory.resolve("library.pdf"))) {
      Class.forName("eapdf.PdfMailWriter");
      assertSame(out, System.out); assertSame(err, System.err);
      assertEquals(previous, System.getProperty(key));
    }
  }

  @Test void nestedCommandScopesRestoreStreamsAndRespectCompressionOverrides() {
    var out = System.out; var err = System.err;
    String key = "org.apache.pdfbox.filter.deflatelevel", previous = System.getProperty(key);
    try {
      System.clearProperty(key);
      try (var outer = Common.commandRuntime()) {
        var captured = System.out;
        assertNotSame(out, captured); assertEquals("9", System.getProperty(key));
        try (var inner = Common.commandRuntime()) { assertSame(captured, System.out); }
        assertSame(captured, System.out);
      }
      assertSame(out, System.out); assertSame(err, System.err); assertNull(System.getProperty(key));
      System.setProperty(key, "3");
      try (var runtime = Common.commandRuntime()) { assertEquals("3", System.getProperty(key)); }
      assertEquals("3", System.getProperty(key));
    } finally { restoreProperty(key, previous); }
  }

  @Test void atomicReplacementPreservesPermissionsAndFailedWritePreservesOldBytes() throws Exception {
    Path output = directory.resolve("existing.pdf");
    Files.writeString(output, "original");
    boolean posix = Files.getFileStore(output).supportsFileAttributeView("posix");
    var permissions = PosixFilePermissions.fromString("rw-r-----");
    if (posix) Files.setPosixFilePermissions(output, permissions);
    Common.writeAtomically(output, "replacement".getBytes(StandardCharsets.UTF_8));
    if (posix) assertEquals(permissions, Files.getPosixFilePermissions(output));
    IOException failure = new IOException("render failed");
    assertSame(failure, assertThrows(IOException.class, () -> Common.writeAtomically(output, temporary -> {
      Files.writeString(temporary, "incomplete"); throw failure;
    })));
    assertEquals("replacement", Files.readString(output));
    try (var files = Files.list(directory)) { assertEquals(List.of(output), files.toList()); }
  }

  @Test void publicationPreservesDestinationModeAndKeepsNewFilesPrivate() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
    Path staged = directory.resolve("staged.pdf"), existing = directory.resolve("existing.pdf"), fresh = directory.resolve("fresh.pdf");
    Files.writeString(staged, "completed"); Files.setPosixFilePermissions(staged, PosixFilePermissions.fromString("rw-------"));
    Files.writeString(existing, "previous");
    var mode = PosixFilePermissions.fromString("rw-r-----"); Files.setPosixFilePermissions(existing, mode);
    String digest = Common.digestHex(staged, "SHA-256");
    var entries = List.of(new OutputTransaction.Entry(existing.toString(), staged.toString(), 9, digest, true),
        new OutputTransaction.Entry(fresh.toString(), staged.toString(), 9, digest, false));
    OutputTransaction.publish(entries, OutputTransaction::publishOutput);
    assertEquals("completed", Files.readString(existing));
    assertEquals(mode, Files.getPosixFilePermissions(existing));
    assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(fresh));
  }

  @Test void cleanupFailureIsSuppressedBehindPrimaryFailure() throws Exception {
    Path[] temporaryPath = new Path[1];
    IOException primary = new IOException("primary render error");
    assertSame(primary, assertThrows(IOException.class, () -> Common.writeAtomically(directory.resolve("output.pdf"), temporary -> {
      temporaryPath[0] = temporary;
      Files.delete(temporary); Files.createDirectory(temporary); Files.writeString(temporary.resolve("child"), "retained");
      throw primary;
    })));
    assertEquals(1, primary.getSuppressed().length);
    Common.deleteTree(temporaryPath[0]);
  }

  @Test void nestedTaskFailuresKeepOriginalExceptionAndErrorTypes() {
    var fatal = new AssertionError("worker error");
    assertSame(fatal, assertThrows(AssertionError.class,
        () -> Common.rethrowTaskFailure(new ExecutionException(new CompletionException(fatal)))));
    var failure = Common.fail("worker limit");
    assertSame(failure, assertThrows(Common.UserFacingException.class,
        () -> EmailToPDFA.rethrowTaskFailure(new ExecutionException(new ExecutionException(failure)))));
  }

  @Test void embeddedChildSizeLimitHasAnActionableError() {
    assertEquals(Integer.MAX_VALUE, Common.checkedPayloadSize(Integer.MAX_VALUE));
    var error = assertThrows(Common.UserFacingException.class, () -> Common.checkedPayloadSize((long) Integer.MAX_VALUE + 1));
    assertTrue(error.getMessage().contains("supported size"));
  }

  @Test void mailboxMetadataDoesNotExposeLocalFilesystemPaths() throws Exception {
    for (String type : List.of("pst", "mbox")) {
      EmailRecord record = AttachmentProfilePolicyTests.record("Mailbox source");
      record.sourceType = type; record.sourcePath = "/private/work/source." + type;
      for (String profile : List.of("m", "mi", "c", "ci")) {
        String xmp = PdfMailWriter.buildXmpMetadata(List.of(record), profile);
        assertFalse(xmp.contains(record.sourcePath)); assertFalse(xmp.contains("/private/work"));
        assertTrue(xmp.contains("preprocessing:"));
      }
    }
  }

  @Test void exportTimestampsAreSharedWithWorkerThreads() throws Exception {
    try (var job = Common.beginJob()) {
      var instant = Common.exportInstant();
      assertEquals(instant.toEpochMilli(), Common.exportCalendar().getTimeInMillis());
      var future = new java.util.concurrent.CompletableFuture<java.time.Instant>();
      Thread worker = new Thread(() -> future.complete(Common.exportInstant())); worker.start(); worker.join();
      assertEquals(instant, future.get());
      try (var capture = Common.beginPerFileLog(directory.resolve("time.pdf"))) {
        assertEquals(instant, Common.exportInstant());
      }
    }
  }

  private static void restoreProperty(String key, String value) {
    if (value == null) System.clearProperty(key); else System.setProperty(key, value);
  }
}
