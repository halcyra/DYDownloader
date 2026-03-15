package com.hhst.dydownloader;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
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
  public void appPrefs_cookieConfiguredFlagTracksStoredCookie() {
    AppPrefs.setCookie(appContext, "msToken=abc123; UIFID=uifid123");
    assertTrue(AppPrefs.hasConfiguredCookie(appContext));
    assertFalse(AppPrefs.getCookie(appContext).isBlank());

    AppPrefs.setCookie(appContext, "");
    assertFalse(AppPrefs.hasConfiguredCookie(appContext));
  }
}
