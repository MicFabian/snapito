package io.github.micfabian.snapito;

import io.github.micfabian.snapito.mockito.Interactions;
import io.github.micfabian.snapito.mockito.RecordingMocks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

@Tag("probe")
class BenchProbeTest {
  interface Ledger { void debit(String a, int b); void bulk(List<String> xs); }

  static long time(String label, Runnable r) {
    r.run(); r.run();
    long t0 = System.nanoTime();
    for (int i = 0; i < 5; i++) r.run();
    long ms = (System.nanoTime() - t0) / 1_000_000 / 5;
    System.out.println("  " + label + " = " + ms + "ms");
    return ms;
  }

  @Test
  void bench() {
    System.out.println("=== BENCH ===");

    Map<String, Object> big = new LinkedHashMap<>();
    for (int i = 0; i < 2000; i++) {
      big.put("key" + i, Map.of("a", i, "b", "value" + i, "c", List.of(1, 2, 3)));
    }
    var json = Comparisons.JSON;
    time("JSON normalize 2000 entries", () -> json.beforeComparison(big));
    time("JSON store 2000 entries", () -> json.beforeStore(big));

    var excl = Comparisons.json(j -> j.excludingProperties("a").excludingPaths("$.key1.b"));
    time("JSON normalize w/ exclusions", () -> excl.beforeComparison(big));

    for (int n : new int[]{500, 1000, 2000, 4000}) {
      Map<String, Object> l = new LinkedHashMap<>(), r2 = new LinkedHashMap<>();
      for (int i = 0; i < n; i++) { l.put("k" + i, i); r2.put("k" + i, i == n - 1 ? -1 : i); }
      time("SnapshotDiff " + n + " keys", () -> SnapshotDiff.describe(l, r2));
    }
    for (int n : new int[]{1000, 2000, 4000, 8000}) {
      Map<String, Object> l2 = new LinkedHashMap<>(), r3 = new LinkedHashMap<>();
      for (int i = 0; i < n; i++) { l2.put("left" + i, i); r3.put("right" + i, i); }
      time("SnapshotDiff " + n + " DISJOINT keys", () -> SnapshotDiff.describe(l2, r3));
    }
    for (int n : new int[]{500, 1000, 2000, 4000}) {
      Ledger scale = RecordingMocks.mock(Ledger.class);
      for (int i = 0; i < n; i++) scale.debit("acc" + i, i);
      time("Interactions " + n + " calls", () -> Interactions.defaults().record(scale));
    }

    Ledger ledger = RecordingMocks.mock(Ledger.class);
    for (int i = 0; i < 2000; i++) ledger.debit("acc" + i, i);
    time("Interactions record 2000 calls", () -> Interactions.defaults().record(ledger));

    Ledger bulk = RecordingMocks.mock(Ledger.class);
    List<String> payload = new ArrayList<>();
    for (int i = 0; i < 50000; i++) payload.add("item" + i);
    bulk.bulk(payload);
    time("Interactions 50k-element arg", () -> Interactions.defaults().record(bulk));

    Ledger huge = RecordingMocks.mock(Ledger.class);
    List<String> huge200k = new ArrayList<>();
    for (int i = 0; i < 200000; i++) huge200k.add("item" + i);
    huge.bulk(huge200k);
    time("Interactions 200k-element arg", () -> Interactions.defaults().record(huge));

    StringBuilder csv = new StringBuilder("a,b,c\n");
    for (int i = 0; i < 5000; i++) csv.append(i).append(",x").append(i).append(",y\n");
    String csvText = csv.toString();
    time("CSV detect+parse 5000 rows", () -> Comparisons.detect(csvText).beforeComparison(csvText));

    System.out.println("=== END BENCH ===");
  }
}
