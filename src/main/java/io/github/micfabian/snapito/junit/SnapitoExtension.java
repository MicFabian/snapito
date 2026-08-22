package io.github.micfabian.snapito.junit;

import io.github.micfabian.snapito.MissingSnapshots;
import io.github.micfabian.snapito.Snapito;
import io.github.micfabian.snapito.SnapshotIndex;
import io.github.micfabian.snapito.SnapshotInvocationContext;
import io.github.micfabian.snapito.SnapshotKey;
import io.github.micfabian.snapito.mockito.InvocationReturns;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapitoExtension implements InvocationInterceptor, AfterEachCallback, AfterAllCallback {
  private static final Logger LOG = LoggerFactory.getLogger(SnapitoExtension.class);
  private static final Map<String, AtomicInteger> ITERATIONS = new ConcurrentHashMap<>();
  private static final Map<String, Set<String>> EXECUTED_METHODS = new ConcurrentHashMap<>();

  @Override
  public void interceptTestMethod(
      Invocation<Void> invocation,
      ReflectiveInvocationContext<Method> invocationContext,
      ExtensionContext extensionContext) throws Throwable {
    intercept(invocation, invocationContext, extensionContext, false);
  }

  @Override
  public void interceptTestTemplateMethod(
      Invocation<Void> invocation,
      ReflectiveInvocationContext<Method> invocationContext,
      ExtensionContext extensionContext) throws Throwable {
    intercept(invocation, invocationContext, extensionContext, true);
  }

  @Override
  public void afterEach(ExtensionContext context) {
    SnapitoContext.clear();
    Snapito.clearThreadState();
    InvocationReturns.clear();
  }

  @Override
  public void afterAll(ExtensionContext context) {
    Class<?> testClass = context.getRequiredTestClass();
    Path packageDir = Snapito.packageDir(packageName(testClass), testClass.getSimpleName());
    ITERATIONS.keySet().removeIf(key -> key.startsWith(testClass.getName() + "#"));

    Set<String> executed = EXECUTED_METHODS.remove(testClass.getName());
    SnapshotCleanup.cleanup(packageDir, Snapito.shouldCleanObsoleteSnapshots() && ranEveryTest(testClass, executed));

    SnapshotIndex.flush();
    String report = MissingSnapshots.reportUnreported();
    if (!report.isEmpty()) {
      LOG.warn("{}", report);
    }
  }

  private void intercept(
      Invocation<Void> invocation,
      ReflectiveInvocationContext<Method> invocationContext,
      ExtensionContext extensionContext,
      boolean template) throws Throwable {
    Method method = invocationContext.getExecutable();
    Class<?> testClass = extensionContext.getRequiredTestClass();
    List<Object> arguments = invocationContext.getArguments();
    boolean parameterized = template && !arguments.isEmpty();
    int iterationIndex = parameterized ? nextIteration(testClass, method) : 0;

    SnapshotInvocationContext context = new SnapshotInvocationContext(
      method.getName(),
      packageName(testClass),
      testClass.getSimpleName(),
      iterationIndex,
      parameterized,
      dataVariables(method, arguments),
      snapshotKeyVariables(method, testClass));

    EXECUTED_METHODS
      .computeIfAbsent(testClass.getName(), ignored -> ConcurrentHashMap.newKeySet())
      .add(method.getName());

    SnapitoContext.set(context);
    InvocationReturns.clear();
    try {
      invocation.proceed();
    } finally {
      SnapitoContext.clear();
      Snapito.clearThreadState();
    }
  }

  private static boolean ranEveryTest(Class<?> testClass, Set<String> executed) {
    Set<String> declared = declaredTestMethods(testClass);
    if (declared.isEmpty()) {
      return true;
    }
    Set<String> actuallyRun = executed == null ? Set.of() : executed;
    if (actuallyRun.containsAll(declared)) {
      return true;
    }
    Set<String> skipped = new java.util.TreeSet<>(declared);
    skipped.removeAll(actuallyRun);
    LOG.info(
      "Skipping obsolete-snapshot cleanup for {} because {} of its {} tests did not run ({}). "
        + "Cleaning now would delete baselines that belong to the tests that were filtered out.",
      testClass.getSimpleName(), skipped.size(), declared.size(), String.join(", ", skipped));
    return false;
  }

  private static Set<String> declaredTestMethods(Class<?> testClass) {
    Set<String> declared = new java.util.LinkedHashSet<>();
    for (Class<?> current = testClass; current != null && current != Object.class; current = current.getSuperclass()) {
      for (Method method : current.getDeclaredMethods()) {
        if (isTestMethod(method)) {
          declared.add(method.getName());
        }
      }
    }
    return declared;
  }

  private static boolean isTestMethod(Method method) {
    for (java.lang.annotation.Annotation annotation : method.getAnnotations()) {
      String name = annotation.annotationType().getName();
      if (name.equals("org.junit.jupiter.api.Test")
        || name.equals("org.junit.jupiter.api.RepeatedTest")
        || name.equals("org.junit.jupiter.api.TestFactory")
        || name.equals("org.junit.jupiter.api.TestTemplate")
        || name.startsWith("org.junit.jupiter.params.ParameterizedTest")) {
        return true;
      }
    }
    return false;
  }

  private static int nextIteration(Class<?> testClass, Method method) {
    String key = testClass.getName() + "#" + method.getName();
    return ITERATIONS.computeIfAbsent(key, ignored -> new AtomicInteger()).getAndIncrement();
  }

  private static Map<String, Object> dataVariables(Method method, List<Object> arguments) {
    Map<String, Object> variables = new LinkedHashMap<>();
    Parameter[] parameters = method.getParameters();
    for (int index = 0; index < parameters.length && index < arguments.size(); index++) {
      variables.put(parameters[index].getName(), arguments.get(index));
    }
    return variables;
  }

  private static List<String> snapshotKeyVariables(Method method, Class<?> testClass) {
    SnapshotKey annotation = method.getAnnotation(SnapshotKey.class);
    if (annotation == null) {
      annotation = testClass.getAnnotation(SnapshotKey.class);
    }
    if (annotation == null) {
      return List.of();
    }
    if (annotation.value().length > 0) {
      return List.of(annotation.value());
    }
    List<String> names = new ArrayList<>();
    for (Parameter parameter : method.getParameters()) {
      names.add(parameter.getName());
    }
    return names;
  }

  private static String packageName(Class<?> testClass) {
    return testClass.getPackage() == null ? "" : testClass.getPackage().getName();
  }
}
