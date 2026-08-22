package io.github.micfabian.snapito.mockito;

import org.mockito.Mockito;
import org.mockito.MockSettings;

public final class RecordingMocks {
  private RecordingMocks() {
  }

  public static MockSettings settings() {
    return Mockito.withSettings().invocationListeners(InvocationReturns.listener());
  }

  public static <T> T mock(Class<T> type) {
    return Mockito.mock(type, settings());
  }

  public static <T> T mock(Class<T> type, String name) {
    return Mockito.mock(type, settings().name(name));
  }

  public static <T> T spy(T instance) {
    return Mockito.mock(instanceType(instance), settings().spiedInstance(instance)
      .defaultAnswer(Mockito.CALLS_REAL_METHODS));
  }

  @SuppressWarnings("unchecked")
  private static <T> Class<T> instanceType(T instance) {
    return (Class<T>) instance.getClass();
  }
}
