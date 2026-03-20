package com.hhst.dydownloader;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.hhst.dydownloader.manager.DownloadQueue;
import com.hhst.dydownloader.share.ShareLinkResolver;

public class MainActivity extends AppCompatActivity {

  private static final String KEY_SELECTED_TAB = "selected_tab";
  private static final long EXIT_CONFIRM_WINDOW_MS = 2000L;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private int selectedTabId = R.id.nav_home;
  private ViewPager2 viewPager;
  private View clipboardPrompt;
  private View topPromptAnchor;
  private Runnable hideClipboardPromptRunnable;
  private Runnable clearExitPendingRunnable;
  private long lastBackPressedAt;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    EdgeToEdge.enable(this);
    setContentView(R.layout.activity_main);

    DownloadQueue.init(this);
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                handleRootBackPressed();
              }
            });

    findViewById(R.id.downloadButton).setOnClickListener(v -> onDownloadClick());
    clipboardPrompt = findViewById(R.id.clipboardPrompt);
    topPromptAnchor = findViewById(R.id.topPromptAnchor);
    findViewById(R.id.clipboardPromptLoad)
        .setOnClickListener(
            v -> {
              ClipboardManager clipboard =
                  (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
              if (clipboard == null || !clipboard.hasPrimaryClip()) {
                hideClipboardPrompt();
                return;
              }
              CharSequence clipText = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
              if (clipText == null) {
                hideClipboardPrompt();
                return;
              }
              String text = clipText.toString().trim();
              if (!looksLikeSupportedLink(text)) {
                hideClipboardPrompt();
                return;
              }
              hideClipboardPrompt();
              openResourceFromShareText(text);
            });

    if (savedInstanceState != null) {
      selectedTabId = savedInstanceState.getInt(KEY_SELECTED_TAB, R.id.nav_home);
    }

    var bottomNavigation = (BottomNavigationView) findViewById(R.id.bottomNavigation);
    viewPager = findViewById(R.id.viewPager);
    viewPager.setAdapter(new MainPagerAdapter(this));
    viewPager.registerOnPageChangeCallback(
        new ViewPager2.OnPageChangeCallback() {
          @Override
          public void onPageSelected(int position) {
            selectedTabId = position == 1 ? R.id.nav_downloads : R.id.nav_home;
            if (bottomNavigation.getSelectedItemId() != selectedTabId) {
              bottomNavigation.setSelectedItemId(selectedTabId);
            }
          }
        });

    bottomNavigation.setOnItemSelectedListener(
        item -> {
          selectedTabId = item.getItemId();
          viewPager.setCurrentItem(selectedTabId == R.id.nav_downloads ? 1 : 0, true);
          return true;
        });

    viewPager.setCurrentItem(selectedTabId == R.id.nav_downloads ? 1 : 0, false);

    ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.main),
        (v, insets) -> {
          var systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
          v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
          if (topPromptAnchor != null) {
            ViewGroup.LayoutParams anchorLayout = topPromptAnchor.getLayoutParams();
            int promptOffset = systemBars.top + resolveToolbarOffset() + dpToPx(16);
            if (anchorLayout.height != promptOffset) {
              anchorLayout.height = promptOffset;
              topPromptAnchor.setLayoutParams(anchorLayout);
            }
          }
          return insets;
        });
  }

  public void setFabConfig(int iconResId, View.OnClickListener listener) {
    var fab = (FloatingActionButton) findViewById(R.id.downloadButton);
    if (fab != null) {
      fab.setImageResource(iconResId);
      fab.setOnClickListener(listener != null ? listener : v -> onDownloadClick());
      int contentDescriptionRes =
          iconResId == R.drawable.ic_delete ? R.string.action_delete : R.string.download;
      fab.setContentDescription(getString(contentDescriptionRes));
    }
  }

  public void handleRootBackPressed() {
    long now = System.currentTimeMillis();
    if (now - lastBackPressedAt <= EXIT_CONFIRM_WINDOW_MS) {
      finish();
      return;
    }
    lastBackPressedAt = now;
    Toast.makeText(this, R.string.press_back_again_to_exit, Toast.LENGTH_SHORT).show();
    scheduleExitConfirmReset();
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt(KEY_SELECTED_TAB, selectedTabId);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (((DYDownloaderApp) getApplication()).consumeClipboardCheckPending()) {
      findViewById(R.id.main).postDelayed(this::maybeShowClipboardLoadPrompt, 250);
    }
  }

  private void onDownloadClick() {
    var dialog = new BottomSheetDialog(this);
    var parent = findViewById(android.R.id.content);
    var view =
        LayoutInflater.from(this)
            .inflate(
                R.layout.dialog_download_bottom_sheet,
                parent instanceof android.view.ViewGroup ? (android.view.ViewGroup) parent : null,
                false);
    dialog.setContentView(view);

    var urlInput = (TextInputEditText) view.findViewById(R.id.urlInput);
    view.findViewById(R.id.btnClear).setOnClickListener(v -> urlInput.setText(""));

    view.findViewById(R.id.btnPaste)
        .setOnClickListener(
            v -> {
              var clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
              if (clipboard != null && clipboard.hasPrimaryClip()) {
                var text = clipboard.getPrimaryClip().getItemAt(0).getText();
                if (text != null) {
                  urlInput.setText(text);
                }
              }
            });

    view.findViewById(R.id.btnLoad)
        .setOnClickListener(
            v -> {
              var text = urlInput.getText() != null ? urlInput.getText().toString().trim() : "";
              if (text.isEmpty()) {
                urlInput.setError(getString(R.string.hint_enter_url));
                return;
              }
              if (!looksLikeSupportedLink(text)) {
                urlInput.setError(getString(R.string.invalid_supported_link));
                return;
              }
              dialog.dismiss();
              openResourceFromShareText(text);
            });
    dialog.show();
  }

  private void maybeShowClipboardLoadPrompt() {
    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    if (clipboard == null || !clipboard.hasPrimaryClip()) {
      return;
    }
    CharSequence clipText = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
    if (clipText == null) {
      return;
    }
    String text = clipText.toString().trim();
    if (text.isEmpty()) {
      return;
    }
    if (!looksLikeSupportedLink(text)) {
      return;
    }
    showClipboardPrompt();
  }

  private boolean looksLikeSupportedLink(String text) {
    return ShareLinkResolver.containsSupportedLink(text);
  }

  private void openResourceFromShareText(String text) {
    startActivity(
        new Intent(MainActivity.this, ResourceActivity.class)
            .putExtra("share_link", text)
            .putExtra(ResourceActivity.EXTRA_REFERRER, ResourceActivity.REFERRER_RESOURCE));
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    hideClipboardPrompt();
    if (clearExitPendingRunnable != null) {
      mainHandler.removeCallbacks(clearExitPendingRunnable);
      clearExitPendingRunnable = null;
    }
    if (isFinishing()) {
      try {
        com.hhst.dydownloader.manager.DownloadManager.getInstance().shutdown();
      } catch (IllegalStateException ignored) {
      }
    }
  }

  private void showClipboardPrompt() {
    if (clipboardPrompt == null) {
      return;
    }
    clipboardPrompt.setVisibility(View.VISIBLE);
    clipboardPrompt.bringToFront();
    if (hideClipboardPromptRunnable != null) {
      mainHandler.removeCallbacks(hideClipboardPromptRunnable);
    }
    hideClipboardPromptRunnable = this::hideClipboardPrompt;
    mainHandler.postDelayed(hideClipboardPromptRunnable, 5000);
  }

  private void hideClipboardPrompt() {
    if (hideClipboardPromptRunnable != null) {
      mainHandler.removeCallbacks(hideClipboardPromptRunnable);
      hideClipboardPromptRunnable = null;
    }
    if (clipboardPrompt != null) {
      clipboardPrompt.setVisibility(View.GONE);
    }
  }

  private void scheduleExitConfirmReset() {
    if (clearExitPendingRunnable != null) {
      mainHandler.removeCallbacks(clearExitPendingRunnable);
    }
    clearExitPendingRunnable = () -> lastBackPressedAt = 0L;
    mainHandler.postDelayed(clearExitPendingRunnable, EXIT_CONFIRM_WINDOW_MS);
  }

  private int resolveToolbarOffset() {
    android.util.TypedValue value = new android.util.TypedValue();
    if (getTheme().resolveAttribute(android.R.attr.actionBarSize, value, true)) {
      return android.util.TypedValue.complexToDimensionPixelSize(
          value.data, getResources().getDisplayMetrics());
    }
    return dpToPx(56);
  }

  private int dpToPx(int dp) {
    return Math.round(dp * getResources().getDisplayMetrics().density);
  }

  private static class MainPagerAdapter extends FragmentStateAdapter {
    MainPagerAdapter(@NonNull AppCompatActivity activity) {
      super(activity);
    }

    @NonNull
    @Override
    public androidx.fragment.app.Fragment createFragment(int pos) {
      return pos == 1 ? new DownloadsFragment() : new HomeFragment();
    }

    @Override
    public int getItemCount() {
      return 2;
    }
  }
}
