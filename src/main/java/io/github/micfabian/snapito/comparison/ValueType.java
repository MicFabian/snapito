package io.github.micfabian.snapito.comparison;

import java.math.BigDecimal;
import java.util.regex.Pattern;

public enum ValueType {
  UUID("uuid") {
    @Override
    public boolean accepts(Object value) {
      return value instanceof CharSequence text && UUID_PATTERN.matcher(text).matches();
    }
  },
  TIMESTAMP("timestamp") {
    @Override
    public boolean accepts(Object value) {
      return value instanceof CharSequence text && TIMESTAMP_PATTERN.matcher(text).matches();
    }
  },
  DATE("date") {
    @Override
    public boolean accepts(Object value) {
      return value instanceof CharSequence text && DATE_PATTERN.matcher(text).matches();
    }
  },
  INTEGER("integer") {
    @Override
    public boolean accepts(Object value) {
      if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
        return true;
      }
      if (value instanceof Number number) {
        BigDecimal decimal = new BigDecimal(number.toString());
        return decimal.signum() == 0 || decimal.stripTrailingZeros().scale() <= 0;
      }
      return false;
    }
  },
  NUMBER("number") {
    @Override
    public boolean accepts(Object value) {
      return value instanceof Number;
    }
  },
  BOOLEAN("boolean") {
    @Override
    public boolean accepts(Object value) {
      return value instanceof Boolean;
    }
  },
  TEXT("text") {
    @Override
    public boolean accepts(Object value) {
      return value instanceof CharSequence;
    }
  };

  private static final Pattern UUID_PATTERN = Pattern.compile(
    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
    "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?");
  private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

  private final String placeholder;

  ValueType(String placeholder) {
    this.placeholder = "<" + placeholder + ">";
  }

  public String placeholder() {
    return placeholder;
  }

  public abstract boolean accepts(Object value);
}
