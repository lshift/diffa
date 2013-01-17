package net.lshift.diffa.sql;

import com.google.common.collect.ImmutableSet;
import net.lshift.diffa.adapter.scanning.*;
import net.lshift.diffa.dbapp.TestDBProvider;
import net.lshift.diffa.interview.Answer;
import net.lshift.diffa.interview.SimpleGroupedAnswer;
import net.lshift.diffa.scanning.plumbing.BufferedPruningHandler;
import org.apache.commons.codec.digest.DigestUtils;
import org.joda.time.DateTime;
import org.jooq.impl.Factory;
import org.jooq.impl.SQLDataType;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.Date;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

public class SQLDriverIT extends AbstractDatabaseAware {
  public SQLDriverIT() {
    super(TestDBProvider.createSchema().getDataSource(), TestDBProvider.getDialect());
  }

  private final PartitionMetadata metadata = new PartitionMetadata("THINGS");
  {
    metadata.withId("ID", SQLDataType.VARCHAR).
        withVersion("VERSION", SQLDataType.VARCHAR).
        partitionBy("ENTRY_DATE", SQLDataType.DATE);
  }

  @Before
  public void seed() throws Exception {
    DateTime start = new DateTime(2005,7,29,0,0,0,0);
    int days = 1000;

    Connection connection = getConnection();

    Factory db = getFactory(connection);

    for (int i = 0; i < days; i++) {
      String id = i + "";
      String version = DigestUtils.md5Hex(id);
      DateTime entryDate = start.plusDays(i);
      Date date = new Date(entryDate.getMillis());
      db.execute("insert into things (id, version,entry_date) values (?,?,?)", id, version, date);
    }

    connection.commit();
    closeConnection(connection, true);
  }

  @Test
  public void shouldFilterByExtentWithDateBasedAggregation() throws Exception {
    // Given the standard test data with an 'extent' column named 'id'...
    String selectedExtent = "1";
    ScanConstraint extentConstraint = new SetConstraint("ID", Collections.singleton(selectedExtent));
    ScanAggregation dateAggregation = new DateAggregation("some_date", DateGranularityEnum.Yearly);

    PartitionAwareDriver driver = new PartitionAwareDriver(ds, metadata, TestDBProvider.getDialect());
    BufferedPruningHandler handler = new BufferedPruningHandler();

    // When
    driver.scan(Collections.singleton(extentConstraint), Collections.singleton(dateAggregation), 1, handler);

    // Then
    // There's only one record with an ID = '1', so we just have one entry in the 'extent'.
    String expectedAggregateVersion = md5(md5(md5(md5(md5(selectedExtent)))));
    Set<Answer> expectedResults = Collections.singleton(
        (Answer) new SimpleGroupedAnswer("2005", expectedAggregateVersion));

    assertEquals(expectedResults, handler.getAnswers());
  }

  @Test
  public void shouldFilterByExtentWithPrefixBasedAggregation() throws Exception {
    // Given the standard test data with an 'extent' column named 'id'...
    String selectedExtent = "1";
    String idColumn = "ID";
    TreeSet<Integer> prefixLengths = new TreeSet<Integer>();
    Collections.addAll(prefixLengths, 1,2,3);
    ScanConstraint extentConstraint = new SetConstraint(idColumn, Collections.singleton(selectedExtent));
    ScanAggregation prefixAggregation = new StringPrefixAggregation(idColumn, null, prefixLengths);
    metadata.partitionBy(idColumn, SQLDataType.VARCHAR);

    PartitionAwareDriver driver = new PartitionAwareDriver(ds, metadata, TestDBProvider.getDialect());
    BufferedPruningHandler handler = new BufferedPruningHandler();

    // When
    driver.scan(Collections.singleton(extentConstraint), Collections.singleton(prefixAggregation), 1, handler);

    // Then
    // There's only one record with an ID = '1', so we just have one entry in the 'extent'.
    String expectedAggregateVersion = md5(md5(md5(md5(md5(selectedExtent)))));
    Set<Answer> expectedResults = Collections.singleton(
        (Answer) new SimpleGroupedAnswer(selectedExtent, expectedAggregateVersion));

    assertEquals(expectedResults, handler.getAnswers());
  }

  @Test
  public void shouldPartitionByDate() throws Exception {
    PartitionAwareDriver driver = new PartitionAwareDriver(ds, metadata, TestDBProvider.getDialect());

    ScanAggregation dateAggregation = new DateAggregation("some_date", DateGranularityEnum.Yearly, null);

    Set<ScanConstraint> cons = null;
    Set<ScanAggregation> aggs = ImmutableSet.of(dateAggregation);

    BufferedPruningHandler handler = new BufferedPruningHandler();
    driver.scan(cons, aggs, 100, handler);

    /**
     * Please note that I got this working and then reverse-engineered the results from that code,
     * rather than forwards engineering the expected from some kind of logic ......
     */

    Set<Answer> expectedResults = new LinkedHashSet<Answer>();
    expectedResults.add(new SimpleGroupedAnswer("2005", "696a51b5982b8521625d39631c1175bb"));
    expectedResults.add(new SimpleGroupedAnswer("2006", "6b4ee3a8dcf9e71af301b5722406e52f"));
    expectedResults.add(new SimpleGroupedAnswer("2007", "c7b7eb798fcf835ace16f469c6919e1c"));
    expectedResults.add(new SimpleGroupedAnswer("2008", "0906f8b73c3e2ff365ff235b3cb020b7"));

    assertEquals(expectedResults, handler.getAnswers());
  }

  private static String md5(String msg) {
    return DigestUtils.md5Hex(msg);
  }
}
