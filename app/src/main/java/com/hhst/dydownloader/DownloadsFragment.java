package com.hhst.dydownloader;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hhst.dydownloader.adapter.DownloadAdapter;
import com.hhst.dydownloader.db.AppDatabase;
import com.hhst.dydownloader.db.ResourceDao;
import com.hhst.dydownloader.db.ResourceEntity;
import com.hhst.dydownloader.manager.DownloadQueue;
import com.hhst.dydownloader.manager.DownloadTask;
import com.hhst.dydownloader.util.StoragePathUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class DownloadsFragment extends Fragment
    implements DownloadQueue.Listener,
        DownloadAdapter.OnRetryClickListener,
        DownloadAdapter.OnTaskClickListener,
        DownloadAdapter.OnTaskMoreClickListener,
        DownloadAdapter.OnPathClickListener,
        DownloadAdapter.OnPathLongClickListener {

  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final AtomicBoolean destroyed = new AtomicBoolean(false);
  private DownloadAdapter adapter;
  private TextView emptyView;
  private ResourceDao resourceDao;
  private ExecutorService dbExecutor;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    dbExecutor =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "dy-downloads-db");
              t.setDaemon(true);
              return t;
            });
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_downloads, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    resourceDao = AppDatabase.getDatabase(requireContext()).resourceDao();
    emptyView = view.findViewById(R.id.downloadQueueEmpty);

    View rootView = view.findViewById(R.id.downloadsRoot);
    RecyclerView recyclerView = view.findViewById(R.id.downloadQueueList);
    recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
        rootView,
        (v, insets) -> {
          var systemBars =
              insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
          v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
          return insets;
        });

    adapter = new DownloadAdapter();
    adapter.setOnRetryClickListener(this);
    adapter.setOnTaskClickListener(this);
    adapter.setOnTaskMoreClickListener(this);
    adapter.setOnPathClickListener(this);
    adapter.setOnPathLongClickListener(this);
    recyclerView.setAdapter(adapter);

    render(DownloadQueue.getTasks());
  }

  @Override
  public void onStart() {
    super.onStart();
    DownloadQueue.addListener(this);
  }

  @Override
  public void onStop() {
    DownloadQueue.removeListener(this);
    super.onStop();
  }

  @Override
  public void onDestroy() {
    destroyed.set(true);
    if (dbExecutor != null) {
      dbExecutor.shutdownNow();
      dbExecutor = null;
    }
    super.onDestroy();
  }

  @Override
  public void onQueueChanged(List<DownloadTask> tasks) {
    render(tasks);
  }

  @Override
  public void onRetry(DownloadTask task) {
    DownloadQueue.retryTask(task);
  }

  @Override
  public void onTaskClick(DownloadTask task) {
    openResource(task);
  }

  @Override
  public void onTaskMoreClick(DownloadTask task, View anchorView) {
    ExecutorService exec = dbExecutor;
    if (exec == null) {
      return;
    }
    exec.execute(
        () -> {
          ResourceActions.LocalMedia media = ResourceActions.resolveLocalMedia(resourceDao, task);
          mainHandler.post(
              () -> {
                if (destroyed.get() || !isAdded()) {
                  return;
                }
                PopupMenu popupMenu = new PopupMenu(requireContext(), anchorView);
                popupMenu.inflate(R.menu.item_more_actions);
                boolean canOpenDirectory = ResourceActions.hasDownloadDirectory(task);
                popupMenu
                    .getMenu()
                    .findItem(R.id.action_open_with)
                    .setEnabled(media.canOpenWith() || canOpenDirectory);
                popupMenu.getMenu().findItem(R.id.action_share).setEnabled(media.canShare());
                popupMenu
                    .getMenu()
                    .findItem(R.id.action_delete_item)
                    .setEnabled(
                        task != null && task.getStatus() != DownloadTask.Status.DOWNLOADING);
                popupMenu.setOnMenuItemClickListener(
                    menuItem -> {
                      int id = menuItem.getItemId();
                      if (id == R.id.action_open_with) {
                        return media.canOpenWith()
                            ? ResourceActions.openWith(requireContext(), media)
                            : ResourceActions.openDownloadDirectory(requireContext(), task);
                      }
                      if (id == R.id.action_share) {
                        return ResourceActions.share(requireContext(), media);
                      }
                      if (id == R.id.action_delete_item) {
                        showDeleteDialog(
                            getString(R.string.dialog_delete_single_message),
                            media.canShare(),
                            deleteLocalFiles -> deleteTaskAsync(task, deleteLocalFiles));
                        return true;
                      }
                      return false;
                    });
                popupMenu.show();
              });
        });
  }

  @Override
  public void onPathClick(DownloadTask task) {
    if (task == null) {
      return;
    }
    if (task.getStatus() != DownloadTask.Status.COMPLETED) {
      ResourceActions.openDownloadDirectory(requireContext(), task);
      return;
    }
    ExecutorService exec = dbExecutor;
    if (exec == null) {
      ResourceActions.openDownloadDirectory(requireContext(), task);
      return;
    }
    exec.execute(
        () -> {
          ResourceActions.LocalMedia media = ResourceActions.resolveLocalMedia(resourceDao, task);
          mainHandler.post(
              () -> {
                if (destroyed.get() || !isAdded()) {
                  return;
                }
                if (media.canOpenWith()) {
                  ResourceActions.openWith(requireContext(), media);
                  return;
                }
                ResourceActions.openDownloadDirectory(requireContext(), task);
              });
        });
  }

  @Override
  public void onPathLongClick(DownloadTask task) {
    if (task == null) {
      return;
    }
    String displayPath =
        StoragePathUtils.buildPublicDownloadDisplayPath(task.getResourceItem().storageDir());
    ClipboardManager clipboardManager =
        (ClipboardManager)
            requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
    if (clipboardManager == null) {
      return;
    }
    clipboardManager.setPrimaryClip(
        ClipData.newPlainText(getString(R.string.download_title), displayPath));
    Toast.makeText(requireContext(), R.string.download_path_copied, Toast.LENGTH_SHORT).show();
  }

  private void render(List<DownloadTask> tasks) {
    List<DownloadTask> orderedTasks = new ArrayList<>(tasks);
    orderedTasks.sort(Comparator.comparingLong(DownloadTask::getCreatedAt).reversed());
    if (adapter != null) adapter.submitList(orderedTasks);
    if (emptyView != null) emptyView.setVisibility(tasks.isEmpty() ? View.VISIBLE : View.GONE);
  }

  private void openResource(DownloadTask task) {
    if (task == null || task.getStatus() != DownloadTask.Status.COMPLETED) {
      return;
    }
    ExecutorService exec = dbExecutor;
    if (exec == null) {
      return;
    }
    exec.execute(
        () -> {
          ResourceEntity target =
              ResourceActions.resolveTargetEntity(resourceDao, task.getResourceItem());
          ResourceEntity pageEntity = target;
          if (target != null && target.isLeaf && target.parentId > 0) {
            ResourceEntity parent = resourceDao.getById(target.parentId);
            if (parent != null) {
              pageEntity = parent;
            }
          }
          ResourceEntity finalPageEntity = pageEntity;
          mainHandler.post(
              () -> {
                if (destroyed.get() || !isAdded()) {
                  return;
                }
                if (finalPageEntity == null) {
                  Toast.makeText(getContext(), R.string.contents_nonexistent, Toast.LENGTH_SHORT)
                      .show();
                  return;
                }
                startActivity(
                    new Intent(requireContext(), ResourceActivity.class)
                        .putExtra("source_url", finalPageEntity.text)
                        .putExtra(ResourceActivity.EXTRA_RESOURCE_ID, finalPageEntity.id)
                        .putExtra(ResourceActivity.EXTRA_REFERRER, ResourceActivity.REFERRER_HOME));
              });
        });
  }

  private void deleteTaskAsync(DownloadTask task, boolean deleteLocalFiles) {
    if (task == null) {
      return;
    }
    ExecutorService exec = dbExecutor;
    if (exec == null) {
      return;
    }
    exec.execute(
        () -> {
          ResourceActions.deleteResourceItem(resourceDao, task.getResourceItem(), deleteLocalFiles);
          mainHandler.post(
              () -> {
                if (destroyed.get() || !isAdded()) {
                  return;
                }
                DownloadQueue.removeTask(task);
              });
        });
  }

  private void showDeleteDialog(
      CharSequence message, boolean showDeleteLocalFiles, Consumer<Boolean> onConfirm) {
    View dialogView =
        LayoutInflater.from(requireContext()).inflate(R.layout.dialog_delete_confirmation, null);
    TextView messageView = dialogView.findViewById(R.id.deleteDialogMessage);
    MaterialCheckBox deleteLocalFilesCheck = dialogView.findViewById(R.id.deleteLocalFilesCheck);
    messageView.setText(message);
    deleteLocalFilesCheck.setVisibility(showDeleteLocalFiles ? View.VISIBLE : View.GONE);
    deleteLocalFilesCheck.setChecked(false);

    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.action_delete)
        .setView(dialogView)
        .setPositiveButton(
            R.string.action_delete,
            (dialog, which) -> onConfirm.accept(deleteLocalFilesCheck.isChecked()))
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }
}
