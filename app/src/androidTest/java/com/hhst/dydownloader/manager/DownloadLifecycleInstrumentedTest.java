package com.hhst.dydownloader.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.hhst.dydownloader.MainActivity;
import com.hhst.dydownloader.model.CardType;
import com.hhst.dydownloader.model.ResourceItem;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DownloadLifecycleInstrumentedTest {
  @Test
  public void queueStillProcessesTasksAfterMainActivityFinishesAndReopens() throws Exception {
    try (ActivityScenario<MainActivity> first = ActivityScenario.launch(MainActivity.class)) {
      first.onActivity(activity -> {});
    }
    ResourceItem item = new ResourceItem(CardType.VIDEO.getIconResId(),
        "Lifecycle test", CardType.VIDEO, 0, true, null);
    CountDownLatch processed = new CountDownLatch(1);
    DownloadQueue.Listener listener = tasks -> {
      for (DownloadTask task : tasks) {
        if (task.getResourceKey().equals(item.key())
            && task.getStatus() == DownloadTask.Status.FAILED
            && task.getError().contains("No downloadable URLs")) {
          processed.countDown();
        }
      }
    };
    try (ActivityScenario<MainActivity> reopened = ActivityScenario.launch(MainActivity.class)) {
      DownloadQueue.addListener(listener);
      reopened.onActivity(activity -> assertEquals(1, DownloadQueue.addAll(List.of(item))));
      assertTrue("The reopened queue must run the task", processed.await(10, TimeUnit.SECONDS));
    } finally {
      DownloadQueue.removeListener(listener);
      DownloadQueue.removeTasksByExactResourceKeys(Set.of(item.key()));
    }
  }
}
