package com.hhst.dydownloader.manager;

import com.hhst.dydownloader.douyin.AwemeProfile;
import com.hhst.dydownloader.douyin.MediaType;
import java.util.List;
import java.util.Objects;

record DownloadPayload(
    AwemeProfile profile,
    List<String> urls,
    boolean imagePost,
    List<MediaType> mediaTypes,
    List<String> coverUrls) {

  DownloadPayload(
      AwemeProfile profile,
      List<String> urls,
      boolean imagePost,
      List<MediaType> mediaTypes,
      List<String> coverUrls) {
    this.profile = profile;
    this.urls = Objects.requireNonNull(urls, "urls");
    this.imagePost = imagePost;
    this.mediaTypes = mediaTypes == null ? List.of() : List.copyOf(mediaTypes);
    this.coverUrls = coverUrls == null ? List.of() : List.copyOf(coverUrls);
  }

  MediaType mediaTypeAt(int index) {
    if (index < 0 || index >= mediaTypes.size()) {
      return imagePost ? MediaType.IMAGE : MediaType.VIDEO;
    }
    MediaType type = mediaTypes.get(index);
    if (type == null) {
      return imagePost ? MediaType.IMAGE : MediaType.VIDEO;
    }
    return type;
  }

  String coverUrlAt(int index) {
    if (index < 0 || index >= coverUrls.size()) {
      return "";
    }
    String url = coverUrls.get(index);
    return url == null ? "" : url.trim();
  }
}
