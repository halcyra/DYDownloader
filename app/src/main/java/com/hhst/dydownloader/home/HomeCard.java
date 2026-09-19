package com.hhst.dydownloader.home;

import com.hhst.dydownloader.manager.DownloadTask;
import com.hhst.dydownloader.model.ResourceItem;

/** 资料库条目或下载任务的首页展示状态。 */
public record HomeCard(ResourceItem item, State state, int progress, String error, long queuedAt) {

  public enum State {
    DONE,
    QUEUED,
    DOWNLOADING,
    FAILED
  }

  public static HomeCard done(ResourceItem item) {
    return new HomeCard(item, State.DONE, 100, "", 0L);
  }

  public static HomeCard fromTask(DownloadTask task) {
    State state =
        switch (task.getStatus()) {
          case QUEUED -> State.QUEUED;
          case DOWNLOADING -> State.DOWNLOADING;
          case COMPLETED -> State.DONE;
          case FAILED -> State.FAILED;
        };
    int progress = state == State.DONE ? 100 : Math.max(0, Math.min(99, task.getProgress()));
    return new HomeCard(
        task.getResourceItem(),
        state,
        progress,
        state == State.FAILED ? DownloadErrorMessage.compactError(task.getError()) : "",
        task.getCreatedAt());
  }

  public String key() {
    return item.key();
  }
}
