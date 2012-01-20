package net.lshift.hibernate.migrations;

import net.lshift.hibernate.migrations.dialects.DialectExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract class to provide table partition DDL statements.
 */
public class PartitionAwareTableHelper {

  private final DialectExtension dialectExtension;
  private int partitionCount = 1;
  private String[] hashPartitionColumns;
  private String listPartitionColumn;
  private Map<String, String[]> listPartitionDefinitions;

  public PartitionAwareTableHelper(DialectExtension dialectExtension) {
    this.dialectExtension = dialectExtension;
  }

  public void defineHashPartitions(int partitions, String... columns) {
    if (shouldPartition()) throw new IllegalStateException("Partitioning has already been enabled for this table");

    if (dialectExtension.supportsHashPartitioning()) {
      partitionCount = partitions;
      hashPartitionColumns = columns;
    }
  }

  public void useListPartitioning(String column) {
    if (shouldPartition()) throw new IllegalStateException("Partitioning has already been enabled for this table");

    if (dialectExtension.supportsListPartitioning()) {
      listPartitionColumn = column;
      listPartitionDefinitions = new HashMap<String, String[]>();
    }
  }

  public void addListPartition(String name, String...values) {
    if (listPartitionColumn == null)
      throw new IllegalStateException("useListPartitioning must be called before adding a list partition definition");

    listPartitionDefinitions.put(name, values);
  }

  /**
   * Indicates whether partitioning is supported by the underlying database and that partitioning
   * information has been supplied.
   */
  private boolean shouldPartition() {
    return canPartition() && (
      (hashPartitionColumns != null && hashPartitionColumns.length > 0) ||
      (listPartitionColumn != null)
    );
  }

  /**
   * Indicates whether partitioning is supported by the underlying database
   */
  public boolean canPartition() {
    return dialectExtension.supportsHashPartitioning();
  }
  
  public void appendPartitionString(StringBuffer buffer) {
    if (shouldPartition()) {
      buffer.append(" ");
      if (hashPartitionColumns != null)
        buffer.append(dialectExtension.defineHashPartitionString(partitionCount, hashPartitionColumns));
      if (listPartitionColumn != null)
        buffer.append(dialectExtension.defineListPartitionString(listPartitionColumn, listPartitionDefinitions));
    }
  }
}
