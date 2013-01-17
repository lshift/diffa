package net.lshift.diffa.versioning;


import com.ecyrd.speed4j.StopWatch;
import com.ecyrd.speed4j.StopWatchFactory;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.*;
import me.prettyprint.cassandra.serializers.BooleanSerializer;
import me.prettyprint.cassandra.serializers.DynamicCompositeSerializer;
import me.prettyprint.cassandra.serializers.StringSerializer;
import me.prettyprint.cassandra.service.CassandraHost;
import me.prettyprint.cassandra.service.CassandraHostConfigurator;
import me.prettyprint.cassandra.service.ColumnSliceIterator;
import me.prettyprint.cassandra.service.template.ColumnFamilyTemplate;
import me.prettyprint.cassandra.service.template.ColumnFamilyResult;
import me.prettyprint.cassandra.service.template.ThriftColumnFamilyTemplate;
import me.prettyprint.hector.api.Cluster;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.beans.DynamicComposite;
import me.prettyprint.hector.api.beans.HColumn;
import me.prettyprint.hector.api.exceptions.HectorException;
import me.prettyprint.hector.api.factory.HFactory;
import me.prettyprint.hector.api.query.SliceQuery;
import net.lshift.diffa.adapter.scanning.*;
import net.lshift.diffa.events.ChangeEvent;
import net.lshift.diffa.events.TombstoneEvent;
import net.lshift.diffa.interview.Answer;
import net.lshift.diffa.interview.NoFurtherQuestions;
import net.lshift.diffa.interview.Question;
import net.lshift.diffa.interview.SimpleQuestion;
import net.lshift.diffa.versioning.events.PartitionedEvent;
import net.lshift.diffa.versioning.events.UnpartitionedEvent;
import net.lshift.diffa.versioning.partitioning.*;
import net.lshift.diffa.versioning.plumbing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CassandraVersionStore implements VersionStore {

  static Logger log = LoggerFactory.getLogger(CassandraVersionStore.class);

  StopWatchFactory stopWatchFactory = StopWatchFactory.getInstance("loggingFactory");

  static final int UNLIMITED_SLICE_SIZE = -1;

  static final Joiner KEY_JOINER = Joiner.on(".").skipNulls();

  static final String KEY_SPACE = "version_store";
  static final String NULL_MD5 = "d41d8cd98f00b204e9800998ecf8427e";

  /**
   * Storage for the raw entity version and their raw partitioning attributes (should they have been supplied)
   */
  static final String ENTITY_VERSIONS_CF = "entity_versions";
  @Deprecated static final String DIRTY_ENTITIES_CF = "dirty_entities";
  static final String USER_DEFINED_ATTRIBUTES_CF = "user_defined_attributes";

  /**
   * Storage for Merkle trees for each endpoint using the (optional) user defined partitioning attribute(s)
   */
  static final String USER_DEFINED_BUCKETS_CF = "user_defined_buckets";
  static final String USER_DEFINED_DIGESTS_CF = "user_defined_digests";
  static final String USER_DEFINED_HIERARCHY_CF = "user_defined_hierarchy";

  /**
   * Storage for Merkle trees for each pair using the (mandatory) entity id field as a partitioning attribute
   */
  static final String PAIR_BUCKETS_CF = "pair_buckets";
  static final String PAIR_DIGESTS_CF = "pair_digests";
  static final String PAIR_HIERARCHY_CF = "pair_hierarchy";

  /**
   * Storage for Merkle trees for the delta between two pairs using the (mandatory) entity id field as a partitioning attribute
   */
  static final String ENTITY_ID_BUCKETS_CF = "entity_id_buckets";
  static final String ENTITY_ID_DIGESTS_CF = "entity_id_digests";
  static final String ENTITY_ID_HIERARCHY_CF = "entity_id_hierarchy";


  /**
   * Commonly used constants in columns
   */

  static final String DIGEST_KEY = "digest";
  static final String VERSION_KEY = "version";
  static final String LAST_UPDATE_KEY = "lastUpdate";
  static final String PARTITION_KEY = "partition";




  private Cluster cluster;
  private Keyspace keyspace;

  private ColumnFamilyTemplate<String, String> entityVersionsTemplate;
  private ColumnFamilyTemplate<String, String> entityIdDigestsTemplate;
  private ColumnFamilyTemplate<String, String> userDefinedDigestsTemplate;
  private ColumnFamilyTemplate<String, String> userDefinedAttributesTemplate;
  private ColumnFamilyTemplate<String, String> pairDigestsTemplate;

  private Map<Long,Integer> sliceSizes = new ConcurrentHashMap<Long,Integer>();

  public CassandraVersionStore() {

    CassandraHostConfigurator configurer = new CassandraHostConfigurator();
    configurer.setHosts("localhost");
    configurer.setRetryDownedHosts(true);

    cluster = HFactory.getOrCreateCluster("test-cluster", configurer);
    keyspace = HFactory.createKeyspace(KEY_SPACE, cluster);

    entityVersionsTemplate =
        new ThriftColumnFamilyTemplate<String, String>(keyspace, ENTITY_VERSIONS_CF,
            StringSerializer.get(),
            StringSerializer.get());

    entityIdDigestsTemplate =
        new ThriftColumnFamilyTemplate<String, String>(keyspace, ENTITY_ID_DIGESTS_CF,
            StringSerializer.get(),
            StringSerializer.get());

    userDefinedDigestsTemplate =
        new ThriftColumnFamilyTemplate<String, String>(keyspace, USER_DEFINED_DIGESTS_CF,
            StringSerializer.get(),
            StringSerializer.get());

    userDefinedAttributesTemplate =
        new ThriftColumnFamilyTemplate<String, String>(keyspace, USER_DEFINED_ATTRIBUTES_CF,
            StringSerializer.get(),
            StringSerializer.get());

    pairDigestsTemplate =
        new ThriftColumnFamilyTemplate<String, String>(keyspace, PAIR_DIGESTS_CF,
            StringSerializer.get(),
            StringSerializer.get());
  }

  public void onEvent(Long endpoint, ChangeEvent event) {

    try {

      if (event instanceof UnpartitionedEvent) {

        UnpartitionedEvent unpartitionedEvent = (UnpartitionedEvent) event;
        addEvent(endpoint, unpartitionedEvent);

      }
      else if (event instanceof TombstoneEvent) {

        deleteEvent(endpoint, event.getId());

      } else {
        throw new InvalidEventException(event, "Version store cannot handle this type of event");
      }

    } catch (HectorException he) {
      String reason = getReason(he);
      log.error("Issue communicating with Cassandra", he);
      throw new VersionStoreException(reason);
    }

  }




  public void setMaxSliceSize(Long endpoint, int size) {
    sliceSizes.put(endpoint, size);
  }

  public TreeLevelRollup getDeltaDigest(PairProjection view) {

    String bucket = (view.getParent() == null) ? "" : view.getParent();

    String context = KEY_JOINER.join(view.getLeft(), view.getRight());

    TreeLevelRollup rollup = null;

    try {

      rollup = getChildDigests(context, bucket, pairDigestsTemplate, PAIR_HIERARCHY_CF, PAIR_DIGESTS_CF, PAIR_BUCKETS_CF, view.getMaxSliceSize());

    } catch (HectorException he) {
      String reason = getReason(he);
      throw new VersionStoreException(reason);

    }

    return unqualify(view.getLeft(), view.getRight(), rollup);
  }

  public List<EntityDifference> getOutrightDifferences(PairProjection view, String bucket) {
    List<EntityDifference> diffs = new ArrayList<EntityDifference>();
    String key = KEY_JOINER.join(view.getLeft(), view.getRight(), bucket);

    SliceQuery<String,String,DynamicComposite> bucketQuery =  HFactory.createSliceQuery(keyspace, StringSerializer.get(), StringSerializer.get(), DynamicCompositeSerializer.get());
    bucketQuery.setColumnFamily(PAIR_BUCKETS_CF);
    bucketQuery.setKey(key);

    try {

      ColumnSliceIterator<String,String,DynamicComposite> iterator = new ColumnSliceIterator<String,String,DynamicComposite>(bucketQuery, "", "",false);

      while(iterator.hasNext()) {
        HColumn<String,DynamicComposite> column = iterator.next();
        String entityId = column.getName();
        DynamicComposite composite = column.getValue();
        String left = composite.get(0).toString();
        String right = composite.get(1).toString();
        EntityDifference diff = new EntityDifference(entityId, left, right);
        diffs.add(diff);
      }

    } catch (HectorException he) {

      String reason = getReason(he);
      throw new VersionStoreException(reason);

    }

    return diffs;
  }

  public Iterable<Question> continueInterview(
      Long endpoint,
      Set<ScanConstraint> constraints,
      Set<ScanAggregation> aggregations,
      Iterable<Answer> entries) {


    try {
        if (aggregations != null && !aggregations.isEmpty()) {


          // TODO Currently we only implement date based aggregation
          Iterator<DateAggregation> dates = Iterables.filter(aggregations, DateAggregation.class).iterator();

          if (!dates.hasNext()) {
            throw new RuntimeException("Must supply 1 date based atrribute: " + aggregations);
          }
          else {
            // TODO Currently we can only handle 1 aggregation
            DateAggregation aggregation = dates.next();
            DateGranularityEnum granularity = aggregation.getGranularity();

            if (granularity == DateGranularityEnum.Yearly) {

              int maxSliceSize = getSliceSize(endpoint);
              final String context = endpoint + "";
              String key = "";
              TreeLevelRollup qualified = getChildDigests(context, key, userDefinedDigestsTemplate, USER_DEFINED_HIERARCHY_CF, USER_DEFINED_DIGESTS_CF, USER_DEFINED_BUCKETS_CF, maxSliceSize);
              TreeLevelRollup unqualified = unqualify(endpoint, qualified);

              boolean isLeaf = false; // TODO It might be a bad decision to hard code this
              Map<String,BucketDigest> remote = DifferencingUtils.convertAggregates(entries, isLeaf);

              //MapDifference<String,BucketDigest> d = Maps.difference(remote, unqualified.getMembers());

              return getNextQuestion(remote, unqualified.getMembers());
            }
          }


        } else {
          throw new RuntimeException("Not aggregated query not yet implemented");
        }

    } catch (HectorException he) {
      String reason = getReason(he);
      throw new VersionStoreException(reason);
    }


    return null;
  }

  //////////////////////////////////////////////////////
  // Internal plumbing
  //////////////////////////////////////////////////////

  private Iterable<Question> getNextQuestion(Map<String,BucketDigest> remote, Map<String,BucketDigest> local) {

    MapDifference<String,BucketDigest> sides = Maps.difference(remote, local);

    if (sides.areEqual()) {
      return NoFurtherQuestions.get();
    }

    else {

      List<Question> questions = new ArrayList<Question>();

      for (Map.Entry<String, MapDifference.ValueDifference<BucketDigest>> entry :  sides.entriesDiffering().entrySet()) {


        SimpleQuestion question = new SimpleQuestion();

        questions.add(question);
      }

      // Retrieve the details of buckets that we haven't received locally

      for (Map.Entry<String,BucketDigest> entry : sides.entriesOnlyOnLeft().entrySet()) {

        SimpleQuestion question = new SimpleQuestion();

        questions.add(question);
      }

      // Purge data that the remote system no longer cares about

      for (Map.Entry<String,BucketDigest> entry : sides.entriesOnlyOnRight().entrySet()) {

      }

      return questions;
    }

  }

  private String getReason(HectorException he) {
    return he.getMessage();
  }

  private void addEvent(Long endpoint, UnpartitionedEvent event) {

    StopWatch stopWatch = stopWatchFactory.getStopWatch();

    final String id = buildIdentifier(endpoint, event.getId());
    final String parentPath = endpoint.toString();

    BatchMutator mutator = new BasicBatchMutator(keyspace);

    mutator.insertColumn(id, ENTITY_VERSIONS_CF, VERSION_KEY, event.getVersion() );

    if (event.getLastUpdated() != null) {
      mutator.insertDateColumn(id, ENTITY_VERSIONS_CF, LAST_UPDATE_KEY, event.getLastUpdated());
    }

    String entityIdPartition = event.getIdHierarchy().getDescendencyPath();
    mutator.insertColumn(id, ENTITY_VERSIONS_CF,  PARTITION_KEY, entityIdPartition);

    // Update the dirty entities for a subsequent re-sync to avoid
    // having to traverse buckets in order to pair-off changes between two
    // endpoints.
    String dirtyKey = buildIdentifier(endpoint, entityIdPartition);
    mutator.invalidateColumn(dirtyKey, DIRTY_ENTITIES_CF, event.getId());

    MerkleNode entityIdRootNode = new MerkleNode("", event.getIdHierarchy());

    List<String> viewsToUpdate = eventSubscribers(endpoint, event);

    for(String view : viewsToUpdate) {
      // TODO Work out how to include the view
      BucketWriter writer = new OutrightBucketWriter(mutator, ENTITY_ID_BUCKETS_CF);
      recordLineage(parentPath, entityIdRootNode, mutator, entityIdDigestsTemplate, writer, ENTITY_ID_HIERARCHY_CF, ENTITY_ID_DIGESTS_CF);
    }

    if (event instanceof PartitionedEvent) {

      PartitionedEvent partitionedEvent = (PartitionedEvent) event;

      Map<String, String> attributes = partitionedEvent.getAttributes();
      if (attributes != null && !attributes.isEmpty()) {

        for(Map.Entry<String,String> entry : attributes.entrySet()) {
          mutator.insertColumn(id, USER_DEFINED_ATTRIBUTES_CF, entry.getKey(), entry.getValue());
        }

        String userDefinedPartition = partitionedEvent.getAttributeHierarchy().getDescendencyPath();
        mutator.insertColumn(id, USER_DEFINED_ATTRIBUTES_CF, PARTITION_KEY, userDefinedPartition);

        MerkleNode userDefinedRootNode = new MerkleNode("", partitionedEvent.getAttributeHierarchy());
        BucketWriter writer = new OutrightBucketWriter(mutator, USER_DEFINED_BUCKETS_CF);
        recordLineage(parentPath, userDefinedRootNode, mutator, userDefinedDigestsTemplate, writer, USER_DEFINED_HIERARCHY_CF, USER_DEFINED_DIGESTS_CF);
      }
      else {
        throw new InvalidEventException(event, "Event is of type PartitionedEvent, but attributes are empty");
      }

    }

    mutator.execute();

    stopWatch.stop(String.format("addEvent: endpoint = %s / event = %s", endpoint, event.getId()));

  }

  private void deleteEvent(Long endpoint, String id) {

    StopWatch stopWatch = stopWatchFactory.getStopWatch();

    // Assume that the hierarchy definitions are going to get compacted on the the next read
    // so that this function does as little work as possible

    BatchMutator mutator = new BasicBatchMutator(keyspace);

    final String key = buildIdentifier(endpoint, id);
    final String parentPath = endpoint.toString();

    // Remove the hierarchies related to this event

    invalidateHierarchy(id, mutator, key, parentPath, entityVersionsTemplate, ENTITY_ID_DIGESTS_CF, ENTITY_ID_BUCKETS_CF);
    invalidateHierarchy(id, mutator, key, parentPath, userDefinedAttributesTemplate, USER_DEFINED_DIGESTS_CF, USER_DEFINED_BUCKETS_CF);

    // Delete the raw data pertaining to this event

    mutator.deleteRow(key, ENTITY_VERSIONS_CF);
    mutator.deleteRow(key, ENTITY_ID_BUCKETS_CF);
    mutator.deleteRow(key, USER_DEFINED_ATTRIBUTES_CF);

    // Mark the entity as dirty so that subsequent compactions had emit match events based on this
    MerkleNode dirtyNode = MerkleUtils.buildEntityIdNode(id, null);
    final String dirtyKey = buildIdentifier(endpoint, dirtyNode.getDescendencyPath());
    mutator.invalidateColumn(dirtyKey, DIRTY_ENTITIES_CF, id);

    mutator.execute();

    stopWatch.stop(String.format("deleteEvent: endpoint = %s / event = %s", endpoint, id));

  }

  /**
   * This returns the list of views that requires an entity based tree (i.e. for each pair.)
   */
  private List<String> eventSubscribers(Long endpoint, UnpartitionedEvent event) {
    List<String> views = new ArrayList<String>();

    // TODO Hack - make sure that the caller indexes at least once (for now) in the for loop that this is called
    views.add("");

    return views;
  }

  private int getSliceSize(Long endpoint) {

    Integer sliceSize = sliceSizes.get(endpoint);

    if (sliceSize == null) {
      return UNLIMITED_SLICE_SIZE;
    }
    else {
      return sliceSize;
    }
  }

  public void deltify(PairProjection view) {
    TreeLevelDifference treeLevelDifference = getDifference(view.getLeft(), view.getRight(), view.getParent());
    BatchMutator mutator = new BasicBatchMutator(keyspace);
    establishDifferingSides(view.getLeft(), view.getRight(), treeLevelDifference, mutator);
    mutator.execute();
  }

  private TreeLevelDifference getDifference(Long left, Long right, String bucket) {
    TreeLevelRollup leftTree = getEntityIdChildDigests(left, bucket);
    TreeLevelRollup rightTree = getEntityIdChildDigests(right, bucket);
    MapDifference<String, BucketDigest> difference = Maps.difference(leftTree.getMembers(), rightTree.getMembers());

    if (leftTree.isLeaf() == rightTree.isLeaf()) {
      return new TreeLevelDifference(difference, leftTree.isLeaf());
    }
    else {
      throw new RuntimeException("Inconsistent leafyness; left [ " + leftTree + " ]; right [ " + rightTree + " ]");
    }

  }


  private void establishDifferingSides(Long left, Long right, TreeLevelDifference currentTreeLevelDiff, BatchMutator mutator) {

    MapDifference<String,BucketDigest> diffs = currentTreeLevelDiff.getDifference();
    Map<String, MapDifference.ValueDifference<BucketDigest>> differing = diffs.entriesDiffering();

    for (String bucket : differing.keySet()) {

      if (currentTreeLevelDiff.isLeaf()) {
        establishBucketDifferences(left, right, bucket, mutator);
      }
      else {
        TreeLevelDifference nextTreeLevelDiff = getDifference(left, right, bucket);
        establishDifferingSides(left, right, nextTreeLevelDiff, mutator);
      }

    }

    Map<String,BucketDigest> onlyOnLeft = diffs.entriesOnlyOnLeft();
    establishMissingSide(left, onlyOnLeft, mutator);

    Map<String,BucketDigest> onlyOnRight = diffs.entriesOnlyOnRight();
    establishMissingSide(right, onlyOnRight, mutator);

  }

  private void establishMissingSide(Long side, Map<String,BucketDigest> missingSide, BatchMutator mutator) {

    for (String bucket : missingSide.keySet()) {

      BucketDigest bucketDigest = missingSide.get(bucket);

      if (bucketDigest.isLeaf()) {

        establishBucketDifferences(side, bucket, mutator);

      } else {

        TreeLevelRollup subTree = getEntityIdChildDigests(side, bucket);
        Map<String,BucketDigest> members = subTree.getMembers();
        establishMissingSide(side, members, mutator);
      }
    }
  }

  private TreeLevelRollup getEntityIdChildDigests(Long endpoint, String bucket) {
    String key = buildKeyFromBucketName(endpoint, bucket);
    String context = endpoint + "";
    TreeLevelRollup qualified =  getChildDigests(context, bucket, entityIdDigestsTemplate, ENTITY_ID_HIERARCHY_CF, ENTITY_ID_DIGESTS_CF, ENTITY_ID_BUCKETS_CF);
    //TreeLevelRollup qualified =  getChildDigests(key, entityIdDigestsTemplate, ENTITY_ID_HIERARCHY_CF, ENTITY_ID_DIGESTS_CF, ENTITY_ID_BUCKETS_CF);
    return unqualify(endpoint, qualified);
  }

  private void establishBucketDifferences(Long side, String key, BatchMutator mutator) {
    establishBucketDifferences(side, null, key , mutator);
  }

  private void establishBucketDifferences(Long left, Long right, String key, BatchMutator mutator) {
    Map<String, EntityDifference> potentialDiffs = new HashMap<String,EntityDifference>();

    // Read out the dirty entities by looking at the LHS first
    // Once we have the established the differences from the LHS perspective,
    // do the same for the RHS, but don't attempt to re-establish diffs that were detected
    // during the LHS processing

    if (left != null) {

      ColumnSliceIterator<String, String, String> leftIterator = getEntityIdBucketIterator(left, key);

      while (leftIterator.hasNext()) {
        HColumn<String,String> entityColumn = leftIterator.next();
        final String entityId = entityColumn.getName();
        EntityDifference potentialDiff = getPotentialDifference(left, right, entityId);
        potentialDiffs.put(entityId, potentialDiff);
      }

    }

    if (right != null) {

      ColumnSliceIterator<String, String, String> rightIterator = getEntityIdBucketIterator(right, key);

      while (rightIterator.hasNext()) {
        HColumn<String,String> entityColumn = rightIterator.next();
        final String entityId = entityColumn.getName();

        if (! potentialDiffs.containsKey(entityId) ) {

          EntityDifference potentialDiff = getPotentialDifference(left, right, entityId);
          potentialDiffs.put(entityId, potentialDiff);
        }

      }

    }

    Predicate<EntityDifference> diffFilter = new Predicate<EntityDifference>() {
      @Override
      public boolean apply(EntityDifference input) {
        return input.isDifferent();
      }
    };

    Map<String,EntityDifference> filtered = Maps.filterValues(potentialDiffs, diffFilter);

    if (!filtered.isEmpty()) {

      String parentPath = KEY_JOINER.join(left, right);

      for(Map.Entry<String,EntityDifference> entry : filtered.entrySet()) {
        EntityDifference diff = entry.getValue();
        MerkleNode entityIdNode = MerkleUtils.buildEntityIdNode(diff.getId(), null);
        MerkleNode rootNode = new MerkleNode("", entityIdNode);
        BucketWriter writer = new DeltaBucketWriter(PAIR_BUCKETS_CF, mutator, diff);
        recordLineage(parentPath, rootNode, mutator, pairDigestsTemplate, writer, PAIR_HIERARCHY_CF, PAIR_DIGESTS_CF);
      }

    }

  }

  private EntityDifference getPotentialDifference(Long left, Long right, String id) {
    final String leftVersion = getEntityVersion(left, id);
    final String rightVersion = getEntityVersion(right, id);
    return new EntityDifference(id, leftVersion, rightVersion);
  }

  private ColumnSliceIterator<String, String, String> getEntityIdBucketIterator(Long endpoint, String key) {

    final String queryKey = buildIdentifier(endpoint, key);

    SliceQuery<String,String,String> query =  HFactory.createSliceQuery(keyspace, StringSerializer.get(), StringSerializer.get(), StringSerializer.get());
    query.setColumnFamily(ENTITY_ID_BUCKETS_CF);
    query.setKey(queryKey);

    return new ColumnSliceIterator<String,String,String>(query, "", "",false);
  }

  private String getEntityVersion(Long endpoint, String key) {

    if (endpoint == null || key == null) {
      return null;
    }
    else {

      final String entityKey = buildIdentifier(endpoint, key);
      ColumnFamilyResult<String,String> result = entityVersionsTemplate.queryColumns(entityKey);

      if (result.hasResults()) {
        return result.getString(VERSION_KEY);
      }
      else {
        return null;
      }
    }

  }

  private String deleteDescendencyPath(String parentPath, MerkleNode node, BatchMutator mutator, String digestCF) {
    String key = qualifyNodeName(parentPath, node);

    mutator.invalidateColumn(key, digestCF, DIGEST_KEY);

    if (node.isLeaf()) {
      return key;
    }
    else {
      return deleteDescendencyPath(key, node.getChild(), mutator, digestCF);
    }
  }

  private void invalidateHierarchy(String id, BatchMutator mutator, String key, String parentPath, ColumnFamilyTemplate<String, String> template, String digestsCF, String bucketsCF) {

    ColumnFamilyResult<String,String> result = template.queryColumns(key);

    if (result.hasResults()) {
      String entityIdPartition = result.getString(PARTITION_KEY);

      // Invalidate the digest hierarchy to the bucket that was just deleted

      MerkleNode entityIdParentNode = MerkleNode.buildHierarchy(entityIdPartition);
      String entityIdBucketKey = deleteDescendencyPath(parentPath, entityIdParentNode, mutator, digestsCF);

      // Delete the item level digest from the buckets CF

      mutator.deleteColumn(entityIdBucketKey, bucketsCF, id);
    }
  }

  private void recordLineage(String parentName, MerkleNode node, BatchMutator mutator, ColumnFamilyTemplate<String,String> hierarchyDigests, BucketWriter bucketWriter, String hierarchyCF, String hierarchyDigestCF) {

    String qualifiedBucketName = qualifyNodeName(parentName, node);

    // In any event, since we are updating a bucket in this lineage, we need to invalidate the digest of each
    // parent node

    mutator.invalidateColumn(qualifiedBucketName, hierarchyDigestCF, DIGEST_KEY);

    if (node.isLeaf()) {
      // This a leaf node so record the bucket value and it's parent
      bucketWriter.write(qualifiedBucketName, node);
      //mutator.insertColumn(qualifiedBucketName, bucketCF, node.getId(), node.getDigest());
    }
    else {

      MerkleNode child = node.getChild();
      HColumn<String,Boolean> childColumn = HFactory.createColumn(child.getName(), child.isLeaf(), StringSerializer.get(), BooleanSerializer.get());

      ColumnFamilyResult<String,String> result = hierarchyDigests.queryColumns(qualifiedBucketName);

      if (result.hasResults()) {
        String digestLabel = result.getString(child.getName());
        if (digestLabel == null || digestLabel.isEmpty()) {
          // TODO Consider populating the child value with the digest of the child instead of leaving it empty
          mutator.insertColumn(qualifiedBucketName, hierarchyCF, childColumn);
        }
      }
      else {
        // TODO See todo 4 lines back
        mutator.insertColumn(qualifiedBucketName, hierarchyCF, childColumn);

      }

      recordLineage(qualifiedBucketName, node.getChild(), mutator, hierarchyDigests, bucketWriter, hierarchyCF, hierarchyDigestCF);
    }

  }

  private String qualifyNodeName(String parentName, MerkleNode node) {
    String qualifiedBucketName;

    if (node.getName().isEmpty()) {
      qualifiedBucketName = parentName;
    } else {
      qualifiedBucketName = parentName + "." + node.getName();
    }
    return qualifiedBucketName;
  }

  private SortedMap<String,BucketDigest> getGenericDigests(Long endpoint, String bucketName, ColumnFamilyTemplate<String, String> hierarchyDigests, String hierarchyCF, String digestCF, String bucketCF, int maxSliceSize) {
    return getGenericDigests(endpoint, bucketName, false, hierarchyDigests, hierarchyCF, digestCF, bucketCF, maxSliceSize);
  }

  private SortedMap<String,BucketDigest> getGenericDigests(Long endpoint, String bucketName, boolean isLeaf, ColumnFamilyTemplate<String, String> hierarchyDigests, String hierarchyCF, String digestCF, String bucketCF, int maxSliceSize) {

    StopWatch stopWatch = stopWatchFactory.getStopWatch();

    BatchMutator mutator = new BasicBatchMutator(keyspace);

    final String key = buildKeyFromBucketName(endpoint, bucketName);

    String context = endpoint + "";
    BucketDigest digest = getDigest(context, bucketName, isLeaf, mutator, hierarchyDigests, hierarchyCF, digestCF, bucketCF, maxSliceSize);
    //BucketDigest digest = getDigest(key, isLeaf, mutator, hierarchyDigests, hierarchyCF, digestCF, bucketCF, maxSliceSize);

    mutator.execute();

    stopWatch.stop(String.format("getEntityIdDigests: endpoint = %s / bucket = %s", endpoint, bucketName));

    SortedMap<String,BucketDigest> digests = new TreeMap<String, BucketDigest>();
    digests.put(key, digest);

    return unqualify(endpoint, digests);
  }

  private String buildKeyFromBucketName(Long endpoint, String bucketName) {
    String key;
    if (bucketName == null || bucketName.isEmpty()) {
      key = endpoint.toString();
    }
    else {
      key = buildIdentifier(endpoint, bucketName);
    }
    return key;
  }

  // TOODO this is very hacky
  private String buildKey(String context, String bucketName) {
    StringBuilder sb = new StringBuilder();

    boolean ctx = false;

    if (context != null && !context.isEmpty()) {
      sb.append(context);
      ctx = true;
    }

    if (bucketName != null && !bucketName.isEmpty()) {

      if (ctx) {
        sb.append(".");
      }

      sb.append(bucketName);

    }

    return sb.toString();
  }

  private TreeLevelRollup getChildDigests(String context, String key, ColumnFamilyTemplate<String, String> hierarchyDigests, String hierarchyCF, String digestCF, String bucketCF) {
    return getChildDigests(context, key, hierarchyDigests, hierarchyCF, digestCF, bucketCF, UNLIMITED_SLICE_SIZE);
  }

  private TreeLevelRollup getChildDigests(final String context, String key, ColumnFamilyTemplate<String, String> hierarchyDigests, String hierarchyCF, String digestCF, String bucketCF, int maxSliceSize) {

    BatchMutator mutator = new BasicBatchMutator(keyspace);

    BitSet bits = new BitSet();
    int index = 0;
    Map<String,BucketDigest> digests = new HashMap<String,BucketDigest>();


    String q = buildKey(context, key);
    ColumnSliceIterator<String, String, Boolean> hierarchyIterator = getHierarchyIterator(q, hierarchyCF);

    while (hierarchyIterator.hasNext()) {
      HColumn<String, Boolean> hierarchyColumn = hierarchyIterator.next();

      String childName = hierarchyColumn.getName();
      boolean leaf = hierarchyColumn.getValue();

      if (leaf) {
        bits.set(index++);
      }

      //String child = key + "." + childName;
      String child = buildKey(key, childName);

      BucketDigest digest = getDigest(context, child, leaf, mutator, hierarchyDigests, hierarchyCF, digestCF, bucketCF, maxSliceSize);
      digests.put(child, digest);

    }

    mutator.execute();

    if (bits.cardinality() == 0 || bits.cardinality() == bits.length()) {

      boolean isLeaf = bits.get(0);
      return new TreeLevelRollup(context, key, maxSliceSize, digests, isLeaf);

    }
    else {
      throw new RuntimeException("Inconsistent sub tree: " + digests);
    }


  }


  private BucketDigest getDigest(final String context, String key, boolean isLeaf, BatchMutator mutator, ColumnFamilyTemplate<String, String> hierarchyDigests, String hierarchyCF, String digestCF, String bucketCF, int maxSliceSize) {


    String qualifiedKey = KEY_JOINER.join(context, key);

    ColumnFamilyResult<String, String> result = hierarchyDigests.queryColumns(qualifiedKey);

    if (isLeaf) {

      // Since this is a leaf node, we need to roll up all of the individual entity digests stored in the bucket CF

      BucketDigest bucketDigest = buildBucketDigest(qualifiedKey, bucketCF, maxSliceSize);

      // Check to see whether this bucket can be compacted

      if (NULL_MD5.equals(bucketDigest)) {

        // Delete since it is empty delete this bucket, digest and hierarchy
        mutator.deleteRow(qualifiedKey, bucketCF);
        deleteDigest(qualifiedKey, mutator, digestCF, hierarchyCF);

      } else {
        cacheDigest(qualifiedKey, mutator, digestCF, bucketDigest);

      }

      return bucketDigest;//digests.put(key, bucketDigest);

    }
    else if (result.hasResults()) {

      String cachedDigest = result.getString(DIGEST_KEY);

      if (cachedDigest == null || cachedDigest.isEmpty()) {

        // There is no cached digest for this key, we need to resolve the children and establish their digests

        ColumnSliceIterator<String, String, Boolean> hierarchyIterator = getHierarchyIterator(qualifiedKey, hierarchyCF);

        Digester digester = new Digester();

        while (hierarchyIterator.hasNext()) {
          HColumn<String, Boolean> hierarchyColumn = hierarchyIterator.next();

          String child = hierarchyColumn.getName();
          boolean leaf = hierarchyColumn.getValue();

          String childKey = key + "." + child;

          //SortedMap<String,BucketDigest> childDigests = getDigest(qualifiedKey, leaf, mutator, hierarchyDigests, hierarchyCF, digestCF, bucketCF);
          BucketDigest childDigest = getDigest(context, childKey, leaf, mutator, hierarchyDigests, hierarchyCF, digestCF, bucketCF, maxSliceSize);
          digester.addVersion(childDigest.getDigest());

          // We need to roll up the subtree digests to produce an over-arching digest for the current bucket

          /*for (Map.Entry<String, BucketDigest> entry : childDigests.entrySet()) {
            digester.addVersion(entry.getValue().getDigest());
          }*/
        }

        String digest = digester.getDigest();
        BucketDigest bucketDigest = new BucketDigest(qualifiedKey, digest, false);

        if (NULL_MD5.equals(bucketDigest)) {
          // Clean up empty digests
          deleteDigest(qualifiedKey, mutator, digestCF, hierarchyCF);
        }
        else {

          //digests.put(key, bucketDigest);

          // Don't forget to cache this digest for later use

          cacheDigest(qualifiedKey, mutator, digestCF, bucketDigest);

        }

        return bucketDigest;

      }
      else {
        BucketDigest bucketDigest = new BucketDigest(key, cachedDigest, false);
        //digests.put(key, bucketDigest);
        return bucketDigest;
      }

    } else {
      // TODO qualify with context
      throw new BucketNotFoundException(key);
    }

    //return digests;
  }

  private ColumnSliceIterator<String, String, Boolean> getHierarchyIterator(String key, String hierarchyCF) {
    SliceQuery<String,String,Boolean> hierarchyQuery =  HFactory.createSliceQuery(keyspace, StringSerializer.get(), StringSerializer.get(), BooleanSerializer.get());
    hierarchyQuery.setColumnFamily(hierarchyCF);
    hierarchyQuery.setKey(key);

    return new ColumnSliceIterator<String,String,Boolean>(hierarchyQuery, "", "",false);
  }

  private void cacheDigest(String key, BatchMutator mutator, String digestCF, BucketDigest bucketDigest) {
    mutator.insertColumn(key, digestCF, DIGEST_KEY, bucketDigest.getDigest());
  }

  private void deleteDigest(String key, BatchMutator mutator, String digestCF, String hierarchyCF) {
    mutator.deleteRow(key, digestCF);
    int pivot = key.lastIndexOf(".");
    String parent = key.substring(0, pivot - 1);
    String child = key.substring(pivot + 1);
    mutator.deleteColumn(parent, hierarchyCF, child);
  }

  private BucketDigest buildBucketDigest(String key, String bucketCF, final int maxSliceSize) {

    List<String> digests = new ArrayList<String>();

    SliceQuery<String,String,String> query =  HFactory.createSliceQuery(keyspace, StringSerializer.get(), StringSerializer.get(), StringSerializer.get());

    query.setColumnFamily(bucketCF);
    query.setKey(key);

    ColumnSliceIterator<String,String,String> bucketIterator = new ColumnSliceIterator<String,String,String>(query, "", "",false);

    // Slice the buckets up according to the maximum sub bucket size

    int currentBucketSize = 0;
    Digester digester = new Digester();

    while (bucketIterator.hasNext()) {
      HColumn<String, String> column = bucketIterator.next();
      String version = column.getValue();

      if (maxSliceSize > 0 && currentBucketSize == maxSliceSize) {
        rolloverSlice(digests, digester);
        currentBucketSize = 0;
      }

      digester.addVersion(version);

      if (maxSliceSize > 0) {
        currentBucketSize++;
      }

    }

    rolloverSlice(digests, digester);

    for (String digest : digests) {
      digester.addVersion(digest);
    }

    return new BucketDigest(key, digester.getDigest(), true);
  }

  private SortedMap<String, BucketDigest> unqualify(Long endpoint, Map<String, BucketDigest> qualified) {
    return unqualify(endpoint, null, qualified);
  }
  /**
   * This is a hack to overcome the fact that the digest tree builder (unhelpfully) qualifies each node
   * by prefixing the endpoint id to the key name. This oversight makes subsequent tree comparisons difficult,
   * because each tree will have differing root names, which is uncool.
   *
   * Rather than fixing the underlying issue, at this point I am just going to rewrite each node label to
   * get rid of this prefixing.
   */
  private SortedMap<String, BucketDigest> unqualify(Long left, Long right, Map<String, BucketDigest> qualified) {
    SortedMap<String,BucketDigest> unqualified = new TreeMap<String, BucketDigest>();

    if (qualified != null) {

      for(Map.Entry<String,BucketDigest> entry : qualified.entrySet()) {
        StringBuilder sb = new StringBuilder();
        if (left != null) {
          sb.append(left);
        }

        if (right != null) {
          if (left != null) {
            sb.append(".");
          }
          sb.append(right);
        }

        sb.append("(\\.)?");

        String prefix = sb.toString();
        String rewrittenKey = entry.getKey().replaceFirst(prefix, "");
        unqualified.put(rewrittenKey, entry.getValue());
      }

    }

    return unqualified;
  }

  private TreeLevelRollup unqualify(Long endpoint, TreeLevelRollup qualified) {
    SortedMap<String,BucketDigest> unqualified = unqualify(endpoint, qualified.getMembers());
    return new TreeLevelRollup(qualified.getContext(), qualified.getBucket(), qualified.getMaxSliceSize(), unqualified, qualified.isLeaf());
  }

  private TreeLevelRollup unqualify(Long left, Long right, TreeLevelRollup qualified) {
    SortedMap<String,BucketDigest> unqualified = unqualify(left, right, qualified.getMembers());
    return new TreeLevelRollup(qualified.getContext(), qualified.getBucket(), qualified.getMaxSliceSize(), unqualified, qualified.isLeaf());
  }

  private void rolloverSlice(List<String> digests, Digester digester) {
    String currentDigest = digester.getDigest();
    digests.add(currentDigest);
    digester.reset();
  }


  private String buildIdentifier(Long endpoint, String id) {
    return KEY_JOINER.join(endpoint, id);
  }

}
