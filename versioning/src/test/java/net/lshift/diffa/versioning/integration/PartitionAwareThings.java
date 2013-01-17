package net.lshift.diffa.versioning.integration;

import com.jolbox.bonecp.BoneCPDataSource;
import net.lshift.diffa.sql.PartitionAwareDriver;
import net.lshift.diffa.sql.PartitionMetadata;
import org.apache.commons.lang.RandomStringUtils;
import org.jooq.SQLDialect;
import org.jooq.impl.*;
import java.sql.Connection;
import java.util.*;

public class PartitionAwareThings extends PartitionAwareDriver {

  public PartitionAwareThings(BoneCPDataSource ds, PartitionMetadata config, SQLDialect dialect) {
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
