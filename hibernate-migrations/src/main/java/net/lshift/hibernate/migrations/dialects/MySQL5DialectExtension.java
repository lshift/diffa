package net.lshift.hibernate.migrations.dialects;

import net.lshift.hibernate.migrations.CopyTableBuilder;
import net.lshift.hibernate.migrations.CreateTableBuilder;
import net.lshift.hibernate.migrations.DropTableBuilder;
import net.lshift.hibernate.migrations.MigrationBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.dialect.Dialect;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Additional dialect-specific syntax for MySQL (5.x) not covered by MySQL5Dialect.
 */
public class MySQL5DialectExtension extends DialectExtension {
  @Override
  public String getDialectName() {
    return "MYSQL";
  }

  @Override
  public String alterColumnString() {
    return "modify";
  }

  @Override
  public boolean supportsPrimaryKeyReplace() {
    return true;
  }
  
  @Override
  public String setColumnNullString() {
    return " ";
  }

  @Override
  public String getTypeStringForSetColumnNullability(Dialect dialect, int sqlType, int length) {
    // The following currently only supports varchar type.  Add parameters to
    // match Dialect.getTypeName for additional type support.
    return " " + dialect.getTypeName(sqlType, length, 0, 0);
  }
  
  @Override
  public boolean indexDropsWithForeignKey() {
    return false;
  }
  
  @Override
  public boolean supportsFractionalSeconds() {
    return false;
  }

  @Override
  public boolean supportsColumnTypeChanges() {
    return false;
  }

  /**
   * MySQL does not support data type changes when altering a column :-(
   */
  public void widenIntColumn(MigrationBuilder builder, String table, String column) throws SQLException {
    throw new RuntimeException("Column widening not supported in MySQL");
  }

}
