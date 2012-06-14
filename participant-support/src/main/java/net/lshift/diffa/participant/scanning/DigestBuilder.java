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
package net.lshift.diffa.participant.scanning;

import net.jcip.annotations.NotThreadSafe;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;


/**
 * Utility for building digests based on a series of entities.
 *
 * This caches each id that is added to it, in order to ensure that id re-ordering is detected.
 * The downside of the current implementation is that the id cache is not thread safe.
 *
 * The 2-argument constructor is present to support non-codepoint ordered
 * collations--eg: most locale-specific orderings will group upper-and lower
 * case letters together, eg:
 * [a b A B].sortBy(unicodeOrdering) -> [A a B b]
 * [a b A B].sortBy(asciiOrdering) -> [A B a b]
 *
 */
@NotThreadSafe
public class DigestBuilder {
  private final static Logger log = LoggerFactory.getLogger(DigestBuilder.class);
  private final Map<BucketKey, Bucket> digestBuckets;
  private final List<ScanAggregation> aggregations;
  // this should be a Comparator<String>, but both ICU and the JDK's Collators
  // implement Comparator<Object>. Even  though they both compare strings.
  // So much for Type safety.
  private final Collation collation;
  private String previousId = null;


  public DigestBuilder(List<ScanAggregation> aggregations) {

      this(aggregations, new AsciiCollation());
  }

  public DigestBuilder(List<ScanAggregation> aggregations, Collation collation) {
    this.aggregations = aggregations;
    this.digestBuckets = new HashMap<BucketKey, Bucket>();

    if (collation == null) {
      throw new NullPointerException("Collator is null");
    }
    this.collation = collation;
  }

  /**
   * Adds a scan result entry. Note that this entry is expected to be for an entity - ie, it should have it's ID
   * property present.
   * @param entry the entry to add.
   */
  public void add(ScanResultEntry entry) {
    add(entry.getId(), entry.getAttributes(), entry.getVersion());
  }

  /**
   * Adds a new version into the builder.
   * @param id the id of the entity being added
   * @param attributes the attributes of the entity being added. The builder expects that an attribute will be present
   *  for each of the aggregations specified.
   * @param vsn the version of the entity
   */
  public void add(String id, Map<String, String> attributes, String vsn) {
    log.trace("Adding to bucket: " + id + ", " + attributes + ", " + vsn);

    if (!isCorrectlyOrdered(id)) {
     throw new OutOfOrderException(previousId,id);
    }
    previousId = id;

    Map<String, String> partitions = new HashMap<String, String>();
    partitions.putAll(attributes);    // Default partitions to the initial attribute set
    for (ScanAggregation aggregation : aggregations) {
      String attrVal = attributes.get(aggregation.getAttributeName());
      if (attrVal == null) {
        throw new MissingAttributeException(id, aggregation.getAttributeName());
      }

      String bucket = aggregation.bucket(attrVal);
      partitions.put(aggregation.getAttributeName(), bucket);
    }

    BucketKey key = new BucketKey(partitions);
    Bucket bucket = digestBuckets.get(key);
    if (bucket == null) {
      bucket = new Bucket(key, partitions);
      digestBuckets.put(key, bucket);
    }

    bucket.add(vsn);
  }

    private boolean isCorrectlyOrdered(String id) {
      if (previousId != null) {
        return !collation.sortsBefore(id, previousId);
      } else {
        return true;
      }
    }

    public List<ScanResultEntry> toDigests() {
    List<ScanResultEntry> result = new ArrayList<ScanResultEntry>();
    for (Bucket bucket : digestBuckets.values()) {
      result.add(bucket.toDigest());
    }

    return result;
  }

  private static class BucketKey {
    private final Map<String, String> attributes;

    public BucketKey(Map<String, String> attributes) {
      this.attributes = attributes;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      BucketKey bucketKey = (BucketKey) o;

      if (attributes != null ? !attributes.equals(bucketKey.attributes) : bucketKey.attributes != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return attributes != null ? attributes.hashCode() : 0;
    }
  }

  private static class Bucket {
    private final BucketKey key;
    private final Map<String, String> attributes;
    private final String digestAlgorithm = "MD5";
    private final MessageDigest messageDigest;
    private String digest = null;

    public Bucket(BucketKey key, Map<String, String> attributes) {
      this.key = key;
      this.attributes = attributes;

      try {
        this.messageDigest = MessageDigest.getInstance(digestAlgorithm);
      } catch (NoSuchAlgorithmException ex) {
        throw new RuntimeException("MD5 digest algorithm no available");
      }
    }

    /**
     * Adds a version to be included the digest computation
     * @param vsn  The version string to add.
     * @throws SealedBucketException When the digest for the current builder instance has already been computed.
     */
    public void add(String vsn) {
      if (digest != null) {
        throw new SealedBucketException(vsn, getLabel());
      }
      byte[] vsnBytes = vsn.getBytes(Charset.forName("UTF-8"));
      messageDigest.update(vsnBytes, 0, vsnBytes.length);
    }

    public ScanResultEntry toDigest() {
      if (digest == null) {
        digest = new String(Hex.encodeHex(messageDigest.digest()));
      }

      return ScanResultEntry.forAggregate(digest, attributes);
    }

    public String getLabel() {
      StringBuilder labelBuilder = new StringBuilder();
      String[] keys = attributes.keySet().toArray(new String[attributes.size()]);
      Arrays.sort(keys);

      for(String key : keys) {
        if (labelBuilder.length() > 0) labelBuilder.append("_");
        labelBuilder.append(attributes.get(key));
      }

      return labelBuilder.toString();
    }
  }
}

