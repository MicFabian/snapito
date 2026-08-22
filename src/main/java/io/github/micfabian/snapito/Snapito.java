package io.github.micfabian.snapito;

import io.github.micfabian.snapito.environment.ContinuousIntegration;
import io.github.micfabian.snapito.junit.SnapitoContext;
import io.github.micfabian.snapito.junit.SnapshotCleanup;
import io.github.micfabian.snapito.mockito.InteractionComparison;
import io.github.micfabian.snapito.mockito.Interactions;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Snapito {
  private static final Logger LOG = LoggerFactory.getLogger(Snapito.class);
  private static final ComparisonDetector DETECTOR = new ComparisonDetector();
  private static final ThreadLocal<Map<String, Integer>> FALLBACK_COUNTS =
    ThreadLocal.withInitial(java.util.HashMap::new);
  private static final ThreadLocal<Boolean> UPDATE_OVERRIDE = new ThreadLocal<>();
  private static final ThreadLocal<String> NAME_OVERRIDE = new ThreadLocal<>();

  private static volatile SnapitoConfig config = SnapitoConfig.fromEnvironment();
  private static volatile Path snapshotsRoot = config.getRootPath();

  private Snapito() {
  }

  public static void expect(Object actual) {
    assertSnapshot(actual, DETECTOR.detect(actual));
  }

  public static void expect(Object actual, Comparison comparison) {
    assertSnapshot(actual, comparison);
  }

  public static void expectNamed(String name, Object actual) {
    expectNamed(name, actual, DETECTOR.detect(actual));
  }

  public static void expectNamed(String name, Object actual, Comparison comparison) {
    withName(name, () -> {
      assertSnapshot(actual, comparison);
      return null;
    });
  }

  public static void expectInteractions(Object... mocks) {
    expectInteractions(Interactions.defaults(), mocks);
  }

  public static void expectInteractions(Interactions interactions, Object... mocks) {
    assertSnapshot(interactions.record(mocks), Comparisons.INTERACTIONS);
  }

  public static void expectInteractionsNamed(String name, Object... mocks) {
    expectInteractionsNamed(name, Interactions.defaults(), mocks);
  }

  public static void expectInteractionsNamed(String name, Interactions interactions, Object... mocks) {
    withName(name, () -> {
      assertSnapshot(interactions.record(mocks), Comparisons.INTERACTIONS);
      return null;
    });
  }

  public static void expectInteractions(
      Interactions interactions,
      InteractionComparison comparison,
      Object... mocks) {
    assertSnapshot(interactions.record(mocks), comparison);
  }

  public static void verifyAll(Consumer<SnapshotSession> block) {
    SnapshotSession session = new SnapshotSession();
    block.accept(session);

    List<SnapshotResult> failures = session.getFailures();
    if (failures.isEmpty()) {
      return;
    }
    if (failures.size() == 1) {
      throw failure(failures.get(0));
    }
    List<Throwable> errors = new ArrayList<>(failures.size());
    for (SnapshotResult result : failures) {
      errors.add(failure(result));
    }
    throw new MultipleFailuresError(failures.size() + " snapshot failures", errors);
  }

  public static void assertSnapshot(Object actual) {
    assertSnapshot(actual, DETECTOR.detect(actual));
  }

  public static void assertSnapshot(Object actual, Comparison comparison) {
    SnapshotResult result = evaluate(actual, comparison);
    if (result.isFailure()) {
      throw failure(result);
    }
  }

  public static SnapshotResult evaluate(Object actual, Comparison comparison) {
    Path resource = detectResource(comparison);
    Object current = comparison.beforeComparison(actual);
    byte[] actualBytes = comparison.beforeStore(actual);
    boolean updating = isUpdating(resource);

    if (!Files.exists(resource) || sizeOf(resource) == 0) {
      boolean mustFail = config.isFailOnMissing()
        || config.isReportMissing()
        || (isRunningInCi() && config.isFailOnMissingInCi());

      if (mustFail && !updating) {
        MissingSnapshots.record(resource);
        String message = "Missing snapshot for " + resource + System.lineSeparator()
          + "Create or update the snapshot locally and commit the reviewed baseline";
        if (config.isReportMissing()) {
          upsertResource(actual, resource, comparison);
        } else {
          writeFailureArtifacts(resource, comparison, null, actualBytes, message);
        }
        return SnapshotResult.builder(SnapshotResult.Status.MISSING, resource)
          .message(message)
          .actual(current)
          .build();
      }

      upsertResource(actual, resource, comparison);
      SnapshotArtifacts.clearFailureArtifacts(resource, List.of(".diff.png"));
      return SnapshotResult.builder(SnapshotResult.Status.WRITTEN, resource).actual(current).build();
    }

    byte[] expectedBytes = SnapshotArtifacts.read(resource);
    Object expected = readResource(resource, comparison);

    if (matches(comparison, expected, current)) {
      SnapshotArtifacts.clearFailureArtifacts(resource, List.of(".diff.png"));
      return SnapshotResult.builder(SnapshotResult.Status.MATCHED, resource)
        .expected(expected)
        .actual(current)
        .build();
    }

    if (updating) {
      upsertResource(actual, resource, comparison);
      SnapshotArtifacts.clearFailureArtifacts(resource, List.of(".diff.png"));
      return SnapshotResult.builder(SnapshotResult.Status.WRITTEN, resource)
        .expected(expected)
        .actual(current)
        .build();
    }

    String diff = describeDifference(comparison, expected, current);
    boolean outOfScope = isUpdateRequested() && !updating;
    String hint = outOfScope ? updateScopeInstructions(resource) : updateInstructions();
    String message = "Snapshot mismatch for " + resource + System.lineSeparator() + hint;
    if (diff != null && !diff.isEmpty()) {
      message = message + System.lineSeparator() + diff;
    }
    writeFailureArtifacts(resource, comparison, expectedBytes, actualBytes, diff);

    return SnapshotResult.builder(
        outOfScope ? SnapshotResult.Status.SKIPPED_UPDATE : SnapshotResult.Status.MISMATCH, resource)
      .message(message)
      .diff(diff)
      .expected(expected)
      .actual(current)
      .build();
  }

  public static Object snapshot(Object actual) {
    return snapshot(actual, DETECTOR.detect(actual));
  }

  public static Object snapshot(Object actual, Comparison comparison) {
    Path resource = detectResource(comparison);
    Object current = comparison.beforeComparison(actual);
    boolean updating = isUpdating(resource);

    if (!Files.exists(resource) || sizeOf(resource) == 0) {
      if (!updating && isRunningInCi() && config.isFailOnMissingInCi()) {
        MissingSnapshots.record(resource);
        throw new AssertionFailedError(
          "Missing snapshot for " + resource + System.lineSeparator()
            + "Create or update the snapshot locally and commit the reviewed baseline",
          null,
          current == null ? null : String.valueOf(current));
      }
      return upsertResource(actual, resource, comparison);
    }

    Object expected = readResource(resource, comparison);
    if (matches(comparison, expected, current)) {
      return expected;
    }
    if (updating) {
      return upsertResource(actual, resource, comparison);
    }
    return expected;
  }

  public static Object snapshotNamed(String name, Object actual) {
    return snapshotNamed(name, actual, DETECTOR.detect(actual));
  }

  public static Object snapshotNamed(String name, Object actual, Comparison comparison) {
    return withName(name, () -> snapshot(actual, comparison));
  }

  public static Object updateSnapshot(Object actual) {
    return updateSnapshot(actual, DETECTOR.detect(actual));
  }

  public static Object updateSnapshot(Object actual, Comparison comparison) {
    return withUpdate(true, () -> snapshot(actual, comparison));
  }

  public static Object updateSnapshotNamed(String name, Object actual) {
    return updateSnapshotNamed(name, actual, DETECTOR.detect(actual));
  }

  public static Object updateSnapshotNamed(String name, Object actual, Comparison comparison) {
    return withName(name, () -> updateSnapshot(actual, comparison));
  }

  public static void withUpdate(Runnable block) {
    withUpdate(true, () -> {
      block.run();
      return null;
    });
  }

  public static <T> T withUpdate(Supplier<T> block) {
    return withUpdate(true, block);
  }

  public static <T> T withUpdate(boolean enabled, Supplier<T> block) {
    Boolean previous = UPDATE_OVERRIDE.get();
    UPDATE_OVERRIDE.set(enabled);
    try {
      return block.get();
    } finally {
      restore(UPDATE_OVERRIDE, previous);
    }
  }

  public static <T> T withName(String name, Supplier<T> block) {
    String previous = NAME_OVERRIDE.get();
    NAME_OVERRIDE.set(name);
    try {
      return block.get();
    } finally {
      restore(NAME_OVERRIDE, previous);
    }
  }

  public static void clearThreadState() {
    FALLBACK_COUNTS.remove();
  }

  public static synchronized void configure(Consumer<SnapitoConfig> block) {
    SnapitoConfig updated = config.copy();
    block.accept(updated);
    config = updated;
    snapshotsRoot = updated.getRootPath();
  }

  public static synchronized void reloadConfiguration() {
    SnapitoConfig reloaded = SnapitoConfig.fromEnvironment();
    config = reloaded;
    snapshotsRoot = reloaded.getRootPath();
  }

  public static SnapitoConfig getConfig() {
    return config.copy();
  }

  public static Path getSnapshotsRoot() {
    return snapshotsRoot;
  }

  public static boolean isUpdating() {
    return isUpdateRequested() && (!isRunningInCi() || config.isAllowUpdateInCi());
  }

  public static boolean isUpdateRequested() {
    Boolean override = UPDATE_OVERRIDE.get();
    return override != null ? override : updateFlagSet();
  }

  public static boolean isUpdating(Path resource) {
    return isUpdating() && inUpdateScope(resource);
  }

  public static boolean inUpdateScope(Path resource) {
    List<String> patterns = config.getUpdateOnly();
    if (patterns.isEmpty() || Boolean.TRUE.equals(UPDATE_OVERRIDE.get())) {
      return true;
    }
    String fileName = resource.getFileName().toString();
    String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
    String stem = fileName.contains(".") ? fileName.substring(0, fileName.indexOf('.')) : fileName;
    String full = resource.toAbsolutePath().normalize().toString().replace(java.io.File.separator, "/");
    return patterns.stream().anyMatch(pattern ->
      matchesGlob(pattern, fileName)
        || matchesGlob(pattern, baseName)
        || matchesGlob(pattern, stem)
        || matchesGlob(pattern, full));
  }

  public static boolean isRunningInIntelliJ() {
    return "true".equalsIgnoreCase(System.getProperty("idea.active", ""))
      || System.getProperty("idea.test.cyclic.buffer.size") != null
      || System.getProperty("idea.launcher.port") != null;
  }

  public static boolean isRunningInCi() {
    return ContinuousIntegration.isCi();
  }

  public static boolean shouldCleanObsoleteSnapshots() {
    return config.isCleanObsoleteSnapshots() && (isUpdating() || cleaningRequested());
  }

  public static Path packageDir() {
    SnapshotInvocationContext context = SnapitoContext.current();
    String packageName = context == null ? "" : context.getPackageName();
    String className = context == null ? inferClassNameFromStack() : context.getClassName();
    return packageDir(packageName, className);
  }

  public static Path packageDir(String packageName, String className) {
    Path root = snapshotsRoot;
    if (packageName != null && !packageName.isEmpty()) {
      root = root.resolve(packageName.replace(".", java.io.File.separator));
    }
    return root.resolve(toKebabCase(className));
  }

  static String inferClassNameFromStack() {
    for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
      String className = element.getClassName();
      if (className.endsWith("Test") || className.endsWith("Tests") || className.endsWith("IT")) {
        int lastDot = className.lastIndexOf('.');
        return lastDot < 0 ? className : className.substring(lastDot + 1);
      }
    }
    return "UnknownTest";
  }

  private static Object upsertResource(Object actual, Path resource, Comparison comparison) {
    LOG.debug("Writing snapshot {}", resource);
    SnapshotArtifacts.write(resource, comparison.beforeStore(actual), config.isAtomicWrites());
    return readResource(resource, comparison);
  }

  private static Path detectResource(Comparison comparison) {
    Path resource = resolveResource(comparison);
    SnapshotIndex.record(resource, comparison, SnapitoContext.current());
    return resource;
  }

  private static Path resolveResource(Comparison comparison) {
    Path directory = packageDir();
    if (!Files.exists(directory)) {
      try {
        Files.createDirectories(directory);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    SnapshotInvocationContext context = SnapitoContext.current();
    String override = NAME_OVERRIDE.get();
    String rawName = override != null ? override : context == null ? null : context.getFeatureName();
    String name = sanitize(rawName);
    if (name.isEmpty()) {
      name = "snapshot";
    }

    if (context != null && context.isParameterized()) {
      name = context.hasSnapshotKey()
        ? name + "-" + sanitize(context.snapshotKey())
        : name + "-iteration-" + (context.getIterationIndex() + 1);
    }

    int count;
    if (override != null) {
      count = 0;
    } else if (context != null) {
      count = context.nextSnapshotIndex();
    } else {
      Map<String, Integer> counts = FALLBACK_COUNTS.get();
      count = counts.getOrDefault(name, 0);
      counts.put(name, count + 1);
    }

    String suffix = count > 0 ? "-" + count : "";
    return directory.resolve(name + suffix + "." + comparison.fileExtension());
  }

  private static Object readResource(Path resource, Comparison comparison) {
    if (!Files.isReadable(resource)) {
      return "";
    }
    SnapshotCleanup.reference(resource);
    LOG.debug("Restoring snapshot from {}", resource);
    Object restored = comparison.afterRestore(SnapshotArtifacts.read(resource));
    return comparison.beforeComparison(restored);
  }

  private static long sizeOf(Path resource) {
    try {
      return Files.size(resource);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static boolean matches(Comparison comparison, Object expected, Object actual) {
    if (comparison instanceof AdvancedComparison advanced) {
      return advanced.matches(expected, actual);
    }
    return Objects.equals(expected, actual);
  }

  private static String describeDifference(Comparison comparison, Object expected, Object actual) {
    if (comparison instanceof AdvancedComparison advanced) {
      return advanced.describeDifference(expected, actual);
    }
    return SnapshotDiff.describe(expected, actual);
  }

  private static void writeFailureArtifacts(
      Path resource,
      Comparison comparison,
      byte[] expectedBytes,
      byte[] actualBytes,
      String diff) {
    if (config.isWriteActualOnMismatch() && actualBytes != null) {
      SnapshotArtifacts.write(SnapshotArtifacts.actualPath(resource), actualBytes, config.isAtomicWrites());
    }
    if (config.isWriteDiffOnMismatch() && diff != null && !diff.isEmpty()) {
      SnapshotArtifacts.write(
        SnapshotArtifacts.textDiffPath(resource),
        diff.getBytes(StandardCharsets.UTF_8),
        config.isAtomicWrites());
    }
    if (config.isWriteDiffOnMismatch()
      && expectedBytes != null
      && actualBytes != null
      && comparison instanceof AdvancedComparison advanced) {
      advanced.differenceArtifacts(expectedBytes, actualBytes).forEach((suffix, bytes) ->
        SnapshotArtifacts.write(SnapshotArtifacts.artifactPath(resource, suffix), bytes, config.isAtomicWrites()));
    }
  }

  private static AssertionFailedError failure(SnapshotResult result) {
    return new AssertionFailedError(
      result.getMessage(),
      result.getExpected() == null ? null : String.valueOf(result.getExpected()),
      result.getActual() == null ? null : String.valueOf(result.getActual()));
  }

  private static String updateScopeInstructions(Path resource) {
    return "Snapshot excluded by snapito.updateOnly=" + String.join(",", config.getUpdateOnly())
      + System.lineSeparator()
      + "Add '" + resource.getFileName() + "' to the filter to update this snapshot";
  }

  private static String updateInstructions() {
    if (isRunningInIntelliJ()) {
      return "IntelliJ rerun: rerun this test with VM option -Dsnapito.snapshot.update=true";
    }
    return "Rerun with -Dsnapito.snapshot.update=true to update this snapshot";
  }

  private static boolean updateFlagSet() {
    String environment = System.getenv("SNAPITO_UPDATE");
    if (environment != null && environment.equalsIgnoreCase("true")) {
      return true;
    }
    String property = System.getProperty("snapito.snapshot.update");
    return property != null && property.equalsIgnoreCase("true");
  }

  private static boolean cleaningRequested() {
    return "true".equalsIgnoreCase(System.getProperty("snapito.snapshot.clean", "false"));
  }

  private static boolean matchesGlob(String pattern, String candidate) {
    if (pattern.equals(candidate)) {
      return true;
    }
    StringBuilder regex = new StringBuilder();
    for (char character : pattern.toCharArray()) {
      switch (character) {
        case '*' -> regex.append(".*");
        case '?' -> regex.append('.');
        default -> regex.append(Pattern.quote(String.valueOf(character)));
      }
    }
    return candidate.matches(regex.toString());
  }

  private static String toKebabCase(String className) {
    if (className == null || className.isEmpty()) {
      return "test";
    }
    String lower = className.replaceAll("([A-Z])", "-$1").toLowerCase(Locale.ENGLISH);
    return lower.startsWith("-") ? lower.substring(1) : lower;
  }

  private static String sanitize(String name) {
    if (name == null || name.isEmpty()) {
      return "";
    }
    return name.replaceAll("([a-z0-9])([A-Z])", "$1-$2")
      .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
      .toLowerCase(Locale.ENGLISH)
      .replaceAll("[^a-z0-9]+", "-")
      .replaceAll("^-+|-+$", "");
  }

  private static <T> void restore(ThreadLocal<T> holder, T previous) {
    if (previous == null) {
      holder.remove();
    } else {
      holder.set(previous);
    }
  }
}
