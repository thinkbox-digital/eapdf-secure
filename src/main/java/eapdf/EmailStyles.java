package eapdf;

import com.openhtmltopdf.css.parser.CSSParser;
import com.openhtmltopdf.css.sheet.MediaRule;
import com.openhtmltopdf.css.sheet.Ruleset;
import com.openhtmltopdf.css.sheet.StylesheetInfo;
import com.openhtmltopdf.css.newmatch.Selector;
import com.openhtmltopdf.layout.SharedContext;
import com.openhtmltopdf.swing.NaiveUserAgent;
import com.openhtmltopdf.util.ThreadCtx;
import java.io.StringReader;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jsoup.nodes.Document;

final class EmailStyles {
  static String visibleBodyText(Document document) {
    if (document.select("[style]").stream().noneMatch(element ->
        element.attr("style").toLowerCase(java.util.Locale.ROOT).contains("display"))) return document.body().text();
    Document visible = document.clone();
    boolean hasStylesheets = !document.select("style,link[rel=stylesheet]").isEmpty();
    CSSParser parser = new CSSParser((uri, message) -> {});
    for (var element : visible.select("[style]")) {
      if (!element.attr("style").toLowerCase(java.util.Locale.ROOT).contains("display")) continue;
      try {
        var sheet = parser.parseStylesheet("inline-email", StylesheetInfo.AUTHOR,
            new StringReader("*{" + element.attr("style") + "}"));
        if (sheet.getContents().size() != 1 || !(sheet.getContents().get(0) instanceof Ruleset rules)) continue;
        com.openhtmltopdf.css.sheet.PropertyDeclaration display = null;
        for (var property : rules.getPropertyDeclarations()) {
          if ("display".equals(property.getPropertyName())
              && (display == null || property.isImportant() || !display.isImportant())) display = property;
        }
        // An author stylesheet can override a non-important inline declaration.
        if (display != null && "none".equalsIgnoreCase(display.getValue().getCssText())
            && (display.isImportant() || !hasStylesheets)) element.remove();
      } catch (java.io.IOException | RuntimeException unsupportedStyle) {
        // Preserve the original choice when visibility cannot be established.
      }
    }
    return visible.body().text();
  }

  static String scoped(Document document, EmailRecord record) {
    SharedContext previous = null;
    try {
      previous = ThreadCtx.get().sharedContext();
    } catch (NullPointerException ignored) {}
    SharedContext context = new SharedContext();
    context.setUserAgentCallback(new NaiveUserAgent() {
      @Override
      public com.openhtmltopdf.resource.ImageResource getImageResource(String uri, com.openhtmltopdf.outputdevice.helper.ExternalResourceType type) {
        return null;
      }

      @Override
      public String resolveUri(String baseUri, String uri) {
        return uri;
      }
    });
    context.registerWithThread();
    try {
      return parse(document, record);
    } finally {
      ThreadCtx.get().setSharedContext(previous);
    }
  }

  private static String parse(Document document, EmailRecord record) {
    StringBuilder output = new StringBuilder();
    Set<String> problems = new LinkedHashSet<>();
    CSSParser parser = new CSSParser((uri, message) -> {
      if (problems.size() < 8) problems.add(message);
    });
    for (var style : document.select("style")) {
      String media = style.attr("media");
      if (!media.isBlank() && !media.matches("(?i).*\\b(all|print)\\b.*")) {
        problems.add("Unsupported stylesheet media: " + media);
        continue;
      }
      try {
        var sheet = parser.parseStylesheet("email", StylesheetInfo.AUTHOR,
            new StringReader(omitUnscopableRules(style.data(), problems)));
        if (!sheet.getImportRules().isEmpty() || !sheet.getFontFaceRules().isEmpty()) {
          problems.add("External CSS imports and custom font definitions were omitted by offline rendering policy");
        }
        appendRules(output, sheet.getContents(), problems);
      } catch (Exception error) {
        problems.add("Could not preserve email stylesheet: " + error.getMessage());
      }
    }
    if (!document.select("link[rel=stylesheet]").isEmpty()) problems.add("External stylesheets were omitted by offline rendering policy");
    if (!problems.isEmpty()) Common.warn(record, "Reduced CSS rendering: " + String.join("; ", problems));
    return output.toString();
  }

  private static String omitUnscopableRules(String css, Set<String> problems) {
    char[] supported = css.toCharArray();
    var blocks = new java.util.ArrayDeque<Integer>();
    int headerStart = 0;
    char quote = 0;
    boolean unsupported = false;
    for (int index = 0; index < css.length(); index++) {
      char current = css.charAt(index);
      if (current == '\\') {
        index++;
        continue;
      }
      if (quote != 0) {
        if (current == quote) quote = 0;
        continue;
      }
      if (current == '\'' || current == '"') {
        quote = current;
        continue;
      }
      if (current == '/' && index + 1 < css.length() && css.charAt(index + 1) == '*') {
        int end = css.indexOf("*/", index + 2);
        if (end < 0) break;
        index = end + 1;
        continue;
      }
      if (current == '+' || current == '~') unsupported = true;
      if (current == '{') {
        boolean omit = unsupported && !css.substring(headerStart, index).trim().startsWith("@");
        blocks.push(omit ? headerStart : -1);
        headerStart = index + 1;
        unsupported = false;
      } else if (current == '}') {
        if (!blocks.isEmpty()) {
          int start = blocks.pop();
          if (start >= 0) {
            java.util.Arrays.fill(supported, start, index + 1, ' ');
            problems.add("A rule with sibling or list-match selectors could not be safely scoped and was omitted");
          }
        }
        headerStart = index + 1;
        unsupported = false;
      } else if (current == ';') {
        headerStart = index + 1;
        unsupported = false;
      }
    }
    return new String(supported);
  }

  private static void appendRules(StringBuilder output, List<?> rules, Set<String> problems) {
    for (Object rule : rules) {
      if (rule instanceof Ruleset declarations) {
        for (var selector : declarations.getFSSelectors()) {
          String scoped = serializeSelector(selector)
              .replaceFirst("(?i)^html\\s*>?\\s+body(?=[\\s.#:\\[]|$)", "body")
              .replaceFirst("(?i)^(html|body|:root)(?=[\\s.#:\\[]|$)", ".ea-email-body");
          if (scoped.isBlank()) scoped = "*";
          output.append("#email-root ").append(scoped);
          output.append(" {");
          for (var property : declarations.getPropertyDeclarations()) {
            if (Set.of("content", "text-decoration").contains(property.getPropertyName())
                && property.getValue() instanceof com.openhtmltopdf.css.parser.PropertyValue value
                && value.getPropertyValueType() == com.openhtmltopdf.css.parser.PropertyValue.VALUE_TYPE_LIST) {
              // These values use spaces; upstream list toString() adds invalid brackets and commas.
              output.append(property.getPropertyName()).append(':');
              for (var part : value.getValues()) output.append(part.getCssText()).append(' ');
              output.append(property.isImportant() ? "!important;" : ";");
            } else if (Set.of("background-image", "background-repeat", "background-position",
                    "background-size", "background-attachment").contains(property.getPropertyName())
                && property.getValue() instanceof com.openhtmltopdf.css.parser.PropertyValue value
                && value.getPropertyValueType() == com.openhtmltopdf.css.parser.PropertyValue.VALUE_TYPE_LIST) {
              String name = property.getPropertyName();
              boolean paired = Set.of("background-position", "background-size").contains(name);
              output.append(name).append(':');
              var values = value.getValues();
              for (int index = 0; index < values.size(); index++) {
                if (index > 0) output.append(paired && index % 2 == 1 ? " " : ", ");
                String text = values.get(index).getCssText();
                output.append(text);
                // The renderer expands cover/contain to duplicate pairs internally.
                if ("background-size".equals(name) && Set.of("cover", "contain").contains(text)) index++;
              }
              output.append(property.isImportant() ? "!important;" : ";");
            } else if (Set.of("counter-reset", "counter-increment").contains(property.getPropertyName())
                && property.getValue() instanceof com.openhtmltopdf.css.parser.PropertyValue value
                && value.getPropertyValueType() == com.openhtmltopdf.css.parser.PropertyValue.VALUE_TYPE_COUNTERS) {
              output.append(property.getPropertyName()).append(':');
              for (var counter : value.getCounters())
                output.append(counter.getName()).append(' ').append(counter.getValue()).append(' ');
              output.append(property.isImportant() ? "!important;" : ";");
            } else {
              property.toCSS(output);
            }
          }
          output.append("}\n");
        }
      } else if (rule instanceof MediaRule media) {
        if (media.matches("print")) appendRules(output, media.getContents(), problems);
        else problems.add("Unsupported conditional stylesheet was omitted");
      } else {
        problems.add("Email page-layout rules were omitted to preserve archive navigation");
      }
    }
  }

  private static String serializeSelector(Selector selector) {
    StringBuilder serialized = new StringBuilder();
    selector.toCSS(serialized, new HashSet<>());
    String css = serialized.toString();
    Selector first = selector;
    while (first.getAncestorSelector() != null) first = first.getAncestorSelector();
    StringBuilder output = new StringBuilder();
    int offset = 0;
    for (Selector part = first; part != null; part = part.getChainedSelector()) {
      if (part.getAxis() == Selector.CHILD_AXIS && css.startsWith("> ", offset)) offset += 2;
      int end = selectorCompoundEnd(css, offset);
      if (output.length() > 0) output.append(part.getAxis() == Selector.CHILD_AXIS ? " > " : " ");
      output.append(end == offset ? "*" : css.substring(offset, end));
      // Upstream toCSS writes conditions such as :link, but omits these state flags.
      appendPseudoClass(output, part, Selector.VISITED_PSEUDOCLASS, "visited");
      appendPseudoClass(output, part, Selector.HOVER_PSEUDOCLASS, "hover");
      appendPseudoClass(output, part, Selector.ACTIVE_PSEUDOCLASS, "active");
      appendPseudoClass(output, part, Selector.FOCUS_PSEUDOCLASS, "focus");
      appendPseudoClass(output, part, Selector.MARKER_PSEUDOCLASS, "marker");
      appendPseudoClass(output, part, Selector.FIRST_FRAGMENT_PSEUDOCLASS, "-fs-first-fragment");
      appendPseudoClass(output, part, Selector.LAST_FRAGMENT_PSEUDOCLASS, "-fs-last-fragment");
      if (part.getPseudoElement() != null) output.append("::").append(part.getPseudoElement());
      offset = Math.min(css.length(), end + 1);
    }
    return output.toString().trim();
  }

  private static void appendPseudoClass(StringBuilder output, Selector selector, int flag, String name) {
    if (selector.isPseudoClass(flag)) output.append(':').append(name);
  }

  private static int selectorCompoundEnd(String css, int start) {
    int brackets = 0, parentheses = 0;
    char quote = 0;
    for (int index = start; index < css.length(); index++) {
      char current = css.charAt(index);
      if (current == '\\') { index++; continue; }
      if (quote != 0) {
        if (current == quote) quote = 0;
      } else if (current == '\'' || current == '"') quote = current;
      else if (current == '[') brackets++;
      else if (current == ']') brackets--;
      else if (current == '(') parentheses++;
      else if (current == ')') parentheses--;
      else if (Character.isWhitespace(current) && brackets == 0 && parentheses == 0) return index;
    }
    return css.length();
  }
}
