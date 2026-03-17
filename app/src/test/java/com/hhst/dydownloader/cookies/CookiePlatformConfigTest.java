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

  @Test
  public void tiktokConfig_readsCookiesFromTiktokHostsOnly() {
    CookiePlatformConfig config = CookiePlatformConfig.forPlatform(Platform.TIKTOK);

    assertEquals("https://www.tiktok.com/", config.cookieUrl());
    assertTrue(config.isTrustedWebUrl("https://www.tiktok.com/@creator/video/7345678901234567890"));
    assertTrue(config.isTrustedWebUrl("https://vm.tiktok.com/ZM123456/"));
    assertFalse(config.isTrustedWebUrl("https://accounts.google.com/o/oauth2/v2/auth"));
  }
}
