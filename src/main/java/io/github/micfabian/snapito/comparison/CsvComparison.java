package io.github.micfabian.snapito.comparison;

import io.github.micfabian.snapito.Comparison;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class CsvComparison implements Comparison {
  private char columnSeparator = ',';
  private String recordSeparator = "\n";

  public CsvComparison separatedBy(char columnSeparator) {
    this.columnSeparator = columnSeparator;
    return this;
  }

  public CsvComparison withRecordSeparator(String recordSeparator) {
    this.recordSeparator = recordSeparator;
    return this;
  }

  @Override
  public String fileExtension() {
    return "csv";
  }

  @Override
  public Object beforeComparison(Object input) {
    return toRows(input);
  }

  @Override
  public byte[] beforeStore(Object input) {
    return renderCsv(toRows(input)).getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public Object afterRestore(byte[] bytes) {
    return new String(bytes, StandardCharsets.UTF_8);
  }

  public List<List<String>> toRows(Object input) {
    if (input == null) {
      return List.of();
    }
    if (input instanceof CharSequence sequence) {
      return parseCsv(sequence.toString());
    }
    if (input instanceof Collection<?> collection) {
      return collection.stream().map(CsvComparison::normalizeRow).collect(Collectors.toList());
    }
    if (input.getClass().isArray()) {
      List<List<String>> rows = new ArrayList<>();
      int size = Array.getLength(input);
      for (int index = 0; index < size; index++) {
        rows.add(normalizeRow(Array.get(input, index)));
      }
      return rows;
    }
    throw new IllegalArgumentException(
      "CsvComparison input must provide CSV text or tabular rows, input was " + input.getClass());
  }

  private static List<String> normalizeRow(Object row) {
    if (row == null) {
      return List.of("");
    }
    if (row instanceof Collection<?> collection) {
      return collection.stream().map(CsvComparison::normalizeValue).collect(Collectors.toList());
    }
    if (row.getClass().isArray()) {
      List<String> values = new ArrayList<>();
      int size = Array.getLength(row);
      for (int index = 0; index < size; index++) {
        values.add(normalizeValue(Array.get(row, index)));
      }
      return values;
    }
    return List.of(normalizeValue(row));
  }

  private static String normalizeValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private List<List<String>> parseCsv(String input) {
    if (input.isEmpty()) {
      return List.of();
    }

    List<List<String>> rows = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean inQuotes = false;
    boolean justEndedRow = false;

    for (int index = 0; index < input.length(); index++) {
      char current = input.charAt(index);

      if (inQuotes) {
        if (current == '"') {
          if (index + 1 < input.length() && input.charAt(index + 1) == '"') {
            field.append('"');
            index++;
          } else {
            inQuotes = false;
          }
        } else {
          field.append(current);
        }
        justEndedRow = false;
        continue;
      }

      if (current == '"') {
        if (field.length() > 0) {
          throw invalidCsv();
        }
        inQuotes = true;
        justEndedRow = false;
        continue;
      }

      if (current == columnSeparator) {
        row.add(field.toString());
        field.setLength(0);
        justEndedRow = false;
        continue;
      }

      if (current == '\n' || current == '\r') {
        row.add(field.toString());
        rows.add(row);
        row = new ArrayList<>();
        field.setLength(0);
        justEndedRow = true;
        if (current == '\r' && index + 1 < input.length() && input.charAt(index + 1) == '\n') {
          index++;
        }
        continue;
      }

      field.append(current);
      justEndedRow = false;
    }

    if (inQuotes) {
      throw invalidCsv();
    }

    if (!justEndedRow || !row.isEmpty() || field.length() > 0) {
      row.add(field.toString());
      rows.add(row);
    }

    return rows;
  }

  private String renderCsv(List<List<String>> rows) {
    return rows.stream()
      .map(row -> row.stream().map(this::escapeValue).collect(Collectors.joining(String.valueOf(columnSeparator))))
      .collect(Collectors.joining(recordSeparator));
  }

  private String escapeValue(String value) {
    String normalized = value == null ? "" : value;
    boolean requiresQuotes = normalized.indexOf(columnSeparator) >= 0
      || normalized.contains("\"")
      || normalized.contains("\n")
      || normalized.contains("\r");

    if (!requiresQuotes) {
      return normalized;
    }
    return "\"" + normalized.replace("\"", "\"\"") + "\"";
  }

  private static IllegalArgumentException invalidCsv() {
    return new IllegalArgumentException("CsvComparison input must provide valid CSV text");
  }
}
