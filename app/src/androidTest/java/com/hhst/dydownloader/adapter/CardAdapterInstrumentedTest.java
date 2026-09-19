package com.hhst.dydownloader.adapter;

import static org.junit.Assert.assertEquals;

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
