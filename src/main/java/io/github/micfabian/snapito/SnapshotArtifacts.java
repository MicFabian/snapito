package io.github.micfabian.snapito;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class SnapshotArtifacts {
  private static final ConcurrentHashMap<Path, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

  private SnapshotArtifacts() {
  }

  public static byte[] read(Path path) {
    try {
      return Files.readAllBytes(path);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static void write(Path path, byte[] bytes) {
    write(path, bytes, true);
  }

  public static void write(Path path, byte[] bytes, boolean atomic) {
    Path absolute = path.toAbsolutePath().normalize();
    ReentrantLock lock = LOCKS.computeIfAbsent(absolute, ignored -> new ReentrantLock());
    lock.lock();
    try {
      Files.createDirectories(absolute.getParent());
      if (!atomic) {
        Files.write(absolute, bytes);
        return;
      }

      Path temporary = Files.createTempFile(absolute.getParent(), "." + absolute.getFileName() + ".", ".tmp");
      try {
        Files.write(temporary, bytes);
        try {
          Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
          Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
      } finally {
        Files.deleteIfExists(temporary);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      lock.unlock();
    }
  }

  public static Path actualPath(Path snapshot) {
    return sibling(snapshot, ".actual");
  }

  public static Path textDiffPath(Path snapshot) {
    return sibling(snapshot, ".diff.txt");
  }

  public static Path artifactPath(Path snapshot, String suffix) {
    return sibling(snapshot, suffix.startsWith(".") ? suffix : "." + suffix);
  }

  public static void clearFailureArtifacts(Path snapshot) {
    clearFailureArtifacts(snapshot, List.of());
  }

  public static void clearFailureArtifacts(Path snapshot, Collection<String> additionalSuffixes) {
    try {
      Files.deleteIfExists(actualPath(snapshot));
      Files.deleteIfExists(textDiffPath(snapshot));
      for (String suffix : additionalSuffixes) {
        Files.deleteIfExists(artifactPath(snapshot, suffix));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Path sibling(Path snapshot, String suffix) {
    return snapshot.resolveSibling(snapshot.getFileName().toString() + suffix);
  }
}
