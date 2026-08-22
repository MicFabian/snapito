package io.github.micfabian.snapito.mockito;

import io.github.micfabian.snapito.comparison.JsonComparison;

public class InteractionComparison extends JsonComparison {
  @Override
  public String fileExtension() {
    return "interactions.json";
  }

  @Override
  public InteractionComparison shared() {
    super.shared();
    return this;
  }

  @Override
  public InteractionComparison copy() {
    InteractionComparison copy = new InteractionComparison();
    copyInto(copy);
    return copy;
  }
}
