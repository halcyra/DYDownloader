package com.hhst.dydownloader.share;

import com.hhst.dydownloader.model.Platform;
import com.hhst.dydownloader.share.ShareLinkResolver.LinkKind;
import java.util.List;
import java.util.Locale;

public final class ResourceProbeRouter {
  private static final List<LinkKind> WORK_FALLBACK_PROBE_KINDS =
      List.of(LinkKind.WORK, LinkKind.ACCOUNT, LinkKind.MIX);

  private ResourceProbeRouter() {}

  public static Plan plan(String text) {
    ShareLinkResolver.Result resolved = ShareLinkResolver.resolve(text);
    if (!resolved.supported()) {
      return Plan.unsupported();
    }
    return new Plan(
        resolved.platform(),
        resolved.kind(),
        resolveProbeKinds(resolved.kind(), resolved.url()),
        resolved.url());
  }

  private static List<LinkKind> resolveProbeKinds(LinkKind kind, String url) {
    if (isAmbiguousShortLink(url)) {
      return WORK_FALLBACK_PROBE_KINDS;
    }
    return List.of(kind);
  }

  private static boolean isAmbiguousShortLink(String url) {
    if (url == null || url.isBlank()) {
      return false;
    }
    String normalized = url.toLowerCase(Locale.ROOT);
    return normalized.contains("://vm.tiktok.com/")
        || normalized.contains("://vt.tiktok.com/")
        || normalized.contains("://v.douyin.com/");
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
      return platform != null
          && kind != LinkKind.UNKNOWN
          && !probeKinds.isEmpty()
          && !url.isBlank();
    }
  }
}
