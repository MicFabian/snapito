package io.github.micfabian.snapito.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

final class SnapshotReview {
  private static final String ACTUAL_SUFFIX = ".actual";
  private static final int MAX_DIFF_CHARACTERS = 20_000;

  private SnapshotReview() {
  }

  record Pending(String name, boolean created, String diff) {
  }

  static List<Pending> scan(Path root) {
    if (!Files.isDirectory(root)) {
      return List.of();
    }
    try (Stream<Path> files = Files.walk(root)) {
      List<Pending> pending = new ArrayList<>();
      files.filter(Files::isRegularFile)
        .filter(path -> path.getFileName().toString().endsWith(ACTUAL_SUFFIX))
        .sorted(Comparator.comparing(Path::toString))
        .forEach(actual -> pending.add(describe(root, actual)));
      return pending;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static int accept(Path root, String selector) {
    PathMatcher matcher = "all".equals(selector)
      ? null
      : FileSystems.getDefault().getPathMatcher("glob:" + selector);
    int accepted = 0;
    for (Pending pending : scan(root)) {
      Path baseline = root.resolve(pending.name());
      if (matcher != null && !matcher.matches(Path.of(pending.name()))) {
        continue;
      }
      try {
        Files.copy(actualOf(baseline), baseline, StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(actualOf(baseline));
        Files.deleteIfExists(sibling(baseline, ".diff.txt"));
        Files.deleteIfExists(sibling(baseline, ".diff.png"));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      accepted++;
    }
    return accepted;
  }

  static String renderHtml(List<Pending> pending) {
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>Snapito review</title><style>")
      .append("body{font-family:sans-serif;margin:2rem;max-width:960px}")
      .append("section{border:1px solid #ccc;border-radius:6px;padding:1rem;margin-bottom:1rem}")
      .append("pre{background:#f6f6f6;padding:.75rem;overflow:auto;white-space:pre-wrap}")
      .append("code{background:#eee;padding:.1rem .3rem}")
      .append(".new{color:#0a6}.changed{color:#b60}</style></head><body>")
      .append("<h1>Snapito review</h1><p>").append(pending.size()).append(" pending snapshot change(s).</p>");
    for (Pending item : pending) {
      html.append("<section><h2>").append(escape(item.name())).append("</h2><p class=\"")
        .append(item.created() ? "new\">new snapshot" : "changed\">changed")
        .append("</p>");
      if (!item.diff().isEmpty()) {
        html.append("<pre>").append(escape(item.diff())).append("</pre>");
      }
      html.append("<p>Accept: <code>./gradlew acceptSnapito -Psnapito.accept='")
        .append(escape(item.name())).append("'</code></p></section>");
    }
    return html.append("</body></html>").toString();
  }

  private static Pending describe(Path root, Path actual) {
    String actualName = root.relativize(actual).toString().replace('\\', '/');
    String name = actualName.substring(0, actualName.length() - ACTUAL_SUFFIX.length());
    Path baseline = root.resolve(name);
    return new Pending(name, !Files.exists(baseline), readDiff(sibling(baseline, ".diff.txt")));
  }

  private static String readDiff(Path diff) {
    if (!Files.isRegularFile(diff)) {
      return "";
    }
    try {
      String text = Files.readString(diff, StandardCharsets.UTF_8);
      return text.length() > MAX_DIFF_CHARACTERS ? text.substring(0, MAX_DIFF_CHARACTERS) + "\n..." : text;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Path actualOf(Path baseline) {
    return sibling(baseline, ACTUAL_SUFFIX);
  }

  private static Path sibling(Path baseline, String suffix) {
    return baseline.resolveSibling(baseline.getFileName() + suffix);
  }

  private static String escape(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
  }
}
