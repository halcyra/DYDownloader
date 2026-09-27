package com.hhst.dydownloader.cookies;

import com.hhst.dydownloader.model.Platform;
import com.hhst.dydownloader.util.HostAllowList;
import java.util.List;

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
    return HostAllowList.matches(url, trustedHostSuffixes);
  }
}
