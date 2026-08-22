package io.github.micfabian.snapito;

import java.nio.file.Path;

public class SnapshotResult {
  public enum Status {
    MATCHED, WRITTEN, MISMATCH, MISSING, SKIPPED_UPDATE
  }

  private final Status status;
  private final Path resource;
  private final String message;
  private final String diff;
  private final Object expected;
  private final Object actual;

  private SnapshotResult(Builder builder) {
    this.status = builder.status;
    this.resource = builder.resource;
    this.message = builder.message;
    this.diff = builder.diff;
    this.expected = builder.expected;
    this.actual = builder.actual;
  }

  public static Builder builder(Status status, Path resource) {
    return new Builder(status, resource);
  }

  public Status getStatus() {
    return status;
  }

  public Path getResource() {
    return resource;
  }

  public String getMessage() {
    return message;
  }

  public String getDiff() {
    return diff;
  }

  public Object getExpected() {
    return expected;
  }

  public Object getActual() {
    return actual;
  }

  public boolean isFailure() {
    return status == Status.MISMATCH || status == Status.MISSING || status == Status.SKIPPED_UPDATE;
  }

  public static final class Builder {
    private final Status status;
    private final Path resource;
    private String message;
    private String diff;
    private Object expected;
    private Object actual;

    private Builder(Status status, Path resource) {
      this.status = status;
      this.resource = resource;
    }

    public Builder message(String message) {
      this.message = message;
      return this;
    }

    public Builder diff(String diff) {
      this.diff = diff;
      return this;
    }

    public Builder expected(Object expected) {
      this.expected = expected;
      return this;
    }

    public Builder actual(Object actual) {
      this.actual = actual;
      return this;
    }

    public SnapshotResult build() {
      return new SnapshotResult(this);
    }
  }
}
