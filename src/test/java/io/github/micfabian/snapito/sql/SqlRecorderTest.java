package io.github.micfabian.snapito.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class SqlRecorderTest {
  @Test
  void recordsPreparedStatementsWithNormalizedSqlAndOrderedParameters() throws SQLException {
    PreparedStatement prepared = mock(PreparedStatement.class);
    Connection connection = mock(Connection.class);
    DataSource dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(prepared);

    SqlRecorder recorder = new SqlRecorder();
    Connection wrapped = recorder.wrap(dataSource).getConnection();
    PreparedStatement statement = wrapped.prepareStatement("select *\n   from users\n where id = ? and name = ?");
    statement.setString(2, "ada");
    statement.setLong(1, 7L);
    statement.executeQuery();

    assertEquals(
      List.of(Map.of(
        "sequence", 1,
        "sql", "select * from users where id = ? and name = ?",
        "parameters", List.of(7L, "ada"))),
      recorder.statements());
  }

  @Test
  void recordsPlainStatementsAndBatches() throws SQLException {
    Statement plain = mock(Statement.class);
    PreparedStatement prepared = mock(PreparedStatement.class);
    Connection connection = mock(Connection.class);
    when(connection.createStatement()).thenReturn(plain);
    when(connection.prepareStatement(anyString())).thenReturn(prepared);

    SqlRecorder recorder = new SqlRecorder().withoutParameters();
    Connection wrapped = recorder.wrap(connection);
    wrapped.createStatement().executeUpdate("delete   from   users");
    PreparedStatement batch = wrapped.prepareStatement("insert into users(name) values (?)");
    batch.setString(1, "a");
    batch.addBatch();
    batch.setString(1, "b");
    batch.addBatch();
    batch.executeBatch();

    assertEquals(
      List.of(
        Map.of("sequence", 1, "sql", "delete from users"),
        Map.of("sequence", 2, "sql", "insert into users(name) values (?)", "batchSize", 2)),
      recorder.statements());
  }

  @Test
  void passesExceptionsThroughUnchanged() throws SQLException {
    PreparedStatement prepared = mock(PreparedStatement.class);
    Connection connection = mock(Connection.class);
    when(connection.prepareStatement(anyString())).thenReturn(prepared);
    when(prepared.executeUpdate()).thenThrow(new SQLException("boom"));

    SqlRecorder recorder = new SqlRecorder();
    PreparedStatement statement = recorder.wrap(connection).prepareStatement("update t set a = 1");

    assertThrows(SQLException.class, statement::executeUpdate);
  }
}
