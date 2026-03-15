package com.hhst.dydownloader;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppPrefsTest {

  @Test
  public void hasAuthenticatedCookieValue_requiresLoginSessionCookie() {
    assertFalse(
        AppPrefs.hasAuthenticatedCookieValue(
            "passport_csrf_token=token; msToken=request-token; UIFID=device-id"));

    assertTrue(
        AppPrefs.hasAuthenticatedCookieValue(
            "sessionid=login-session; passport_csrf_token=token; msToken=request-token"));
  }

  @Test
  public void isConfiguredCookieValue_acceptsRequestCookieWithoutLoginSession() {
    assertTrue(
        AppPrefs.isConfiguredCookieValue(
            "passport_csrf_token=token; msToken=request-token; UIFID=device-id"));
  }
}
