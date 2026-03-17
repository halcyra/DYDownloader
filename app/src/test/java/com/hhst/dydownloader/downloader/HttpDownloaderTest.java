package com.hhst.dydownloader.downloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.hhst.dydownloader.model.Platform;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

public class HttpDownloaderTest {

  @Test
  public void download_appliesPlatformHeadersToHeadAndGet() throws Exception {
    AtomicReference<String> headCookie = new AtomicReference<>();
    AtomicReference<String> getCookie = new AtomicReference<>();
    AtomicReference<String> headReferer = new AtomicReference<>();
    AtomicReference<String> getReferer = new AtomicReference<>();
    AtomicReference<String> headUserAgent = new AtomicReference<>();
    AtomicReference<String> getUserAgent = new AtomicReference<>();
    OkHttpClient client =
        new OkHttpClient.Builder()
            .addInterceptor(
                chain -> {
                  Request request = chain.request();
                  if ("HEAD".equals(request.method())) {
                    headCookie.set(request.header("Cookie"));
                    headReferer.set(request.header("Referer"));
                    headUserAgent.set(request.header("User-Agent"));
                    return response(
                        request,
                        200,
                        "image/jpeg",
                        new byte[0],
                        String.valueOf(jpegBytes().length));
                  }
                  getCookie.set(request.header("Cookie"));
                  getReferer.set(request.header("Referer"));
                  getUserAgent.set(request.header("User-Agent"));
                  return response(request, 200, "image/jpeg", jpegBytes(), null);
                })
            .build();
    HttpDownloader downloader =
        new HttpDownloader(
            client, PlatformRequestContexts.forPlatform(Platform.TIKTOK, "sessionid=1"));
    File output = Files.createTempFile("http-downloader", ".jpg").toFile();
    output.deleteOnExit();

    downloader.download(
        "https://www.tiktok.com/file.jpg",
        output,
        null,
        HttpDownloader.ExpectedContent.IMAGE);

    assertEquals("sessionid=1", headCookie.get());
    assertEquals("sessionid=1", getCookie.get());
    assertEquals("https://www.tiktok.com/explore", headReferer.get());
    assertEquals("https://www.tiktok.com/explore", getReferer.get());
    assertEquals(headUserAgent.get(), getUserAgent.get());
  }

  @Test
  public void download_skipsCookieWhenHostIsOutsidePlatformAllowList() throws Exception {
    AtomicReference<String> headCookie = new AtomicReference<>("unset");
    AtomicReference<String> getCookie = new AtomicReference<>("unset");
    OkHttpClient client =
        new OkHttpClient.Builder()
            .addInterceptor(
                chain -> {
                  Request request = chain.request();
                  if ("HEAD".equals(request.method())) {
                    headCookie.set(request.header("Cookie"));
                    return response(
                        request,
                        200,
                        "image/jpeg",
                        new byte[0],
                        String.valueOf(jpegBytes().length));
                  }
                  getCookie.set(request.header("Cookie"));
                  return response(request, 200, "image/jpeg", jpegBytes(), null);
                })
            .build();
    HttpDownloader downloader =
        new HttpDownloader(
            client, PlatformRequestContexts.forPlatform(Platform.TIKTOK, "sessionid=1"));
    File output = Files.createTempFile("http-downloader-no-cookie", ".jpg").toFile();
    output.deleteOnExit();

    downloader.download(
        "https://cdn.example.com/file.jpg",
        output,
        null,
        HttpDownloader.ExpectedContent.IMAGE);

    assertNull(headCookie.get());
    assertNull(getCookie.get());
  }

  private static Response response(
      Request request, int code, String contentType, byte[] body, String contentLength) {
    Response.Builder builder =
        new Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .body(ResponseBody.create(body, okhttp3.MediaType.get(contentType)));
    if (contentLength != null) {
      builder.header("Content-Length", contentLength);
    }
    return builder.build();
  }

  private static byte[] jpegBytes() throws IOException {
    return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
  }
}
