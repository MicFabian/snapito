package io.github.micfabian.snapito;

import io.github.micfabian.snapito.comparison.ArrayComparison;
import io.github.micfabian.snapito.comparison.BinaryComparison;
import io.github.micfabian.snapito.comparison.CsvComparison;
import io.github.micfabian.snapito.comparison.HtmlComparison;
import io.github.micfabian.snapito.comparison.Json;
import io.github.micfabian.snapito.comparison.JsonComparison;
import io.github.micfabian.snapito.comparison.PngComparison;
import io.github.micfabian.snapito.comparison.TextComparison;
import io.github.micfabian.snapito.comparison.Xml;
import io.github.micfabian.snapito.comparison.XmlComparison;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

public class ComparisonDetector {
  private static final byte[] PNG_SIGNATURE = {
    (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47,
    (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A
  };
  private static final Pattern HTML_MARKER = Pattern.compile(
    "(?is)^\\s*(<!DOCTYPE\\s+html|<html[\\s>]|<body[\\s>]|<div[\\s>]|<table[\\s>]|<section[\\s>]|<span[\\s>]|<p[\\s>]|<ul[\\s>]|<h[1-6][\\s>])");
  private static final Pattern TEXT_PREFIX = Pattern.compile("[A-Za-z0-9]+");

  private final List<CanCompare> builtIns = new CopyOnWriteArrayList<>(List.of(
    new CanComparePng(),
    new CanCompareArray(),
    new CanCompareJson(),
    new CanCompareHtml(),
    new CanCompareXml(),
    new CanCompareCsv(),
    new CanCompareText()));
  private final List<ComparisonProvider> providers = new CopyOnWriteArrayList<>();

  public ComparisonDetector() {
    ServiceLoader.load(ComparisonProvider.class).forEach(this::register);
  }

  public void register(ComparisonProvider provider) {
    providers.add(provider);
    List<ComparisonProvider> sorted = new ArrayList<>(providers);
    sorted.sort(Comparator.comparingInt(ComparisonProvider::priority).reversed());
    providers.clear();
    providers.addAll(sorted);
  }

  public boolean unregister(ComparisonProvider provider) {
    return providers.remove(provider);
  }

  public Comparison detect(Object input) {
    List<CanCompare> candidates = new ArrayList<>(providers);
    candidates.addAll(builtIns);
    for (CanCompare candidate : candidates) {
      Comparison comparison = candidate.detect(input);
      if (comparison != null) {
        return comparison;
      }
    }
    if (input instanceof byte[]) {
      return new BinaryComparison();
    }
    return new JsonComparison();
  }

  static class CanComparePng implements CanCompare {
    @Override
    public Comparison detect(Object input) {
      if (!(input instanceof byte[] bytes) || bytes.length < PNG_SIGNATURE.length) {
        return null;
      }
      for (int index = 0; index < PNG_SIGNATURE.length; index++) {
        if (bytes[index] != PNG_SIGNATURE[index]) {
          return null;
        }
      }
      return new PngComparison();
    }
  }

  static class CanCompareArray implements CanCompare {
    @Override
    public Comparison detect(Object input) {
      if (input instanceof byte[]) {
        return null;
      }
      if (input instanceof Collection<?> || (input != null && input.getClass().isArray())) {
        return new ArrayComparison();
      }
      return null;
    }
  }

  static class CanCompareJson implements CanCompare {
    @Override
    public Comparison detect(Object input) {
      if (input instanceof CharSequence sequence && Json.isJson(sequence.toString())) {
        return new JsonComparison();
      }
      return null;
    }
  }

  static class CanCompareHtml implements CanCompare {
    @Override
    public Comparison detect(Object input) {
      if (!(input instanceof CharSequence sequence)) {
        return null;
      }
      return HTML_MARKER.matcher(sequence.toString()).find() ? new HtmlComparison() : null;
    }
  }

  static class CanCompareXml implements CanCompare {
    @Override
    public Comparison detect(Object input) {
      if (input instanceof CharSequence sequence && Xml.isXml(sequence.toString())) {
        return new XmlComparison();
      }
      return null;
    }
  }

  static class CanCompareCsv implements CanCompare {
    private final CsvComparison comparison = new CsvComparison();

    @Override
    public Comparison detect(Object input) {
      if (!(input instanceof CharSequence sequence)) {
        return null;
      }
      String text = sequence.toString();
      try {
        List<List<String>> rows = comparison.toRows(text);
        if (rows.isEmpty()) {
          return null;
        }
        int columns = rows.get(0).size();
        boolean rectangular = columns > 1 && rows.stream().allMatch(row -> row.size() == columns);
        boolean multipleRows = rows.size() > 1;
        if (rectangular && multipleRows) {
          return new CsvComparison();
        }
      } catch (RuntimeException ignored) {
        return null;
      }
      return null;
    }
  }

  static class CanCompareText implements CanCompare {
    @Override
    public Comparison detect(Object input) {
      if (input instanceof CharSequence sequence && isText(sequence.toString())) {
        return new TextComparison();
      }
      return null;
    }

    private boolean isText(String text) {
      String decoded = new String(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
      return decoded.length() > 4 && TEXT_PREFIX.matcher(decoded.substring(0, 4)).matches();
    }
  }
}
