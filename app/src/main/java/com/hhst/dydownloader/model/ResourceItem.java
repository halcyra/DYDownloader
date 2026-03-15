package com.hhst.dydownloader.model;

import java.util.ArrayList;
import java.util.List;

public record ResourceItem(
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

  public ResourceItem {
    thumbnailUrl = thumbnailUrl == null ? "" : thumbnailUrl;
    children = children == null ? null : new ArrayList<>(children);
    sourceKey = sourceKey == null ? "" : sourceKey;
    downloadUrls = downloadUrls == null ? List.of() : new ArrayList<>(downloadUrls);
    downloadPath = downloadPath == null ? "" : downloadPath;
    storageDir = storageDir == null ? "" : storageDir;
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
        id,
        parentId,
        imageResId,
        text,
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
        null,
        0L,
        imageResId,
        text,
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
        null,
        0L,
        imageResId,
        text,
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
      return sourceKey;
    }
    if (id != null && id > 0) {
      return "id:" + id;
    }
    return type + ":" + parentId + ":" + text + ":" + createTime;
  }
}
