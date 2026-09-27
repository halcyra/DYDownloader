package com.hhst.dydownloader.adapter;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
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
      Picasso.get().cancelRequest(holder.resourceImage);
      holder.resourceImage.setImageResource(item.imageResId());
    }

    holder.resourceTypeIcon.setImageResource(item.type().getIconResId());

    if (item.text() != null && !item.text().isBlank()) {
      holder.resourceText.setText(item.text());
      holder.resourceText.setVisibility(View.VISIBLE);
    } else {
      holder.resourceText.setVisibility(View.GONE);
    }

    holder.itemView.setOnClickListener(
        v -> {
          if (holder.getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
            listener.onResourceClick(item);
          }
        });

    if (!isFromReferrer) {
      Context context = holder.checkContainer.getContext();
      boolean isDownloaded = selectionState != null && selectionState.isDownloaded(item);
      boolean isQueued = selectionState != null && selectionState.isQueued(item);
      boolean isSelected = selectionState != null && selectionState.isSelected(item);
      String stateDescription =
          context.getString(
              isQueued
                  ? R.string.resource_check_disabled_state
                  : isSelected
                      ? R.string.resource_check_selected_state
                      : isDownloaded
                          ? R.string.resource_check_downloaded_state
                          : R.string.resource_check_unselected_state);

      holder.checkContainer.setVisibility(View.VISIBLE);
      ViewCompat.setStateDescription(holder.checkContainer, stateDescription);
      holder.checkContainer.setContentDescription(
          context.getString(R.string.resource_check_container_desc));
      holder.checkIcon.setVisibility(isSelected || isDownloaded ? View.VISIBLE : View.INVISIBLE);
      if (isDownloaded && !isSelected) {
        holder.checkIcon.setColorFilter(
            com.google.android.material.color.MaterialColors.getColor(
                holder.checkIcon,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
      } else {
        holder.checkIcon.clearColorFilter();
      }
      holder.checkContainer.setAlpha(isQueued ? 0.48f : 1f);
      holder.checkContainer.setEnabled(!isQueued);
      holder.checkContainer.setClickable(!isQueued);
      holder.checkContainer.setFocusable(!isQueued);
      holder.checkContainer.setBackgroundResource(
          isSelected ? R.drawable.bg_check_container_selected : R.drawable.bg_check_container);
      holder.checkContainer.setOnClickListener(
          v -> {
            int currentPosition = holder.getBindingAdapterPosition();
            if (currentPosition == RecyclerView.NO_POSITION) {
              return;
            }
            listener.onResourceSelectToggle(resourceList.get(currentPosition), currentPosition);
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
    void onResourceClick(ResourceItem item);

    void onResourceSelectToggle(ResourceItem item, int position);
  }

  public interface SelectionState {
    boolean isSelected(ResourceItem item);

    boolean isQueued(ResourceItem item);

    boolean isDownloaded(ResourceItem item);
  }

  static class ResourceViewHolder extends RecyclerView.ViewHolder {
    final ImageView resourceImage, resourceTypeIcon, checkIcon;
    final TextView resourceText;
    final View checkContainer;

    ResourceViewHolder(@NonNull View itemView) {
      super(itemView);
      resourceImage = itemView.findViewById(R.id.resourceImage);
      resourceTypeIcon = itemView.findViewById(R.id.resourceTypeIcon);
      resourceText = itemView.findViewById(R.id.resourceText);
      checkContainer = itemView.findViewById(R.id.resourceCheckContainer);
      checkIcon = itemView.findViewById(R.id.resourceCheckIcon);
    }
  }
}
