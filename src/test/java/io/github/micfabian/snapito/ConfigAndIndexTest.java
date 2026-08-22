package io.github.micfabian.snapito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.micfabian.snapito.environment.ContinuousIntegration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigAndIndexTest {
  @TempDir
  Path root;

  @BeforeEach
  void setUp() {
    SnapitoTestSupport.useTemporaryRoot(root);
    SnapitoTestSupport.enterTest("IndexServiceTest", "writesAnIndex");
  }

  @AfterEach
  void tearDown() {
    SnapitoTestSupport.leaveTest();
    for (String property : List.of(
        "snapito.snapshot.dir", "snapito.failOnMissing", "snapito.updateOnly",
        "snapito.writeIndex", "snapito.atomicWrites")) {
      System.clearProperty(property);
    }
    System.setProperty("snapito.ci", "false");
    Snapito.reloadConfiguration();
  }

  @Test
  void readsBooleanPropertiesCaseInsensitively() {
    System.setProperty("snapito.failOnMissing", "TRUE");
    assertTrue(SnapitoConfig.fromEnvironment().isFailOnMissing());

    System.setProperty("snapito.failOnMissing", "yes");
    assertFalse(SnapitoConfig.fromEnvironment().isFailOnMissing(),
      "Only 'true' enables a flag; anything else must leave the default in place");
  }

  @Test
  void ignoresWhitespaceAroundBooleanProperties() {
    System.setProperty("snapito.failOnMissing", "  true  ");

    assertTrue(SnapitoConfig.fromEnvironment().isFailOnMissing(),
      "A padded property value in a CI config must not silently disable a flag");
  }

  @Test
  void fallsBackToTheDefaultRootForABlankDirectoryProperty() {
    System.setProperty("snapito.snapshot.dir", "   ");

    assertEquals(Paths.get("src/test/resources/snapshots"), SnapitoConfig.fromEnvironment().getRootPath());
  }

  @Test
  void trimsAndDropsEmptyEntriesInTheUpdateOnlyList() {
    System.setProperty("snapito.updateOnly", " a , ,b ,, ");

    assertEquals(List.of("a", "b"), SnapitoConfig.fromEnvironment().getUpdateOnly());
  }

  @Test
  void exposesTheUpdateOnlyListAsAnUnmodifiableView() {
    org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
      () -> new SnapitoConfig().getUpdateOnly().add("x"));
  }

  @Test
  void copiesEveryConfiguredField() {
    SnapitoConfig original = new SnapitoConfig()
      .setRootPath(Paths.get("/tmp/snapshots"))
      .setFailOnMissing(true)
      .setAllowUpdateInCi(true)
      .setWriteIndex(true)
      .setAtomicWrites(false)
      .setUpdateOnly(List.of("a"));

    SnapitoConfig copy = original.copy();

    assertEquals(Paths.get("/tmp/snapshots"), copy.getRootPath());
    assertTrue(copy.isFailOnMissing());
    assertTrue(copy.isAllowUpdateInCi());
    assertTrue(copy.isWriteIndex());
    assertFalse(copy.isAtomicWrites());
    assertEquals(List.of("a"), copy.getUpdateOnly());
  }

  @Test
  void isolatesTheUpdateOnlyListOfACopy() {
    SnapitoConfig original = new SnapitoConfig().setUpdateOnly(List.of("a"));
    SnapitoConfig copy = original.copy();

    copy.setUpdateOnly(List.of("b"));

    assertEquals(List.of("a"), original.getUpdateOnly());
  }

  @Test
  void treatsANullUpdateOnlyListAsEmpty() {
    assertEquals(List.of(), new SnapitoConfig().setUpdateOnly(null).getUpdateOnly());
  }

  @Test
  void letsTheCiPropertyOverrideEnvironmentDetection() {
    System.setProperty("snapito.ci", "true");
    assertTrue(ContinuousIntegration.isCi());

    System.setProperty("snapito.ci", "false");
    assertFalse(ContinuousIntegration.isCi());
  }

  @Test
  void matchesUpdateScopeByFileNameStemAndFullPath() {
    Path resource = root.resolve("pkg/some-test/books-a-payment.interactions.json");

    assertTrue(inScope(resource, "books-a-payment.interactions.json"), "full file name");
    assertTrue(inScope(resource, "books-a-payment.interactions"), "name without the final extension");
    assertTrue(inScope(resource, "books-a-payment"), "name before the first dot");
    assertTrue(inScope(resource, "books-*"), "glob prefix");
    assertTrue(inScope(resource, "*payment*"), "glob around the middle");
    assertFalse(inScope(resource, "other-*"), "a non-matching pattern must stay out of scope");
  }

  @Test
  void treatsQuestionMarkAsASingleCharacterWildcard() {
    Path resource = root.resolve("pkg/some-test/ab.json");

    assertTrue(inScope(resource, "a?"));
    assertFalse(inScope(resource, "a?c"));
  }

  @Test
  void treatsRegexCharactersInAPatternAsLiteralText() {
    Path resource = root.resolve("pkg/some-test/a+b.json");

    assertTrue(inScope(resource, "a+b"));
    assertFalse(inScope(resource, "a+b", root.resolve("pkg/some-test/aab.json")));
  }

  @Test
  void treatsAnEmptyUpdateOnlyListAsUnrestricted() {
    Snapito.configure(config -> config.setUpdateOnly(List.of()));

    assertTrue(Snapito.inUpdateScope(root.resolve("anything.json")));
  }

  @Test
  void mergesNewEntriesIntoAnExistingIndexFile() throws Exception {
    Files.createDirectories(root);
    Files.writeString(root.resolve(SnapshotIndex.INDEX_FILE),
      "{\"version\":1,\"snapshots\":[{\"snapshot\":\"kept/old.json\",\"comparison\":\"Old\"}]}");

    Snapito.configure(config -> config.setWriteIndex(true));
    Snapito.expect(Map.of("a", 1));
    SnapshotIndex.flush();

    String index = Files.readString(root.resolve(SnapshotIndex.INDEX_FILE));
    assertTrue(index.contains("kept/old.json"), "An existing index entry must survive a merge");
    assertTrue(index.contains("writes-an-index.json"), "The new entry must be added");
  }

  @Test
  void ignoresACorruptExistingIndexFile() throws Exception {
    Files.createDirectories(root);
    Files.writeString(root.resolve(SnapshotIndex.INDEX_FILE), "not json at all");

    assertEquals(Map.of(), SnapshotIndex.readExisting());
  }

  @Test
  void recordsNothingInTheIndexWhenIndexingIsDisabled() {
    SnapshotIndex.clear();
    Snapito.expect(Map.of("a", 1));

    assertEquals(Map.of(), SnapshotIndex.entries());
  }

  @Test
  void reportsEveryMissingBaselineOnce() {
    MissingSnapshots.clear();
    MissingSnapshots.record(root.resolve("b.json"));
    MissingSnapshots.record(root.resolve("a.json"));
    MissingSnapshots.record(root.resolve("a.json"));

    String report = MissingSnapshots.report();

    assertTrue(report.startsWith("Missing snapshot baselines (2):"));
    assertTrue(report.indexOf("a.json") < report.indexOf("b.json"), "Paths must be reported in sorted order");
    MissingSnapshots.clear();
  }

  @Test
  void reportsNothingWhenNoBaselineIsMissing() {
    MissingSnapshots.clear();

    assertEquals("", MissingSnapshots.report());
  }

  @Test
  void reportsEachMissingBaselineOnlyOnceAcrossRepeatedReports() {
    MissingSnapshots.clear();
    MissingSnapshots.record(root.resolve("a.json"));

    assertTrue(MissingSnapshots.reportUnreported().contains("a.json"));
    assertEquals("", MissingSnapshots.reportUnreported(),
      "A baseline already reported must not be repeated for every later test class");
    MissingSnapshots.clear();
  }

  @Test
  void marksOnlyFailingStatusesAsFailures() {
    for (SnapshotResult.Status status : SnapshotResult.Status.values()) {
      boolean expected = status == SnapshotResult.Status.MISMATCH
        || status == SnapshotResult.Status.MISSING
        || status == SnapshotResult.Status.SKIPPED_UPDATE;

      assertEquals(expected, SnapshotResult.builder(status, root).build().isFailure(),
        "Wrong failure classification for " + status);
    }
  }

  private boolean inScope(Path resource, String pattern) {
    return inScope(resource, pattern, resource);
  }

  private boolean inScope(Path resource, String pattern, Path candidate) {
    Snapito.configure(config -> config.setUpdateOnly(List.of(pattern)));
    return Snapito.inUpdateScope(candidate);
  }
}
