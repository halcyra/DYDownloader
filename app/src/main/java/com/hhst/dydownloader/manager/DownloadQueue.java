package com.hhst.dydownloader.manager;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.hhst.dydownloader.db.AppDatabase;
import com.hhst.dydownloader.db.DownloadTaskDao;
import com.hhst.dydownloader.db.DownloadTaskEntity;
import com.hhst.dydownloader.model.ResourceItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;

public final class DownloadQueue {

  private static final List<DownloadTask> TASKS = new ArrayList<>();
  private static final Set<Listener> LISTENERS = new HashSet<>();
  private static final ExecutorService DB_EXECUTOR =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "dy-queue-db");
            t.setDaemon(true);
            return t;
          });
  private static volatile Executor listenerExecutor = Runnable::run;
  private static boolean initialized;
  private static DownloadTaskDao downloadTaskDao;

  private DownloadQueue() {}

  public static synchronized void init(Context context) {
    listenerExecutor = createMainThreadExecutor();
    AppDatabase database = AppDatabase.getDatabase(context.getApplicationContext());
    downloadTaskDao = database.downloadTaskDao();
    DownloadManager.init(context);
    if (initialized) {
      return;
    }
    initialized = true;
    runRestoreTaskBlocking(DownloadQueue::restorePersistedTasks);
    if (peekNextQueuedTask() != null) {
      DownloadManager.getInstance().onQueueUpdated();
    }
  }

  public static synchronized List<DownloadTask> getTasks() {
    return copyTasks(TASKS);
  }

  static synchronized DownloadTask peekNextQueuedTask() {
    for (DownloadTask task : TASKS) {
      if (task.getStatus() == DownloadTask.Status.QUEUED) {
        return task;
      }
    }
    return null;
  }

  public static synchronized int addAll(List<ResourceItem> items) {
    if (items == null || items.isEmpty()) {
      return 0;
    }
    Map<String, DownloadTask> existingTasks = new LinkedHashMap<>();
    for (DownloadTask task : TASKS) {
      existingTasks.put(task.getResourceKey(), task);
    }
    int added = 0;
    for (ResourceItem item : items) {
      if (item == null) {
        continue;
      }
      String resourceKey = item.key();
      if (resourceKey.isBlank()) {
        continue;
      }
      DownloadTask existing = existingTasks.get(resourceKey);
      if (existing != null) {
        if (existing.getStatus() == DownloadTask.Status.QUEUED
            || existing.getStatus() == DownloadTask.Status.DOWNLOADING) {
          continue;
        }
        TASKS.remove(existing);
        deletePersistedTaskAsync(existing.getKey());
      }
      DownloadTask task = new DownloadTask(item);
      TASKS.add(task);
      persistTaskAsync(task);
      existingTasks.put(resourceKey, task);
      added++;
    }
    if (added > 0) {
      notifyListeners();
      DownloadManager.getInstance().onQueueUpdated();
    }
    return added;
  }

  public static synchronized void updateTask(DownloadTask task) {
    if (!TASKS.contains(task)) return;
    persistTaskAsync(task);
    notifyListeners();
  }

  static synchronized void requireActive(DownloadTask task) {
    if (!TASKS.contains(task)) throw new CancellationException("Download removed");
  }

  static synchronized void completeTask(DownloadTask task, Runnable saveResource) {
    requireActive(task);
    // Removal and final persistence must not interleave and recreate a deleted card.
    saveResource.run();
    task.setStatus(DownloadTask.Status.COMPLETED);
    task.setProgress(100);
    updateTask(task);
  }

  public static synchronized void retryTask(DownloadTask task) {
    if (task == null) {
      return;
    }
    for (DownloadTask existing : TASKS) {
      if (existing.getKey().equals(task.getKey())) {
        if (existing.getStatus() != DownloadTask.Status.FAILED) return;
        existing.setStatus(DownloadTask.Status.QUEUED);
        existing.setProgress(0);
        existing.setError(null);
        persistTaskAsync(existing);
        notifyListeners();
        DownloadManager.getInstance().onQueueUpdated();
        return;
      }
    }
  }

  public static synchronized void retryTaskFor(ResourceItem item) {
    if (item == null) {
      return;
    }
    for (DownloadTask existing : TASKS) {
      if (existing.getResourceKey().equals(item.key())) {
        retryTask(existing);
        return;
      }
    }
  }

  public static synchronized void removeTasksForResource(ResourceItem item) {
    if (item == null) {
      return;
    }
    removeTasksByExactResourceKeys(Set.of(item.key()));
  }

  public static synchronized void removeTasksByResourceKeys(Set<String> resourceKeys) {
    if (resourceKeys == null || resourceKeys.isEmpty()) {
      return;
    }
    Set<String> normalizedTargets = normalizeTaskKeys(resourceKeys);
    if (normalizedTargets.isEmpty()) {
      return;
    }

    removeMatchingTasks(key -> matchesAnyResourceKey(key, normalizedTargets));
  }

  public static synchronized void removeTasksByExactResourceKeys(Set<String> resourceKeys) {
    if (resourceKeys == null || resourceKeys.isEmpty()) {
      return;
    }
    Set<String> normalizedTargets = normalizeExactTaskKeys(resourceKeys);
    if (normalizedTargets.isEmpty()) {
      return;
    }

    removeMatchingTasks(key -> matchesAnyExactResourceKey(key, normalizedTargets));
  }

  private static void removeMatchingTasks(Predicate<String> matches) {
    List<String> removedTaskKeys = new ArrayList<>();
    Iterator<DownloadTask> iterator = TASKS.iterator();
    while (iterator.hasNext()) {
      DownloadTask existing = iterator.next();
      if (!matches.test(existing.getResourceKey())) {
        continue;
      }
      iterator.remove();
      removedTaskKeys.add(existing.getKey());
    }

    for (String taskKey : removedTaskKeys) {
      deletePersistedTaskAsync(taskKey);
    }

    if (!removedTaskKeys.isEmpty()) {
      notifyListeners();
    }
  }

  public static synchronized void addListener(Listener listener) {
    if (listener == null) {
      return;
    }
    LISTENERS.add(listener);
    dispatchQueueChanged(listener, getTasks());
  }

  public static synchronized void removeListener(Listener listener) {
    if (listener == null) {
      return;
    }
    LISTENERS.remove(listener);
  }

  private static void notifyListeners() {
    List<DownloadTask> snapshot = copyTasks(TASKS);
    List<Listener> listeners = new ArrayList<>(LISTENERS);
    for (Listener listener : listeners) {
      dispatchQueueChanged(listener, snapshot);
    }
  }

  private static void dispatchQueueChanged(Listener listener, List<DownloadTask> snapshot) {
    listenerExecutor.execute(() -> listener.onQueueChanged(snapshot));
  }

  private static Executor createMainThreadExecutor() {
    Handler mainHandler = new Handler(Looper.getMainLooper());
    // Always enqueue so a main-thread removal cannot overtake older progress snapshots.
    return mainHandler::post;
  }

  private static void restorePersistedTasks() {
    clearInMemoryQueue();
    if (downloadTaskDao == null) {
      return;
    }

    RestorePlan plan = buildRestorePlan(downloadTaskDao.getAll());
    for (DownloadTask task : plan.tasks()) {
      TASKS.add(task);
      persistTask(task);
    }
    for (String obsoleteTaskKey : plan.obsoleteTaskKeys()) {
      deletePersistedTask(obsoleteTaskKey);
    }
    for (String invalidTaskKey : plan.invalidTaskKeys()) {
      deletePersistedTask(invalidTaskKey);
    }
  }

  private static void persistTask(DownloadTask task) {
    if (downloadTaskDao == null || task == null) {
      return;
    }
    downloadTaskDao.upsert(DownloadTaskEntity.fromTask(task));
  }

  private static void persistTaskAsync(DownloadTask task) {
    if (task == null) {
      return;
    }
    DownloadTask snapshot = task.copy();
    DB_EXECUTOR.execute(() -> persistTask(snapshot));
  }

  private static void deletePersistedTask(String taskKey) {
    if (downloadTaskDao == null || taskKey == null || taskKey.isBlank()) {
      return;
    }
    downloadTaskDao.deleteByKey(taskKey);
  }

  private static void deletePersistedTaskAsync(String taskKey) {
    if (taskKey == null || taskKey.isBlank()) {
      return;
    }
    DB_EXECUTOR.execute(() -> deletePersistedTask(taskKey));
  }

  private static void clearInMemoryQueue() {
    TASKS.clear();
  }

  static boolean runRestoreTaskBlocking(Runnable restoreTask) {
    if (restoreTask == null) {
      return true;
    }
    Future<?> future = DB_EXECUTOR.submit(restoreTask);
    try {
      future.get();
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return clearFailedRestore();
    } catch (Exception ignored) {
      return clearFailedRestore();
    }
  }

  static RestorePlan buildRestorePlan(List<DownloadTaskEntity> entities) {
    Map<String, DownloadTask> restoredTasks = new LinkedHashMap<>();
    List<String> obsoleteTaskKeys = new ArrayList<>();
    List<String> invalidTaskKeys = new ArrayList<>();
    if (entities == null || entities.isEmpty()) {
      return new RestorePlan(List.of(), List.of(), List.of());
    }
    for (DownloadTaskEntity entity : entities) {
      DownloadTask task = restoreTaskSafely(entity, invalidTaskKeys);
      if (task == null) {
        continue;
      }
      DownloadTask previousTask = restoredTasks.put(task.getResourceKey(), task);
      if (previousTask != null && !previousTask.getKey().equals(task.getKey())) {
        obsoleteTaskKeys.add(previousTask.getKey());
      }
    }
    return new RestorePlan(
        new ArrayList<>(restoredTasks.values()), obsoleteTaskKeys, invalidTaskKeys);
  }

  private static DownloadTask restoreTaskSafely(
      DownloadTaskEntity entity, List<String> invalidTaskKeys) {
    if (entity == null) {
      return null;
    }
    try {
      DownloadTask task = entity.toTask();
      return resetInterruptedTask(task);
    } catch (Exception ignored) {
      if (!entity.taskKey.isBlank()) {
        invalidTaskKeys.add(entity.taskKey);
      }
      return null;
    }
  }

  private static boolean clearFailedRestore() {
    clearInMemoryQueue();
    return false;
  }

  private static DownloadTask resetInterruptedTask(DownloadTask task) {
    if (task.getStatus() == DownloadTask.Status.DOWNLOADING) {
      task.setStatus(DownloadTask.Status.QUEUED);
      task.setProgress(0);
      task.setError(null);
    }
    return task;
  }

  private static List<DownloadTask> copyTasks(List<DownloadTask> tasks) {
    List<DownloadTask> snapshot = new ArrayList<>(tasks.size());
    for (DownloadTask task : tasks) {
      snapshot.add(task.copy());
    }
    return snapshot;
  }

  static boolean matchesAnyResourceKey(String taskResourceKey, Set<String> normalizedTargets) {
    String normalizedTaskKey = SourceKeyUtils.normalize(taskResourceKey);
    if (normalizedTaskKey.isEmpty() || normalizedTargets == null || normalizedTargets.isEmpty()) {
      return false;
    }
    for (String targetKey : normalizedTargets) {
      if (SourceKeyUtils.isSameResource(normalizedTaskKey, targetKey)) {
        return true;
      }
    }
    return false;
  }

  static boolean matchesAnyExactResourceKey(String taskResourceKey, Set<String> normalizedTargets) {
    String normalizedTaskKey = SourceKeyUtils.normalize(taskResourceKey);
    if (normalizedTaskKey.isEmpty() || normalizedTargets == null || normalizedTargets.isEmpty()) {
      return false;
    }
    return normalizedTargets.contains(normalizedTaskKey);
  }

  private static Set<String> normalizeTaskKeys(Set<String> resourceKeys) {
    Set<String> normalizedKeys = new HashSet<>();
    for (String key : resourceKeys) {
      addTaskKey(normalizedKeys, key);
    }
    return normalizedKeys;
  }

  private static Set<String> normalizeExactTaskKeys(Set<String> resourceKeys) {
    Set<String> normalizedKeys = new HashSet<>();
    for (String key : resourceKeys) {
      String normalized = SourceKeyUtils.normalize(key);
      if (!normalized.isEmpty()) {
        normalizedKeys.add(normalized);
      }
    }
    return normalizedKeys;
  }

  private static void addTaskKey(Set<String> targetKeys, String key) {
    String normalized = SourceKeyUtils.normalize(key);
    if (!normalized.isEmpty()) {
      targetKeys.add(normalized);
      String base = SourceKeyUtils.baseOf(normalized);
      if (!base.isEmpty()) {
        targetKeys.add(base);
      }
    }
  }

  public interface Listener {
    void onQueueChanged(List<DownloadTask> tasks);
  }

  record RestorePlan(
      List<DownloadTask> tasks, List<String> obsoleteTaskKeys, List<String> invalidTaskKeys) {}
}
