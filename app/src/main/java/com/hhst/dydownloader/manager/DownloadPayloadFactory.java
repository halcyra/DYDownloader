package com.hhst.dydownloader.manager;

import com.hhst.dydownloader.douyin.AwemeProfile;
import com.hhst.dydownloader.douyin.DouyinDownloader;
import com.hhst.dydownloader.douyin.MediaType;
import com.hhst.dydownloader.model.Platform;
import com.hhst.dydownloader.model.ResourceItem;
import com.hhst.dydownloader.tiktok.TikTokDownloader;
import com.hhst.dydownloader.util.MediaSourceUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class DownloadPayloadFactory {
  private final ProfileLoader profileLoader;

  DownloadPayloadFactory() {
    this(DownloadPayloadFactory::loadProfile);
  }

  DownloadPayloadFactory(ProfileLoader profileLoader) {
    this.profileLoader = profileLoader;
  }

  DownloadPayload build(String cookie, ResourceItem item) throws Exception {
    List<String> urls = item.downloadUrls();
    boolean imagePost = item.imagePost() || SourceKeyUtils.hasImageLeafMarker(item.sourceKey());
    AwemeProfile profile = null;
    String baseSourceKey = SourceKeyUtils.baseOf(item.sourceKey());
    List<MediaType> mediaTypes = inferMediaTypes(item, urls, imagePost);

    if ((urls == null || urls.isEmpty()) && !baseSourceKey.isBlank()) {
      profile = profileLoader.load(item.platform(), cookie, baseSourceKey);
      urls = profile.downloadUrls();
      imagePost =
          profile.mediaType() == MediaType.IMAGE
              || SourceKeyUtils.hasImageLeafMarker(item.sourceKey());
      mediaTypes = inferMediaTypes(profile, urls, imagePost);

      int imageIndex = SourceKeyUtils.imageLeafIndex(item.sourceKey());
      if (imagePost && imageIndex >= 0 && urls != null && imageIndex < urls.size()) {
        urls = List.of(urls.get(imageIndex));
        mediaTypes = List.of(mediaTypeAt(mediaTypes, imageIndex, true));
      }
    }

    if (urls == null || urls.isEmpty()) {
      throw new IOException("No downloadable URLs");
    }

    mediaTypes = normalizeMediaTypes(mediaTypes, urls.size(), imagePost);
    List<String> coverUrls = resolveCoverUrls(item, profile, urls, imagePost);
    return new DownloadPayload(profile, urls, imagePost, mediaTypes, coverUrls);
  }

  private List<MediaType> inferMediaTypes(ResourceItem item, List<String> urls, boolean imagePost) {
    if (urls == null || urls.isEmpty()) {
      return List.of();
    }
    List<MediaType> types = new ArrayList<>(urls.size());
    int liveLeafIndex = SourceKeyUtils.liveIndex(item != null ? item.sourceKey() : null);
    int photoLeafIndex = SourceKeyUtils.photoIndex(item != null ? item.sourceKey() : null);
    for (String url : urls) {
      if (!imagePost) {
        types.add(MediaType.VIDEO);
      } else if (liveLeafIndex >= 0) {
        types.add(MediaType.VIDEO);
      } else if (photoLeafIndex >= 0) {
        types.add(MediaType.IMAGE);
      } else if (MediaSourceUtils.isLikelyVideoSource(url)) {
        types.add(MediaType.VIDEO);
      } else {
        types.add(MediaType.IMAGE);
      }
    }
    return List.copyOf(types);
  }

  private List<MediaType> inferMediaTypes(
      AwemeProfile profile, List<String> urls, boolean imagePost) {
    if (urls == null || urls.isEmpty()) {
      return List.of();
    }
    List<MediaType> types = new ArrayList<>(urls.size());
    for (int i = 0; i < urls.size(); i++) {
      if (imagePost) {
        types.add(profile != null ? profile.mediaTypeAt(i) : MediaType.IMAGE);
      } else {
        types.add(MediaType.VIDEO);
      }
    }
    return List.copyOf(types);
  }

  private List<MediaType> normalizeMediaTypes(
      List<MediaType> mediaTypes, int urlCount, boolean imagePost) {
    if (urlCount <= 0) {
      return List.of();
    }
    List<MediaType> normalized = new ArrayList<>(urlCount);
    for (int i = 0; i < urlCount; i++) {
      normalized.add(mediaTypeAt(mediaTypes, i, imagePost));
    }
    return List.copyOf(normalized);
  }

  private MediaType mediaTypeAt(List<MediaType> mediaTypes, int index, boolean imagePost) {
    if (mediaTypes == null || index < 0 || index >= mediaTypes.size()) {
      return imagePost ? MediaType.IMAGE : MediaType.VIDEO;
    }
    MediaType type = mediaTypes.get(index);
    if (type == null) {
      return imagePost ? MediaType.IMAGE : MediaType.VIDEO;
    }
    return type;
  }

  private List<String> resolveCoverUrls(
      ResourceItem item, AwemeProfile profile, List<String> urls, boolean imagePost) {
    if (urls == null || urls.isEmpty()) {
      return List.of();
    }

    List<String> covers = new ArrayList<>(urls.size());
    for (int i = 0; i < urls.size(); i++) {
      covers.add("");
    }

    if (imagePost) {
      applyProfileCoverUrls(covers, profile);
      applyChildCoverUrls(covers, item);
      applyLeafCoverUrl(covers, item);
      applyRootFallbackCoverUrl(covers, item);
    } else {
      String cover =
          firstNonBlank(
              profile != null ? profile.thumbnailUrl() : "",
              findVideoCoverFromChildren(item),
              item != null ? item.thumbnailUrl() : "");
      if (!cover.isBlank()) {
        covers.set(0, cover);
      }
    }
    return List.copyOf(covers);
  }

  private void applyProfileCoverUrls(List<String> covers, AwemeProfile profile) {
    if (covers == null || covers.isEmpty() || profile == null || profile.thumbnailUrls() == null) {
      return;
    }
    List<String> thumbnailUrls = profile.thumbnailUrls();
    for (int i = 0; i < covers.size() && i < thumbnailUrls.size(); i++) {
      String candidate = normalize(thumbnailUrls.get(i));
      if (!candidate.isBlank()) {
        covers.set(i, candidate);
      }
    }
  }

  private void applyChildCoverUrls(List<String> covers, ResourceItem item) {
    if (covers == null || covers.isEmpty() || item == null || item.children() == null) {
      return;
    }
    for (ResourceItem child : item.children()) {
      if (child == null) {
        continue;
      }
      int photoIndex = SourceKeyUtils.photoIndex(child.sourceKey());
      if (photoIndex >= 0 && photoIndex < covers.size()) {
        String candidate = firstNonBlank(firstUrl(child.downloadUrls()), child.thumbnailUrl());
        if (!candidate.isBlank()) {
          covers.set(photoIndex, candidate);
        }
        continue;
      }

      int liveIndex = SourceKeyUtils.liveIndex(child.sourceKey());
      if (liveIndex < 0 || liveIndex >= covers.size()) {
        continue;
      }
      if (covers.get(liveIndex) != null && !covers.get(liveIndex).isBlank()) {
        continue;
      }
      String candidate = normalize(child.thumbnailUrl());
      if (!candidate.isBlank()) {
        covers.set(liveIndex, candidate);
      }
    }
  }

  private void applyLeafCoverUrl(List<String> covers, ResourceItem item) {
    if (covers == null || covers.isEmpty() || item == null) {
      return;
    }
    int photoIndex = SourceKeyUtils.photoIndex(item.sourceKey());
    int liveIndex = SourceKeyUtils.liveIndex(item.sourceKey());
    int targetIndex = photoIndex >= 0 ? photoIndex : liveIndex;
    if (targetIndex < 0 || targetIndex >= covers.size()) {
      return;
    }
    String candidate =
        photoIndex >= 0
            ? firstNonBlank(firstUrl(item.downloadUrls()), item.thumbnailUrl())
            : firstNonBlank(item.thumbnailUrl(), firstNonVideoUrl(item.downloadUrls()));
    if (!candidate.isBlank()) {
      covers.set(targetIndex, candidate);
    }
  }

  private void applyRootFallbackCoverUrl(List<String> covers, ResourceItem item) {
    if (covers == null || covers.isEmpty() || item == null) {
      return;
    }
    String fallback = normalize(item.thumbnailUrl());
    if (fallback.isBlank()) {
      return;
    }
    for (int i = 0; i < covers.size(); i++) {
      if (covers.get(i) == null || covers.get(i).isBlank()) {
        covers.set(i, fallback);
      }
    }
  }

  private String findVideoCoverFromChildren(ResourceItem item) {
    if (item == null || item.children() == null) {
      return "";
    }
    for (ResourceItem child : item.children()) {
      if (child == null) {
        continue;
      }
      if (child.sourceKey() != null && child.sourceKey().endsWith("#cover")) {
        String candidate = firstNonBlank(firstUrl(child.downloadUrls()), child.thumbnailUrl());
        if (!candidate.isBlank()) {
          return candidate;
        }
      }
    }
    return "";
  }

  private String firstUrl(List<String> urls) {
    if (urls == null || urls.isEmpty()) {
      return "";
    }
    for (String url : urls) {
      String normalized = normalize(url);
      if (!normalized.isBlank()) {
        return normalized;
      }
    }
    return "";
  }

  private String firstNonVideoUrl(List<String> urls) {
    if (urls == null || urls.isEmpty()) {
      return "";
    }
    for (String url : urls) {
      String normalized = normalize(url);
      if (!normalized.isBlank() && !MediaSourceUtils.isLikelyVideoSource(normalized)) {
        return normalized;
      }
    }
    return "";
  }

  private String firstNonBlank(String... values) {
    if (values == null) {
      return "";
    }
    for (String value : values) {
      String normalized = normalize(value);
      if (!normalized.isBlank()) {
        return normalized;
      }
    }
    return "";
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  private static AwemeProfile loadProfile(Platform platform, String cookie, String sourceKey)
      throws Exception {
    if (platform == Platform.TIKTOK) {
      return new TikTokDownloader(cookie).collectWorkInfo(sourceKey);
    }
    return new DouyinDownloader(cookie).collectWorkInfo(sourceKey);
  }

  interface ProfileLoader {
    AwemeProfile load(Platform platform, String cookie, String sourceKey) throws Exception;
  }
}
