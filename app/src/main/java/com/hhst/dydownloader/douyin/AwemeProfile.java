package com.hhst.dydownloader.douyin;

import com.hhst.dydownloader.model.Platform;
import java.util.List;

public record AwemeProfile(
    Platform platform,
    String awemeId,
    MediaType mediaType,
    String desc,
    long createTime,
    String authorNickname,
    String authorSecUserId,
    String collectionTitle,
    String thumbnailUrl,
    List<String> thumbnailUrls,
    List<String> downloadUrls,
    List<MediaType> imageMediaTypes) {

  public AwemeProfile {
    platform = platform == null ? Platform.DOUYIN : platform;
    collectionTitle = collectionTitle == null ? "" : collectionTitle;
    thumbnailUrl = thumbnailUrl == null ? "" : thumbnailUrl;
    thumbnailUrls = thumbnailUrls == null ? List.of() : List.copyOf(thumbnailUrls);
    downloadUrls = downloadUrls == null ? List.of() : List.copyOf(downloadUrls);
    imageMediaTypes = imageMediaTypes == null ? List.of() : List.copyOf(imageMediaTypes);
  }

  public MediaType mediaTypeAt(int index) {
    return index < 0 || index >= imageMediaTypes.size() ? MediaType.IMAGE : imageMediaTypes.get(index);
  }
}
