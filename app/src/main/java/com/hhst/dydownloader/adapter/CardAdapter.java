package com.hhst.dydownloader.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.hhst.dydownloader.R;
import com.hhst.dydownloader.model.ResourceItem;
import com.squareup.picasso.Picasso;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CardAdapter extends RecyclerView.Adapter<CardAdapter.CardViewHolder> {

  private final List<ResourceItem> cardList = new ArrayList<>();
  private final OnCardClickListener listener;
  private final SimpleDateFormat formatter =
      new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
  private final Set<ResourceItem> selectedItems = new HashSet<>();
  private boolean selectionMode = false;

  public CardAdapter(OnCardClickListener listener) {
    this.listener = listener;
  }

  public void submitList(List<ResourceItem> newList) {
    var diffResult =
        DiffUtil.calculateDiff(
            new DiffUtil.Callback() {
              @Override
              public int getOldListSize() {
                return cardList.size();
              }

              @Override
              public int getNewListSize() {
                return newList.size();
              }

              @Override
              public boolean areItemsTheSame(int oldPos, int newPos) {
                return cardList.get(oldPos).key().equals(newList.get(newPos).key());
              }

              @Override
              public boolean areContentsTheSame(int oldPos, int newPos) {
                return cardList.get(oldPos).equals(newList.get(newPos));
              }
            });
    cardList.clear();
    cardList.addAll(newList);
    diffResult.dispatchUpdatesTo(this);
  }

  @NonNull
  @Override
  public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new CardViewHolder(
        LayoutInflater.from(parent.getContext()).inflate(R.layout.item_card, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
    var item = cardList.get(position);

    if (item.thumbnailUrl() != null && !item.thumbnailUrl().isEmpty()) {
      Picasso.get()
          .load(item.thumbnailUrl())
          .placeholder(R.drawable.ic_placeholder)
          .error(item.imageResId())
          .into(holder.cardImage);
    } else {
      holder.cardImage.setImageResource(item.imageResId());
    }

    holder.cardText.setText(item.text());
    holder.cardTypeIcon.setImageResource(item.type().getIconResId());

    holder.cardChildrenNum.setVisibility(View.GONE);
    holder.cardChildrenNum.setText("");

    holder.cardTime.setText(formatter.format(new Date(item.createTime())));
    holder.cardView.setChecked(selectedItems.contains(item));
    holder.cardMore.setVisibility(selectionMode ? View.GONE : View.VISIBLE);

    holder.itemView.setOnClickListener(
        v -> {
          if (selectionMode) toggleSelection(item);
          else listener.onCardClick(item, position);
        });

    holder.itemView.setOnLongClickListener(
        v -> {
          if (!selectionMode) listener.onCardLongClick(item, position);
          return true;
        });
    holder.cardMore.setOnClickListener(
        v -> {
          if (!selectionMode) {
            listener.onCardMoreClick(item, position, v);
          }
        });
  }

  public boolean isSelectionMode() {
    return selectionMode;
  }

  public void setSelectionMode(boolean enabled) {
    if (this.selectionMode != enabled) {
      this.selectionMode = enabled;
      selectedItems.clear();
      notifyItemRangeChanged(0, getItemCount());
    }
  }

  private void toggleSelection(ResourceItem item) {
    if (!selectedItems.remove(item)) selectedItems.add(item);
    notifyItemChanged(cardList.indexOf(item));
    if (listener != null) listener.onSelectionChanged(selectedItems.size());
  }

  public Set<ResourceItem> getSelectedItems() {
    return selectedItems;
  }

  @Override
  public int getItemCount() {
    return cardList.size();
  }

  public interface OnCardClickListener {
    void onCardClick(ResourceItem item, int position);

    void onCardLongClick(ResourceItem item, int position);

    default void onCardMoreClick(ResourceItem item, int position, View anchorView) {}

    default void onSelectionChanged(int count) {}
  }

  static class CardViewHolder extends RecyclerView.ViewHolder {
    final MaterialCardView cardView;
    final ImageView cardImage, cardTypeIcon;
    final View cardMore;
    final TextView cardText, cardTime, cardChildrenNum;

    CardViewHolder(@NonNull View itemView) {
      super(itemView);
      cardView = itemView.findViewById(R.id.materialCardView);
      cardImage = itemView.findViewById(R.id.cardImage);
      cardText = itemView.findViewById(R.id.cardText);
      cardTypeIcon = itemView.findViewById(R.id.cardTypeIcon);
      cardTime = itemView.findViewById(R.id.cardTime);
      cardChildrenNum = itemView.findViewById(R.id.cardChildrenNum);
      cardMore = itemView.findViewById(R.id.cardMore);
    }
  }
}
