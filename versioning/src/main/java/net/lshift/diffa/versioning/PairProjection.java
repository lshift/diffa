package net.lshift.diffa.versioning;

public class PairProjection {

  private Long left, right;
  private String parent = null;
  private int maxSliceSize = 100;

  public PairProjection(Long left, Long right) {
    this(left, right, "");
  }

  public PairProjection(Long left, Long right, String parent) {
    this.parent = parent;
    this.left = left;
    this.right = right;
  }

  public String getContext() {
    return left + "." + right;
  }

  public String getParent() {
    return parent;
  }

  public int getMaxSliceSize() {
    return maxSliceSize;
  }

  public void setMaxSliceSize(int maxSliceSize) {
    this.maxSliceSize = maxSliceSize;
  }

  public Long getLeft() {
    return left;
  }

  public Long getRight() {
    return right;
  }
}
