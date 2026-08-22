package io.github.micfabian.snapito;

import io.github.micfabian.snapito.junit.SnapitoContext;
import io.github.micfabian.snapito.junit.SnapshotCleanup;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class SnapitoTestSupport {
  private SnapitoTestSupport() {
  }

  public static void useTemporaryRoot(Path root) {
    Snapito.configure(config -> config
      .setRootPath(root)
      .setFailOnMissing(false)
      .setFailOnMissingInCi(true)
      .setAllowUpdateInCi(false)
      .setReportMissing(false)
      .setWriteIndex(false)
      .setUpdateOnly(List.of()));
    System.setProperty("snapito.ci", "false");
    System.clearProperty("snapito.snapshot.update");
    System.clearProperty("snapito.snapshot.clean");
    SnapshotCleanup.clear();
    MissingSnapshots.clear();
    SnapshotIndex.clear();
  }

  public static void enterTest(String testClass, String testMethod) {
    enterTest(testClass, testMethod, 0, false, Map.of(), List.of());
  }

  public static void enterTest(
      String testClass,
      String testMethod,
      int iterationIndex,
      boolean parameterized,
      Map<String, Object> dataVariables,
      List<String> snapshotKeyVariables) {
    SnapitoContext.set(new SnapshotInvocationContext(
      testMethod,
      "io.github.micfabian.snapito.fixtures",
      testClass,
      iterationIndex,
      parameterized,
      dataVariables,
      snapshotKeyVariables));
  }

  public static void leaveTest() {
    SnapitoContext.clear();
  }

  public static void deleteRecursively(Path path) {
    if (!Files.exists(path)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(path)) {
      paths.sorted(Comparator.reverseOrder()).forEach(entry -> {
        try {
          Files.deleteIfExists(entry);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
