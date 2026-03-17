package com.hhst.dydownloader.cookies;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hhst.dydownloader.model.Platform;
import org.junit.Test;

public class CookiePlatformConfigTest {

  @Test
  public void tiktokConfig_usesLoginUrlAndMobileUa() {
    CookiePlatformConfig config = CookiePlatformConfig.forPlatform(Platform.TIKTOK);

    assertEquals("https://www.tiktok.com/login", config.loginUrl());
    assertFalse(config.forceDesktopUserAgent());
    assertTrue(config.allowExternalAppRedirects());
  }

  @Test
  public void douyinConfig_keepsDesktopUaFlow() {
    CookiePlatformConfig config = CookiePlatformConfig.forPlatform(Platform.DOUYIN);

    assertEquals("https://www.douyin.com/user/self", config.loginUrl());
    assertTrue(config.forceDesktopUserAgent());
    assertFalse(config.allowExternalAppRedirects());
  }
}
