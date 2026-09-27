package com.hhst.dydownloader.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class SignatureDigest {
  private SignatureDigest() {}

  public static String md5Hex(byte[] input) {
    try {
      byte[] digest = MessageDigest.getInstance("MD5").digest(input);
      StringBuilder builder = new StringBuilder(digest.length * 2);
      for (byte value : digest) {
        builder.append(Character.forDigit((value >> 4) & 0xF, 16));
        builder.append(Character.forDigit(value & 0xF, 16));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("MD5 unavailable", e);
    }
  }
}
