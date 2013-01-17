package net.lshift.diffa.versioning.integration;

import com.google.common.collect.*;
import com.googlecode.flyway.core.Flyway;
import com.jolbox.bonecp.BoneCPConfig;
import com.jolbox.bonecp.BoneCPDataSource;
import net.lshift.diffa.adapter.scanning.*;
import net.lshift.diffa.events.TombstoneEvent;
import net.lshift.diffa.interview.NoFurtherQuestions;
import net.lshift.diffa.interview.Question;
import net.lshift.diffa.scanning.plumbing.BufferedPruningHandler;
import net.lshift.diffa.sql.PartitionMetadata;
import net.lshift.diffa.versioning.*;
import net.lshift.diffa.versioning.events.PartitionedEvent;
import net.lshift.diffa.versioning.partitioning.AbstractPartitionedEvent;
import net.lshift.diffa.versioning.partitioning.MerkleNode;
import net.lshift.diffa.versioning.partitioning.MerkleUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.jooq.SQLDialect;
import org.jooq.impl.SQLDataType;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

import java.util.*;

public class CassandraVersionStoreIT {

  static Logger log = LoggerFactory.getLogger(CassandraVersionStoreIT.class);

  private CassandraVersionStore store;
  private PairProjection view;
  private List<TestablePartitionedEvent> leftEvents = new LinkedList<TestablePartitionedEvent>();
  private List<TestablePartitionedEvent> rightEvents = new LinkedList<TestablePartitionedEvent>();
  private int itemsInSync = 20;

  @Before
  public void setup() throws Exception {
    store = new CassandraVersionStore();
    view = createRandomPair();
    insertEventStreams(store, view, leftEvents, rightEvents, itemsInSync);

    // At this point, both event streams have inserted the same data, so the delta should be flat
  }

  @Test
  public void flatEventStreamsShouldNotProduceDelta() throws Exception {
    store.deltify(view);
    TreeLevelRollup rollup = store.getDeltaDigest(view);
    assertTrue(rollup.isEmpty());
  }

  @Test
  public void differingEndpointsShouldProduceDelta() throws Exception {

    // Mutate an event to produce a delta

    Random random = new Random();
    final TestablePartitionedEvent randomLeftEvent = leftEvents.get(random.nextInt(itemsInSync));
    randomLeftEvent.setVersion(RandomStringUtils.randomAlphanumeric(10));

    store.onEvent(view.getLeft(), randomLeftEvent);

    store.deltify(view);
    TreeLevelRollup secondRollup = store.getDeltaDigest(view);

    assertNotNull(secondRollup);
    assertFalse(secondRollup.isEmpty());

    // At this point we could/should walk the delta tree, but we already know which event caused the delta
    // and hence we know the bucket that it belongs to

    MerkleNode leftEntityNode = MerkleUtils.buildEntityIdNode(randomLeftEvent.getId(), randomLeftEvent.getVersion());
    String path = leftEntityNode.getDescendencyPath();

    List<EntityDifference> diffs = store.getOutrightDifferences(view, path);

    assertEquals(1, diffs.size());

    EntityDifference diff = diffs.get(0);

    assertEquals(randomLeftEvent.getId(), diff.getId());
    assertEquals(randomLeftEvent.getVersion(), diff.getLeft());

    // TODO This could be more comprehensive, i.e. it asserts nothing about the RHS of the diff

  }

  @Test
  public void interviewShouldFinishWhenVersionStoreIsInSyncWithRemoteSystem() throws Exception {

    int maxSliceSize = 100;
    final Long left = System.currentTimeMillis() * 2;


    store.setMaxSliceSize(left, maxSliceSize);

    BoneCPDataSource ds = null;

    try {
      BoneCPConfig config = new BoneCPConfig();
      config.setJdbcUrl("jdbc:hsqldb:mem:" + RandomStringUtils.randomAlphabetic(5));
      config.setUsername("sa");
      config.setPassword("");

      ds = new BoneCPDataSource(config);

      Flyway flyway = new Flyway();
      flyway.setDataSource(ds);
      flyway.migrate();

    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    PartitionMetadata conf = new PartitionMetadata("THINGS");
    conf.withId("ID", SQLDataType.VARCHAR).
         withVersion("VERSION", SQLDataType.VARCHAR).
         partitionBy("ENTRY_DATE", SQLDataType.DATE);

    // TODO add support for databases other than HSQLDB.
    PartitionAwareThings partitionAwareStore = new PartitionAwareThings(ds, conf, SQLDialect.HSQLDB);

    String attributeName = "bizDate";

    // Populate the remote system with some random data

    Random random = new Random();
    DateTime median = new DateTime();
    int itemsInSync = 100;

    for (int i = 0; i < itemsInSync; i++) {
      DateTime randomDate = median.minusDays(random.nextInt(365 * 10));
      PartitionedEvent event = partitionAwareStore.createRandomThing(ImmutableMap.of(attributeName, randomDate));
      store.onEvent(left, event);
    }

    // Begin the interview process

    ScanAggregation dateAggregation = new DateAggregation(attributeName, DateGranularityEnum.Yearly, null);

    Set<ScanConstraint> cons = null;
    Set<ScanAggregation> aggs = ImmutableSet.of(dateAggregation);

    BufferedPruningHandler handler = new BufferedPruningHandler();

    partitionAwareStore.scan(cons, aggs, maxSliceSize, handler);

    Iterable<Question> questions = store.continueInterview(left, cons, aggs, handler.getAnswers());

    assertTrue(questions instanceof NoFurtherQuestions);

  }



  @Test
  public void unalignedChangeShouldProduceDelta() throws Exception {

    Random random = new Random();
    final TestablePartitionedEvent randomUpstreamEvent = leftEvents.get(random.nextInt(itemsInSync));
    randomUpstreamEvent.setVersion(RandomStringUtils.randomAlphanumeric(10));
    store.onEvent(view.getLeft(), randomUpstreamEvent);

    store.deltify(view);
    TreeLevelRollup firstRollup = store.getDeltaDigest(view);
    assertFalse(firstRollup.isEmpty());

    // TODO Assert something about the rollup, e.g. what digest it contains

    final String idToDelete = randomUpstreamEvent.getId();

    TombstoneEvent tombstone = new TombstoneEvent() {
      @Override
      public String getId() {
        return idToDelete;
      }
    };

    store.onEvent(view.getLeft(), tombstone);
    store.onEvent(view.getRight(), tombstone);

    store.deltify(view);
    TreeLevelRollup secondRollup = store.getDeltaDigest(view);

    // TODO Fix this bug
    //assertTrue(secondRollup.isEmpty());

  }

  private PairProjection createRandomPair() {
    final Long left = System.currentTimeMillis() * 2;
    final Long right = left + 1;
    return new PairProjection(left, right);
  }

  private void insertEventStreams(CassandraVersionStore store, PairProjection pair, List<TestablePartitionedEvent> upstreamEvents, List<TestablePartitionedEvent> downstreamEvents, int itemsInSync) throws InterruptedException {

    Random random = new Random();

    for (int i = 0; i < itemsInSync; i++) {

      String id = RandomStringUtils.randomAlphanumeric(10);
      String version = RandomStringUtils.randomAlphanumeric(10);

      TestablePartitionedEvent upstreamEvent = new DatePartitionedEvent(id, version, random, "transactionDate" );
      TestablePartitionedEvent downstreamEvent = new StringPartitionedEvent(id, version, "userId");

      insertAtRandomPoint(random, upstreamEvents, upstreamEvent);
      insertAtRandomPoint(random, downstreamEvents, downstreamEvent);
    }


    Thread leftEventStream = new Thread(new EventStream(store, pair.getLeft(), upstreamEvents));
    Thread rightEventStream = new Thread(new EventStream(store, pair.getRight(), downstreamEvents));

    leftEventStream.start();
    rightEventStream.start();

    leftEventStream.join();
    rightEventStream.join();
  }

  private <T extends PartitionedEvent> void insertAtRandomPoint(Random random, List<T> eventList, T event) {

    if (eventList.isEmpty()) {
      eventList.add(event);
    }
    else {
      int currentSize = eventList.size();
      int nextInsertion = random.nextInt(currentSize);
      eventList.add(nextInsertion, event);
    }
  }

  private class DatePartitionedEvent extends AbstractPartitionedEvent implements TestablePartitionedEvent {

    private final DateTimeFormatter YEARLY_FORMAT = DateTimeFormat.forPattern("yyyy");
    private final DateTimeFormatter MONTHLY_FORMAT = DateTimeFormat.forPattern("MM");
    private final DateTimeFormatter DAILY_FORMAT = DateTimeFormat.forPattern("dd");

    private DateTime date;

    private DatePartitionedEvent(String id, String version, Random random, String attributeName) {
      super(version, id);

      int range = 10;

      int randomDay = random.nextInt(range);
      DateTime start = new DateTime().minusDays(range);
      DateTime date = start.plusDays(randomDay);
      attributes.put(attributeName, date.toString());
      this.date = date;
    }

    @Override
    public MerkleNode getAttributeHierarchy() {
      MerkleNode leaf = new MerkleNode(DAILY_FORMAT.print(this.date), id, version);
      MerkleNode monthlyBucket = new MerkleNode(MONTHLY_FORMAT.print(this.date), leaf);
      return new MerkleNode(YEARLY_FORMAT.print(this.date), monthlyBucket);
    }
  }

  private class StringPartitionedEvent extends AbstractPartitionedEvent implements TestablePartitionedEvent {

    String attribute;

    private StringPartitionedEvent(String id, String version, String attributeName) {
      super(version, id);
      this.attribute = RandomStringUtils.randomAlphanumeric(10);
      attributes.put(attributeName, this.attribute);
    }

    @Override
    public MerkleNode getAttributeHierarchy() {
      return new MerkleNode(this.attribute.substring(0,2), id, version);
    }
  }

  private class EventStream implements Runnable {

    int cnt = 0;
    List<? extends PartitionedEvent> events;
    Long endpoint;
    VersionStore store;


    EventStream(VersionStore store, Long endpoint, List<? extends PartitionedEvent> events) {
      this.events = events;
      this.endpoint = endpoint;
      this.store = store;
    }

    @Override
    public void run() {
      for (PartitionedEvent event : events) {
        store.onEvent(endpoint, event);
      }
    }
  }

}
