package io.github.micfabian.snapito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.micfabian.snapito.mockito.Interactions;
import io.github.micfabian.snapito.mockito.InvocationReturns;
import io.github.micfabian.snapito.mockito.RecordingMocks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

class InteractionsTest {
  @TempDir
  Path root;

  private LedgerGateway ledger;
  private AuditLog audit;

  @BeforeEach
  void setUp() {
    SnapitoTestSupport.useTemporaryRoot(root);
    SnapitoTestSupport.enterTest("PaymentServiceTest", "books a payment");
    InvocationReturns.clear();
    ledger = RecordingMocks.mock(LedgerGateway.class);
    audit = RecordingMocks.mock(AuditLog.class);
  }

  @AfterEach
  void tearDown() {
    SnapitoTestSupport.leaveTest();
    InvocationReturns.clear();
    Snapito.reloadConfiguration();
  }

  @Test
  void recordsMethodNamesAndArgumentsInCallOrder() {
    ledger.debit("acc-1", 250);
    audit.log("debited");
    ledger.credit("acc-2", 250);

    List<Map<String, Object>> recorded = Interactions.defaults().record(ledger, audit);

    assertEquals(3, recorded.size());
    assertEquals("LedgerGateway.debit(String, int)", recorded.get(0).get("method"));
    assertEquals(List.of("acc-1", 250), recorded.get(0).get("arguments"));
    assertEquals("AuditLog.log(String)", recorded.get(1).get("method"));
    assertEquals("LedgerGateway.credit(String, int)", recorded.get(2).get("method"));
  }

  @Test
  void writesAndMatchesAnInteractionSnapshot() {
    ledger.debit("acc-1", 250);
    audit.log("debited");
    Snapito.expectInteractions(ledger, audit);

    Path snapshot = snapshotPath("books-a-payment.interactions.json");
    assertTrue(Files.exists(snapshot));
    assertTrue(SnapitoTestSupport.read(snapshot).contains("LedgerGateway.debit(String, int)"));

    SnapitoTestSupport.enterTest("PaymentServiceTest", "books a payment");
    LedgerGateway sameLedger = RecordingMocks.mock(LedgerGateway.class);
    AuditLog sameAudit = RecordingMocks.mock(AuditLog.class);
    sameLedger.debit("acc-1", 250);
    sameAudit.log("debited");

    Snapito.expectInteractions(sameLedger, sameAudit);
  }

  @Test
  void failsWhenAnInteractionChanges() {
    ledger.debit("acc-1", 250);
    Snapito.expectInteractions(ledger);

    SnapitoTestSupport.enterTest("PaymentServiceTest", "books a payment");
    LedgerGateway changed = RecordingMocks.mock(LedgerGateway.class);
    changed.debit("acc-1", 999);

    AssertionFailedError error = assertThrows(AssertionFailedError.class,
      () -> Snapito.expectInteractions(changed));

    assertTrue(error.getMessage().contains("Snapshot mismatch"));
    assertTrue(error.getMessage().contains("expected 250, but was 999"));
  }

  @Test
  void failsWhenAnExtraInteractionAppears() {
    ledger.debit("acc-1", 250);
    Snapito.expectInteractions(ledger);

    SnapitoTestSupport.enterTest("PaymentServiceTest", "books a payment");
    LedgerGateway extra = RecordingMocks.mock(LedgerGateway.class);
    extra.debit("acc-1", 250);
    extra.credit("acc-2", 250);

    AssertionFailedError error = assertThrows(AssertionFailedError.class,
      () -> Snapito.expectInteractions(extra));

    assertTrue(error.getMessage().contains("size mismatch"));
  }

  @Test
  void capturesReturnValuesFromRecordingMocks() {
    when(ledger.balance("acc-1")).thenReturn(1250);
    ledger.balance("acc-1");

    List<Map<String, Object>> recorded = Interactions.defaults().record(ledger);

    assertEquals(1250, recorded.get(0).get("returnValue"));
  }

  @Test
  void capturesThrownExceptions() {
    when(ledger.balance("missing")).thenThrow(new IllegalStateException("no account"));
    assertThrows(IllegalStateException.class, () -> ledger.balance("missing"));

    List<Map<String, Object>> recorded = Interactions.defaults().record(ledger);

    assertEquals("java.lang.IllegalStateException: no account", recorded.get(0).get("thrown"));
  }

  @Test
  void distinguishesReturnValuesOfIdenticalRepeatedCalls() {
    when(ledger.balance("acc-1")).thenReturn(1).thenReturn(2);
    ledger.balance("acc-1");
    ledger.balance("acc-1");

    List<Map<String, Object>> recorded = Interactions.defaults().record(ledger);

    assertEquals(2, recorded.size());
    assertEquals(1, recorded.get(0).get("returnValue"));
    assertEquals(2, recorded.get(1).get("returnValue"));
  }

  @Test
  void repeatedRecordingYieldsTheSameResult() {
    when(ledger.balance("acc-1")).thenReturn(1).thenReturn(2);
    ledger.balance("acc-1");
    ledger.balance("acc-1");

    assertEquals(Interactions.defaults().record(ledger), Interactions.defaults().record(ledger));
  }

  @Test
  void omitsReturnValuesWhenConfigured() {
    when(ledger.balance("acc-1")).thenReturn(1250);
    ledger.balance("acc-1");

    List<Map<String, Object>> recorded = Interactions.configured(Interactions::withoutReturnValues).record(ledger);

    assertFalse(recorded.get(0).containsKey("returnValue"));
  }

  @Test
  void ignoresConfiguredMethods() {
    ledger.debit("acc-1", 250);
    ledger.balance("acc-1");

    List<Map<String, Object>> recorded =
      Interactions.configured(interactions -> interactions.ignoringMethods("balance")).record(ledger);

    assertEquals(1, recorded.size());
    assertEquals("LedgerGateway.debit(String, int)", recorded.get(0).get("method"));
  }

  @Test
  void restrictsToSelectedMethods() {
    ledger.debit("acc-1", 250);
    ledger.credit("acc-2", 250);

    List<Map<String, Object>> recorded =
      Interactions.configured(interactions -> interactions.onlyMethods("credit")).record(ledger);

    assertEquals(1, recorded.size());
    assertEquals("LedgerGateway.credit(String, int)", recorded.get(0).get("method"));
  }

  @Test
  void redactsMatchingArguments() {
    ledger.debit("acc-4711", 250);

    List<Map<String, Object>> recorded = Interactions
      .configured(interactions -> interactions.replacing("acc-[0-9]+", "<account>"))
      .record(ledger);

    assertEquals(List.of("<account>", 250), recorded.get(0).get("arguments"));
  }

  @Test
  void unorderedRecordingIsStableRegardlessOfCallOrder() {
    ledger.credit("acc-2", 1);
    ledger.debit("acc-1", 1);

    LedgerGateway reversed = RecordingMocks.mock(LedgerGateway.class);
    reversed.debit("acc-1", 1);
    reversed.credit("acc-2", 1);

    assertEquals(
      Interactions.configured(Interactions::unordered).record(ledger),
      Interactions.configured(Interactions::unordered).record(reversed));
  }

  @Test
  void orderedRecordingDistinguishesCallOrder() {
    ledger.credit("acc-2", 1);
    ledger.debit("acc-1", 1);

    LedgerGateway reversed = RecordingMocks.mock(LedgerGateway.class);
    reversed.debit("acc-1", 1);
    reversed.credit("acc-2", 1);

    List<Map<String, Object>> left = Interactions.configured(Interactions::withoutSequence).record(ledger);
    List<Map<String, Object>> right = Interactions.configured(Interactions::withoutSequence).record(reversed);

    assertEquals("LedgerGateway.credit(String, int)", left.get(0).get("method"));
    assertEquals("LedgerGateway.debit(String, int)", right.get(0).get("method"));
  }

  @Test
  void keepsOnlyVerifiedInteractionsWhenRequested() {
    ledger.debit("acc-1", 250);
    ledger.credit("acc-2", 250);
    verify(ledger).debit("acc-1", 250);

    List<Map<String, Object>> recorded = Interactions.configured(Interactions::onlyVerified).record(ledger);

    assertEquals(1, recorded.size());
    assertEquals("LedgerGateway.debit(String, int)", recorded.get(0).get("method"));
  }

  @Test
  void rendersNestedMocksByName() {
    Receipt receipt = RecordingMocks.mock(Receipt.class, "receipt");
    audit.attach(receipt);

    List<Map<String, Object>> recorded = Interactions.defaults().record(audit);

    assertEquals(List.of("mock:receipt"), recorded.get(0).get("arguments"));
  }

  @Test
  void usesQualifiedMethodNamesWhenRequested() {
    ledger.debit("acc-1", 1);

    List<Map<String, Object>> recorded =
      Interactions.configured(Interactions::withQualifiedMethodNames).record(ledger);

    assertEquals("io.github.micfabian.snapito.InteractionsTest$LedgerGateway.debit(String, int)",
      recorded.get(0).get("method"));
  }

  @Test
  void flattensArrayArguments() {
    ledger.batch(new String[]{"acc-1", "acc-2"});

    List<Map<String, Object>> recorded = Interactions.defaults().record(ledger);

    assertEquals(List.of(List.of("acc-1", "acc-2")), recorded.get(0).get("arguments"));
  }

  @Test
  void rejectsNonMockArguments() {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
      () -> Interactions.defaults().record("not a mock"));

    assertTrue(error.getMessage().contains("can only be recorded from Mockito mocks"));
  }

  @Test
  void worksWithPlainMockitoMocksWithoutReturnValues() {
    LedgerGateway plain = mock(LedgerGateway.class);
    plain.debit("acc-1", 250);

    List<Map<String, Object>> recorded = Interactions.defaults().record(plain);

    assertEquals(1, recorded.size());
    assertFalse(recorded.get(0).containsKey("returnValue"));
  }

  @Test
  void namesInteractionSnapshotsExplicitly() {
    ledger.debit("acc-1", 250);
    Snapito.expectInteractionsNamed("ledger-calls", ledger);

    assertTrue(Files.exists(snapshotPath("ledger-calls.interactions.json")));
  }

  @Test
  void collectsInteractionsInsideVerifyAll() {
    ledger.debit("acc-1", 250);
    audit.log("debited");

    Snapito.verifyAll(session -> {
      session.interactions("ledger", ledger);
      session.interactions("audit", audit);
    });

    assertTrue(Files.exists(snapshotPath("ledger.interactions.json")));
    assertTrue(Files.exists(snapshotPath("audit.interactions.json")));
  }

  private Path snapshotPath(String fileName) {
    return root.resolve("io/github/micfabian/snapito/fixtures/payment-service-test").resolve(fileName);
  }

  interface LedgerGateway {
    void debit(String account, int amount);

    void credit(String account, int amount);

    int balance(String account);

    void batch(String[] accounts);
  }

  interface AuditLog {
    void log(String message);

    void attach(Receipt receipt);
  }

  interface Receipt {
    String id();
  }
}
