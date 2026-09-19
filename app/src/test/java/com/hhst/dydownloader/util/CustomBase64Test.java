package com.hhst.dydownloader.util;

import static org.junit.Assert.assertEquals;

import java.util.Base64;
import java.util.Random;
import org.junit.Test;

public class CustomBase64Test {
  @Test
  public void encode_matchesReferenceForEveryPaddingLengthAndByteValue() {
    String standard = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    String custom = "u09tbS3UvgDEe6r-ZVMXzLpsAohTn7mdINQlW412GqBjfYiyk8JORCF5/xKHwacP";
    Random random = new Random(42);
    for (int length = 0; length < 260; length++) {
      byte[] bytes = new byte[length];
      random.nextBytes(bytes);
      String expected = Base64.getEncoder().encodeToString(bytes);
      assertEquals(expected, CustomBase64.encode(bytes, standard));
      StringBuilder mapped = new StringBuilder();
      for (char c : expected.toCharArray()) {
        mapped.append(c == '=' ? '=' : custom.charAt(standard.indexOf(c)));
      }
      assertEquals(mapped.toString(), CustomBase64.encode(bytes, custom));
    }
  }
}
