package com.hhst.dydownloader.adapter;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.hhst.dydownloader.R;
import com.hhst.dydownloader.model.CardType;
import com.hhst.dydownloader.model.ResourceItem;
import com.hhst.dydownloader.util.StorageReferenceUtils;
import com.squareup.picasso.Picasso;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ResourceAdapter extends RecyclerView.Adapter<ResourceAdapter.ResourceViewHolder> {

  private final List<ResourceItem> resourceList = new ArrayList<>();
  private final OnResourceClickListener listener;
  private final SelectionState selectionState;
  private final boolean isFromReferrer;

  public ResourceAdapter(
      List<ResourceItem> initialList,
      SelectionState selectionState,
      OnResourceClickListener listener,
      boolean isFromReferrer) {
    if (initialList != null) this.resourceList.addAll(initialList);
    this.listener = listener;
    this.selectionState = selectionState;
    this.isFromReferrer = isFromReferrer;
  }

  public void submitList(List<ResourceItem> newList) {
    List<ResourceItem> target = newList == null ? List.of() : newList;
    var diffResult =
        DiffUtil.calculateDiff(
            new DiffUtil.Callback() {
              @Override
              public int getOldListSize() {
                return resourceList.size();
              }

              @Override
              public int getNewListSize() {
                return target.size();
              }

              @Override
              public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return resourceList
                    .get(oldItemPosition)
                    .key()
                    .equals(target.get(newItemPosition).key());
              }

              @Override
              public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return resourceList.get(oldItemPosition).equals(target.get(newItemPosition));
              }
            });
    resourceList.clear();
    resourceList.addAll(target);
    diffResult.dispatchUpdatesTo(this);
  }

  @NonNull
  @Override
  public ResourceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ResourceViewHolder(
        LayoutInflater.from(parent.getContext()).inflate(R.layout.item_resource, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull ResourceViewHolder holder, int position) {
    var item = resourceList.get(position);
    if (item.type() == CardType.PHOTO
        && item.downloadPath() != null
        && !item.downloadPath().isEmpty()) {
      if (StorageReferenceUtils.isContentReference(item.downloadPath())) {
        Picasso.get()
            .load(Uri.parse(item.downloadPath()))
            .placeholder(R.drawable.ic_placeholder)
            .error(item.imageResId())
            .into(holder.resourceImage);
      } else {
        Picasso.get()
            .load(new File(item.downloadPath()))
            .placeholder(R.drawable.ic_placeholder)
            .error(item.imageResId())
            .into(holder.resourceImage);
      }
    } else if (item.thumbnailUrl() != null && !item.thumbnailUrl().isEmpty()) {
      Picasso.get()
          .load(item.thumbnailUrl())
          .placeholder(R.drawable.ic_placeholder)
          .error(item.imageResId())
          .into(holder.resourceImage);
    } else {
      holder.resourceImage.setImageResource(item.imageResId());
    }

    holder.resourceTypeIcon.setImageResource(item.type().getIconResId());

    holder.resourceVideoRow.setVisibility(View.GONE);
    holder.resourceImage.setVisibility(View.VISIBLE);
    if (item.text() != null && !item.text().isBlank()) {
      holder.resourceText.setText(item.text());
      holder.resourceText.setVisibility(View.VISIBLE);
    } else {
      holder.resourceText.setVisibility(View.GONE);
    }

    holder.itemView.setOnClickListener(v -> listener.onResourceClick(item, position));
    holder.itemView.setOnLongClickListener(
        v -> {
          listener.onResourceLongClick(item, position);
          return true;
        });

    if (!isFromReferrer) {
      boolean isDownloaded = selectionState != null && selectionState.isDownloaded(item);
      boolean isQueued = selectionState != null && selectionState.isQueued(item);
      boolean isSelected =
          isDownloaded || (selectionState != null && selectionState.isSelected(item));
      boolean unavailable = isQueued || isDownloaded;

      holder.checkContainer.setVisibility(View.VISIBLE);
      holder.checkIcon.setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);
      holder.checkContainer.setAlpha(unavailable ? 0.48f : 1f);
      holder.checkContainer.setEnabled(!unavailable);
      holder.checkContainer.setClickable(!unavailable);
      holder.checkContainer.setFocusable(!unavailable);
      holder.checkContainer.setBackgroundResource(
          isSelected ? R.drawable.bg_check_container_selected : R.drawable.bg_check_container);
      holder.checkContainer.setOnClickListener(
          v -> {
            if (unavailable) {
              return;
            }
            listener.onResourceSelectToggle(item, position);
          });
    } else holder.checkContainer.setVisibility(View.GONE);
  }

  public void refreshSelectionState() {
    notifyItemRangeChanged(0, getItemCount());
  }

  @Override
  public int getItemCount() {
    return resourceList.size();
  }

  public interface OnResourceClickListener {
    void onResourceClick(ResourceItem item, int position);

    void onResourceLongClick(ResourceItem item, int position);

    void onResourceSelectToggle(ResourceItem item, int position);
  }

  public interface SelectionState {
    boolean isSelected(ResourceItem item);

    boolean isQueued(ResourceItem item);

    boolean isDownloaded(ResourceItem item);
  }

  static class ResourceViewHolder extends RecyclerView.ViewHolder {
    final ImageView resourceImage, resourceVideoThumbnail, resourceTypeIcon, checkIcon;
    final TextView resourceText, resourceVideoTitle;
    final LinearLayout resourceVideoRow;
    final View checkContainer;

    ResourceViewHolder(@NonNull View itemView) {
      super(itemView);
      resourceImage = itemView.findViewById(R.id.resourceImage);
      resourceVideoRow = itemView.findViewById(R.id.resourceVideoRow);
      resourceVideoThumbnail = itemView.findViewById(R.id.resourceVideoThumbnail);
      resourceTypeIcon = itemView.findViewById(R.id.resourceTypeIcon);
      resourceText = itemView.findViewById(R.id.resourceText);
      resourceVideoTitle = itemView.findViewById(R.id.resourceVideoTitle);
      checkContainer = itemView.findViewById(R.id.resourceCheckContainer);
      checkIcon = itemView.findViewById(R.id.resourceCheckIcon);
    }
  }
}
