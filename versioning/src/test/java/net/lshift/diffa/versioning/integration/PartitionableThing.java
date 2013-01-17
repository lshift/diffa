package net.lshift.diffa.versioning.integration;

import com.google.common.base.Function;
import com.google.common.collect.Maps;
import net.lshift.diffa.versioning.events.PartitionedEvent;
import net.lshift.diffa.versioning.partitioning.MerkleNode;
import net.lshift.diffa.versioning.partitioning.MerkleUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.sql.Date;
import java.util.Map;

public class PartitionableThing implements PartitionedEvent {

  private final DateTimeFormatter YEARLY_FORMAT = DateTimeFormat.forPattern("yyyy");
  private final DateTimeFormatter MONTHLY_FORMAT = DateTimeFormat.forPattern("MM");
  private final DateTimeFormatter DAILY_FORMAT = DateTimeFormat.forPattern("dd");

  private final Map<String, ?> attributes;

  private MerkleNode node;
  private Date entryDate;
  private String id;
  private String version;


  // TODO need a function to infer the hierarchy from the attributes using ref data
  public PartitionableThing(Map<String, ?> attributes) {
    this.attributes = attributes;
    for (Object attribute : attributes.values()) {
      if (attribute instanceof DateTime) {
        DateTime date = (DateTime) attribute;
        setEntryDate(new Date(date.getMillis()));
      }
    }
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getVersion() {
    return version;
  }

  @Override
  public DateTime getLastUpdated() {
    // TODO this is bogus
    return new DateTime(getEntryDate());
  }

  @Override
  public MerkleNode getIdHierarchy() {
    return MerkleUtils.buildEntityIdNode(getId(), getVersion());
  }

  @Override
  public Map<String, String> getAttributes() {
    return Maps.transformValues(attributes, new Function<Object,String>() {
      @Override public String apply(Object input) {
        return input.toString();
      }
    });

  }

  @Override
  public MerkleNode getAttributeHierarchy() {
    /*
    MerkleNode leaf = new MerkleNode(DAILY_FORMAT.print(getLastUpdated()), getId(), getDigest());
    MerkleNode monthlyBucket = new MerkleNode(MONTHLY_FORMAT.print(getLastUpdated()), leaf);
    return new MerkleNode(YEARLY_FORMAT.print(getLastUpdated()), monthlyBucket);
    */

    for (Object attribute : attributes.values()) {
      if (attribute instanceof DateTime) {
        DateTime date = (DateTime) attribute;
        return MerkleUtils.buildDateOnlyNode(date,getId(), getVersion());
      }
    }

    throw new RuntimeException("Unfinished code");
  }

  public void setEntryDate(Date entryDate) {
    this.entryDate = entryDate;
  }

  public Date getEntryDate() {
    return entryDate;
  }
}
