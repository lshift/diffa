package net.lshift.diffa.versioning.partitioning;

import net.lshift.diffa.adapter.changes.ChangeEvent;
import net.lshift.diffa.adapter.scanning.ScanAggregation;
import net.lshift.diffa.versioning.events.PartitionedEvent;
import org.joda.time.DateTime;

import java.util.Map;
import java.util.SortedMap;

/**
 * This is a sketch pad class ATM ....
 */
public class PartitionedChangeEvent implements PartitionedEvent {

  private ChangeEvent event = null;
  private MerkleNode idHierarchy = null;
  private MerkleNode userHierarchy = null;
  private SortedMap<String, ScanAggregation> categories = null;

  public PartitionedChangeEvent(ChangeEvent event) {
    this.event = event;
  }

  public PartitionedChangeEvent(ChangeEvent event, SortedMap<String, ScanAggregation> categories) {
    this.event = event;
    this.categories = categories;
  }

  @Override
  public String getId() {
    return event.getId();
  }

  @Override
  public String getVersion() {
    return event.getVersion();
  }

  @Override
  public DateTime getLastUpdated() {
    return event.getLastUpdated();
  }

  @Override
  public Map<String, String> getAttributes() {
    return event.getAttributes();
  }

  @Override
  public MerkleNode getIdHierarchy() {

    if (idHierarchy == null) {
      idHierarchy = MerkleUtils.buildEntityIdNode(getId(), getVersion());
    }

    return idHierarchy;
  }



  @Override
  public MerkleNode getAttributeHierarchy() {

    if (getAttributes() != null && categories != null) {
      if (userHierarchy == null) {
        userHierarchy = MerkleUtils.buildUserDefinedNode(categories, getAttributes());
      }
    }

    return userHierarchy;
  }
}
