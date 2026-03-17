package com.hhst.dydownloader.downloader;

import com.hhst.dydownloader.model.Platform;
import java.util.List;

public final class PlatformRequestContexts {
  private static final String WEB_USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
          + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36";

  private PlatformRequestContexts() {}

  public static PlatformRequestContext forPlatform(Platform platform, String cookie) {
    if (platform == Platform.TIKTOK) {
      return new PlatformRequestContext(
          WEB_USER_AGENT, "https://www.tiktok.com/explore", cookie, List.of("tiktok.com"));
    }
    return new PlatformRequestContext(
        WEB_USER_AGENT,
        "https://www.douyin.com/?recommend=1",
        cookie,
        List.of("douyin.com", "iesdouyin.com"));
  }
}
