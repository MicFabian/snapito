package io.github.micfabian.snapito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SnapshotDiffTest {
  @Test
  void reportsNothingForEqualValues() {
    assertEquals("", SnapshotDiff.describe(Map.of("a", 1), Map.of("a", 1)));
  }

  @Test
  void reportsScalarDifferencesWithJsonPaths() {
    String diff = SnapshotDiff.describe(Map.of("amount", 1), Map.of("amount", 2));

    assertTrue(diff.startsWith("Differences (1):"));
    assertTrue(diff.contains("$.amount expected 1, but was 2"));
  }

  @Test
  void reportsMissingAndUnexpectedKeys() {
    String diff = SnapshotDiff.describe(Map.of("a", 1), Map.of("b", 2));

    assertTrue(diff.contains("$.a missing in actual (expected 1)"));
    assertTrue(diff.contains("$.b unexpected in actual (actual 2)"));
  }

  @Test
  void quotesNonIdentifierKeys() {
    String diff = SnapshotDiff.describe(Map.of("a-b", 1), Map.of("a-b", 2));

    assertTrue(diff.contains("$['a-b'] expected 1, but was 2"));
  }

  @Test
  void reportsListSizeAndElementDifferences() {
    String diff = SnapshotDiff.describe(List.of(1, 2), List.of(1, 3, 4));

    assertTrue(diff.contains("$ size mismatch: expected 2, but was 3"));
    assertTrue(diff.contains("$[1] expected 2, but was 3"));
  }

  @Test
  void reportsTypeMismatches() {
    String diff = SnapshotDiff.describe(Map.of("a", 1), Map.of("a", true));

    assertTrue(diff.contains("$.a type mismatch"));
    assertTrue(diff.contains("(Integer)"));
    assertTrue(diff.contains("(Boolean)"));
  }

  @Test
  void reportsMultilineTextByLineWithFirstDifferingCharacter() {
    String diff = SnapshotDiff.describe("alpha\nbeta", "alpha\nbeXa");

    assertTrue(diff.contains("$ line 2 expected 'beta', but was 'beXa'"));
    assertTrue(diff.contains("first difference at char 3"));
  }

  @Test
  void reportsLineCountMismatchForMultilineText() {
    String diff = SnapshotDiff.describe("a\nb", "a\nb\nc");

    assertTrue(diff.contains("line count mismatch: expected 2, but was 3"));
  }

  @Test
  void normalizesWindowsLineEndingsBeforeComparing() {
    assertEquals("", SnapshotDiff.describe("a\r\nb", "a\nb"));
  }

  @Test
  void honoursNumericTolerance() {
    assertEquals("", SnapshotDiff.describe(Map.of("rate", 1.0), Map.of("rate", 1.004), new BigDecimal("0.01")));
    assertTrue(SnapshotDiff.describe(Map.of("rate", 1.0), Map.of("rate", 1.5), new BigDecimal("0.01"))
      .contains("$.rate expected 1.0, but was 1.5"));
  }

  @Test
  void comparesArbitraryObjectsByTheirFields() {
    String diff = SnapshotDiff.describe(new Point(1, 2), new Point(1, 3));

    assertTrue(diff.contains("$.y expected 2, but was 3"));
  }

  @Test
  void comparesArraysAsLists() {
    String diff = SnapshotDiff.describe(new int[]{1, 2}, new int[]{1, 3});

    assertTrue(diff.contains("$[1] expected 2, but was 3"));
  }

  @Test
  void truncatesVeryLongValues() {
    String longValue = "x".repeat(400);
    String diff = SnapshotDiff.describe(Map.of("a", longValue), Map.of("a", "short"));

    assertTrue(diff.contains("..."));
    assertTrue(diff.length() < 400 + 200);
  }

  @Test
  void truncatesAfterTheConfiguredNumberOfDifferences() {
    Map<String, Object> expected = new LinkedHashMap<>();
    Map<String, Object> actual = new LinkedHashMap<>();
    for (int index = 0; index < 60; index++) {
      expected.put("key" + index, index);
      actual.put("key" + index, index + 1);
    }

    String diff = SnapshotDiff.describe(expected, actual);

    assertTrue(diff.startsWith("Differences (showing first 25):"));
    assertEquals(25, diff.lines().filter(line -> line.startsWith(" - ")).count());
  }

  @Test
  void escapesQuotesInRenderedStrings() {
    String diff = SnapshotDiff.describe(List.of("it's"), List.of("its"));

    assertTrue(diff.contains("\\'"));
  }

  @Test
  void handlesNullsOnEitherSide() {
    Map<String, Object> expected = new LinkedHashMap<>();
    expected.put("a", null);
    Map<String, Object> actual = new LinkedHashMap<>();
    actual.put("a", 1);

    String diff = SnapshotDiff.describe(expected, actual);

    assertTrue(diff.contains("$.a"));
    assertTrue(diff.contains("null"));
  }

  @Test
  void comparesNestedStructuresRecursively() {
    Map<String, Object> expected = Map.of("items", List.of(Map.of("sku", "a")));
    Map<String, Object> actual = Map.of("items", List.of(Map.of("sku", "b")));

    assertTrue(SnapshotDiff.describe(expected, actual).contains("$.items[0].sku expected 'a', but was 'b'"));
  }

  @Test
  void treatsMutableCollectionsTheSameAsImmutableOnes() {
    List<Object> expected = new ArrayList<>(List.of(1, 2));
    List<Object> actual = new ArrayList<>(List.of(1, 2));

    assertEquals("", SnapshotDiff.describe(expected, actual));
  }

  static class Point {
    private final int x;
    private final int y;

    Point(int x, int y) {
      this.x = x;
      this.y = y;
    }
  }
}
