package eapdf;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfSystemProperty(named = "eapdf.validator.uiTest", matches = "true")
class SecurityValidatorDesktopSmokeTests {
  @TempDir Path temporary;

  @Test void separateWindowDisplaysAndSavesSecurityThenCancelsWithoutAStaleResult() throws Exception {
    var started = new CountDownLatch(1);
    var cancelled = new CountDownLatch(1);
    SecurityValidatorGui.Engine engine = new SecurityValidatorGui.Engine() {
      int runs;
      @Override public JsonObject verify(Path input, String policy, String protection) throws Exception {
        if (++runs > 1) { started.countDown(); assertTrue(cancelled.await(15, TimeUnit.SECONDS)); throw new CancellationException(); }
        assertEquals("PADES_B_LTA", protection);
        return JsonParser.parseString("""
            {"status":"UNKNOWN","padesBLta":true,"checks":{
              "c2pa.binding":{"category":"C2PA","status":"PASS","code":"PDF_AND_ARCHIVE_MANIFEST_BOUND"},
              "pdf.padesLevel":{"category":"PAdES","status":"PASS","code":"EU_DSS_LEVEL_PAdES-BASELINE-LTA"},
              "c2pa.trust":{"category":"C2PA","status":"UNKNOWN","code":"SIGNER_REQUIRES_EXTERNAL_C2PA_TRUST_POLICY"}}}
            """).getAsJsonObject();
      }
      @Override public void cancel() { cancelled.countDown(); }
    };
    var reference = new AtomicReference<SecurityValidatorGui>();
    SwingUtilities.invokeAndWait(() -> { var gui = new SecurityValidatorGui(engine); reference.set(gui); gui.show(); });
    var gui = reference.get(); var frame = field(gui, "frame", JFrame.class);
    Path input = temporary.resolve("input.pdf"), saved = temporary.resolve("security.json");
    Files.writeString(input, "controlled GUI input");
    Path policy = Files.writeString(temporary.resolve("policy.json"), "{}");
    try {
      SwingUtilities.invokeAndWait(() -> {
        field(gui, "file", JTextField.class).setText(input.toString());
        field(gui, "trustPolicy", JTextField.class).setText(policy.toString());
        field(gui, "protection", JComboBox.class).setSelectedIndex(3);
        field(gui, "verify", JButton.class).doClick();
      });
      await(() -> field(gui, "save", JButton.class).isEnabled());
      SwingUtilities.invokeAndWait(() -> {
        assertTrue(field(gui, "status", JLabel.class).getText().contains("Not verified"));
        JTable table = field(gui, "table", JTable.class);
        assertEquals(3, table.getRowCount());
        assertEquals("C2PA", table.getValueAt(0, 0)); assertEquals("PAdES", table.getValueAt(1, 0));
        assertEquals("Not verified", table.getValueAt(2, 1));
        table.setRowSelectionInterval(2, 2);
        assertTrue(field(gui, "detail", JTextArea.class).getText().contains("SIGNER_REQUIRES_EXTERNAL_C2PA_TRUST_POLICY"));
        saveTo(gui, saved);
        saveTo(gui, input);
        saveTo(gui, policy);
      });
      assertEquals("UNKNOWN", JsonParser.parseString(Files.readString(saved)).getAsJsonObject().get("status").getAsString());
      assertEquals("controlled GUI input", Files.readString(input));
      assertEquals("{}", Files.readString(policy));
      SwingUtilities.invokeAndWait(() -> {
        field(gui, "file", JTextField.class).setText(temporary.resolve("missing.pdf").toString());
        assertFalse(field(gui, "save", JButton.class).isEnabled());
        field(gui, "verify", JButton.class).doClick();
        assertTrue(field(gui, "status", JLabel.class).getText().contains("did not start"));
        assertEquals(0, field(gui, "table", JTable.class).getRowCount());
        assertNull(field(gui, "report", JsonObject.class));
        field(gui, "file", JTextField.class).setText(input.toString());
        field(gui, "verify", JButton.class).doClick();
      });
      assertTrue(started.await(10, TimeUnit.SECONDS));
      SwingUtilities.invokeAndWait(() -> {
        assertFalse(field(gui, "save", JButton.class).isEnabled());
        frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
      });
      await(() -> !frame.isDisplayable());
      assertFalse(field(gui, "save", JButton.class).isEnabled());
      assertNull(field(gui, "report", JsonObject.class));
    } finally { cancelled.countDown(); SwingUtilities.invokeAndWait(frame::dispose); }
  }

  private static void saveTo(SecurityValidatorGui gui, Path target) {
    var picker = new Timer(50, event -> {
      for (Window window : Window.getWindows()) {
        if (!window.isShowing()) continue;
        var chooser = component(window, JFileChooser.class);
        if (chooser != null) {
          chooser.setSelectedFile(target.toFile());
          chooser.approveSelection();
          return;
        }
        var dialog = component(window, JOptionPane.class);
        if (dialog != null) {
          // Accept a replace prompt if this regression ever reappears, so the
          // assertions verify that neither of the protected files was touched.
          dialog.setValue(JOptionPane.YES_OPTION);
          return;
        }
      }
    });
    picker.start();
    try { field(gui, "save", JButton.class).doClick(); }
    finally { picker.stop(); }
  }

  private static <T> T component(java.awt.Container parent, Class<T> type) {
    if (type.isInstance(parent)) return type.cast(parent);
    for (var child : parent.getComponents()) if (child instanceof java.awt.Container container) {
      var found = component(container, type);
      if (found != null) return found;
    }
    return null;
  }
  private static <T> T field(Object object, String name, Class<T> type) {
    try { var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return type.cast(field.get(object)); }
    catch (ReflectiveOperationException error) { throw new AssertionError(error); }
  }
  private static void await(BooleanSupplier condition) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (System.nanoTime() < deadline) {
      var ready = new AtomicReference<Boolean>(); SwingUtilities.invokeAndWait(() -> ready.set(condition.getAsBoolean()));
      if (ready.get()) return;
      Thread.sleep(50);
    }
    fail("Security window action timed out");
  }
}
