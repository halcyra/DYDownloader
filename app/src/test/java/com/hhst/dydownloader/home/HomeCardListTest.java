package com.hhst.dydownloader.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.hhst.dydownloader.manager.DownloadTask;
import com.hhst.dydownloader.model.CardType;
import com.hhst.dydownloader.model.Platform;
import com.hhst.dydownloader.model.ResourceItem;
import java.util.Comparator;
import java.util.List;
import org.junit.Test;

public class HomeCardListTest {
  private static final Comparator<ResourceItem> ORDER = Comparator.comparingLong(ResourceItem::createTime);

  @Test
  public void queueUsesEnqueueTimeInsteadOfPublicationDate() {
    HomeCard oldPost = HomeCard.fromTask(new DownloadTask(item("old", "Old", CardType.VIDEO, 1), 200));
    HomeCard newPost = HomeCard.fromTask(new DownloadTask(item("new", "New", CardType.VIDEO, 100), 100));
    assertEquals(List.of(oldPost, newPost),
        HomeCardList.build(List.of(), List.of(newPost, oldPost), "", null, ORDER));
  }

  @Test
  public void typeFilterAppliesToBothQueueAndLibrary() {
    ResourceItem video = item("v", "Video", CardType.VIDEO, 1);
    ResourceItem photo = item("p", "Photo", CardType.PHOTO, 2);
    assertEquals(List.of(HomeCard.done(photo)), HomeCardList.build(
        List.of(photo), List.of(HomeCard.fromTask(new DownloadTask(video, 3))), "", CardType.PHOTO, ORDER));
  }

  @Test
  public void retryOverridesSavedCardEvenWhenOnlyOldTitleMatchesSearch() {
    ResourceItem saved = item("same", "Old title", CardType.ALBUM, 1);
    HomeCard queued = HomeCard.fromTask(new DownloadTask(item("same", "New title", CardType.ALBUM, 1)));
    assertTrue(HomeCardList.build(List.of(saved), List.of(queued), "Old", null, ORDER).isEmpty());
    assertEquals(List.of(queued), HomeCardList.build(List.of(saved), List.of(queued), "Creator", null, ORDER));
  }

  @Test
  public void retriedTaskNoLongerCarriesErrorInPresentation() {
    DownloadTask task = new DownloadTask(item("work", null, CardType.VIDEO, 1));
    task.setError("IOException: failed");
    task.setStatus(DownloadTask.Status.FAILED);
    assertEquals("failed", HomeCard.fromTask(task).error());
    task.setStatus(DownloadTask.Status.QUEUED);
    assertEquals("", HomeCard.fromTask(task).error());
    assertEquals(1, HomeCardList.build(List.of(), List.of(HomeCard.fromTask(task)), "Creator", null, ORDER).size());
  }

  private ResourceItem item(String key, String title, CardType type, long publishedAt) {
    return new ResourceItem(Platform.DOUYIN, null, 0L, 0, title, "Creator", type,
        publishedAt, 0, true, "", null, key, List.of(), false, "", "");
  }
}
