package net.lshift.diffa.conductor.plumbing;

import com.jolbox.bonecp.BoneCPConfig;
import com.jolbox.bonecp.BoneCPDataSource;
import net.lshift.diffa.conductor.DriverConfiguration;
import net.lshift.diffa.sql.PartitionMetadata;
import org.jooq.impl.SQLDataType;

import javax.sql.DataSource;

public class ConfigurationBuilder {

  public static DataSource buildDataSource(DriverConfiguration conf) {
    return buildDataSource(conf.getUrl(), conf.getDriverClass(), conf.getUsername(), conf.getPassword());
  }

  public static DataSource buildDataSource(String url, String driver, String user, String password) {
    BoneCPConfig config = new BoneCPConfig();

    config.setJdbcUrl(url);
    config.setUsername(user);
    config.setPassword(password);
    BoneCPDataSource ds = new BoneCPDataSource(config);
    ds.setDriverClass(driver);

    return ds;
  }

  public static PartitionMetadata buildMetaData(DriverConfiguration conf) {
    return new PartitionMetadata(conf.getTableName()).
      withId(conf.getIdFieldName(), SQLDataType.VARCHAR). // TODO Make less hard coded
      withVersion(conf.getVersionFieldName(), SQLDataType.VARCHAR).
      partitionBy(conf.getPartitionFieldName(), SQLDataType.DATE);
  }
}
