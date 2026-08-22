package io.github.micfabian.snapito.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import io.github.micfabian.snapito.SnapshotInvocationContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.testkit.engine.EngineTestKit;

class SnapitoExtensionTest {
  @Test
  void populatesTheInvocationContextForPlainTests() {
    EngineTestKit.engine("junit-jupiter")
      .selectors(selectClass(ContextProbe.class))
      .execute()
      .testEvents()
      .assertStatistics(stats -> stats.succeeded(1));

    assertEquals("io.github.micfabian.snapito.junit", ContextProbe.observedPackage);
    assertEquals("ContextProbe", ContextProbe.observedClass);
    assertEquals("recordsItsOwnContext", ContextProbe.observedFeature);
  }

  @Test
  void numbersParameterizedIterations() {
    IterationProbe.observedIterations.clear();
    IterationProbe.observedParameterized.clear();
    IterationProbe.observedValues.clear();

    EngineTestKit.engine("junit-jupiter")
      .selectors(selectClass(IterationProbe.class))
      .execute()
      .testEvents()
      .assertStatistics(stats -> stats.succeeded(3));

    assertEquals(List.of(0, 1, 2), IterationProbe.observedIterations);
    assertTrue(IterationProbe.observedParameterized.stream().allMatch(Boolean::booleanValue));
    assertEquals(List.of("a", "b", "c"), IterationProbe.observedValues);
  }

  @Test
  void clearsTheContextAfterEachTest() {
    EngineTestKit.engine("junit-jupiter")
      .selectors(selectClass(ContextProbe.class))
      .execute()
      .testEvents()
      .assertStatistics(stats -> stats.succeeded(1));

    org.junit.jupiter.api.Assertions.assertNull(SnapitoContext.current());
  }

  @Test
  void removesUnreferencedSnapshotsWhenCleaningIsEnabled(@org.junit.jupiter.api.io.TempDir Path directory)
      throws Exception {
    Path kept = Files.writeString(directory.resolve("kept.json"), "{}");
    Path obsolete = Files.writeString(directory.resolve("obsolete.json"), "{}");
    SnapshotCleanup.reference(kept);

    SnapshotCleanup.cleanup(directory, true);

    assertTrue(Files.exists(kept));
    assertTrue(Files.notExists(obsolete));
  }

  @Test
  void keepsEverythingWhenCleaningIsDisabled(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
    Path obsolete = Files.writeString(directory.resolve("obsolete.json"), "{}");

    SnapshotCleanup.cleanup(directory, false);

    assertTrue(Files.exists(obsolete));
  }

  @ExtendWith(SnapitoExtension.class)
  @org.junit.jupiter.api.Tag("probe")
  static class ContextProbe {
    static String observedPackage;
    static String observedClass;
    static String observedFeature;

    @Test
    void recordsItsOwnContext() {
      SnapshotInvocationContext context = SnapitoContext.current();
      assertNotNull(context);
      observedPackage = context.getPackageName();
      observedClass = context.getClassName();
      observedFeature = context.getFeatureName();
    }
  }

  @ExtendWith(SnapitoExtension.class)
  @org.junit.jupiter.api.Tag("probe")
  static class IterationProbe {
    static final List<Integer> observedIterations = new java.util.ArrayList<>();
    static final List<Boolean> observedParameterized = new java.util.ArrayList<>();
    static final List<String> observedValues = new java.util.ArrayList<>();

    @ParameterizedTest
    @ValueSource(strings = {"a", "b", "c"})
    void recordsEachIteration(String value) {
      SnapshotInvocationContext context = SnapitoContext.current();
      assertNotNull(context);
      observedIterations.add(context.getIterationIndex());
      observedParameterized.add(context.isParameterized());
      observedValues.add(String.valueOf(context.getDataVariables().values().iterator().next()));
    }
  }

  @SuppressWarnings("unused")
  private static List<String> names(Stream<Path> paths) {
    return paths.map(path -> path.getFileName().toString()).collect(Collectors.toList());
  }
}
