package net.lshift.diffa.kernel.util

import org.hibernate.dialect.{MySQL5Dialect, Oracle10gDialect, Dialect}

/**
 * Empty the target schema of all database objects (tables, views, triggers, etc.).
 * A thorough and simple implementation would involve dropping and recreating the
 * target schema.  This approach may also require restoring baseline privileges.
 */
trait SchemaCleaner {
  def clean(sysUserEnvironment: DatabaseEnvironment, appEnvironment: DatabaseEnvironment) {}
  def drop(sysUserEnvironment: DatabaseEnvironment, appEnvironment: DatabaseEnvironment) {}
}

object SchemaCleaner {
  def forDialect(dialect: Dialect): SchemaCleaner = {
    if (dialect.isInstanceOf[Oracle10gDialect]) OracleSchemaCleaner
    else if (dialect.isInstanceOf[MySQL5Dialect]) MySqlSchemaCleaner
    else new SchemaCleaner {}
  }
}
