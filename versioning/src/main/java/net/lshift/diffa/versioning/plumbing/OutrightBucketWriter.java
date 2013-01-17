package net.lshift.diffa.versioning.plumbing;


import net.lshift.diffa.versioning.BatchMutator;
import net.lshift.diffa.versioning.partitioning.MerkleNode;

public class OutrightBucketWriter extends AbstractBucketWriter {

  public OutrightBucketWriter(BatchMutator mutator, String bucketCF) {
    super(bucketCF, mutator);
  }

  @Override
  public void write(String bucketName, MerkleNode node) {
    mutator.insertColumn(bucketName, bucketCF, node.getId(), node.getVersion());
  }
}
