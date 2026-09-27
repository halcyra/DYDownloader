package com.hhst.dydownloader.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

public final class StoragePathUtils {
  public static final String PUBLIC_DOWNLOADS_DIRECTORY_NAME = "DYDownloader";
  public static final String DEFAULT_FILE_NAME_TEMPLATE = "{desc}";
  private static final int MAX_SEGMENT_LENGTH = 48;
  private static final int MAX_FILE_NAME_LENGTH = 96;
  private static final Pattern TEMPLATE_FIELD = Pattern.compile("\\{(author|desc|date|id)\\}");

  private StoragePathUtils() {}

  /**
   * 展开文件名模板，占位符：{author} 作者、{desc} 描述、{date} 日期、{id} 作品 ID。
   * 未知占位符原样保留；空占位符留下的连续分隔下划线会被折叠。
   */
  public static String expandFileNameTemplate(
      String template, String author, String desc, String date, String id) {
    String value =
        template == null || template.isBlank() ? DEFAULT_FILE_NAME_TEMPLATE : template.trim();
    Matcher matcher = TEMPLATE_FIELD.matcher(value);
    StringBuffer expanded = new StringBuffer();
    while (matcher.find()) {
      String replacement = switch (matcher.group(1)) {
        case "author" -> author;
        case "desc" -> desc;
        case "date" -> date;
        default -> id;
      };
      matcher.appendReplacement(expanded, Matcher.quoteReplacement(nullSafe(replacement)));
    }
    matcher.appendTail(expanded);
    return expanded.toString().replaceAll("_+", "_").replaceAll("^_+|_+$", "");
  }

  private static String nullSafe(String value) {
    return value == null ? "" : value.trim();
  }

  public static String joinSegments(String... segments) {
    if (segments == null || segments.length == 0) {
      return "";
    }
    List<String> normalizedSegments = new ArrayList<>();
    for (String segment : segments) {
      String normalized = sanitizeSegment(segment, "");
      if (!normalized.isBlank()) {
        normalizedSegments.add(normalized);
      }
    }
    return String.join("/", normalizedSegments);
  }

  public static String normalizeRelativeDir(String relativeDir) {
    if (relativeDir == null || relativeDir.isBlank()) return "";
    return joinSegments(relativeDir.replace('\\', '/').split("/"));
  }

  public static String sanitizeSegment(String raw, String fallback) {
    return shortenWithHash(normalizeName(raw, fallback), MAX_SEGMENT_LENGTH);
  }

  public static String sanitizeFileName(String raw, String fallback) {
    String value = normalizeName(raw, fallback);
    int extensionIndex = value.lastIndexOf('.');
    String extension =
        extensionIndex > 0 && extensionIndex < value.length() - 1
            ? value.substring(extensionIndex)
            : "";
    String baseName = extension.isEmpty() ? value : value.substring(0, extensionIndex);
    int maxBaseLength = Math.max(12, MAX_FILE_NAME_LENGTH - extension.length());
    baseName = shortenWithHash(baseName, maxBaseLength);
    return (baseName + extension).trim();
  }

  private static String normalizeName(String raw, String fallback) {
    String value = cleanName(raw);
    return value.isEmpty() ? cleanName(fallback) : value;
  }

  private static String cleanName(String raw) {
    String value = nullSafe(raw)
        .replaceAll("[\\\\/:*?\"<>|]", " ")
        .replaceAll("\\p{Cntrl}", " ")
        .replaceAll("\\s+", " ")
        .trim();
    while (value.startsWith(".")) value = value.substring(1).trim();
    while (value.endsWith(".")) value = value.substring(0, value.length() - 1).trim();
    return value;
  }

  public static String stableToken(String raw) {
    CRC32 crc32 = new CRC32();
    byte[] bytes = (raw == null ? "" : raw).getBytes(StandardCharsets.UTF_8);
    crc32.update(bytes, 0, bytes.length);
    return String.format(java.util.Locale.ROOT, "%08x", crc32.getValue());
  }

  public static String shortenWithHash(String value, int maxLength) {
    if (value == null) {
      return "";
    }
    String normalized = value.trim();
    if (normalized.length() <= maxLength || maxLength <= 0) {
      return normalized;
    }
    String suffix = "_" + stableToken(normalized);
    int prefixLength = Math.max(12, maxLength - suffix.length());
    if (prefixLength >= normalized.length()) {
      return normalized;
    }
    String prefix = normalized.substring(0, prefixLength).trim();
    return prefix.isEmpty() ? suffix.substring(1) : prefix + suffix;
  }
}
