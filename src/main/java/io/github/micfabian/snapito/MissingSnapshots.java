package io.github.micfabian.snapito;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MissingSnapshots {
  private static final Set<Path> MISSING = ConcurrentHashMap.newKeySet();
  private static final Set<Path> REPORTED = new java.util.HashSet<>();

  private MissingSnapshots() {
  }

  public static void record(Path resource) {
    MISSING.add(resource.toAbsolutePath().normalize());
  }

  public static List<Path> recorded() {
    List<Path> recorded = new ArrayList<>(MISSING);
    Collections.sort(recorded);
    return recorded;
  }

  public static void clear() {
    MISSING.clear();
    synchronized (REPORTED) {
      REPORTED.clear();
    }
  }

  public static String reportUnreported() {
    List<Path> missing;
    synchronized (REPORTED) {
      missing = new ArrayList<>(recorded());
      missing.removeAll(REPORTED);
      REPORTED.addAll(missing);
    }
    return render(missing);
  }

  public static String report() {
    return render(recorded());
  }

  private static String render(List<Path> missing) {
    if (missing.isEmpty()) {
      return "";
    }
    StringBuilder builder = new StringBuilder("Missing snapshot baselines (" + missing.size() + "):");
    for (Path path : missing) {
      builder.append(System.lineSeparator()).append(" - ").append(path);
    }
    return builder.toString();
  }
}
