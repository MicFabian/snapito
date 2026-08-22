package io.github.micfabian.snapito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.micfabian.snapito.comparison.ArrayComparison;
import io.github.micfabian.snapito.comparison.BinaryComparison;
import io.github.micfabian.snapito.comparison.CsvComparison;
import io.github.micfabian.snapito.comparison.HtmlComparison;
import io.github.micfabian.snapito.comparison.JsonComparison;
import io.github.micfabian.snapito.comparison.PngComparison;
import io.github.micfabian.snapito.comparison.TextComparison;
import io.github.micfabian.snapito.comparison.XmlComparison;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ComparisonsTest {
  @Test
  void detectsJsonText() {
    assertInstanceOf(JsonComparison.class, Comparisons.detect("{\"a\":1}"));
  }

  @Test
  void detectsXmlText() {
    assertInstanceOf(XmlComparison.class, Comparisons.detect("<root><item>1</item></root>"));
  }

  @Test
  void detectsHtmlBeforeXml() {
    assertInstanceOf(HtmlComparison.class, Comparisons.detect("<div><span>hi</span></div>"));
  }

  @Test
  void detectsMultilineCsv() {
    assertInstanceOf(CsvComparison.class, Comparisons.detect("sku,name\n1,widget"));
  }

  @Test
  void detectsPlainText() {
    assertInstanceOf(TextComparison.class, Comparisons.detect("hello world"));
  }

  @Test
  void detectsCollectionsAsArrays() {
    assertInstanceOf(ArrayComparison.class, Comparisons.detect(List.of(1, 2, 3)));
  }

  @Test
  void detectsPngSignature() throws IOException {
    assertInstanceOf(PngComparison.class, Comparisons.detect(png(2, 2, Color.RED)));
  }

  @Test
  void fallsBackToBinaryForUnknownBytes() {
    assertInstanceOf(BinaryComparison.class, Comparisons.detect(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9}));
  }

  @Test
  void fallsBackToJsonForArbitraryObjects() {
    assertInstanceOf(JsonComparison.class, Comparisons.detect(new Payment("abc", 12)));
  }

  @Test
  void jsonExcludesPropertiesEverywhereInTheTree() {
    JsonComparison comparison = Comparisons.jsonExcludingProperties("id");
    Object normalized = comparison.beforeComparison(
      Map.of("id", "x", "items", List.of(Map.of("id", "y", "sku", "s1"))));

    assertEquals(Map.of("items", List.of(Map.of("sku", "s1"))), normalized);
  }

  @Test
  void jsonExcludesPaths() {
    JsonComparison comparison = Comparisons.jsonExcludingPaths("$.items[*].createdAt");
    Object normalized = comparison.beforeComparison(
      Map.of("items", List.of(Map.of("createdAt", "now", "sku", "s1"))));

    assertEquals(Map.of("items", List.of(Map.of("sku", "s1"))), normalized);
  }

  @Test
  void jsonExcludesTypes() {
    JsonComparison comparison = Comparisons.jsonExcludingTypes(Instant.class);
    Object normalized = comparison.beforeComparison(new Payment("abc", 12, Instant.parse("2026-01-01T00:00:00Z")));

    assertEquals(Map.of("reference", "abc", "amount", java.math.BigDecimal.valueOf(12)), normalized);
  }

  @Test
  void jsonSortsUnorderedPaths() {
    JsonComparison comparison = Comparisons.json(json -> json.unordered("$.roles"));
    Object normalized = comparison.beforeComparison(Map.of("roles", List.of("writer", "admin")));

    assertEquals(Map.of("roles", List.of("admin", "writer")), normalized);
  }

  @Test
  void jsonSortsCollectionsByField() {
    JsonComparison comparison = Comparisons.json(json -> json.sortedBy("$.items", "sku"));
    Object normalized = comparison.beforeComparison(
      Map.of("items", List.of(Map.of("sku", "b"), Map.of("sku", "a"))));

    assertEquals(Map.of("items", List.of(Map.of("sku", "a"), Map.of("sku", "b"))), normalized);
  }

  @Test
  void jsonReplacesMatchingText() {
    JsonComparison comparison = Comparisons.json(json -> json.replacing("[0-9]{4}", "<year>"));
    Object normalized = comparison.beforeComparison(Map.of("label", "since 2026"));

    assertEquals(Map.of("label", "since <year>"), normalized);
  }

  @Test
  void jsonToleratesNumericDrift() {
    JsonComparison comparison = Comparisons.json(json -> json.within(0.01));

    assertTrue(comparison.matches(
      comparison.beforeComparison(Map.of("rate", 1.001)),
      comparison.beforeComparison(Map.of("rate", 1.005))));
    assertFalse(comparison.matches(
      comparison.beforeComparison(Map.of("rate", 1.0)),
      comparison.beforeComparison(Map.of("rate", 1.5))));
  }

  @Test
  void jsonCopyKeepsConfigurationIndependent() {
    JsonComparison base = Comparisons.json(json -> json.excludingProperties("id"));
    JsonComparison derived = base.with(json -> json.excludingProperties("createdAt"));

    assertEquals(Map.of("keep", true),
      derived.beforeComparison(Map.of("id", 1, "createdAt", "now", "keep", true)));
    assertEquals(Map.of("createdAt", "now", "keep", true),
      base.beforeComparison(Map.of("id", 1, "createdAt", "now", "keep", true)));
  }

  @Test
  void textIgnoresWhitespaceByDefault() {
    TextComparison comparison = new TextComparison();
    assertEquals(comparison.beforeComparison("a b\nc"), comparison.beforeComparison("abc"));
  }

  @Test
  void textCanRespectWhitespaceAndCase() {
    TextComparison comparison = new TextComparison().ignoringWhitespace(false).ignoringCase(true);
    assertEquals("a b", comparison.beforeComparison("A B"));
  }

  @Test
  void xmlCanonicalizesIndentationAndDeclaration() {
    XmlComparison comparison = new XmlComparison();
    Object left = comparison.beforeComparison("<?xml version=\"1.0\"?><root>  <item>1</item>  </root>");
    Object right = comparison.beforeComparison("<root><item>1</item></root>");

    assertTrue(comparison.matches(left, right));
  }

  @Test
  void xmlRejectsNonStringInput() {
    assertThrows(IllegalArgumentException.class, () -> new XmlComparison().beforeComparison(Map.of("a", 1)));
  }

  @Test
  void htmlCanonicalizesAttributeOrderAndClassOrder() {
    HtmlComparison comparison = new HtmlComparison();
    assertTrue(comparison.matches(
      "<div id=\"a\" class=\"b a\">text</div>",
      "<div class=\"a b\" id=\"a\">text</div>"));
  }

  @Test
  void htmlIgnoresConfiguredAttributesAndElements() {
    HtmlComparison comparison = Comparisons.html(html -> html
      .ignoringAttributes("data-testid")
      .removingElements("script"));

    assertTrue(comparison.matches(
      "<div data-testid=\"one\"><script>x</script><span>hi</span></div>",
      "<div data-testid=\"two\"><span>hi</span></div>"));
  }

  @Test
  void csvRoundTripsQuotedValues() {
    CsvComparison comparison = new CsvComparison();
    byte[] stored = comparison.beforeStore(List.of(List.of("a,b", "c\"d")));

    assertEquals("\"a,b\",\"c\"\"d\"", new String(stored, StandardCharsets.UTF_8));
    assertEquals(List.of(List.of("a,b", "c\"d")), comparison.beforeComparison(new String(stored, StandardCharsets.UTF_8)));
  }

  @Test
  void csvRejectsMalformedQuoting() {
    assertThrows(IllegalArgumentException.class, () -> new CsvComparison().beforeComparison("a\"b,c"));
  }

  @Test
  void arrayRoundsAndClampsNumbers() {
    ArrayComparison comparison = new ArrayComparison().rounded(2).clampedTo(0, 10);
    assertEquals("1.23,10", comparison.toCsv(List.of(List.of(1.2345, 99))));
  }

  @Test
  void arrayDropsIgnoredValuesAndEmptyRows() {
    ArrayComparison comparison = new ArrayComparison().ignoring("skip");
    assertEquals("a", comparison.toCsv(List.of(List.of("a"), List.of("skip"))));
  }

  @Test
  void binaryComparesByContentNotIdentity() {
    BinaryComparison comparison = new BinaryComparison();
    assertTrue(comparison.matches(new byte[]{1, 2, 3}, new byte[]{1, 2, 3}));
    assertFalse(comparison.matches(new byte[]{1, 2, 3}, new byte[]{1, 2, 4}));
    assertTrue(comparison.describeDifference(new byte[]{1, 2, 3}, new byte[]{1, 2, 4})
      .contains("differs at byte 2"));
  }

  @Test
  void pngComparesDimensionsByDefault() throws IOException {
    PngComparison comparison = new PngComparison();
    assertTrue(comparison.matches(
      comparison.beforeComparison(png(4, 4, Color.RED)),
      comparison.beforeComparison(png(4, 4, Color.BLUE))));
    assertFalse(comparison.matches(
      comparison.beforeComparison(png(4, 4, Color.RED)),
      comparison.beforeComparison(png(5, 4, Color.RED))));
  }

  @Test
  void pngComparesPixelsWithTolerance() throws IOException {
    PngComparison strict = new PngComparison(PngComparison.Mode.PIXEL);
    assertFalse(strict.matches(
      strict.beforeComparison(png(2, 2, Color.RED)),
      strict.beforeComparison(png(2, 2, new Color(250, 0, 0)))));

    PngComparison tolerant = new PngComparison(PngComparison.Mode.PIXEL).tolerating(10);
    assertTrue(tolerant.matches(
      tolerant.beforeComparison(png(2, 2, Color.RED)),
      tolerant.beforeComparison(png(2, 2, new Color(250, 0, 0)))));
  }

  @Test
  void pngProducesVisualDiffArtifact() throws IOException {
    PngComparison comparison = new PngComparison(PngComparison.Mode.PIXEL);
    Map<String, byte[]> artifacts = comparison.differenceArtifacts(png(2, 2, Color.RED), png(2, 2, Color.BLUE));

    assertTrue(artifacts.containsKey(".diff.png"));
    assertTrue(artifacts.get(".diff.png").length > 0);
  }

  @Test
  void registersCustomProviders() {
    ComparisonProvider provider = new ComparisonProvider() {
      @Override
      public int priority() {
        return 1000;
      }

      @Override
      public Comparison detect(Object input) {
        return input instanceof String text && text.startsWith("---") ? new TextComparison() : null;
      }
    };

    Comparisons.register(provider);
    try {
      assertInstanceOf(TextComparison.class, Comparisons.detect("--- a: 1"));
    } finally {
      assertTrue(Comparisons.unregister(provider));
    }
  }

  static byte[] png(int width, int height, Color color) throws IOException {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        image.setRGB(x, y, color.getRGB());
      }
    }
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    return output.toByteArray();
  }

  public static class Payment {
    private final String reference;
    private final int amount;
    private final Instant createdAt;

    Payment(String reference, int amount) {
      this(reference, amount, null);
    }

    Payment(String reference, int amount, Instant createdAt) {
      this.reference = reference;
      this.amount = amount;
      this.createdAt = createdAt;
    }

    public String getReference() {
      return reference;
    }

    public int getAmount() {
      return amount;
    }

    public Instant getCreatedAt() {
      return createdAt;
    }
  }
}
