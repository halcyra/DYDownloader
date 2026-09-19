package com.hhst.dydownloader.tiktok;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class TikTokRequestSignerTest {
  private static final String USER_AGENT =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
          + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36";

  private static final long[] KEY1 = {
    2653228291L, 3975144315L, 2290358181L, 3048911309L, 1571306751L, 2452336048L,
    2515937522L, 3143464376L, 711621471L, 3329457035L, 3540658286L, 1429319277L,
  };
  private static final long[] KEY2 = {
    1647999605L, 2726762725L, 3391361277L, 1545524063L, 3958785211L, 4276264787L,
    1321275334L, 1681700205L, 4261668719L, 2994107253L, 880236376L, 3270773552L,
  };

  /** 与上游 Python 实现逐字节比对的固定向量（DouK-Downloader src/encrypt/tiktok_sign.py）。 */
  @Test
  public void sign_matchesUpstreamReferenceVector() {
    String signed =
        TikTokRequestSigner.sign(
            "aid=1988&count=2&itemId=7345678901234567890&device_id=1234567890123456789",
            USER_AGENT,
            "test_ms_token_123",
            1758213600L,
            123456789L,
            3307312974L,
            KEY1,
            KEY2,
            TikTokRequestSigner.CALL_SEQUENCE_START);

    String expectedXDynosaur =
        "MRasCS7fHqM/Nb/CI2ZXkgKFaDJzPkI0KRtJ/oujPUcDq5rbWZe6goxH/cPfqVmbQeF8ijsP-DN7fDRjWyvGaqp"
            + "/7LFHs5YBDGYHnwoiDujXTh/8LozPBaoUxdIkLARwBqyObSZTSBibYbBMsx3IG5NrgzOkUlw2kC1zadwYh"
            + "x-CfN/2DZD22diI0r1Yw-QEUDX1FiCNw1rLRlLTs8VBv-0C3vO1ofg0ZJegkACCcnrPeTI5CjOicTU-GCO"
            + "5557Q7BnX090uivi/Ej9JpS0o9k83SJQlN92GsHBKA9l7tdDlHE/SNz9P0w/dqk1VHvDor8IErsE9J4imn"
            + "gpHWt-Yt4V0slN15Fmf036sh-75VMTb5UQq75uZ4FexD3IZAPdvN3dPXAnBtxFu";
    String expectedXGnarly =
        "MOMZ7sGKAizANKEab9XDsaemsEYbaiYX2cEc8IP0X1FC-3Vyxk-c7s6Ff4Ns7OZk9PX9NbK2-AHMkxyjjI6oU"
            + "iuKYcQQXRRWAz3kjOn1L5AXuLMBEpnvNRq3MiWSru6hiUXIJyb2Ok1IAMfr7cyA1wRnhRGvMb3n3WarpCR"
            + "ORbTT8bCKHToATCU15RFGnnl63YiWR5C7gJ9KiNqXqGfDpBs/KuWYf22GHHa99k0oayyW9cf2IZ6yuo7Vd"
            + "jafPKNP/YaQm1pwNu4VL5LXqnqhDKdntBJ7Lymt1i9Nc0e9h6LkY7FhWYXPlITooltaRnwn9dI=";

    assertEquals(
        "aid=1988&count=2&itemId=7345678901234567890&device_id=1234567890123456789"
            + "&X-Dynosaur="
            + expectedXDynosaur
            + "&msToken=test_ms_token_123&X-Bogus=1&X-Gnarly="
            + expectedXGnarly,
        signed);
  }

  @Test
  public void sign_parameterOrderFollowsSdk() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("aid", "1988");
    params.put("count", "2");
    String signed =
        TikTokRequestSigner.sign(params, USER_AGENT, "token", 1758213600L, 42L, 43L, KEY1, KEY2, null);

    assertTrue(signed.startsWith("aid=1988&count=2&X-Dynosaur="));
    assertTrue(
        signed.indexOf("X-Dynosaur=") < signed.indexOf("msToken=token")
            && signed.indexOf("msToken=token") < signed.indexOf("X-Bogus=1")
            && signed.indexOf("X-Bogus=1") < signed.indexOf("X-Gnarly="));
  }

  @Test
  public void sign_extractEmbeddedMsTokenInsteadOfDuplicating() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("aid", "1988");
    params.put("msToken", "embedded_token");
    String signed =
        TikTokRequestSigner.sign(params, USER_AGENT, "passed_token", 1758213600L, 42L, 43L, KEY1, KEY2, null);

    assertEquals(1, signed.split("msToken=", -1).length - 1);
    assertTrue(signed.contains("msToken=embedded_token"));
  }

  @Test
  public void encodeQuery_usesBrowserEncoding() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("browser_version", "5.0 (Windows)");
    params.put("root_referer", "https://www.tiktok.com/");
    params.put("tz_name", "America/Los_Angeles");
    params.put("q", "中");
    // 增补平面字符须按整体代理对取 UTF-8 字节
    params.put("emoji", "\uD835\uDD4F");

    String encoded =
        TikTokRequestSigner.sign(params, USER_AGENT, "", 1758213600L, 42L, 43L, KEY1, KEY2, null);
    // 括号、斜杠、冒号保持原样；空格为 %20；非 ASCII 转为大写 UTF-8 百分号编码
    assertTrue(
        encoded.startsWith(
            "browser_version=5.0%20(Windows)&root_referer=https://www.tiktok.com/"
                + "&tz_name=America/Los_Angeles&q=%E4%B8%AD&emoji=%F0%9D%95%8F&"));
  }

  @Test
  public void hashState_matchesUpstreamReferenceValues() {
    assertEquals(1835302635L, TikTokRequestSigner.hashState("aid=6383"));
  }
}
