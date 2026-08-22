package io.github.micfabian.snapito.comparison;

import io.github.micfabian.snapito.AdvancedComparison;
import io.github.micfabian.snapito.SnapshotDiff;
import java.util.Arrays;
import java.util.Map;

public class BinaryComparison implements AdvancedComparison {
  private final String extension;

  public BinaryComparison() {
    this("bin");
  }

  public BinaryComparison(String extension) {
    this.extension = extension;
  }

  @Override
  public String fileExtension() {
    return extension;
  }

  @Override
  public Object beforeComparison(Object input) {
    return input;
  }

  @Override
  public byte[] beforeStore(Object input) {
    if (!(input instanceof byte[] bytes)) {
      throw new IllegalArgumentException(
        "BinaryComparison input must provide a byte[], input was " + (input == null ? "null" : input.getClass()));
    }
    return bytes;
  }

  @Override
  public Object afterRestore(byte[] bytes) {
    return bytes;
  }

  @Override
  public boolean matches(Object expected, Object actual) {
    if (expected instanceof byte[] left && actual instanceof byte[] right) {
      return Arrays.equals(left, right);
    }
    return java.util.Objects.equals(expected, actual);
  }

  @Override
  public String describeDifference(Object expected, Object actual) {
    if (expected instanceof byte[] left && actual instanceof byte[] right) {
      if (left.length != right.length) {
        return "Binary length mismatch: expected " + left.length + " bytes, but was " + right.length + " bytes";
      }
      int index = Arrays.mismatch(left, right);
      if (index < 0) {
        return "";
      }
      return "Binary content differs at byte " + index
        + ": expected 0x" + hex(left[index]) + ", but was 0x" + hex(right[index]);
    }
    return SnapshotDiff.describe(expected, actual);
  }

  @Override
  public Map<String, byte[]> differenceArtifacts(byte[] expectedBytes, byte[] actualBytes) {
    return Map.of();
  }

  private static String hex(byte value) {
    return String.format("%02x", value);
  }
}
