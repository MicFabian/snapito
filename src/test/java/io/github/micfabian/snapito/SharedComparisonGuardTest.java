package io.github.micfabian.snapito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SharedComparisonGuardTest {
  @Test
  void refusesToReconfigureSharedJsonConstants() {
    IllegalStateException error =
      assertThrows(IllegalStateException.class, () -> Comparisons.JSON.excludingProperties("id"));

    assertTrue(error.getMessage().contains("shared constant"));
  }

  @Test
  void refusesToReconfigureEverySharedJsonMutator() {
    assertThrows(IllegalStateException.class, () -> Comparisons.JSON.excludingPaths("$.id"));
    assertThrows(IllegalStateException.class, () -> Comparisons.JSON.excludingTypes(String.class));
    assertThrows(IllegalStateException.class, () -> Comparisons.JSON.unordered("$.roles"));
    assertThrows(IllegalStateException.class, () -> Comparisons.JSON.sortedBy("$.items", "sku"));
    assertThrows(IllegalStateException.class, () -> Comparisons.JSON.replacing("x"));
    assertThrows(IllegalStateException.class, () -> Comparisons.JSON.within(0.1));
    assertThrows(IllegalStateException.class, () -> Comparisons.API_RESPONSE.excludingProperties("x"));
    assertThrows(IllegalStateException.class, () -> Comparisons.INTERACTIONS.excludingProperties("x"));
    assertThrows(IllegalStateException.class, () -> Comparisons.HTML.ignoringAttributes("id"));
  }

  @Test
  void leavesSharedConstantsUntouchedAfterARefusedMutation() {
    assertThrows(IllegalStateException.class, () -> Comparisons.JSON.excludingProperties("id"));

    assertEquals(Map.of("id", java.math.BigDecimal.valueOf(1), "keep", java.math.BigDecimal.valueOf(2)),
      Comparisons.JSON.beforeComparison(Map.of("id", 1, "keep", 2)));
  }

  @Test
  void derivedCopiesRemainFreelyConfigurable() {
    var derived = Comparisons.JSON.with(json -> json.excludingProperties("id"));

    assertEquals(Map.of("keep", java.math.BigDecimal.valueOf(2)), derived.beforeComparison(Map.of("id", 1, "keep", 2)));
    assertEquals(Map.of("id", java.math.BigDecimal.valueOf(1), "keep", java.math.BigDecimal.valueOf(2)),
      Comparisons.JSON.beforeComparison(Map.of("id", 1, "keep", 2)));
  }

  @Test
  void freshlyBuiltComparisonsAreConfigurable() {
    var built = Comparisons.json(json -> json.excludingProperties("id"));

    assertEquals(Map.of("keep", java.math.BigDecimal.valueOf(2)), built.beforeComparison(Map.of("id", 1, "keep", 2)));
  }

  @Test
  void interactionCopiesKeepTheirFileExtension() {
    assertEquals("interactions.json", Comparisons.INTERACTIONS.copy().fileExtension());
    assertEquals("interactions.json",
      Comparisons.INTERACTIONS.with(json -> json.excludingProperties("x")).fileExtension());
  }
}
