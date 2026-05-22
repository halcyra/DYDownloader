package com.hhst.dydownloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppPrefsInstrumentedTest {
  private final Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

  @After
  public void tearDown() {
    AppPrefs.setCookie(appContext, "");
  }

  @Test
  public void appContext_packageNameIsCorrect() {
    assertEquals("com.hhst.dydownloader", appContext.getPackageName());
  }

  @Test
  public void cookieConfiguredFlag_tracksStoredCookie() {
    AppPrefs.setCookie(appContext, "msToken=abc123; UIFID=uifid123");
    assertTrue(AppPrefs.hasConfiguredCookie(appContext));
    assertFalse(AppPrefs.getCookie(appContext).isBlank());

    AppPrefs.setCookie(appContext, "");
    assertFalse(AppPrefs.hasConfiguredCookie(appContext));
  }
}
