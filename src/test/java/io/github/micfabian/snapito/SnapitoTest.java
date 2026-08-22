package io.github.micfabian.snapito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

class SnapitoTest {
  @TempDir
  Path root;

  @BeforeEach
  void setUp() {
    SnapitoTestSupport.useTemporaryRoot(root);
    SnapitoTestSupport.enterTest("PaymentServiceTest", "creates a payment");
  }

  @AfterEach
  void tearDown() {
    SnapitoTestSupport.leaveTest();
    Snapito.reloadConfiguration();
  }

  @Test
  void writesMissingSnapshotOnFirstLocalRun() {
    Snapito.expect(Map.of("amount", 42));

    Path snapshot = snapshotPath("creates-a-payment.json");
    assertTrue(Files.exists(snapshot));
    assertTrue(SnapitoTestSupport.read(snapshot).contains("\"amount\" : 42"));
  }

  @Test
  void matchesExistingSnapshot() {
    Snapito.expect(Map.of("amount", 42));
    SnapitoTestSupport.enterTest("PaymentServiceTest", "creates a payment");

    Snapito.expect(Map.of("amount", 42));
  }

  @Test
  void failsOnMismatchAndWritesReviewArtifacts() {
    Snapito.expect(Map.of("amount", 42));
    SnapitoTestSupport.enterTest("PaymentServiceTest", "creates a payment");

    AssertionFailedError error = assertThrows(AssertionFailedError.class,
      () -> Snapito.expect(Map.of("amount", 43)));

    assertTrue(error.getMessage().contains("Snapshot mismatch"));
    assertTrue(error.getMessage().contains("$.amount expected 42, but was 43"));
    assertTrue(Files.exists(snapshotPath("creates-a-payment.json.actual")));
    assertTrue(Files.exists(snapshotPath("creates-a-payment.json.diff.txt")));
  }

  @Test
  void clearsReviewArtifactsOnceMatching() {
    Snapito.expect(Map.of("amount", 42));
    SnapitoTestSupport.enterTest("PaymentServiceTest", "creates a payment");
    assertThrows(AssertionFailedError.class, () -> Snapito.expect(Map.of("amount", 43)));

    SnapitoTestSupport.enterTest("PaymentServiceTest", "creates a payment");
    Snapito.expect(Map.of("amount", 42));

    assertFalse(Files.exists(snapshotPath("creates-a-payment.json.actual")));
    assertFalse(Files.exists(snapshotPath("creates-a-payment.json.diff.txt")));
  }

  @Test
  void failsOnMissingBaselineInCi() {
    System.setProperty("snapito.ci", "true");
    try {
      AssertionFailedError error = assertThrows(AssertionFailedError.class,
        () -> Snapito.expect(Map.of("amount", 42)));
      assertTrue(error.getMessage().contains("Missing snapshot"));
      assertFalse(Files.exists(snapshotPath("creates-a-payment.json")));
      assertEquals(1, MissingSnapshots.recorded().size());
    } finally {
      System.setProperty("snapito.ci", "false");
    }
  }

  @Test
  void updatesSnapshotWhenUpdatingIsRequested() {
    Snapito.expect(Map.of("amount", 42));
    SnapitoTestSupport.enterTest("PaymentServiceTest", "creates a payment");

    Snapito.withUpdate(() -> Snapito.expect(Map.of("amount", 43)));

    assertTrue(SnapitoTestSupport.read(snapshotPath("creates-a-payment.json")).contains("43"));
  }

  @Test
  void blocksUpdatesInCiUnlessExplicitlyAllowed() {
    Snapito.expect(Map.of("amount", 42));
    System.setProperty("snapito.ci", "true");
    System.setProperty("snapito.snapshot.update", "true");
    try {
      SnapitoTestSupport.enterTest("PaymentServiceTest", "creates a payment");
      assertThrows(AssertionFailedError.class, () -> Snapito.expect(Map.of("amount", 43)));
      assertTrue(SnapitoTestSupport.read(snapshotPath("creates-a-payment.json")).contains("42"));
    } finally {
      System.clearProperty("snapito.snapshot.update");
      System.setProperty("snapito.ci", "false");
    }
  }

  @Test
  void allowsUpdatesInCiWhenExplicitlyOptedIn() {
    Snapito.expect(Map.of("amount", 42));
    System.setProperty("snapito.ci", "true");
    System.setProperty("snapito.snapshot.update", "true");
    Snapito.configure(config -> config.setAllowUpdateInCi(true));
    try {
      SnapitoTestSupport.enterTest("PaymentServiceTest", "creates a payment");
      Snapito.expect(Map.of("amount", 43));
      assertTrue(SnapitoTestSupport.read(snapshotPath("creates-a-payment.json")).contains("43"));
    } finally {
      System.clearProperty("snapito.snapshot.update");
      System.setProperty("snapito.ci", "false");
    }
  }

  @Test
  void restrictsUpdatesToTheConfiguredScope() {
    Snapito.expect(Map.of("amount", 42));
    System.setProperty("snapito.snapshot.update", "true");
    Snapito.configure(config -> config.setUpdateOnly(List.of("other-*")));
    try {
      SnapitoTestSupport.enterTest("PaymentServiceTest", "creates a payment");
      AssertionFailedError error = assertThrows(AssertionFailedError.class,
        () -> Snapito.expect(Map.of("amount", 43)));
      assertTrue(error.getMessage().contains("Snapshot excluded by snapito.updateOnly=other-*"));
    } finally {
      System.clearProperty("snapito.snapshot.update");
    }
  }

  @Test
  void numbersUnnamedSnapshotsWithinOneTest() {
    Snapito.expect(Map.of("first", 1));
    Snapito.expect(Map.of("second", 2));

    assertTrue(Files.exists(snapshotPath("creates-a-payment.json")));
    assertTrue(Files.exists(snapshotPath("creates-a-payment-1.json")));
  }

  @Test
  void keepsNamedSnapshotsStable() {
    Snapito.expectNamed("users-list", List.of("ada", "grace"));
    Snapito.expectNamed("users-list", List.of("ada", "grace"));

    assertTrue(Files.exists(snapshotPath("users-list.csv")));
    assertFalse(Files.exists(snapshotPath("users-list-1.csv")));
  }

  @Test
  void suffixesNamedSnapshotsPerIterationToo() {
    SnapitoTestSupport.enterTest("PaymentServiceTest", "creates a payment", 0, true, Map.of(), List.of());
    Snapito.expectNamed("payload", Map.of("amount", 1));
    SnapitoTestSupport.enterTest("PaymentServiceTest", "creates a payment", 1, true, Map.of(), List.of());
    Snapito.expectNamed("payload", Map.of("amount", 2));

    assertTrue(Files.exists(snapshotPath("payload-iteration-1.json")),
      "A named snapshot inside a parameterized test must not share one file across iterations");
    assertTrue(Files.exists(snapshotPath("payload-iteration-2.json")));
  }

  @Test
  void suffixesParameterizedIterations() {
    SnapitoTestSupport.enterTest("PaymentServiceTest", "creates a payment", 0, true, Map.of(), List.of());
    Snapito.expect(Map.of("amount", 1));
    SnapitoTestSupport.enterTest("PaymentServiceTest", "creates a payment", 1, true, Map.of(), List.of());
    Snapito.expect(Map.of("amount", 2));

    assertTrue(Files.exists(snapshotPath("creates-a-payment-iteration-1.json")));
    assertTrue(Files.exists(snapshotPath("creates-a-payment-iteration-2.json")));
  }

  @Test
  void namesIterationsBySnapshotKeyWhenDeclared() {
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("currency", "EUR");
    SnapitoTestSupport.enterTest("PaymentServiceTest", "creates a payment", 0, true, variables, List.of("currency"));

    Snapito.expect(Map.of("amount", 1));

    assertTrue(Files.exists(snapshotPath("creates-a-payment-currency-eur.json")));
  }

  @Test
  void collectsEveryFailureInVerifyAll() {
    Snapito.expectNamed("left", Map.of("value", 1));
    Snapito.expectNamed("right", Map.of("value", 2));
    SnapitoTestSupport.enterTest("PaymentServiceTest", "creates a payment");

    Throwable error = assertThrows(Throwable.class, () -> Snapito.verifyAll(session -> {
      session.json("left", Map.of("value", 9));
      session.json("right", Map.of("value", 9));
    }));

    assertTrue(error.getMessage().contains("2 snapshot failures"));
  }

  @Test
  void reportsIndividualFailureFromVerifyAll() {
    Snapito.expectNamed("left", Map.of("value", 1));
    SnapitoTestSupport.enterTest("PaymentServiceTest", "creates a payment");

    AssertionFailedError error = assertThrows(AssertionFailedError.class, () -> Snapito.verifyAll(session ->
      session.json("left", Map.of("value", 9))));

    assertTrue(error.getMessage().contains("Snapshot mismatch"));
  }

  @Test
  void writesTheProvenanceIndexWhenEnabled() {
    Snapito.configure(config -> config.setWriteIndex(true));
    Snapito.expect(Map.of("amount", 42));
    SnapshotIndex.flush();

    Path index = root.resolve(SnapshotIndex.INDEX_FILE);
    assertTrue(Files.exists(index));
    String content = SnapitoTestSupport.read(index);
    assertTrue(content.contains("creates-a-payment.json"));
    assertTrue(content.contains("PaymentServiceTest"));
  }

  private Path snapshotPath(String fileName) {
    return root.resolve("io/github/micfabian/snapito/fixtures/payment-service-test").resolve(fileName);
  }
}
