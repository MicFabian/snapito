package io.github.micfabian.snapito;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.micfabian.snapito.comparison.Json;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public final class SnapshotIndex {
  public static final String INDEX_FILE = "snapito-index.json";

  private static final Map<String, Map<String, Object>> ENTRIES = new ConcurrentHashMap<>();
  private static final Object FLUSH_LOCK = new Object();

  private SnapshotIndex() {
  }

  public static void record(Path resource, Comparison comparison, SnapshotInvocationContext context) {
    if (!Snapito.getConfig().isWriteIndex()) {
      return;
    }
    Path root = Snapito.getSnapshotsRoot().toAbsolutePath().normalize();
    Path absolute = resource.toAbsolutePath().normalize();
    String key = relativize(root, absolute);

    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("snapshot", key);
    entry.put("comparison", comparison.getClass().getName());
    entry.put("testClass", qualifiedTestName(context));
    entry.put("test", context == null ? "" : context.getFeatureName());
    entry.put("parameterized", context != null && context.isParameterized());
    if (context != null && !context.getDataVariables().isEmpty()) {
      Map<String, String> variables = new LinkedHashMap<>();
      context.getDataVariables().forEach((name, value) ->
        variables.put(name, value == null ? null : String.valueOf(value)));
      entry.put("dataVariables", variables);
    }
    ENTRIES.put(key, entry);
  }

  public static Map<String, Map<String, Object>> entries() {
    return new LinkedHashMap<>(ENTRIES);
  }

  public static void clear() {
    ENTRIES.clear();
  }

  public static Path indexPath() {
    return Snapito.getSnapshotsRoot().resolve(INDEX_FILE);
  }

  public static void flush() {
    if (!Snapito.getConfig().isWriteIndex() || ENTRIES.isEmpty()) {
      return;
    }
    synchronized (FLUSH_LOCK) {
      Map<String, Object> merged = new TreeMap<>(readExisting());
      merged.putAll(ENTRIES);

      Map<String, Object> document = new LinkedHashMap<>();
      document.put("version", 1);
      document.put("snapshots", merged.values());

      SnapshotArtifacts.write(
        indexPath(),
        Json.writePretty(document).getBytes(StandardCharsets.UTF_8),
        Snapito.getConfig().isAtomicWrites());
    }
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> readExisting() {
    Path path = indexPath();
    if (!Files.exists(path)) {
      return Map.of();
    }
    try {
      JsonNode parsed = Json.mapper().readTree(path.toFile());
      JsonNode snapshots = parsed.get("snapshots");
      if (snapshots == null || !snapshots.isArray()) {
        return Map.of();
      }
      Map<String, Object> existing = new LinkedHashMap<>();
      for (JsonNode entry : snapshots) {
        Object plain = Json.toPlain(entry);
        if (plain instanceof Map<?, ?> map && map.get("snapshot") != null) {
          existing.put(String.valueOf(map.get("snapshot")), plain);
        }
      }
      return existing;
    } catch (IOException | RuntimeException ignored) {
      return Map.of();
    }
  }

  private static String qualifiedTestName(SnapshotInvocationContext context) {
    if (context == null) {
      return "";
    }
    String packageName = context.getPackageName();
    String className = context.getClassName() == null ? "" : context.getClassName();
    return packageName == null || packageName.isEmpty() ? className : packageName + "." + className;
  }

  private static String relativize(Path root, Path absolute) {
    try {
      return root.relativize(absolute).toString().replace(File.separator, "/");
    } catch (IllegalArgumentException ignored) {
      return absolute.toString().replace(File.separator, "/");
    }
  }
}
