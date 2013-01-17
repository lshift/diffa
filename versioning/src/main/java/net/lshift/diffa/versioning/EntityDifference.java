package net.lshift.diffa.versioning;

public class EntityDifference {
  private String id;
  private String left;
  private String right;

  public EntityDifference(String id, String left, String right) {
    this.id = id;
    this.left = left;
    this.right = right;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getLeft() {
    return left;
  }

  public void setLeft(String left) {
    this.left = left;
  }

  public String getRight() {
    return right;
  }

  public void setRight(String right) {
    this.right = right;
  }

  public boolean isDifferent() {

    if (left == null && right == null) {
      return false;
    }
    else if (left != null && right == null) {
      return true;
    }
    else if (left == null && right != null) {
      return true;
    }
    else {
      return !left.equals(right);
    }

  }
}
