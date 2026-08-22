package io.github.micfabian.snapito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class SnapshotInvocationContext {
  private final String featureName;
  private final String packageName;
  private final String className;
  private final int iterationIndex;
  private final boolean parameterized;
  private final Map<String, Object> dataVariables;
  private final List<String> snapshotKeyVariables;
  private final AtomicInteger snapshotIndex = new AtomicInteger();

  public SnapshotInvocationContext(
      String featureName,
      String packageName,
      String className,
      int iterationIndex,
      boolean parameterized,
      Map<String, Object> dataVariables,
      List<String> snapshotKeyVariables) {
    this.featureName = featureName;
    this.packageName = packageName;
    this.className = className;
    this.iterationIndex = iterationIndex;
    this.parameterized = parameterized;
    this.dataVariables = dataVariables == null ? new LinkedHashMap<>() : new LinkedHashMap<>(dataVariables);
    this.snapshotKeyVariables = snapshotKeyVariables == null ? List.of() : List.copyOf(snapshotKeyVariables);
  }

  public String getFeatureName() {
    return featureName;
  }

  public String getPackageName() {
    return packageName;
  }

  public String getClassName() {
    return className;
  }

  public int getIterationIndex() {
    return iterationIndex;
  }

  public boolean isParameterized() {
    return parameterized;
  }

  public Map<String, Object> getDataVariables() {
    return new LinkedHashMap<>(dataVariables);
  }

  public List<String> getSnapshotKeyVariables() {
    return snapshotKeyVariables;
  }

  public int nextSnapshotIndex() {
    return snapshotIndex.getAndIncrement();
  }

  public boolean hasSnapshotKey() {
    return !resolvedKeyVariables().isEmpty();
  }

  public String snapshotKey() {
    return resolvedKeyVariables().stream()
      .map(name -> name + "-" + stringify(dataVariables.get(name)))
      .collect(Collectors.joining("-"));
  }

  private List<String> resolvedKeyVariables() {
    if (dataVariables.isEmpty() || snapshotKeyVariables.isEmpty()) {
      return List.of();
    }
    List<String> resolved = new ArrayList<>();
    for (String name : snapshotKeyVariables) {
      if (dataVariables.containsKey(name)) {
        resolved.add(name);
      }
    }
    return resolved;
  }

  private static String stringify(Object value) {
    return value == null ? "null" : String.valueOf(value);
  }
}
