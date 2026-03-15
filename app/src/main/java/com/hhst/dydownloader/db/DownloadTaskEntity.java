package com.hhst.dydownloader.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhst.dydownloader.manager.DownloadTask;
import com.hhst.dydownloader.model.ResourceItem;
import java.util.List;

@Entity(tableName = "download_tasks")
public class DownloadTaskEntity {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

  @PrimaryKey @NonNull public String taskKey = "";

  public Long resourceId;
  public long parentId;
  public int imageResId;
  public String text;
  public com.hhst.dydownloader.model.CardType type;
  public long createTime;
  public int childrenNum;
  public boolean isLeaf;
  public String thumbnailUrl;
  public String sourceKey;
  public String downloadUrlsJson;
  public boolean imagePost;
  public String storageDir;
  public String status;
  public int progress;
  public String error;
  public long addedAt;

  public static DownloadTaskEntity fromTask(DownloadTask task) {
    ResourceItem item = task.getResourceItem();
    DownloadTaskEntity entity = new DownloadTaskEntity();
    entity.taskKey = task.getKey();
    entity.resourceId = item.id();
    entity.parentId = item.parentId() == null ? 0L : item.parentId();
    entity.imageResId = item.imageResId();
    entity.text = item.text();
    entity.type = item.type();
    entity.createTime = item.createTime();
    entity.childrenNum = item.childrenNum();
    entity.isLeaf = item.isLeaf();
    entity.thumbnailUrl = item.thumbnailUrl();
    entity.sourceKey = item.sourceKey();
    entity.downloadUrlsJson = serializeUrls(item.downloadUrls());
    entity.imagePost = item.imagePost();
    entity.storageDir = item.storageDir();
    entity.status = task.getStatus().name();
    entity.progress = task.getProgress();
    entity.error = task.getError();
    entity.addedAt = task.getCreatedAt();
    return entity;
  }

  private static String serializeUrls(List<String> urls) {
    try {
      return OBJECT_MAPPER.writeValueAsString(urls == null ? List.of() : urls);
    } catch (Exception ignored) {
      return "[]";
    }
  }

  private static List<String> deserializeUrls(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return OBJECT_MAPPER.readValue(json, STRING_LIST_TYPE);
    } catch (Exception ignored) {
      return List.of();
    }
  }

  public DownloadTask toTask() {
    ResourceItem item =
        new ResourceItem(
            resourceId,
            parentId,
            imageResId,
            text,
            type,
            createTime,
            childrenNum,
            isLeaf,
            thumbnailUrl,
            null,
            sourceKey,
            deserializeUrls(downloadUrlsJson),
            imagePost,
            "",
            storageDir);
    DownloadTask task = new DownloadTask(taskKey, item, addedAt);
    try {
      task.setStatus(DownloadTask.Status.valueOf(status));
    } catch (Exception ignored) {
      task.setStatus(DownloadTask.Status.QUEUED);
    }
    task.setProgress(progress);
    task.setError(error);
    return task;
  }
}
