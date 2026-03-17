package com.hhst.dydownloader.share;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hhst.dydownloader.model.Platform;
import com.hhst.dydownloader.share.ShareLinkResolver.LinkKind;
import org.junit.Test;

public class ShareLinkResolverTest {

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
}
