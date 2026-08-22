package io.github.micfabian.snapito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.micfabian.snapito.comparison.JsonComparison;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonNormalizersTest {
  @Test
  void removesANestedProperty() {
    assertEquals(
      Map.of("a", Map.of("keep", num(1))),
      normalize(json -> json.excludingPaths("$.a.drop"), mutable("a", mutable("drop", 1, "keep", 1))));
  }

  @Test
  void removesAWildcardPropertyAcrossAList() {
    Object result = normalize(
      json -> json.excludingPaths("$.items[*].secret"),
      mutable("items", list(mutable("secret", 1, "sku", "a"), mutable("secret", 2, "sku", "b"))));

    assertEquals(Map.of("items", List.of(Map.of("sku", "a"), Map.of("sku", "b"))), result);
  }

  @Test
  void removesASpecificListIndex() {
    Object result = normalize(json -> json.excludingPaths("$.items[1]"), mutable("items", list("a", "b", "c")));

    assertEquals(Map.of("items", List.of("a", "c")), result);
  }

  @Test
  void ignoresAnOutOfRangeListIndex() {
    Object result = normalize(json -> json.excludingPaths("$.items[9]"), mutable("items", list("a")));

    assertEquals(Map.of("items", List.of("a")), result);
  }

  @Test
  void clearsAWholeMapWithATrailingWildcard() {
    Object result = normalize(json -> json.excludingPaths("$.a.*"), mutable("a", mutable("x", 1, "y", 2)));

    assertEquals(Map.of("a", Map.of()), result);
  }

  @Test
  void ignoresAPathThatDoesNotExist() {
    Object result = normalize(json -> json.excludingPaths("$.nope.deeper"), mutable("a", 1));

    assertEquals(Map.of("a", num(1)), result);
  }

  @Test
  void treatsTheRootPathAsANoOp() {
    Object result = normalize(json -> json.excludingPaths("$"), mutable("a", 1));

    assertEquals(Map.of("a", num(1)), result);
  }

  @Test
  void acceptsAPathWithoutTheLeadingDollar() {
    Object result = normalize(json -> json.excludingPaths("a.b"), mutable("a", mutable("b", 1, "c", 2)));

    assertEquals(Map.of("a", Map.of("c", num(2))), result);
  }

  @Test
  void acceptsALeadingListIndexWithoutTheDollar() {
    Object result = normalize(json -> json.excludingPaths("items[0]"), mutable("items", list("a", "b")));

    assertEquals(Map.of("items", List.of("b")), result);
  }

  @Test
  void rejectsAnUnparseablePathInsteadOfSilentlyExcludingTheWrongThing() {
    IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(
      IllegalArgumentException.class,
      () -> normalize(json -> json.excludingPaths("$.a."), mutable("a", mutable("b", 1))));

    assertTrue(error.getMessage().contains("Unparseable snapshot path"));
  }

  @Test
  void sortsANestedUnorderedList() {
    Object result = normalize(json -> json.unordered("$.a.roles"), mutable("a", mutable("roles", list("z", "a"))));

    assertEquals(Map.of("a", Map.of("roles", List.of("a", "z"))), result);
  }

  @Test
  void sortsListsOfNumbersByTheirRenderedValue() {
    Object result = normalize(json -> json.unordered("$.values"), mutable("values", list(10, 2)));

    assertEquals(List.of(num(10), num(2)), ((Map<?, ?>) result).get("values"));
  }

  @Test
  void sortsByAFieldThatIsMissingOnSomeEntries() {
    Object result = normalize(
      json -> json.sortedBy("$.items", "sku"),
      mutable("items", list(mutable("sku", "b"), mutable("other", "x"))));

    List<?> items = (List<?>) ((Map<?, ?>) result).get("items");
    assertEquals(2, items.size());
  }

  @Test
  void leavesANonListAloneWhenSortingByAPath() {
    Object result = normalize(json -> json.unordered("$.a"), mutable("a", "scalar"));

    assertEquals(Map.of("a", "scalar"), result);
  }

  @Test
  void appliesReplacementsInsideNestedStructures() {
    Object result = normalize(
      json -> json.replacing("[0-9]+", "<n>"),
      mutable("a", list(mutable("b", "id-42"))));

    assertEquals(Map.of("a", List.of(Map.of("b", "id-<n>"))), result);
  }

  @Test
  void treatsADollarInTheReplacementAsLiteralText() {
    Object result = normalize(json -> json.replacing("secret", "$0"), mutable("a", "secret"));

    assertEquals(Map.of("a", "$0"), result);
  }

  @Test
  void appliesEveryConfiguredReplacementInOrder() {
    Object result = normalize(
      json -> json.replacing("a", "b").replacing("b", "c"),
      mutable("k", "a"));

    assertEquals(Map.of("k", "c"), result);
  }

  @Test
  void comparesNumbersWithinToleranceAcrossNestedStructures() {
    JsonComparison comparison = Comparisons.json(json -> json.within(0.5));

    assertTrue(comparison.matches(
      comparison.beforeComparison(mutable("a", list(1.0))),
      comparison.beforeComparison(mutable("a", list(1.4)))));
    assertFalse(comparison.matches(
      comparison.beforeComparison(mutable("a", list(1.0))),
      comparison.beforeComparison(mutable("a", list(1.6)))));
  }

  @Test
  void treatsDifferentKeySetsAsUnequalEvenWithTolerance() {
    JsonComparison comparison = Comparisons.json(json -> json.within(100));

    assertFalse(comparison.matches(Map.of("a", 1), Map.of("b", 1)));
  }

  @Test
  void treatsDifferentListLengthsAsUnequalEvenWithTolerance() {
    JsonComparison comparison = Comparisons.json(json -> json.within(100));

    assertFalse(comparison.matches(List.of(1), List.of(1, 2)));
  }

  @Test
  void combinesExclusionsSortingAndReplacementsInOneNormalization() {
    Object result = normalize(
      json -> json
        .excludingPaths("$.items[*].createdAt")
        .sortedBy("$.items", "sku")
        .replacing("acc-[0-9]+", "<account>"),
      mutable("items", list(
        mutable("sku", "b", "createdAt", "now", "account", "acc-2"),
        mutable("sku", "a", "createdAt", "then", "account", "acc-1"))));

    assertEquals(
      Map.of("items", List.of(
        Map.of("sku", "a", "account", "<account>"),
        Map.of("sku", "b", "account", "<account>"))),
      result);
  }

  private static Object normalize(java.util.function.Consumer<JsonComparison> configuration, Object input) {
    return Comparisons.json(configuration).beforeComparison(input);
  }

  private static BigDecimal num(int value) {
    return BigDecimal.valueOf(value);
  }

  private static List<Object> list(Object... values) {
    return new ArrayList<>(java.util.Arrays.asList(values));
  }

  private static Map<String, Object> mutable(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int index = 0; index < pairs.length; index += 2) {
      map.put(String.valueOf(pairs[index]), pairs[index + 1]);
    }
    return map;
  }
}
