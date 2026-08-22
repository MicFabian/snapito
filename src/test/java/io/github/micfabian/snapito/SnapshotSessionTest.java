package io.github.micfabian.snapito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class SnapshotSessionTest {
  @TempDir
  Path root;

  @BeforeEach
  void setUp() {
    SnapitoTestSupport.useTemporaryRoot(root);
    SnapitoTestSupport.enterTest("ReportServiceTest", "buildsAReport");
  }

  @AfterEach
  void tearDown() {
    SnapitoTestSupport.leaveTest();
    Snapito.reloadConfiguration();
  }

  @Test
  void acceptsAnEmptySession() {
    Snapito.verifyAll(session -> { });
  }

  @Test
  void recordsEveryResultIncludingSuccesses() {
    SnapshotSession session = new SnapshotSession();
    session.json("first", Map.of("a", 1));
    session.json("second", Map.of("b", 2));

    assertEquals(2, session.getResults().size());
    assertEquals(List.of(), session.getFailures());
    assertTrue(session.getResults().stream()
      .allMatch(result -> result.getStatus() == SnapshotResult.Status.WRITTEN));
  }

  @Test
  void writesEachFormatWithItsOwnExtension() {
    Snapito.verifyAll(session -> {
      session.json("payload", Map.of("a", 1));
      session.xml("document", "<root><item>1</item></root>");
      session.text("note", "hello");
      session.csv("table", "a,b\n1,2");
      session.html("page", "<div><span>hi</span></div>");
      session.binary("blob", new byte[]{1, 2, 3});
    });

    assertTrue(Files.exists(snapshot("payload.json")));
    assertTrue(Files.exists(snapshot("document.xml")));
    assertTrue(Files.exists(snapshot("note.txt")));
    assertTrue(Files.exists(snapshot("table.csv")));
    assertTrue(Files.exists(snapshot("page.html")));
    assertTrue(Files.exists(snapshot("blob.bin")));
  }

  @Test
  void writesPngSnapshotsThroughTheSession() throws Exception {
    Snapito.verifyAll(session -> session.png("chart", ComparisonsTest.pngBytes()));

    assertTrue(Files.exists(snapshot("chart.png")));
  }

  @Test
  void writesInteractionSnapshotsThroughTheSession() {
    Ledger ledger = RecordingMocks.mock(Ledger.class);
    ledger.debit("acc-1", 10);

    Snapito.verifyAll(session -> session.interactions("ledger", ledger));

    assertTrue(Files.exists(snapshot("ledger.interactions.json")));
  }

  @Test
  void detectsTheComparisonForUnnamedSessionSnapshots() {
    Snapito.verifyAll(session -> session.expect(Map.of("a", 1)));

    assertTrue(Files.exists(snapshot("builds-a-report.json")));
  }

  @Test
  void keepsEvaluatingAfterTheFirstFailure() {
    Snapito.verifyAll(session -> {
      session.json("left", Map.of("v", 1));
      session.json("right", Map.of("v", 2));
    });
    SnapitoTestSupport.enterTest("ReportServiceTest", "buildsAReport");

    SnapshotSession session = new SnapshotSession();
    session.json("left", Map.of("v", 9));
    session.json("right", Map.of("v", 9));

    assertEquals(2, session.getFailures().size(),
      "A session must evaluate every snapshot, not stop at the first mismatch");
  }

  @Test
  void reportsEveryFailingSnapshotPathInTheAggregateError() {
    Snapito.verifyAll(session -> {
      session.json("left", Map.of("v", 1));
      session.json("right", Map.of("v", 2));
    });
    SnapitoTestSupport.enterTest("ReportServiceTest", "buildsAReport");

    Throwable error = assertThrows(Throwable.class, () -> Snapito.verifyAll(session -> {
      session.json("left", Map.of("v", 9));
      session.json("right", Map.of("v", 9));
    }));

    assertTrue(error.getMessage().contains("2 snapshot failures"));
  }

  @Test
  void exposesResultsAsAnUnmodifiableView() {
    SnapshotSession session = new SnapshotSession();
    session.json("only", Map.of("a", 1));

    assertThrows(UnsupportedOperationException.class, () -> session.getResults().clear());
  }

  @Test
  void reportsMatchedStatusOnASecondRun() {
    Snapito.verifyAll(session -> session.json("stable", Map.of("a", 1)));
    SnapitoTestSupport.enterTest("ReportServiceTest", "buildsAReport");

    SnapshotSession session = new SnapshotSession();
    session.json("stable", Map.of("a", 1));

    assertEquals(SnapshotResult.Status.MATCHED, session.getResults().get(0).getStatus());
  }

  private Path snapshot(String fileName) {
    return root.resolve("io/github/micfabian/snapito/fixtures/report-service-test").resolve(fileName);
  }

  interface Ledger {
    void debit(String account, int amount);
  }
}
