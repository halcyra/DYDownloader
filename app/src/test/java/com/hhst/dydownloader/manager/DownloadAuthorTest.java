package com.hhst.dydownloader.manager;

import static org.junit.Assert.assertEquals;

import com.hhst.dydownloader.db.ResourceEntity;
import com.hhst.dydownloader.douyin.AwemeProfile;
import com.hhst.dydownloader.douyin.MediaType;
import com.hhst.dydownloader.model.CardType;
import com.hhst.dydownloader.model.Platform;
import com.hhst.dydownloader.model.ResourceItem;
import java.util.List;
import org.junit.Test;

public class DownloadAuthorTest {

  @Test
  public void downloadWithoutProfileKeepsSourceAuthorThroughResourceEntity() {
    ResourceItem item = item("创作者");

    assertEquals("创作者", DownloadManager.resolveAuthorNickname(null, item));
    assertEquals(
        "创作者", ResourceEntity.fromResourceItem(0L, item).toResourceItem().authorNickname());
  }

  @Test
  public void freshProfileAuthorTakesPriorityWhenAvailable() {
    AwemeProfile profile =
        new AwemeProfile(
            "7345678901234567890",
            MediaType.VIDEO,
            "作品",
            1L,
            "新作者",
            "",
            "",
            List.of());

    assertEquals("新作者", DownloadManager.resolveAuthorNickname(profile, item("旧作者")));
  }

  private static ResourceItem item(String author) {
    return new ResourceItem(
        Platform.DOUYIN,
        null,
        0L,
        0,
        "作品",
        author,
        CardType.ALBUM,
        1L,
        1,
        false,
        "",
        null,
        "7345678901234567890",
        List.of("https://example.com/video.mp4"),
        false,
        "",
        "");
  }
}
