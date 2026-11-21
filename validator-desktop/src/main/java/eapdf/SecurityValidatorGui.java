package eapdf;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/** Separate, read-only C2PA and PAdES verification window. */
public final class SecurityValidatorGui {
  interface Engine {
    JsonObject verify(Path input, String policy, String protection) throws Exception;
    void cancel();
  }
  public static void main(String[] args) {
    if (java.awt.GraphicsEnvironment.isHeadless()) {
      System.err.println("EA-PDF Security Validator needs a graphical desktop."); System.exit(1);
    }
    SwingUtilities.invokeLater(() -> {
      try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) { }
      new SecurityValidatorGui(new SecurityValidatorEngine()).show();
    });
  }

  private final Engine engine;
  private final JFrame frame = new JFrame("EA-PDF Security Validator Experimental");
  private final JTextField file = new JTextField();
  private final JTextField trustPolicy = new JTextField(System.getProperty("eapdf.security.policy", System.getenv().getOrDefault("EAPDF_SECURITY_POLICY", "")));
  private final JComboBox<String> protection = new JComboBox<>(new String[]{"Auto-detect", "PDF signature", "Timestamped PDF signature", "PAdES B-LTA"});
  private final JButton browse = new JButton("Browse…"), chooseTrust = new JButton("Browse…"), verify = new JButton("Verify"), stop = new JButton("Stop"), save = new JButton("Save report…");
  private final JLabel status = new JLabel("Choose a PDF to verify C2PA and PDF signature protection.");
  private final JProgressBar progress = new JProgressBar();
  private final JTextArea detail = new JTextArea();
  private final DefaultTableModel rows = new DefaultTableModel(new String[]{"Part", "Result", "Check", "Evidence"}, 0) {
    @Override public boolean isCellEditable(int row, int column) { return false; }
  };
  private final JTable table = new JTable(rows);
  private JsonObject report;
  private boolean running, stopping, closeRequested;

  SecurityValidatorGui(Engine engine) { this.engine = engine; }

  void show() {
    frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    frame.addWindowListener(new WindowAdapter() {
      @Override public void windowClosing(WindowEvent event) {
        if (running) { closeRequested = true; requestStop(); } else frame.dispose();
      }
    });
    JPanel content = new JPanel(new BorderLayout(12, 12)); content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
    JPanel header = new JPanel(new java.awt.GridLayout(0, 1, 0, 10));
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    for (JButton button : List.of(browse, verify, stop, save)) { button.setMargin(new Insets(6, 12, 6, 12)); actions.add(button); }
    header.add(inputRow("PDF:", file, actions)); header.add(inputRow("Trust JSON (optional):", trustPolicy, chooseTrust));
    header.add(inputRow("Required protection:", protection, new JLabel("Public trust evidence only; no signing key is needed.")));
    content.add(header, BorderLayout.NORTH);
    file.setToolTipText("The PDF is read without being changed. Press Enter to verify.");
    trustPolicy.setToolTipText("Select the public trust/revocation policy JSON used for security verification.");
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); table.setAutoCreateRowSorter(true); table.setRowHeight(30);
    table.setFillsViewportHeight(true); table.setShowGrid(false);
    table.getColumnModel().getColumn(0).setPreferredWidth(110); table.getColumnModel().getColumn(1).setPreferredWidth(110);
    table.getColumnModel().getColumn(2).setPreferredWidth(260); table.getColumnModel().getColumn(3).setPreferredWidth(380);
    table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
      { putClientProperty("html.disable", Boolean.TRUE); }
      @Override public java.awt.Component getTableCellRendererComponent(JTable owner, Object value, boolean selected, boolean focused, int row, int column) {
        super.getTableCellRendererComponent(owner, value, selected, focused, row, column);
        setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        setForeground(selected ? owner.getSelectionForeground() : column != 1 ? new Color(35, 48, 60)
            : "Verified".equals(value) ? new Color(22, 101, 52) : "Information".equals(value) ? new Color(100, 116, 139) : new Color(153, 27, 27));
        return this;
      }
    });
    detail.setEditable(false); detail.setLineWrap(true); detail.setWrapStyleWord(true); detail.setMargin(new Insets(12, 12, 12, 12));
    table.getSelectionModel().addListSelectionListener(event -> {
      int selected = table.getSelectedRow(); if (selected < 0) return;
      int row = table.convertRowIndexToModel(selected);
      detail.setText(rows.getValueAt(row, 0) + " — " + rows.getValueAt(row, 2) + "\n\n" + rows.getValueAt(row, 1) + "\n\n" + rows.getValueAt(row, 3));
      detail.setCaretPosition(0);
    });
    JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), new JScrollPane(detail)); split.setResizeWeight(.7); content.add(split, BorderLayout.CENTER);
    JPanel footer = new JPanel(new BorderLayout(0, 8)); status.putClientProperty("html.disable", Boolean.TRUE); footer.add(status, BorderLayout.CENTER); footer.add(progress, BorderLayout.SOUTH); content.add(footer, BorderLayout.SOUTH);
    browse.addActionListener(event -> choose(file, "PDF files", "pdf")); chooseTrust.addActionListener(event -> choose(trustPolicy, "Trust policy JSON", "json"));
    verify.addActionListener(event -> start()); file.addActionListener(event -> start()); stop.addActionListener(event -> requestStop()); save.addActionListener(event -> save());
    stop.setEnabled(false); save.setEnabled(false); frame.setContentPane(content); frame.setSize(1120, 760);
    var bounds = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds(); frame.setSize(Math.min(frame.getWidth(), bounds.width), Math.min(frame.getHeight(), bounds.height));
    frame.setMinimumSize(new Dimension(Math.min(700, bounds.width), Math.min(480, bounds.height))); frame.setLocationRelativeTo(null); frame.setVisible(true);
  }

  private static JPanel inputRow(String text, java.awt.Component input, java.awt.Component actions) {
    JPanel row = new JPanel(new BorderLayout(10, 0)); JLabel label = new JLabel(text); label.setLabelFor(input);
    row.add(label, BorderLayout.WEST); row.add(input, BorderLayout.CENTER); row.add(actions, BorderLayout.EAST); return row;
  }
  private void choose(JTextField target, String description, String extension) {
    var picker = new JFileChooser(); picker.setFileFilter(new FileNameExtensionFilter(description, extension));
    if (picker.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) target.setText(picker.getSelectedFile().getAbsolutePath());
  }
  private void start() {
    if (running) return;
    Path input; String policy = trustPolicy.getText().strip();
    try {
      input = Path.of(file.getText().strip()).toAbsolutePath().normalize();
      if (file.getText().isBlank() || !Files.isRegularFile(input) || !Files.isReadable(input)) throw new java.io.IOException("Choose a readable PDF.");
      if (!policy.isEmpty() && (!Files.isRegularFile(Path.of(policy)) || !Files.isReadable(Path.of(policy)))) throw new java.io.IOException("The trust JSON cannot be read.");
      if (!policy.isEmpty()) policy = Path.of(policy).toAbsolutePath().normalize().toString();
    } catch (Exception error) { status.setText("Verification did not start."); detail.setText(error.getMessage()); return; }
    String required = switch (protection.getSelectedIndex()) { case 1 -> "CMS_AND_PDF"; case 2 -> "CMS_AND_PDF_TIMESTAMPED"; case 3 -> "PADES_B_LTA"; default -> ""; };
    String selectedPolicy = policy; report = null; rows.setRowCount(0); detail.setText(""); stopping = false; setRunning(true);
    status.setText("Verifying " + input.getFileName() + "…");
    new SwingWorker<JsonObject, Void>() {
      @Override protected JsonObject doInBackground() throws Exception { return engine.verify(input, selectedPolicy, required); }
      @Override protected void done() {
        try {
          JsonObject checked = get();
          if (stopping) throw new CancellationException();
          report = checked; display(checked); save.setEnabled(true);
        } catch (InterruptedException error) { Thread.currentThread().interrupt(); status.setText("Verification interrupted."); }
        catch (CancellationException error) { status.setText("Stopped. Verification was not completed."); }
        catch (ExecutionException error) {
          if (stopping || error.getCause() instanceof CancellationException) status.setText("Stopped. Verification was not completed.");
          else { status.setText("Security verification could not finish."); detail.setText(error.getCause().getMessage()); }
        } finally { setRunning(false); if (closeRequested) frame.dispose(); }
      }
    }.execute();
  }
  private void display(JsonObject checked) {
    if (checked.has("checks")) for (var entry : checked.getAsJsonObject("checks").entrySet()) {
      JsonObject value = entry.getValue().getAsJsonObject();
      rows.addRow(new Object[]{SecurityValidation.text(value, "category"), label(SecurityValidation.text(value, "status")), entry.getKey(), SecurityValidation.text(value, "code")});
    }
    String state = SecurityValidation.text(checked, "status");
    status.setText("C2PA / PDF signature security: " + label(state) + (checked.has("padesBLta") && checked.get("padesBLta").getAsBoolean() ? "    PAdES B-LTA: Verified" : ""));
    if (checked.has("message")) detail.setText(SecurityValidation.text(checked, "message"));
    else if (table.getRowCount() > 0) table.setRowSelectionInterval(0, 0);
  }
  static String label(String status) {
    return switch (status) { case "PASS" -> "Verified"; case "FAIL", "ERROR" -> "Failed"; case "NOT_APPLICABLE" -> "Information"; default -> "Not verified"; };
  }
  private void requestStop() { if (running && !stopping) { stopping = true; stop.setEnabled(false); status.setText("Stopping verification…"); engine.cancel(); } }
  private void setRunning(boolean value) {
    running = value; for (var input : List.of(file, trustPolicy, protection, browse, chooseTrust, verify)) input.setEnabled(!value);
    stop.setEnabled(value); progress.setIndeterminate(value); if (value) save.setEnabled(false);
  }
  private void save() {
    if (report == null) return;
    var picker = new JFileChooser(); picker.setSelectedFile(new java.io.File("eapdf-security-report.json"));
    if (picker.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
    Path destination = picker.getSelectedFile().toPath();
    if (Files.exists(destination) && JOptionPane.showConfirmDialog(frame, "Replace the existing report?", "Save report", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
    try { Files.writeString(destination, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
    catch (Exception error) { JOptionPane.showMessageDialog(frame, error.getMessage(), "Unable to save report", JOptionPane.ERROR_MESSAGE); }
  }
}
