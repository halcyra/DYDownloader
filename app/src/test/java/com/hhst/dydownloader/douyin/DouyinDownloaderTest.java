package com.hhst.dydownloader.douyin;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DouyinDownloaderTest {

  @Test
  public void trustedShareUrl_onlyAllowsDouyinHosts() {
    assertTrue(DouyinDownloader.isTrustedShareUrl("https://v.douyin.com/abcdefg/"));
    assertTrue(DouyinDownloader.isTrustedShareUrl("https://www.iesdouyin.com/share/video/123"));
    assertFalse(DouyinDownloader.isTrustedShareUrl("https://example.com/video/123"));
  }

  @Test
  public void containsDouyinLink_detectsDouyinUrlInShareText() {
    String text = "快来看这个作品 https://v.douyin.com/AbCdEfG/ 复制此链接";
    assertTrue(DouyinDownloader.containsDouyinLink(text));
    assertFalse(DouyinDownloader.containsDouyinLink("https://example.com/not-douyin"));
  }
}
