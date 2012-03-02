package net.lshift.hibernate.migrations.dialects;

import net.lshift.hibernate.migrations.HibernateHelper;
import net.lshift.hibernate.migrations.MigrationBuilder;
import org.junit.Test;

import java.sql.Types;

import static net.lshift.hibernate.migrations.VerificationUtil.verifyMigrationBuilder;

/**
 * Validates support for the MySQL dialect.
 */
public class MySQLDialectSupportTest {
  @Test
  public void shouldSetColumnNullable() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration(HibernateHelper.MYSQL_DIALECT));
    mb.alterTable("foo").setColumnNullable("bar", Types.VARCHAR, 255, true);
    verifyMigrationBuilder(mb, "alter table foo modify bar varchar(255) null");
  }

  @Test
  public void shouldSetColumnNotNull() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration(HibernateHelper.MYSQL_DIALECT));
    mb.alterTable("foo").setColumnNullable("bar", Types.VARCHAR, 255, false);
    verifyMigrationBuilder(mb, "alter table foo modify bar varchar(255) not null");
  }

  @Test
  public void shouldDropIndexWithForeignKey() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration(HibernateHelper.MYSQL_DIALECT));
    mb.alterTable("foo").dropForeignKey("bar");
    verifyMigrationBuilder(mb, "alter table foo  drop foreign key bar, drop index bar");
  }
}
