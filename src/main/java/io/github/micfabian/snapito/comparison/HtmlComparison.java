package io.github.micfabian.snapito.comparison;

import io.github.micfabian.snapito.AdvancedComparison;
import io.github.micfabian.snapito.SnapshotDiff;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class HtmlComparison implements AdvancedComparison {
  private static final Pattern VOID_ELEMENTS = Pattern.compile(
    "(?i)<(area|base|br|col|embed|hr|img|input|link|meta|param|source|track|wbr)([^>]*?)/?>");
  private static final Pattern DOCTYPE = Pattern.compile("(?i)<!DOCTYPE[^>]*>");
  private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Pattern NAMED_ENTITY = Pattern.compile("&([a-zA-Z][a-zA-Z0-9]{1,31});");
  private static final Set<String> XML_ENTITIES = Set.of("amp", "lt", "gt", "quot", "apos");
  private static final int INDENT_WIDTH = 2;

  private final Set<String> ignoredAttributes = new LinkedHashSet<>();
  private final Set<String> removedElements = new LinkedHashSet<>();
  private boolean ignoreComments = true;
  private boolean ignoreWhitespace = true;
  private boolean shared = false;

  public HtmlComparison shared() {
    this.shared = true;
    return this;
  }

  private void checkMutable() {
    if (shared) {
      throw new IllegalStateException(SharedComparisons.MESSAGE);
    }
  }

  public HtmlComparison ignoringAttributes(String... attributes) {
    checkMutable();
    ignoredAttributes.addAll(Arrays.asList(attributes));
    return this;
  }

  public HtmlComparison removingElements(String... tags) {
    checkMutable();
    removedElements.addAll(Arrays.asList(tags));
    return this;
  }

  public HtmlComparison keepingComments() {
    checkMutable();
    ignoreComments = false;
    return this;
  }

  public HtmlComparison keepingWhitespace() {
    checkMutable();
    ignoreWhitespace = false;
    return this;
  }

  @Override
  public String fileExtension() {
    return "html";
  }

  @Override
  public Object beforeComparison(Object input) {
    return canonical(String.valueOf(input));
  }

  @Override
  public byte[] beforeStore(Object input) {
    return canonical(String.valueOf(input)).getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public Object afterRestore(byte[] bytes) {
    return new String(bytes, StandardCharsets.UTF_8);
  }

  @Override
  public boolean matches(Object expected, Object actual) {
    return canonical(String.valueOf(expected)).equals(canonical(String.valueOf(actual)));
  }

  @Override
  public String describeDifference(Object expected, Object actual) {
    List<String> expectedLines = canonical(String.valueOf(expected)).lines().collect(Collectors.toList());
    List<String> actualLines = canonical(String.valueOf(actual)).lines().collect(Collectors.toList());
    return SnapshotDiff.describe(expectedLines, actualLines);
  }

  @Override
  public Map<String, byte[]> differenceArtifacts(byte[] expectedBytes, byte[] actualBytes) {
    return Map.of();
  }

  public String canonical(String html) {
    String prepared = prepare(html);
    Document document;
    try {
      document = Xml.parse(prepared);
    } catch (RuntimeException ignored) {
      return normalizeText(prepared);
    }
    StringBuilder builder = new StringBuilder();
    render(document.getDocumentElement(), 0, builder);
    return builder.toString().trim();
  }

  private String prepare(String html) {
    String prepared = html;
    if (ignoreComments) {
      prepared = COMMENT.matcher(prepared).replaceAll("");
    }
    prepared = DOCTYPE.matcher(prepared).replaceAll("");
    prepared = VOID_ELEMENTS.matcher(prepared).replaceAll("<$1$2/>");
    prepared = resolveNamedEntities(prepared);
    return prepared.trim();
  }

  private static String resolveNamedEntities(String html) {
    java.util.regex.Matcher matcher = NAMED_ENTITY.matcher(html);
    StringBuilder resolved = new StringBuilder();
    while (matcher.find()) {
      String name = matcher.group(1);
      String replacement = XML_ENTITIES.contains(name)
        ? matcher.group()
        : HtmlEntities.resolve(name).orElseGet(() -> "&amp;" + name + ";");
      matcher.appendReplacement(resolved, java.util.regex.Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(resolved);
    return resolved.toString();
  }

  private void render(Element element, int depth, StringBuilder builder) {
    String name = element.getTagName().toLowerCase(Locale.ROOT);
    if (removedElements.stream().anyMatch(tag -> tag.equalsIgnoreCase(name))) {
      return;
    }

    String indent = " ".repeat(depth * INDENT_WIDTH);
    builder.append(indent).append('<').append(name);
    attributes(element).forEach((key, value) ->
      builder.append(' ').append(key).append("=\"").append(value).append('"'));
    builder.append('>').append(System.lineSeparator());

    String text = normalizeText(localText(element));
    if (!text.isEmpty()) {
      builder.append(indent).append(" ".repeat(INDENT_WIDTH)).append(text).append(System.lineSeparator());
    }

    NodeList children = element.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      Node child = children.item(index);
      if (child instanceof Element childElement) {
        render(childElement, depth + 1, builder);
      }
    }

    builder.append(indent).append("</").append(name).append('>').append(System.lineSeparator());
  }

  private Map<String, String> attributes(Element element) {
    Map<String, String> attributes = new TreeMap<>();
    NamedNodeMap map = element.getAttributes();
    for (int index = 0; index < map.getLength(); index++) {
      Node attribute = map.item(index);
      String name = attribute.getNodeName().toLowerCase(Locale.ROOT);
      if (ignoredAttributes.stream().anyMatch(ignored -> ignored.equalsIgnoreCase(name))) {
        continue;
      }
      attributes.put(name, normalizeAttribute(name, attribute.getNodeValue()));
    }
    return attributes;
  }

  private String normalizeAttribute(String name, String value) {
    if (!"class".equals(name)) {
      return value.trim();
    }
    List<String> classes = new ArrayList<>(Arrays.asList(WHITESPACE.split(value.trim())));
    classes.removeIf(String::isEmpty);
    classes.sort(String::compareTo);
    return String.join(" ", classes);
  }

  private static String localText(Element element) {
    StringBuilder text = new StringBuilder();
    NodeList children = element.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      Node child = children.item(index);
      if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
        if (text.length() > 0) {
          text.append(' ');
        }
        text.append(child.getTextContent());
      }
    }
    return text.toString();
  }

  private String normalizeText(String text) {
    return ignoreWhitespace ? WHITESPACE.matcher(text).replaceAll(" ").trim() : text.trim();
  }
}
