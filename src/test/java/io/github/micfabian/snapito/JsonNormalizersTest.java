package io.github.micfabian.snapito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.micfabian.snapito.comparison.JsonComparison;
import io.github.micfabian.snapito.comparison.ValueType;
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
  void ignoringArrayOrderSortsEveryListIncludingNestedOnes() {
    Object result = normalize(
      json -> json.ignoringArrayOrder(),
      mutable("b", list("z", "a"), "a", list(mutable("k", list(2, 1)), mutable("k", list(0)))));

    assertEquals(
      Map.of("a", List.of(Map.of("k", List.of(num(0))), Map.of("k", List.of(num(1), num(2)))), "b", List.of("a", "z")),
      result);
  }

  @Test
  void ignoringArrayOrderMakesDifferentlyOrderedDocumentsEqual() {
    JsonComparison comparison = JsonComparison.configured(json -> json.ignoringArrayOrder());
    Object left = comparison.beforeComparison("{\"items\":[{\"a\":1,\"b\":2},{\"a\":3}],\"t\":[\"x\",\"y\"]}");
    Object right = comparison.beforeComparison("{\"t\":[\"y\",\"x\"],\"items\":[{\"a\":3},{\"b\":2,\"a\":1}]}");

    assertTrue(comparison.matches(left, right));
  }

  @Test
  void maskingVolatileNumbersUuidsAndTimestampsInDocumentOrder() {
    JsonComparison comparison = JsonComparison.configured(json -> json.maskingVolatile());
    Object masked = comparison.beforeComparison(
      "{\"id\":\"3F2504E0-4F89-11D3-9A0C-0305E82C3301\",\"ref\":\"/orders/3f2504e0-4f89-11d3-9a0c-0305e82c3301/items\","
        + "\"other\":\"aaaaaaaa-4f89-11d3-9a0c-0305e82c3301\",\"at\":\"2026-11-24T10:15:30.123Z\",\"later\":\"2026-11-25T10:15:30+01:00\"}");

    assertEquals(
      Map.of(
        "id", "<uuid-1>",
        "ref", "/orders/<uuid-1>/items",
        "other", "<uuid-2>",
        "at", "<timestamp-1>",
        "later", "<timestamp-2>"),
      masked);
  }

  @Test
  void maskedDocumentsFromDifferentRunsMatchButChangedRelationshipsDoNot() {
    JsonComparison comparison = JsonComparison.configured(json -> json.maskingVolatile());
    Object first = comparison.beforeComparison(
      "{\"a\":\"11111111-1111-1111-1111-111111111111\",\"b\":\"11111111-1111-1111-1111-111111111111\",\"t\":\"2026-01-01T00:00:00Z\"}");
    Object second = comparison.beforeComparison(
      "{\"t\":\"2027-05-05T05:05:05Z\",\"b\":\"22222222-2222-2222-2222-222222222222\",\"a\":\"22222222-2222-2222-2222-222222222222\"}");
    Object different = comparison.beforeComparison(
      "{\"a\":\"33333333-3333-3333-3333-333333333333\",\"b\":\"44444444-4444-4444-4444-444444444444\",\"t\":\"2026-01-01T00:00:00Z\"}");

    assertTrue(comparison.matches(first, second));
    assertFalse(comparison.matches(first, different));
  }

  @Test
  void maskingIsIdempotentForAlreadyStoredBaselines() {
    JsonComparison comparison = JsonComparison.configured(json -> json.maskingVolatile());
    Object masked = comparison.beforeComparison("{\"id\":\"11111111-1111-1111-1111-111111111111\"}");
    Object restored = comparison.beforeComparison(comparison.afterRestore(comparison.beforeStore(masked)));

    assertEquals(masked, restored);
  }

  @Test
  void maskingCanBeLimitedToUuidsOrTimestamps() {
    Object onlyUuids = JsonComparison.configured(json -> json.maskingUuids()).beforeComparison(
      "{\"id\":\"11111111-1111-1111-1111-111111111111\",\"at\":\"2026-01-01T00:00:00Z\"}");

    assertEquals(Map.of("id", "<uuid-1>", "at", "2026-01-01T00:00:00Z"), onlyUuids);
  }

  @Test
  void onlyPathsKeepsJustTheSelectedParts() {
    JsonComparison comparison = JsonComparison.configured(json -> json.onlyPaths("$.items[*].name", "$.meta.total"));
    Object kept = comparison.beforeComparison(
      "{\"items\":[{\"name\":\"a\",\"price\":1},{\"name\":\"b\",\"price\":2}],\"meta\":{\"total\":2,\"page\":1},\"noise\":true}");

    assertEquals(
      Map.of("items", List.of(Map.of("name", "a"), Map.of("name", "b")), "meta", Map.of("total", num(2))),
      kept);
    assertEquals(kept, comparison.beforeComparison(comparison.afterRestore(comparison.beforeStore(kept))));
  }

  @Test
  void onlyPathsRejectsIndexPositions() {
    assertThrows(IllegalArgumentException.class, () -> new JsonComparison().onlyPaths("$.items[1].name"));
  }

  @Test
  void asTypeReplacesValuesOfTheRightTypeAndKeepsOthersVisible() {
    JsonComparison comparison = JsonComparison.configured(json -> json
      .asType("$.createdAt", ValueType.TIMESTAMP)
      .asType("$.items[*].id", ValueType.UUID));
    Object result = comparison.beforeComparison(
      "{\"createdAt\":\"2026-11-24T10:15:30Z\",\"items\":[{\"id\":\"11111111-1111-1111-1111-111111111111\"},{\"id\":\"not-a-uuid\"}]}");

    assertEquals(
      Map.of("createdAt", "<timestamp>", "items", List.of(Map.of("id", "<uuid>"), Map.of("id", "not-a-uuid"))),
      result);
  }

  @Test
  void shapeOnlyKeepsStructureAndTypesAndIsIdempotent() {
    JsonComparison comparison = JsonComparison.configured(json -> json.shapeOnly());
    Object shape = comparison.beforeComparison(
      "{\"id\":7,\"name\":\"x\",\"ok\":true,\"none\":null,\"tags\":[\"a\",\"b\"],\"rows\":[{\"v\":1},{\"v\":2}]}");

    assertEquals(
      Map.of(
        "id", "<number>", "name", "<string>", "ok", "<boolean>", "none", "<null>",
        "tags", List.of("<string>"), "rows", List.of(Map.of("v", "<number>"))),
      shape);
    assertEquals(shape, comparison.beforeComparison(comparison.afterRestore(comparison.beforeStore(shape))));
  }

  @Test
  void listOrderStillMattersByDefault() {
    JsonComparison comparison = new JsonComparison();

    assertFalse(comparison.matches(comparison.beforeComparison("[1,2]"), comparison.beforeComparison("[2,1]")));
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
