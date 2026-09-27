package com.hhst.dydownloader.util;

import java.net.URI;
import java.util.Collection;
import java.util.Locale;

public final class HostAllowList {
  private HostAllowList() {}

  public static boolean matches(String url, Collection<String> suffixes) {
    if (url == null || url.isBlank()) return false;
    try {
      String host = URI.create(url.trim()).getHost();
      if (host == null || host.isBlank()) return false;
      String normalizedHost = host.toLowerCase(Locale.ROOT);
      for (String suffix : suffixes) {
        if (suffix == null || suffix.isBlank()) continue;
        String normalizedSuffix = suffix.toLowerCase(Locale.ROOT);
        if (normalizedHost.equals(normalizedSuffix)
            || normalizedHost.endsWith("." + normalizedSuffix)) {
          return true;
        }
      }
      return false;
    } catch (IllegalArgumentException ignored) {
      return false;
    }
  }
}
