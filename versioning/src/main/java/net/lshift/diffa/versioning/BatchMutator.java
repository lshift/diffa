package net.lshift.diffa.versioning;

import me.prettyprint.hector.api.beans.DynamicComposite;
import me.prettyprint.hector.api.beans.HColumn;
import org.joda.time.DateTime;

public interface BatchMutator {

  void execute();
  void insertColumn(String rowKey, String columnFamily, String columnName, String columnValue);
  void insertDateColumn(String rowKey, String columnFamily, String columnName, DateTime columnValue);
  void invalidateColumn(String rowKey, String columnFamily, String columnName);
  void insertColumn(String rowKey, String columnFamily, HColumn<String,Boolean> column);
  void deleteColumn(String rowKey, String columnFamily, String columnName);
  void deleteRow(String rowKey, String columnFamily);

  void insertColumn(String rowKey, String columnFamily, String columnName, DynamicComposite composite);
}
