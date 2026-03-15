package com.hhst.dydownloader.util;

import java.util.Locale;

public final class MediaSourceUtils {
  private MediaSourceUtils() {}

  public static boolean isLikelyVideoSource(String source) {
    if (source == null || source.isBlank()) {
      return false;
    }
    String normalized = source.toLowerCase(Locale.ROOT);
    return normalized.contains(".mp4")
        || normalized.contains("video_mp4")
        || normalized.contains("mime_type=video")
        || normalized.contains("/play/")
        || normalized.contains("xgvideo");
  }
}
