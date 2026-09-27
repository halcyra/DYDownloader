package com.hhst.dydownloader.manager;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.hhst.dydownloader.R;

/** Android 7–9 requires storage permission for the public Downloads directory. */
public final class DownloadPermission {
  private final Fragment fragment;
  private final ActivityResultLauncher<String> launcher;
  private Runnable pendingAction;

  public DownloadPermission(Fragment fragment) {
    this.fragment = fragment;
    launcher = fragment.registerForActivityResult(
        new ActivityResultContracts.RequestPermission(),
        granted -> {
          Runnable action = pendingAction;
          pendingAction = null;
          if (!fragment.isAdded() || fragment.getView() == null) return;
          if (granted) {
            if (action != null) action.run();
          } else {
            Toast.makeText(fragment.requireContext(),
                R.string.download_storage_permission_required, Toast.LENGTH_LONG).show();
          }
        });
  }

  public void runWhenGranted(Runnable action) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        || ContextCompat.checkSelfPermission(fragment.requireContext(),
            Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
      action.run();
    } else {
      pendingAction = action;
      launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }
  }
}
