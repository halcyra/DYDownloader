package com.hhst.dydownloader.cookies;

import com.hhst.dydownloader.model.Platform;

public record CookiePlatformConfig(
    Platform platform,
    String loginUrl,
    boolean forceDesktopUserAgent,
    boolean allowExternalAppRedirects) {

  public static CookiePlatformConfig forPlatform(Platform platform) {
    Platform safePlatform = platform == null ? Platform.DOUYIN : platform;
    return switch (safePlatform) {
      case DOUYIN ->
          new CookiePlatformConfig(
              Platform.DOUYIN, "https://www.douyin.com/user/self", true, false);
      case TIKTOK ->
          new CookiePlatformConfig(
              Platform.TIKTOK, "https://www.tiktok.com/login", false, true);
    };
  }
}
