package io.github.micfabian.snapito.mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.mockito.invocation.DescribedInvocation;
import org.mockito.invocation.Invocation;
import org.mockito.listeners.InvocationListener;
import org.mockito.listeners.MethodInvocationReport;

public final class InvocationReturns {
  private static final Map<String, List<Returns>> RECORDED = new ConcurrentHashMap<>();
  private static final Map<String, AtomicInteger> CONSUMED = new ConcurrentHashMap<>();

  private InvocationReturns() {
  }

  public static InvocationListener listener() {
    return new RecordingListener();
  }

  public static Optional<Returns> lookup(Invocation invocation, int expectedCount) {
    String key = key(invocation);
    List<Returns> recorded = RECORDED.get(key);
    if (recorded == null || recorded.isEmpty()) {
      return Optional.empty();
    }

    int position = CONSUMED.computeIfAbsent(key, ignored -> new AtomicInteger()).getAndIncrement();
    int offset = Math.max(0, recorded.size() - expectedCount);
    int index = Math.min(offset + position, recorded.size() - 1);
    return Optional.of(recorded.get(index));
  }

  public static void resetLookups() {
    CONSUMED.clear();
  }

  public static void clear() {
    RECORDED.clear();
    CONSUMED.clear();
  }

  public record Returns(Object value, String thrown) {
  }

  private static String key(Invocation invocation) {
    return System.identityHashCode(invocation.getMock()) + "|" + invocation;
  }

  private static String key(Object mock, DescribedInvocation invocation) {
    return System.identityHashCode(mock) + "|" + invocation;
  }

  private static final class RecordingListener implements InvocationListener {
    @Override
    public void reportInvocation(MethodInvocationReport report) {
      DescribedInvocation described = report.getInvocation();
      Object mock = described instanceof Invocation invocation ? invocation.getMock() : described;
      String key = key(mock, described);
      List<Returns> recorded = RECORDED.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>());
      if (report.threwException()) {
        Throwable thrown = report.getThrowable();
        recorded.add(new Returns(null, thrown.getClass().getName()
          + (thrown.getMessage() == null ? "" : ": " + thrown.getMessage())));
        return;
      }
      recorded.add(new Returns(report.getReturnedValue(), null));
    }
  }
}
