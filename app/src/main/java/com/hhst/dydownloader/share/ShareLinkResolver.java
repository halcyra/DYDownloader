package com.hhst.dydownloader.share;

import com.hhst.dydownloader.model.Platform;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShareLinkResolver {
  private static final Pattern URL_PATTERN =
      Pattern.compile(
          "(https?://[^\\s\"<>^`{|}\\uFF0C\\u3002\\uFF1B\\uFF01\\uFF1F\\u3001\\u3010\\u3011\\u300A\\u300B]+)");
  private static final String[] DOUYIN_HOST_SUFFIXES = {"douyin.com", "iesdouyin.com"};
  private static final String[] TIKTOK_HOST_SUFFIXES = {"tiktok.com"};

  private ShareLinkResolver() {}

  public static Result resolve(String text) {
    Optional<String> firstUrl = extractFirstUrl(text);
    if (firstUrl.isEmpty()) {
      return Result.unsupported();
    }
    String url = firstUrl.get();
    if (isHostInAllowList(url, TIKTOK_HOST_SUFFIXES)) {
      return new Result(Platform.TIKTOK, inferTikTokKind(url), url);
    }
    if (isHostInAllowList(url, DOUYIN_HOST_SUFFIXES)) {
      return new Result(Platform.DOUYIN, inferDouyinKind(url), url);
    }
    return Result.unsupported();
  }

  public static boolean containsSupportedLink(String text) {
    return resolve(text).supported();
  }

  private static Optional<String> extractFirstUrl(String text) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    Matcher matcher = URL_PATTERN.matcher(text);
    return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  private static boolean isHostInAllowList(String url, String[] hostSuffixes) {
    if (url == null || url.isBlank()) {
      return false;
    }
    try {
      String host = URI.create(url).getHost();
      if (host == null || host.isBlank()) {
        return false;
      }
      String normalizedHost = host.toLowerCase(Locale.ROOT);
      for (String suffix : hostSuffixes) {
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

  private static LinkKind inferDouyinKind(String url) {
    String normalized = normalize(url);
    if (normalized.contains("/collection/")
        || normalized.contains("mix_id=")
        || normalized.contains("mixid=")
        || normalized.contains("collectionid=")) {
      return LinkKind.MIX;
    }
    if (normalized.contains("/user/")
        || normalized.contains("sec_user_id=")
        || normalized.contains("secuid=")) {
      return LinkKind.ACCOUNT;
    }
    return LinkKind.WORK;
  }

  private static LinkKind inferTikTokKind(String url) {
    String normalized = normalize(url);
    if (normalized.contains("/collection/")
        || normalized.contains("/playlist/")
        || normalized.contains("collectionid=")
        || normalized.contains("playlistid=")) {
      return LinkKind.MIX;
    }
    if (normalized.contains("/@")
        && !normalized.contains("/video/")
        && !normalized.contains("/photo/")
        && !normalized.contains("/collection/")
        && !normalized.contains("/playlist/")) {
      return LinkKind.ACCOUNT;
    }
    return LinkKind.WORK;
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
