package io.github.micfabian.snapito.comparison;

import io.github.micfabian.snapito.AdvancedComparison;
import io.github.micfabian.snapito.SnapshotDiff;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class XmlComparison implements AdvancedComparison {
  @Override
  public String fileExtension() {
    return "xml";
  }

  @Override
  public Object beforeComparison(Object input) {
    if (input == null) {
      throw new IllegalArgumentException("XmlComparison input must provide XML as String, input was null");
    }
    if (!(input instanceof CharSequence sequence)) {
      throw new IllegalArgumentException(
        "XmlComparison input must provide XML as String, input was " + input.getClass());
    }
    return Xml.canonical(sequence.toString());
  }

  @Override
  public byte[] beforeStore(Object input) {
    return String.valueOf(beforeComparison(input)).getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public Object afterRestore(byte[] bytes) {
    return new String(bytes, StandardCharsets.UTF_8);
  }

  @Override
  public boolean matches(Object expected, Object actual) {
    return String.valueOf(expected).equals(String.valueOf(actual));
  }

  @Override
  public String describeDifference(Object expected, Object actual) {
    return SnapshotDiff.describe(String.valueOf(expected), String.valueOf(actual));
  }

  @Override
  public Map<String, byte[]> differenceArtifacts(byte[] expectedBytes, byte[] actualBytes) {
    return Map.of();
  }
}
