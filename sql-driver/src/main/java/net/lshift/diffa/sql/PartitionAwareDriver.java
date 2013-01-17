package net.lshift.diffa.sql;

import com.google.inject.Inject;
import net.lshift.diffa.adapter.scanning.DateAggregation;
import net.lshift.diffa.adapter.scanning.ScanAggregation;
import net.lshift.diffa.adapter.scanning.ScanConstraint;
import net.lshift.diffa.scanning.PruningHandler;
import net.lshift.diffa.scanning.Scannable;
import org.jooq.*;
import org.jooq.impl.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Set;

public class PartitionAwareDriver extends AbstractDatabaseAware implements Scannable {

  static Logger log = LoggerFactory.getLogger(PartitionAwareDriver.class);

  private PartitionMetadata config;

  private static HashMap<SQLDialect, String> md5FunctionDefinitions = new HashMap<SQLDialect, String>();

  static {
    md5FunctionDefinitions.put(SQLDialect.HSQLDB,
        "create function md5(v varchar(32672)) returns varchar(32) language java deterministic no sql external name 'CLASSPATH:org.apache.commons.codec.digest.DigestUtils.md5Hex'");
    md5FunctionDefinitions.put(SQLDialect.ORACLE,
        "create or replace function md5(input_string varchar2) " +
            "return varchar2 " +
            "is " +
            "begin " +
            "        declare " +
            "                h_string varchar2(255); " +
            "        begin " +
            "                dbms_obfuscation_toolkit.md5(input_string => input_string, checksum_string => h_string); " +
            "                return lower(rawtohex(utl_raw.cast_to_raw(h_string))); " +
            "        end; " +
            "end; ");
  }

  @Inject
  public PartitionAwareDriver(DataSource ds, PartitionMetadata config, SQLDialect dialect) {
    super(ds, dialect);
    this.config = config;

    Connection connection = getConnection();
    Factory db = getFactory(connection);

    String functionDefinition = md5DefinitionForDialect(this.dialect);

    try {

      // TODO Hack - Just try to create the function, if it fails, we just assume that it already exists
      // It is probably a better idea to introspect the information schema to find out for sure that this function is available

      db.execute(functionDefinition);
      log.info("Created md5 function");
    }
    catch (Exception e) {

      if (e.getMessage().contains("already exists")) {
        // Crude way of ignoring
        log.info("Did not create md5 function, since it looks like it already exists in the target database");
      }
      else {
        log.info("Find out whether we need to care about this", e);
      }

    }

  }
  private String md5DefinitionForDialect(SQLDialect dialect) {
    return md5FunctionDefinitions.get(dialect);
  }

  @Override
  public void scan(Set<ScanConstraint> constraints, Set<ScanAggregation> aggregations, int maxSliceSize, PruningHandler handler) {

    Connection connection = getConnection();
    Factory db = getFactory(connection);

//    DataType<?> partitionType = underlyingPartition.getDataType();
//
//    if (partitionType.equals(SQLDataType.DATE) || partitionType.equals(SQLDataType.TIMESTAMP)) {
//      Field<Date> typedField = (Field<Date>) underlyingPartition;
//      underlyingPartition = A.getField(typedField);
//    }
//    else {
//      throw new RuntimeException("Currently we can not handle partition type: " + underlyingPartition.getDataType() + " ; please contact your nearest software developer");
//    }

    // default to date based aggregation for now. TODO default to prefix aggregation or no aggregation.
    AggregatingScanner scanner = new PrefixBasedAggregationScanner(db, config, maxSliceSize);
    if (aggregations != null) {
      if (aggregations.size() == 1) {
        ScanAggregation head = aggregations.iterator().next();
        if (head instanceof DateAggregation) {
          scanner = new DateBasedAggregationScanner(db, config, maxSliceSize);
        }
      }
    }

    scanner.scan(constraints, aggregations, handler);

    handler.onCompletion();

    closeConnection(connection);
  }
  // TODO This stuff shouldn't really get invoked inline, we should have some kind of wrapping function ....
}
