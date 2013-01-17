package net.lshift.diffa.versioning.partitioning;

public class MerkleNode {

  private MerkleNode child;
  private String id;
  private String version;
  private String name;
  private boolean isLeaf = false;

  public static MerkleNode buildHierarchy(String path) {
    MerkleNode root = new MerkleNode("");

    String[] segments = path.split("\\.");

    if (segments != null && segments.length > 0) {

      MerkleNode currentNode = root;

      for (String segment : segments) {
        MerkleNode node = new MerkleNode(segment);
        currentNode.setChild(node);
        currentNode = node;
      }

      currentNode.isLeaf = true;
    }


    return root;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MerkleNode that = (MerkleNode) o;

    if (isLeaf != that.isLeaf) return false;
    if (child != null ? !child.equals(that.child) : that.child != null) return false;
    if (id != null ? !id.equals(that.id) : that.id != null) return false;
    if (name != null ? !name.equals(that.name) : that.name != null) return false;
    if (version != null ? !version.equals(that.version) : that.version != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = child != null ? child.hashCode() : 0;
    result = 31 * result + (id != null ? id.hashCode() : 0);
    result = 31 * result + (version != null ? version.hashCode() : 0);
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (isLeaf ? 1 : 0);
    return result;
  }

  public MerkleNode(String name) {
    this.name = name;
  }

  public MerkleNode(String name, MerkleNode child) {
    this.name = name;
    this.child = child;
  }

  public MerkleNode(String name, String id, String version) {
    this.name = name;
    this.id = id;
    this.version = version;
    isLeaf = true;
  }

  // TODO quick and dirty path construction .....
  public String getDescendencyPath() {
    if (isLeaf()) {
      return getName();
    }
    else {
      return getName() + "." + getChild().getDescendencyPath();
    }
  }

  public MerkleNode getChild() {
    return child;
  }

  public void setChild(MerkleNode child) {
    this.child = child;
  }

  public boolean isLeaf() {
    return isLeaf;
  }

  public void setLeaf(boolean leaf) {
    isLeaf = leaf;
  }

  public String getName() {
    return name;
  }

  public String getVersion() {
    return version;
  }

  public String getId() {
    return id;
  }

}
