package net.lshift.diffa.versioning;

import me.prettyprint.cassandra.serializers.DateSerializer;
import me.prettyprint.cassandra.serializers.DynamicCompositeSerializer;
import me.prettyprint.cassandra.serializers.StringSerializer;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.beans.DynamicComposite;
import me.prettyprint.hector.api.beans.HColumn;
import me.prettyprint.hector.api.factory.HFactory;
import me.prettyprint.hector.api.mutation.Mutator;
import org.joda.time.DateTime;

public class BasicBatchMutator implements BatchMutator {

  protected Mutator<String> mutator;

  public BasicBatchMutator(Keyspace keyspace) {
    mutator = HFactory.createMutator(keyspace, StringSerializer.get());
  }

  @Override
  public void execute() {
    mutator.execute();
  }

  @Override
  public void insertColumn(String rowKey, String columnFamily, String columnName, String columnValue) {
    mutator.addInsertion(rowKey, columnFamily,  HFactory.createStringColumn(columnName, columnValue));
  }

  @Override
  public void insertColumn(String rowKey, String columnFamily, String columnName, DynamicComposite composite) {
    HColumn<String, DynamicComposite> column = HFactory.createColumn(columnName, composite, StringSerializer.get(), DynamicCompositeSerializer.get());
    mutator.addInsertion(rowKey, columnFamily, column);
  }


  @Override
  public void insertDateColumn(String rowKey, String columnFamily, String columnName, DateTime columnValue) {
    // TODO Implement serializer for Joda
    mutator.addInsertion(rowKey, columnFamily,  HFactory.createColumn(columnName, columnValue.toDate(), StringSerializer.get(), DateSerializer.get()));
  }

  @Override
  public void invalidateColumn(String rowKey, String columnFamily, String columnName) {
    mutator.addInsertion(rowKey, columnFamily, HFactory.createStringColumn(columnName,""));
  }

  @Override
  public void insertColumn(String rowKey, String columnFamily, HColumn<String, Boolean> column) {
    mutator.addInsertion(rowKey, columnFamily, column);
  }

  @Override
  public void deleteColumn(String rowKey, String columnFamily, String columnName) {
    mutator.addDeletion(rowKey, columnFamily, columnName, StringSerializer.get());
  }

  @Override
  public void deleteRow(String rowKey, String columnFamily) {
    mutator.addDeletion(rowKey, columnFamily);
  }
}
