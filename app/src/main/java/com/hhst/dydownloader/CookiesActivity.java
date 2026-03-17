package com.hhst.dydownloader;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.hhst.dydownloader.model.Platform;

public class CookiesActivity extends AppCompatActivity {
  private static final String EXTRA_PLATFORM = "extra_platform";

  private TextView douyinCookieStatus;
  private TextView tiktokCookieStatus;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    androidx.activity.EdgeToEdge.enable(this);
    setContentView(R.layout.activity_cookies);

    var toolbar = (com.google.android.material.appbar.MaterialToolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    toolbar.setNavigationOnClickListener(v -> finish());

    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.cookies_main),
        (v, insets) -> {
          var systemBars =
              insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
          v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
          toolbar.setPadding(0, systemBars.top, 0, 0);
          return insets;
        });

    douyinCookieStatus = findViewById(R.id.douyinCookieStatus);
    tiktokCookieStatus = findViewById(R.id.tiktokCookieStatus);

    findViewById(R.id.layoutDouyinCookies)
        .setOnClickListener(v -> openCookieLogin(Platform.DOUYIN));
    findViewById(R.id.layoutTikTokCookies)
        .setOnClickListener(v -> openCookieLogin(Platform.TIKTOK));

    updateCookieStatus();
  }

  @Override
  protected void onResume() {
    super.onResume();
    updateCookieStatus();
  }

  private void openCookieLogin(Platform platform) {
    Intent intent = new Intent(this, CookieWebViewActivity.class);
    intent.putExtra(EXTRA_PLATFORM, platform.name());
    startActivity(intent);
  }

  private void updateCookieStatus() {
    douyinCookieStatus.setText(resolveStatusResId(Platform.DOUYIN));
    tiktokCookieStatus.setText(resolveStatusResId(Platform.TIKTOK));
  }

  private int resolveStatusResId(Platform platform) {
    if (AppPrefs.hasAuthenticatedCookie(this, platform)) {
      return R.string.settings_status_set;
    }
    return AppPrefs.hasConfiguredCookie(this, platform)
        ? R.string.settings_status_request_only
        : R.string.settings_status_unset;
  }
}
