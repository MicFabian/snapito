package io.github.micfabian.snapito;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class SnapshotDiff {
  private static final int MAX_DIFFERENCES = Integer.getInteger("snapito.diff.max", 25);
  private static final int MAX_VALUE_LENGTH = Integer.getInteger("snapito.diff.value.maxLength", 160);
  private static final Pattern SIMPLE_KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

  private SnapshotDiff() {
  }

  public static String describe(Object expected, Object actual) {
    return describe(expected, actual, BigDecimal.ZERO);
  }

  public static String describe(Object expected, Object actual, BigDecimal numericTolerance) {
    List<String> differences = new ArrayList<>();
    boolean[] truncated = new boolean[]{false};

    collectDifferences(
      "$",
      normalize(expected),
      normalize(actual),
      differences,
      truncated,
      numericTolerance == null ? BigDecimal.ZERO : numericTolerance);

    if (differences.isEmpty()) {
      return "";
    }

    StringBuilder builder = new StringBuilder();
    if (truncated[0]) {
      builder.append("Differences (showing first ").append(MAX_DIFFERENCES).append("):");
    } else {
      builder.append("Differences (").append(differences.size()).append("):");
    }
    for (String difference : differences) {
      builder.append(System.lineSeparator()).append(" - ").append(difference);
    }
    return builder.toString();
  }

  private static void collectDifferences(
      String path,
      Object expected,
      Object actual,
      List<String> differences,
      boolean[] truncated,
      BigDecimal numericTolerance) {
    if (differences.size() >= MAX_DIFFERENCES) {
      truncated[0] = true;
      return;
    }
    if (Objects.equals(expected, actual)) {
      return;
    }
    if (expected instanceof Number left && actual instanceof Number right && equalNumbers(left, right, numericTolerance)) {
      return;
    }
    if (expected instanceof CharSequence left && actual instanceof CharSequence right) {
      compareText(path, left.toString(), right.toString(), differences, truncated);
      return;
    }
    if (expected instanceof Map<?, ?> left && actual instanceof Map<?, ?> right) {
      compareMaps(path, left, right, differences, truncated, numericTolerance);
      return;
    }
    if (expected instanceof List<?> left && actual instanceof List<?> right) {
      compareLists(path, left, right, differences, truncated, numericTolerance);
      return;
    }
    if (typeOf(expected) != typeOf(actual)) {
      addDifference(differences, truncated, path + " type mismatch: expected " + formatValue(expected)
        + " (" + typeName(expected) + "), but was " + formatValue(actual) + " (" + typeName(actual) + ")");
      return;
    }
    addDifference(differences, truncated, path + " expected " + formatValue(expected) + ", but was " + formatValue(actual));
  }

  private static void compareText(String path, String expected, String actual, List<String> differences, boolean[] truncated) {
    String normalizedExpected = expected.replace("\r\n", "\n");
    String normalizedActual = actual.replace("\r\n", "\n");
    if (normalizedExpected.equals(normalizedActual)) {
      return;
    }

    if (!normalizedExpected.contains("\n") && !normalizedActual.contains("\n")) {
      addDifference(differences, truncated,
        path + " expected " + formatValue(normalizedExpected) + ", but was " + formatValue(normalizedActual));
      return;
    }

    String[] expectedLines = normalizedExpected.split("\n", -1);
    String[] actualLines = normalizedActual.split("\n", -1);

    if (expectedLines.length != actualLines.length) {
      addDifference(differences, truncated,
        path + " line count mismatch: expected " + expectedLines.length + ", but was " + actualLines.length);
    }

    int maxLine = Math.min(expectedLines.length, actualLines.length);
    for (int index = 0; index < maxLine; index++) {
      if (differences.size() >= MAX_DIFFERENCES) {
        truncated[0] = true;
        return;
      }
      String expectedLine = expectedLines[index];
      String actualLine = actualLines[index];
      if (expectedLine.equals(actualLine)) {
        continue;
      }
      int charIndex = firstDifference(expectedLine, actualLine);
      addDifference(differences, truncated,
        path + " line " + (index + 1) + " expected " + formatValue(expectedLine)
          + ", but was " + formatValue(actualLine) + " (first difference at char " + (charIndex + 1) + ")");
    }
  }

  private static void compareMaps(
      String path,
      Map<?, ?> expected,
      Map<?, ?> actual,
      List<String> differences,
      boolean[] truncated,
      BigDecimal numericTolerance) {
    List<Object> keys = new ArrayList<>(expected.keySet());
    for (Object key : actual.keySet()) {
      if (!keys.contains(key)) {
        keys.add(key);
      }
    }
    keys.sort(Comparator.comparing(String::valueOf));

    for (Object key : keys) {
      if (differences.size() >= MAX_DIFFERENCES) {
        truncated[0] = true;
        return;
      }
      String keyPath = pathForKey(path, key);
      boolean hasExpected = expected.containsKey(key);
      boolean hasActual = actual.containsKey(key);

      if (hasExpected && !hasActual) {
        addDifference(differences, truncated,
          keyPath + " missing in actual (expected " + formatValue(expected.get(key)) + ")");
        continue;
      }
      if (!hasExpected) {
        addDifference(differences, truncated,
          keyPath + " unexpected in actual (actual " + formatValue(actual.get(key)) + ")");
        continue;
      }
      collectDifferences(keyPath, normalize(expected.get(key)), normalize(actual.get(key)), differences, truncated, numericTolerance);
    }
  }

  private static void compareLists(
      String path,
      List<?> expected,
      List<?> actual,
      List<String> differences,
      boolean[] truncated,
      BigDecimal numericTolerance) {
    if (expected.size() != actual.size()) {
      addDifference(differences, truncated,
        path + " size mismatch: expected " + expected.size() + ", but was " + actual.size());
    }
    int maxIndex = Math.min(expected.size(), actual.size());
    for (int index = 0; index < maxIndex; index++) {
      if (differences.size() >= MAX_DIFFERENCES) {
        truncated[0] = true;
        return;
      }
      collectDifferences(path + "[" + index + "]",
        normalize(expected.get(index)), normalize(actual.get(index)), differences, truncated, numericTolerance);
    }
  }

  private static void addDifference(List<String> differences, boolean[] truncated, String difference) {
    if (differences.size() >= MAX_DIFFERENCES) {
      truncated[0] = true;
      return;
    }
    differences.add(difference);
  }

  static Object normalize(Object value) {
    return normalize(value, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
  }

  private static Object normalize(Object value, java.util.Set<Object> visited) {
    if (value == null) {
      return null;
    }
    boolean composite = value instanceof Map<?, ?> || value instanceof List<?> || value.getClass().isArray();
    if (composite && !visited.add(value)) {
      return "<cycle>";
    }
    if (value instanceof Map<?, ?> map) {
      Map<Object, Object> normalized = new LinkedHashMap<>();
      map.forEach((key, item) -> normalized.put(key, normalize(item, visited)));
      return normalized;
    }
    if (value instanceof List<?> list) {
      List<Object> normalized = new ArrayList<>(list.size());
      for (Object item : list) {
        normalized.add(normalize(item, visited));
      }
      return normalized;
    }
    if (value.getClass().isArray()) {
      int size = Array.getLength(value);
      List<Object> normalized = new ArrayList<>(size);
      for (int index = 0; index < size; index++) {
        normalized.add(normalize(Array.get(value, index), visited));
      }
      return normalized;
    }
    if (shouldNormalizeObjectFields(value)) {
      if (!visited.add(value)) {
        return "<cycle>";
      }
      return normalizeObjectFields(value, visited);
    }
    return value;
  }

  private static Map<String, Object> normalizeObjectFields(Object value, java.util.Set<Object> visited) {
    Map<String, Object> normalized = new LinkedHashMap<>();
    Class<?> currentClass = value.getClass();
    while (currentClass != null && currentClass != Object.class) {
      for (Field field : currentClass.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
          continue;
        }
        String name = field.getName();
        if (name.startsWith("$") || name.startsWith("this$") || normalized.containsKey(name)) {
          continue;
        }
        try {
          field.setAccessible(true);
          normalized.put(name, normalize(field.get(value), visited));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
          normalized.put(name, "<inaccessible>");
        }
      }
      currentClass = currentClass.getSuperclass();
    }
    return normalized;
  }

  private static String pathForKey(String path, Object key) {
    String name = String.valueOf(key);
    if (SIMPLE_KEY.matcher(name).matches()) {
      return path + "." + name;
    }
    String escaped = name.replace("\\", "\\\\").replace("'", "\\'");
    return path + "['" + escaped + "']";
  }

  private static String formatValue(Object value) {
    if (value == null) {
      return "null";
    }
    String rendered;
    if (value instanceof CharSequence sequence) {
      rendered = "'" + sequence.toString().replace("\\", "\\\\").replace("'", "\\'") + "'";
    } else {
      rendered = String.valueOf(value);
    }
    if (rendered.length() > MAX_VALUE_LENGTH) {
      return rendered.substring(0, MAX_VALUE_LENGTH - 3) + "...";
    }
    return rendered;
  }

  private static Class<?> typeOf(Object value) {
    return value == null ? null : value.getClass();
  }

  private static String typeName(Object value) {
    return value == null ? "null" : value.getClass().getSimpleName();
  }

  private static int firstDifference(String left, String right) {
    int max = Math.min(left.length(), right.length());
    for (int index = 0; index < max; index++) {
      if (left.charAt(index) != right.charAt(index)) {
        return index;
      }
    }
    return max;
  }

  private static boolean shouldNormalizeObjectFields(Object value) {
    Class<?> type = value.getClass();
    if (type.isPrimitive() || type.isEnum()) {
      return false;
    }
    if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean
      || value instanceof Character || value instanceof Enum<?>) {
      return false;
    }
    String name = type.getName();
    return !name.startsWith("java.") && !name.startsWith("javax.") && !name.startsWith("jdk.") && !name.startsWith("sun.");
  }

  private static boolean equalNumbers(Number expected, Number actual, BigDecimal tolerance) {
    BigDecimal left = new BigDecimal(expected.toString());
    BigDecimal right = new BigDecimal(actual.toString());
    return left.subtract(right).abs().compareTo(tolerance) <= 0;
  }
}
