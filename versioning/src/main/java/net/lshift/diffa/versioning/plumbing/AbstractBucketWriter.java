package net.lshift.diffa.versioning.plumbing;

import net.lshift.diffa.versioning.BatchMutator;
import net.lshift.diffa.versioning.partitioning.MerkleNode;

public abstract class AbstractBucketWriter implements BucketWriter {

  protected BatchMutator mutator;
  protected String bucketCF;

  public AbstractBucketWriter(String bucketCF, BatchMutator mutator) {
    this.bucketCF = bucketCF;
    this.mutator = mutator;
  }

  @Override
  public abstract void write(String bucketName, MerkleNode node);
}
