package io.github.micfabian.snapito;

import io.github.micfabian.snapito.mockito.Interactions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SnapshotSession {
  private final List<SnapshotResult> collected = new ArrayList<>();

  public List<SnapshotResult> getResults() {
    return Collections.unmodifiableList(collected);
  }

  public List<SnapshotResult> getFailures() {
    return collected.stream().filter(SnapshotResult::isFailure).collect(Collectors.toList());
  }

  public void expect(Object actual) {
    expect(actual, Comparisons.detect(actual));
  }

  public void expect(Object actual, Comparison comparison) {
    collected.add(Snapito.evaluate(actual, comparison));
  }

  public void expect(String name, Object actual) {
    expect(name, actual, Comparisons.detect(actual));
  }

  public void expect(String name, Object actual, Comparison comparison) {
    Snapito.withName(name, () -> collected.add(Snapito.evaluate(actual, comparison)));
  }

  public void json(String name, Object actual) {
    expect(name, actual, Comparisons.JSON);
  }

  public void xml(String name, Object actual) {
    expect(name, actual, Comparisons.XML);
  }

  public void text(String name, Object actual) {
    expect(name, actual, Comparisons.TXT);
  }

  public void csv(String name, Object actual) {
    expect(name, actual, Comparisons.CSV);
  }

  public void html(String name, Object actual) {
    expect(name, actual, Comparisons.HTML);
  }

  public void png(String name, byte[] actual) {
    expect(name, actual, Comparisons.PNG);
  }

  public void binary(String name, byte[] actual) {
    expect(name, actual, Comparisons.BINARY);
  }

  public void interactions(String name, Object... mocks) {
    interactions(name, Interactions.defaults(), mocks);
  }

  public void interactions(String name, Interactions interactions, Object... mocks) {
    expect(name, interactions.record(mocks), Comparisons.INTERACTIONS);
  }
}
