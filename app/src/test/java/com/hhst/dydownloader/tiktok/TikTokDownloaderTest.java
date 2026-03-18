package com.hhst.dydownloader.tiktok;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hhst.dydownloader.douyin.AwemeProfile;
import com.hhst.dydownloader.douyin.MediaType;
import com.hhst.dydownloader.model.Platform;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class TikTokDownloaderTest {
  private static final String WORK_ITEM_ID = "7345678901234567890";
  private static final String WORK_PATH = "/@creator/video/" + WORK_ITEM_ID;
  private static final String SHORT_WORK_SHARE_URL = "https://vt.tiktok.com/ZSuXJ91x8/";

  @Test
  public void trustedShareUrl_onlyAllowsTikTokHosts() {
    assertTrue(TikTokDownloader.isTrustedShareUrl("https://vm.tiktok.com/ZM1234567/"));
    assertTrue(
        TikTokDownloader.isTrustedShareUrl(
            "https://www.tiktok.com/@user/video/7345678901234567890"));
    assertFalse(TikTokDownloader.isTrustedShareUrl("https://example.com/video/7345678901234567890"));
  }

  @Test
  public void containsTikTokLink_detectsShareText() {
    assertTrue(TikTokDownloader.containsTikTokLink("watch https://vm.tiktok.com/ZM1234567/ now"));
    assertFalse(TikTokDownloader.containsTikTokLink("https://example.com/not-tiktok"));
  }

  @Test
  public void isAccountLink_detectsUserProfileLinks() {
    assertTrue(TikTokDownloader.isAccountLink("https://www.tiktok.com/@creator"));
    assertFalse(TikTokDownloader.isAccountLink("https://www.tiktok.com/@creator/video/1"));
  }

  @Test
  public void isMixLink_detectsCollectionLinks() {
    assertTrue(
        TikTokDownloader.isMixLink(
            "https://www.tiktok.com/@creator/collection/list-7345678901234567890"));
    assertFalse(TikTokDownloader.isMixLink("https://www.tiktok.com/@creator"));
  }

  @Test
  public void collectAccountWorksInfo_readsSecUidFromHtmlAndDeduplicatesAcrossPages()
      throws Exception {
    TikTokDownloader downloader = new TikTokDownloader(buildAccountClient(), "msToken=base");

    List<AwemeProfile> profiles =
        downloader.collectAccountWorksInfo("https://www.tiktok.com/@creator", "msToken=base");

    assertEquals(2, profiles.size());
    assertEquals("7345678901234567890", profiles.get(0).awemeId());
    assertEquals("7345678901234567891", profiles.get(1).awemeId());
    assertEquals(Platform.TIKTOK, profiles.get(0).platform());
    assertEquals(MediaType.VIDEO, profiles.get(0).mediaType());
    assertEquals(MediaType.IMAGE, profiles.get(1).mediaType());
  }

  @Test
  public void collectAccountWorksInfo_prefersSecUidFromUrlBeforeHtmlFallback() throws Exception {
    TikTokDownloader downloader =
        new TikTokDownloader(buildAccountClientPreferringUrlSecUid(), "msToken=base");

    List<AwemeProfile> profiles =
        downloader.collectAccountWorksInfo(
            "https://www.tiktok.com/@creator?secUid=MS4wLjABAAAAurl1234", "msToken=base");

    assertEquals(1, profiles.size());
    assertEquals("7345678901234567890", profiles.get(0).awemeId());
    assertEquals(Platform.TIKTOK, profiles.get(0).platform());
  }

  @Test
  public void collectMixWorksInfo_readsCollectionIdFromUrlAndDeduplicatesAcrossPages()
      throws Exception {
    TikTokDownloader downloader = new TikTokDownloader(buildCollectionClient(), "msToken=base");

    List<AwemeProfile> profiles =
        downloader.collectMixWorksInfo(
            "https://www.tiktok.com/@creator/collection/list-7345678901234567890",
            "msToken=base");

    assertEquals(2, profiles.size());
    assertEquals("7345678901234567890", profiles.get(0).awemeId());
    assertEquals("7345678901234567891", profiles.get(1).awemeId());
    assertEquals(Platform.TIKTOK, profiles.get(1).platform());
    assertEquals(MediaType.IMAGE, profiles.get(1).mediaType());
    assertEquals("Collection title", profiles.get(1).collectionTitle());
  }

  @Test
  public void collectMixWorksInfo_readsCollectionIdFromHtmlFallback() throws Exception {
    TikTokDownloader downloader =
        new TikTokDownloader(buildCollectionClientUsingHtmlFallback(), "msToken=base");

    List<AwemeProfile> profiles =
        downloader.collectMixWorksInfo("https://www.tiktok.com/@creator/collection/list", "msToken=base");

    assertEquals(1, profiles.size());
    assertEquals("7345678901234567890", profiles.get(0).awemeId());
    assertEquals("Collection title", profiles.get(0).collectionTitle());
  }

  @Test
  public void collectWorkInfo_readsItemStructFromHtmlFallbackWhenDetailPayloadIsEmpty()
      throws Exception {
    TikTokDownloader downloader =
        new TikTokDownloader(buildWorkClientUsingHtmlFallback(), "msToken=base");

    AwemeProfile profile =
        downloader.collectWorkInfo("https://www.tiktok.com/@creator/video/" + WORK_ITEM_ID, "msToken=base");

    assertEquals(WORK_ITEM_ID, profile.awemeId());
    assertEquals(Platform.TIKTOK, profile.platform());
    assertEquals(MediaType.VIDEO, profile.mediaType());
    assertEquals("work-desc", profile.desc());
  }

  @Test
  public void collectWorkInfo_readsItemStructFromHtmlWhenShortLinkHasNoResolvableDeviceId()
      throws Exception {
    TikTokDownloader downloader =
        new TikTokDownloader(buildShortLinkWorkClientWithoutDeviceId(), "msToken=base");

    AwemeProfile profile = downloader.collectWorkInfo(SHORT_WORK_SHARE_URL, "msToken=base");

    assertEquals(WORK_ITEM_ID, profile.awemeId());
    assertEquals(Platform.TIKTOK, profile.platform());
    assertEquals(MediaType.VIDEO, profile.mediaType());
    assertEquals("work-desc", profile.desc());
  }

  private static OkHttpClient buildAccountClient() {
    return new OkHttpClient.Builder()
        .followRedirects(true)
        .addInterceptor(
            chain -> {
              Request request = chain.request();
              String url = request.url().toString();
              String encodedPath = request.url().encodedPath();
              if ("/explore".equals(encodedPath)) {
                return response(
                    request,
                    200,
                    "text/html",
                    "{\"wid\":\"7350000000000000000\"}",
                    "ttwid=ttwid-cookie; Path=/");
              }
              if ("/@creator".equals(encodedPath)) {
                return response(
                    request,
                    200,
                    "text/html",
                    "<html>\"verified\":true,\"secUid\":\"MS4wLjABAAAA1234\"</html>");
              }
              if ("/api/post/item_list/".equals(encodedPath)) {
                String cursor = request.url().queryParameter("cursor");
                if ("1".equals(cursor)) {
                  return response(
                      request,
                      200,
                      "application/json",
                      "{\"itemList\":["
                          + accountVideoItem("7345678901234567890")
                          + ","
                          + accountImageItem("7345678901234567891")
                          + "],\"hasMore\":false,\"cursor\":\"1\"}");
                }
                return response(
                    request,
                    200,
                    "application/json",
                    "{\"itemList\":["
                        + accountVideoItem("7345678901234567890")
                        + "],\"hasMore\":true,\"cursor\":\"1\"}");
              }
              throw new IOException("Unexpected URL: " + url);
            })
        .build();
  }

  private static OkHttpClient buildCollectionClient() {
    return new OkHttpClient.Builder()
        .followRedirects(true)
        .addInterceptor(
            chain -> {
              Request request = chain.request();
              String url = request.url().toString();
              String encodedPath = request.url().encodedPath();
              if ("/explore".equals(encodedPath)) {
                return response(
                    request,
                    200,
                    "text/html",
                    "{\"wid\":\"7350000000000000000\"}",
                    "msToken=resolved-token; Path=/");
              }
              if ("/api/collection/item_list/".equals(encodedPath)) {
                String cursor = request.url().queryParameter("cursor");
                if ("1".equals(cursor)) {
                  return response(
                      request,
                      200,
                      "application/json",
                      "{\"itemList\":["
                          + collectionVideoItem("7345678901234567890")
                          + ","
                          + collectionImageItem("7345678901234567891")
                          + "],\"hasMore\":false,\"cursor\":\"1\"}");
                }
                return response(
                    request,
                    200,
                    "application/json",
                    "{\"itemList\":["
                        + collectionVideoItem("7345678901234567890")
                        + "],\"hasMore\":true,\"cursor\":\"1\"}");
              }
              throw new IOException("Unexpected URL: " + url);
            })
        .build();
  }

  private static OkHttpClient buildAccountClientPreferringUrlSecUid() {
    return new OkHttpClient.Builder()
        .followRedirects(true)
        .addInterceptor(
            chain -> {
              Request request = chain.request();
              String url = request.url().toString();
              String encodedPath = request.url().encodedPath();
              if ("/explore".equals(encodedPath)) {
                return response(
                    request,
                    200,
                    "text/html",
                    "{\"wid\":\"7350000000000000000\"}",
                    "ttwid=ttwid-cookie; Path=/");
              }
              if ("/@creator".equals(encodedPath)) {
                return response(request, 200, "text/html", "<html>missing-secuid</html>");
              }
              if ("/api/post/item_list/".equals(encodedPath)) {
                assertEquals("MS4wLjABAAAAurl1234", request.url().queryParameter("secUid"));
                return response(
                    request,
                    200,
                    "application/json",
                    "{\"itemList\":[" + accountVideoItem("7345678901234567890") + "],\"hasMore\":false,\"cursor\":\"0\"}");
              }
              throw new IOException("Unexpected URL: " + url);
            })
        .build();
  }

  private static OkHttpClient buildCollectionClientUsingHtmlFallback() {
    return new OkHttpClient.Builder()
        .followRedirects(true)
        .addInterceptor(
            chain -> {
              Request request = chain.request();
              String url = request.url().toString();
              String encodedPath = request.url().encodedPath();
              if ("/explore".equals(encodedPath)) {
                return response(
                    request,
                    200,
                    "text/html",
                    "{\"wid\":\"7350000000000000000\"}",
                    "msToken=resolved-token; Path=/");
              }
              if ("/@creator/collection/list".equals(encodedPath)) {
                return response(
                    request,
                    200,
                    "text/html",
                    "<html>\"canonical\":\"https://www.tiktok.com/@creator/collection/list-7345678901234567890\"</html>");
              }
              if ("/api/collection/item_list/".equals(encodedPath)) {
                assertEquals("7345678901234567890", request.url().queryParameter("collectionId"));
                return response(
                    request,
                    200,
                    "application/json",
                    "{\"itemList\":[" + collectionVideoItem("7345678901234567890") + "],\"hasMore\":false,\"cursor\":\"0\"}");
              }
              throw new IOException("Unexpected URL: " + url);
            })
        .build();
  }

  private static OkHttpClient buildWorkClientUsingHtmlFallback() {
    return new OkHttpClient.Builder()
        .followRedirects(true)
        .addInterceptor(
            chain -> {
              Request request = chain.request();
              String url = request.url().toString();
              String encodedPath = request.url().encodedPath();
              if ("/explore".equals(encodedPath)) {
                return response(
                    request,
                    200,
                    "text/html",
                    "{\"wid\":\"7350000000000000000\"}",
                    "msToken=resolved-token; Path=/");
              }
              if ("/api/item/detail/".equals(encodedPath)) {
                return response(request, 200, "application/json", "{\"itemInfo\":{}}");
              }
              if (WORK_PATH.equals(encodedPath)) {
                return workPageResponse(request);
              }
              throw new IOException("Unexpected URL: " + url);
            })
        .build();
  }

  private static OkHttpClient buildShortLinkWorkClientWithoutDeviceId() {
    return new OkHttpClient.Builder()
        .followRedirects(true)
        .addInterceptor(
            chain -> {
              Request request = chain.request();
              String url = request.url().toString();
              String encodedPath = request.url().encodedPath();
              if ("vt.tiktok.com".equals(request.url().host())
                  && "/ZSuXJ91x8/".equals(encodedPath)) {
                Request finalRequest =
                    request.newBuilder().url("https://www.tiktok.com" + WORK_PATH).build();
                return response(finalRequest, 200, "text/html", "");
              }
              if ("/explore".equals(encodedPath)) {
                return response(request, 200, "text/html", "<html>missing-wid</html>");
              }
              if ("/api/item/detail/".equals(encodedPath)) {
                throw new IOException("Detail API should not be called when HTML already contains the item");
              }
              if (WORK_PATH.equals(encodedPath)) {
                return workPageResponse(request);
              }
              throw new IOException("Unexpected URL: " + url);
            })
        .build();
  }

  private static Response workPageResponse(Request request) {
    return response(request, 200, "text/html", workPageHtml("work-desc"));
  }

  private static String workPageHtml(String desc) {
    return "<script>window.__UNIVERSAL_DATA_FOR_REHYDRATION__={\"itemStruct\":"
        + accountVideoItem(WORK_ITEM_ID).replace("video-" + WORK_ITEM_ID, desc)
        + "}</script>";
  }

  private static Response response(Request request, int code, String contentType, String body) {
    return response(request, code, contentType, body, null);
  }

  private static Response response(
      Request request, int code, String contentType, String body, String setCookieHeader) {
    Response.Builder builder =
        new Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .body(ResponseBody.create(body, okhttp3.MediaType.get(contentType)));
    if (setCookieHeader != null) {
      builder.addHeader("Set-Cookie", setCookieHeader);
    }
    return builder.build();
  }

  private static String accountVideoItem(String id) {
    return "{"
        + "\"id\":\""
        + id
        + "\","
        + "\"desc\":\"video-"
        + id
        + "\","
        + "\"createTime\":1710000000,"
        + "\"author\":{\"nickname\":\"creator\",\"secUid\":\"MS4wLjABAAAA1234\"},"
        + "\"video\":{\"cover\":\"https://img.example.com/"
        + id
        + ".jpg\",\"playAddr\":\"https://video.example.com/"
        + id
        + ".mp4\"}"
        + "}";
  }

  private static String accountImageItem(String id) {
    return "{"
        + "\"id\":\""
        + id
        + "\","
        + "\"desc\":\"image-"
        + id
        + "\","
        + "\"createTime\":1710000001,"
        + "\"author\":{\"nickname\":\"creator\",\"secUid\":\"MS4wLjABAAAA1234\"},"
        + "\"imagePost\":{\"images\":[{\"imageURL\":{\"urlList\":[\"https://img.example.com/"
        + id
        + ".jpeg\"]}}]}"
        + "}";
  }

  private static String collectionVideoItem(String id) {
    return "{"
        + "\"id\":\""
        + id
        + "\","
        + "\"desc\":\"video-"
        + id
        + "\","
        + "\"createTime\":1710000000,"
        + "\"author\":{\"nickname\":\"creator\",\"secUid\":\"MS4wLjABAAAA1234\"},"
        + "\"video\":{\"cover\":\"https://img.example.com/"
        + id
        + ".jpg\",\"playAddr\":\"https://video.example.com/"
        + id
        + ".mp4\"},"
        + "\"collectionTitle\":\"Collection title\""
        + "}";
  }

  private static String collectionImageItem(String id) {
    return "{"
        + "\"id\":\""
        + id
        + "\","
        + "\"desc\":\"image-"
        + id
        + "\","
        + "\"createTime\":1710000001,"
        + "\"author\":{\"nickname\":\"creator\",\"secUid\":\"MS4wLjABAAAA1234\"},"
        + "\"imagePost\":{\"images\":[{\"imageURL\":{\"urlList\":[\"https://img.example.com/"
        + id
        + ".jpeg\"]}}]},"
        + "\"collectionTitle\":\"Collection title\""
        + "}";
  }
}
