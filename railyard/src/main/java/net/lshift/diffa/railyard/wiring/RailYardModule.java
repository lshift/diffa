package net.lshift.diffa.railyard.wiring;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import net.lshift.diffa.scanning.Scannable;
import net.lshift.diffa.scanning.http.HttpDriver;
import net.lshift.diffa.system.SystemConfiguration;
import net.lshift.diffa.system.SystemConfigurationClient;
import net.lshift.diffa.versioning.CassandraVersionStore;
import net.lshift.diffa.versioning.VersionStore;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.map.MappingJsonFactory;

public class RailYardModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(JsonFactory.class).to(MappingJsonFactory.class);
    bind(VersionStore.class).to(CassandraVersionStore.class);
    bind(SystemConfiguration.class).to(SystemConfigurationClient.class);
    bind(String.class).annotatedWith(Names.named("scan.url")).toInstance("http://localhost:7654");
    bind(Scannable.class).to(HttpDriver.class);
  }
}
