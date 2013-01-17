package net.lshift.diffa.versioning.events;


import net.lshift.diffa.versioning.partitioning.MerkleNode;
import net.lshift.diffa.versioning.partitioning.MerkleUtils;
import org.joda.time.DateTime;

import java.util.Map;

public class DefaultPartitionedEvent extends DefaultUnpartitionedEvent implements PartitionedEvent {

  private final Map<String,String> attributes;
  private final MerkleNode userDefinedHierarchy;

  public DefaultPartitionedEvent(String id, String version, DateTime dateTime, Map<String,String> attributes) {
    super(id, version, dateTime);
    this.attributes = attributes;
    userDefinedHierarchy = MerkleUtils.buildUserDefinedNode(null, attributes);
  }

  @Override
  public Map<String, String> getAttributes() {
    return attributes;
  }

  @Override
  public MerkleNode getAttributeHierarchy() {
    return userDefinedHierarchy;
  }
}
