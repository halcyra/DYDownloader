package com.hhst.dydownloader;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.hhst.dydownloader.cookies.CookiePlatformConfig;
import com.hhst.dydownloader.databinding.ActivityCookieWebviewBinding;
import com.hhst.dydownloader.model.Platform;

public class CookieWebViewActivity extends AppCompatActivity {
  public static final String EXTRA_COOKIE = "extra_cookie";
  public static final String EXTRA_PLATFORM = "extra_platform";
  private static final String DESKTOP_UA =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
          + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36";

  private ActivityCookieWebviewBinding binding;
  private Platform platform;
  private CookiePlatformConfig config;

  @SuppressLint("SetJavaScriptEnabled")
  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    androidx.activity.EdgeToEdge.enable(this);
    binding = ActivityCookieWebviewBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    platform = resolvePlatform(getIntent());
    config = CookiePlatformConfig.forPlatform(platform);

    setSupportActionBar(binding.toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setDisplayShowTitleEnabled(true);
    }
    binding.toolbar.setNavigationOnClickListener(v -> handleBack());

    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
        binding.cookieWebviewMain,
        (v, insets) -> {
          var systemBars =
              insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
          v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
          binding.toolbar.setPadding(0, systemBars.top, 0, 0);
          return insets;
        });

    WebSettings settings = binding.webView.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setUserAgentString(config.forceDesktopUserAgent() ? DESKTOP_UA : null);
    settings.setUseWideViewPort(true);
    settings.setLoadWithOverviewMode(true);

    CookieManager cookieManager = CookieManager.getInstance();
    cookieManager.setAcceptCookie(true);
    cookieManager.setAcceptThirdPartyCookies(binding.webView, config.allowExternalAppRedirects());

    binding.webView.setWebViewClient(
        new WebViewClient() {
          @Override
          public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return shouldOpenExternally(request != null ? request.getUrl() : null);
          }

          @Override
          public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            binding.progressBar.setVisibility(View.VISIBLE);
          }

          @Override
          public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            binding.progressBar.setVisibility(View.GONE);
            cookieManager.flush();
          }
        });

    binding.webView.setWebChromeClient(
        new WebChromeClient() {
          @Override
          public void onProgressChanged(WebView view, int newProgress) {
            if (newProgress == 100) {
              binding.progressBar.setVisibility(View.GONE);
            } else {
              binding.progressBar.setVisibility(View.VISIBLE);
              binding.progressBar.setIndeterminate(false);
              binding.progressBar.setProgress(newProgress);
            }
          }
        });

    binding.webView.loadUrl(config.loginUrl());

    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                handleBack();
              }
            });
  }

  private void handleBack() {
    if (binding.webView.canGoBack()) {
      binding.webView.goBack();
    } else {
      finish();
    }
  }

  @Override
  public void finish() {
    saveCookies();
    super.finish();
  }

  private void saveCookies() {
    if (binding != null) {
      CookieManager cookieManager = CookieManager.getInstance();
      String currentUrl = binding.webView.getUrl();
      String cookie = cookieManager.getCookie(config.cookieUrl());
      if ((cookie == null || cookie.isBlank()) && config.isTrustedWebUrl(currentUrl)) {
        cookie = cookieManager.getCookie(currentUrl);
      }
      AppPrefs.setCookie(this, platform, cookie == null ? "" : cookie);
      Intent data = new Intent();
      data.putExtra(EXTRA_COOKIE, cookie == null ? "" : cookie);
      data.putExtra(EXTRA_PLATFORM, platform.name());
      setResult(RESULT_OK, data);
    }
  }

  private boolean shouldOpenExternally(@Nullable Uri uri) {
    if (uri == null || !config.allowExternalAppRedirects()) {
      return false;
    }
    String scheme = uri.getScheme();
    if (scheme == null || scheme.isBlank()) {
      return false;
    }
    if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
      return false;
    }
    try {
      Intent externalIntent =
          "intent".equalsIgnoreCase(scheme)
              ? Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
              : new Intent(Intent.ACTION_VIEW, uri);
      externalIntent.addCategory(Intent.CATEGORY_BROWSABLE);
      startActivity(externalIntent);
      return true;
    } catch (ActivityNotFoundException | java.net.URISyntaxException ignored) {
      if (!"intent".equalsIgnoreCase(scheme)) {
        return false;
      }
      try {
        Intent parsedIntent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
        String fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url");
        if (fallbackUrl != null && !fallbackUrl.isBlank()) {
          binding.webView.loadUrl(fallbackUrl);
          return true;
        }
      } catch (java.net.URISyntaxException ignoredAgain) {
        // Ignore and fall through.
      }
      return false;
    }
  }

  private Platform resolvePlatform(Intent intent) {
    if (intent == null) {
      return Platform.DOUYIN;
    }
    String rawPlatform = intent.getStringExtra(EXTRA_PLATFORM);
    if (rawPlatform == null || rawPlatform.isBlank()) {
      return Platform.DOUYIN;
    }
    try {
      return Platform.valueOf(rawPlatform);
    } catch (IllegalArgumentException ignored) {
      return Platform.DOUYIN;
    }
  }

  @Override
  protected void onDestroy() {
    if (binding != null) {
      binding.webView.destroy();
    }
    super.onDestroy();
  }
}
