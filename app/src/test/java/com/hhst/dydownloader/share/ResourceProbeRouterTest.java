package com.hhst.dydownloader.share;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hhst.dydownloader.model.Platform;
import com.hhst.dydownloader.share.ShareLinkResolver.LinkKind;
import java.util.List;
import org.junit.Test;

public class ResourceProbeRouterTest {

  @Test
  public void plan_routesTiktokWorkLinksToTiktokPlatform() {
    ResourceProbeRouter.Plan plan =
        ResourceProbeRouter.plan("https://www.tiktok.com/@creator/video/7345678901234567890");

    assertTrue(plan.supported());
    assertEquals(Platform.TIKTOK, plan.platform());
    assertEquals(LinkKind.WORK, plan.kind());
    assertEquals(List.of(LinkKind.ACCOUNT, LinkKind.MIX, LinkKind.WORK), plan.probeKinds());
  }

  @Test
  public void plan_routesDouyinAccountLinksToDouyinPlatform() {
    ResourceProbeRouter.Plan plan =
        ResourceProbeRouter.plan("https://www.douyin.com/user/MS4wLjABAAAA");

    assertTrue(plan.supported());
    assertEquals(Platform.DOUYIN, plan.platform());
    assertEquals(LinkKind.ACCOUNT, plan.kind());
  }

  @Test
  public void plan_marksUnsupportedLinksAsUnsupported() {
    ResourceProbeRouter.Plan plan = ResourceProbeRouter.plan("https://example.com/video/123");

    assertFalse(plan.supported());
    assertEquals(LinkKind.UNKNOWN, plan.kind());
  }
}
