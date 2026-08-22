package io.github.micfabian.snapito.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import io.github.micfabian.snapito.SnapshotInvocationContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.platform.testkit.engine.EngineTestKit;

class SnapshotKeyNamingTest {
  @Test
  void derivesSnapshotKeysFromNamedParameters() {
    KeyedProbe.observedKeys.clear();

    EngineTestKit.engine("junit-jupiter")
      .selectors(selectClass(KeyedProbe.class))
      .execute()
      .testEvents()
      .assertStatistics(stats -> stats.succeeded(2));

    assertEquals(List.of("currency-EUR", "currency-USD"), KeyedProbe.observedKeys);
  }

  @Test
  void derivesSnapshotKeysFromEveryParameterWhenUnspecified() {
    AllParametersProbe.observedKeys.clear();

    EngineTestKit.engine("junit-jupiter")
      .selectors(selectClass(AllParametersProbe.class))
      .execute()
      .testEvents()
      .assertStatistics(stats -> stats.succeeded(1));

    assertEquals(List.of("currency-EUR-amount-5"), AllParametersProbe.observedKeys);
  }

  @ExtendWith(SnapitoExtension.class)
  @org.junit.jupiter.api.Tag("probe")
  static class KeyedProbe {
    static final List<String> observedKeys = new ArrayList<>();

    @ParameterizedTest
    @CsvSource({"EUR, 5", "USD, 7"})
    @io.github.micfabian.snapito.SnapshotKey("currency")
    void booksAPayment(String currency, int amount) {
      SnapshotInvocationContext context = SnapitoContext.current();
      observedKeys.add(context.snapshotKey());
    }
  }

  @ExtendWith(SnapitoExtension.class)
  @org.junit.jupiter.api.Tag("probe")
  static class AllParametersProbe {
    static final List<String> observedKeys = new ArrayList<>();

    @ParameterizedTest
    @CsvSource({"EUR, 5"})
    @io.github.micfabian.snapito.SnapshotKey
    void booksAPayment(String currency, int amount) {
      SnapshotInvocationContext context = SnapitoContext.current();
      observedKeys.add(context.snapshotKey());
    }
  }
}
