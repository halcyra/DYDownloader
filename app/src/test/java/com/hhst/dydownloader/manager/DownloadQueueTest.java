package com.hhst.dydownloader.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.hhst.dydownloader.db.DownloadTaskEntity;
import com.hhst.dydownloader.model.CardType;
import com.hhst.dydownloader.model.Platform;
import com.hhst.dydownloader.model.ResourceItem;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class DownloadQueueTest {

  @Test
  public void collectKeysByStatuses_excludesFailedTasksFromQueuedKeys() {
    DownloadTask queued = task(Platform.DOUYIN, "aweme-a#photo:1", DownloadTask.Status.QUEUED);
    DownloadTask downloading =
        task(Platform.DOUYIN, "aweme-b#video", DownloadTask.Status.DOWNLOADING);
    DownloadTask failed = task(Platform.DOUYIN, "aweme-c#photo:1", DownloadTask.Status.FAILED);
    DownloadTask completed =
        task(Platform.DOUYIN, "aweme-d#video", DownloadTask.Status.COMPLETED);

    Set<String> keys =
        DownloadQueue.collectKeysByStatuses(
            List.of(queued, downloading, failed, completed),
            EnumSet.of(DownloadTask.Status.QUEUED, DownloadTask.Status.DOWNLOADING));

    assertEquals(Set.of("DOUYIN:aweme-a#photo:1", "DOUYIN:aweme-b#video"), keys);
  }

  @Test
  public void matchesAnyResourceKey_matchesByBaseSourceKey() {
    Set<String> targets = Set.of("TIKTOK:aweme-1234567890");
    assertTrue(
        DownloadQueue.matchesAnyResourceKey("TIKTOK:aweme-1234567890#photo:2", targets));
    assertFalse(
        DownloadQueue.matchesAnyResourceKey("DOUYIN:aweme-1234567890#photo:2", targets));
  }

  @Test
  public void matchesAnyExactResourceKey_requiresExactLeafMatch() {
    Set<String> targets = Set.of("TIKTOK:aweme-1234567890#photo:1");

    assertTrue(
        DownloadQueue.matchesAnyExactResourceKey("TIKTOK:aweme-1234567890#photo:1", targets));
    assertFalse(
        DownloadQueue.matchesAnyExactResourceKey("TIKTOK:aweme-1234567890#photo:2", targets));
    assertFalse(
        DownloadQueue.matchesAnyExactResourceKey("DOUYIN:aweme-1234567890#photo:1", targets));
  }

  @Test
  public void downloadTaskEntityRoundTrip_preservesPlatformAwareResourceKey() {
    DownloadTask original = task(Platform.TIKTOK, "aweme-1234567890#video", DownloadTask.Status.QUEUED);

    DownloadTask restored = DownloadTaskEntity.fromTask(original).toTask();

    assertEquals("TIKTOK:aweme-1234567890#video", restored.getResourceKey());
    assertEquals(Platform.TIKTOK, restored.getResourceItem().platform());
  }

  @Test
  public void runRestoreTaskBlocking_returnsFalseWhenRestoreFails() {
    boolean restored =
        DownloadQueue.runRestoreTaskBlocking(
            () -> {
              throw new IllegalArgumentException("bad persisted row");
            });

    assertFalse(restored);
  }

  @Test
  public void runRestoreTaskBlocking_returnsTrueWhenRestoreSucceeds() {
    boolean restored = DownloadQueue.runRestoreTaskBlocking(() -> {});
    assertTrue(restored);
  }

  @Test
  public void buildRestorePlan_restoresRowsWhenPlatformFallsBackToDefault() {
    DownloadTaskEntity valid =
        DownloadTaskEntity.fromTask(task(Platform.TIKTOK, "aweme-1#video", DownloadTask.Status.QUEUED));
    DownloadTaskEntity fallback =
        DownloadTaskEntity.fromTask(task(Platform.DOUYIN, "aweme-2#video", DownloadTask.Status.QUEUED));
    fallback.platform = null;
    fallback.taskKey = "broken-task-key";

    DownloadQueue.RestorePlan plan = DownloadQueue.buildRestorePlan(List.of(valid, fallback));

    assertEquals(2, plan.tasks().size());
    assertEquals("TIKTOK:aweme-1#video", plan.tasks().get(0).getResourceKey());
    assertEquals("DOUYIN:aweme-2#video", plan.tasks().get(1).getResourceKey());
    assertTrue(plan.invalidTaskKeys().isEmpty());
  }

  @Test
  public void addListener_dispatchesInitialSnapshotThroughListenerExecutor() throws Exception {
    RecordingExecutor recordingExecutor = new RecordingExecutor();
    Executor originalExecutor = swapListenerExecutor(recordingExecutor);
    AtomicInteger callbackCount = new AtomicInteger();
    DownloadQueue.Listener listener = tasks -> callbackCount.incrementAndGet();

    try {
      clearListeners();

      DownloadQueue.addListener(listener);

      assertEquals(0, callbackCount.get());
      assertEquals(1, recordingExecutor.pendingCount());

      recordingExecutor.runAll();

      assertEquals(1, callbackCount.get());
    } finally {
      DownloadQueue.removeListener(listener);
      clearListeners();
      setListenerExecutor(originalExecutor);
    }
  }

  @Test
  public void removeTasksByExactResourceKeys_dispatchesCallbacksThroughListenerExecutor()
      throws Exception {
    RecordingExecutor recordingExecutor = new RecordingExecutor();
    Executor originalExecutor = swapListenerExecutor(recordingExecutor);
    DownloadTask existing =
        task(Platform.DOUYIN, "aweme-delete#photo:1", DownloadTask.Status.QUEUED);
    AtomicInteger callbackCount = new AtomicInteger();
    DownloadQueue.Listener listener = tasks -> callbackCount.incrementAndGet();

    try {
      clearTasks();
      clearListeners();
      currentTasks().add(existing);
      DownloadQueue.addListener(listener);
      recordingExecutor.runAll();
      callbackCount.set(0);

      DownloadQueue.removeTasksByExactResourceKeys(Set.of(existing.getResourceKey()));

      assertEquals(0, callbackCount.get());
      assertEquals(1, recordingExecutor.pendingCount());

      recordingExecutor.runAll();

      assertEquals(1, callbackCount.get());
    } finally {
      DownloadQueue.removeListener(listener);
      clearTasks();
      clearListeners();
      setListenerExecutor(originalExecutor);
    }
  }

  private DownloadTask task(
      Platform platform, String sourceKey, DownloadTask.Status status) {
    ResourceItem item =
        new ResourceItem(
            platform,
            null,
            0L,
            0,
            "item",
            CardType.ALBUM,
            1L,
            0,
            true,
            "",
            null,
            sourceKey,
            List.of(),
            false,
            "");
    DownloadTask task = new DownloadTask(item.key(), item, 1L);
    task.setStatus(status);
    return task;
  }

  @SuppressWarnings("unchecked")
  private List<DownloadTask> currentTasks() throws Exception {
    Field field = DownloadQueue.class.getDeclaredField("TASKS");
    field.setAccessible(true);
    return (List<DownloadTask>) field.get(null);
  }

  @SuppressWarnings("unchecked")
  private Set<DownloadQueue.Listener> currentListeners() throws Exception {
    Field field = DownloadQueue.class.getDeclaredField("LISTENERS");
    field.setAccessible(true);
    return (Set<DownloadQueue.Listener>) field.get(null);
  }

  private void clearTasks() throws Exception {
    currentTasks().clear();
  }

  private void clearListeners() throws Exception {
    currentListeners().clear();
  }

  private Executor swapListenerExecutor(Executor replacement) throws Exception {
    Field field = listenerExecutorField();
    Executor original = (Executor) field.get(null);
    field.set(null, replacement);
    return original;
  }

  private void setListenerExecutor(Executor executor) throws Exception {
    listenerExecutorField().set(null, executor);
  }

  private Field listenerExecutorField() {
    try {
      Field field = DownloadQueue.class.getDeclaredField("listenerExecutor");
      field.setAccessible(true);
      return field;
    } catch (NoSuchFieldException e) {
      fail("DownloadQueue should route listener callbacks through listenerExecutor");
      throw new AssertionError(e);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private static final class RecordingExecutor implements Executor {
    private final Queue<Runnable> tasks = new ArrayDeque<>();

    @Override
    public void execute(Runnable command) {
      tasks.add(command);
    }

    int pendingCount() {
      return tasks.size();
    }

    void runAll() {
      Runnable next;
      while ((next = tasks.poll()) != null) {
        next.run();
      }
    }
  }
}
