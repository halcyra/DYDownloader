package com.hhst.dydownloader.manager;

import android.content.Context;
import android.util.Log;
import com.hhst.dydownloader.AppPrefs;
import com.hhst.dydownloader.R;
import com.hhst.dydownloader.db.AppDatabase;
import com.hhst.dydownloader.db.ResourceDao;
import com.hhst.dydownloader.db.ResourceEntity;
import com.hhst.dydownloader.douyin.AwemeProfile;
import com.hhst.dydownloader.douyin.MediaType;
import com.hhst.dydownloader.downloader.HttpDownloader;
import com.hhst.dydownloader.downloader.PlatformRequestContexts;
import com.hhst.dydownloader.model.CardType;
import com.hhst.dydownloader.model.Platform;
import com.hhst.dydownloader.model.ResourceItem;
import com.hhst.dydownloader.util.StoragePathUtils;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.OkHttpClient;

public class DownloadManager {
  private static final String TAG = "DownloadManager";
  private static DownloadManager instance;
  private final Context context;
  private final AppDatabase database;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final AtomicBoolean isProcessing = new AtomicBoolean(false);
  private final OkHttpClient httpClient;
  private final ResourceDao resourceDao;
  private final DownloadPayloadFactory payloadFactory = new DownloadPayloadFactory();
  private final DownloadStorage downloadStorage;

  private DownloadManager(Context context) {
    this.context = context.getApplicationContext();
    this.database = AppDatabase.getDatabase(this.context);
    this.resourceDao = database.resourceDao();
    this.downloadStorage = new DownloadStorage(this.context);
    this.httpClient =
        new OkHttpClient.Builder()
            .followRedirects(true)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build();
  }

  public static synchronized void init(Context context) {
    if (instance == null) {
      instance = new DownloadManager(context);
    }
  }

  public static synchronized DownloadManager getInstance() {
    if (instance == null) {
      throw new IllegalStateException("DownloadManager not initialized");
    }
    return instance;
  }

  public void onQueueUpdated() {
    if (isProcessing.compareAndSet(false, true)) {
      executor.execute(this::processQueue);
    }
  }

  private void processQueue() {
    try {
      while (true) {
        DownloadTask task = DownloadQueue.peekNextQueuedTask();

        if (task == null) {
          break;
        }

        downloadTask(task);
      }
    } finally {
      isProcessing.set(false);
      if (DownloadQueue.peekNextQueuedTask() != null && isProcessing.compareAndSet(false, true)) {
        executor.execute(this::processQueue);
      }
    }
  }

  private void downloadTask(DownloadTask task) {
    task.setStatus(DownloadTask.Status.DOWNLOADING);
    task.setProgress(0);
    task.setError(null);
    DownloadQueue.updateTask(task);

    File tempDownloadDir = downloadStorage.tempDirectory();

    ResourceItem item = task.getResourceItem();
    try {
      DownloadQueue.requireActive(task);
      if (item == null) {
        throw new IOException("Empty task");
      }
      String cookie = AppPrefs.getCookie(context, item.platform());

      DownloadPayload payload = payloadFactory.build(cookie, item);
      DownloadQueue.requireActive(task);
      String relativeDir = resolveDownloadRelativeDir(item);
      String baseName = buildFileBaseName(payload.profile(), item);

      HttpDownloader httpDownloader =
          new HttpDownloader(
              httpClient, PlatformRequestContexts.forPlatform(item.platform(), cookie));

      List<DownloadedAsset> assets =
          payload.imagePost()
              ? downloadImages(
                  httpDownloader, tempDownloadDir, relativeDir, baseName, payload, task)
              : downloadVideo(
                  httpDownloader, tempDownloadDir, relativeDir, baseName, payload, task);

      DownloadQueue.completeTask(
          task, () -> saveWorkToHome(item, payload.profile(), assets, payload.imagePost()));
    } catch (CancellationException ignored) {
      // The queue owns cancellation; removed tasks must not be persisted again.
    } catch (Exception e) {
      Log.e(TAG, "Download failed for " + describeTask(task), e);
      String errorMsg = getCompactErrorMessage(e);
      task.setStatus(DownloadTask.Status.FAILED);
      task.setError(errorMsg);
      DownloadQueue.updateTask(task);
    }
  }

  private String getCompactErrorMessage(Exception e) {
    String message =
        e.getMessage() != null
            ? e.getMessage()
            : context.getString(R.string.download_error_unknown);
    String className = e.getClass().getSimpleName();

    Throwable cause = e.getCause();
    if (cause != null && cause.getMessage() != null) {
      message += " (" + cause.getClass().getSimpleName() + ": " + cause.getMessage() + ")";
    }

    return className + ": " + message;
  }

  private String describeTask(DownloadTask task) {
    if (task == null || task.getResourceItem() == null) {
      return "<unknown>";
    }
    return task.getResourceItem().key();
  }

  private List<DownloadedAsset> downloadImages(
      HttpDownloader httpDownloader,
      File tempDownloadDir,
      String relativeDir,
      String baseName,
      DownloadPayload payload,
      DownloadTask task)
      throws IOException {
    List<String> urls = payload.urls();
    int total = urls.size();
    ArrayList<DownloadedAsset> assets = new ArrayList<>(total);

    for (int i = 0; i < total; i++) {
      DownloadQueue.requireActive(task);
      String url = urls.get(i);
      int index = i;
      MediaType mediaType = payload.mediaTypeAt(i);
      String extension = mediaType == MediaType.VIDEO ? ".mp4" : ".jpg";
      HttpDownloader.ExpectedContent expectedContent =
          mediaType == MediaType.VIDEO
              ? HttpDownloader.ExpectedContent.VIDEO
              : HttpDownloader.ExpectedContent.IMAGE;
      File out =
          new File(
              tempDownloadDir,
              baseName + "_" + String.format(Locale.ROOT, "%02d", i + 1) + extension);
      httpDownloader.download(
          url,
          out,
          (p, downloaded, tot) -> {
            int overall = (int) (((index * 100.0) + p) / total);
            updateProgress(task, overall);
          },
          expectedContent);
      String mediaReference =
          downloadStorage.storeDownloadedFile(
              out,
              relativeDir,
              out.getName(),
              mediaType == MediaType.VIDEO ? "video/mp4" : "image/jpeg");
      String coverReference = "";
      if (mediaType == MediaType.VIDEO) {
        coverReference =
            downloadCoverFile(
                httpDownloader,
                tempDownloadDir,
                relativeDir,
                baseName,
                String.format(Locale.ROOT, "%02d", i + 1),
                payload.coverUrlAt(i),
                task);
      }
      assets.add(new DownloadedAsset(mediaType, mediaReference, coverReference));
    }

    return assets;
  }

  private List<DownloadedAsset> downloadVideo(
      HttpDownloader httpDownloader,
      File tempDownloadDir,
      String relativeDir,
      String baseName,
      DownloadPayload payload,
      DownloadTask task)
      throws IOException {
    List<String> candidates = payload.urls();
    File out = new File(tempDownloadDir, baseName + ".mp4");

    IOException last = null;
    for (String url : candidates) {
      if (url == null || url.isBlank()) {
        continue;
      }
      try {
        httpDownloader.download(
            url,
            out,
            (p, downloaded, tot) -> updateProgress(task, p),
            HttpDownloader.ExpectedContent.VIDEO);
        String mediaReference =
            downloadStorage.storeDownloadedFile(out, relativeDir, out.getName(), "video/mp4");
        String coverReference =
            downloadCoverFile(
                httpDownloader,
                tempDownloadDir,
                relativeDir,
                baseName,
                "01",
                payload.coverUrlAt(0),
                task);
        return List.of(new DownloadedAsset(MediaType.VIDEO, mediaReference, coverReference));
      } catch (IOException e) {
        last = e;
      }
    }
    if (last != null) {
      throw last;
    }
    throw new IOException("All video candidates failed");
  }

  private String downloadCoverFile(
      HttpDownloader httpDownloader,
      File tempDownloadDir,
      String relativeDir,
      String baseName,
      String indexToken,
      String coverUrl,
      DownloadTask task) {
    DownloadQueue.requireActive(task);
    if (coverUrl == null || coverUrl.isBlank()) {
      return "";
    }
    File out = new File(tempDownloadDir, baseName + "_" + indexToken + "_cover.jpg");
    try {
      httpDownloader.download(
          coverUrl,
          out,
          (p, downloaded, total) -> DownloadQueue.requireActive(task),
          HttpDownloader.ExpectedContent.IMAGE);
      return downloadStorage.storeDownloadedFile(out, relativeDir, out.getName(), "image/jpeg");
    } catch (IOException ignored) {
      return "";
    }
  }

  static String resolveAuthorNickname(AwemeProfile profile, ResourceItem item) {
    if (profile != null && profile.authorNickname() != null && !profile.authorNickname().isBlank()) {
      return profile.authorNickname().trim();
    }
    return item == null ? "" : item.authorNickname().trim();
  }

  private void updateProgress(DownloadTask task, int progress) {
    DownloadQueue.requireActive(task);
    int clamped = Math.min(99, Math.max(0, progress));
    if (task.getProgress() != clamped) {
      task.setProgress(clamped);
      DownloadQueue.updateTask(task);
    }
  }

  private String buildFileBaseName(AwemeProfile profile, ResourceItem item) {
    String genericName = context.getString(R.string.generic_media_name);
    String desc = item != null && item.text() != null ? item.text().trim() : "";
    if (desc.isEmpty() && item != null && item.sourceKey() != null) {
      desc = item.sourceKey().trim();
    }
    long createTime =
        profile != null && profile.createTime() > 0
            ? profile.createTime() * 1000L
            : item != null ? item.createTime() : 0L;
    String expanded =
        StoragePathUtils.expandFileNameTemplate(
            AppPrefs.getFileNameTemplate(context),
            resolveAuthorNickname(profile, item),
            desc,
            new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(createTime)),
            SourceKeyUtils.baseOf(item != null ? item.sourceKey() : ""));
    String normalized = StoragePathUtils.sanitizeSegment(expanded, genericName);
    if (normalized.isEmpty()) {
      normalized = genericName;
    }

    // 同名作品依靠来源哈希后缀保持文件名唯一
    if (item != null && item.sourceKey() != null && !item.sourceKey().isBlank()) {
      normalized = normalized + "_" + StoragePathUtils.stableToken(item.sourceKey());
    }

    return normalized;
  }

  private String resolveDownloadRelativeDir(ResourceItem item) {
    if (item == null) {
      return "";
    }
    if (item.storageDir() != null && !item.storageDir().isBlank()) {
      return item.storageDir().trim();
    }
    return "";
  }

  private void saveWorkToHome(
      ResourceItem item, AwemeProfile profile, List<DownloadedAsset> assets, boolean imagePost) {
    if (assets == null || assets.isEmpty()) {
      return;
    }

    database.runInTransaction(
        () -> {
          Platform platform = resolvePlatform(item, profile);
          long now = System.currentTimeMillis();
          boolean isSinglePhotoLeaf = imagePost && SourceKeyUtils.photoIndex(item.sourceKey()) >= 0;
          boolean isSingleLiveLeaf = imagePost && SourceKeyUtils.liveIndex(item.sourceKey()) >= 0;
          boolean isSingleImageLeaf = isSinglePhotoLeaf || isSingleLiveLeaf;
          boolean isVideoCoverLeaf = isVideoCover(item.sourceKey());
          String normalizedSourceKey = SourceKeyUtils.baseOf(item.sourceKey());
          if (normalizedSourceKey.isBlank() && item.sourceKey() != null) {
            normalizedSourceKey = item.sourceKey().trim();
          }
          String title =
              profile != null && profile.desc() != null && !profile.desc().isBlank()
                  ? profile.desc().trim()
                  : deriveRootTitle(item);
          CardType rootType = normalizeRootType(item.type());
          String thumb =
              profile != null && profile.thumbnailUrl() != null && !profile.thumbnailUrl().isBlank()
                  ? profile.thumbnailUrl()
                  : item.thumbnailUrl();
          String authorNickname = resolveAuthorNickname(profile, item);
          int expectedChildren =
              imagePost
                      && profile != null
                      && profile.thumbnailUrls() != null
                      && !profile.thumbnailUrls().isEmpty()
                  ? Math.max(profile.thumbnailUrls().size(), countExpectedChildren(assets))
                  : countExpectedChildren(assets);

          ResourceEntity root = null;
          if (!normalizedSourceKey.isBlank()) {
            root = resourceDao.getBySourceKey(platform, normalizedSourceKey);
          }
          long rootId;
          if (root == null) {
            root =
                new ResourceEntity(
                    platform,
                    0,
                    rootType.getIconResId(),
                    title,
                    rootType,
                    now,
                    expectedChildren,
                    false);
            root.thumbnailUrl = thumb;
            root.sourceKey = normalizedSourceKey;
            root.downloadPath = assets.get(0).mediaReference();
            root.authorNickname = authorNickname;
            rootId = resourceDao.insert(root);
          } else {
            rootId = root.id;
            root.platform = platform;
            root.text = title;
            root.type = rootType;
            root.imageResId = rootType.getIconResId();
            root.childrenNum = Math.max(root.childrenNum, expectedChildren);
            root.createTime = now;
            root.thumbnailUrl = thumb;
            if (!authorNickname.isBlank()) {
              root.authorNickname = authorNickname;
            }
            if (root.downloadPath == null || root.downloadPath.isBlank()) {
              root.downloadPath = assets.get(0).mediaReference();
            }
            resourceDao.update(root);
          }

          if (isSingleImageLeaf) {
            MediaType leafType = isSingleLiveLeaf ? MediaType.VIDEO : MediaType.IMAGE;
            DownloadedAsset primaryAsset = assets.get(0);
            upsertSingleImageChild(
                rootId,
                platform,
                item.sourceKey(),
                thumb,
                primaryAsset.mediaReference(),
                leafType,
                now);
            if (isSingleLiveLeaf && !primaryAsset.coverReference().isBlank()) {
              int liveIndex = Math.max(0, SourceKeyUtils.liveIndex(item.sourceKey()));
              String coverSourceKey =
                  normalizedSourceKey.isBlank()
                      ? ""
                      : normalizedSourceKey + "#photo:" + (liveIndex + 1);
              if (!coverSourceKey.isBlank()) {
                upsertSingleImageChild(
                    rootId,
                    platform,
                    coverSourceKey,
                    thumb,
                    primaryAsset.coverReference(),
                    MediaType.IMAGE,
                    now);
              }
            }
          } else if (isVideoCoverLeaf) {
            upsertVideoCoverChild(
                rootId, platform, normalizedSourceKey, thumb, assets.get(0).mediaReference(), now);
          } else if (imagePost) {
            replaceChildren(rootId, platform, normalizedSourceKey, thumb, assets, now);
          } else {
            DownloadedAsset videoAsset = assets.get(0);
            upsertVideoChild(
                rootId, platform, normalizedSourceKey, thumb, videoAsset.mediaReference(), now);
            if (!videoAsset.coverReference().isBlank()) {
              upsertVideoCoverChild(
                  rootId, platform, normalizedSourceKey, thumb, videoAsset.coverReference(), now);
            }
          }

          ResourceEntity updatedRoot = resourceDao.getById(rootId);
          if (updatedRoot != null) {
            syncRootAfterSave(
                updatedRoot, rootType, title, thumb, assets.get(0).mediaReference(), now);
          }
        });
  }

  private void syncRootAfterSave(
      ResourceEntity root,
      CardType rootType,
      String title,
      String thumb,
      String fallbackPath,
      long now) {
    List<ResourceEntity> children = resourceDao.getByParentId(root.id);
    root.text = title;
    root.type = normalizeRootType(rootType);
    root.imageResId = root.type.getIconResId();
    root.createTime = now;
    if (thumb != null && !thumb.isBlank()) {
      root.thumbnailUrl = thumb;
    }
    root.childrenNum = Math.max(1, children.size());
    String preferredPath = preferredRootPath(children);
    root.downloadPath =
        preferredPath.isBlank() ? (fallbackPath == null ? "" : fallbackPath) : preferredPath;
    resourceDao.update(root);
  }

  private String preferredRootPath(List<ResourceEntity> children) {
    for (ResourceEntity child : children) {
      if (child.type == CardType.VIDEO
          && child.downloadPath != null
          && !child.downloadPath.isBlank()) {
        return child.downloadPath;
      }
    }
    for (ResourceEntity child : children) {
      if (child.downloadPath != null && !child.downloadPath.isBlank()) {
        return child.downloadPath;
      }
    }
    return "";
  }

  private String deriveRootTitle(ResourceItem item) {
    String rawTitle = item != null && item.text() != null ? item.text().trim() : "";
    if (rawTitle.isEmpty()) {
      return context.getString(R.string.generic_work_title);
    }
    String sourceKey = item.sourceKey();
    if (sourceKey != null
        && (sourceKey.endsWith("#cover")
            || sourceKey.contains("#photo:")
            || sourceKey.contains("#live:"))) {
      int separatorIndex = rawTitle.lastIndexOf(" - ");
      if (separatorIndex > 0) {
        String strippedTitle = rawTitle.substring(0, separatorIndex).trim();
        if (!strippedTitle.isEmpty()) {
          return strippedTitle;
        }
      }
    }
    return rawTitle;
  }

  private boolean isVideoCover(String sourceKey) {
    return sourceKey != null && sourceKey.endsWith("#cover");
  }

  private void upsertVideoCoverChild(
      long rootId,
      Platform platform,
      String rootSourceKey,
      String thumb,
      String reference,
      long now) {
    String safeRootSourceKey = rootSourceKey == null ? "" : rootSourceKey.trim();
    String coverSourceKey = safeRootSourceKey.isBlank() ? "" : safeRootSourceKey + "#cover";
    ResourceEntity child =
        coverSourceKey.isBlank()
            ? resourceDao.getByParentId(rootId).stream()
                .filter(existing -> existing.type == CardType.PHOTO)
                .findFirst()
                .orElse(null)
            : resourceDao.getByParentIdAndSourceKey(rootId, platform, coverSourceKey);
    if (child == null) {
      child =
          new ResourceEntity(
              platform,
              rootId,
              CardType.PHOTO.getIconResId(),
              context.getString(R.string.download_child_cover),
              CardType.PHOTO,
              now,
              0,
              true);
      child.sourceKey = coverSourceKey;
    } else {
      child.platform = platform;
      child.createTime = now;
    }
    child.thumbnailUrl = thumb;
    child.downloadPath = reference;
    if (child.id == 0) {
      resourceDao.insert(child);
    } else {
      resourceDao.update(child);
    }
  }

  private void upsertVideoChild(
      long rootId,
      Platform platform,
      String rootSourceKey,
      String thumb,
      String reference,
      long now) {
    String safeRootSourceKey = rootSourceKey == null ? "" : rootSourceKey.trim();
    String videoSourceKey = safeRootSourceKey.isBlank() ? "" : safeRootSourceKey + "#video";
    ResourceEntity child =
        videoSourceKey.isBlank()
            ? resourceDao.getByParentId(rootId).stream()
                .filter(existing -> existing.type == CardType.VIDEO)
                .findFirst()
                .orElse(null)
            : resourceDao.getByParentIdAndSourceKey(rootId, platform, videoSourceKey);
    if (child == null) {
      child =
          new ResourceEntity(
              platform,
              rootId,
              CardType.VIDEO.getIconResId(),
              context.getString(R.string.download_child_video),
              CardType.VIDEO,
              now,
              0,
              true);
      child.sourceKey = videoSourceKey;
    } else {
      child.platform = platform;
      child.createTime = now;
    }
    child.thumbnailUrl = thumb;
    child.downloadPath = reference;
    if (child.id == 0) {
      resourceDao.insert(child);
    } else {
      resourceDao.update(child);
    }
  }

  private void replaceChildren(
      long rootId,
      Platform platform,
      String rootSourceKey,
      String thumb,
      List<DownloadedAsset> assets,
      long now) {
    resourceDao.deleteByParentId(rootId);
    for (int i = 0; i < assets.size(); i++) {
      DownloadedAsset asset = assets.get(i);
      if (asset == null || asset.mediaReference().isBlank()) {
        continue;
      }
      int index = i + 1;
      if (asset.mediaType() == MediaType.VIDEO) {
        if (!asset.coverReference().isBlank()) {
          insertImageChild(
              rootId,
              platform,
              thumb,
              asset.coverReference(),
              context.getString(R.string.download_child_photo, index),
              composeChildSourceKey(rootSourceKey, "#photo:", index),
              now);
        }
        insertVideoChild(
            rootId,
            platform,
            thumb,
            asset.mediaReference(),
            context.getString(R.string.download_child_photo, index),
            composeChildSourceKey(rootSourceKey, "#live:", index),
            now);
        continue;
      }
      insertImageChild(
          rootId,
          platform,
          thumb,
          asset.mediaReference(),
          context.getString(R.string.download_child_photo, index),
          composeChildSourceKey(rootSourceKey, "#photo:", index),
          now);
    }
  }

  private void upsertSingleImageChild(
      long rootId,
      Platform platform,
      String childSourceKey,
      String thumb,
      String reference,
      MediaType mediaType,
      long now) {
    String safeSourceKey = childSourceKey == null ? "" : childSourceKey;
    ResourceEntity child = resourceDao.getByParentIdAndSourceKey(rootId, platform, safeSourceKey);
    int photoNumber = Math.max(1, SourceKeyUtils.imageLeafIndex(childSourceKey) + 1);
    CardType childType = mediaType == MediaType.VIDEO ? CardType.VIDEO : CardType.PHOTO;
    if (child == null) {
      child =
          new ResourceEntity(
              platform,
              rootId,
              childType.getIconResId(),
              context.getString(R.string.download_child_photo, photoNumber),
              childType,
              now,
              0,
              true);
      child.sourceKey = safeSourceKey;
    } else {
      child.platform = platform;
      child.createTime = now;
      child.type = childType;
      child.imageResId = childType.getIconResId();
      child.text = context.getString(R.string.download_child_photo, photoNumber);
    }
    child.thumbnailUrl = thumb;
    child.downloadPath = reference;
    if (child.id == 0) {
      resourceDao.insert(child);
    } else {
      resourceDao.update(child);
    }
  }

  private int countExpectedChildren(List<DownloadedAsset> assets) {
    if (assets == null || assets.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (DownloadedAsset asset : assets) {
      if (asset == null || asset.mediaReference().isBlank()) {
        continue;
      }
      if (asset.mediaType() == MediaType.VIDEO) {
        count += asset.coverReference().isBlank() ? 1 : 2;
      } else {
        count += 1;
      }
    }
    return Math.max(1, count);
  }

  private void insertImageChild(
      long rootId,
      Platform platform,
      String thumb,
      String reference,
      String text,
      String sourceKey,
      long now) {
    if (reference == null || reference.isBlank()) {
      return;
    }
    ResourceEntity child =
        new ResourceEntity(
            platform, rootId, CardType.PHOTO.getIconResId(), text, CardType.PHOTO, now, 0, true);
    child.thumbnailUrl = thumb;
    child.downloadPath = reference;
    child.sourceKey = sourceKey;
    resourceDao.insert(child);
  }

  private void insertVideoChild(
      long rootId,
      Platform platform,
      String thumb,
      String reference,
      String text,
      String sourceKey,
      long now) {
    if (reference == null || reference.isBlank()) {
      return;
    }
    ResourceEntity child =
        new ResourceEntity(
            platform, rootId, CardType.VIDEO.getIconResId(), text, CardType.VIDEO, now, 0, true);
    child.thumbnailUrl = thumb;
    child.downloadPath = reference;
    child.sourceKey = sourceKey;
    resourceDao.insert(child);
  }

  private String composeChildSourceKey(String rootSourceKey, String marker, int number) {
    if (rootSourceKey == null || rootSourceKey.isBlank()) {
      return "";
    }
    return rootSourceKey + marker + number;
  }

  private CardType normalizeRootType(CardType type) {
    return type == CardType.COLLECTION ? CardType.COLLECTION : CardType.ALBUM;
  }

  private Platform resolvePlatform(ResourceItem item, AwemeProfile profile) {
    if (profile != null && profile.platform() != null) {
      return profile.platform();
    }
    if (item != null && item.platform() != null) {
      return item.platform();
    }
    return Platform.DOUYIN;
  }

  public void shutdown() {
    executor.shutdownNow();
    httpClient.dispatcher().executorService().shutdown();
    httpClient.connectionPool().evictAll();
    if (httpClient.cache() != null) {
      try {
        httpClient.cache().close();
      } catch (IOException ignored) {
      }
    }
  }

  private record DownloadedAsset(
      MediaType mediaType, String mediaReference, String coverReference) {}
}
