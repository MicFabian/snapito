package io.github.micfabian.snapito;

import java.util.Map;

public interface AdvancedComparison extends Comparison {
  boolean matches(Object expected, Object actual);

  String describeDifference(Object expected, Object actual);

  Map<String, byte[]> differenceArtifacts(byte[] expectedBytes, byte[] actualBytes);
}
