package com.hhst.dydownloader.douyin;

import static com.hhst.dydownloader.util.SignatureDigest.md5Hex;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * WebSign implementation based on DouK-Downloader's secsdk runtime_bundler_34.js.
 *
 * <p>The signature covers the normalized query, including a_bogus, uifid and timestamp.
 */
public final class WebSignGenerator {
  private static final String SIGNATURE_PARAM = "x-secsdk-web-signature";
  private static final String UIFID_PARAM = "uifid";
  private static final String TIMESTAMP_PARAM = "timestamp";

  private static final String SALT = "A96D855A08C0A9707F8BEF0D9A527E4E";

  // Python quote 的 safe='*-._'：WebSign 原生百分号编码的额外安全字符
  private static final String NATIVE_SAFE = "*-._";

  private WebSignGenerator() {}

  public static String sign(String query, String uifid, long timestampSeconds) {
    String stamp = String.valueOf(timestampSeconds);
    List<String[]> pairs = decodePairs(query);
    boolean hasUifid = false;
    for (String[] pair : pairs) {
      if (UIFID_PARAM.equals(pair[0])) {
        hasUifid = true;
        break;
      }
    }
    if (!hasUifid) {
      pairs.add(new String[] {UIFID_PARAM, uifid});
    }
    pairs.add(new String[] {TIMESTAMP_PARAM, stamp});
    String hashed = encodePairs(pairs);
    String signature = md5Hex(utf8(uifid + "_" + stamp + "_" + SALT + "_" + hashed));
    return hashed + "&" + SIGNATURE_PARAM + "=" + signature;
  }

  /** 返回 WebSign 的规范 query 字节序：解码后按其原生百分号编码规则重编码。 */
  public static String normalizeQuery(String query) {
    return encodePairs(decodePairs(query));
  }

  private static List<String[]> decodePairs(String query) {
    List<String[]> pairs = new ArrayList<>();
    for (String part : query.split("&")) {
      if (part.isEmpty()) {
        continue;
      }
      int separator = part.indexOf('=');
      String name = separator >= 0 ? part.substring(0, separator) : part;
      String value = separator >= 0 ? part.substring(separator + 1) : "";
      pairs.add(new String[] {unquote(name), unquote(value)});
    }
    return pairs;
  }

  private static String encodePairs(List<String[]> pairs) {
    StringBuilder builder = new StringBuilder();
    for (String[] pair : pairs) {
      if (builder.length() > 0) {
        builder.append('&');
      }
      builder.append(quote(pair[0], NATIVE_SAFE)).append('=').append(quote(pair[1], NATIVE_SAFE));
    }
    return builder.toString();
  }

  /** Python quote 的等价实现：安全字符 = [A-Za-z0-9_.-~] 加上调用方指定的 safe。 */
  static String quote(String text, String safe) {
    StringBuilder builder = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char current = text.charAt(i);
      if (isAlwaysSafe(current) || safe.indexOf(current) >= 0) {
        builder.append(current);
      } else if (current < 0x80) {
        appendPercentByte(builder, current);
      } else {
        // 代理对按整体取 UTF-8 字节，避免低位代理被单独编码
        String unit = String.valueOf(current);
        if (Character.isHighSurrogate(current)
            && i + 1 < text.length()
            && Character.isLowSurrogate(text.charAt(i + 1))) {
          unit = text.substring(i, i + 2);
          i++;
        }
        for (byte value : unit.getBytes(StandardCharsets.UTF_8)) {
          appendPercentByte(builder, value & 0xFF);
        }
      }
    }
    return builder.toString();
  }

  private static boolean isAlwaysSafe(char current) {
    return (current >= 'a' && current <= 'z')
        || (current >= 'A' && current <= 'Z')
        || (current >= '0' && current <= '9')
        || current == '_'
        || current == '.'
        || current == '-'
        || current == '~';
  }

  /** 解码百分号编码字符串，同时保留字面量 "+"（unquote 而非 unquote_plus）。 */
  static String unquote(String text) {
    if (text.indexOf('%') < 0) {
      return text;
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    for (int i = 0; i < text.length(); i++) {
      char current = text.charAt(i);
      if (current == '%' && i + 2 < text.length()) {
        int high = Character.digit(text.charAt(i + 1), 16);
        int low = Character.digit(text.charAt(i + 2), 16);
        if (high >= 0 && low >= 0) {
          bytes.write((high << 4) | low);
          i += 2;
          continue;
        }
      }
      byte[] encoded = String.valueOf(current).getBytes(StandardCharsets.UTF_8);
      bytes.write(encoded, 0, encoded.length);
    }
    return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
  }

  private static void appendPercentByte(StringBuilder builder, int value) {
    builder.append('%');
    builder.append(Character.toUpperCase(Character.forDigit((value >> 4) & 0xF, 16)));
    builder.append(Character.toUpperCase(Character.forDigit(value & 0xF, 16)));
  }

  private static byte[] utf8(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }
}
