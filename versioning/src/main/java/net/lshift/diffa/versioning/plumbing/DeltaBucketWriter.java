package net.lshift.diffa.versioning.plumbing;

import me.prettyprint.cassandra.serializers.StringSerializer;
import me.prettyprint.hector.api.beans.DynamicComposite;
import net.lshift.diffa.versioning.BatchMutator;
import net.lshift.diffa.versioning.EntityDifference;
import net.lshift.diffa.versioning.partitioning.MerkleNode;

public class DeltaBucketWriter extends AbstractBucketWriter {

  private EntityDifference diff;

  public DeltaBucketWriter(String bucketCF, BatchMutator mutator, EntityDifference diff) {
    super(bucketCF, mutator);
    this.diff = diff;
  }

  @Override
  public void write(String bucketName, MerkleNode node) {
    DynamicComposite composite = new DynamicComposite();
    composite.addComponent(diff.getLeft(), StringSerializer.get());
    composite.addComponent(diff.getRight(), StringSerializer.get());
    mutator.insertColumn(bucketName, bucketCF, node.getId(), composite);
  }
}
