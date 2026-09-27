package com.hhst.dydownloader.share;

import com.hhst.dydownloader.model.Platform;
import com.hhst.dydownloader.util.HostAllowList;
import com.hhst.dydownloader.util.ShareUrls;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ShareLinkResolver {
  private static final List<String> DOUYIN_HOST_SUFFIXES = List.of("douyin.com", "iesdouyin.com");
  private static final List<String> TIKTOK_HOST_SUFFIXES = List.of("tiktok.com");

  private ShareLinkResolver() {}

  public static Result resolve(String text) {
    Optional<String> firstUrl = ShareUrls.firstUrl(text);
    if (firstUrl.isEmpty()) {
      return Result.unsupported();
    }
    String url = firstUrl.get();
    if (HostAllowList.matches(url, TIKTOK_HOST_SUFFIXES)) {
      LinkKind kind = inferTikTokKind(url);
      return kind == LinkKind.UNKNOWN ? Result.unsupported() : new Result(Platform.TIKTOK, kind, url);
    }
    if (HostAllowList.matches(url, DOUYIN_HOST_SUFFIXES)) {
      LinkKind kind = inferDouyinKind(url);
      return kind == LinkKind.UNKNOWN ? Result.unsupported() : new Result(Platform.DOUYIN, kind, url);
    }
    return Result.unsupported();
  }

  public static boolean containsSupportedLink(String text) {
    return resolve(text).supported();
  }

  private static LinkKind inferDouyinKind(String url) {
    URI uri = URI.create(url);
    String host = normalize(uri.getHost());
    String path = normalize(uri.getPath());
    String query = normalize(uri.getRawQuery());
    if (path.matches("^/(?:video|note|share/video|share/note)/\\d{19}(?:/.*)?$")
        || hasQueryValueMatching(query, "\\d{19}", "modal_id", "aweme_id")) {
      return LinkKind.WORK;
    }
    if (path.matches("^/(?:share/)?collection/\\d{5,25}/?$")
        || hasNumericQueryValue(query, "mix_id", "mixid", "collectionid")) {
      return LinkKind.MIX;
    }
    if (path.matches("^/(?:share/)?user/[^/]+/?$")
        || hasQueryValue(query, "sec_user_id", "secuid")) {
      return LinkKind.ACCOUNT;
    }
    if (host.equals("v.douyin.com") && path.length() > 1) {
      return LinkKind.WORK;
    }
    return LinkKind.UNKNOWN;
  }

  private static LinkKind inferTikTokKind(String url) {
    URI uri = URI.create(url);
    String host = normalize(uri.getHost());
    String path = normalize(uri.getPath());
    String query = normalize(uri.getRawQuery());
    if (path.matches("^/@[^/]+/(?:collection|playlist)/.+")
        || hasNumericQueryValue(query, "collectionid", "playlistid")) {
      return LinkKind.MIX;
    }
    if (path.matches("^/@[^/]+/?$")) {
      return LinkKind.ACCOUNT;
    }
    if (((host.equals("vm.tiktok.com") || host.equals("vt.tiktok.com")) && path.length() > 1)
        || path.matches("^/t/[^/]+/?$")
        || path.matches("^/@[^/]+/(?:video|photo)/\\d{19}(?:/.*)?$")) {
      return LinkKind.WORK;
    }
    return LinkKind.UNKNOWN;
  }

  private static boolean hasNumericQueryValue(String query, String... names) {
    return hasQueryValueMatching(query, "\\d{5,25}", names);
  }

  private static boolean hasQueryValue(String query, String... names) {
    return hasQueryValueMatching(query, "[^&]+", names);
  }

  private static boolean hasQueryValueMatching(String query, String valuePattern, String... names) {
    if (query.isBlank()) return false;
    for (String name : names) {
      if (Pattern.compile("(?:^|&)" + name + "=" + valuePattern + "(?:&|$)")
          .matcher(query)
          .find()) return true;
    }
    return false;
  }

  private static String normalize(String text) {
    return text == null ? "" : text.toLowerCase(Locale.ROOT);
  }

  public enum LinkKind {
    WORK,
    ACCOUNT,
    MIX,
    UNKNOWN
  }

  public record Result(Platform platform, LinkKind kind, String url) {
    public static Result unsupported() {
      return new Result(null, LinkKind.UNKNOWN, "");
    }

    public boolean supported() {
      return platform != null && kind != LinkKind.UNKNOWN && url != null && !url.isBlank();
    }
  }
}
