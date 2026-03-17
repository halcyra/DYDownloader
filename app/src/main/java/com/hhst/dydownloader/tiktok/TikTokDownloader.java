package com.hhst.dydownloader.tiktok;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhst.dydownloader.douyin.AwemeProfile;
import com.hhst.dydownloader.douyin.MediaType;
import com.hhst.dydownloader.model.Platform;
import com.hhst.dydownloader.tiktok.exception.TikTokDetailFetchException;
import com.hhst.dydownloader.tiktok.exception.TikTokDownloaderException;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class TikTokDownloader {
  public static final Pattern URL_PATTERN =
      Pattern.compile(
          "(https?://[^\\s\"<>^`{|}\\uFF0C\\u3002\\uFF1B\\uFF01\\uFF1F\\u3001\\u3010\\u3011\\u300A\\u300B]+)");
  private static final String DETAIL_API = "https://www.tiktok.com/api/item/detail/";
  private static final String[] TRUSTED_SHARE_HOSTS = {"tiktok.com"};
  private static final String DEFAULT_USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
          + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36";
  private static final String DEFAULT_REFERER = "https://www.tiktok.com/explore";

  private final OkHttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final TikTokRequestSigner requestSigner;
  private final TikTokDeviceIdResolver deviceIdResolver;
  private final String defaultCookie;
  private volatile String cachedDeviceId = "";
  private volatile String cachedCookieAdditions = "";

  public TikTokDownloader() {
    this("");
  }

  public TikTokDownloader(String cookie) {
    this(defaultClient(), cookie);
  }

  public TikTokDownloader(OkHttpClient httpClient, String cookie) {
    this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    this.objectMapper = new ObjectMapper();
    this.requestSigner = new TikTokRequestSigner();
    this.deviceIdResolver = new TikTokDeviceIdResolver(httpClient);
    this.defaultCookie = normalizeCookie(cookie);
  }

  public static boolean containsTikTokLink(String text) {
    return extractSupportedShareUrl(text).isPresent();
  }

  public static boolean isTrustedShareUrl(String url) {
    return isHostInAllowList(url, TRUSTED_SHARE_HOSTS);
  }

  public static boolean isAccountLink(String text) {
    if (text == null) {
      return false;
    }
    String normalized = text.toLowerCase(Locale.ROOT);
    return normalized.contains("tiktok.com/@")
        && !normalized.contains("/video/")
        && !normalized.contains("/photo/")
        && !normalized.contains("/collection/")
        && !normalized.contains("/playlist/");
  }

  public static boolean isMixLink(String text) {
    if (text == null) {
      return false;
    }
    String normalized = text.toLowerCase(Locale.ROOT);
    return normalized.contains("/collection/") || normalized.contains("/playlist/");
  }

  public AwemeProfile collectWorkInfo(String shareLink) throws TikTokDownloaderException {
    return collectWorkInfo(shareLink, defaultCookie);
  }

  public AwemeProfile collectWorkInfo(String shareLink, String cookie)
      throws TikTokDownloaderException {
    if (shareLink == null || shareLink.trim().isEmpty()) {
      throw new IllegalArgumentException("shareLink is blank");
    }
    ResolvedShareLink resolved = resolveShareLink(shareLink, cookie);
    DeviceCookieContext deviceContext = ensureDeviceId(resolved.normalizedCookie());
    String itemId =
        TikTokLinkParser.extractItemId(resolved.resolvedUrl())
            .or(() -> TikTokLinkParser.extractItemId(shareLink))
            .orElseThrow(
                () -> new IllegalArgumentException("Cannot extract itemId from shareLink"));
    JsonNode item = fetchItemDetail(itemId, deviceContext.cookie());
    return mapItemToProfile(item);
  }

  private static OkHttpClient defaultClient() {
    return new OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build();
  }

  private static Optional<String> extractSupportedShareUrl(String text) {
    Optional<String> firstUrl = extractFirstUrl(text);
    if (firstUrl.isEmpty()) {
      return Optional.empty();
    }
    return isTrustedShareUrl(firstUrl.get()) ? firstUrl : Optional.empty();
  }

  private static Optional<String> extractFirstUrl(String text) {
    if (text == null || text.trim().isEmpty()) {
      return Optional.empty();
    }
    Matcher matcher = URL_PATTERN.matcher(text);
    return matcher.find() ? Optional.ofNullable(matcher.group(1)) : Optional.empty();
  }

  private static boolean isHostInAllowList(String url, String[] allowList) {
    if (url == null || url.isBlank()) {
      return false;
    }
    try {
      URI uri = URI.create(url.trim());
      String host = uri.getHost();
      if (host == null || host.isBlank()) {
        return false;
      }
      String normalizedHost = host.toLowerCase(Locale.ROOT);
      for (String allowedHost : allowList) {
        if (normalizedHost.equals(allowedHost) || normalizedHost.endsWith("." + allowedHost)) {
          return true;
        }
      }
      return false;
    } catch (Exception ignored) {
      return false;
    }
  }

  private ResolvedShareLink resolveShareLink(String shareLink, String cookie) {
    String normalizedCookie = normalizeCookie(cookie);
    Optional<String> extractedUrl = extractFirstUrl(shareLink);
    if (extractedUrl.isPresent() && !isTrustedShareUrl(extractedUrl.get())) {
      throw new IllegalArgumentException("Unsupported share link host");
    }
    String rawUrl = extractedUrl.orElse(shareLink.trim());
    String resolvedUrl = resolveFinalUrl(rawUrl, normalizedCookie);
    if (extractFirstUrl(resolvedUrl).isPresent() && !isTrustedShareUrl(resolvedUrl)) {
      throw new IllegalArgumentException("Unsupported resolved share link host");
    }
    return new ResolvedShareLink(normalizedCookie, resolvedUrl);
  }

  private String resolveFinalUrl(String url, String cookie) {
    try {
      Request request =
          requestBuilder(url, cookie)
              .get()
              .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
              .build();
      try (Response response = httpClient.newCall(request).execute()) {
        return response.request().url().toString();
      }
    } catch (Exception ignored) {
      return url;
    }
  }

  private DeviceCookieContext ensureDeviceId(String cookie) throws TikTokDetailFetchException {
    String normalizedCookie = normalizeCookie(cookie);
    String mergedCookie = mergeCookies(normalizedCookie, cachedCookieAdditions);
    String deviceId = cachedDeviceId;
    if (!deviceId.isBlank()) {
      return new DeviceCookieContext(deviceId, mergedCookie);
    }
    try {
      TikTokDeviceIdResolver.Result result = deviceIdResolver.resolve(DEFAULT_USER_AGENT, mergedCookie);
      cachedDeviceId = result.deviceId() == null ? "" : result.deviceId().trim();
      cachedCookieAdditions =
          result.cookieAdditions() == null ? "" : result.cookieAdditions().trim();
      mergedCookie = mergeCookies(mergedCookie, cachedCookieAdditions);
      if (cachedDeviceId.isBlank()) {
        throw new TikTokDetailFetchException("Cannot resolve TikTok device_id");
      }
      return new DeviceCookieContext(cachedDeviceId, mergedCookie);
    } catch (IOException e) {
      throw new TikTokDetailFetchException("Failed to resolve TikTok device_id", e);
    }
  }

  private JsonNode fetchItemDetail(String itemId, String cookie) throws TikTokDetailFetchException {
    String msToken = extractCookieField(cookie, "msToken");
    Map<String, String> params = buildBaseParams();
    params.put("itemId", itemId);
    String query = requestSigner.sign(params, DEFAULT_USER_AGENT, cachedDeviceId, msToken);
    String apiUrl = DETAIL_API + "?" + query;

    try {
      Request request =
          requestBuilder(apiUrl, cookie)
              .get()
              .header("Accept", "application/json, text/plain, */*")
              .build();
      try (Response response = httpClient.newCall(request).execute()) {
        ResponseBody body = response.body();
        String payload = body != null ? body.string() : "";
        JsonNode root = objectMapper.readTree(payload);
        JsonNode item = root.path("itemInfo").path("itemStruct");
        if (item.isMissingNode() || item.isNull() || item.isEmpty()) {
          throw new TikTokDetailFetchException("TikTok item detail payload is empty");
        }
        return item;
      }
    } catch (IOException | RuntimeException e) {
      throw new TikTokDetailFetchException("Failed to fetch TikTok item detail", e);
    }
  }

  private Map<String, String> buildBaseParams() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("WebIdLastTime", String.valueOf(System.currentTimeMillis() / 1000L));
    params.put("aid", "1988");
    params.put("app_language", "en");
    params.put("app_name", "tiktok_web");
    params.put("browser_language", "zh-SG");
    params.put("browser_name", "Mozilla");
    params.put("browser_online", "true");
    params.put("browser_platform", "Win32");
    params.put("browser_version", DEFAULT_USER_AGENT);
    params.put("channel", "tiktok_web");
    params.put("cookie_enabled", "true");
    params.put("data_collection_enabled", "true");
    params.put("device_platform", "web_pc");
    params.put("enable_cache", "true");
    params.put("focus_state", "true");
    params.put("from_page", "user");
    params.put("history_len", "4");
    params.put("is_fullscreen", "false");
    params.put("is_page_visible", "true");
    params.put("language", "en");
    params.put("os", "windows");
    params.put("priority_region", "US");
    params.put("referer", "");
    params.put("region", "US");
    params.put("screen_height", "864");
    params.put("screen_width", "1536");
    params.put("tz_name", "Asia/Shanghai");
    params.put("user_is_login", "true");
    params.put("webcast_language", "en");
    return params;
  }

  private Request.Builder requestBuilder(String url, String cookie) {
    Request.Builder builder =
        new Request.Builder()
            .url(url)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Referer", DEFAULT_REFERER)
            .header("Accept-Encoding", "*/*");
    String normalizedCookie = normalizeCookie(cookie);
    if (!normalizedCookie.isBlank() && isTrustedShareUrl(url)) {
      builder.header("Cookie", normalizedCookie);
    }
    return builder;
  }

  private AwemeProfile mapItemToProfile(JsonNode item) throws TikTokDetailFetchException {
    String awemeId = item.path("id").asText("").trim();
    if (awemeId.isEmpty()) {
      throw new TikTokDetailFetchException("TikTok item id is missing");
    }
    boolean imagePost = isImagePost(item);
    MediaType mediaType = imagePost ? MediaType.IMAGE : MediaType.VIDEO;
    String desc = item.path("desc").asText("");
    long createTime = item.path("createTime").asLong(0L);
    String authorNickname = item.path("author").path("nickname").asText("");
    String authorSecUid = item.path("author").path("secUid").asText("");
    List<String> thumbnailUrls =
        imagePost ? collectImageThumbnailUrls(item) : collectVideoThumbnailUrls(item);
    List<String> downloadUrls =
        imagePost ? collectImageDownloadUrls(item) : collectVideoDownloadUrls(item);
    List<MediaType> imageMediaTypes = imagePost ? fillImageMediaTypes(downloadUrls.size()) : List.of();
    String thumbnailUrl = thumbnailUrls.isEmpty() ? "" : thumbnailUrls.get(0);
    return new AwemeProfile(
        Platform.TIKTOK,
        awemeId,
        mediaType,
        desc,
        createTime,
        authorNickname,
        authorSecUid,
        "",
        thumbnailUrl,
        thumbnailUrls,
        downloadUrls,
        imageMediaTypes);
  }

  private boolean isImagePost(JsonNode item) {
    JsonNode images = item.path("imagePost").path("images");
    return images.isArray() && !images.isEmpty();
  }

  private List<String> collectImageThumbnailUrls(JsonNode item) {
    LinkedHashSet<String> urls = new LinkedHashSet<>();
    JsonNode images = item.path("imagePost").path("images");
    if (!images.isArray()) {
      return List.of();
    }
    for (JsonNode image : images) {
      pickLastUrl(image.path("imageURL").path("urlList")).ifPresent(urls::add);
    }
    return List.copyOf(urls);
  }

  private List<String> collectImageDownloadUrls(JsonNode item) {
    LinkedHashSet<String> urls = new LinkedHashSet<>();
    JsonNode images = item.path("imagePost").path("images");
    if (!images.isArray()) {
      return List.of();
    }
    for (JsonNode image : images) {
      pickLastUrl(image.path("imageURL").path("urlList")).ifPresent(urls::add);
    }
    return List.copyOf(urls);
  }

  private List<String> collectVideoThumbnailUrls(JsonNode item) {
    LinkedHashSet<String> urls = new LinkedHashSet<>();
    appendTextUrl(urls, item.path("video").path("cover").asText(""));
    appendTextUrl(urls, item.path("video").path("dynamicCover").asText(""));
    return List.copyOf(urls);
  }

  private List<String> collectVideoDownloadUrls(JsonNode item) {
    List<VideoCandidate> candidates = new ArrayList<>();
    LinkedHashSet<String> uniqueUrls = new LinkedHashSet<>();
    JsonNode bitrateInfo = item.path("video").path("bitrateInfo");
    if (bitrateInfo.isArray()) {
      for (JsonNode bitRate : bitrateInfo) {
        JsonNode playAddr = bitRate.path("PlayAddr");
        Optional<String> url = pickLastUrl(playAddr.path("UrlList"));
        if (url.isEmpty()) {
          continue;
        }
        candidates.add(
            new VideoCandidate(
                bitRate.path("Bitrate").asLong(0L),
                playAddr.path("DataSize").asLong(0L),
                playAddr.path("Height").asInt(-1),
                playAddr.path("Width").asInt(-1),
                url.get()));
      }
    }
    candidates.sort(
        (left, right) -> {
          int resolution =
              Integer.compare(
                  Math.max(right.height(), right.width()), Math.max(left.height(), left.width()));
          if (resolution != 0) {
            return resolution;
          }
          int bitrate = Long.compare(right.bitrate(), left.bitrate());
          if (bitrate != 0) {
            return bitrate;
          }
          return Long.compare(right.dataSize(), left.dataSize());
        });
    for (VideoCandidate candidate : candidates) {
      uniqueUrls.add(candidate.url());
    }
    if (uniqueUrls.isEmpty()) {
      appendTextUrl(uniqueUrls, item.path("video").path("playAddr").asText(""));
    }
    return List.copyOf(uniqueUrls);
  }

  private List<MediaType> fillImageMediaTypes(int size) {
    if (size <= 0) {
      return List.of();
    }
    List<MediaType> types = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      types.add(MediaType.IMAGE);
    }
    return List.copyOf(types);
  }

  private Optional<String> pickLastUrl(JsonNode urls) {
    if (!urls.isArray() || urls.isEmpty()) {
      return Optional.empty();
    }
    for (int index = urls.size() - 1; index >= 0; index--) {
      String value = urls.path(index).asText("").trim();
      if (!value.isEmpty()) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }

  private void appendTextUrl(LinkedHashSet<String> urls, String url) {
    if (url != null && !url.isBlank()) {
      urls.add(url.trim());
    }
  }

  private String extractCookieField(String cookie, String name) {
    if (cookie == null || cookie.isBlank() || name == null || name.isBlank()) {
      return "";
    }
    for (String part : cookie.split(";")) {
      String trimmed = part.trim();
      int separator = trimmed.indexOf('=');
      if (separator <= 0) {
        continue;
      }
      String key = trimmed.substring(0, separator).trim();
      if (name.equals(key)) {
        return trimmed.substring(separator + 1).trim();
      }
    }
    return "";
  }

  private String mergeCookies(String baseCookie, String additionalCookie) {
    Map<String, String> pairs = new LinkedHashMap<>();
    appendCookiePairs(pairs, baseCookie);
    appendCookiePairs(pairs, additionalCookie);
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<String, String> entry : pairs.entrySet()) {
      if (builder.length() > 0) {
        builder.append("; ");
      }
      builder.append(entry.getKey()).append('=').append(entry.getValue());
    }
    return builder.toString();
  }

  private void appendCookiePairs(Map<String, String> target, String cookie) {
    if (cookie == null || cookie.isBlank()) {
      return;
    }
    for (String part : cookie.split(";")) {
      String trimmed = part.trim();
      int separator = trimmed.indexOf('=');
      if (separator <= 0) {
        continue;
      }
      target.put(trimmed.substring(0, separator).trim(), trimmed.substring(separator + 1).trim());
    }
  }

  private String normalizeCookie(String cookie) {
    return cookie == null ? "" : cookie.trim();
  }

  private record ResolvedShareLink(String normalizedCookie, String resolvedUrl) {}

  private record DeviceCookieContext(String deviceId, String cookie) {}

  private record VideoCandidate(long bitrate, long dataSize, int height, int width, String url) {}
}
