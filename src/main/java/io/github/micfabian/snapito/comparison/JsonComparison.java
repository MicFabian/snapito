package io.github.micfabian.snapito.comparison;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.micfabian.snapito.AdvancedComparison;
import io.github.micfabian.snapito.SnapshotDiff;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class JsonComparison implements AdvancedComparison {
  private final Set<String> excludedProperties = new LinkedHashSet<>();
  private final Set<Class<?>> excludedTypes = new LinkedHashSet<>();
  private final List<String> excludedPaths = new ArrayList<>();
  private final List<String> unorderedPaths = new ArrayList<>();
  private final Map<String, String> sortedPaths = new LinkedHashMap<>();
  private final Map<Pattern, String> regexReplacements = new LinkedHashMap<>();
  private BigDecimal numericTolerance = BigDecimal.ZERO;
  private boolean shared = false;

  public JsonComparison() {
  }

  public JsonComparison shared() {
    this.shared = true;
    return this;
  }

  private void checkMutable() {
    if (shared) {
      throw new IllegalStateException(SharedComparisons.MESSAGE);
    }
  }

  public static JsonComparison configured(Consumer<JsonComparison> configuration) {
    JsonComparison comparison = new JsonComparison();
    configuration.accept(comparison);
    return comparison;
  }

  @Override
  public String fileExtension() {
    return "json";
  }

  @Override
  public Object beforeComparison(Object input) {
    return normalize(input);
  }

  @Override
  public byte[] beforeStore(Object input) {
    return Json.writePretty(normalize(input)).getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public Object afterRestore(byte[] bytes) {
    return Json.parse(new String(bytes, StandardCharsets.UTF_8));
  }

  public JsonComparison excludingPaths(String... paths) {
    checkMutable();
    excludedPaths.addAll(Arrays.asList(paths));
    return this;
  }

  public JsonComparison excludingProperties(String... properties) {
    checkMutable();
    excludedProperties.addAll(Arrays.asList(properties));
    return this;
  }

  public JsonComparison excludingTypes(Class<?>... types) {
    checkMutable();
    excludedTypes.addAll(Arrays.asList(types));
    return this;
  }

  public JsonComparison unordered(String... paths) {
    checkMutable();
    unorderedPaths.addAll(Arrays.asList(paths));
    return this;
  }

  public JsonComparison sortedBy(String path, String field) {
    checkMutable();
    sortedPaths.put(path, field);
    return this;
  }

  public JsonComparison replacing(String regex) {
    return replacing(regex, "<redacted>");
  }

  public JsonComparison replacing(String regex, String replacement) {
    checkMutable();
    regexReplacements.put(Pattern.compile(regex), replacement);
    return this;
  }

  public JsonComparison within(Number tolerance) {
    checkMutable();
    numericTolerance = new BigDecimal(tolerance.toString()).abs();
    return this;
  }

  public JsonComparison copy() {
    JsonComparison copy = new JsonComparison();
    copyInto(copy);
    return copy;
  }

  protected void copyInto(JsonComparison copy) {
    copy.excludedProperties.addAll(excludedProperties);
    copy.excludedTypes.addAll(excludedTypes);
    copy.excludedPaths.addAll(excludedPaths);
    copy.unorderedPaths.addAll(unorderedPaths);
    copy.sortedPaths.putAll(sortedPaths);
    copy.regexReplacements.putAll(regexReplacements);
    copy.numericTolerance = numericTolerance;
  }

  public JsonComparison with(Consumer<JsonComparison> configuration) {
    JsonComparison derived = copy();
    configuration.accept(derived);
    return derived;
  }

  public BigDecimal getNumericTolerance() {
    return numericTolerance;
  }

  @Override
  public boolean matches(Object expected, Object actual) {
    return JsonNormalizers.equal(expected, actual, numericTolerance);
  }

  @Override
  public String describeDifference(Object expected, Object actual) {
    return SnapshotDiff.describe(expected, actual, numericTolerance);
  }

  @Override
  public Map<String, byte[]> differenceArtifacts(byte[] expectedBytes, byte[] actualBytes) {
    return Map.of();
  }

  private Object normalize(Object input) {
    Object plain = toPlain(input);
    Object normalized = JsonNormalizers.copy(plain);
    removeExcludedProperties(normalized);
    JsonNormalizers.removePaths(normalized, excludedPaths);
    JsonNormalizers.sortPaths(normalized, unorderedPaths);
    JsonNormalizers.sortPathsBy(normalized, sortedPaths);
    return JsonNormalizers.replaceRegex(normalized, regexReplacements);
  }

  private Object toPlain(Object input) {
    if (input instanceof CharSequence sequence) {
      return Json.parse(sequence.toString());
    }
    if (input instanceof JsonNode node) {
      return Json.toPlain(node);
    }
    return Json.convert(stripExcludedTypes(input));
  }

  private Object stripExcludedTypes(Object input) {
    if (input == null || excludedTypes.isEmpty()) {
      return input;
    }
    return new ExcludedTypeStripper(excludedTypes).strip(input);
  }

  private void removeExcludedProperties(Object node) {
    if (excludedProperties.isEmpty()) {
      return;
    }
    if (node instanceof Map<?, ?> map) {
      map.keySet().removeIf(key -> excludedProperties.contains(String.valueOf(key)));
      for (Object value : map.values()) {
        removeExcludedProperties(value);
      }
      return;
    }
    if (node instanceof Collection<?> collection) {
      for (Object value : collection) {
        removeExcludedProperties(value);
      }
    }
  }
}
