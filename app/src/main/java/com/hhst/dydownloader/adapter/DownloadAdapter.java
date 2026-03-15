package com.hhst.dydownloader.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.hhst.dydownloader.R;
import com.hhst.dydownloader.manager.DownloadTask;
import com.hhst.dydownloader.model.ResourceItem;
import com.hhst.dydownloader.util.StoragePathUtils;
import com.squareup.picasso.Picasso;
import java.util.ArrayList;
import java.util.List;

public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.DownloadViewHolder> {

  private final List<DownloadTask> tasks = new ArrayList<>();
  private OnRetryClickListener retryListener;
  private OnTaskClickListener taskClickListener;
  private OnTaskMoreClickListener taskMoreClickListener;
  private OnPathClickListener pathClickListener;
  private OnPathLongClickListener pathLongClickListener;

  public void setOnRetryClickListener(OnRetryClickListener listener) {
    this.retryListener = listener;
  }

  public void setOnTaskClickListener(OnTaskClickListener listener) {
    this.taskClickListener = listener;
  }

  public void setOnTaskMoreClickListener(OnTaskMoreClickListener listener) {
    this.taskMoreClickListener = listener;
  }

  public void setOnPathClickListener(OnPathClickListener listener) {
    this.pathClickListener = listener;
  }

  public void setOnPathLongClickListener(OnPathLongClickListener listener) {
    this.pathLongClickListener = listener;
  }

  @NonNull
  @Override
  public DownloadViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new DownloadViewHolder(
        LayoutInflater.from(parent.getContext()).inflate(R.layout.item_download, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull DownloadViewHolder holder, int position) {
    var task = tasks.get(position);
    ResourceItem item = task.getResourceItem();
    Context context = holder.itemView.getContext();
    if (item.thumbnailUrl() != null && !item.thumbnailUrl().isEmpty()) {
      Picasso.get()
          .load(item.thumbnailUrl())
          .placeholder(R.drawable.ic_placeholder)
          .error(item.imageResId())
          .into(holder.thumbnail);
    } else {
      holder.thumbnail.setImageResource(item.imageResId());
    }
    holder.title.setText(item.text());
    bindPathRow(holder, task, item, context);
    holder.typeIcon.setImageResource(item.type().getIconResId());
    holder.moreButton.setOnClickListener(
        v -> {
          if (taskMoreClickListener != null) {
            taskMoreClickListener.onTaskMoreClick(task, v);
          }
        });

    holder.itemView.setOnClickListener(null);
    holder.itemView.setClickable(false);

    switch (task.getStatus()) {
      case QUEUED:
        holder.status.setText(R.string.download_status_queued);
        holder.progress.setVisibility(View.GONE);
        holder.retryButton.setVisibility(View.GONE);
        break;
      case DOWNLOADING:
        holder.status.setText(
            holder
                .itemView
                .getContext()
                .getString(R.string.download_status_downloading, task.getProgress()));
        holder.progress.setVisibility(View.VISIBLE);
        holder.progress.setIndeterminate(task.getProgress() <= 0);
        holder.progress.setProgress(task.getProgress());
        holder.retryButton.setVisibility(View.GONE);
        break;
      case COMPLETED:
        holder.status.setText(R.string.download_status_completed);
        holder.progress.setVisibility(View.GONE);
        holder.retryButton.setVisibility(View.GONE);
        holder.itemView.setClickable(true);
        holder.itemView.setOnClickListener(
            v -> {
              if (taskClickListener != null) {
                taskClickListener.onTaskClick(task);
              }
            });
        break;
      case FAILED:
        String errorMessage =
            task.getError() != null
                ? task.getError()
                : context.getString(R.string.download_error_unknown);
        holder.status.setText(R.string.download_status_failed_short);
        holder.path.setEllipsize(TextUtils.TruncateAt.END);
        holder.path.setText(
            context.getString(
                R.string.download_error_summary, summarizeError(errorMessage, context)));
        holder.path.setTextColor(holder.errorPathTextColor);
        holder.path.setClickable(false);
        holder.path.setFocusable(false);
        holder.path.setLongClickable(false);
        holder.path.setOnClickListener(null);
        holder.path.setOnLongClickListener(null);
        holder.progress.setVisibility(View.GONE);
        holder.retryButton.setVisibility(View.VISIBLE);
        holder.retryButton.setOnClickListener(
            v -> {
              if (retryListener != null) {
                retryListener.onRetry(task);
              }
            });
        break;
    }
  }

  @Override
  public int getItemCount() {
    return tasks.size();
  }

  private void bindPathRow(
      @NonNull DownloadViewHolder holder,
      @NonNull DownloadTask task,
      ResourceItem item,
      Context context) {
    holder.path.setEllipsize(TextUtils.TruncateAt.MIDDLE);
    holder.path.setText(context.getString(R.string.download_save_path, resolveDisplayPath(item)));
    holder.path.setTextColor(holder.defaultPathTextColor);
    holder.path.setClickable(true);
    holder.path.setFocusable(true);
    holder.path.setLongClickable(true);
    holder.path.setOnClickListener(
        v -> {
          if (pathClickListener != null) {
            pathClickListener.onPathClick(task);
          }
        });
    holder.path.setOnLongClickListener(
        v -> {
          if (pathLongClickListener != null) {
            pathLongClickListener.onPathLongClick(task);
            return true;
          }
          return false;
        });
  }

  private String resolveDisplayPath(ResourceItem item) {
    if (item == null) {
      return StoragePathUtils.buildPublicDownloadDisplayPath("");
    }
    return StoragePathUtils.buildPublicDownloadDisplayPath(item.storageDir());
  }

  private String summarizeError(String errorMessage, Context context) {
    String compact =
        errorMessage == null || errorMessage.isBlank()
            ? context.getString(R.string.download_error_unknown)
            : errorMessage.replaceAll("\\s+", " ").trim();
    if (compact.matches("^[A-Za-z0-9_.$]+:.*")) {
      compact = compact.substring(compact.indexOf(':') + 1).trim();
    }
    int causeSeparator = compact.indexOf(" (");
    if (causeSeparator > 0) {
      compact = compact.substring(0, causeSeparator).trim();
    }
    if (compact.length() > 60) {
      compact = compact.substring(0, 57).trim() + "...";
    }
    if (compact.isEmpty()) {
      compact = context.getString(R.string.download_error_unknown);
    }
    return compact;
  }

  public void submitList(List<DownloadTask> newList) {
    List<DownloadTask> target = newList == null ? List.of() : newList;
    var diffResult =
        DiffUtil.calculateDiff(
            new DiffUtil.Callback() {
              @Override
              public int getOldListSize() {
                return tasks.size();
              }

              @Override
              public int getNewListSize() {
                return target.size();
              }

              @Override
              public boolean areItemsTheSame(int oldPos, int newPos) {
                return tasks.get(oldPos).getKey().equals(target.get(newPos).getKey());
              }

              @Override
              public boolean areContentsTheSame(int oldPos, int newPos) {
                DownloadTask oldT = tasks.get(oldPos);
                DownloadTask newT = target.get(newPos);
                return oldT.getStatus() == newT.getStatus()
                    && oldT.getProgress() == newT.getProgress()
                    && (oldT.getError() == null
                        ? newT.getError() == null
                        : oldT.getError().equals(newT.getError()));
              }
            });
    tasks.clear();
    tasks.addAll(target);
    diffResult.dispatchUpdatesTo(this);
  }

  public interface OnRetryClickListener {
    void onRetry(DownloadTask task);
  }

  public interface OnTaskClickListener {
    void onTaskClick(DownloadTask task);
  }

  public interface OnTaskMoreClickListener {
    void onTaskMoreClick(DownloadTask task, View anchorView);
  }

  public interface OnPathClickListener {
    void onPathClick(DownloadTask task);
  }

  public interface OnPathLongClickListener {
    void onPathLongClick(DownloadTask task);
  }

  static class DownloadViewHolder extends RecyclerView.ViewHolder {
    final ImageView thumbnail, typeIcon;
    final TextView title, path, status;
    final LinearProgressIndicator progress;
    final com.google.android.material.button.MaterialButton retryButton;
    final View moreButton;
    final int defaultPathTextColor;
    final int errorPathTextColor;

    DownloadViewHolder(@NonNull View itemView) {
      super(itemView);
      thumbnail = itemView.findViewById(R.id.downloadThumbnail);
      typeIcon = itemView.findViewById(R.id.downloadTypeIcon);
      title = itemView.findViewById(R.id.downloadTitle);
      path = itemView.findViewById(R.id.downloadPath);
      status = itemView.findViewById(R.id.downloadStatus);
      progress = itemView.findViewById(R.id.downloadProgress);
      retryButton = itemView.findViewById(R.id.downloadRetry);
      moreButton = itemView.findViewById(R.id.downloadMore);
      defaultPathTextColor = path.getCurrentTextColor();
      errorPathTextColor = status.getCurrentTextColor();
    }
  }
}
