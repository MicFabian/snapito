package io.github.micfabian.snapito;

import io.github.micfabian.snapito.comparison.ArrayComparison;
import io.github.micfabian.snapito.comparison.BinaryComparison;
import io.github.micfabian.snapito.comparison.CsvComparison;
import io.github.micfabian.snapito.comparison.HtmlComparison;
import io.github.micfabian.snapito.comparison.JsonComparison;
import io.github.micfabian.snapito.comparison.PngComparison;
import io.github.micfabian.snapito.comparison.TextComparison;
import io.github.micfabian.snapito.comparison.XmlComparison;
import io.github.micfabian.snapito.mockito.InteractionComparison;
import java.util.function.Consumer;

public final class Comparisons {
  private static final ComparisonDetector DETECTOR = new ComparisonDetector();

  public static final JsonComparison JSON = new JsonComparison().shared();
  public static final JsonComparison OBJECT_AS_JSON = new JsonComparison().shared();
  public static final JsonComparison API_RESPONSE =
    new JsonComparison().excludingProperties("id", "createdAt", "lastModified").shared();
  public static final Comparison PNG = new PngComparison();
  public static final Comparison BINARY = new BinaryComparison();
  public static final Comparison CSV = new CsvComparison();
  public static final Comparison XML = new XmlComparison();
  public static final Comparison TXT = new TextComparison();
  public static final HtmlComparison HTML = new HtmlComparison().shared();
  public static final Comparison ARRAY = new ArrayComparison();
  public static final InteractionComparison INTERACTIONS = new InteractionComparison().shared();

  private Comparisons() {
  }

  public static Comparison png(PngComparison.Mode mode) {
    return new PngComparison(mode);
  }

  public static Comparison png(PngComparison.Mode mode, int channelTolerance) {
    return png(mode, channelTolerance, 0.0d);
  }

  public static Comparison png(PngComparison.Mode mode, int channelTolerance, double maxDifferentPixelRatio) {
    return new PngComparison(mode).tolerating(channelTolerance, maxDifferentPixelRatio);
  }

  public static JsonComparison jsonExcludingProperties(String... properties) {
    return new JsonComparison().excludingProperties(properties);
  }

  public static JsonComparison jsonExcludingTypes(Class<?>... types) {
    return new JsonComparison().excludingTypes(types);
  }

  public static JsonComparison jsonExcludingPaths(String... paths) {
    return new JsonComparison().excludingPaths(paths);
  }

  public static JsonComparison json(Consumer<JsonComparison> configuration) {
    return JsonComparison.configured(configuration);
  }

  public static HtmlComparison html(Consumer<HtmlComparison> configuration) {
    HtmlComparison comparison = new HtmlComparison();
    configuration.accept(comparison);
    return comparison;
  }

  public static InteractionComparison interactions(Consumer<InteractionComparison> configuration) {
    InteractionComparison comparison = new InteractionComparison();
    configuration.accept(comparison);
    return comparison;
  }

  public static Comparison detect(Object input) {
    return DETECTOR.detect(input);
  }

  public static void register(ComparisonProvider provider) {
    DETECTOR.register(provider);
  }

  public static boolean unregister(ComparisonProvider provider) {
    return DETECTOR.unregister(provider);
  }
}
