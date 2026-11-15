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
