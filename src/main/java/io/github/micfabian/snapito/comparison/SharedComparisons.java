package io.github.micfabian.snapito.comparison;

final class SharedComparisons {
  static final String MESSAGE =
    "This Comparison is a shared constant and must not be reconfigured, because every test in the JVM "
      + "would see the change. Derive an independent copy instead, for example "
      + "Comparisons.json(json -> json.excludingProperties(\"id\")) or existing.with(json -> ...)";

  private SharedComparisons() {
  }
}
