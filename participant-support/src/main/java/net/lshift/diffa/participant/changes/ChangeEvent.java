/**
 * Copyright (C) 2010-2011 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.lshift.diffa.participant.changes;

import net.lshift.diffa.participant.common.MissingMandatoryFieldException;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.joda.time.DateTime;

import java.util.Map;

/**
 * Defines an event that is sent from a participant to the agent indicating that an internal entity has changed.
 */
public class ChangeEvent {
  private String id;
  private String version;
  private String parentVersion;
  private DateTime lastUpdated;
  private Map<String, String> attributes;

  /**
   * Constructs a ChangeEvent with no attributes when this change was initiated directly originally by this application.
   */
  public static ChangeEvent forChange(String id, String version, DateTime lastUpdated) {
    return new ChangeEvent(id, version, null, lastUpdated, null);
  }
  /**
   * Constructs a ChangeEvent with no attributes when this change was initiated directly originally by this application.
   */
  public static ChangeEvent forChange(String id, String version, DateTime lastUpdated, Map<String, String> attributes) {
    return new ChangeEvent(id, version, null, lastUpdated, attributes);
  }

  /**
   * Constructs a change event with no attributes for an event that was triggered by another system. The version provided
   * by the other system should be included if different to this system's version to allow for correlation.
   */
  public static ChangeEvent forTriggeredChange(String id, String version, String parentVersion, DateTime lastUpdated) {
    return new ChangeEvent(id, version, parentVersion, lastUpdated, null);
  }
  /**
   * Constructs a change event with attributes for an event that was triggered by another system. The version provided
   * by the other system should be included if different to this system's version to allow for correlation.
   */
  public static ChangeEvent forTriggeredChange(String id, String version, String parentVersion, DateTime lastUpdated, Map<String, String> attributes) {
    return new ChangeEvent(id, version, parentVersion, lastUpdated, attributes);
  }

  public ChangeEvent() {
    this(null, null, null, null, null);
  }

  public ChangeEvent(String id, String version, String parentVersion, DateTime lastUpdated, Map<String, String> attributes) {
    this.id = id;
    this.version = version;
    this.parentVersion = parentVersion;
    this.lastUpdated = lastUpdated;
    this.attributes = attributes;
  }

  @JsonSerialize(include = JsonSerialize.Inclusion.NON_DEFAULT)
  public String getId() {
    return id;
  }
  public void setId(String id) {
    this.id = id;
  }

  @JsonSerialize(include = JsonSerialize.Inclusion.NON_DEFAULT)
  public String getVersion() {
    return version;
  }
  public void setVersion(String version) {
    this.version = version;
  }

  @JsonSerialize(include = JsonSerialize.Inclusion.NON_DEFAULT)
  public String getParentVersion() {
    return parentVersion;
  }
  public void setParentVersion(String parentVersion) {
    this.parentVersion = parentVersion;
  }

  @JsonSerialize(include = JsonSerialize.Inclusion.NON_DEFAULT)
  public DateTime getLastUpdated() {
    return lastUpdated;
  }
  public void setLastUpdated(DateTime lastUpdated) {
    this.lastUpdated = lastUpdated;
  }

  @JsonSerialize(include = JsonSerialize.Inclusion.NON_DEFAULT)
  public Map<String, String> getAttributes() {
    return attributes;
  }
  public void setAttributes(Map<String, String> attributes) {
    this.attributes = attributes;
  }

  /**
   * Verifies that the mandatory fields are set.
   * @throws MissingMandatoryFieldException If a mandatory field is missing
   */
  public void ensureContainsMandatoryFields() {
    if (id == null || id.length() == 0) {
      throw new MissingMandatoryFieldException("id");
    }
    if (version == null || version.length() == 0) {
      throw new MissingMandatoryFieldException("version");
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ChangeEvent that = (ChangeEvent) o;

    if (attributes != null ? !attributes.equals(that.attributes) : that.attributes != null) return false;
    if (id != null ? !id.equals(that.id) : that.id != null) return false;
    if (lastUpdated != null ? !lastUpdated.equals(that.lastUpdated) : that.lastUpdated != null) return false;
    if (parentVersion != null ? !parentVersion.equals(that.parentVersion) : that.parentVersion != null) return false;
    if (version != null ? !version.equals(that.version) : that.version != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (version != null ? version.hashCode() : 0);
    result = 31 * result + (parentVersion != null ? parentVersion.hashCode() : 0);
    result = 31 * result + (lastUpdated != null ? lastUpdated.hashCode() : 0);
    result = 31 * result + (attributes != null ? attributes.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "ChangeEvent{" +
      "id='" + id + '\'' +
      ", version='" + version + '\'' +
      ", parentVersion='" + parentVersion + '\'' +
      ", lastUpdated=" + lastUpdated +
      ", attributes=" + attributes +
      '}';
  }
}
