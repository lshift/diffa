package net.lshift.diffa.schema;

import net.lshift.diffa.schema.migrations.HibernateConfigStorePreparationStep;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

/**
 * Helper class used by JOOQ binding generation.
 *
 * The main method will be Invoked from the schema-bindings module. JOOQ generates its bindings by introspection
 * of a database schema, and this class is responsible for building that schema.
 */
public class Migrate {

  public static void main(String[] args) {
    Configuration config = new Configuration()
      .setProperty("hibernate.dialect", System.getProperty("diffa.hibernate.dialect"))
      .setProperty("hibernate.connection.url", System.getProperty("diffa.jdbc.url"))
      .setProperty("hibernate.connection.driver_class", System.getProperty("diffa.jdbc.driver"))
      .setProperty("hibernate.connection.username", System.getProperty("diffa.jdbc.username"))
      .setProperty("hibernate.connection.password", System.getProperty("diffa.jdbc.password"));
    SessionFactory sessionFactory = config.buildSessionFactory();
    try {
      (new HibernateConfigStorePreparationStep()).prepare(sessionFactory, config);
    } finally {
      sessionFactory.close();
    }
  }
}
