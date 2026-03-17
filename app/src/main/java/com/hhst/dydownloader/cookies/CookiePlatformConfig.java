package com.hhst.dydownloader.cookies;

import com.hhst.dydownloader.model.Platform;
import java.net.URI;
import java.util.List;
import java.util.Locale;

public record CookiePlatformConfig(
    Platform platform,
    String loginUrl,
    String cookieUrl,
    boolean forceDesktopUserAgent,
    boolean allowExternalAppRedirects,
    List<String> trustedHostSuffixes) {

  public CookiePlatformConfig {
    platform = platform == null ? Platform.DOUYIN : platform;
    trustedHostSuffixes =
        trustedHostSuffixes == null ? List.of() : List.copyOf(trustedHostSuffixes);
  }

  public static CookiePlatformConfig forPlatform(Platform platform) {
    Platform safePlatform = platform == null ? Platform.DOUYIN : platform;
    return switch (safePlatform) {
      case DOUYIN ->
          new CookiePlatformConfig(
              Platform.DOUYIN,
              "https://www.douyin.com/user/self",
              "https://www.douyin.com/",
              true,
              false,
              List.of("douyin.com", "iesdouyin.com"));
      case TIKTOK ->
          new CookiePlatformConfig(
              Platform.TIKTOK,
              "https://www.tiktok.com/login",
              "https://www.tiktok.com/",
              false,
              true,
              List.of("tiktok.com"));
    };
  }

  public boolean isTrustedWebUrl(String url) {
    if (url == null || url.isBlank()) {
      return false;
    }
    try {
      String host = URI.create(url).getHost();
      if (host == null || host.isBlank()) {
        return false;
      }
      String normalizedHost = host.toLowerCase(Locale.ROOT);
      for (String suffix : trustedHostSuffixes) {
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
