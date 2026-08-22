package io.github.micfabian.snapito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.micfabian.snapito.comparison.ArrayComparison;
import io.github.micfabian.snapito.comparison.CsvComparison;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TabularComparisonTest {
  @Test
  void parsesEmptyFieldsAndTrailingSeparators() {
    assertEquals(List.of(List.of("a", "", "c")), rows("a,,c"));
    assertEquals(List.of(List.of("a", "")), rows("a,"));
  }

  @Test
  void parsesQuotedFieldsContainingNewlines() {
    assertEquals(List.of(List.of("a\nb", "c")), rows("\"a\nb\",c"));
  }

  @Test
  void treatsCarriageReturnLineFeedAsOneRowBreak() {
    assertEquals(List.of(List.of("a", "b"), List.of("c", "d")), rows("a,b\r\nc,d"));
  }

  @Test
  void treatsALoneCarriageReturnAsARowBreak() {
    assertEquals(List.of(List.of("a"), List.of("b")), rows("a\rb"));
  }

  @Test
  void returnsNoRowsForEmptyInput() {
    assertEquals(List.of(), rows(""));
  }

  @Test
  void treatsNullInputAsNoRows() {
    assertEquals(List.of(), new CsvComparison().toRows(null));
  }

  @Test
  void rejectsAQuoteThatStartsMidField() {
    assertThrows(IllegalArgumentException.class, () -> rows("a\"b"));
  }

  @Test
  void rejectsAnUnterminatedQuote() {
    assertThrows(IllegalArgumentException.class, () -> rows("\"abc"));
  }

  @Test
  void rejectsInputThatIsNeitherTextNorRows() {
    assertThrows(IllegalArgumentException.class, () -> new CsvComparison().toRows(42));
  }

  @Test
  void rendersNullCellsAsEmptyFields() {
    byte[] stored = new CsvComparison().beforeStore(List.of(Arrays.asList("a", null, "c")));

    assertEquals("a,,c", new String(stored, StandardCharsets.UTF_8));
  }

  @Test
  void keepsRaggedRowsAsWritten() {
    assertEquals(List.of(List.of("a", "b"), List.of("c")), rows("a,b\nc"));
  }

  @Test
  void supportsAnAlternativeColumnSeparator() {
    assertEquals(List.of(List.of("a", "b")), new CsvComparison().separatedBy(';').toRows("a;b"));
  }

  @Test
  void treatsAScalarRowAsASingleCell() {
    assertEquals(List.of(List.of("42")), new CsvComparison().toRows(List.of(42)));
  }

  @Test
  void roundTripsThroughStoreAndParseUnchanged() {
    CsvComparison comparison = new CsvComparison();
    List<List<String>> original = List.of(List.of("a\nb", "c,d"), List.of("e\"f", "g"));

    String stored = new String(comparison.beforeStore(original), StandardCharsets.UTF_8);

    assertEquals(original, comparison.beforeComparison(stored));
  }

  @Test
  void roundsNumbersHalfUp() {
    assertEquals("1.235", new ArrayComparison().rounded(3).toCsv(List.of(List.of(1.2345))));
  }

  @Test
  void clampsNumbersAtBothBoundaries() {
    ArrayComparison comparison = new ArrayComparison().clampedTo(0, 10);

    assertEquals("0,10", comparison.toCsv(List.of(List.of(-5, 50))));
  }

  @Test
  void stripsTrailingZerosFromRoundedNumbers() {
    assertEquals("1.5", new ArrayComparison().rounded(4).toCsv(List.of(List.of(1.5))));
  }

  @Test
  void rendersIgnoredValuesAsEmptyCells() {
    assertEquals("a,,c", new ArrayComparison().ignoring("skip").toCsv(List.of(List.of("a", "skip", "c"))));
  }

  @Test
  void dropsRowsThatAreEntirelyBlank() {
    assertEquals("a", new ArrayComparison().toCsv(List.of(List.of("a"), List.of("", "  "))));
  }

  @Test
  void treatsASingleScalarAsOneRow() {
    assertEquals("a", new ArrayComparison().toCsv("a"));
  }

  @Test
  void rendersPrimitiveArraysOfNumbers() {
    assertEquals("1,2", new ArrayComparison().rounded(0).toCsv(new int[][]{{1, 2}}));
  }

  @Test
  void restoresStoredArrayTextVerbatim() {
    ArrayComparison comparison = new ArrayComparison();
    byte[] stored = comparison.beforeStore(List.of(List.of("a", "b")));

    assertEquals("a,b", comparison.afterRestore(stored));
  }

  @Test
  void keepsStoredCsvStableAcrossAReadWriteCycle() {
    ArrayComparison comparison = new ArrayComparison().rounded(2);
    String first = comparison.toCsv(List.of(List.of(1.005, "x")));

    assertTrue(first.startsWith("1.01"), "Expected half-up rounding but got " + first);
    assertEquals(first, comparison.toCsv(List.of(List.of(new java.math.BigDecimal("1.01"), "x"))));
  }

  private static List<List<String>> rows(String text) {
    return new CsvComparison().toRows(text);
  }
}
