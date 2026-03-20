package com.hhst.dydownloader.manager;

import com.hhst.dydownloader.model.ResourceItem;
import java.util.UUID;

public class DownloadTask {
  private final String taskId;
  private final ResourceItem resourceItem;
  private final long createdAt;
  private Status status;
  private int progress; // 0-100
  private String error;

  public DownloadTask(ResourceItem resourceItem) {
    this(resolveTaskId(resourceItem), resourceItem, System.currentTimeMillis());
  }

  public DownloadTask(ResourceItem resourceItem, long createdAt) {
    this(resolveTaskId(resourceItem), resourceItem, createdAt);
  }

  public DownloadTask(String taskId, ResourceItem resourceItem, long createdAt) {
    this.taskId = taskId;
    this.resourceItem = resourceItem;
    this.createdAt = createdAt;
    this.status = Status.QUEUED;
    this.progress = 0;
  }

  private static String resolveTaskId(ResourceItem resourceItem) {
    String resourceKey = resourceItem != null ? resourceItem.key() : "";
    return resourceKey.isBlank()
        ? UUID.randomUUID().toString()
        : resourceKey;
  }

  public DownloadTask copy() {
    DownloadTask copy = new DownloadTask(taskId, resourceItem, createdAt);
    copy.status = status;
    copy.progress = progress;
    copy.error = error;
    return copy;
  }

  public ResourceItem getResourceItem() {
    return resourceItem;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public int getProgress() {
    return progress;
  }

  public void setProgress(int progress) {
    this.progress = progress;
  }

  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }

  public String getKey() {
    return taskId;
  }

  public String getResourceKey() {
    return resourceItem.key();
  }

  public long getCreatedAt() {
    return createdAt;
  }

  public enum Status {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED
  }
}
