package com.hhst.dydownloader;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.hhst.dydownloader.model.Platform;
import org.apache.commons.io.FileUtils;

public class SettingsActivity extends AppCompatActivity {
  private TextView currentLanguage, cacheText, cookieStatus, downloadSubdirectoriesSummary,
      fileNamingSummary;
  private MaterialSwitch downloadSubdirectoriesSwitch;

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
    downloadSubdirectoriesSummary = findViewById(R.id.downloadSubdirectoriesSummary);
    downloadSubdirectoriesSwitch = findViewById(R.id.downloadSubdirectoriesSwitch);
    fileNamingSummary = findViewById(R.id.fileNamingSummary);
    updateCurrentLanguage();
    updateCookieStatus();
    updateDownloadSubdirectorySetting();
    updateFileNameTemplate();

    findViewById(R.id.layoutLanguage).setOnClickListener(v -> showLanguageDialog());
    findViewById(R.id.layoutDownloadSubdirectories)
        .setOnClickListener(
            v -> {
              boolean enabled = !downloadSubdirectoriesSwitch.isChecked();
              AppPrefs.setUseDownloadSubdirectories(this, enabled);
              updateDownloadSubdirectorySetting();
            });
    downloadSubdirectoriesSwitch.setOnCheckedChangeListener(
        (buttonView, isChecked) -> {
          AppPrefs.setUseDownloadSubdirectories(this, isChecked);
          updateDownloadSubdirectorySetting();
        });
    findViewById(R.id.layoutFileNaming).setOnClickListener(v -> showFileNameTemplateDialog());
    findViewById(R.id.layoutClearCache).setOnClickListener(v -> showClearCacheDialog());
    findViewById(R.id.layoutCookies)
        .setOnClickListener(
            v -> {
              Intent intent = new Intent(this, CookiesActivity.class);
              startActivity(intent);
            });

    updateCacheSize();
  }

  @Override
  protected void onResume() {
    super.onResume();
    updateCurrentLanguage();
    updateCookieStatus();
    updateDownloadSubdirectorySetting();
    updateFileNameTemplate();
  }

  private void showFileNameTemplateDialog() {
    View view = getLayoutInflater().inflate(R.layout.dialog_file_name_template, null);
    var input = (TextInputEditText) view.findViewById(R.id.fileNameTemplateInput);
    input.setText(AppPrefs.getFileNameTemplate(this));
    new AlertDialog.Builder(this)
        .setTitle(R.string.file_naming)
        .setView(view)
        .setPositiveButton(
            R.string.dialog_ok,
            (dialog, which) -> {
              CharSequence text = input.getText();
              AppPrefs.setFileNameTemplate(this, text == null ? "" : text.toString());
              updateFileNameTemplate();
            })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  private void updateFileNameTemplate() {
    if (fileNamingSummary != null) {
      fileNamingSummary.setText(AppPrefs.getFileNameTemplate(this));
    }
  }

  private void showLanguageDialog() {
    String[] tags = {
      AppLocaleManager.TAG_EN, AppLocaleManager.TAG_ZH_CN, AppLocaleManager.TAG_ZH_TW
    };
    String[] langs = {
      AppLocaleManager.getLanguageLabel(AppLocaleManager.TAG_EN),
      AppLocaleManager.getLanguageLabel(AppLocaleManager.TAG_ZH_CN),
      AppLocaleManager.getLanguageLabel(AppLocaleManager.TAG_ZH_TW)
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
              currentLanguage.setText(AppLocaleManager.getLanguageLabel(tags[which]));
              AppLocaleManager.setLocale(this, tags[which]);
              dialog.dismiss();
            })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  private void updateCurrentLanguage() {
    currentLanguage.setText(AppLocaleManager.getLanguageLabel(AppPrefs.getLanguageTag(this)));
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
    boolean hasAuthenticated =
        AppPrefs.hasAuthenticatedCookie(this, Platform.DOUYIN)
            || AppPrefs.hasAuthenticatedCookie(this, Platform.TIKTOK);
    boolean hasConfigured =
        AppPrefs.hasConfiguredCookie(this, Platform.DOUYIN)
            || AppPrefs.hasConfiguredCookie(this, Platform.TIKTOK);
    int statusResId =
        hasAuthenticated
            ? R.string.settings_status_set
            : hasConfigured
                ? R.string.settings_status_request_only
                : R.string.settings_status_unset;
    cookieStatus.setText(statusResId);
  }

  private void updateDownloadSubdirectorySetting() {
    if (downloadSubdirectoriesSwitch == null || downloadSubdirectoriesSummary == null) {
      return;
    }
    boolean enabled = AppPrefs.shouldUseDownloadSubdirectories(this);
    if (downloadSubdirectoriesSwitch.isChecked() != enabled) {
      downloadSubdirectoriesSwitch.setChecked(enabled);
    }
    downloadSubdirectoriesSummary.setText(
        enabled
            ? R.string.download_subdirectories_summary_on
            : R.string.download_subdirectories_summary_off);
  }
}
