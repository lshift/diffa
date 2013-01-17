package net.lshift.diffa.versioning.plumbing;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.beans.HColumn;
import net.lshift.diffa.versioning.BasicBatchMutator;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LoggingBatchMutator extends BasicBatchMutator {

  static Logger log = LoggerFactory.getLogger(LoggingBatchMutator.class);

  private String description;
  private List<Mutation> mutations = new ArrayList<Mutation>();

  public LoggingBatchMutator(Keyspace keyspace, String description) {
    super(keyspace);
    this.description = description;
  }

  public void execute() {
    super.execute();
    logMutations();
  }

  private void logMutations() {
    StringBuilder sb = new StringBuilder();
    sb.append("\n\n");

    sb.append(description).append("\n\n");

    Function<Mutation, String> columnFamily = new Function<Mutation, String>() {
      public String apply(Mutation mutation) {
        return mutation.columnFamily;
      }
    };

    ImmutableListMultimap<String,Mutation> grouped = Multimaps.index(mutations, columnFamily);
    Iterator<String> it = grouped.keySet().iterator();

    while (it.hasNext()) {
      String cf = it.next();

      sb.append(cf).append("\n");
      sb.append(StringUtils.repeat("-", cf.length()));
      sb.append("\n");

      ImmutableList<Mutation> mutations = grouped.get(cf);
      for (Mutation mutation : mutations) {
        sb.append(mutation.rowKey).append(": ");

        if (mutation.isInvalidation) {
          sb.append(" invalidated [").append(mutation.columnName).append("]");
        }
        else if (mutation.isColumnDeletion) {
          sb.append(" deleted [").append(mutation.columnName).append("]");
        }
        else if (mutation.isRowDeletion) {
          sb.append(" deleted row");
        }
        else {
          sb.append(mutation.columnName).append(" [").append(mutation.columnValue).append("]");
        }


        sb.append("\n");
      }
      sb.append("\n");
    }

    log.info(sb.toString());
  }

  @Override
  public void insertColumn(String rowKey, String columnFamily, String columnName, String columnValue) {
    addMutation(rowKey, columnFamily, columnName, columnValue);
    super.insertColumn(rowKey, columnFamily, columnName, columnValue);
  }

  @Override
  public void insertDateColumn(String rowKey, String columnFamily, String columnName, DateTime columnValue) {
    // TODO use a serializer for Joda DateTime
    addMutation(rowKey, columnFamily, columnName, columnValue);
    super.insertDateColumn(rowKey, columnFamily, columnName, columnValue);
  }

  @Override
  public void invalidateColumn(String rowKey, String columnFamily, String columnName) {
    addInvalidation(rowKey, columnFamily, columnName);
    super.invalidateColumn(rowKey, columnFamily, columnName);
  }

  @Override
  public void insertColumn(String rowKey, String columnFamily, HColumn<String, Boolean> column) {
    addMutation(rowKey, columnFamily, column.getName(), column.getValue());
    super.insertColumn(rowKey, columnFamily, column);
  }

  @Override
  public void deleteColumn(String rowKey, String columnFamily, String columnName) {
    addColumnDeletion(rowKey, columnFamily, columnName);
    super.deleteColumn(rowKey, columnFamily, columnName);
  }

  public void deleteRow(String rowKey, String columnFamily) {
    addRowDeletion(rowKey, columnFamily);
    super.deleteRow(rowKey, columnFamily);
  }

  private void addInvalidation(String rowKey, String columnFamily, String columnName) {
    Mutation mutation = new Mutation(rowKey, columnFamily, columnName, true);
    mutations.add(mutation);
  }

  private void addMutation(String rowKey, String columnFamily, String columnName, Object columnValue) {
    Mutation mutation = new Mutation(rowKey, columnFamily, columnName, columnValue);
    mutations.add(mutation);
  }

  private void addColumnDeletion(String rowKey, String columnFamily, String columnName) {
    Mutation mutation = new Mutation(true, rowKey, columnFamily, columnName);
    mutations.add(mutation);
  }

  private void addRowDeletion(String rowKey, String columnFamily) {
    Mutation mutation = new Mutation(rowKey, columnFamily);
    mutations.add(mutation);
  }

  private class Mutation {

    String rowKey;
    String columnFamily;
    String columnName;
    Object columnValue;
    boolean isInvalidation = false;
    boolean isColumnDeletion = false;
    boolean isRowDeletion = false;

    private Mutation(String rowKey, String columnFamily, String columnName, Object columnValue) {
      this.rowKey = rowKey;
      this.columnFamily = columnFamily;
      this.columnName = columnName;
      this.columnValue = columnValue;
    }

    private Mutation(String rowKey, String columnFamily, String columnName, boolean isInvalidation) {
      this.rowKey = rowKey;
      this.columnFamily = columnFamily;
      this.columnName = columnName;
      this.isInvalidation = isInvalidation;
    }

    private Mutation(boolean isDeletion, String rowKey, String columnFamily, String columnName) {
      this.rowKey = rowKey;
      this.columnFamily = columnFamily;
      this.columnName = columnName;
      this.isColumnDeletion = isDeletion;
    }

    private Mutation(String rowKey, String columnFamily) {
      this.rowKey = rowKey;
      this.columnFamily = columnFamily;
      this.isRowDeletion = true;
    }

  }
}
