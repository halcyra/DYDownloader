package com.hhst.dydownloader.share;

import com.hhst.dydownloader.model.Platform;
import com.hhst.dydownloader.share.ShareLinkResolver.LinkKind;
import java.util.List;

public final class ResourceProbeRouter {
  private static final List<LinkKind> DEFAULT_PROBE_KINDS =
      List.of(LinkKind.ACCOUNT, LinkKind.MIX, LinkKind.WORK);

  private ResourceProbeRouter() {}

  public static Plan plan(String text) {
    ShareLinkResolver.Result resolved = ShareLinkResolver.resolve(text);
    if (!resolved.supported()) {
      return Plan.unsupported();
    }
    return new Plan(resolved.platform(), resolved.kind(), DEFAULT_PROBE_KINDS, resolved.url());
  }

  public record Plan(Platform platform, LinkKind kind, List<LinkKind> probeKinds, String url) {
    public Plan {
      probeKinds = probeKinds == null ? List.of() : List.copyOf(probeKinds);
      url = url == null ? "" : url;
    }

    public static Plan unsupported() {
      return new Plan(null, LinkKind.UNKNOWN, List.of(), "");
    }

    public boolean supported() {
      return platform != null && kind != LinkKind.UNKNOWN && !probeKinds.isEmpty() && !url.isBlank();
    }
  }
}
