package com.hhst.dydownloader.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hhst.dydownloader.db.DownloadTaskEntity;
import com.hhst.dydownloader.model.Platform;
import com.hhst.dydownloader.model.CardType;
import com.hhst.dydownloader.model.ResourceItem;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class DownloadQueueTest {

  @Test
  public void collectKeysByStatuses_excludesFailedTasksFromQueuedKeys() {
    DownloadTask queued = task(Platform.DOUYIN, "aweme-a#photo:1", DownloadTask.Status.QUEUED);
    DownloadTask downloading =
        task(Platform.DOUYIN, "aweme-b#video", DownloadTask.Status.DOWNLOADING);
    DownloadTask failed = task(Platform.DOUYIN, "aweme-c#photo:1", DownloadTask.Status.FAILED);
    DownloadTask completed =
        task(Platform.DOUYIN, "aweme-d#video", DownloadTask.Status.COMPLETED);

    Set<String> keys =
        DownloadQueue.collectKeysByStatuses(
            List.of(queued, downloading, failed, completed),
            EnumSet.of(DownloadTask.Status.QUEUED, DownloadTask.Status.DOWNLOADING));

    assertEquals(Set.of("DOUYIN:aweme-a#photo:1", "DOUYIN:aweme-b#video"), keys);
  }

  @Test
  public void matchesAnyResourceKey_matchesByBaseSourceKey() {
    Set<String> targets = Set.of("TIKTOK:aweme-1234567890");
    assertTrue(
        DownloadQueue.matchesAnyResourceKey("TIKTOK:aweme-1234567890#photo:2", targets));
    assertFalse(
        DownloadQueue.matchesAnyResourceKey("DOUYIN:aweme-1234567890#photo:2", targets));
  }

  @Test
  public void matchesAnyExactResourceKey_requiresExactLeafMatch() {
    Set<String> targets = Set.of("TIKTOK:aweme-1234567890#photo:1");

    assertTrue(
        DownloadQueue.matchesAnyExactResourceKey("TIKTOK:aweme-1234567890#photo:1", targets));
    assertFalse(
        DownloadQueue.matchesAnyExactResourceKey("TIKTOK:aweme-1234567890#photo:2", targets));
    assertFalse(
        DownloadQueue.matchesAnyExactResourceKey("DOUYIN:aweme-1234567890#photo:1", targets));
  }

  @Test
  public void downloadTaskEntityRoundTrip_preservesPlatformAwareResourceKey() {
    DownloadTask original = task(Platform.TIKTOK, "aweme-1234567890#video", DownloadTask.Status.QUEUED);

    DownloadTask restored = DownloadTaskEntity.fromTask(original).toTask();

    assertEquals("TIKTOK:aweme-1234567890#video", restored.getResourceKey());
    assertEquals(Platform.TIKTOK, restored.getResourceItem().platform());
  }

  private DownloadTask task(
      Platform platform, String sourceKey, DownloadTask.Status status) {
    ResourceItem item =
        new ResourceItem(
            platform,
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
    DownloadTask task = new DownloadTask(item.key(), item, 1L);
    task.setStatus(status);
    return task;
  }
}
