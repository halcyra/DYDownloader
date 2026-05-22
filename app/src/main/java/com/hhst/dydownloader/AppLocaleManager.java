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
    AppCompatDelegate.setApplicationLocales(
        LocaleListCompat.forLanguageTags(AppPrefs.getLanguageTag(context)));
  }

  public static String getLanguageLabel(String languageTag) {
    return switch (normalizeLanguageTag(languageTag)) {
      case TAG_ZH_CN -> "简体中文";
      case TAG_ZH_TW -> "繁體中文";
      default -> "English";
    };
  }

  public static String normalizeLanguageTag(String languageTag) {
    if (languageTag == null || languageTag.isBlank()) {
      return TAG_EN;
    }
    return switch (languageTag) {
      case "English", TAG_EN -> TAG_EN;
      case "中文", "简体中文", "簡體中文", TAG_ZH_CN -> TAG_ZH_CN;
      case "繁體中文", "繁体中文", TAG_ZH_TW -> TAG_ZH_TW;
      default -> TAG_EN;
    };
  }
}
