package net.lshift.diffa.versioning.partitioning;

import net.lshift.diffa.versioning.events.PartitionedEvent;
import org.joda.time.DateTime;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractPartitionedEvent implements PartitionedEvent {

  protected String id;
  protected String version;
  protected DateTime lastUpdate = new DateTime();
  protected Map<String,String> attributes = new HashMap<String, String>();

  public AbstractPartitionedEvent(String version, String id) {
    this.version = version;
    this.id = id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AbstractPartitionedEvent)) return false;

    AbstractPartitionedEvent that = (AbstractPartitionedEvent) o;

    if (id != null ? !id.equals(that.id) : that.id != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : 0;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  @Override
  public DateTime getLastUpdated() {
    return lastUpdate;
  }

  @Override
  public MerkleNode getIdHierarchy() {
    return MerkleUtils.buildEntityIdNode(getId(), getVersion());
  }

  @Override
  public Map<String, String> getAttributes() {
    return attributes;
  }
}
