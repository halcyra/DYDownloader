package com.hhst.dydownloader;

import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public final class AppLocaleManager {
  public static final String TAG_EN = "en";
  public static final String TAG_ZH_CN = "zh-CN";
  public static final String TAG_ZH_TW = "zh-TW";

  private AppLocaleManager() {}

  public static void applySavedLocale(Context context) {
    AppCompatDelegate.setApplicationLocales(
        LocaleListCompat.forLanguageTags(AppPrefs.getLanguageTag(context)));
  }

  public static void setLocale(Context context, String languageTag) {
    AppPrefs.setLanguageTag(context, languageTag);
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag));
  }

  public static String getLanguageLabel(Context context, String languageTag) {
    return switch (languageTag) {
      case TAG_ZH_CN -> context.getString(R.string.language_option_zh_cn);
      case TAG_ZH_TW -> context.getString(R.string.language_option_zh_tw);
      default -> context.getString(R.string.language_option_en);
    };
  }
}
