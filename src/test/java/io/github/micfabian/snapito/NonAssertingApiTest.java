package io.github.micfabian.snapito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

class NonAssertingApiTest {
  @TempDir
  Path root;

  @BeforeEach
  void setUp() {
    SnapitoTestSupport.useTemporaryRoot(root);
    SnapitoTestSupport.enterTest("LedgerServiceTest", "buildsALedger");
  }

  @AfterEach
  void tearDown() {
    SnapitoTestSupport.leaveTest();
    System.setProperty("snapito.ci", "false");
    System.clearProperty("snapito.snapshot.update");
    MissingSnapshots.clear();
    Snapito.reloadConfiguration();
  }

  @Test
  void writesABaselineWhoseNormalizedFormIsEmpty() {
    Object returned = Snapito.snapshotNamed("empty", "", Comparisons.TXT);

    assertEquals("", returned);
    assertTrue(Files.exists(snapshot("empty.txt")),
      "A value that normalizes to empty must still create a baseline, or every later run silently misses it");
  }

  @Test
  void detectsAChangeAfterAnEmptyBaselineWasWritten() {
    Snapito.snapshotNamed("empty", "", Comparisons.TXT);
    SnapitoTestSupport.enterTest("LedgerServiceTest", "buildsALedger");

    Object returned = Snapito.snapshotNamed("empty", "changed", Comparisons.TXT);

    assertEquals("changed", returned,
      "Once a baseline exists, a changed value must not be reported as the old empty value");
  }

  @Test
  void writesABaselineForAWhitespaceOnlyValue() {
    Snapito.snapshotNamed("blank", "   \n  ", Comparisons.TXT);

    assertTrue(Files.exists(snapshot("blank.txt")));
  }

  @Test
  void writesABaselineForAnEmptyCollection() {
    Snapito.snapshotNamed("none", List.of(), Comparisons.ARRAY);

    assertTrue(Files.exists(snapshot("none.csv")));
  }

  @Test
  void returnsTheStoredBaselineWhenTheValueMatches() {
    Snapito.snapshotNamed("stable", Map.of("amount", 42));
    SnapitoTestSupport.enterTest("LedgerServiceTest", "buildsALedger");

    Object returned = Snapito.snapshotNamed("stable", Map.of("amount", 42));

    assertEquals(Map.of("amount", java.math.BigDecimal.valueOf(42)), returned);
  }

  @Test
  void returnsTheStoredBaselineOnMismatchAndLeavesItUntouched() {
    Snapito.snapshotNamed("drift", Map.of("amount", 42));
    SnapitoTestSupport.enterTest("LedgerServiceTest", "buildsALedger");

    Object returned = Snapito.snapshotNamed("drift", Map.of("amount", 43));

    assertEquals(Map.of("amount", java.math.BigDecimal.valueOf(42)), returned,
      "snapshot() reports the reviewed baseline rather than the drifting value");
    assertTrue(SnapitoTestSupport.read(snapshot("drift.json")).contains("42"));
  }

  @Test
  void overwritesTheBaselineWhenUpdatingIsRequested() {
    Snapito.snapshotNamed("updated", Map.of("amount", 42));
    SnapitoTestSupport.enterTest("LedgerServiceTest", "buildsALedger");

    Object returned = Snapito.updateSnapshotNamed("updated", Map.of("amount", 43));

    assertEquals(Map.of("amount", java.math.BigDecimal.valueOf(43)), returned);
    assertTrue(SnapitoTestSupport.read(snapshot("updated.json")).contains("43"));
  }

  @Test
  void refusesToSelfBaselineInCi() {
    System.setProperty("snapito.ci", "true");

    AssertionFailedError error = assertThrows(AssertionFailedError.class,
      () -> Snapito.snapshotNamed("ci-guard", Map.of("amount", 1)));

    assertTrue(error.getMessage().contains("Missing snapshot"));
    assertFalse(Files.exists(snapshot("ci-guard.json")),
      "The non-asserting API must honour the CI guard, or CI silently accepts unreviewed baselines");
    assertEquals(1, MissingSnapshots.recorded().size());
  }

  @Test
  void stillWritesInCiWhenUpdatingIsExplicitlyAllowed() {
    System.setProperty("snapito.ci", "true");
    System.setProperty("snapito.snapshot.update", "true");
    Snapito.configure(config -> config.setAllowUpdateInCi(true));

    Snapito.snapshotNamed("ci-allowed", Map.of("amount", 1));

    assertTrue(Files.exists(snapshot("ci-allowed.json")));
  }

  @Test
  void writesInCiWhenTheMissingBaselineGuardIsDisabled() {
    System.setProperty("snapito.ci", "true");
    Snapito.configure(config -> config.setFailOnMissingInCi(false));

    Snapito.snapshotNamed("ci-off", Map.of("amount", 1));

    assertTrue(Files.exists(snapshot("ci-off.json")));
  }

  @Test
  void detectsTheComparisonForUnnamedSnapshots() {
    Snapito.snapshot(Map.of("amount", 1));

    assertTrue(Files.exists(snapshot("builds-a-ledger.json")));
  }

  @Test
  void appliesAnExplicitComparisonToAnUnnamedSnapshot() {
    Snapito.snapshot("plain text value", Comparisons.TXT);

    assertTrue(Files.exists(snapshot("builds-a-ledger.txt")));
  }

  @Test
  void updatesAnUnnamedSnapshotInPlace() {
    Snapito.snapshot(Map.of("amount", 1));
    SnapitoTestSupport.enterTest("LedgerServiceTest", "buildsALedger");

    Snapito.updateSnapshot(Map.of("amount", 2));

    assertTrue(SnapitoTestSupport.read(snapshot("builds-a-ledger.json")).contains("2"));
  }

  @Test
  void restoresTheSurroundingUpdateStateAfterAScopedUpdate() {
    assertFalse(Snapito.isUpdateRequested());

    Snapito.withUpdate(() -> assertTrue(Snapito.isUpdateRequested()));

    assertFalse(Snapito.isUpdateRequested(),
      "withUpdate must restore the previous state, including on nested use");
  }

  @Test
  void supportsDisablingUpdatesForOneBlock() {
    Snapito.withUpdate(() -> {
      assertTrue(Snapito.isUpdateRequested());
      Snapito.withUpdate(false, () -> {
        assertFalse(Snapito.isUpdateRequested());
        return null;
      });
      assertTrue(Snapito.isUpdateRequested(), "The outer update scope must survive an inner opt-out");
    });
  }

  private Path snapshot(String fileName) {
    return root.resolve("io/github/micfabian/snapito/fixtures/ledger-service-test").resolve(fileName);
  }
}
