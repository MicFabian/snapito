package io.github.micfabian.snapito;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.micfabian.snapito.junit.SnapitoExtension;
import io.github.micfabian.snapito.mockito.Interactions;
import io.github.micfabian.snapito.mockito.RecordingMocks;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ExtendWith(SnapitoExtension.class)
class DocumentedExamplesTest {
  @TempDir
  Path root;

  interface LedgerGateway {
    void debit(String account, int amount);
  }

  @BeforeEach
  void setUp() {
    SnapitoTestSupport.useTemporaryRoot(root);
  }

  @AfterEach
  void tearDown() {
    Snapito.reloadConfiguration();
  }

  @Test
  void theValueSnapshotExamplesCompileAndRun() {
    Snapito.expect(Map.of("amount", 250));
    Snapito.expect(Map.of("a", 1), Comparisons.JSON);
    Snapito.expectNamed("response", Map.of("b", 2));
  }

  @Test
  void theStabilityExampleCompilesAndRuns() {
    var comparison = Comparisons.json(json -> json
      .excludingPaths("$.request.id", "$.items[*].createdAt")
      .excludingProperties("traceId")
      .excludingTypes(Instant.class)
      .unordered("$.roles")
      .sortedBy("$.items", "sku")
      .replacing("[0-9a-f]{8}-[0-9a-f-]{27}", "<uuid>")
      .within(0.01));

    Snapito.expect(Map.of("roles", List.of("b", "a"), "traceId", "x"), comparison);
  }

  @Test
  void derivingFromASharedConstantWorksWhileMutatingItThrows() {
    var derived = Comparisons.JSON.with(json -> json.excludingProperties("id"));
    Snapito.expect(Map.of("id", 1, "keep", 2), derived);

    assertThrows(IllegalStateException.class, () -> Comparisons.JSON.excludingProperties("id"));
  }

  @Test
  void theInteractionExamplesCompileAndRun() {
    LedgerGateway ledger = RecordingMocks.mock(LedgerGateway.class);
    ledger.debit("acc-1", 250);

    Snapito.expectInteractions(ledger);
    Snapito.expectInteractionsNamed("named", ledger);
    Snapito.expectInteractions(
      Interactions.configured(i -> i
        .ignoringMethods("toString", "hashCode")
        .replacing("acc-[0-9]+", "<account>")
        .unordered()),
      ledger);
  }

  @Test
  void theVerifyAllExampleCompilesAndRuns() {
    LedgerGateway ledger = RecordingMocks.mock(LedgerGateway.class);
    ledger.debit("acc-1", 1);

    Snapito.verifyAll(session -> {
      session.json("response", Map.of("a", 1));
      session.interactions("ledger", ledger);
    });
  }

  @Test
  void theUpdateExamplesCompileAndRun() {
    Snapito.expectNamed("updatable", Map.of("v", 1));
    Snapito.updateSnapshot(Map.of("v", 2));
    Snapito.withUpdate(() -> Snapito.expectNamed("updatable", Map.of("v", 3)));
  }

  @Test
  void theReturningFormAssertsAndReturns() {
    Object first = Snapito.snapshotNamed("returning", Map.of("v", 1));
    assertTrue(String.valueOf(first).contains("1"));
  }

  @ParameterizedTest
  @CsvSource({"EUR, 5", "USD, 7"})
  @SnapshotKey("currency")
  void booksAPayment(String currency, int amount) {
    Snapito.expect(Map.of("currency", currency, "amount", amount));
  }
}
