package io.github.micfabian.snapito;

public interface Comparison {
  String fileExtension();

  Object beforeComparison(Object input);

  byte[] beforeStore(Object input);

  Object afterRestore(byte[] bytes);
}
