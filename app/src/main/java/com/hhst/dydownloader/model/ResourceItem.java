package com.hhst.dydownloader.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

// Explicit bindings survive Android record desugaring, which removes record reflection metadata.
@JsonAutoDetect(isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResourceItem(
    @JsonProperty("platform") Platform platform,
    @JsonProperty("id") Long id,
    @JsonProperty("parentId") Long parentId,
    @JsonProperty("imageResId") int imageResId,
    @JsonProperty("text") String text,
    @JsonProperty("authorNickname") String authorNickname,
    @JsonProperty("type") CardType type,
    @JsonProperty("createTime") long createTime,
    @JsonProperty("childrenNum") int childrenNum,
    @JsonProperty("isLeaf") boolean isLeaf,
    @JsonProperty("thumbnailUrl") String thumbnailUrl,
    @JsonProperty("children") List<ResourceItem> children,
    @JsonProperty("sourceKey") String sourceKey,
    @JsonProperty("downloadUrls") List<String> downloadUrls,
    @JsonProperty("imagePost") boolean imagePost,
    @JsonProperty("downloadPath") String downloadPath,
    @JsonProperty("storageDir") String storageDir) {

  @JsonCreator
  public ResourceItem {
    platform = platform == null ? Platform.DOUYIN : platform;
    text = text == null ? "" : text;
    authorNickname = authorNickname == null ? "" : authorNickname;
    thumbnailUrl = thumbnailUrl == null ? "" : thumbnailUrl;
    children = children == null ? null : new ArrayList<>(children);
    sourceKey = sourceKey == null ? "" : sourceKey;
    downloadUrls = downloadUrls == null ? List.of() : new ArrayList<>(downloadUrls);
    downloadPath = downloadPath == null ? "" : downloadPath;
    storageDir = storageDir == null ? "" : storageDir;
  }

  public ResourceItem(
      Platform platform,
      Long id,
      Long parentId,
      int imageResId,
      String text,
      CardType type,
      long createTime,
      int childrenNum,
      boolean isLeaf,
      String thumbnailUrl,
      List<ResourceItem> children,
      String sourceKey,
      List<String> downloadUrls,
      boolean imagePost,
      String downloadPath) {
    this(
        platform,
        id,
        parentId,
        imageResId,
        text,
        "",
        type,
        createTime,
        childrenNum,
        isLeaf,
        thumbnailUrl,
        children,
        sourceKey,
        downloadUrls,
        imagePost,
        downloadPath,
        "");
  }

  public ResourceItem(
      Long id,
      Long parentId,
      int imageResId,
      String text,
      CardType type,
      long createTime,
      int childrenNum,
      boolean isLeaf,
      String thumbnailUrl,
      List<ResourceItem> children,
      String sourceKey,
      List<String> downloadUrls,
      boolean imagePost,
      String downloadPath,
      String storageDir) {
    this(
        Platform.DOUYIN,
        id,
        parentId,
        imageResId,
        text,
        "",
        type,
        createTime,
        childrenNum,
        isLeaf,
        thumbnailUrl,
        children,
        sourceKey,
        downloadUrls,
        imagePost,
        downloadPath,
        storageDir);
  }

  public ResourceItem(
      Long id,
      Long parentId,
      int imageResId,
      String text,
      CardType type,
      long createTime,
      int childrenNum,
      boolean isLeaf,
      String thumbnailUrl,
      List<ResourceItem> children,
      String sourceKey,
      List<String> downloadUrls,
      boolean imagePost,
      String downloadPath) {
    this(
        Platform.DOUYIN,
        id,
        parentId,
        imageResId,
        text,
        "",
        type,
        createTime,
        childrenNum,
        isLeaf,
        thumbnailUrl,
        children,
        sourceKey,
        downloadUrls,
        imagePost,
        downloadPath,
        "");
  }

  public ResourceItem(
      int imageResId,
      String text,
      CardType type,
      int childrenNum,
      boolean isLeaf,
      List<ResourceItem> children) {
    this(
        Platform.DOUYIN,
        null,
        0L,
        imageResId,
        text,
        "",
        type,
        System.currentTimeMillis(),
        childrenNum,
        isLeaf,
        null,
        children,
        "",
        List.of(),
        false,
        "",
        "");
  }

  public ResourceItem(
      int imageResId,
      String text,
      CardType type,
      int childrenNum,
      boolean isLeaf,
      String thumbnailUrl,
      List<ResourceItem> children) {
    this(
        Platform.DOUYIN,
        null,
        0L,
        imageResId,
        text,
        "",
        type,
        System.currentTimeMillis(),
        childrenNum,
        isLeaf,
        thumbnailUrl,
        children,
        "",
        List.of(),
        false,
        "",
        "");
  }

  public String key() {
    if (!sourceKey.isBlank()) {
      return platform.name() + ":" + sourceKey;
    }
    if (id != null && id > 0) {
      return platform.name() + ":id:" + id;
    }
    return platform.name() + ":" + type + ":" + parentId + ":" + text + ":" + createTime;
  }
}
