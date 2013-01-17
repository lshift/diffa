package net.lshift.diffa.versioning.events;

import net.lshift.diffa.versioning.partitioning.MerkleNode;

import java.util.Map;

public interface PartitionedEvent extends UnpartitionedEvent {

  Map<String, String> getAttributes();
  MerkleNode getAttributeHierarchy();

}
