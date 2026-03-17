package com.hhst.dydownloader.downloader;

import java.net.URI;
import java.util.List;
import java.util.Locale;

public record PlatformRequestContext(
    String userAgent, String referer, String cookie, List<String> cookieHostSuffixes) {

  public PlatformRequestContext {
    userAgent = userAgent == null ? "" : userAgent.trim();
    referer = referer == null ? "" : referer.trim();
    cookie = cookie == null ? "" : cookie.trim();
    cookieHostSuffixes =
        cookieHostSuffixes == null ? List.of() : List.copyOf(cookieHostSuffixes);
  }

  public boolean shouldAttachCookie(String url) {
    if (cookie.isBlank() || url == null || url.isBlank()) {
      return false;
    }
    try {
      String host = URI.create(url.trim()).getHost();
      if (host == null || host.isBlank()) {
        return false;
      }
      String normalizedHost = host.toLowerCase(Locale.ROOT);
      for (String suffix : cookieHostSuffixes) {
        if (suffix == null || suffix.isBlank()) {
          continue;
        }
        String normalizedSuffix = suffix.toLowerCase(Locale.ROOT);
        if (normalizedHost.equals(normalizedSuffix)
            || normalizedHost.endsWith("." + normalizedSuffix)) {
          return true;
        }
      }
      return false;
    } catch (Exception ignored) {
      return false;
    }
  }
}
