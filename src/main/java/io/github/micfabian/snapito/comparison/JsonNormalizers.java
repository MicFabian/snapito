package io.github.micfabian.snapito.comparison;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JsonNormalizers {
  private static final Pattern UUID_VALUE = Pattern.compile(
    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  private static final Pattern TIMESTAMP_VALUE = Pattern.compile(
    "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?");
  private static final Pattern PATH_TOKEN = Pattern.compile("(?:\\.([^.\\[]+)|\\[(\\d+|\\*)])");

  private JsonNormalizers() {
  }

  @SuppressWarnings("unchecked")
  public static Object copy(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      map.forEach((key, item) -> result.put(String.valueOf(key), copy(item)));
      return result;
    }
    if (value instanceof Collection<?> collection) {
      List<Object> result = new ArrayList<>(collection.size());
      for (Object item : collection) {
        result.add(copy(item));
      }
      return result;
    }
    return value;
  }

  public static void removePaths(Object root, Collection<String> paths) {
    for (String path : paths) {
      remove(root, tokens(path), 0);
    }
  }

  public static void sortPaths(Object root, Collection<String> paths) {
    for (String path : paths) {
      sort(root, tokens(path), 0, null);
    }
  }

  @SuppressWarnings("unchecked")
  public static void sortAllLists(Object current) {
    if (current instanceof Map<?, ?> map) {
      for (Object item : map.values()) {
        sortAllLists(item);
      }
      return;
    }
    if (current instanceof List<?> rawList) {
      List<Object> list = (List<Object>) rawList;
      for (Object item : list) {
        sortAllLists(item);
      }
      list.sort((left, right) -> canonical(left).compareTo(canonical(right)));
    }
  }

  private static String canonical(Object value) {
    if (value == null || value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
      return String.valueOf(value);
    }
    return Json.write(sortedKeys(value));
  }

  private static Object sortedKeys(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> sorted = new java.util.TreeMap<>();
      map.forEach((key, item) -> sorted.put(String.valueOf(key), sortedKeys(item)));
      return sorted;
    }
    if (value instanceof List<?> list) {
      List<Object> result = new ArrayList<>(list.size());
      for (Object item : list) {
        result.add(sortedKeys(item));
      }
      return result;
    }
    return value;
  }

  public static void sortPathsBy(Object root, Map<String, String> paths) {
    paths.forEach((path, field) -> sort(root, tokens(path), 0, field));
  }

  @SuppressWarnings("unchecked")
  public static Object maskVolatile(Object value, boolean uuids, boolean timestamps) {
    if (!uuids && !timestamps) {
      return value;
    }
    VolatileMasker masker = new VolatileMasker(uuids, timestamps);
    return masker.mask(value);
  }

  private static final class VolatileMasker {
    private final boolean uuids;
    private final boolean timestamps;
    private final Map<String, Integer> uuidNumbers = new LinkedHashMap<>();
    private final Map<String, Integer> timestampNumbers = new LinkedHashMap<>();

    private VolatileMasker(boolean uuids, boolean timestamps) {
      this.uuids = uuids;
      this.timestamps = timestamps;
    }

    @SuppressWarnings("unchecked")
    private Object mask(Object value) {
      if (value instanceof Map<?, ?> map) {
        Map<String, Object> target = (Map<String, Object>) map;
        for (String key : new TreeMap<>(target).keySet()) {
          target.put(key, mask(target.get(key)));
        }
        return target;
      }
      if (value instanceof List<?> list) {
        List<Object> target = (List<Object>) list;
        for (int index = 0; index < target.size(); index++) {
          target.set(index, mask(target.get(index)));
        }
        return target;
      }
      if (value instanceof CharSequence sequence) {
        String text = sequence.toString();
        if (uuids) {
          text = replace(text, UUID_VALUE, uuidNumbers, "uuid", true);
        }
        if (timestamps) {
          text = replace(text, TIMESTAMP_VALUE, timestampNumbers, "timestamp", false);
        }
        return text;
      }
      return value;
    }

    private static String replace(
        String text,
        Pattern pattern,
        Map<String, Integer> numbers,
        String label,
        boolean caseInsensitive) {
      Matcher matcher = pattern.matcher(text);
      if (!matcher.find()) {
        return text;
      }
      StringBuilder result = new StringBuilder();
      int last = 0;
      do {
        String found = matcher.group();
        String key = caseInsensitive ? found.toLowerCase(java.util.Locale.ROOT) : found;
        int number = numbers.computeIfAbsent(key, ignored -> numbers.size() + 1);
        result.append(text, last, matcher.start()).append('<').append(label).append('-').append(number).append('>');
        last = matcher.end();
      } while (matcher.find());
      return result.append(text, last, text.length()).toString();
    }
  }

  public static Object replaceRegex(Object value, Map<Pattern, String> replacements) {
    if (replacements.isEmpty()) {
      return value;
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> target = (Map<String, Object>) map;
      for (Map.Entry<String, Object> entry : target.entrySet()) {
        entry.setValue(replaceRegex(entry.getValue(), replacements));
      }
      return target;
    }
    if (value instanceof List<?> list) {
      List<Object> target = (List<Object>) list;
      for (int index = 0; index < target.size(); index++) {
        target.set(index, replaceRegex(target.get(index), replacements));
      }
      return target;
    }
    if (value instanceof CharSequence sequence) {
      String normalized = sequence.toString();
      for (Map.Entry<Pattern, String> replacement : replacements.entrySet()) {
        normalized = replacement.getKey().matcher(normalized).replaceAll(Matcher.quoteReplacement(replacement.getValue()));
      }
      return normalized;
    }
    return value;
  }

  public static boolean equal(Object expected, Object actual, BigDecimal tolerance) {
    if (expected instanceof Number left && actual instanceof Number right && tolerance != null) {
      return new BigDecimal(left.toString()).subtract(new BigDecimal(right.toString())).abs().compareTo(tolerance) <= 0;
    }
    if (expected instanceof Map<?, ?> left && actual instanceof Map<?, ?> right) {
      if (!left.keySet().equals(right.keySet())) {
        return false;
      }
      for (Object key : left.keySet()) {
        if (!equal(left.get(key), right.get(key), tolerance)) {
          return false;
        }
      }
      return true;
    }
    if (expected instanceof List<?> left && actual instanceof List<?> right) {
      if (left.size() != right.size()) {
        return false;
      }
      for (int index = 0; index < left.size(); index++) {
        if (!equal(left.get(index), right.get(index), tolerance)) {
          return false;
        }
      }
      return true;
    }
    return java.util.Objects.equals(expected, actual);
  }

  @SuppressWarnings("unchecked")
  private static void remove(Object current, List<String> tokens, int index) {
    if (current == null || index >= tokens.size()) {
      return;
    }
    String token = tokens.get(index);
    boolean last = index == tokens.size() - 1;

    if (current instanceof Map<?, ?> rawMap) {
      Map<String, Object> map = (Map<String, Object>) rawMap;
      if ("*".equals(token)) {
        if (last) {
          map.clear();
        } else {
          for (Object item : new ArrayList<>(map.values())) {
            remove(item, tokens, index + 1);
          }
        }
      } else if (last) {
        map.remove(token);
      } else {
        remove(map.get(token), tokens, index + 1);
      }
      return;
    }

    if (current instanceof List<?> rawList) {
      List<Object> list = (List<Object>) rawList;
      if ("*".equals(token)) {
        if (last) {
          list.clear();
        } else {
          for (Object item : new ArrayList<>(list)) {
            remove(item, tokens, index + 1);
          }
        }
      } else if (isInteger(token)) {
        int position = Integer.parseInt(token);
        if (position < list.size()) {
          if (last) {
            list.remove(position);
          } else {
            remove(list.get(position), tokens, index + 1);
          }
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static void sort(Object current, List<String> tokens, int index, String field) {
    if (current == null) {
      return;
    }
    if (index >= tokens.size()) {
      if (current instanceof List<?> rawList) {
        List<Object> list = (List<Object>) rawList;
        list.sort((left, right) -> sortValue(left, field).compareTo(sortValue(right, field)));
      }
      return;
    }

    String token = tokens.get(index);
    if (current instanceof Map<?, ?> map) {
      if ("*".equals(token)) {
        for (Object item : map.values()) {
          sort(item, tokens, index + 1, field);
        }
      } else {
        sort(map.get(token), tokens, index + 1, field);
      }
      return;
    }

    if (current instanceof List<?> list) {
      if ("*".equals(token)) {
        for (Object item : list) {
          sort(item, tokens, index + 1, field);
        }
      } else if (isInteger(token)) {
        int position = Integer.parseInt(token);
        if (position < list.size()) {
          sort(list.get(position), tokens, index + 1, field);
        }
      }
    }
  }

  private static String sortValue(Object value, String field) {
    Object candidate = field != null && value instanceof Map<?, ?> map ? map.get(field) : value;
    if (candidate == null || candidate instanceof CharSequence || candidate instanceof Number || candidate instanceof Boolean) {
      return String.valueOf(candidate);
    }
    return Json.write(candidate);
  }

  static List<String> tokens(String path) {
    if (path == null || path.isEmpty() || "$".equals(path)) {
      return List.of();
    }
    String normalized = path.startsWith("$") ? path.substring(1) : "." + path;
    List<String> result = new ArrayList<>();
    int consumed = 0;
    Matcher matcher = PATH_TOKEN.matcher(normalized);
    while (matcher.find()) {
      if (matcher.start() != consumed) {
        throw new IllegalArgumentException("Unparseable snapshot path '" + path + "'");
      }
      consumed = matcher.end();
      result.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
    }
    if (consumed != normalized.length()) {
      throw new IllegalArgumentException("Unparseable snapshot path '" + path + "'");
    }
    return result;
  }

  private static boolean isInteger(String token) {
    if (token.isEmpty()) {
      return false;
    }
    for (int index = 0; index < token.length(); index++) {
      if (!Character.isDigit(token.charAt(index))) {
        return false;
      }
    }
    return true;
  }
}
