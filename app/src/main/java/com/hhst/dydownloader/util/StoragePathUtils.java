package com.hhst.dydownloader.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

public final class StoragePathUtils {
  public static final String PUBLIC_DOWNLOADS_DISPLAY_ROOT = "Download/DYDownloader";
  public static final String PUBLIC_DOWNLOADS_DIRECTORY_NAME = "DYDownloader";
  private static final int MAX_SEGMENT_LENGTH = 48;
  private static final int MAX_FILE_NAME_LENGTH = 96;

  private StoragePathUtils() {}

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

  public static String sanitizeSegment(String raw, String fallback) {
    String fallbackValue = fallback == null ? "" : fallback.trim();
    String value = raw == null ? "" : raw.trim();
    if (value.isEmpty()) {
      value = fallbackValue;
    }
    value =
        value
            .replaceAll("[\\\\/:*?\"<>|]", " ")
            .replaceAll("\\p{Cntrl}", " ")
            .replaceAll("\\s+", " ")
            .trim();
    while (value.startsWith(".")) {
      value = value.substring(1).trim();
    }
    while (value.endsWith(".")) {
      value = value.substring(0, value.length() - 1).trim();
    }
    value = shortenWithHash(value, MAX_SEGMENT_LENGTH);
    if (value.isEmpty()) {
      value = fallbackValue;
    }
    return value.trim();
  }

  public static String sanitizeFileName(String raw, String fallback) {
    String fallbackValue = fallback == null ? "" : fallback.trim();
    String value = raw == null ? "" : raw.trim();
    if (value.isEmpty()) {
      value = fallbackValue;
    }
    value =
        value
            .replaceAll("[\\\\/:*?\"<>|]", " ")
            .replaceAll("\\p{Cntrl}", " ")
            .replaceAll("\\s+", " ")
            .trim();
    while (value.startsWith(".")) {
      value = value.substring(1).trim();
    }
    while (value.endsWith(".")) {
      value = value.substring(0, value.length() - 1).trim();
    }
    int extensionIndex = value.lastIndexOf('.');
    String extension =
        extensionIndex > 0 && extensionIndex < value.length() - 1
            ? value.substring(extensionIndex)
            : "";
    String baseName = extension.isEmpty() ? value : value.substring(0, extensionIndex);
    int maxBaseLength = Math.max(12, MAX_FILE_NAME_LENGTH - extension.length());
    baseName = shortenWithHash(baseName, maxBaseLength);
    String fileName = (baseName + extension).trim();
    if (fileName.isEmpty()) {
      fileName = shortenWithHash(fallbackValue, MAX_FILE_NAME_LENGTH);
    }
    return fileName;
  }

  public static String buildPublicDownloadDisplayPath(String relativeDir) {
    String normalized = normalizeRelativePath(relativeDir);
    if (normalized.isBlank()) {
      return PUBLIC_DOWNLOADS_DISPLAY_ROOT;
    }
    return PUBLIC_DOWNLOADS_DISPLAY_ROOT + "/" + normalized;
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

  private static String normalizeRelativePath(String relativeDir) {
    if (relativeDir == null || relativeDir.isBlank()) {
      return "";
    }
    String[] parts = relativeDir.replace('\\', '/').split("/");
    return joinSegments(parts);
  }
}
