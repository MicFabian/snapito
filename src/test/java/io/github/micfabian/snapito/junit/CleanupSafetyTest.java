package io.github.micfabian.snapito.junit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

import io.github.micfabian.snapito.Snapito;
import io.github.micfabian.snapito.SnapitoTestSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.testkit.engine.EngineTestKit;

class CleanupSafetyTest {
  @AfterEach
  void tearDown() {
    System.clearProperty("snapito.snapshot.update");
    Snapito.reloadConfiguration();
  }

  @Test
  void keepsBaselinesOfTestsThatWereFilteredOut(@TempDir Path root) throws IOException {
    Path otherBaseline = prepare(root);

    EngineTestKit.engine("junit-jupiter")
      .selectors(selectMethod(TwoSnapshotProbe.class, "methodA"))
      .execute();

    assertTrue(Files.exists(otherBaseline),
      "A filtered run must never delete baselines belonging to tests it did not execute");
  }

  @Test
  void removesObsoleteBaselinesWhenTheWholeClassRuns(@TempDir Path root) throws IOException {
    Path orphan = prepare(root).resolveSibling("orphaned.json");
    Files.writeString(orphan, "{\"orphaned\":true}");

    EngineTestKit.engine("junit-jupiter")
      .selectors(selectClass(TwoSnapshotProbe.class))
      .execute();

    assertFalse(Files.exists(orphan),
      "A complete run must still remove baselines no test references any more");
  }

  private Path prepare(Path root) throws IOException {
    SnapitoTestSupport.useTemporaryRoot(root);
    System.setProperty("snapito.snapshot.update", "true");
    Path directory = root.resolve("io/github/micfabian/snapito/junit/two-snapshot-probe");
    Files.createDirectories(directory);
    return Files.writeString(directory.resolve("method-b.json"), "{\"kept\":true}");
  }

  @ExtendWith(SnapitoExtension.class)
  @org.junit.jupiter.api.Tag("probe")
  static class TwoSnapshotProbe {
    @Test
    void methodA() {
      Snapito.expect(Map.of("a", 1));
    }

    @Test
    void methodB() {
      Snapito.expect(Map.of("b", 2));
    }
  }
}
