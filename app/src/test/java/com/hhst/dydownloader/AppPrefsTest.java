package com.hhst.dydownloader;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hhst.dydownloader.model.Platform;
import org.junit.Test;

public class AppPrefsTest {

  @Test
  public void douyinAuthenticatedCookieValue_requiresLoginSessionCookie() {
    assertFalse(
        AppPrefs.hasAuthenticatedCookieValue(
            Platform.DOUYIN,
            "passport_csrf_token=token; msToken=request-token; UIFID=device-id"));

    assertTrue(
        AppPrefs.hasAuthenticatedCookieValue(
            Platform.DOUYIN,
            "sessionid=login-session; passport_csrf_token=token; msToken=request-token"));
  }

  @Test
  public void douyinConfiguredCookieValue_acceptsRequestCookieWithoutLoginSession() {
    assertTrue(
        AppPrefs.isConfiguredCookieValue(
            Platform.DOUYIN,
            "passport_csrf_token=token; msToken=request-token; UIFID=device-id"));
  }

  @Test
  public void tiktokConfiguredCookieValue_acceptsRequestCookieWithoutLoginSession() {
    assertTrue(AppPrefs.isConfiguredCookieValue(Platform.TIKTOK, "msToken=req-token; ttwid=wid"));
  }

  @Test
  public void tiktokAuthenticatedCookieValue_requiresSessionIdSs() {
    assertTrue(AppPrefs.hasAuthenticatedCookieValue(Platform.TIKTOK, "sessionid_ss=login"));
    assertFalse(AppPrefs.hasAuthenticatedCookieValue(Platform.TIKTOK, "msToken=req-token"));
  }
}
