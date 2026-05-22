package com.hhst.dydownloader;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppLocaleManagerTest {

  @Test
  public void normalizeLanguageTag_acceptsCurrentAndLegacyValues() {
    assertEquals(AppLocaleManager.TAG_EN, AppLocaleManager.normalizeLanguageTag(null));
    assertEquals(AppLocaleManager.TAG_EN, AppLocaleManager.normalizeLanguageTag(""));
    assertEquals(AppLocaleManager.TAG_EN, AppLocaleManager.normalizeLanguageTag("English"));
    assertEquals(AppLocaleManager.TAG_ZH_CN, AppLocaleManager.normalizeLanguageTag("中文"));
    assertEquals(AppLocaleManager.TAG_ZH_CN, AppLocaleManager.normalizeLanguageTag("简体中文"));
    assertEquals(AppLocaleManager.TAG_ZH_CN, AppLocaleManager.normalizeLanguageTag("簡體中文"));
    assertEquals(AppLocaleManager.TAG_ZH_TW, AppLocaleManager.normalizeLanguageTag("繁體中文"));
    assertEquals(AppLocaleManager.TAG_ZH_TW, AppLocaleManager.normalizeLanguageTag("繁体中文"));
    assertEquals(AppLocaleManager.TAG_EN, AppLocaleManager.normalizeLanguageTag("unsupported"));
  }

  @Test
  public void getLanguageLabel_returnsStableSelfNames() {
    assertEquals("English", AppLocaleManager.getLanguageLabel(AppLocaleManager.TAG_EN));
    assertEquals("简体中文", AppLocaleManager.getLanguageLabel(AppLocaleManager.TAG_ZH_CN));
    assertEquals("繁體中文", AppLocaleManager.getLanguageLabel(AppLocaleManager.TAG_ZH_TW));
  }
}
