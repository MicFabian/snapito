package io.github.micfabian.snapito.junit;

import io.github.micfabian.snapito.SnapshotInvocationContext;

public final class SnapitoContext {
  private static final ThreadLocal<SnapshotInvocationContext> CURRENT = new ThreadLocal<>();

  private SnapitoContext() {
  }

  public static SnapshotInvocationContext current() {
    return CURRENT.get();
  }

  public static void set(SnapshotInvocationContext context) {
    CURRENT.set(context);
  }

  public static void clear() {
    CURRENT.remove();
  }

  public static String featureName() {
    SnapshotInvocationContext context = CURRENT.get();
    return context == null ? null : context.getFeatureName();
  }

  public static String packageName() {
    SnapshotInvocationContext context = CURRENT.get();
    return context == null ? null : context.getPackageName();
  }

  public static String className() {
    SnapshotInvocationContext context = CURRENT.get();
    return context == null ? null : context.getClassName();
  }
}
