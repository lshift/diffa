package net.lshift.diffa.versioning;

import net.lshift.diffa.versioning.partitioning.MerkleNode;
import net.lshift.diffa.versioning.partitioning.MerkleUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(Theories.class)
public class MerkleNodeTest {

  @DataPoint
  public static TestScenario entityId() {
    final String version = RandomStringUtils.randomAlphabetic(10);

    // MD5(baz) = 73feffa4b7f6bb68e44cf984c85f6e88

    MerkleNode leaf = new MerkleNode("f", "baz", version);
    MerkleNode mid = new MerkleNode("3", leaf);
    MerkleNode root = new MerkleNode("7", mid);
    return new TestScenario("baz", version, root);
  }

  @Theory
  public void shouldBuildEntityId(TestScenario scenario) {
    MerkleNode node = MerkleUtils.buildEntityIdNode(scenario.getId(), scenario.getVersion());
    assertEquals(scenario.getExpected(), node);
  }

  @Test
  public void shouldBuildHierarchyFromString() {

    MerkleNode hydrated = MerkleNode.buildHierarchy("ab.cd.ef");

    MerkleNode leaf = new MerkleNode("ef", null, null);
    MerkleNode middle = new MerkleNode("cd", leaf);
    MerkleNode top = new MerkleNode("ab", middle);
    MerkleNode expected = new MerkleNode("", top);

    assertEquals(expected, hydrated);
  }

  private static class TestScenario {
    private String id;
    private String version;
    private MerkleNode expected;

    public TestScenario(String id, String version, MerkleNode node) {
      this.id = id;
      this.version = version;
      this.expected = node;
    }

    public MerkleNode getExpected() {
      return expected;
    }

    public String getId() {
      return id;
    }

    public String getVersion() {
      return version;
    }
  }
}
