package io.github.micfabian.snapito;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.github.micfabian.snapito.comparison.BinaryComparison;
import io.github.micfabian.snapito.comparison.HtmlComparison;
import io.github.micfabian.snapito.comparison.PngComparison;
import io.github.micfabian.snapito.comparison.TextComparison;
import io.github.micfabian.snapito.mockito.Interactions;
import io.github.micfabian.snapito.mockito.InvocationReturns;
import io.github.micfabian.snapito.mockito.RecordingMocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RobustnessTest {
  @BeforeEach
  @AfterEach
  void resetReturns() {
    InvocationReturns.clear();
  }

  @Test
  void keepsReturnValuesOfDistinctMocksOfTheSameTypeApart() {
    Ledger primary = RecordingMocks.mock(Ledger.class);
    Ledger secondary = RecordingMocks.mock(Ledger.class);
    when(primary.balance("x")).thenReturn(100);
    when(secondary.balance("x")).thenReturn(200);

    primary.balance("x");
    secondary.balance("x");

    assertEquals(100, Interactions.defaults().record(primary).get(0).get("returnValue"));
    assertEquals(200, Interactions.defaults().record(secondary).get(0).get("returnValue"));
  }

  @Test
  void survivesSelfReferencingArguments() {
    List<Object> cycle = new ArrayList<>();
    cycle.add("head");
    cycle.add(cycle);

    Ledger ledger = RecordingMocks.mock(Ledger.class);
    ledger.accept(cycle);

    List<Map<String, Object>> recorded = assertDoesNotThrow(() -> Interactions.defaults().record(ledger));
    assertTrue(String.valueOf(recorded).contains("<cycle>"));
  }

  @Test
  void diffSurvivesSelfReferencingStructures() {
    Map<String, Object> left = new java.util.LinkedHashMap<>();
    left.put("self", left);
    Map<String, Object> right = new java.util.LinkedHashMap<>();
    right.put("self", right);

    assertDoesNotThrow(() -> SnapshotDiff.describe(left, right));
  }

  @Test
  void neverWritesAnExcludedTypeWhenItIsTheOnlyProperty() {
    var comparison = Comparisons.jsonExcludingTypes(Secret.class);

    Object normalized = comparison.beforeComparison(new OnlySecret());

    assertFalse(String.valueOf(normalized).contains("hunter2"),
      "An excluded type must never reach the snapshot, even when nothing else remains");
    assertEquals(Map.of(), normalized);
  }

  @Test
  void reportsMalformedPngSnapshotsClearly() {
    PngComparison comparison = new PngComparison(PngComparison.Mode.PIXEL);

    IllegalArgumentException error =
      assertThrows(IllegalArgumentException.class, () -> comparison.matches("", new byte[]{1, 2, 3}));

    assertTrue(error.getMessage().contains("expected decoded pixels"));
  }

  @Test
  void comparesPngPixelsWithoutBuildingAHugeIntermediateString() {
    PngComparison comparison = new PngComparison(PngComparison.Mode.PIXEL);

    Object decoded = comparison.beforeComparison(redPng());

    assertFalse(decoded instanceof CharSequence,
      "Pixel mode must not expand an image into a hex string; that cost ~1500x the PNG size");
    assertTrue(comparison.matches(decoded, comparison.beforeComparison(redPng())));
  }

  private static byte[] redPng() {
    try {
      java.awt.image.BufferedImage image =
        new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_ARGB);
      for (int y = 0; y < 8; y++) {
        for (int x = 0; x < 8; x++) {
          image.setRGB(x, y, java.awt.Color.RED.getRGB());
        }
      }
      var output = new java.io.ByteArrayOutputStream();
      javax.imageio.ImageIO.write(image, "png", output);
      return output.toByteArray();
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  @Test
  void reportsNonBinaryInputClearly() {
    IllegalArgumentException error =
      assertThrows(IllegalArgumentException.class, () -> new BinaryComparison().beforeStore("not bytes"));

    assertTrue(error.getMessage().contains("must provide a byte[]"));
  }

  @Test
  void appliesHtmlConfigurationEvenWhenNamedEntitiesArePresent() {
    HtmlComparison comparison = Comparisons.html(html -> html
      .ignoringAttributes("data-x")
      .removingElements("script"));

    String canonical = comparison.canonical("<div data-x=\"1\"><script>s</script><p>k&nbsp;</p></div>");

    assertFalse(canonical.contains("data-x"), "An ignored attribute must stay ignored when entities are present");
    assertFalse(canonical.contains("script"), "A removed element must stay removed when entities are present");
  }

  @Test
  void treatsUnknownEntitiesAsLiteralTextInsteadOfFailingToParse() {
    HtmlComparison comparison = new HtmlComparison();

    String canonical = comparison.canonical("<p>a&notARealEntity;b</p>");

    assertTrue(canonical.startsWith("<p>"), "Unknown entities must not disable canonicalization");
  }

  @Test
  void detectsShortStringsConsistentlyAtTheTextBoundary() {
    assertInstanceOf(io.github.micfabian.snapito.comparison.JsonComparison.class, Comparisons.detect("abcd"));
    assertInstanceOf(TextComparison.class, Comparisons.detect("abcde"));
  }

  @Test
  void fallsBackToJsonForNullAndEmptyInput() {
    assertInstanceOf(io.github.micfabian.snapito.comparison.JsonComparison.class, Comparisons.detect(null));
    assertInstanceOf(io.github.micfabian.snapito.comparison.JsonComparison.class, Comparisons.detect(""));
  }

  @Test
  void treatsAnEmptyOrTruncatedByteArrayAsBinary() {
    assertInstanceOf(BinaryComparison.class, Comparisons.detect(new byte[0]));
    assertInstanceOf(BinaryComparison.class,
      Comparisons.detect(new byte[]{(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47}));
  }

  @Test
  void doesNotSerializeTheValueWhenTheSnapshotAlreadyMatches() throws java.io.IOException {
    java.nio.file.Path root = java.nio.file.Files.createTempDirectory("snapito-lazy");
    SnapitoTestSupport.useTemporaryRoot(root);
    SnapitoTestSupport.enterTest("LazyTest", "matches");
    try {
      CountingComparison comparison = new CountingComparison();
      Snapito.expectNamed("lazy", "value", comparison);
      int afterWrite = comparison.stores;

      SnapitoTestSupport.enterTest("LazyTest", "matches");
      Snapito.expectNamed("lazy", "value", comparison);

      assertEquals(afterWrite, comparison.stores,
        "A matching snapshot must not serialize the value again; those bytes are only needed for artifacts");
    } finally {
      SnapitoTestSupport.leaveTest();
      Snapito.reloadConfiguration();
    }
  }

  @Test
  void stillSerializesTheValueWhenAMismatchNeedsArtifacts() throws java.io.IOException {
    java.nio.file.Path root = java.nio.file.Files.createTempDirectory("snapito-lazy-miss");
    SnapitoTestSupport.useTemporaryRoot(root);
    SnapitoTestSupport.enterTest("LazyTest", "mismatches");
    try {
      CountingComparison comparison = new CountingComparison();
      Snapito.expectNamed("lazy", "value", comparison);
      int afterWrite = comparison.stores;

      SnapitoTestSupport.enterTest("LazyTest", "mismatches");
      assertThrows(AssertionError.class, () -> Snapito.expectNamed("lazy", "changed", comparison));

      assertTrue(comparison.stores > afterWrite, "A mismatch must still produce the .actual artifact bytes");
    } finally {
      SnapitoTestSupport.leaveTest();
      Snapito.reloadConfiguration();
    }
  }

  static class CountingComparison extends TextComparison {
    int stores;

    @Override
    public byte[] beforeStore(Object input) {
      stores++;
      return super.beforeStore(input);
    }
  }

  @Test
  void doesNotMistakeProseForCsv() {
    assertInstanceOf(TextComparison.class, Comparisons.detect("Dear Bob,\nthanks for everything"));
  }

  @Test
  void stillDetectsRectangularCsv() {
    assertInstanceOf(io.github.micfabian.snapito.comparison.CsvComparison.class,
      Comparisons.detect("sku,name\n1,widget\n2,gadget"));
  }

  public interface Ledger {
    int balance(String account);

    void accept(Object payload);
  }

  public static class Secret {
    public String getToken() {
      return "hunter2";
    }
  }

  public static class OnlySecret {
    public Secret getSecret() {
      return new Secret();
    }
  }
}
