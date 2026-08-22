package io.github.micfabian.snapito.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

class AutoDetectionTest {
  @Test
  void registersItselfThroughServiceLoaderWhenAutodetectionIsEnabled() {
    AutoProbe.observedClass = null;

    EngineTestKit.engine("junit-jupiter")
      .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
      .selectors(selectClass(AutoProbe.class))
      .execute()
      .testEvents()
      .assertStatistics(stats -> stats.succeeded(1));

    assertEquals("AutoProbe", AutoProbe.observedClass);
  }

  @org.junit.jupiter.api.Tag("probe")
  static class AutoProbe {
    static String observedClass;

    @Test
    void seesTheContextWithoutAnExtendWithAnnotation() {
      assertNotNull(SnapitoContext.current());
      observedClass = SnapitoContext.current().getClassName();
    }
  }
}
