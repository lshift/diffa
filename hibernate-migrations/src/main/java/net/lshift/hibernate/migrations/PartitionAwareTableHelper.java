package net.lshift.hibernate.migrations;

import net.lshift.hibernate.migrations.dialects.DialectExtension;

import java.util.List;

/**
 * Abstract class to provide table partition DDL statements.
 */
public class PartitionAwareTableHelper {

  private final DialectExtension dialectExtension;
  private int partitionCount = 1;
  private String[] partitionColumns;

  public PartitionAwareTableHelper(DialectExtension dialectExtension) {
    this.dialectExtension = dialectExtension;
  }

  public void definePartitions(int partitions, String ... columns) {
    if (dialectExtension.supportsHashPartitioning()) {
      partitionCount = partitions;
      partitionColumns = columns;
    }
  }

  /**
   * Indicates whether partitioning is supported by the underlying database and that partitioning
   * information has been supplied.
   */
  private boolean shouldPartition() {
    return canPartition() && partitionColumns != null && partitionColumns.length > 0;
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
      buffer.append(dialectExtension.defineHashPartitionString(partitionCount, partitionColumns));
    }
  }

}
