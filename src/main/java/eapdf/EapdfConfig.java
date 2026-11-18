package eapdf;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class EapdfConfig {
  static class SigningConfig {
    static final String DEFAULT_TSA_URL = "http://timestamp.digicert.com";
    boolean enabled = false;
    String pkcs12Path = "";
    String passphraseFile = "";
    String alias = "";
    String reason = "";
    boolean c2pa = false;
    String protection = "CMS_AND_PDF";
    String policyPath = "";
    JsonObject trustPolicy;
    String tsaUrl = "";
    String tsaPolicyOid = "";
  }





  static class SearchIndexConfig {
    boolean enabled = false;
    boolean fullTextEnabled = false;
  }



  static class RenderingConfig {
    String engine = "weasyprint";
    String pythonExecutable = "";
    int timeoutSeconds = 120;
    int maxProcesses = 2;
    int memoryMiB = 512;
    int maxOutputMiB = 128;
  }

  static class AccessibilityConfig {
    boolean addPdfUaFeatures = false;
    boolean markInfoMarked = true;
    String language = "en";
    String readingDirection = "L2R";
    PageLabelsConfig pageLabels = new PageLabelsConfig();
    ExperimentalConfig experimental = new ExperimentalConfig();
  }

  static class PageLabelsConfig {
    boolean enabled = true;
    boolean singleMessage = false;
    boolean archiveAndCollection = true;
    String frontMatterPrefix = "Index-";
  }

  static class ExperimentalConfig {
    boolean altTextGeneratedImages = false;
  }

  SigningConfig signing = new SigningConfig();
  int configVersion = 1;
  boolean validateOnCreation = true;
  String validationWarning = "";
  static final String DEFAULT_CREATOR = "Thinkbox.DIGITAL";
  String creator = DEFAULT_CREATOR;
  SearchIndexConfig searchIndex = new SearchIndexConfig();
  AccessibilityConfig accessibility = new AccessibilityConfig();
  RenderingConfig rendering = new RenderingConfig();

  String requireCreator() {
    if (creator == null || creator.isBlank()) {
      return DEFAULT_CREATOR;
    }
    if (!Common.stripInvalidXml(creator).equals(creator)) {
      throw new IllegalArgumentException("The creator name contains characters that cannot be preserved in XMP.");
    }
    return creator.strip();
  }

  private static volatile EapdfConfig INSTANCE;

  static EapdfConfig get() {
    EapdfConfig cached = INSTANCE;
    if (cached != null) return cached;
    synchronized (EapdfConfig.class) {
      if (INSTANCE == null) INSTANCE = loadDefault();
      return INSTANCE;
    }
  }

  static EapdfConfig loadDefault() {
    String envPath = System.getenv("EAPDF_CONFIG");
    Path path = (envPath != null && !envPath.isBlank())
        ? Paths.get(envPath)
        : Common.resolveExistingPath("config/eapdf_config.json", "eapdf_config.json");
    if (path == null) path = Paths.get("config/eapdf_config.json");
    if (!Files.exists(path) && (envPath == null || envPath.isBlank())) return normalize(new EapdfConfig());
    return load(path);
  }

  static EapdfConfig load(Path path) {
    try (Reader reader = Files.newBufferedReader(path)) {
      JsonElement json = JsonParser.parseReader(reader);
      if (!json.isJsonObject()) throw new IllegalArgumentException("configuration must be a JSON object");
      validateKeys(json.getAsJsonObject(), EapdfConfig.class, "");
      EapdfConfig loaded = normalize(new Gson().fromJson(json, EapdfConfig.class));
      if (loaded.configVersion != 1) throw new IllegalArgumentException("unsupported configVersion: " + loaded.configVersion + " (expected 1)");
      if (loaded.accessibility.experimental.altTextGeneratedImages) {
        System.err.println("warning: accessibility.experimental settings are unavailable and do not change the output");
      }
      return loaded;
    } catch (Exception e) {
      throw new RuntimeException("failed to load config from " + path + ": " + e.getMessage(), e);
    }
  }

  static void validateKeys(JsonObject json, Class<?> type, String prefix) {
    for (var entry : json.entrySet()) {
      String key = prefix + entry.getKey();
      java.lang.reflect.Field field;
      try {
        field = type.getDeclaredField(entry.getKey());
        if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) throw new NoSuchFieldException();
      } catch (NoSuchFieldException ignored) {
        System.err.println("warning: unknown configuration setting: " + key);
        continue;
      }
      JsonElement value = entry.getValue();
      if (value.isJsonNull()) continue;
      if (field.getType() == boolean.class) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(key + " must be a boolean");
      } else if (field.getType() == int.class) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("[0-9]+")) throw new IllegalArgumentException(key + " must be an integer");
      } else if (field.getType() == String.class) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(key + " must be a string");
      } else if (field.getType() == JsonObject.class) {
        if (!value.isJsonObject()) throw new IllegalArgumentException(key + " must be an object");
        try { eapdf.security.SecurityPolicy.validate(value.getAsJsonObject()); }
        catch (java.io.IOException invalid) { throw new IllegalArgumentException(invalid.getMessage(), invalid); }
      } else {
        if (!value.isJsonObject()) throw new IllegalArgumentException(key + " must be an object");
        validateKeys(value.getAsJsonObject(), field.getType(), key + ".");
      }
    }
  }

  static EapdfConfig normalize(EapdfConfig cfg) {
    if (cfg.rendering == null) cfg.rendering = new RenderingConfig();
    if (!java.util.Set.of("openhtmltopdf", "weasyprint").contains(Common.nz(cfg.rendering.engine)))
      throw new IllegalArgumentException("rendering.engine must be openhtmltopdf or weasyprint");
    if (cfg.rendering.timeoutSeconds < 1 || cfg.rendering.timeoutSeconds > 3600
        || cfg.rendering.maxProcesses < 1 || cfg.rendering.maxProcesses > 16
        || cfg.rendering.memoryMiB < 128 || cfg.rendering.memoryMiB > 8192
        || cfg.rendering.maxOutputMiB < 1 || cfg.rendering.maxOutputMiB > 1024)
      throw new IllegalArgumentException("Invalid rendering limits: timeout 1..3600 seconds, processes 1..16, memory 128..8192 MiB, output 1..1024 MiB");
    if (cfg.signing == null) cfg.signing = new SigningConfig();
    try { digital.thinkbox.pdfsecurity.PdfSecurity.Protection.valueOf(cfg.signing.protection); }
    catch (Exception invalid) { throw new IllegalArgumentException("signing.protection must be CMS_AND_PDF, CMS_AND_PDF_TIMESTAMPED or PADES_B_LTA"); }
    if (!cfg.signing.enabled && !"CMS_AND_PDF".equals(cfg.signing.protection))
      throw new IllegalArgumentException("Timestamped protection requires signing.enabled");
    if ("PADES_B_LTA".equals(cfg.signing.protection)) cfg.signing.c2pa = true;
    if (!"CMS_AND_PDF".equals(cfg.signing.protection) && Common.nz(cfg.signing.tsaUrl).isBlank())
      cfg.signing.tsaUrl = SigningConfig.DEFAULT_TSA_URL;
    if (cfg.searchIndex == null) cfg.searchIndex = new SearchIndexConfig();
    if (cfg.accessibility == null) cfg.accessibility = new AccessibilityConfig();
    if (cfg.accessibility.pageLabels == null) cfg.accessibility.pageLabels = new PageLabelsConfig();
    if (cfg.accessibility.experimental == null) cfg.accessibility.experimental = new ExperimentalConfig();
    if (cfg.accessibility.language != null && cfg.accessibility.language.isBlank()) cfg.accessibility.language = null;
    if (cfg.accessibility.readingDirection != null && cfg.accessibility.readingDirection.isBlank()) cfg.accessibility.readingDirection = null;
    if (cfg.accessibility.pageLabels.frontMatterPrefix == null || cfg.accessibility.pageLabels.frontMatterPrefix.isBlank()) {
      cfg.accessibility.pageLabels.frontMatterPrefix = "Index-";
    }
    return cfg;
  }
}
