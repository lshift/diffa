package net.lshift.diffa.versioning.plumbing;


import org.apache.commons.codec.binary.Hex;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Digester {

  private MessageDigest digest;
  private boolean sealed = false;

  public Digester() {
    try {
      digest = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  public void addVersion(String version) {
    if (sealed) {
      throw new RuntimeException("Digester has already been sealed and not yet been reset");
    }
    byte[] vsnBytes = version.getBytes();
    digest.update(vsnBytes, 0, vsnBytes.length);
  }

  public void reset() {
    digest.reset();
    sealed = false;
  }

  public String getDigest() {
    sealed = true;
    return new String(Hex.encodeHex(digest.digest()));
  }
}
