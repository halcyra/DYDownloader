package com.hhst.dydownloader.downloader;

import com.hhst.dydownloader.util.HostAllowList;
import java.util.List;

public record PlatformRequestContext(
    String userAgent, String referer, String cookie, List<String> cookieHostSuffixes) {

  public PlatformRequestContext {
    userAgent = userAgent == null ? "" : userAgent.trim();
    referer = referer == null ? "" : referer.trim();
    cookie = cookie == null ? "" : cookie.trim();
    cookieHostSuffixes = cookieHostSuffixes == null ? List.of() : List.copyOf(cookieHostSuffixes);
  }

  public boolean shouldAttachCookie(String url) {
    return !cookie.isBlank() && HostAllowList.matches(url, cookieHostSuffixes);
  }
}
