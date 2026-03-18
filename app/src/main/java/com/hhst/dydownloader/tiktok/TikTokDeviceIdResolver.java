package com.hhst.dydownloader.tiktok;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class TikTokDeviceIdResolver {
  private static final String EXPLORE_URL = "https://www.tiktok.com/explore";
  private static final Pattern DEVICE_ID_PATTERN = Pattern.compile("\"wid\":\"(\\d{19})\"");
  private final OkHttpClient httpClient;

  public TikTokDeviceIdResolver() {
    this(
        new OkHttpClient.Builder()
            .followRedirects(true)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build());
  }

  public TikTokDeviceIdResolver(OkHttpClient httpClient) {
    this.httpClient = httpClient;
  }

  static String extractDeviceId(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }
    Matcher matcher = DEVICE_ID_PATTERN.matcher(html);
    return matcher.find() ? matcher.group(1) : "";
  }

  static String extractCookieAdditions(List<String> setCookieHeaders) {
    if (setCookieHeaders == null || setCookieHeaders.isEmpty()) {
      return "";
    }
    List<String> cookiePairs = new ArrayList<>();
    for (String header : setCookieHeaders) {
      if (header == null || header.isBlank()) {
        continue;
      }
      int separator = header.indexOf(';');
      cookiePairs.add(separator >= 0 ? header.substring(0, separator).trim() : header.trim());
    }
    return String.join("; ", cookiePairs);
  }

  public Result resolve(String userAgent, String cookie) throws IOException {
    Request.Builder requestBuilder =
        new Request.Builder()
            .url(EXPLORE_URL)
            .header(
                "User-Agent", userAgent == null || userAgent.isBlank() ? "Mozilla/5.0" : userAgent)
            .header("Referer", "https://www.tiktok.com/");
    if (cookie != null && !cookie.isBlank()) {
      requestBuilder.header("Cookie", cookie);
    }
    try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("Failed to resolve TikTok device_id: HTTP " + response.code());
      }
      String body = response.body() == null ? "" : response.body().string();
      String deviceId = extractDeviceId(body);
      return new Result(deviceId, extractCookieAdditions(response.headers("Set-Cookie")));
    }
  }

  public record Result(String deviceId, String cookieAdditions) {}
}
