package io.github.micfabian.snapito;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SnapitoConfig {
  private Path rootPath = Paths.get("src/test/resources/snapshots");
  private boolean failOnMissing = false;
  private boolean failOnMissingInCi = true;
  private boolean allowUpdateInCi = false;
  private boolean writeActualOnMismatch = true;
  private boolean writeDiffOnMismatch = true;
  private boolean cleanObsoleteSnapshots = true;
  private boolean atomicWrites = true;
  private boolean reportMissing = false;
  private boolean writeIndex = false;
  private List<String> updateOnly = new ArrayList<>();

  public SnapitoConfig copy() {
    SnapitoConfig copy = new SnapitoConfig();
    copy.rootPath = rootPath;
    copy.failOnMissing = failOnMissing;
    copy.failOnMissingInCi = failOnMissingInCi;
    copy.allowUpdateInCi = allowUpdateInCi;
    copy.writeActualOnMismatch = writeActualOnMismatch;
    copy.writeDiffOnMismatch = writeDiffOnMismatch;
    copy.cleanObsoleteSnapshots = cleanObsoleteSnapshots;
    copy.atomicWrites = atomicWrites;
    copy.reportMissing = reportMissing;
    copy.writeIndex = writeIndex;
    copy.updateOnly = new ArrayList<>(updateOnly);
    return copy;
  }

  public static SnapitoConfig fromEnvironment() {
    SnapitoConfig config = new SnapitoConfig();
    config.rootPath = Paths.get(stringProperty("snapito.snapshot.dir", "src/test/resources/snapshots"));
    config.failOnMissing = booleanProperty("snapito.failOnMissing", false);
    config.failOnMissingInCi = booleanProperty("snapito.failOnMissingInCi", true);
    config.allowUpdateInCi = booleanProperty("snapito.allowUpdateInCi", false);
    config.writeActualOnMismatch = booleanProperty("snapito.writeActual", true);
    config.writeDiffOnMismatch = booleanProperty("snapito.writeDiff", true);
    config.cleanObsoleteSnapshots = booleanProperty("snapito.cleanObsolete", true);
    config.atomicWrites = booleanProperty("snapito.atomicWrites", true);
    config.reportMissing = booleanProperty("snapito.reportMissing", false);
    config.writeIndex = booleanProperty("snapito.writeIndex", false);
    config.updateOnly = listProperty("snapito.updateOnly");
    return config;
  }

  public Path getRootPath() {
    return rootPath;
  }

  public SnapitoConfig setRootPath(Path rootPath) {
    this.rootPath = rootPath;
    return this;
  }

  public boolean isFailOnMissing() {
    return failOnMissing;
  }

  public SnapitoConfig setFailOnMissing(boolean failOnMissing) {
    this.failOnMissing = failOnMissing;
    return this;
  }

  public boolean isFailOnMissingInCi() {
    return failOnMissingInCi;
  }

  public SnapitoConfig setFailOnMissingInCi(boolean failOnMissingInCi) {
    this.failOnMissingInCi = failOnMissingInCi;
    return this;
  }

  public boolean isAllowUpdateInCi() {
    return allowUpdateInCi;
  }

  public SnapitoConfig setAllowUpdateInCi(boolean allowUpdateInCi) {
    this.allowUpdateInCi = allowUpdateInCi;
    return this;
  }

  public boolean isWriteActualOnMismatch() {
    return writeActualOnMismatch;
  }

  public SnapitoConfig setWriteActualOnMismatch(boolean writeActualOnMismatch) {
    this.writeActualOnMismatch = writeActualOnMismatch;
    return this;
  }

  public boolean isWriteDiffOnMismatch() {
    return writeDiffOnMismatch;
  }

  public SnapitoConfig setWriteDiffOnMismatch(boolean writeDiffOnMismatch) {
    this.writeDiffOnMismatch = writeDiffOnMismatch;
    return this;
  }

  public boolean isCleanObsoleteSnapshots() {
    return cleanObsoleteSnapshots;
  }

  public SnapitoConfig setCleanObsoleteSnapshots(boolean cleanObsoleteSnapshots) {
    this.cleanObsoleteSnapshots = cleanObsoleteSnapshots;
    return this;
  }

  public boolean isAtomicWrites() {
    return atomicWrites;
  }

  public SnapitoConfig setAtomicWrites(boolean atomicWrites) {
    this.atomicWrites = atomicWrites;
    return this;
  }

  public boolean isReportMissing() {
    return reportMissing;
  }

  public SnapitoConfig setReportMissing(boolean reportMissing) {
    this.reportMissing = reportMissing;
    return this;
  }

  public boolean isWriteIndex() {
    return writeIndex;
  }

  public SnapitoConfig setWriteIndex(boolean writeIndex) {
    this.writeIndex = writeIndex;
    return this;
  }

  public List<String> getUpdateOnly() {
    return Collections.unmodifiableList(updateOnly);
  }

  public SnapitoConfig setUpdateOnly(List<String> updateOnly) {
    this.updateOnly = updateOnly == null ? new ArrayList<>() : new ArrayList<>(updateOnly);
    return this;
  }

  private static boolean booleanProperty(String name, boolean fallback) {
    String value = System.getProperty(name);
    return value == null ? fallback : value.equalsIgnoreCase("true");
  }

  private static String stringProperty(String primary, String fallback) {
    String value = System.getProperty(primary);
    return value == null || value.isBlank() ? fallback : value;
  }

  private static List<String> listProperty(String name) {
    String value = System.getProperty(name);
    if (value == null || value.isBlank()) {
      return new ArrayList<>();
    }
    List<String> entries = new ArrayList<>();
    for (String entry : Arrays.asList(value.split(","))) {
      String trimmed = entry.trim();
      if (!trimmed.isEmpty()) {
        entries.add(trimmed);
      }
    }
    return entries;
  }
}
