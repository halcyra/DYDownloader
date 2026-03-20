package com.hhst.dydownloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hhst.dydownloader.douyin.AwemeProfile;
import com.hhst.dydownloader.douyin.MediaType;
import com.hhst.dydownloader.model.CardType;
import com.hhst.dydownloader.model.Platform;
import com.hhst.dydownloader.model.ResourceItem;
import com.hhst.dydownloader.share.ShareLinkResolver.LinkKind;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ResourceScreenSupportTest {

  @Test
  public void snapshot_persistsNestedResourceItemsOutsideBundlePayload() throws Exception {
    ResourceItem child =
        new ResourceItem(
            Platform.TIKTOK,
            11L,
            7L,
            CardType.VIDEO.getIconResId(),
            "Video",
            CardType.VIDEO,
            2L,
            0,
            true,
            "thumb",
            null,
            "aweme-1#video",
            List.of("https://video.example.com/1.mp4"),
            false,
            "",
            "creator/Work");
    ResourceItem root =
        new ResourceItem(
            Platform.TIKTOK,
            7L,
            0L,
            CardType.ALBUM.getIconResId(),
            "Work",
            CardType.ALBUM,
            1L,
            1,
            false,
            "thumb",
            List.of(child),
            "aweme-1",
            List.of(),
            false,
            "",
            "creator/Work");

    File snapshotDir = Files.createTempDirectory("dy-resource-snapshot").toFile();
    String token = persist(snapshotDir, "screen-1", List.of(root));
    ArrayList<ResourceItem> restored = restore(snapshotDir, token);

    assertEquals(1, restored.size());
    assertEquals("screen-1", token);
    assertEquals(Platform.TIKTOK, restored.get(0).platform());
    assertEquals("creator/Work", restored.get(0).storageDir());
    assertEquals(1, restored.get(0).children().size());
    assertEquals("aweme-1#video", restored.get(0).children().get(0).sourceKey());
    assertTrue(new File(snapshotDir, "screen-1.json").exists());
  }

  @Test
  public void resolveDownloadGroupDir_usesFallbackTitleForSingleWorkLinks() throws Exception {
    String result = resolveDownloadGroupDir(LinkKind.WORK, List.of(), "Single Work");
    assertEquals("Single Work", result);
  }

  @Test
  public void resolveDownloadGroupDir_usesAwemeIdForUntitledSingleWorkLinks() throws Exception {
    AwemeProfile workProfile =
        new AwemeProfile(
            Platform.TIKTOK,
            "7345678901234567890",
            MediaType.VIDEO,
            "",
            1L,
            "creator",
            "sec",
            "",
            "thumb",
            List.of("thumb"),
            List.of("video"),
            List.of());

    String result =
        resolveDownloadGroupDir(LinkKind.WORK, List.of(workProfile), "Work Detail");

    assertEquals("7345678901234567890", result);
  }

  @Test
  public void resolveDownloadGroupDir_prefersCollectionOrAuthorTitlesWhenAvailable()
      throws Exception {
    AwemeProfile collectionProfile =
        new AwemeProfile(
            Platform.TIKTOK,
            "123",
            MediaType.VIDEO,
            "desc",
            1L,
            "creator",
            "sec",
            "Playlist title",
            "thumb",
            List.of("thumb"),
            List.of("video"),
            List.of());
    AwemeProfile accountProfile =
        new AwemeProfile(
            Platform.TIKTOK,
            "234",
            MediaType.VIDEO,
            "desc",
            1L,
            "creator",
            "sec",
            "",
            "thumb",
            List.of("thumb"),
            List.of("video"),
            List.of());

    assertEquals(
        "Playlist title",
        resolveDownloadGroupDir(LinkKind.MIX, List.of(collectionProfile), "fallback"));
    assertEquals(
        "creator", resolveDownloadGroupDir(LinkKind.ACCOUNT, List.of(accountProfile), "fallback"));
  }

  @Test
  public void snapshot_restoreBlankTokenReturnsEmptyList() throws Exception {
    File snapshotDir = Files.createTempDirectory("dy-resource-snapshot-empty").toFile();
    assertTrue(restore(snapshotDir, "").isEmpty());
  }

  @Test
  public void shouldPersistSnapshot_supportsShareLinkBackedScreensWithoutDbId() throws Exception {
    ResourceItem item =
        new ResourceItem(
            Platform.TIKTOK,
            null,
            -1L,
            CardType.VIDEO.getIconResId(),
            "Work",
            CardType.VIDEO,
            1L,
            0,
            true,
            "thumb",
            null,
            "aweme-1",
            List.of("https://video.example.com/1.mp4"),
            false,
            "",
            "creator/Work");

    assertTrue(shouldPersistSnapshot(-1L, List.of(item)));
  }

  @Test
  public void resourceScreenSnapshot_sourceAvoidsJavaNioFilesDependency() throws Exception {
    String source =
        readSource(
            "app",
            "src",
            "main",
            "java",
            "com",
            "hhst",
            "dydownloader",
            "ResourceScreenSnapshot.java");

    assertFalse(source.contains("java.nio.file.Files"));
    assertFalse(source.contains("Files.write("));
    assertFalse(source.contains("Files.readAllBytes("));
  }

  private static String persist(File directory, String token, List<ResourceItem> items)
      throws Exception {
    Class<?> snapshotClass = Class.forName("com.hhst.dydownloader.ResourceScreenSnapshot");
    Method method = snapshotClass.getDeclaredMethod("persist", File.class, String.class, List.class);
    method.setAccessible(true);
    return (String) method.invoke(null, directory, token, items);
  }

  @SuppressWarnings("unchecked")
  private static ArrayList<ResourceItem> restore(File directory, String token) throws Exception {
    Class<?> snapshotClass = Class.forName("com.hhst.dydownloader.ResourceScreenSnapshot");
    Method method = snapshotClass.getDeclaredMethod("restore", File.class, String.class);
    method.setAccessible(true);
    return (ArrayList<ResourceItem>) method.invoke(null, directory, token);
  }

  private static String resolveDownloadGroupDir(
      LinkKind kind, List<AwemeProfile> profiles, String fallbackTitle) throws Exception {
    Class<?> supportClass = Class.forName("com.hhst.dydownloader.ResourceScreenSupport");
    Method method =
        supportClass.getDeclaredMethod(
            "resolveDownloadGroupDir", LinkKind.class, List.class, String.class);
    method.setAccessible(true);
    return (String) method.invoke(null, kind, profiles, fallbackTitle);
  }

  private static boolean shouldPersistSnapshot(long resourceId, List<ResourceItem> items)
      throws Exception {
    Class<?> supportClass = Class.forName("com.hhst.dydownloader.ResourceScreenSupport");
    Method method =
        supportClass.getDeclaredMethod("shouldPersistSnapshot", long.class, List.class);
    method.setAccessible(true);
    return (boolean) method.invoke(null, resourceId, items);
  }

  private static String readSource(String... segments) throws Exception {
    Path directPath = Path.of("", segments);
    if (Files.exists(directPath)) {
      return new String(Files.readAllBytes(directPath), StandardCharsets.UTF_8);
    }
    String[] prefixedSegments = new String[segments.length - 1];
    System.arraycopy(segments, 1, prefixedSegments, 0, prefixedSegments.length);
    return new String(Files.readAllBytes(Path.of("", prefixedSegments)), StandardCharsets.UTF_8);
  }
}
