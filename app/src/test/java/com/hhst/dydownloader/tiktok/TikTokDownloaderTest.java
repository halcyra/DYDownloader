package com.hhst.dydownloader.tiktok;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TikTokDownloaderTest {

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
}
