package net.lshift.diffa.sql;

import org.jooq.DataType;
import org.jooq.impl.TableImpl;

/**
 * This appears to be the best way to dynamically define a table with fields that you can alias in JOOQ.
 */
public class DynamicTable extends TableImpl {

  public DynamicTable(String name) {
    super(name);
  }

  /**
   * Registers the column against the current table implementation such that table.getField(name) will actually
   * return a proper instance at runtime.
   */
  public <T> void addField(String name, DataType<T> type) {
    createField(name, type, this);
  }
}
