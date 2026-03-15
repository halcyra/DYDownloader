package com.hhst.dydownloader;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import androidx.annotation.NonNull;

public class DYDownloaderApp extends Application {
  private static DYDownloaderApp instance;
  private int startedActivities;
  private boolean clipboardCheckPending = true;

  public static DYDownloaderApp getInstance() {
    return instance;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    instance = this;
    AppLocaleManager.applySavedLocale(this);
    registerActivityLifecycleCallbacks(
        new ActivityLifecycleCallbacks() {
          @Override
          public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {}

          @Override
          public void onActivityStarted(@NonNull Activity activity) {
            if (startedActivities == 0) {
              clipboardCheckPending = true;
            }
            startedActivities++;
          }

          @Override
          public void onActivityResumed(@NonNull Activity activity) {}

          @Override
          public void onActivityPaused(@NonNull Activity activity) {}

          @Override
          public void onActivityStopped(@NonNull Activity activity) {
            startedActivities = Math.max(0, startedActivities - 1);
          }

          @Override
          public void onActivitySaveInstanceState(
              @NonNull Activity activity, @NonNull Bundle outState) {}

          @Override
          public void onActivityDestroyed(@NonNull Activity activity) {}
        });
  }

  public boolean consumeClipboardCheckPending() {
    boolean current = clipboardCheckPending;
    clipboardCheckPending = false;
    return current;
  }
}
