package net.lshift.hibernate.migrations.dialects;

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
}
