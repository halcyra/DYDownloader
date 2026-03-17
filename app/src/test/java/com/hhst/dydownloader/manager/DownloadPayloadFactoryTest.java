package com.hhst.dydownloader.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.hhst.dydownloader.douyin.AwemeProfile;
import com.hhst.dydownloader.douyin.MediaType;
import com.hhst.dydownloader.model.CardType;
import com.hhst.dydownloader.model.Platform;
import com.hhst.dydownloader.model.ResourceItem;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class DownloadPayloadFactoryTest {

  @Test
  public void build_imagePostUsesPhotoChildAsCoverForLiveAsset() throws Exception {
    ResourceItem liveCoverChild =
        new ResourceItem(
            null,
            1L,
            CardType.PHOTO.getIconResId(),
            "Photo 01 - Cover",
            CardType.PHOTO,
            1L,
            0,
            true,
            "https://example.com/thumb.jpg",
            null,
            "aweme123#photo:1",
            List.of("https://example.com/cover.jpg"),
            true,
            "");
    ResourceItem liveVideoChild =
        new ResourceItem(
            null,
            1L,
            CardType.VIDEO.getIconResId(),
            "Photo 01",
            CardType.VIDEO,
            1L,
            0,
            true,
            "https://example.com/thumb.jpg",
            null,
            "aweme123#live:1",
            List.of("https://example.com/live.mp4"),
            false,
            "");
    ResourceItem rootItem =
        new ResourceItem(
            1L,
            0L,
            CardType.ALBUM.getIconResId(),
            "Work",
            CardType.ALBUM,
            1L,
            2,
            false,
            "https://example.com/root-thumb.jpg",
            List.of(liveCoverChild, liveVideoChild),
            "aweme123",
            List.of("https://example.com/live.mp4"),
            true,
            "");

    DownloadPayload payload = new DownloadPayloadFactory().build("", rootItem);

    assertEquals("https://example.com/cover.jpg", payload.coverUrlAt(0));
  }

  @Test
  public void build_videoUsesRootThumbnailAsCover() throws Exception {
    ResourceItem videoRoot =
        new ResourceItem(
            1L,
            0L,
            CardType.ALBUM.getIconResId(),
            "Video Work",
            CardType.ALBUM,
            1L,
            1,
            false,
            "https://example.com/video-thumb.jpg",
            null,
            "aweme456",
            List.of("https://example.com/video.mp4"),
            false,
            "");

    DownloadPayload payload = new DownloadPayloadFactory().build("", videoRoot);

    assertEquals("https://example.com/video-thumb.jpg", payload.coverUrlAt(0));
  }

  @Test
  public void build_liveLeafUsesThumbnailAsCover() throws Exception {
    ResourceItem liveLeaf =
        new ResourceItem(
            null,
            1L,
            CardType.VIDEO.getIconResId(),
            "Photo 01",
            CardType.VIDEO,
            1L,
            0,
            true,
            "https://example.com/live-cover.jpg",
            null,
            "aweme789#live:1",
            List.of("https://example.com/live.mp4"),
            false,
            "");

    DownloadPayload payload = new DownloadPayloadFactory().build("", liveLeaf);

    assertEquals("https://example.com/live-cover.jpg", payload.coverUrlAt(0));
  }

  @Test
  public void build_missingUrlsUsesPlatformSpecificProfileLoader() throws Exception {
    AtomicReference<Platform> requestedPlatform = new AtomicReference<>();
    AtomicReference<String> requestedSourceKey = new AtomicReference<>();
    DownloadPayloadFactory factory =
        new DownloadPayloadFactory(
            (platform, cookie, sourceKey) -> {
              requestedPlatform.set(platform);
              requestedSourceKey.set(sourceKey);
              return new AwemeProfile(
                  Platform.TIKTOK,
                  "7345678901234567890",
                  MediaType.VIDEO,
                  "TikTok work",
                  1L,
                  "creator",
                  "secUid",
                  "",
                  "https://example.com/cover.jpg",
                  List.of("https://example.com/cover.jpg"),
                  List.of("https://example.com/video.mp4"),
                  List.of());
            });
    ResourceItem item =
        new ResourceItem(
            Platform.TIKTOK,
            1L,
            0L,
            CardType.ALBUM.getIconResId(),
            "TikTok work",
            CardType.ALBUM,
            1L,
            0,
            false,
            "https://example.com/cover.jpg",
            null,
            "7345678901234567890",
            List.of(),
            false,
            "");

    DownloadPayload payload = factory.build("cookie", item);

    assertEquals(Platform.TIKTOK, requestedPlatform.get());
    assertEquals("7345678901234567890", requestedSourceKey.get());
    assertNotNull(payload.profile());
    assertEquals(Platform.TIKTOK, payload.profile().platform());
    assertEquals("https://example.com/video.mp4", payload.urls().get(0));
  }
}
