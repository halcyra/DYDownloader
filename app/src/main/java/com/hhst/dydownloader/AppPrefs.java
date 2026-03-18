package com.hhst.dydownloader;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;
import com.hhst.dydownloader.model.CardType;
import com.hhst.dydownloader.model.Platform;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class AppPrefs {
  public static final String PREFS = "dydownloader_prefs";
  public static final String KEY_COOKIE = "cookie";
  public static final String KEY_LANGUAGE = "language";
  public static final String KEY_HOME_SORT = "home_sort";
  public static final String KEY_HOME_FILTER = "home_filter";
  private static final String TAG = "AppPrefs";
  private static final String KEY_COOKIE_ENCRYPTED = "cookie_encrypted";
  private static final String KEY_TIKTOK_COOKIE = "cookie_tiktok";
  private static final String KEY_TIKTOK_COOKIE_ENCRYPTED = "cookie_tiktok_encrypted";
  private static final String COOKIE_KEYSTORE = "AndroidKeyStore";
  private static final String COOKIE_KEY_ALIAS = "dydownloader_cookie_key";
  private static final String COOKIE_CIPHER = "AES/GCM/NoPadding";
  private static final String COOKIE_VALUE_PREFIX = "v1";
  private static final String[] DOUYIN_COOKIE_LOGIN_FIELDS = {
    "sessionid", "sessionid_ss", "sid_guard"
  };
  private static final String[] DOUYIN_COOKIE_REQUEST_FIELDS = {
    "msToken", "UIFID", "uifid", "passport_csrf_token"
  };
  private static final String[] TIKTOK_COOKIE_LOGIN_FIELDS = {"sessionid_ss", "sessionid"};
  private static final String[] TIKTOK_COOKIE_REQUEST_FIELDS = {
    "msToken", "ttwid", "tt_chain_token"
  };

  private AppPrefs() {}

  public static SharedPreferences prefs(Context context) {
    return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  public static String getCookie(Context context) {
    return getCookie(context, Platform.DOUYIN);
  }

  public static String getCookie(Context context, Platform platform) {
    SharedPreferences sharedPreferences = prefs(context);
    String encryptedCookie = sharedPreferences.getString(encryptedCookieKey(platform), "");
    if (!encryptedCookie.isBlank()) {
      String decryptedCookie = decryptCookie(encryptedCookie);
      if (isConfiguredCookieValue(platform, decryptedCookie)) {
        return normalizeCookie(decryptedCookie);
      }
      sharedPreferences
          .edit()
          .remove(encryptedCookieKey(platform))
          .remove(cookieStorageKey(platform))
          .apply();
      return "";
    }

    String legacyCookie = sharedPreferences.getString(cookieStorageKey(platform), "");
    if (!isConfiguredCookieValue(platform, legacyCookie)) {
      if (!legacyCookie.isBlank()) {
        sharedPreferences.edit().remove(cookieStorageKey(platform)).apply();
      }
      return "";
    }

    String normalizedCookie = normalizeCookie(legacyCookie);
    setCookie(context, platform, normalizedCookie);
    return normalizedCookie;
  }

  public static void setCookie(Context context, String cookie) {
    setCookie(context, Platform.DOUYIN, cookie);
  }

  public static void setCookie(Context context, Platform platform, String cookie) {
    SharedPreferences.Editor editor = prefs(context).edit();
    String normalizedCookie = normalizeCookie(cookie);
    if (!isConfiguredCookieValue(platform, normalizedCookie)) {
      editor.remove(cookieStorageKey(platform)).remove(encryptedCookieKey(platform)).apply();
      return;
    }

    String encryptedCookie = encryptCookie(normalizedCookie);
    if (encryptedCookie.isBlank()) {
      Log.w(TAG, "Failed to encrypt cookie, clearing stored value");
      editor.remove(cookieStorageKey(platform)).remove(encryptedCookieKey(platform)).apply();
      return;
    }

    editor
        .putString(encryptedCookieKey(platform), encryptedCookie)
        .remove(cookieStorageKey(platform))
        .apply();
  }

  public static boolean hasConfiguredCookie(Context context) {
    return hasConfiguredCookie(context, Platform.DOUYIN);
  }

  public static boolean hasConfiguredCookie(Context context, Platform platform) {
    return isConfiguredCookieValue(platform, getCookie(context, platform));
  }

  public static boolean hasAuthenticatedCookie(Context context) {
    return hasAuthenticatedCookie(context, Platform.DOUYIN);
  }

  public static boolean hasAuthenticatedCookie(Context context, Platform platform) {
    return hasAuthenticatedCookieValue(platform, getCookie(context, platform));
  }

  public static boolean isConfiguredCookieValue(String cookie) {
    return isConfiguredCookieValue(Platform.DOUYIN, cookie);
  }

  public static boolean isConfiguredCookieValue(Platform platform, String cookie) {
    String normalizedCookie = normalizeCookie(cookie);
    if (normalizedCookie.isBlank()) {
      return false;
    }
    boolean hasAuthField = hasAuthenticatedCookieValue(platform, normalizedCookie);
    boolean hasRequestField = hasAnyCookieField(normalizedCookie, cookieRequestFields(platform));
    return hasAuthField || hasRequestField;
  }

  public static boolean hasAuthenticatedCookieValue(String cookie) {
    return hasAuthenticatedCookieValue(Platform.DOUYIN, cookie);
  }

  public static boolean hasAuthenticatedCookieValue(Platform platform, String cookie) {
    String normalizedCookie = normalizeCookie(cookie);
    if (normalizedCookie.isBlank()) {
      return false;
    }
    return hasAnyCookieField(normalizedCookie, cookieLoginFields(platform));
  }

  public static String getLanguageTag(Context context) {
    String raw = prefs(context).getString(KEY_LANGUAGE, "en");
    if (raw.isBlank()) {
      return "en";
    }
    return switch (raw) {
      case "English" -> "en";
      case "中文", "简体中文" -> "zh-CN";
      case "繁體中文" -> "zh-TW";
      default -> raw;
    };
  }

  public static void setLanguageTag(Context context, String languageTag) {
    prefs(context).edit().putString(KEY_LANGUAGE, languageTag).apply();
  }

  public static int getHomeSort(Context context) {
    return prefs(context).getInt(KEY_HOME_SORT, 0);
  }

  public static void setHomeSort(Context context, int sortType) {
    prefs(context).edit().putInt(KEY_HOME_SORT, sortType).apply();
  }

  public static CardType getHomeFilter(Context context) {
    String raw = prefs(context).getString(KEY_HOME_FILTER, "");
    if (raw.isBlank()) {
      return null;
    }
    try {
      return CardType.valueOf(raw);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  public static void setHomeFilter(Context context, CardType filterType) {
    prefs(context)
        .edit()
        .putString(KEY_HOME_FILTER, filterType == null ? "" : filterType.name())
        .apply();
  }

  private static String normalizeCookie(String cookie) {
    return cookie == null ? "" : cookie.trim();
  }

  static String cookieStorageKey(Platform platform) {
    return platform == Platform.TIKTOK ? KEY_TIKTOK_COOKIE : KEY_COOKIE;
  }

  private static String encryptedCookieKey(Platform platform) {
    return platform == Platform.TIKTOK ? KEY_TIKTOK_COOKIE_ENCRYPTED : KEY_COOKIE_ENCRYPTED;
  }

  private static String[] cookieLoginFields(Platform platform) {
    return platform == Platform.TIKTOK ? TIKTOK_COOKIE_LOGIN_FIELDS : DOUYIN_COOKIE_LOGIN_FIELDS;
  }

  private static String[] cookieRequestFields(Platform platform) {
    return platform == Platform.TIKTOK
        ? TIKTOK_COOKIE_REQUEST_FIELDS
        : DOUYIN_COOKIE_REQUEST_FIELDS;
  }

  private static boolean hasAnyCookieField(String cookie, String[] names) {
    for (String name : names) {
      if (hasCookieField(cookie, name)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasCookieField(String cookie, String name) {
    if (cookie == null || cookie.isBlank() || name == null || name.isBlank()) {
      return false;
    }
    return Pattern.compile("(?:^|;\\s*)" + Pattern.quote(name) + "=").matcher(cookie).find();
  }

  private static String encryptCookie(String cookie) {
    try {
      Cipher cipher = Cipher.getInstance(COOKIE_CIPHER);
      cipher.init(Cipher.ENCRYPT_MODE, getOrCreateCookieKey());
      byte[] encryptedBytes = cipher.doFinal(cookie.getBytes(StandardCharsets.UTF_8));
      byte[] iv = cipher.getIV();
      return COOKIE_VALUE_PREFIX
          + ":"
          + Base64.encodeToString(iv, Base64.NO_WRAP)
          + ":"
          + Base64.encodeToString(encryptedBytes, Base64.NO_WRAP);
    } catch (Exception e) {
      Log.w(TAG, "Cookie encryption failed", e);
      return "";
    }
  }

  private static String decryptCookie(String encryptedCookie) {
    try {
      String[] parts = encryptedCookie.split(":", 3);
      if (parts.length != 3 || !COOKIE_VALUE_PREFIX.equals(parts[0])) {
        return "";
      }
      byte[] iv = Base64.decode(parts[1], Base64.NO_WRAP);
      byte[] encryptedBytes = Base64.decode(parts[2], Base64.NO_WRAP);
      Cipher cipher = Cipher.getInstance(COOKIE_CIPHER);
      cipher.init(Cipher.DECRYPT_MODE, getOrCreateCookieKey(), new GCMParameterSpec(128, iv));
      return new String(cipher.doFinal(encryptedBytes), StandardCharsets.UTF_8);
    } catch (Exception e) {
      Log.w(TAG, "Cookie decryption failed", e);
      return "";
    }
  }

  private static SecretKey getOrCreateCookieKey() throws Exception {
    KeyStore keyStore = KeyStore.getInstance(COOKIE_KEYSTORE);
    keyStore.load(null);
    if (!keyStore.containsAlias(COOKIE_KEY_ALIAS)) {
      KeyGenerator keyGenerator =
          KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, COOKIE_KEYSTORE);
      keyGenerator.init(
          new KeyGenParameterSpec.Builder(
                  COOKIE_KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .setKeySize(256)
              .build());
      return keyGenerator.generateKey();
    }

    KeyStore.Entry entry = keyStore.getEntry(COOKIE_KEY_ALIAS, null);
    if (entry instanceof KeyStore.SecretKeyEntry) {
      return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
    }
    throw new IllegalStateException("Unexpected keystore entry for cookie alias");
  }
}
