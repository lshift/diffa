package net.lshift.diffa.conductor.integration;

import net.lshift.diffa.sql.PartitionAwareDriver;
import net.lshift.diffa.sql.PartitionMetadata;
import org.apache.commons.lang3.RandomStringUtils;
import org.jooq.SQLDialect;
import org.jooq.impl.Factory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

public class PartitionedStore extends PartitionAwareDriver {

  public PartitionedStore(DataSource ds, PartitionMetadata config, SQLDialect dialect) {
    super(ds, config, dialect);
  }

  public PartitionableThing createRandomThing(Map<String, ?> attributes) {

    String id = RandomStringUtils.randomAlphabetic(10);
    String version = RandomStringUtils.randomAlphabetic(10);

    PartitionableThing record = new PartitionableThing(attributes);

    record.setId(id);
    record.setVersion(version);

    Connection connection = getConnection();

    Factory db = getFactory(connection);
    db.execute("insert into things (id, version,entry_date) values (?,?,?)", id, version, record.getEntryDate());

    closeConnection(connection, true);

    return record;
  }
}
