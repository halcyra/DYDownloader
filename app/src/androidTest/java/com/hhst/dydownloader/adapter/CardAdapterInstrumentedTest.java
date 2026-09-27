package com.hhst.dydownloader.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.res.Configuration;
import android.view.View;
import android.widget.FrameLayout;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.hhst.dydownloader.R;
import com.hhst.dydownloader.home.HomeCard;
import com.hhst.dydownloader.manager.DownloadTask;
import com.hhst.dydownloader.model.CardType;
import com.hhst.dydownloader.model.ResourceItem;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CardAdapterInstrumentedTest {
  @Test
  public void authorStaysAboveBottomTimestampForShortLongAndFailedCards() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      for (float fontScale : new float[] {1f, 1.6f}) {
        var target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config = new Configuration(target.getResources().getConfiguration());
        config.fontScale = fontScale;
        ContextThemeWrapper context = new ContextThemeWrapper(
            target.createConfigurationContext(config), R.style.Theme_DYDownloader);
        CardAdapter adapter = new CardAdapter(null);
        CardAdapter.CardViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(context), 0);
        int authorBottomOffset = -1;
        for (String title : List.of("Short", "A long title that takes up both lines of this card")) {
          for (HomeCard.State state : List.of(HomeCard.State.DONE, HomeCard.State.FAILED)) {
            ResourceItem item = new ResourceItem(null, null, 0L, CardType.VIDEO.getIconResId(),
                title, "Creator", CardType.VIDEO, 1L, 0, true, "", null, "video", List.of(),
                false, "", "");
            adapter.submitList(List.of(new HomeCard(item, state, 0, "Download failed", 0)));
            adapter.onBindViewHolder(holder, 0);
            int width = Math.round(180 * context.getResources().getDisplayMetrics().density);
            holder.itemView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            holder.itemView.layout(0, 0, width, holder.itemView.getMeasuredHeight());
            View info = holder.itemView.findViewById(R.id.cardInfoContainer);
            assertEquals(info.getHeight() - info.getPaddingBottom(), holder.cardTime.getBottom());
            int offset = info.getHeight() - holder.cardAuthor.getBottom();
            if (authorBottomOffset < 0) authorBottomOffset = offset;
            assertEquals(authorBottomOffset, offset);
            assertTrue(holder.cardText.getBottom() <= holder.cardAuthor.getTop());
            assertTrue(holder.cardAuthor.getBottom() <= holder.cardTime.getTop());
            if (state == HomeCard.State.FAILED) {
              assertTrue(holder.cardText.getBottom() <= holder.cardFailReason.getTop());
              assertTrue(holder.cardFailReason.getBottom() <= holder.cardAuthor.getTop());
            }
          }
        }
      }
    });
  }

  @Test
  public void retryClearsFailureTextOnReusedHolder() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      ContextThemeWrapper context = new ContextThemeWrapper(
          InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.Theme_DYDownloader);
      ResourceItem item = new ResourceItem(CardType.VIDEO.getIconResId(), "Video",
          CardType.VIDEO, 0, true, null);
      DownloadTask task = new DownloadTask(item);
      task.setStatus(DownloadTask.Status.FAILED);
      task.setError("IOException: failed");
      CardAdapter adapter = new CardAdapter(null);
      CardAdapter.CardViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(context), 0);
      adapter.submitList(List.of(HomeCard.fromTask(task)));
      adapter.onBindViewHolder(holder, 0);
      assertEquals(View.VISIBLE, holder.cardFailReason.getVisibility());
      task.setStatus(DownloadTask.Status.QUEUED);
      adapter.submitList(List.of(HomeCard.fromTask(task)));
      adapter.onBindViewHolder(holder, 0);
      assertEquals(View.GONE, holder.cardFailReason.getVisibility());
      assertEquals(View.GONE, holder.cardFailActions.getVisibility());
      assertEquals(View.VISIBLE, holder.cardProgressPercent.getVisibility());
    });
  }
}
