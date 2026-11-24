package io.github.micfabian.snapito.sql;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import javax.sql.DataSource;

public final class SqlRecorder {
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Set<String> EXECUTE_METHODS = Set.of(
    "execute", "executeQuery", "executeUpdate", "executeLargeUpdate", "executeBatch", "executeLargeBatch");

  private final List<Map<String, Object>> statements = new CopyOnWriteArrayList<>();
  private final boolean includeParameters;

  public SqlRecorder() {
    this(true);
  }

  private SqlRecorder(boolean includeParameters) {
    this.includeParameters = includeParameters;
  }

  public SqlRecorder withoutParameters() {
    return new SqlRecorder(false);
  }

  public DataSource wrap(DataSource target) {
    return proxy(DataSource.class, target, (method, arguments, result) -> {
      if ("getConnection".equals(method.getName()) && result instanceof Connection connection) {
        return wrapConnection(connection);
      }
      return result;
    });
  }

  public Connection wrap(Connection target) {
    return wrapConnection(target);
  }

  public List<Map<String, Object>> statements() {
    List<Map<String, Object>> snapshot = new ArrayList<>();
    int sequence = 1;
    for (Map<String, Object> statement : statements) {
      Map<String, Object> numbered = new LinkedHashMap<>();
      numbered.put("sequence", sequence++);
      numbered.putAll(statement);
      snapshot.add(numbered);
    }
    return snapshot;
  }

  public int count() {
    return statements.size();
  }

  public void clear() {
    statements.clear();
  }

  private Connection wrapConnection(Connection connection) {
    return proxy(Connection.class, connection, (method, arguments, result) -> {
      String name = method.getName();
      if ("prepareStatement".equals(name) && result instanceof PreparedStatement prepared) {
        return recordingProxy(PreparedStatement.class, prepared, new StatementRecording(normalize((String) arguments[0])));
      }
      if ("prepareCall".equals(name) && result instanceof CallableStatement callable) {
        return recordingProxy(CallableStatement.class, callable, new StatementRecording(normalize((String) arguments[0])));
      }
      if ("createStatement".equals(name) && result instanceof Statement statement) {
        return recordingProxy(Statement.class, statement, new StatementRecording(null));
      }
      return result;
    });
  }

  private static String normalize(String sql) {
    return WHITESPACE.matcher(sql.trim()).replaceAll(" ");
  }

  private interface ResultMapper {
    Object map(Method method, Object[] arguments, Object result);
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, T target, ResultMapper mapper) {
    InvocationHandler handler = (proxy, method, arguments) -> {
      Object[] actual = arguments == null ? new Object[0] : arguments;
      Object result;
      try {
        result = method.invoke(target, actual);
      } catch (InvocationTargetException e) {
        throw e.getCause();
      }
      return mapper.map(method, actual, result);
    };
    return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
  }

  private <T> T recordingProxy(Class<T> type, T target, StatementRecording recording) {
    @SuppressWarnings("unchecked")
    T proxied = (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, arguments) -> {
      Object[] actual = arguments == null ? new Object[0] : arguments;
      String name = method.getName();
      if (name.startsWith("set") && actual.length >= 2 && actual[0] instanceof Integer index) {
        recording.parameters.put(index, "setNull".equals(name) ? null : actual[1]);
      } else if ("clearParameters".equals(name)) {
        recording.parameters.clear();
      } else if ("addBatch".equals(name)) {
        recording.batchSize++;
        if (actual.length == 1 && actual[0] instanceof String batchSql) {
          recording.batchStatements.add(normalize(batchSql));
        }
      }
      Object result;
      try {
        result = method.invoke(target, actual);
      } catch (InvocationTargetException e) {
        throw e.getCause();
      }
      if (EXECUTE_METHODS.contains(name)) {
        record(recording, name, actual);
      }
      return result;
    });
    return proxied;
  }

  private void record(StatementRecording recording, String method, Object[] arguments) {
    Map<String, Object> entry = new LinkedHashMap<>();
    if (recording.sql != null) {
      entry.put("sql", recording.sql);
    } else if (arguments.length > 0 && arguments[0] instanceof String direct) {
      entry.put("sql", normalize(direct));
    } else if (!recording.batchStatements.isEmpty()) {
      entry.put("sql", List.copyOf(recording.batchStatements));
    }
    if (includeParameters && !recording.parameters.isEmpty()) {
      List<Object> values = new ArrayList<>();
      new TreeMap<>(recording.parameters).values().forEach(value -> values.add(render(value)));
      entry.put("parameters", values);
    }
    if (method.endsWith("Batch")) {
      entry.put("batchSize", recording.batchSize);
      recording.batchSize = 0;
      recording.batchStatements.clear();
    }
    statements.add(entry);
  }

  private static Object render(Object value) {
    if (value == null || value instanceof Number || value instanceof Boolean || value instanceof String) {
      return value;
    }
    return String.valueOf(value);
  }

  private static final class StatementRecording {
    private final String sql;
    private final Map<Integer, Object> parameters = new TreeMap<>();
    private final List<String> batchStatements = new ArrayList<>();
    private int batchSize;

    private StatementRecording(String sql) {
      this.sql = sql;
    }
  }
}
