package net.lshift.diffa.conductor.plumbing;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.jolbox.bonecp.BoneCPDataSource;

import javax.sql.DataSource;

public class DataSourceModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(DataSource.class).to(BoneCPDataSource.class);
  }

  static class DataSourceProvider implements Provider<DataSource> {

    @Override
    public DataSource get() {
      return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
  }
}
