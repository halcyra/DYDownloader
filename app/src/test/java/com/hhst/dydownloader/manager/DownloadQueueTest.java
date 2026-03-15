package com.hhst.dydownloader.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hhst.dydownloader.model.CardType;
import com.hhst.dydownloader.model.ResourceItem;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class DownloadQueueTest {

  @Test
  public void collectKeysByStatuses_excludesFailedTasksFromQueuedKeys() {
    DownloadTask queued = task("aweme-a#photo:1", DownloadTask.Status.QUEUED);
    DownloadTask downloading = task("aweme-b#video", DownloadTask.Status.DOWNLOADING);
    DownloadTask failed = task("aweme-c#photo:1", DownloadTask.Status.FAILED);
    DownloadTask completed = task("aweme-d#video", DownloadTask.Status.COMPLETED);

    Set<String> keys =
        DownloadQueue.collectKeysByStatuses(
            List.of(queued, downloading, failed, completed),
            EnumSet.of(DownloadTask.Status.QUEUED, DownloadTask.Status.DOWNLOADING));

    assertEquals(Set.of("aweme-a#photo:1", "aweme-b#video"), keys);
  }

  @Test
  public void matchesAnyResourceKey_matchesByBaseSourceKey() {
    Set<String> targets = Set.of("aweme-1234567890");
    assertTrue(DownloadQueue.matchesAnyResourceKey("aweme-1234567890#photo:2", targets));
    assertFalse(DownloadQueue.matchesAnyResourceKey("aweme-99887766#photo:2", targets));
  }

  @Test
  public void matchesAnyExactResourceKey_requiresExactLeafMatch() {
    Set<String> targets = Set.of("aweme-1234567890#photo:1");

    assertTrue(DownloadQueue.matchesAnyExactResourceKey("aweme-1234567890#photo:1", targets));
    assertFalse(DownloadQueue.matchesAnyExactResourceKey("aweme-1234567890#photo:2", targets));
    assertFalse(DownloadQueue.matchesAnyExactResourceKey("aweme-1234567890#live:1", targets));
  }

  private DownloadTask task(String sourceKey, DownloadTask.Status status) {
    ResourceItem item =
        new ResourceItem(
            null,
            0L,
            0,
            "item",
            CardType.ALBUM,
            1L,
            0,
            true,
            "",
            null,
            sourceKey,
            List.of(),
            false,
            "");
    DownloadTask task = new DownloadTask(sourceKey, item, 1L);
    task.setStatus(status);
    return task;
  }
}
