package com.hhst.dydownloader;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.hhst.dydownloader.databinding.ActivityCookieWebviewBinding;
import com.hhst.dydownloader.douyin.DouyinDownloader;

public class CookieWebViewActivity extends AppCompatActivity {
  public static final String EXTRA_COOKIE = "extra_cookie";
  private static final String DESKTOP_UA =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
          + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36";

  private ActivityCookieWebviewBinding binding;

  @SuppressLint("SetJavaScriptEnabled")
  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    androidx.activity.EdgeToEdge.enable(this);
    binding = ActivityCookieWebviewBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

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
    settings.setUserAgentString(DESKTOP_UA);
    settings.setUseWideViewPort(true);
    settings.setLoadWithOverviewMode(true);

    CookieManager cookieManager = CookieManager.getInstance();
    cookieManager.setAcceptCookie(true);
    cookieManager.setAcceptThirdPartyCookies(binding.webView, false);

    binding.webView.setWebViewClient(
        new WebViewClient() {
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

    binding.webView.loadUrl("https://www.douyin.com/user/self");

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
    if (binding != null && binding.webView != null) {
      CookieManager cookieManager = CookieManager.getInstance();
      String currentUrl = binding.webView.getUrl();
      String cookie = cookieManager.getCookie("https://www.douyin.com/");
      if ((cookie == null || cookie.isBlank()) && DouyinDownloader.isTrustedShareUrl(currentUrl)) {
        cookie = cookieManager.getCookie(currentUrl);
      }
      Intent data = new Intent();
      data.putExtra(EXTRA_COOKIE, cookie == null ? "" : cookie);
      setResult(RESULT_OK, data);
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
