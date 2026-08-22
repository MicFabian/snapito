package io.github.micfabian.snapito.mockito;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.mockito.MockingDetails;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.mockito.mock.MockCreationSettings;

public class Interactions {
  private final Set<String> ignoredMethods = new LinkedHashSet<>();
  private final Set<String> includedMethods = new LinkedHashSet<>();
  private final List<Pattern> redactions = new ArrayList<>();
  private String redactionReplacement = "<redacted>";
  private boolean ordered = true;
  private boolean includeSequence = true;
  private boolean includeReturnValues = true;
  private boolean includeUnverified = true;
  private boolean qualifiedMethodNames = false;

  public static Interactions defaults() {
    return new Interactions();
  }

  public static Interactions configured(Consumer<Interactions> configuration) {
    Interactions interactions = new Interactions();
    configuration.accept(interactions);
    return interactions;
  }

  public Interactions ignoringMethods(String... methods) {
    ignoredMethods.addAll(Arrays.asList(methods));
    return this;
  }

  public Interactions onlyMethods(String... methods) {
    includedMethods.addAll(Arrays.asList(methods));
    return this;
  }

  public Interactions replacing(String regex) {
    return replacing(regex, redactionReplacement);
  }

  public Interactions replacing(String regex, String replacement) {
    redactions.add(Pattern.compile(regex));
    redactionReplacement = replacement;
    return this;
  }

  public Interactions unordered() {
    ordered = false;
    includeSequence = false;
    return this;
  }

  public Interactions withoutSequence() {
    includeSequence = false;
    return this;
  }

  public Interactions withoutReturnValues() {
    includeReturnValues = false;
    return this;
  }

  public Interactions onlyVerified() {
    includeUnverified = false;
    return this;
  }

  public Interactions withQualifiedMethodNames() {
    qualifiedMethodNames = true;
    return this;
  }

  public List<Map<String, Object>> record(Object... mocks) {
    InvocationReturns.resetLookups();
    List<RecordedInvocation> recorded = new ArrayList<>();
    for (Object mock : mocks) {
      recorded.addAll(recordSingle(mock));
    }

    List<RecordedInvocation> ordered = new ArrayList<>(recorded);
    if (this.ordered) {
      ordered.sort(Comparator.comparingLong(RecordedInvocation::getGlobalSequence));
    } else {
      ordered.sort(Comparator.comparing(RecordedInvocation::getMock)
        .thenComparing(RecordedInvocation::getMethod)
        .thenComparing(invocation -> String.valueOf(invocation.getArguments())));
    }

    for (int index = 0; index < ordered.size(); index++) {
      ordered.get(index).setSequence(includeSequence ? index + 1 : null);
    }

    return ordered.stream().map(RecordedInvocation::asMap).collect(Collectors.toList());
  }

  private List<RecordedInvocation> recordSingle(Object mock) {
    MockingDetails details = Mockito.mockingDetails(mock);
    if (!details.isMock() && !details.isSpy()) {
      throw new IllegalArgumentException(
        "Interactions can only be recorded from Mockito mocks or spies, but " + describeType(mock) + " is neither");
    }

    String mockName = mockName(details);
    Map<Invocation, String> renderedInvocations = new java.util.IdentityHashMap<>();
    Map<String, Integer> realInvocationCounts = new java.util.HashMap<>();
    for (Invocation invocation : details.getInvocations()) {
      String rendered = invocation.toString();
      renderedInvocations.put(invocation, rendered);
      realInvocationCounts.merge(rendered, 1, Integer::sum);
    }

    Map<Method, String> methodNames = new java.util.IdentityHashMap<>();
    List<RecordedInvocation> recorded = new ArrayList<>();
    for (Invocation invocation : details.getInvocations()) {
      if (!includeUnverified && !invocation.isVerified()) {
        continue;
      }
      Method method = invocation.getMethod();
      if (!isIncluded(method.getName())) {
        continue;
      }

      RecordedInvocation entry = new RecordedInvocation();
      entry.setGlobalSequence(invocation.getSequenceNumber());
      entry.setMock(mockName);
      entry.setMethod(methodNames.computeIfAbsent(method, this::methodName));
      entry.setArguments(arguments(invocation));
      if (includeReturnValues) {
        int realCount = realInvocationCounts.getOrDefault(renderedInvocations.get(invocation), 1);
        InvocationReturns.lookup(invocation, realCount).ifPresent(returns -> {
          if (returns.thrown() != null) {
            entry.setThrown(returns.thrown());
          } else {
            entry.setReturnValue(redact(returns.value()));
          }
        });
      }
      recorded.add(entry);
    }
    return recorded;
  }

  private List<Object> arguments(Invocation invocation) {
    Object[] raw = invocation.getArguments();
    List<Object> arguments = new ArrayList<>(raw.length);
    for (Object argument : raw) {
      arguments.add(redact(argument));
    }
    return arguments;
  }

  private Object redact(Object value) {
    Object normalized = flatten(value);
    if (redactions.isEmpty() || !(normalized instanceof String text)) {
      return normalized;
    }
    String replaced = text;
    for (Pattern redaction : redactions) {
      replaced = redaction.matcher(replaced).replaceAll(redactionReplacement);
    }
    return replaced;
  }

  private Object flatten(Object value) {
    return flatten(value, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
  }

  private Object flatten(Object value, Set<Object> visited) {
    if (value == null) {
      return null;
    }
    if (value.getClass().isArray()) {
      if (!visited.add(value)) {
        return "<cycle>";
      }
      int size = Array.getLength(value);
      List<Object> items = new ArrayList<>(size);
      for (int index = 0; index < size; index++) {
        items.add(flatten(Array.get(value, index), visited));
      }
      return items;
    }
    if (value instanceof Collection<?> collection) {
      if (!visited.add(value)) {
        return "<cycle>";
      }
      return collection.stream().map(item -> flatten(item, visited)).collect(Collectors.toList());
    }
    if (isDefinitelyNotAMock(value)) {
      return value;
    }
    MockingDetails details = Mockito.mockingDetails(value);
    if (details.isMock() || details.isSpy()) {
      return "mock:" + mockName(details);
    }
    return value;
  }

  private static boolean isDefinitelyNotAMock(Object value) {
    return value instanceof CharSequence
      || value instanceof Number
      || value instanceof Boolean
      || value instanceof Character
      || value instanceof Enum<?>
      || value instanceof java.time.temporal.Temporal
      || value instanceof java.util.UUID;
  }

  private boolean isIncluded(String methodName) {
    if (ignoredMethods.contains(methodName)) {
      return false;
    }
    return includedMethods.isEmpty() || includedMethods.contains(methodName);
  }

  private String methodName(Method method) {
    String parameters = Arrays.stream(method.getParameterTypes())
      .map(Class::getSimpleName)
      .collect(Collectors.joining(", "));
    String name = qualifiedMethodNames
      ? method.getDeclaringClass().getName() + "." + method.getName()
      : method.getDeclaringClass().getSimpleName() + "." + method.getName();
    return name + "(" + parameters + ")";
  }

  private static String mockName(MockingDetails details) {
    MockCreationSettings<?> settings = details.getMockCreationSettings();
    if (settings.getMockName().isDefault()) {
      return settings.getTypeToMock().getSimpleName();
    }
    return settings.getMockName().toString();
  }

  private static String describeType(Object value) {
    return value == null ? "null" : value.getClass().getName();
  }
}
