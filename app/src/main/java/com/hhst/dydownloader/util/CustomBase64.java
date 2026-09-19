package com.hhst.dydownloader.util;

/** Base64 with a caller-supplied alphabet, also available on Android 7. */
public final class CustomBase64 {
  private CustomBase64() {}

  public static String encode(byte[] data, String alphabet) {
    if (alphabet.length() != 64) {
      throw new IllegalArgumentException("Base64 alphabet must contain 64 characters");
    }
    StringBuilder out = new StringBuilder(((data.length + 2) / 3) * 4);
    for (int offset = 0; offset < data.length; offset += 3) {
      int remaining = data.length - offset;
      int block =
          ((data[offset] & 0xFF) << 16)
              | (remaining > 1 ? (data[offset + 1] & 0xFF) << 8 : 0)
              | (remaining > 2 ? data[offset + 2] & 0xFF : 0);
      out.append(alphabet.charAt((block >>> 18) & 0x3F));
      out.append(alphabet.charAt((block >>> 12) & 0x3F));
      out.append(remaining > 1 ? alphabet.charAt((block >>> 6) & 0x3F) : '=');
      out.append(remaining > 2 ? alphabet.charAt(block & 0x3F) : '=');
    }
    return out.toString();
  }
}
