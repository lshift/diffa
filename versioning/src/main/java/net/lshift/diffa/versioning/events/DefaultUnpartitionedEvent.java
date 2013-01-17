package net.lshift.diffa.versioning.events;

import net.lshift.diffa.events.SimpleUpsertEvent;
import net.lshift.diffa.versioning.partitioning.MerkleNode;
import net.lshift.diffa.versioning.partitioning.MerkleUtils;
import org.joda.time.DateTime;

public class DefaultUnpartitionedEvent extends SimpleUpsertEvent implements UnpartitionedEvent {

  private final MerkleNode idHierarchy;

  public DefaultUnpartitionedEvent(String id, String version, DateTime lastUpdated) {
    super(id, version, lastUpdated);
    idHierarchy = MerkleUtils.buildEntityIdNode(id, version);
  }

  @Override
  public MerkleNode getIdHierarchy() {
    return idHierarchy;
  }
}
