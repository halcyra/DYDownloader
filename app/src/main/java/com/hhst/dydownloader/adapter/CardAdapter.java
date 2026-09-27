package com.hhst.dydownloader.adapter;

import android.graphics.drawable.Drawable;
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
import com.hhst.dydownloader.home.CardProgressDrawable;
import com.hhst.dydownloader.home.HomeCard;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Transformation;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CardAdapter extends RecyclerView.Adapter<CardAdapter.CardViewHolder> {

  private final List<HomeCard> cardList = new ArrayList<>();
  private final OnCardClickListener listener;
  private final SimpleDateFormat formatter =
      new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
  private final Set<String> selectedKeys = new HashSet<>();
  private boolean selectionMode = false;
  private int progressColor;

  public CardAdapter(OnCardClickListener listener) {
    this.listener = listener;
  }

  /** 进度描边用色，需在首次 bind 前设置。 */
  public void setProgressColor(int color) {
    this.progressColor = color;
  }

  public void submitList(List<HomeCard> newList) {
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
    Set<String> visibleKeys = new HashSet<>();
    for (HomeCard card : cardList) visibleKeys.add(card.key());
    boolean selectionChanged = selectedKeys.retainAll(visibleKeys);
    diffResult.dispatchUpdatesTo(this);
    if (selectionChanged && listener != null) listener.onSelectionChanged(selectedKeys.size());
  }

  @NonNull
  @Override
  public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new CardViewHolder(
        LayoutInflater.from(parent.getContext()).inflate(R.layout.item_card, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
    HomeCard card = cardList.get(position);
    var item = card.item();

    bindImage(holder, card);
    holder.cardText.setText(item.text());
    holder.cardAuthor.setText(item.authorNickname());
    holder.cardAuthor.setVisibility(
        item.authorNickname().isEmpty() ? View.GONE : View.VISIBLE);
    holder.cardTypeIcon.setImageResource(item.type().getIconResId());
    holder.cardTime.setText(formatter.format(new Date(item.createTime())));
    holder.cardMore.setVisibility(selectionMode ? View.GONE : View.VISIBLE);
    holder.cardView.setChecked(selectedKeys.contains(card.key()));

    bindProgress(holder, card);
    bindActions(holder, card);

    holder.itemView.setOnClickListener(
        v -> {
          int currentPosition = holder.getBindingAdapterPosition();
          if (currentPosition == RecyclerView.NO_POSITION) return;
          if (selectionMode) toggleSelection(card, currentPosition);
          else listener.onCardClick(card);
        });
    holder.itemView.setOnLongClickListener(
        v -> {
          if (!selectionMode && holder.getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
            listener.onCardLongClick(card);
          }
          return true;
        });
    holder.cardMore.setOnClickListener(
        v -> {
          if (!selectionMode && holder.getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
            listener.onCardMoreClick(card, v);
          }
        });
  }

  private void bindImage(CardViewHolder holder, HomeCard card) {
    boolean blur = card.state() == HomeCard.State.DOWNLOADING || card.state() == HomeCard.State.QUEUED;
    if (card.item().thumbnailUrl() != null && !card.item().thumbnailUrl().isEmpty()) {
      var request =
          Picasso.get()
              .load(card.item().thumbnailUrl())
              .placeholder(R.drawable.ic_placeholder)
              .error(card.item().imageResId());
      if (blur) {
        request = request.transform(new BlurTransformation());
      }
      request.into(holder.cardImage);
    } else {
      Picasso.get().cancelRequest(holder.cardImage);
      holder.cardImage.setImageResource(card.item().imageResId());
    }
  }

  private void bindProgress(CardViewHolder holder, HomeCard card) {
    var context = holder.itemView.getContext();
    Drawable background = holder.cardProgressOutline.getBackground();
    CardProgressDrawable progress;
    if (background instanceof CardProgressDrawable) {
      progress = (CardProgressDrawable) background;
    } else {
      progress =
          new CardProgressDrawable(
              progressColor,
              com.google.android.material.color.MaterialColors.getColor(
                  holder.cardView, androidx.appcompat.R.attr.colorError),
              dp(3, context),
              dp(12, context));
      holder.cardProgressOutline.setBackground(progress);
    }
    switch (card.state()) {
      case DONE -> {
        progress.setFailed(false);
        progress.setProgress(1f);
        holder.cardProgressPercent.setVisibility(View.GONE);
        holder.cardFailReason.setVisibility(View.GONE);
      }
      case QUEUED -> {
        progress.setFailed(false);
        progress.setProgress(0f);
        showPercent(holder, context.getString(R.string.download_status_queued_short));
      }
      case DOWNLOADING -> {
        progress.setFailed(false);
        progress.setProgress(card.progress() / 100f);
        showPercent(holder, card.progress() + "%");
      }
      case FAILED -> {
        progress.setFailed(true);
        progress.setProgress(1f);
        holder.cardProgressPercent.setVisibility(View.GONE);
        String reason =
            card.error() == null || card.error().isBlank()
                ? context.getString(R.string.download_status_failed_short)
                : card.error();
        holder.cardFailReason.setText(
            context.getString(R.string.download_failed_reason, reason));
        holder.cardFailReason.setVisibility(View.VISIBLE);
      }
    }
  }

  private void showPercent(CardViewHolder holder, String text) {
    holder.cardFailReason.setVisibility(View.GONE);
    holder.cardProgressPercent.setText(text);
    holder.cardProgressPercent.setVisibility(View.VISIBLE);
  }

  private void bindActions(CardViewHolder holder, HomeCard card) {
    boolean failed = card.state() == HomeCard.State.FAILED;
    holder.cardFailActions.setVisibility(failed && !selectionMode ? View.VISIBLE : View.GONE);
    holder.cardRetry.setOnClickListener(
        v -> {
          if (!selectionMode) listener.onCardRetryClick(card);
        });
    holder.cardDelete.setOnClickListener(
        v -> {
          if (!selectionMode) listener.onCardDeleteClick(card);
        });
  }

  public boolean isSelectionMode() {
    return selectionMode;
  }

  public void setSelectionMode(boolean enabled) {
    if (this.selectionMode != enabled) {
      this.selectionMode = enabled;
      selectedKeys.clear();
      notifyItemRangeChanged(0, getItemCount());
    }
  }

  private void toggleSelection(HomeCard card, int position) {
    if (!selectedKeys.remove(card.key())) selectedKeys.add(card.key());
    notifyItemChanged(position);
    if (listener != null) listener.onSelectionChanged(selectedKeys.size());
  }

  public List<HomeCard> getSelectedCards() {
    List<HomeCard> selected = new ArrayList<>();
    for (HomeCard card : cardList) {
      if (selectedKeys.contains(card.key())) {
        selected.add(card);
      }
    }
    return selected;
  }

  @Override
  public int getItemCount() {
    return cardList.size();
  }

  private static int dp(int dp, android.content.Context context) {
    return Math.round(dp * context.getResources().getDisplayMetrics().density);
  }

  public interface OnCardClickListener {
    void onCardClick(HomeCard card);

    void onCardLongClick(HomeCard card);

    default void onCardMoreClick(HomeCard card, View anchorView) {}

    default void onCardRetryClick(HomeCard card) {}

    default void onCardDeleteClick(HomeCard card) {}

    default void onSelectionChanged(int count) {}
  }

  /** 在缩小的封面上做盒式模糊，限制逐像素处理的开销。 */
  static class BlurTransformation implements Transformation {

    @Override
    public android.graphics.Bitmap transform(android.graphics.Bitmap source) {
      int width = source.getWidth();
      int height = source.getHeight();
      float scale = 64f / Math.max(1, Math.max(width, height));
      if (scale >= 1f) {
        return source;
      }
      int smallW = Math.max(1, Math.round(width * scale));
      int smallH = Math.max(1, Math.round(height * scale));
      android.graphics.Bitmap small =
          android.graphics.Bitmap.createScaledBitmap(source, smallW, smallH, true);
      android.graphics.Bitmap blurred = boxBlur(small);
      android.graphics.Bitmap result =
          android.graphics.Bitmap.createScaledBitmap(blurred, width, height, true);
      if (small != source) {
        small.recycle();
      }
      if (blurred != source) {
        blurred.recycle();
      }
      // Picasso requires recycling the input whenever a transformation returns a new bitmap.
      if (result != source) {
        source.recycle();
      }
      return result;
    }

    private static android.graphics.Bitmap boxBlur(android.graphics.Bitmap source) {
      int w = source.getWidth();
      int h = source.getHeight();
      int[] pixels = new int[w * h];
      source.getPixels(pixels, 0, w, 0, 0, w, h);
      int[] out = new int[w * h];
      for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
          long r = 0, g = 0, b = 0;
          int count = 0;
          for (int dy = -1; dy <= 1; dy++) {
            int ny = y + dy;
            if (ny < 0 || ny >= h) continue;
            for (int dx = -1; dx <= 1; dx++) {
              int nx = x + dx;
              if (nx < 0 || nx >= w) continue;
              int p = pixels[ny * w + nx];
              r += (p >> 16) & 0xFF;
              g += (p >> 8) & 0xFF;
              b += p & 0xFF;
              count++;
            }
          }
          out[y * w + x] =
              0xFF000000
                  | ((int) (r / count) << 16)
                  | ((int) (g / count) << 8)
                  | (int) (b / count);
        }
      }
      return android.graphics.Bitmap.createBitmap(out, w, h, android.graphics.Bitmap.Config.ARGB_8888);
    }

    @Override
    public String key() {
      return "blur";
    }
  }

  static class CardViewHolder extends RecyclerView.ViewHolder {
    final MaterialCardView cardView;
    final View cardProgressOutline;
    final ImageView cardImage, cardTypeIcon;
    final View cardMore, cardFailActions, cardRetry, cardDelete;
    final TextView cardText, cardAuthor, cardTime, cardProgressPercent, cardFailReason;

    CardViewHolder(@NonNull View itemView) {
      super(itemView);
      cardView = itemView.findViewById(R.id.materialCardView);
      cardProgressOutline = itemView.findViewById(R.id.cardProgressOutline);
      cardImage = itemView.findViewById(R.id.cardImage);
      cardText = itemView.findViewById(R.id.cardText);
      cardAuthor = itemView.findViewById(R.id.cardAuthor);
      cardTypeIcon = itemView.findViewById(R.id.cardTypeIcon);
      cardTime = itemView.findViewById(R.id.cardTime);
      cardProgressPercent = itemView.findViewById(R.id.cardProgressPercent);
      cardFailReason = itemView.findViewById(R.id.cardFailReason);
      cardFailActions = itemView.findViewById(R.id.cardFailActions);
      cardRetry = itemView.findViewById(R.id.cardRetry);
      cardDelete = itemView.findViewById(R.id.cardDelete);
      cardMore = itemView.findViewById(R.id.cardMore);
    }
  }
}
