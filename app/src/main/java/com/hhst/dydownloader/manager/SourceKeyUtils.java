package com.hhst.dydownloader.manager;

public final class SourceKeyUtils {
  private static final String DOUYIN_PREFIX = "DOUYIN:";
  private static final String TIKTOK_PREFIX = "TIKTOK:";
  private static final String PHOTO_MARKER = "#photo:";
  private static final String LIVE_MARKER = "#live:";

  private SourceKeyUtils() {}

  public static String normalize(String key) {
    return key == null ? "" : key.trim();
  }

  public static String baseOf(String key) {
    String normalized = rawSourceKey(key);
    if (normalized.isEmpty()) {
      return "";
    }
    int hashIndex = normalized.indexOf('#');
    String base = hashIndex >= 0 ? normalized.substring(0, hashIndex) : normalized;
    String prefix = platformPrefix(key);
    return prefix.isEmpty() ? base : prefix + base;
  }

  public static boolean isSameResource(String leftKey, String rightKey) {
    String left = normalize(leftKey);
    String right = normalize(rightKey);
    if (left.isEmpty() || right.isEmpty()) {
      return false;
    }
    if (left.equals(right)) {
      return true;
    }
    String leftPlatform = platformPrefix(left);
    String rightPlatform = platformPrefix(right);
    if (!leftPlatform.isEmpty()
        && !rightPlatform.isEmpty()
        && !leftPlatform.equals(rightPlatform)) {
      return false;
    }
    String leftBase = baseOf(left);
    String rightBase = baseOf(right);
    return !leftBase.isEmpty() && leftBase.equals(rightBase);
  }

  public static int photoIndex(String sourceKey) {
    return markerIndex(sourceKey, PHOTO_MARKER);
  }

  public static int liveIndex(String sourceKey) {
    return markerIndex(sourceKey, LIVE_MARKER);
  }

  public static int imageLeafIndex(String sourceKey) {
    int photo = photoIndex(sourceKey);
    if (photo >= 0) {
      return photo;
    }
    return liveIndex(sourceKey);
  }

  public static boolean hasImageLeafMarker(String sourceKey) {
    return photoIndex(sourceKey) >= 0 || liveIndex(sourceKey) >= 0;
  }

  public static int markerIndex(String sourceKey, String marker) {
    String normalizedSource = rawSourceKey(sourceKey);
    String normalizedMarker = normalize(marker);
    if (normalizedSource.isEmpty() || normalizedMarker.isEmpty()) {
      return -1;
    }
    int markerPosition = normalizedSource.indexOf(normalizedMarker);
    if (markerPosition < 0) {
      return -1;
    }
    try {
      return Integer.parseInt(
              normalizedSource.substring(markerPosition + normalizedMarker.length()))
          - 1;
    } catch (NumberFormatException ignored) {
      return -1;
    }
  }

  public static String rawSourceKey(String key) {
    String normalized = normalize(key);
    if (normalized.startsWith(DOUYIN_PREFIX)) {
      return normalized.substring(DOUYIN_PREFIX.length());
    }
    if (normalized.startsWith(TIKTOK_PREFIX)) {
      return normalized.substring(TIKTOK_PREFIX.length());
    }
    return normalized;
  }

  public static String platformPrefix(String key) {
    String normalized = normalize(key);
    if (normalized.startsWith(DOUYIN_PREFIX)) {
      return DOUYIN_PREFIX;
    }
    if (normalized.startsWith(TIKTOK_PREFIX)) {
      return TIKTOK_PREFIX;
    }
    return "";
  }
}
