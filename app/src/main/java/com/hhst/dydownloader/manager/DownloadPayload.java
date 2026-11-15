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

  DownloadPayload {
    urls = Objects.requireNonNull(urls, "urls");
    mediaTypes = mediaTypes == null ? List.of() : List.copyOf(mediaTypes);
    coverUrls = coverUrls == null ? List.of() : List.copyOf(coverUrls);
  }

  MediaType mediaTypeAt(int index) {
    if (index < 0 || index >= mediaTypes.size()) {
      return imagePost ? MediaType.IMAGE : MediaType.VIDEO;
    }
    return mediaTypes.get(index);
  }

  String coverUrlAt(int index) {
    if (index < 0 || index >= coverUrls.size()) {
      return "";
    }
    return coverUrls.get(index).trim();
  }
}
