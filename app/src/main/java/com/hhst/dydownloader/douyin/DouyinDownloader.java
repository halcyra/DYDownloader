package com.hhst.dydownloader.douyin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhst.dydownloader.douyin.exception.AwemeDetailFetchException;
import com.hhst.dydownloader.douyin.exception.DouyinDownloaderException;
import com.hhst.dydownloader.douyin.exception.WorkListFetchException;
import com.hhst.dydownloader.util.HostAllowList;
import com.hhst.dydownloader.util.ShareUrls;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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

public final class DouyinDownloader {
  private static final Pattern AWEME_ID_PATTERN = Pattern.compile("(?<!\\d)(\\d{19})(?!\\d)");
  private static final Pattern SEC_USER_ID_URL_PATTERN = Pattern.compile("/user/([^/?#]+)");
  private static final Pattern SEC_USER_ID_QUERY_PATTERN =
      Pattern.compile("(?:^|[?&])(?:sec_user_id|secUid)=([^&\\s]+)");
  private static final Pattern SEC_USER_ID_JSON_PATTERN =
      Pattern.compile("\"(?:sec_user_id|secUid)\"\\s*:\\s*\"([^\"]+)\"");
  private static final Pattern MIX_ID_URL_PATTERN = Pattern.compile("/collection/(\\d{5,25})");
  private static final Pattern MIX_ID_QUERY_PATTERN =
      Pattern.compile("(?:^|[?&])(?:mix_id|mixId|collectionId)=(\\d{5,25})(?:&|$)");
  private static final Pattern MIX_ID_JSON_PATTERN =
      Pattern.compile("\"(?:mix_id|mixId|collectionId)\"\\s*:\\s*\"?(\\d{5,25})\"?");
  private static final int DEFAULT_MAX_PAGES = 200;
  private static final int ACCOUNT_PAGE_SIZE = 18;
  private static final int MIX_PAGE_SIZE = 12;
  private static final List<String> TRUSTED_HOSTS = List.of("douyin.com", "iesdouyin.com");
  // 必须与 a_bogus 签名使用的 UA 一致（第三条摘要链与其绑定）
  private static final String DEFAULT_USER_AGENT =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
          + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36";
  private static final String DEFAULT_REFERER = "https://www.douyin.com/?recommend=1";
  // 抖音对以下受保护接口要求附加 x-secsdk-web-signature，否则返回 403 Uifid Not Found
  private static final String[] SIGNED_PATHS = {
    "/aweme/v1/web/aweme/detail/",
    "/aweme/v1/web/aweme/post/",
    "/aweme/v1/web/aweme/favorite/",
    "/aweme/v1/web/aweme/listcollection/",
    "/aweme/v1/web/mix/aweme/",
    "/aweme/v1/web/tab/feed/",
    "/aweme/v1/web/mix/list/",
    "/aweme/v1/web/music/aweme/",
    "/aweme/v1/web/music/list/",
    "/aweme/v1/web/mix/detail/",
    "/aweme/v1/web/mix/listcollection/",
    "/aweme/v1/web/music/detail/",
    "/aweme/v1/web/collects/list/",
    "/aweme/v1/web/collects/video/list/",
  };
  private final OkHttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final ABogusGenerator aBogusGenerator;
  private final String defaultCookie;

  public DouyinDownloader() {
    this("");
  }

  public DouyinDownloader(String cookie) {
    this(defaultClient(), cookie);
  }

  public DouyinDownloader(OkHttpClient httpClient, String cookie) {
    this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    this.objectMapper = new ObjectMapper();
    this.aBogusGenerator = new ABogusGenerator(DEFAULT_USER_AGENT);
    this.defaultCookie = normalizeCookie(cookie);
  }

  public static boolean isTrustedShareUrl(String url) {
    return HostAllowList.matches(url, TRUSTED_HOSTS);
  }

  public static boolean shouldAttachCookie(String url) {
    return HostAllowList.matches(url, TRUSTED_HOSTS);
  }

  public static boolean isAccountLink(String text) {
    if (text == null) return false;
    return text.contains("/user/") || text.contains("sec_user_id");
  }

  private static OkHttpClient defaultClient() {
    return new OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build();
  }

  private static Map<String, String> buildAccountListParams(String secUserId, long cursor) {
    Map<String, String> extraParams = new LinkedHashMap<>();
    extraParams.put("sec_user_id", secUserId);
    extraParams.put("max_cursor", String.valueOf(cursor));
    extraParams.put("locate_query", "false");
    extraParams.put("show_live_replay_strategy", "1");
    extraParams.put("need_time_list", "1");
    extraParams.put("time_list_query", "0");
    extraParams.put("whale_cut_token", "");
    extraParams.put("cut_version", "1");
    extraParams.put("count", String.valueOf(ACCOUNT_PAGE_SIZE));
    extraParams.put("publish_video_strategy_type", "2");
    extraParams.put("from_user_page", "1");
    return extraParams;
  }

  public AwemeProfile collectWorkInfo(String shareLink) throws DouyinDownloaderException {
    return collectWorkInfo(shareLink, this.defaultCookie);
  }

  public AwemeProfile collectWorkInfo(String shareLink, String cookie)
      throws DouyinDownloaderException {
    SingleAwemeContext context = resolveSingleAwemeContext(shareLink, cookie);
    return collectAwemeProfileFromNode(context.aweme(), context.awemeId());
  }

  public List<AwemeProfile> collectAccountWorksInfo(String accountShareLink)
      throws DouyinDownloaderException {
    return collectAccountWorksInfo(accountShareLink, this.defaultCookie);
  }

  public List<AwemeProfile> collectAccountWorksInfo(String accountShareLink, String cookie)
      throws DouyinDownloaderException {
    ResolvedShareLink resolved = resolveShareLink(accountShareLink, cookie);
    String secUserId =
        resolveSecUserId(accountShareLink, resolved.resolvedUrl(), resolved.normalizedCookie());
    return collectAwemeProfileList(fetchAccountAwemeList(secUserId, resolved.normalizedCookie()));
  }

  public List<AwemeProfile> collectMixWorksInfo(String mixShareLink)
      throws DouyinDownloaderException {
    return collectMixWorksInfo(mixShareLink, this.defaultCookie);
  }

  public List<AwemeProfile> collectMixWorksInfo(String mixShareLink, String cookie)
      throws DouyinDownloaderException {
    ResolvedShareLink resolved = resolveShareLink(mixShareLink, cookie);
    String mixId = resolveMixId(mixShareLink, resolved.resolvedUrl(), resolved.normalizedCookie());
    return collectAwemeProfileList(fetchMixAwemeList(mixId, resolved.normalizedCookie()));
  }

  private SingleAwemeContext resolveSingleAwemeContext(String shareLink, String cookie)
      throws DouyinDownloaderException {
    if (shareLink == null || shareLink.trim().isEmpty()) {
      throw new IllegalArgumentException("shareLink is blank");
    }

    ResolvedShareLink resolved = resolveShareLink(shareLink, cookie);
    String awemeId =
        extractAwemeId(resolved.resolvedUrl())
            .orElseGet(
                () ->
                    extractAwemeId(shareLink)
                        .orElseThrow(
                            () ->
                                new IllegalArgumentException(
                                    "Cannot extract aweme_id from shareLink")));
    JsonNode aweme = fetchAwemeDetail(awemeId, resolved.normalizedCookie());
    return new SingleAwemeContext(awemeId, aweme);
  }

  private ResolvedShareLink resolveShareLink(String shareLink, String cookie) {
    String normalizedCookie = normalizeCookie(cookie);
    Optional<String> extractedUrl = ShareUrls.firstUrl(shareLink);
    if (extractedUrl.isPresent() && !isTrustedShareUrl(extractedUrl.get())) {
      throw new IllegalArgumentException("Unsupported share link host");
    }
    String rawUrl = extractedUrl.orElse(shareLink.trim());
    String resolvedUrl = resolveFinalUrl(rawUrl, normalizedCookie);
    if (ShareUrls.firstUrl(resolvedUrl).isPresent() && !isTrustedShareUrl(resolvedUrl)) {
      throw new IllegalArgumentException("Unsupported resolved share link host");
    }
    return new ResolvedShareLink(normalizedCookie, resolvedUrl);
  }

  private List<AwemeProfile> collectAwemeProfileList(List<JsonNode> awemeList)
      throws DouyinDownloaderException {
    List<AwemeProfile> results = new ArrayList<>(awemeList.size());
    for (JsonNode aweme : awemeList) {
      String awemeId = aweme.path("aweme_id").asText("").trim();
      results.add(collectAwemeProfileFromNode(aweme, awemeId));
    }
    return Collections.unmodifiableList(results);
  }

  private AwemeProfile collectAwemeProfileFromNode(JsonNode aweme, String fallbackAwemeId)
      throws DouyinDownloaderException {
    String awemeId = aweme.path("aweme_id").asText("").trim();
    if (awemeId.isEmpty()) {
      awemeId = fallbackAwemeId == null ? "" : fallbackAwemeId.trim();
    }
    if (awemeId.isEmpty()) {
      throw new AwemeDetailFetchException("aweme_id is missing in aweme payload");
    }

    MediaType mediaType = isImagePost(aweme) ? MediaType.IMAGE : MediaType.VIDEO;
    String desc = aweme.path("desc").asText("");
    long createTime = aweme.path("create_time").asLong(0L);
    String authorNickname = aweme.path("author").path("nickname").asText("");
    String authorSecUserId = aweme.path("author").path("sec_uid").asText("");
    String collectionTitle = aweme.path("mix_info").path("mix_name").asText("").trim();
    List<String> thumbnailUrls;
    List<String> downloadUrls;
    List<MediaType> imageMediaTypes;

    if (mediaType == MediaType.IMAGE) {
      ImageAssetCollection imageAssets = collectImageAssets(aweme);
      thumbnailUrls = imageAssets.thumbnailUrls();
      downloadUrls = imageAssets.downloadUrls();
      imageMediaTypes = imageAssets.mediaTypes();
      if (thumbnailUrls.isEmpty()) {
        thumbnailUrls = collectThumbnailUrls(aweme);
      }
    } else {
      thumbnailUrls = collectThumbnailUrls(aweme);
      downloadUrls = collectVideoUrlCandidates(aweme);
      imageMediaTypes = List.of();
    }
    String thumbnailUrl = thumbnailUrls.isEmpty() ? "" : thumbnailUrls.get(0);

    return new AwemeProfile(
        awemeId,
        mediaType,
        desc,
        createTime,
        authorNickname,
        authorSecUserId,
        collectionTitle,
        thumbnailUrl,
        thumbnailUrls,
        downloadUrls,
        imageMediaTypes);
  }

  private String resolveSecUserId(String sourceText, String resolvedUrl, String cookie)
      throws WorkListFetchException {
    Optional<String> secUserId = extractSecUserId(resolvedUrl);
    if (secUserId.isEmpty()) secUserId = extractSecUserId(sourceText);
    // Only attempt to scrape sec_user_id from HTML when the link itself looks like an account link.
    // Otherwise (e.g. aweme/mix links), logged-in cookies may cause us to capture the *current
    // user* secUid.
    boolean isSelfUserPage =
        resolvedUrl != null
            && (resolvedUrl.contains("/user/self") || resolvedUrl.contains("/user/me"));
    if (secUserId.isEmpty()
        && !isSelfUserPage
        && (isAccountLink(resolvedUrl) || isAccountLink(sourceText))) {
      secUserId = fetchSecUserIdFromPage(resolvedUrl, cookie);
    }

    return secUserId
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .orElseThrow(
            () -> new WorkListFetchException("Cannot extract sec_user_id from account share link"));
  }

  private String resolveMixId(String sourceText, String resolvedUrl, String cookie)
      throws DouyinDownloaderException {
    Optional<String> mixId = extractMixId(resolvedUrl);
    if (mixId.isEmpty()) mixId = extractMixId(sourceText);
    if (mixId.isEmpty()) mixId = fetchMixIdFromPage(resolvedUrl, cookie);

    if (mixId.isPresent() && !mixId.get().isEmpty()) {
      return mixId.get();
    }

    Optional<String> awemeId = extractAwemeId(resolvedUrl);
    if (awemeId.isEmpty()) awemeId = extractAwemeId(sourceText);
    if (awemeId.isPresent()) {
      JsonNode aweme = fetchAwemeDetail(awemeId.get(), cookie);
      String resolvedMixId = aweme.path("mix_info").path("mix_id").asText("").trim();
      if (!resolvedMixId.isEmpty()) {
        return resolvedMixId;
      }
    }

    throw new WorkListFetchException("Cannot extract mix_id from mix share link");
  }

  private List<JsonNode> fetchAccountAwemeList(String secUserId, String cookie)
      throws WorkListFetchException {
    long cursor = 0;
    boolean hasMore = true;
    int pageCount = 0;
    long lastCursor = Long.MIN_VALUE;
    List<JsonNode> awemeList = new ArrayList<>();
    LinkedHashSet<String> seenAwemeIds = new LinkedHashSet<>();

    while (hasMore && pageCount < DEFAULT_MAX_PAGES) {
      Map<String, String> extraParams = buildAccountListParams(secUserId, cursor);
      String postApi = "https://www.douyin.com/aweme/v1/web/aweme/post/";
      String query = buildWebListQuery(extraParams, cookie, postApi);
      JsonNode pageData =
          fetchPagedAwemeList(
              Arrays.asList(
                  "https://www.douyin.com/aweme/v1/web/aweme/post/?" + query,
                  "https://www.iesdouyin.com/aweme/v1/web/aweme/post/?" + query),
              cookie,
              "account");

      JsonNode items = pageData.path("aweme_list");
      if (!items.isArray()) {
        throw new WorkListFetchException("aweme_list is missing in account list response");
      }
      int newItemCount = 0;
      for (JsonNode item : items) {
        if (item.isObject()) {
          String awemeId = item.path("aweme_id").asText("").trim();
          if (!awemeId.isEmpty() && !seenAwemeIds.add(awemeId)) {
            continue;
          }
          awemeList.add(item);
          newItemCount++;
        }
      }

      hasMore = parseHasMore(pageData.path("has_more"));
      long nextCursor = pageData.path("max_cursor").asLong(cursor);
      pageCount++;

      if (hasMore
          && (newItemCount == 0
              || nextCursor == cursor
              || (lastCursor != Long.MIN_VALUE && nextCursor == lastCursor))) {
        break;
      }
      lastCursor = cursor;
      cursor = nextCursor;
    }

    return awemeList;
  }

  private List<JsonNode> fetchMixAwemeList(String mixId, String cookie)
      throws WorkListFetchException {
    long cursor = 0;
    boolean hasMore = true;
    int pageCount = 0;
    long lastCursor = Long.MIN_VALUE;
    List<JsonNode> awemeList = new ArrayList<>();
    LinkedHashSet<String> seenAwemeIds = new LinkedHashSet<>();

    while (hasMore && pageCount < DEFAULT_MAX_PAGES) {
      Map<String, String> extraParams = new LinkedHashMap<>();
      extraParams.put("mix_id", mixId);
      extraParams.put("cursor", String.valueOf(cursor));
      extraParams.put("count", String.valueOf(MIX_PAGE_SIZE));

      String mixApi = "https://www.douyin.com/aweme/v1/web/mix/aweme/";
      String query = buildWebListQuery(extraParams, cookie, mixApi);
      JsonNode pageData =
          fetchPagedAwemeList(
              Arrays.asList(
                  "https://www.douyin.com/aweme/v1/web/mix/aweme/?" + query,
                  "https://www.iesdouyin.com/aweme/v1/web/mix/aweme/?" + query),
              cookie,
              "mix");

      JsonNode items = pageData.path("aweme_list");
      if (!items.isArray()) {
        throw new WorkListFetchException("aweme_list is missing in mix list response");
      }
      int newItemCount = 0;
      for (JsonNode item : items) {
        if (item.isObject()) {
          String awemeId = item.path("aweme_id").asText("").trim();
          if (!awemeId.isEmpty() && !seenAwemeIds.add(awemeId)) {
            continue;
          }
          awemeList.add(item);
          newItemCount++;
        }
      }

      hasMore = parseHasMore(pageData.path("has_more"));
      long nextCursor = pageData.path("cursor").asLong(cursor);
      pageCount++;

      if (hasMore
          && (newItemCount == 0
              || nextCursor == cursor
              || (lastCursor != Long.MIN_VALUE && nextCursor == lastCursor))) {
        break;
      }
      lastCursor = cursor;
      cursor = nextCursor;
    }

    return awemeList;
  }

  private JsonNode fetchPagedAwemeList(List<String> apiUrls, String cookie, String listType)
      throws WorkListFetchException {
    List<String> attempts = new ArrayList<>();

    for (String apiUrl : apiUrls) {
      // 签名接口偶发 403/空响应时重试一次，提高弱网与风控抖动下的成功率
      for (int attempt = 0; attempt < 2; attempt++) {
        try {
          Request request =
              requestBuilder(apiUrl, cookie)
                  .get()
                  .header("Accept", "*/*")
                  .build();

          try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody bodyObj = response.body();
            String body = bodyObj != null ? bodyObj.string() : "";
            body = body.trim();
            if (body.isEmpty()) {
              attempts.add("HTTP=" + response.code() + ", URL=" + apiUrl + ", BODY=<empty>");
              continue;
            }

            JsonNode root = objectMapper.readTree(body);
            if (response.isSuccessful()
                && root != null
                && root.path("status_code").asInt(0) == 0
                && root.path("aweme_list").isArray()) {
              return root;
            }
            attempts.add("HTTP=" + response.code() + ", URL=" + apiUrl + ", BODY=" + shortBody(body));
          }
        } catch (IOException | RuntimeException e) {
          attempts.add(
              "ERR="
                  + e.getClass().getSimpleName()
                  + ", URL="
                  + apiUrl
                  + ", MSG="
                  + shortBody(e.getMessage()));
        }
      }
    }

    throw new WorkListFetchException(
        "Cannot fetch "
            + listType
            + " work list. Tried "
            + apiUrls.size()
            + " endpoints. "
            + String.join(" | ", attempts));
  }

  private String resolveFinalUrl(String url, String cookie) {
    Request request;
    try {
      request =
          requestBuilder(url, cookie)
              .get()
              .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
              .build();
    } catch (Exception ignored) {
      return url;
    }

    try (Response response = httpClient.newCall(request).execute()) {
      return response.request().url().toString();
    } catch (IOException e) {
      return url;
    }
  }

  private JsonNode fetchAwemeDetail(String awemeId, String cookie)
      throws AwemeDetailFetchException {
    // Prefer the signed detail API, then fall back to the legacy iteminfo endpoint.
    String detailQuery = buildWebDetailQuery(awemeId, cookie);
    List<String> candidates =
        Arrays.asList(
            "https://www.douyin.com/aweme/v1/web/aweme/detail/?" + detailQuery,
            "https://www.iesdouyin.com/aweme/v1/web/aweme/detail/?" + detailQuery,
            "https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids="
                + quotePlus(awemeId));

    List<String> attempts = new ArrayList<>();
    for (String apiUrl : candidates) {
      try {
        Request request =
            requestBuilder(apiUrl, cookie)
                .get()
                .header("Accept", "*/*")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
          ResponseBody bodyObj = response.body();
          String body = bodyObj != null ? bodyObj.string() : "";
          body = body.trim();

          Optional<JsonNode> aweme = parseAwemeNode(body);
          if (aweme.isPresent()) {
            return aweme.get();
          }

          attempts.add("HTTP=" + response.code() + ", URL=" + apiUrl + ", BODY=" + shortBody(body));
        }
      } catch (IOException | RuntimeException e) {
        attempts.add(
            "ERR="
                + e.getClass().getSimpleName()
                + ", URL="
                + apiUrl
                + ", MSG="
                + shortBody(e.getMessage()));
      }
    }

    throw new AwemeDetailFetchException(
        "Cannot parse aweme detail for aweme_id="
            + awemeId
            + ". Tried "
            + candidates.size()
            + " endpoints. "
            + String.join(" | ", attempts)
            + ". Suggestion: ensure Cookie is injected and include signature chain (a_bogus/msToken/uifid).");
  }

  private Optional<JsonNode> parseAwemeNode(String body) {
    if (body == null || body.trim().isEmpty()) {
      return Optional.empty();
    }

    try {
      JsonNode root = objectMapper.readTree(body);
      JsonNode itemList = root.path("item_list");
      if (itemList.isArray() && !itemList.isEmpty()) {
        return Optional.of(itemList.get(0));
      }

      JsonNode awemeDetail = root.path("aweme_detail");
      if (awemeDetail.isObject() && !awemeDetail.isEmpty()) {
        return Optional.of(awemeDetail);
      }

      JsonNode nestedAwemeDetail = root.path("data").path("aweme_detail");
      if (nestedAwemeDetail.isObject() && !nestedAwemeDetail.isEmpty()) {
        return Optional.of(nestedAwemeDetail);
      }

      JsonNode awemeList = root.path("aweme_list");
      if (awemeList.isArray() && !awemeList.isEmpty()) {
        return Optional.of(awemeList.get(0));
      }

      return Optional.empty();
    } catch (Exception ignored) {
      return Optional.empty();
    }
  }

  private String buildWebDetailQuery(String awemeId, String cookie) {
    Map<String, String> params = buildCommonWebParams(cookie, "190500", "19.5.0");
    params.put("aweme_id", awemeId);
    return buildSignedQuery("https://www.douyin.com/aweme/v1/web/aweme/detail/", params);
  }

  private String buildWebListQuery(Map<String, String> extraParams, String cookie, String apiUrl) {
    Map<String, String> params = buildCommonWebParams(cookie, "170400", "17.4.0");
    if (extraParams != null) {
      params.putAll(extraParams);
    }
    return buildSignedQuery(apiUrl, params);
  }

  private Map<String, String> buildCommonWebParams(
      String cookie, String versionCode, String versionName) {
    Map<String, String> params = new LinkedHashMap<>();
    String uifid = extractCookieValue(cookie, "UIFID");
    if (uifid == null) uifid = extractCookieValue(cookie, "uifid");
    if (uifid == null) uifid = "";

    String msToken = extractCookieValue(cookie, "msToken");
    if (msToken == null) msToken = "";

    params.put("device_platform", "webapp");
    params.put("aid", "6383");
    params.put("channel", "channel_pc_web");
    params.put("update_version_code", "170400");
    params.put("pc_client_type", "1");
    params.put("pc_libra_divert", "Mac");
    params.put("support_h265", "1");
    params.put("support_dash", "1");
    params.put("version_code", versionCode);
    params.put("version_name", versionName);
    params.put("cookie_enabled", "true");
    params.put("screen_width", "1536");
    params.put("screen_height", "864");
    params.put("browser_language", "zh-CN");
    params.put("browser_platform", "MacIntel");
    params.put("browser_name", "Chrome");
    params.put("browser_version", "146.0.0.0");
    params.put("browser_online", "true");
    params.put("engine_name", "Blink");
    params.put("engine_version", "146.0.0.0");
    params.put("os_name", "Mac OS");
    params.put("os_version", "10.15.7");
    params.put("cpu_core_num", "16");
    params.put("device_memory", "8");
    params.put("platform", "PC");
    params.put("downlink", "10");
    params.put("effective_type", "4g");
    params.put("round_trip_time", "200");
    params.put("uifid", uifid);
    params.put("msToken", msToken);
    return params;
  }

  /**
   * 构造最终签名的 query：a_bogus 始终附加；受保护接口且 query 含 uifid 时，
   * 先按 WebSign 规范化 query 再计算 a_bogus，最后追加 timestamp 与
   * x-secsdk-web-signature。
   */
  private String buildSignedQuery(String apiUrl, Map<String, String> params) {
    String query = encodeQueryParams(params);
    String uifid = isSignProtected(apiUrl) ? extractQueryValue(query, "uifid") : "";
    // 受保护接口且携带 uifid 时才走 WebSign
    boolean webSign = !uifid.isEmpty();
    if (webSign) {
      query = WebSignGenerator.normalizeQuery(query);
    }
    String aBogus = aBogusGenerator.getValue(query);
    // s4 字母表包含 "/" 与 "=" 填充，发送前需百分号编码
    String signedQuery = query + "&a_bogus=" + WebSignGenerator.quote(aBogus, "");
    if (webSign) {
      signedQuery = WebSignGenerator.sign(signedQuery, uifid, System.currentTimeMillis() / 1000L);
    }
    return signedQuery;
  }

  private static boolean isSignProtected(String apiUrl) {
    String path;
    try {
      path = URI.create(apiUrl).getPath();
    } catch (Exception e) {
      return false;
    }
    if (path == null || !path.startsWith("/")) {
      path = "/" + (path == null ? "" : path);
    }
    if (!path.endsWith("/")) {
      path += "/";
    }
    for (String candidate : SIGNED_PATHS) {
      if (path.equals(candidate)) {
        return true;
      }
    }
    return false;
  }

  /** 从已编码的 query 中取值并解码（unquote 而非 unquote_plus）。 */
  private static String extractQueryValue(String query, String name) {
    for (String part : query.split("&")) {
      if (part.isEmpty()) {
        continue;
      }
      int separator = part.indexOf('=');
      String key = separator >= 0 ? part.substring(0, separator) : part;
      if (name.equals(WebSignGenerator.unquote(key))) {
        String value = separator >= 0 ? part.substring(separator + 1) : "";
        return WebSignGenerator.unquote(value);
      }
    }
    return "";
  }

  /** Python urlencode（quote_plus）：空格编码为 "+"，保留 [A-Za-z0-9_.-~]。 */
  private static String encodeQueryParams(Map<String, String> params) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, String> entry : params.entrySet()) {
      if (!first) {
        sb.append("&");
      }
      first = false;
      sb.append(quotePlus(entry.getKey())).append("=").append(quotePlus(entry.getValue()));
    }
    return sb.toString();
  }

  private static String quotePlus(String value) {
    String input = value == null ? "" : value;
    StringBuilder builder = new StringBuilder(input.length());
    byte[] encoded = input.getBytes(StandardCharsets.UTF_8);
    for (byte item : encoded) {
      int unsigned = item & 0xFF;
      if ((unsigned >= 'a' && unsigned <= 'z')
          || (unsigned >= 'A' && unsigned <= 'Z')
          || (unsigned >= '0' && unsigned <= '9')
          || unsigned == '_'
          || unsigned == '.'
          || unsigned == '-'
          || unsigned == '~') {
        builder.append((char) unsigned);
      } else if (unsigned == ' ') {
        builder.append('+');
      } else {
        builder.append('%');
        builder.append(Character.toUpperCase(Character.forDigit((unsigned >> 4) & 0xF, 16)));
        builder.append(Character.toUpperCase(Character.forDigit(unsigned & 0xF, 16)));
      }
    }
    return builder.toString();
  }

  private String extractCookieValue(String cookie, String name) {
    String normalizedCookie = normalizeCookie(cookie);
    if (normalizedCookie.trim().isEmpty()) {
      return null;
    }

    Pattern pattern = Pattern.compile("(?:^|;\\s*)" + Pattern.quote(name) + "=([^;]*)");
    Matcher matcher = pattern.matcher(normalizedCookie);
    if (matcher.find()) {
      String val = matcher.group(1);
      return val != null ? val.trim() : null;
    }
    return null;
  }

  private Optional<String> extractSecUserId(String text) {
    if (text == null || text.trim().isEmpty()) {
      return Optional.empty();
    }

    String trimmed = text.trim();
    if (trimmed.matches("MS4w[0-9A-Za-z._-]+")) {
      return Optional.of(trimmed);
    }

    Matcher pathMatcher = SEC_USER_ID_URL_PATTERN.matcher(trimmed);
    if (pathMatcher.find()) {
      String candidate = decodeUrlValue(pathMatcher.group(1));
      if (candidate != null) {
        candidate = candidate.trim();
        if (candidate.matches("MS4w[0-9A-Za-z._-]+")) {
          return Optional.of(candidate);
        }
      }
    }

    Matcher queryMatcher = SEC_USER_ID_QUERY_PATTERN.matcher(trimmed);
    if (queryMatcher.find()) {
      String candidate = decodeUrlValue(queryMatcher.group(1));
      if (candidate != null) {
        candidate = candidate.trim();
        if (candidate.matches("MS4w[0-9A-Za-z._-]+")) {
          return Optional.of(candidate);
        }
      }
    }

    Matcher jsonMatcher = SEC_USER_ID_JSON_PATTERN.matcher(trimmed);
    if (jsonMatcher.find()) {
      String candidate = decodeUrlValue(jsonMatcher.group(1));
      if (candidate != null) {
        candidate = candidate.trim();
        if (candidate.matches("MS4w[0-9A-Za-z._-]+")) {
          return Optional.of(candidate);
        }
      }
    }

    return Optional.empty();
  }

  private Optional<String> extractMixId(String text) {
    if (text == null || text.trim().isEmpty()) {
      return Optional.empty();
    }

    Matcher pathMatcher = MIX_ID_URL_PATTERN.matcher(text);
    if (pathMatcher.find()) {
      return Optional.ofNullable(pathMatcher.group(1));
    }

    Matcher queryMatcher = MIX_ID_QUERY_PATTERN.matcher(text);
    if (queryMatcher.find()) {
      return Optional.ofNullable(queryMatcher.group(1));
    }

    Matcher jsonMatcher = MIX_ID_JSON_PATTERN.matcher(text);
    if (jsonMatcher.find()) {
      return Optional.ofNullable(jsonMatcher.group(1));
    }

    return Optional.empty();
  }

  private Optional<String> fetchSecUserIdFromPage(String url, String cookie) {
    Optional<String> page = fetchPageContent(url, cookie);
    if (page.isEmpty()) {
      return Optional.empty();
    }
    return extractSecUserIdFromPage(page.get());
  }

  private Optional<String> extractSecUserIdFromPage(String html) {
    if (html == null || html.trim().isEmpty()) {
      return Optional.empty();
    }

    // Prefer URL-shaped occurrences; avoid JSON "secUid" which may be the logged-in user.
    Matcher pathMatcher = SEC_USER_ID_URL_PATTERN.matcher(html);
    while (pathMatcher.find()) {
      String candidate = decodeUrlValue(pathMatcher.group(1));
      if (candidate != null) {
        candidate = candidate.trim();
        if (candidate.startsWith("MS4w")) {
          return Optional.of(candidate);
        }
      }
    }

    Matcher queryMatcher = SEC_USER_ID_QUERY_PATTERN.matcher(html);
    while (queryMatcher.find()) {
      String candidate = decodeUrlValue(queryMatcher.group(1));
      if (candidate != null) {
        candidate = candidate.trim();
        if (candidate.startsWith("MS4w")) {
          return Optional.of(candidate);
        }
      }
    }

    return Optional.empty();
  }

  private Optional<String> fetchMixIdFromPage(String url, String cookie) {
    Optional<String> page = fetchPageContent(url, cookie);
    if (page.isEmpty()) {
      return Optional.empty();
    }
    return extractMixId(page.get());
  }

  private Optional<String> fetchPageContent(String url, String cookie) {
    try {
      Request request =
          requestBuilder(url, cookie)
              .get()
              .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
              .build();
      try (Response response = httpClient.newCall(request).execute()) {
        ResponseBody bodyObj = response.body();
        String body = bodyObj != null ? bodyObj.string() : "";
        if (body.trim().isEmpty()) {
          return Optional.empty();
        }
        return Optional.of(body);
      }
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  private boolean isImagePost(JsonNode aweme) {
    JsonNode images = aweme.path("images");
    if (images.isArray() && !images.isEmpty()) {
      return true;
    }
    JsonNode imagePostImages = aweme.path("image_post_info").path("images");
    return imagePostImages.isArray() && !imagePostImages.isEmpty();
  }

  private ImageAssetCollection collectImageAssets(JsonNode aweme) {
    List<ImageAsset> assets = new ArrayList<>();

    JsonNode images = aweme.path("images");
    if (images.isArray() && !images.isEmpty()) {
      appendImageAssets(assets, images);
    }

    JsonNode imagePostImages = aweme.path("image_post_info").path("images");
    if (assets.isEmpty() && imagePostImages.isArray() && !imagePostImages.isEmpty()) {
      appendImageAssets(assets, imagePostImages);
    }

    return ImageAssetCollection.from(assets);
  }

  private void appendImageAssets(List<ImageAsset> assets, JsonNode images) {
    if (assets == null || !images.isArray()) {
      return;
    }
    for (JsonNode image : images) {
      if (image == null || image.isNull()) {
        continue;
      }
      String thumbnailUrl = pickImageThumbnailUrl(image).orElse("");
      Optional<String> livePhotoVideoUrl = pickLivePhotoVideoUrl(image);
      if (livePhotoVideoUrl.isPresent()) {
        assets.add(new ImageAsset(thumbnailUrl, livePhotoVideoUrl.get(), MediaType.VIDEO));
        continue;
      }

      Optional<String> imageUrl = pickImageDownloadUrl(image);
      if (imageUrl.isPresent()) {
        assets.add(new ImageAsset(thumbnailUrl, imageUrl.get(), MediaType.IMAGE));
        continue;
      }

      if (!thumbnailUrl.isBlank()) {
        assets.add(new ImageAsset(thumbnailUrl, thumbnailUrl, MediaType.IMAGE));
      }
    }
  }

  private Optional<String> pickImageDownloadUrl(JsonNode image) {
    Optional<String> url = pickLastUrl(image.path("url_list"));
    if (url.isPresent()) {
      return url;
    }
    url = pickLastUrl(image.path("display_image").path("url_list"));
    if (url.isPresent()) {
      return url;
    }
    url = pickLastUrl(image.path("owner_watermark_image").path("url_list"));
    if (url.isPresent()) {
      return url;
    }
    return pickLastUrl(image.path("download_url_list"));
  }

  private Optional<String> pickLivePhotoVideoUrl(JsonNode image) {
    Optional<String> url = pickLastUrl(image.path("video").path("play_addr").path("url_list"));
    if (url.isPresent()) {
      return url;
    }
    url = pickLastUrl(image.path("video").path("play_addr_h264").path("url_list"));
    if (url.isPresent()) {
      return url;
    }
    url = pickLastUrl(image.path("video").path("download_addr").path("url_list"));
    if (url.isPresent()) {
      return url;
    }
    url = pickLastUrl(image.path("live_photo").path("video").path("play_addr").path("url_list"));
    if (url.isPresent()) {
      return url;
    }
    url =
        pickLastUrl(image.path("live_photo").path("video").path("download_addr").path("url_list"));
    if (url.isPresent()) {
      return url;
    }
    url = pickLastUrl(image.path("motion_photo").path("video").path("play_addr").path("url_list"));
    if (url.isPresent()) {
      return url;
    }
    url =
        pickLastUrl(
            image.path("motion_photo").path("video").path("download_addr").path("url_list"));
    if (url.isPresent()) {
      return url;
    }
    return pickLastUrl(image.path("video").path("url_list"));
  }

  private List<String> collectThumbnailUrls(JsonNode aweme) {
    LinkedHashSet<String> urls = new LinkedHashSet<>();

    if (isImagePost(aweme)) {
      appendImageThumbnailUrls(urls, aweme.path("images"));
      appendImageThumbnailUrls(urls, aweme.path("image_post_info").path("images"));
      if (!urls.isEmpty()) {
        return List.copyOf(urls);
      }
    }

    appendUrlListCandidates(urls, aweme.path("video").path("cover").path("url_list"));
    appendUrlListCandidates(urls, aweme.path("video").path("origin_cover").path("url_list"));
    appendUrlListCandidates(urls, aweme.path("video").path("dynamic_cover").path("url_list"));

    return List.copyOf(urls);
  }

  private void appendImageThumbnailUrls(LinkedHashSet<String> urls, JsonNode images) {
    if (!images.isArray()) {
      return;
    }
    for (JsonNode image : images) {
      Optional<String> url = pickImageThumbnailUrl(image);
      url.ifPresent(urls::add);
    }
  }

  private Optional<String> pickImageThumbnailUrl(JsonNode image) {
    Optional<String> url = pickLastUrl(image.path("display_image").path("url_list"));
    if (url.isPresent()) {
      return url;
    }
    url = pickLastUrl(image.path("owner_watermark_image").path("url_list"));
    if (url.isPresent()) {
      return url;
    }
    url = pickLastUrl(image.path("url_list"));
    if (url.isPresent()) {
      return url;
    }
    return pickLastUrl(image.path("download_url_list"));
  }

  private List<String> collectVideoUrlCandidates(JsonNode aweme) {
    LinkedHashSet<String> urls = new LinkedHashSet<>();
    LinkedHashSet<String> playUris = new LinkedHashSet<>();
    List<VideoCandidate> qualityCandidates = new ArrayList<>();

    JsonNode bitRates = aweme.path("video").path("bit_rate");
    if (bitRates.isArray()) {
      for (JsonNode bitRate : bitRates) {
        JsonNode playAddr = bitRate.path("play_addr");
        Optional<String> url = pickLastUrl(playAddr.path("url_list"));
        if (url.isEmpty()) {
          continue;
        }
        playUris.add(playAddr.path("uri").asText("").trim());
        qualityCandidates.add(
            new VideoCandidate(
                playAddr.path("height").asInt(-1),
                playAddr.path("width").asInt(-1),
                bitRate.path("FPS").asInt(-1),
                bitRate.path("bit_rate").asInt(-1),
                playAddr.path("data_size").asLong(-1),
                url.get()));
      }
    }

    qualityCandidates.sort(
        (a, b) -> {
          int cmp =
              Integer.compare(Math.max(b.height(), b.width()), Math.max(a.height(), a.width()));
          if (cmp != 0) {
            return cmp;
          }
          cmp = Integer.compare(b.fps(), a.fps());
          if (cmp != 0) {
            return cmp;
          }
          cmp = Integer.compare(b.bitRate(), a.bitRate());
          if (cmp != 0) {
            return cmp;
          }
          return Long.compare(b.dataSize(), a.dataSize());
        });

    for (VideoCandidate candidate : qualityCandidates) {
      urls.add(candidate.url());
    }

    appendUrlListCandidates(urls, aweme.path("video").path("play_addr").path("url_list"));
    appendUrlListCandidates(urls, aweme.path("video").path("play_addr_h264").path("url_list"));
    appendUrlListCandidates(urls, aweme.path("video").path("download_addr").path("url_list"));

    playUris.add(aweme.path("video").path("play_addr").path("uri").asText("").trim());
    playUris.add(aweme.path("video").path("play_addr_h264").path("uri").asText("").trim());
    for (String playUri : playUris) {
      appendPlayApiCandidates(urls, playUri);
    }

    return new ArrayList<>(urls);
  }

  private void appendPlayApiCandidates(LinkedHashSet<String> urls, String playUri) {
    if (playUri == null || playUri.trim().isEmpty()) {
      return;
    }
    String encodedUri = quotePlus(playUri);
    urls.add(
        "https://aweme.snssdk.com/aweme/v1/play/?video_id=" + encodedUri + "&line=0&ratio=1080p");
    urls.add(
        "https://aweme.snssdk.com/aweme/v1/play/?video_id=" + encodedUri + "&line=0&ratio=720p");
    urls.add("https://aweme.snssdk.com/aweme/v1/play/?video_id=" + encodedUri + "&line=0");
  }

  private void appendUrlListCandidates(LinkedHashSet<String> urls, JsonNode urlList) {
    if (!urlList.isArray() || urlList.isEmpty()) {
      return;
    }
    for (int i = urlList.size() - 1; i >= 0; i--) {
      String url = urlList.get(i).asText("").trim();
      if (!url.isEmpty()) {
        urls.add(url);
      }
    }
  }

  private Request.Builder requestBuilder(String url, String cookie) {
    Request.Builder builder =
        new Request.Builder()
            .url(url)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Referer", DEFAULT_REFERER)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "*/*")
            .header("x-tt-argus", "1");

    String normalizedCookie = normalizeCookie(cookie);
    if (!normalizedCookie.trim().isEmpty() && shouldAttachCookie(url)) {
      builder.header("Cookie", normalizedCookie);
      String uifid = extractCookieValue(normalizedCookie, "UIFID");
      if (uifid == null) {
        uifid = extractCookieValue(normalizedCookie, "uifid");
      }
      if (uifid != null && !uifid.isEmpty()) {
        builder.header("uifid", uifid);
      }
    }
    return builder;
  }

  private String normalizeCookie(String cookie) {
    if (cookie == null) {
      return "";
    }
    return cookie.trim();
  }

  private String shortBody(String body) {
    if (body == null || body.trim().isEmpty()) {
      return "<empty>";
    }
    String compact = body.replaceAll("\\s+", " ").trim();
    int limit = 180;
    return compact.length() <= limit ? compact : compact.substring(0, limit) + "...";
  }

  private String decodeUrlValue(String value) {
    if (value == null || value.trim().isEmpty()) {
      return "";
    }
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
    } catch (Exception ignored) {
      return value;
    }
  }

  private boolean parseHasMore(JsonNode hasMoreNode) {
    if (hasMoreNode == null || hasMoreNode.isMissingNode() || hasMoreNode.isNull()) {
      return false;
    }
    if (hasMoreNode.isBoolean()) {
      return hasMoreNode.asBoolean();
    }
    if (hasMoreNode.isNumber()) {
      return hasMoreNode.asInt(0) != 0;
    }
    String text = hasMoreNode.asText("").trim();
    return "1".equals(text) || "true".equalsIgnoreCase(text);
  }

  private Optional<String> pickLastUrl(JsonNode urlList) {
    if (!urlList.isArray() || urlList.isEmpty()) {
      return Optional.empty();
    }
    for (int i = urlList.size() - 1; i >= 0; i--) {
      String url = urlList.get(i).asText("").trim();
      if (!url.isEmpty()) {
        return Optional.of(url);
      }
    }
    return Optional.empty();
  }

  private Optional<String> extractAwemeId(String text) {
    if (text == null || text.trim().isEmpty()) {
      return Optional.empty();
    }
    Matcher matcher = AWEME_ID_PATTERN.matcher(text);
    if (matcher.find()) {
      return Optional.ofNullable(matcher.group(1));
    }
    return Optional.empty();
  }

  private record ResolvedShareLink(String normalizedCookie, String resolvedUrl) {}

  private record SingleAwemeContext(String awemeId, JsonNode aweme) {}

  private record ImageAsset(String thumbnailUrl, String downloadUrl, MediaType mediaType) {}

  private record ImageAssetCollection(
      List<String> thumbnailUrls, List<String> downloadUrls, List<MediaType> mediaTypes) {
    private static ImageAssetCollection from(List<ImageAsset> assets) {
      if (assets == null || assets.isEmpty()) {
        return new ImageAssetCollection(List.of(), List.of(), List.of());
      }
      List<String> thumbnails = new ArrayList<>(assets.size());
      List<String> downloads = new ArrayList<>(assets.size());
      List<MediaType> types = new ArrayList<>(assets.size());
      for (ImageAsset asset : assets) {
        if (asset == null) {
          continue;
        }
        String downloadUrl = asset.downloadUrl() == null ? "" : asset.downloadUrl().trim();
        if (downloadUrl.isEmpty()) {
          continue;
        }
        String thumbnailUrl = asset.thumbnailUrl() == null ? "" : asset.thumbnailUrl().trim();
        thumbnails.add(thumbnailUrl);
        downloads.add(downloadUrl);
        types.add(asset.mediaType() == null ? MediaType.IMAGE : asset.mediaType());
      }
      return new ImageAssetCollection(
          Collections.unmodifiableList(thumbnails),
          Collections.unmodifiableList(downloads),
          Collections.unmodifiableList(types));
    }
  }

  private record VideoCandidate(
      int height, int width, int fps, int bitRate, long dataSize, String url) {}
}
