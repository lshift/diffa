package net.lshift.diffa.versioning.events;

import net.lshift.diffa.events.UpsertEvent;
import net.lshift.diffa.versioning.partitioning.MerkleNode;
import org.joda.time.DateTime;

public interface UnpartitionedEvent extends UpsertEvent {

  String getVersion();

  DateTime getLastUpdated();

  MerkleNode getIdHierarchy();
}
