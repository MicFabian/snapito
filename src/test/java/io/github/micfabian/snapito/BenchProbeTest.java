package io.github.micfabian.snapito;

import io.github.micfabian.snapito.comparison.HtmlComparison;
import io.github.micfabian.snapito.comparison.XmlComparison;
import io.github.micfabian.snapito.mockito.Interactions;
import io.github.micfabian.snapito.mockito.RecordingMocks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("probe")
class BenchProbeTest {
  interface Ledger { void debit(String a, int b); void bulk(List<String> xs); }

  static void time(String label, Runnable r) {
    for (int i = 0; i < 20; i++) r.run();
    long t0 = System.nanoTime();
    for (int i = 0; i < 20; i++) r.run();
    System.out.println("  " + label + " = " + ((System.nanoTime() - t0) / 1_000_000 / 20.0) + "ms");
  }

  @Test
  void bench() {
    System.out.println("=== BENCH ===");

    Map<String, Object> big = new LinkedHashMap<>();
    for (int i = 0; i < 2000; i++) big.put("key" + i, Map.of("a", i, "b", "v" + i, "c", List.of(1, 2, 3)));
    time("JSON normalize 2000", () -> Comparisons.JSON.beforeComparison(big));
    time("JSON store 2000", () -> Comparisons.JSON.beforeStore(big));

    Map<String, Object> l = new LinkedHashMap<>(), r2 = new LinkedHashMap<>();
    for (int i = 0; i < 4000; i++) { l.put("k" + i, i); r2.put("k" + i, i == 3999 ? -1 : i); }
    time("Diff 4000 keys", () -> SnapshotDiff.describe(l, r2));

    Map<String, Object> d1 = new LinkedHashMap<>(), d2 = new LinkedHashMap<>();
    for (int i = 0; i < 8000; i++) { d1.put("L" + i, i); d2.put("R" + i, i); }
    time("Diff 8000 disjoint", () -> SnapshotDiff.describe(d1, d2));

    Ledger ledger = RecordingMocks.mock(Ledger.class);
    for (int i = 0; i < 4000; i++) ledger.debit("acc" + i, i);
    time("Interactions 4000 calls", () -> Interactions.defaults().record(ledger));

    Ledger bulk = RecordingMocks.mock(Ledger.class);
    List<String> payload = new ArrayList<>();
    for (int i = 0; i < 200000; i++) payload.add("item" + i);
    bulk.bulk(payload);
    time("Interactions 200k-elem arg", () -> Interactions.defaults().record(bulk));

    StringBuilder xml = new StringBuilder("<root>");
    for (int i = 0; i < 3000; i++) xml.append("<item id='").append(i).append("'>v").append(i).append("</item>");
    String xmlText = xml.append("</root>").toString();
    var xc = new XmlComparison();
    time("XML canonical 3000 nodes", () -> xc.beforeComparison(xmlText));

    StringBuilder html = new StringBuilder("<div>");
    for (int i = 0; i < 3000; i++) html.append("<p class='b a'>t").append(i).append("</p>");
    String htmlText = html.append("</div>").toString();
    var hc = new HtmlComparison();
    time("HTML canonical 3000 nodes", () -> hc.beforeComparison(htmlText));

    StringBuilder csv = new StringBuilder("a,b,c\n");
    for (int i = 0; i < 20000; i++) csv.append(i).append(",x").append(i).append(",y\n");
    String csvText = csv.toString();
    time("CSV 20000 rows", () -> Comparisons.detect(csvText).beforeComparison(csvText));

    try {
      java.nio.file.Path root = java.nio.file.Files.createTempDirectory("snapito-bench");
      SnapitoTestSupport.useTemporaryRoot(root);
      SnapitoTestSupport.enterTest("BenchTest", "matches");
      Snapito.expectNamed("hot", big);
      time("evaluate MATCH 2000-entry map", () -> {
        SnapitoTestSupport.enterTest("BenchTest", "matches");
        Snapito.expectNamed("hot", big);
      });
      SnapitoTestSupport.leaveTest();
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }

    System.out.println("=== END BENCH ===");
  }
}
