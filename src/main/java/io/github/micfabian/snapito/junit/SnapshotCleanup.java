package io.github.micfabian.snapito.junit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SnapshotCleanup {
  private static final Logger LOG = LoggerFactory.getLogger(SnapshotCleanup.class);
  private static final Map<Path, Set<String>> REFERENCED = new ConcurrentHashMap<>();

  private SnapshotCleanup() {
  }

  public static void reference(Path file) {
    Path directory = file.toAbsolutePath().normalize().getParent();
    REFERENCED.computeIfAbsent(directory, ignored -> ConcurrentHashMap.newKeySet())
      .add(file.getFileName().toString());
  }

  public static void cleanup(Path packageDir, boolean enabled) {
    Path key = packageDir.toAbsolutePath().normalize();
    Set<String> keep = REFERENCED.remove(key);
    if (!enabled) {
      LOG.debug("Not cleaning obsolete snapshots in {}", key);
      return;
    }
    if (!Files.isDirectory(key)) {
      return;
    }

    Set<String> keepNames = new HashSet<>(keep == null ? Set.of() : keep);
    try (Stream<Path> files = Files.list(key)) {
      files.filter(Files::isRegularFile)
        .filter(path -> !keepNames.contains(path.getFileName().toString()))
        .forEach(path -> {
          try {
            Files.deleteIfExists(path);
            LOG.warn("Deleted obsolete snapshot {}", path);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static void clear() {
    REFERENCED.clear();
  }
}
