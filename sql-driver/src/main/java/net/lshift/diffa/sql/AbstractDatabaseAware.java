package net.lshift.diffa.sql;

import org.jooq.SQLDialect;
import org.jooq.impl.Factory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * This is a very bad name for a little piece of refactoring ...have to think of something better in due course.
 */
public class AbstractDatabaseAware {

  protected DataSource ds;
  protected SQLDialect dialect;

  public AbstractDatabaseAware(DataSource ds, SQLDialect dialect) {
    this.ds = ds;
    this.dialect = dialect;
  }

  protected Factory getFactory(Connection c) {
    return new Factory(c, dialect);
  }

  protected void closeConnection(Connection connection) {
    closeConnection(connection, false);
  }

  protected void closeConnection(Connection connection, boolean shouldCommit) {
    try {
      if (shouldCommit) {
        connection.commit();
      }
      connection.close();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  protected Connection getConnection() {
    Connection c;

    try {
      c = ds.getConnection();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
    return c;
  }
}
