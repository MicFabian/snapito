package io.github.micfabian.snapito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import io.github.micfabian.snapito.junit.SnapitoExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.testkit.engine.EngineTestKit;

class ParallelSafetyTest {
  @AfterEach
  void tearDown() {
    Snapito.reloadConfiguration();
  }

  @Test
  void neverExposesTheLiveConfigurationInstance() {
    SnapitoConfig first = Snapito.getConfig();
    SnapitoConfig second = Snapito.getConfig();

    assertNotSame(first, second, "getConfig must hand out a copy, or callers can mutate shared state in place");

    first.setFailOnMissing(true);

    assertEquals(false, Snapito.getConfig().isFailOnMissing(),
      "Mutating the returned configuration must not change the configuration Snapito uses");
  }

  @Test
  void publishesConfigurationChangesAtomically() {
    Snapito.configure(config -> config.setFailOnMissing(true).setWriteIndex(true));

    SnapitoConfig published = Snapito.getConfig();

    assertTrue(published.isFailOnMissing());
    assertTrue(published.isWriteIndex(),
      "A configure block must publish every change together, never one field at a time");
  }

  @Test
  void writesEverySnapshotCorrectlyWhenTestsRunInParallel(@TempDir Path root) throws Exception {
    SnapitoTestSupport.useTemporaryRoot(root);
    ParallelProbe.failures.clear();

    EngineTestKit.engine("junit-jupiter")
      .configurationParameter("junit.jupiter.execution.parallel.enabled", "true")
      .configurationParameter("junit.jupiter.execution.parallel.mode.default", "concurrent")
      .configurationParameter("junit.jupiter.execution.parallel.config.strategy", "fixed")
      .configurationParameter("junit.jupiter.execution.parallel.config.fixed.parallelism", "8")
      .selectors(selectClass(ParallelProbe.class))
      .execute()
      .testEvents()
      .assertStatistics(stats -> stats.succeeded(ParallelProbe.COUNT));

    assertEquals(List.of(), List.copyOf(ParallelProbe.failures));

    Path directory = root.resolve("io/github/micfabian/snapito/parallel-probe");
    for (int index = 0; index < ParallelProbe.COUNT; index++) {
      Path snapshot = directory.resolve("writes-its-own-snapshot-iteration-" + (index + 1) + ".json");
      assertTrue(Files.exists(snapshot), "Missing snapshot for iteration " + (index + 1));
    }
  }

  @ExtendWith(SnapitoExtension.class)
  @org.junit.jupiter.api.Tag("probe")
  @org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT)
  static class ParallelProbe {
    static final int COUNT = 32;
    static final ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("indexes")
    void writesItsOwnSnapshot(int index) {
      try {
        Snapito.expect(Map.of("index", index));
      } catch (RuntimeException | AssertionError e) {
        failures.add(index + ": " + e);
      }
    }

    static java.util.stream.IntStream indexes() {
      return java.util.stream.IntStream.range(0, COUNT);
    }
  }
}
