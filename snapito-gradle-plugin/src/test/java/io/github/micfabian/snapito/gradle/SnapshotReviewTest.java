package io.github.micfabian.snapito.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotReviewTest {
  @TempDir
  Path root;

  @Test
  void listsChangedAndNewSnapshotsWithTheirDiff() throws IOException {
    Files.createDirectories(root.resolve("api"));
    Files.writeString(root.resolve("api/a.json"), "{\"v\":1}");
    Files.writeString(root.resolve("api/a.json.actual"), "{\"v\":2}");
    Files.writeString(root.resolve("api/a.json.diff.txt"), "v: 1 -> 2 <changed>");
    Files.writeString(root.resolve("api/b.json.actual"), "{}");

    List<SnapshotReview.Pending> pending = SnapshotReview.scan(root);

    assertEquals(2, pending.size());
    assertEquals("api/a.json", pending.get(0).name());
    assertFalse(pending.get(0).created());
    assertTrue(pending.get(1).created());
    String html = SnapshotReview.renderHtml(pending);
    assertTrue(html.contains("v: 1 -&gt; 2 &lt;changed&gt;"));
    assertTrue(html.contains("acceptSnapito -Psnapito.accept='api/a.json'"));
  }

  @Test
  void acceptsOnlySelectedSnapshotsAndCleansArtifacts() throws IOException {
    Files.writeString(root.resolve("a.json"), "old");
    Files.writeString(root.resolve("a.json.actual"), "new");
    Files.writeString(root.resolve("a.json.diff.txt"), "diff");
    Files.writeString(root.resolve("b.json.actual"), "other");

    int accepted = SnapshotReview.accept(root, "a.*");

    assertEquals(1, accepted);
    assertEquals("new", Files.readString(root.resolve("a.json")));
    assertFalse(Files.exists(root.resolve("a.json.actual")));
    assertFalse(Files.exists(root.resolve("a.json.diff.txt")));
    assertTrue(Files.exists(root.resolve("b.json.actual")));
    assertFalse(Files.exists(root.resolve("b.json")));
  }

  @Test
  void acceptAllTakesEverythingPending() throws IOException {
    Files.writeString(root.resolve("a.json.actual"), "1");
    Files.writeString(root.resolve("b.json.actual"), "2");

    assertEquals(2, SnapshotReview.accept(root, "all"));
    assertEquals("2", Files.readString(root.resolve("b.json")));
  }

  @Test
  void missingDirectoryHasNothingPending() {
    assertTrue(SnapshotReview.scan(root.resolve("missing")).isEmpty());
  }
}
