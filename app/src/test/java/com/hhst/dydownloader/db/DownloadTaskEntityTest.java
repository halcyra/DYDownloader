package com.hhst.dydownloader.db;

import static org.junit.Assert.assertEquals;

import com.hhst.dydownloader.manager.DownloadTask;
import com.hhst.dydownloader.model.CardType;
import com.hhst.dydownloader.model.Platform;
import org.junit.Test;

public class DownloadTaskEntityTest {

  @Test
  public void toTask_usesTaskKeyPlatformWhenPersistedPlatformIsMissing() {
    DownloadTaskEntity entity = baseEntity();
    entity.platform = null;
    entity.taskKey = "TIKTOK:aweme-1#video";
    entity.sourceKey = "aweme-1#video";

    DownloadTask task = entity.toTask();

    assertEquals(Platform.TIKTOK, task.getResourceItem().platform());
    assertEquals("TIKTOK:aweme-1#video", task.getResourceKey());
  }

  @Test
  public void toTask_fallsBackToPersistedPlatformForLegacyTaskKey() {
    DownloadTaskEntity entity = baseEntity();
    entity.platform = Platform.DOUYIN;
    entity.taskKey = "aweme-1#video";
    entity.sourceKey = "aweme-1#video";

    DownloadTask task = entity.toTask();

    assertEquals(Platform.DOUYIN, task.getResourceItem().platform());
    assertEquals("DOUYIN:aweme-1#video", task.getResourceKey());
  }

  @Test
  public void toTask_prefersTaskKeyPlatformWhenPersistedPlatformMismatches() {
    DownloadTaskEntity entity = baseEntity();
    entity.platform = Platform.DOUYIN;
    entity.taskKey = "TIKTOK:aweme-1#video";
    entity.sourceKey = "aweme-1#video";

    DownloadTask task = entity.toTask();

    assertEquals(Platform.TIKTOK, task.getResourceItem().platform());
    assertEquals("TIKTOK:aweme-1#video", task.getResourceKey());
  }

  private static DownloadTaskEntity baseEntity() {
    DownloadTaskEntity entity = new DownloadTaskEntity();
    entity.resourceId = 1L;
    entity.parentId = 0L;
    entity.imageResId = 0;
    entity.text = "item";
    entity.type = CardType.VIDEO;
    entity.createTime = 1L;
    entity.childrenNum = 0;
    entity.isLeaf = true;
    entity.thumbnailUrl = "";
    entity.downloadUrlsJson = "[\"https://example.com/video.mp4\"]";
    entity.imagePost = false;
    entity.storageDir = "";
    entity.status = DownloadTask.Status.QUEUED.name();
    entity.progress = 0;
    entity.error = null;
    entity.addedAt = 1L;
    return entity;
  }
}
