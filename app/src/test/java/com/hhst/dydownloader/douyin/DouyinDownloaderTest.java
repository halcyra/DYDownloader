package com.hhst.dydownloader.douyin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DouyinDownloaderTest {

  @Test
  public void listFetch_retriesHttpAndApiErrorsInsteadOfReportingNoWorks() throws Exception {
    java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
    okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
        .addInterceptor(chain -> {
          int attempt = calls.incrementAndGet();
          String body = attempt == 1 ? "{\"status_code\":0,\"aweme_list\":[]}"
              : attempt == 2 ? "{\"status_code\":8,\"aweme_list\":[]}"
              : "{\"status_code\":0,\"aweme_list\":[{\"aweme_id\":\"123\"}]}";
          return new okhttp3.Response.Builder().request(chain.request())
              .protocol(okhttp3.Protocol.HTTP_1_1).code(attempt == 1 ? 403 : 200).message("test")
              .body(okhttp3.ResponseBody.create(body, okhttp3.MediaType.get("application/json")))
              .build();
        }).build();
    DouyinDownloader downloader = new DouyinDownloader(client, "");
    java.lang.reflect.Method fetch = DouyinDownloader.class.getDeclaredMethod(
        "fetchPagedAwemeList", java.util.List.class, String.class, String.class);
    fetch.setAccessible(true);
    com.fasterxml.jackson.databind.JsonNode result = (com.fasterxml.jackson.databind.JsonNode)
        fetch.invoke(downloader, java.util.List.of("https://www.douyin.com/primary", "https://www.iesdouyin.com/fallback"), "", "account");
    assertEquals(3, calls.get());
    assertEquals("123", result.path("aweme_list").get(0).path("aweme_id").asText());
  }

  @Test
  public void trustedShareUrl_onlyAllowsDouyinHosts() {
    assertTrue(DouyinDownloader.isTrustedShareUrl("https://v.douyin.com/abcdefg/"));
    assertTrue(DouyinDownloader.isTrustedShareUrl("https://www.iesdouyin.com/share/video/123"));
    assertFalse(DouyinDownloader.isTrustedShareUrl("https://example.com/video/123"));
  }
}
