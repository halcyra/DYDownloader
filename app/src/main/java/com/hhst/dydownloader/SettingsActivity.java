package com.hhst.dydownloader;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import org.apache.commons.io.FileUtils;

public class SettingsActivity extends AppCompatActivity {

  private final ActivityResultLauncher<Intent> cookieLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
              String cookie = result.getData().getStringExtra(CookieWebViewActivity.EXTRA_COOKIE);
              AppPrefs.setCookie(this, cookie == null ? "" : cookie);
              updateCookieStatus();
            }
          });
  private TextView currentLanguage, cacheText, cookieStatus;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    androidx.activity.EdgeToEdge.enable(this);
    setContentView(R.layout.activity_settings);

    var toolbar = (com.google.android.material.appbar.MaterialToolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    toolbar.setNavigationOnClickListener(v -> finish());

    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.settings_main),
        (v, insets) -> {
          var systemBars =
              insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
          v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
          toolbar.setPadding(
              0,
              systemBars.top,
              0,
              0); // MaterialToolbar will handle internal padding if its height is wrap_content or
          // minHeight is set
          return insets;
        });

    currentLanguage = findViewById(R.id.currentLanguage);
    cacheText = findViewById(R.id.cacheSize);
    cookieStatus = findViewById(R.id.cookieStatus);
    currentLanguage.setText(AppLocaleManager.getLanguageLabel(this, AppPrefs.getLanguageTag(this)));
    updateCookieStatus();

    findViewById(R.id.layoutLanguage).setOnClickListener(v -> showLanguageDialog());
    findViewById(R.id.layoutClearCache).setOnClickListener(v -> showClearCacheDialog());
    findViewById(R.id.layoutCookies)
        .setOnClickListener(
            v -> {
              Intent intent = new Intent(this, CookieWebViewActivity.class);
              cookieLauncher.launch(intent);
            });

    updateCacheSize();
  }

  @Override
  protected void onResume() {
    super.onResume();
    updateCookieStatus();
  }

  private void showLanguageDialog() {
    String[] tags = {
      AppLocaleManager.TAG_EN, AppLocaleManager.TAG_ZH_CN, AppLocaleManager.TAG_ZH_TW
    };
    String[] langs = {
      getString(R.string.language_option_en),
      getString(R.string.language_option_zh_cn),
      getString(R.string.language_option_zh_tw)
    };
    String current = AppPrefs.getLanguageTag(this);
    int checkedItem = 0;
    for (int i = 0; i < tags.length; i++) {
      if (tags[i].equals(current)) {
        checkedItem = i;
        break;
      }
    }
    new AlertDialog.Builder(this)
        .setTitle(R.string.language)
        .setSingleChoiceItems(
            langs,
            checkedItem,
            (dialog, which) -> {
              currentLanguage.setText(langs[which]);
              AppLocaleManager.setLocale(this, tags[which]);
              dialog.dismiss();
            })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  private void showClearCacheDialog() {
    new AlertDialog.Builder(this)
        .setTitle(R.string.clear_cache)
        .setMessage(R.string.clear_cache_confirm)
        .setPositiveButton(
            R.string.clear,
            (dialog, which) -> {
              FileUtils.deleteQuietly(getCacheDir());
              updateCacheSize();
            })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  private void updateCacheSize() {
    long size = FileUtils.sizeOfDirectory(getCacheDir());

    if (size < 1024) {
      cacheText.setText(getString(R.string.cache_size_bytes, size));
    } else if (size < 1048576) {
      cacheText.setText(getString(R.string.cache_size_kb, size / 1024.0));
    } else {
      cacheText.setText(getString(R.string.cache_size_mb, size / 1048576.0));
    }
  }

  private void updateCookieStatus() {
    if (cookieStatus == null) {
      return;
    }
    int statusResId =
        AppPrefs.hasAuthenticatedCookie(this)
            ? R.string.settings_status_set
            : AppPrefs.hasConfiguredCookie(this)
                ? R.string.settings_status_request_only
                : R.string.settings_status_unset;
    cookieStatus.setText(statusResId);
  }
}
