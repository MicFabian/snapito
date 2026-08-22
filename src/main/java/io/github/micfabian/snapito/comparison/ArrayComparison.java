package io.github.micfabian.snapito.comparison;

import io.github.micfabian.snapito.Comparison;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ArrayComparison implements Comparison {
  private BigDecimal minValue = BigDecimal.valueOf(Integer.MIN_VALUE);
  private BigDecimal maxValue = BigDecimal.valueOf(Integer.MAX_VALUE);
  private List<Object> ignoreValues = new ArrayList<>();
  private String columnSeparator = ",";
  private int rounding = 4;

  public ArrayComparison clampedTo(Number min, Number max) {
    this.minValue = new BigDecimal(min.toString());
    this.maxValue = new BigDecimal(max.toString());
    return this;
  }

  public ArrayComparison ignoring(Object... values) {
    this.ignoreValues = new ArrayList<>(Arrays.asList(values));
    return this;
  }

  public ArrayComparison separatedBy(String columnSeparator) {
    this.columnSeparator = columnSeparator;
    return this;
  }

  public ArrayComparison rounded(int rounding) {
    this.rounding = rounding;
    return this;
  }

  @Override
  public String fileExtension() {
    return "csv";
  }

  @Override
  public Object beforeComparison(Object input) {
    return toCsv(input);
  }

  @Override
  public byte[] beforeStore(Object input) {
    return toCsv(input).getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public Object afterRestore(byte[] bytes) {
    return new String(bytes, StandardCharsets.UTF_8);
  }

  public String toCsv(Object input) {
    return toList(input).stream()
      .map(this::normalizeRow)
      .filter(row -> !isEmptyLine(row))
      .map(row -> row.stream().map(value -> value == null ? "" : String.valueOf(value))
        .collect(Collectors.joining(columnSeparator)))
      .collect(Collectors.joining("\n"));
  }

  private List<Object> normalizeRow(Object row) {
    List<Object> values = new ArrayList<>();
    if (row == null) {
      values.add(null);
      return values;
    }
    if (row instanceof Collection<?> collection) {
      for (Object value : collection) {
        values.add(normalizeValue(value));
      }
      return values;
    }
    if (row.getClass().isArray()) {
      int size = Array.getLength(row);
      for (int index = 0; index < size; index++) {
        values.add(normalizeValue(Array.get(row, index)));
      }
      return values;
    }
    values.add(normalizeValue(row));
    return values;
  }

  private Object normalizeValue(Object value) {
    if (ignoreValues.contains(value)) {
      return null;
    }
    if (value instanceof Number number) {
      BigDecimal decimal = new BigDecimal(number.toString());
      if (decimal.compareTo(minValue) < 0) {
        decimal = minValue;
      }
      if (decimal.compareTo(maxValue) > 0) {
        decimal = maxValue;
      }
      return decimal.setScale(rounding, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
    return value;
  }

  private boolean isEmptyLine(Collection<Object> row) {
    return row.stream().noneMatch(value -> value != null && !String.valueOf(value).trim().isEmpty());
  }

  private List<Object> toList(Object input) {
    if (input instanceof Collection<?> collection) {
      return new ArrayList<>(collection);
    }
    if (input != null && input.getClass().isArray()) {
      int size = Array.getLength(input);
      List<Object> values = new ArrayList<>(size);
      for (int index = 0; index < size; index++) {
        values.add(Array.get(input, index));
      }
      return values;
    }
    List<Object> single = new ArrayList<>();
    single.add(input);
    return single;
  }
}
