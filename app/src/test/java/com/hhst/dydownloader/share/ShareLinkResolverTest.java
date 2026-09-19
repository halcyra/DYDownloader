package com.hhst.dydownloader.share;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hhst.dydownloader.model.Platform;
import com.hhst.dydownloader.share.ShareLinkResolver.LinkKind;
import org.junit.Test;

public class ShareLinkResolverTest {

  @Test
  public void resolve_prioritizesWorkOpenedOnAccountPage() {
    assertEquals(LinkKind.WORK, ShareLinkResolver.resolve(
        "https://www.douyin.com/user/MS4wLjABAAAA?modal_id=7345678901234567890").kind());
  }

  @Test
  public void resolve_acceptsSharedAccountPaths() {
    assertEquals(LinkKind.ACCOUNT, ShareLinkResolver.resolve(
        "https://www.iesdouyin.com/share/user/MS4wLjABAAAA").kind());
  }

  @Test
  public void resolve_detectsTiktokShortLinkAsWork() {
    ShareLinkResolver.Result result =
        ShareLinkResolver.resolve("check https://vm.tiktok.com/ZM1234567/ now");

    assertEquals(Platform.TIKTOK, result.platform());
    assertEquals(LinkKind.WORK, result.kind());
  }

  @Test
  public void resolve_detectsDouyinAccountLink() {
    ShareLinkResolver.Result result =
        ShareLinkResolver.resolve("https://www.douyin.com/user/MS4wLjABAAAA");

    assertEquals(Platform.DOUYIN, result.platform());
    assertEquals(LinkKind.ACCOUNT, result.kind());
  }

  @Test
  public void resolve_marksUnsupportedUrls() {
    ShareLinkResolver.Result result = ShareLinkResolver.resolve("https://example.com/video/123");

    assertFalse(result.supported());
    assertEquals(LinkKind.UNKNOWN, result.kind());
  }

  @Test
  public void resolve_detectsTiktokCollectionLinks() {
    ShareLinkResolver.Result result =
        ShareLinkResolver.resolve("https://www.tiktok.com/@creator/collection/name-734567890123");

    assertEquals(Platform.TIKTOK, result.platform());
    assertEquals(LinkKind.MIX, result.kind());
    assertTrue(result.supported());
  }

  @Test
  public void resolve_rejectsUnsupportedPagesOnKnownHosts() {
    assertFalse(ShareLinkResolver.resolve("https://www.douyin.com/").supported());
    assertFalse(ShareLinkResolver.resolve("https://www.tiktok.com/explore").supported());
    assertFalse(ShareLinkResolver.resolve("https://www.douyin.com/search/cats").supported());
    assertFalse(ShareLinkResolver.resolve("https://www.douyin.com/video/abc").supported());
    assertFalse(ShareLinkResolver.resolve("https://www.douyin.com/?modal_id=abc").supported());
  }

  @Test
  public void resolve_keepsSupportedWorkAndShortLinks() {
    assertEquals(
        LinkKind.WORK,
        ShareLinkResolver.resolve("https://www.douyin.com/video/7345678901234567890").kind());
    assertEquals(
        LinkKind.WORK, ShareLinkResolver.resolve("https://v.douyin.com/AbCd123/").kind());
    assertEquals(
        LinkKind.WORK,
        ShareLinkResolver.resolve("https://www.tiktok.com/@creator/photo/7345678901234567890")
            .kind());
  }

  @Test
  public void resolve_detectsTiktokPlaylistQueryLinks() {
    ShareLinkResolver.Result result =
        ShareLinkResolver.resolve("https://www.tiktok.com/@creator?playlistId=7345678901234567890");

    assertEquals(Platform.TIKTOK, result.platform());
    assertEquals(LinkKind.MIX, result.kind());
    assertTrue(result.supported());
  }

  @Test
  public void resolve_detectsTiktokLowercaseCollectionIdQueryLinks() {
    ShareLinkResolver.Result result =
        ShareLinkResolver.resolve("https://www.tiktok.com/@creator?collectionid=7345678901234567890");

    assertEquals(Platform.TIKTOK, result.platform());
    assertEquals(LinkKind.MIX, result.kind());
    assertTrue(result.supported());
  }
}
