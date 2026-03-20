package com.hhst.dydownloader;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.hhst.dydownloader.db.ResourceDao;
import com.hhst.dydownloader.db.ResourceEntity;
import com.hhst.dydownloader.manager.DownloadQueue;
import com.hhst.dydownloader.manager.DownloadTask;
import com.hhst.dydownloader.manager.SourceKeyUtils;
import com.hhst.dydownloader.model.CardType;
import com.hhst.dydownloader.model.Platform;
import com.hhst.dydownloader.model.ResourceItem;
import com.hhst.dydownloader.util.StorageReferenceUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ResourceActions {
  private ResourceActions() {}

  public static LocalMedia resolveLocalMedia(
      @NonNull ResourceDao resourceDao, @Nullable ResourceItem item) {
    ResourceEntity target = resolveTargetEntity(resourceDao, item);
    if (target != null) {
      return resolveLocalMedia(resourceDao, target, item.text());
    }
    if (item != null && StorageReferenceUtils.exists(null, item.downloadPath())) {
      return new LocalMedia(item.text(), null, List.of(item.downloadPath()));
    }
    return LocalMedia.empty(item != null ? item.text() : "");
  }

  public static LocalMedia resolveLocalMedia(
      @NonNull ResourceDao resourceDao, @Nullable DownloadTask task) {
    return resolveLocalMedia(resourceDao, task != null ? task.getResourceItem() : null);
  }

  public static boolean openWith(@NonNull Context context, @NonNull LocalMedia media) {
    if (!media.canOpenWith()) {
      Toast.makeText(
              context,
              media.references().isEmpty()
                  ? R.string.resource_action_no_local_files
                  : R.string.resource_action_open_single_only,
              Toast.LENGTH_SHORT)
          .show();
      return false;
    }
    String reference = media.references().get(0);
    Uri uri = StorageReferenceUtils.toShareableUri(context, reference);
    String mimeType = StorageReferenceUtils.resolveMimeType(context, reference);
    Intent intent =
        new Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    intent.setClipData(ClipData.newRawUri(media.displayName(), uri));
    try {
      context.startActivity(
          Intent.createChooser(intent, context.getString(R.string.action_open_with)));
      return true;
    } catch (ActivityNotFoundException e) {
      Toast.makeText(context, R.string.resource_action_no_app_found, Toast.LENGTH_SHORT).show();
      return false;
    }
  }

  public static boolean share(@NonNull Context context, @NonNull LocalMedia media) {
    if (!media.canShare()) {
      Toast.makeText(context, R.string.resource_action_no_local_files, Toast.LENGTH_SHORT).show();
      return false;
    }

    ArrayList<Uri> uris = new ArrayList<>(media.references().size());
    ClipData clipData = null;
    for (String reference : media.references()) {
      Uri uri = StorageReferenceUtils.toShareableUri(context, reference);
      uris.add(uri);
      if (clipData == null) {
        clipData = ClipData.newRawUri(media.displayName(), uri);
      } else {
        clipData.addItem(new ClipData.Item(uri));
      }
    }

    Intent intent;
    if (uris.size() == 1) {
      intent =
          new Intent(Intent.ACTION_SEND)
              .setType(StorageReferenceUtils.resolveMimeType(context, media.references().get(0)))
              .putExtra(Intent.EXTRA_STREAM, uris.get(0));
    } else {
      intent =
          new Intent(Intent.ACTION_SEND_MULTIPLE)
              .setType(resolveMultiMimeType(context, media.references()))
              .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    intent.setClipData(clipData);

    try {
      context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_share)));
      return true;
    } catch (ActivityNotFoundException e) {
      Toast.makeText(context, R.string.resource_action_no_app_found, Toast.LENGTH_SHORT).show();
      return false;
    }
  }

  public static boolean hasDownloadDirectory(@Nullable ResourceItem item) {
    return item != null;
  }

  public static boolean hasDownloadDirectory(@Nullable DownloadTask task) {
    return task != null && hasDownloadDirectory(task.getResourceItem());
  }

  public static boolean openDownloadDirectory(
      @NonNull Context context, @Nullable DownloadTask task) {
    return openDownloadDirectory(context, task != null ? task.getResourceItem() : null);
  }

  public static boolean openDownloadDirectory(
      @NonNull Context context, @Nullable ResourceItem item) {
    if (item == null) {
      Toast.makeText(context, R.string.resource_action_no_local_files, Toast.LENGTH_SHORT).show();
      return false;
    }
    String relativeDir = resolveDownloadDirectory(item, context);
    try {
      java.io.File directory = StorageReferenceUtils.buildPublicDownloadDirectoryFile(relativeDir);
      if (directory.exists() && directory.isDirectory()) {
        Uri directUri =
            StorageReferenceUtils.buildPublicDownloadDirectoryShareUri(context, relativeDir);
        Intent directIntent =
            new Intent(Intent.ACTION_VIEW)
                .setDataAndType(directUri, DocumentsContract.Document.MIME_TYPE_DIR)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(
            Intent.createChooser(directIntent, context.getString(R.string.action_open_with)));
        return true;
      }
    } catch (ActivityNotFoundException | SecurityException ignored) {
      // Fall through to SAF browsing below when no app can open the directory directly.
    }

    Uri directoryUri = StorageReferenceUtils.buildPublicDownloadDirectoryUri(relativeDir);
    try {
      Intent intent =
          new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
              .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
              .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
              .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
              .addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        intent.putExtra("android.provider.extra.INITIAL_URI", directoryUri);
      }
      context.startActivity(intent);
      return true;
    } catch (ActivityNotFoundException | SecurityException e) {
      try {
        context.startActivity(new Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS));
        return true;
      } catch (ActivityNotFoundException ignored) {
        Toast.makeText(context, R.string.resource_action_no_app_found, Toast.LENGTH_SHORT).show();
        return false;
      }
    }
  }

  public static boolean deleteResourceItem(
      @NonNull ResourceDao resourceDao, @Nullable ResourceItem item, boolean deleteLocalFiles) {
    ResourceEntity target =
        item != null && item.isLeaf()
            ? resolveExactEntity(resourceDao, item)
            : resolveTargetEntity(resourceDao, item);
    if (target != null) {
      return deleteResourceEntity(resourceDao, target, deleteLocalFiles);
    }
    if (deleteLocalFiles
        && item != null
        && item.downloadPath() != null
        && !item.downloadPath().isBlank()) {
      deleteFile(item.downloadPath());
    }
    if (item != null) {
      DownloadQueue.removeTasksForResource(item);
    }
    return item != null;
  }

  public static boolean deleteResourceEntity(
      @NonNull ResourceDao resourceDao, @Nullable ResourceEntity target, boolean deleteLocalFiles) {
    if (target == null) {
      return false;
    }

    List<String> pathsToDelete = collectOwnedPaths(resourceDao, target);
    boolean groupTaskRemoval = !target.isLeaf;
    Set<String> taskKeysToDelete = collectOwnedTaskKeys(resourceDao, target, groupTaskRemoval);

    if (target.isLeaf && target.parentId > 0) {
      ResourceEntity parent = resourceDao.getById(target.parentId);
      String deletedPath = safeText(target.downloadPath);
      resourceDao.deleteById(target.id);
      syncParentAfterLeafDeletion(resourceDao, parent, deletedPath);
    } else {
      if (!target.isLeaf && target.id > 0) {
        resourceDao.deleteByParentId(target.id);
      }
      resourceDao.deleteById(target.id);
    }

    if (deleteLocalFiles) {
      for (String path : pathsToDelete) {
        deleteFile(path);
      }
    }
    if (groupTaskRemoval) {
      DownloadQueue.removeTasksByResourceKeys(taskKeysToDelete);
    } else {
      DownloadQueue.removeTasksByExactResourceKeys(taskKeysToDelete);
    }
    return true;
  }

  @Nullable
  public static ResourceEntity resolveTargetEntity(
      @NonNull ResourceDao resourceDao, @Nullable ResourceItem item) {
    if (item == null) {
      return null;
    }

    if (item.id() != null && item.id() > 0) {
      ResourceEntity byId = resourceDao.getById(item.id());
      if (byId != null) {
        return byId;
      }
    }

    if (item.sourceKey() != null && !item.sourceKey().isBlank()) {
      ResourceEntity bySourceKey = getBySourceKey(resourceDao, item.platform(), item.sourceKey());
      if (bySourceKey != null) {
        return bySourceKey;
      }
      int photoMarkerIndex = item.sourceKey().indexOf("#photo:");
      int liveMarkerIndex = item.sourceKey().indexOf("#live:");
      int markerIndex = -1;
      if (photoMarkerIndex > 0 && liveMarkerIndex > 0) {
        markerIndex = Math.min(photoMarkerIndex, liveMarkerIndex);
      } else if (photoMarkerIndex > 0) {
        markerIndex = photoMarkerIndex;
      } else if (liveMarkerIndex > 0) {
        markerIndex = liveMarkerIndex;
      }
      if (markerIndex > 0) {
        return getBySourceKey(
            resourceDao, item.platform(), item.sourceKey().substring(0, markerIndex));
      }
    }

    return null;
  }

  private static LocalMedia resolveLocalMedia(
      @NonNull ResourceDao resourceDao,
      @NonNull ResourceEntity target,
      @Nullable String displayName) {
    LinkedHashMap<String, String> references = new LinkedHashMap<>();
    if (target.isLeaf) {
      addReference(references, target.downloadPath);
    } else {
      List<ResourceEntity> children = resourceDao.getByParentId(target.id);
      if (children.isEmpty()) {
        addReference(references, target.downloadPath);
      } else {
        ResourceEntity primaryVideoChild = resolvePrimaryVideoChild(target, children);
        if (primaryVideoChild != null) {
          addReference(references, primaryVideoChild.downloadPath);
        } else {
          for (ResourceEntity child : children) {
            addReference(references, child.downloadPath);
          }
        }
      }
    }

    if (references.isEmpty()) {
      addReference(references, target.downloadPath);
    }
    String resolvedDisplayName =
        displayName == null || displayName.isBlank() ? safeText(target.text) : displayName;
    return new LocalMedia(resolvedDisplayName, target, new ArrayList<>(references.values()));
  }

  @Nullable
  private static ResourceEntity resolvePrimaryVideoChild(
      @NonNull ResourceEntity target, @NonNull List<ResourceEntity> children) {
    String rootSourceKey = safeText(target.sourceKey).trim();
    for (ResourceEntity child : children) {
      if (child == null || child.type != CardType.VIDEO) {
        continue;
      }
      String childSourceKey = safeText(child.sourceKey).trim();
      if (!rootSourceKey.isBlank() && childSourceKey.equals(rootSourceKey + "#video")) {
        return child;
      }
    }

    ResourceEntity singleVideoChild = null;
    for (ResourceEntity child : children) {
      if (child == null) {
        continue;
      }
      if (child.type == CardType.VIDEO) {
        if (singleVideoChild != null) {
          return null;
        }
        singleVideoChild = child;
        continue;
      }
      if (child.type != CardType.PHOTO) {
        return null;
      }
      String childSourceKey = safeText(child.sourceKey).trim();
      if (!childSourceKey.isBlank() && !childSourceKey.endsWith("#cover")) {
        return null;
      }
    }
    return singleVideoChild;
  }

  private static List<String> collectOwnedPaths(
      @NonNull ResourceDao resourceDao, @NonNull ResourceEntity target) {
    LinkedHashMap<String, String> paths = new LinkedHashMap<>();
    addPath(paths, target.downloadPath);
    if (!target.isLeaf && target.id > 0) {
      for (ResourceEntity child : resourceDao.getByParentId(target.id)) {
        addPath(paths, child.downloadPath);
      }
    }
    return new ArrayList<>(paths.values());
  }

  private static Set<String> collectOwnedTaskKeys(
      @NonNull ResourceDao resourceDao, @NonNull ResourceEntity target, boolean includeBaseKeys) {
    Set<String> keys = new HashSet<>();
    addTaskKey(keys, target.id, target.sourceKey, includeBaseKeys);
    if (!target.isLeaf && target.id > 0) {
      for (ResourceEntity child : resourceDao.getByParentId(target.id)) {
        addTaskKey(keys, child.id, child.sourceKey, includeBaseKeys);
      }
    }
    return keys;
  }

  public static boolean hasCompleteLocalMedia(
      @NonNull ResourceDao resourceDao, @Nullable ResourceItem item) {
    if (item == null) {
      return false;
    }
    if (item.isLeaf() && hasLocalFile(item.downloadPath())) {
      return true;
    }

    ResourceEntity exactTarget = resolveExactEntity(resourceDao, item);
    if (item.isLeaf()) {
      return exactTarget != null && hasLocalFile(exactTarget.downloadPath);
    }

    if (item.children() != null && !item.children().isEmpty()) {
      for (ResourceItem child : item.children()) {
        if (!hasCompleteLocalMedia(resourceDao, child)) {
          return false;
        }
      }
      return true;
    }

    if (exactTarget == null) {
      return false;
    }

    List<ResourceEntity> children = resourceDao.getByParentId(exactTarget.id);
    if (children.isEmpty()) {
      return hasLocalFile(item.downloadPath()) || hasLocalFile(exactTarget.downloadPath);
    }
    for (ResourceEntity child : children) {
      if (child == null || !hasLocalFile(child.downloadPath)) {
        return false;
      }
    }
    return true;
  }

  public static boolean consolidateTopLevelResources(@NonNull ResourceDao resourceDao) {
    List<ResourceEntity> roots = resourceDao.getByParentId(0);
    if (roots.isEmpty()) {
      return false;
    }

    LinkedHashMap<String, List<ResourceEntity>> groups = new LinkedHashMap<>();
    for (ResourceEntity root : roots) {
      String groupKey = topLevelGroupKey(root);
      if (groupKey.isBlank()) {
        continue;
      }
      groups.computeIfAbsent(groupKey, unused -> new ArrayList<>()).add(root);
    }

    boolean changed = false;
    for (Map.Entry<String, List<ResourceEntity>> entry : groups.entrySet()) {
      String groupKey = entry.getKey();
      List<ResourceEntity> group = entry.getValue();
      if (group == null || group.isEmpty()) {
        continue;
      }

      ResourceEntity primary = selectPrimaryTopLevelResource(group, groupKey);
      if (primary == null) {
        continue;
      }

      boolean groupChanged = false;
      if (!groupKey.equals(safeText(primary.sourceKey).trim())) {
        primary.sourceKey = groupKey;
        changed = true;
        groupChanged = true;
      }

      if (group.size() > 1) {
        for (ResourceEntity duplicate : group) {
          if (duplicate == null || duplicate.id == primary.id) {
            continue;
          }
          mergeTopLevelResourceInto(resourceDao, primary, duplicate);
          resourceDao.deleteById(duplicate.id);
          changed = true;
          groupChanged = true;
        }
      }

      if (syncMergedTopLevelResource(resourceDao, primary, groupKey)) {
        changed = true;
      } else if (groupChanged) {
        resourceDao.update(primary);
      }
    }
    return changed;
  }

  private static void syncParentAfterLeafDeletion(
      @NonNull ResourceDao resourceDao,
      @Nullable ResourceEntity parent,
      @NonNull String deletedPath) {
    if (parent == null) {
      return;
    }

    List<ResourceEntity> children = resourceDao.getByParentId(parent.id);
    if (children.isEmpty()) {
      resourceDao.deleteById(parent.id);
      return;
    }

    parent.childrenNum = children.size();
    if (parent.downloadPath == null
        || parent.downloadPath.isBlank()
        || parent.downloadPath.equals(deletedPath)) {
      parent.downloadPath = firstAvailablePath(children);
    }
    if ((parent.thumbnailUrl == null || parent.thumbnailUrl.isBlank())
        && children.get(0).thumbnailUrl != null
        && !children.get(0).thumbnailUrl.isBlank()) {
      parent.thumbnailUrl = children.get(0).thumbnailUrl;
    }
    resourceDao.update(parent);
  }

  private static String topLevelGroupKey(@Nullable ResourceEntity root) {
    if (root == null || root.parentId != 0) {
      return "";
    }
    String normalizedSourceKey = SourceKeyUtils.normalize(root.sourceKey);
    if (!normalizedSourceKey.isBlank()) {
      return SourceKeyUtils.baseOf(normalizedSourceKey);
    }
    return "";
  }

  @Nullable
  private static ResourceEntity selectPrimaryTopLevelResource(
      @NonNull List<ResourceEntity> group, @NonNull String groupKey) {
    ResourceEntity selected = null;
    for (ResourceEntity candidate : group) {
      if (candidate == null) {
        continue;
      }
      if (selected == null || isBetterTopLevelCandidate(candidate, selected, groupKey)) {
        selected = candidate;
      }
    }
    return selected;
  }

  private static boolean isBetterTopLevelCandidate(
      @NonNull ResourceEntity candidate,
      @NonNull ResourceEntity current,
      @NonNull String groupKey) {
    boolean candidateExact = groupKey.equals(safeText(candidate.sourceKey).trim());
    boolean currentExact = groupKey.equals(safeText(current.sourceKey).trim());
    if (candidateExact != currentExact) {
      return candidateExact;
    }
    if (candidate.childrenNum != current.childrenNum) {
      return candidate.childrenNum > current.childrenNum;
    }
    if (candidate.createTime != current.createTime) {
      return candidate.createTime > current.createTime;
    }
    return candidate.id < current.id;
  }

  private static void mergeTopLevelResourceInto(
      @NonNull ResourceDao resourceDao,
      @NonNull ResourceEntity primary,
      @NonNull ResourceEntity duplicate) {
    mergeTopLevelMetadata(primary, duplicate);
    List<ResourceEntity> duplicateChildren = resourceDao.getByParentId(duplicate.id);
    for (ResourceEntity duplicateChild : duplicateChildren) {
      ResourceEntity existingChild = findEquivalentChild(resourceDao, primary.id, duplicateChild);
      if (existingChild == null) {
        duplicateChild.parentId = primary.id;
        resourceDao.update(duplicateChild);
        continue;
      }
      mergeChildMetadata(existingChild, duplicateChild);
      resourceDao.update(existingChild);
      resourceDao.deleteById(duplicateChild.id);
    }
  }

  private static void mergeTopLevelMetadata(
      @NonNull ResourceEntity primary, @NonNull ResourceEntity duplicate) {
    primary.parentId = 0;
    primary.isLeaf = false;
    primary.type =
        primary.type == CardType.COLLECTION || duplicate.type == CardType.COLLECTION
            ? CardType.COLLECTION
            : CardType.ALBUM;
    primary.imageResId = primary.type.getIconResId();
    if (preferredText(duplicate.text, primary.text).equals(safeText(duplicate.text).trim())) {
      primary.text = duplicate.text;
    }
    if (primary.thumbnailUrl == null
        || primary.thumbnailUrl.isBlank()
        || primary.thumbnailUrl.equals(primary.downloadPath)) {
      if (duplicate.thumbnailUrl != null && !duplicate.thumbnailUrl.isBlank()) {
        primary.thumbnailUrl = duplicate.thumbnailUrl;
      }
    }
    if ((primary.downloadPath == null || primary.downloadPath.isBlank())
        && duplicate.downloadPath != null
        && !duplicate.downloadPath.isBlank()) {
      primary.downloadPath = duplicate.downloadPath;
    }
    primary.childrenNum = Math.max(primary.childrenNum, duplicate.childrenNum);
    primary.createTime = Math.max(primary.createTime, duplicate.createTime);
    if ((primary.sourceKey == null || primary.sourceKey.isBlank())
        && duplicate.sourceKey != null
        && !duplicate.sourceKey.isBlank()) {
      primary.sourceKey = SourceKeyUtils.baseOf(duplicate.sourceKey);
    }
  }

  @Nullable
  private static ResourceEntity findEquivalentChild(
      @NonNull ResourceDao resourceDao, long parentId, @Nullable ResourceEntity child) {
    if (child == null) {
      return null;
    }
    String childSourceKey = safeText(child.sourceKey).trim();
    if (!childSourceKey.isBlank()) {
      ResourceEntity bySourceKey =
          getByParentIdAndSourceKey(resourceDao, parentId, child.platform, childSourceKey);
      if (bySourceKey != null) {
        return bySourceKey;
      }
    }
    for (ResourceEntity existing : resourceDao.getByParentId(parentId)) {
      if (existing == null || existing.id == child.id) {
        continue;
      }
      if (existing.type == child.type
          && safeText(existing.text).trim().equals(safeText(child.text).trim())) {
        return existing;
      }
    }
    return null;
  }

  private static void mergeChildMetadata(
      @NonNull ResourceEntity existing, @NonNull ResourceEntity incoming) {
    existing.isLeaf = true;
    existing.type = incoming.type;
    existing.imageResId = incoming.type.getIconResId();
    existing.createTime = Math.max(existing.createTime, incoming.createTime);
    if ((existing.sourceKey == null || existing.sourceKey.isBlank())
        && incoming.sourceKey != null
        && !incoming.sourceKey.isBlank()) {
      existing.sourceKey = incoming.sourceKey;
    }
    if (preferredText(incoming.text, existing.text).equals(safeText(incoming.text).trim())) {
      existing.text = incoming.text;
    }
    if ((existing.thumbnailUrl == null || existing.thumbnailUrl.isBlank())
        && incoming.thumbnailUrl != null
        && !incoming.thumbnailUrl.isBlank()) {
      existing.thumbnailUrl = incoming.thumbnailUrl;
    }
    if ((existing.downloadPath == null || existing.downloadPath.isBlank())
        && incoming.downloadPath != null
        && !incoming.downloadPath.isBlank()) {
      existing.downloadPath = incoming.downloadPath;
    }
  }

  private static boolean syncMergedTopLevelResource(
      @NonNull ResourceDao resourceDao, @NonNull ResourceEntity root, @NonNull String groupKey) {
    boolean changed = false;
    root.parentId = 0;
    if (root.isLeaf) {
      root.isLeaf = false;
      changed = true;
    }
    CardType normalizedType =
        root.type == CardType.COLLECTION ? CardType.COLLECTION : CardType.ALBUM;
    if (root.type != normalizedType) {
      root.type = normalizedType;
      changed = true;
    }
    if (root.imageResId != normalizedType.getIconResId()) {
      root.imageResId = normalizedType.getIconResId();
      changed = true;
    }
    if (!groupKey.equals(safeText(root.sourceKey).trim())) {
      root.sourceKey = groupKey;
      changed = true;
    }

    List<ResourceEntity> children = resourceDao.getByParentId(root.id);
    int nextChildrenNum = Math.max(1, children.size());
    if (root.childrenNum != nextChildrenNum) {
      root.childrenNum = nextChildrenNum;
      changed = true;
    }

    String preferredPath = preferredTopLevelPath(children);
    if (!preferredPath.isBlank() && !preferredPath.equals(safeText(root.downloadPath))) {
      root.downloadPath = preferredPath;
      changed = true;
    }

    String preferredThumbnail = preferredTopLevelThumbnail(children);
    if ((root.thumbnailUrl == null || root.thumbnailUrl.isBlank())
        && !preferredThumbnail.isBlank()) {
      root.thumbnailUrl = preferredThumbnail;
      changed = true;
    }

    if (root.text == null || root.text.isBlank()) {
      root.text = groupKey;
      changed = true;
    }

    if (changed) {
      resourceDao.update(root);
    }
    return changed;
  }

  private static String preferredTopLevelPath(@NonNull List<ResourceEntity> children) {
    for (ResourceEntity child : children) {
      if (child != null
          && child.type == CardType.VIDEO
          && child.downloadPath != null
          && !child.downloadPath.isBlank()) {
        return child.downloadPath;
      }
    }
    return firstAvailablePath(children);
  }

  private static String preferredTopLevelThumbnail(@NonNull List<ResourceEntity> children) {
    for (ResourceEntity child : children) {
      if (child != null && child.thumbnailUrl != null && !child.thumbnailUrl.isBlank()) {
        return child.thumbnailUrl;
      }
    }
    return "";
  }

  private static String preferredText(@Nullable String first, @Nullable String second) {
    String normalizedFirst = safeText(first).trim();
    String normalizedSecond = safeText(second).trim();
    if (normalizedFirst.isBlank()) {
      return normalizedSecond;
    }
    if (normalizedSecond.isBlank()) {
      return normalizedFirst;
    }
    return normalizedFirst.length() >= normalizedSecond.length()
        ? normalizedFirst
        : normalizedSecond;
  }

  private static String firstAvailablePath(List<ResourceEntity> children) {
    for (ResourceEntity child : children) {
      if (child.downloadPath != null && !child.downloadPath.isBlank()) {
        return child.downloadPath;
      }
    }
    return "";
  }

  private static String resolveDownloadDirectory(
      @NonNull ResourceItem item, @NonNull Context context) {
    if (item.storageDir() != null && !item.storageDir().isBlank()) {
      return item.storageDir();
    }
    if (item.downloadPath() != null && !item.downloadPath().isBlank()) {
      return StorageReferenceUtils.resolvePublicDownloadRelativeDir(context, item.downloadPath());
    }
    return "";
  }

  private static void addReference(
      LinkedHashMap<String, String> references, @Nullable String reference) {
    if (StorageReferenceUtils.exists(null, reference)) {
      references.put(reference, reference);
    }
  }

  private static void addPath(LinkedHashMap<String, String> paths, @Nullable String path) {
    if (path != null && !path.isBlank()) {
      paths.put(path, path);
    }
  }

  private static boolean hasLocalFile(@Nullable String path) {
    return StorageReferenceUtils.exists(null, path);
  }

  private static boolean deleteFile(@Nullable String path) {
    return StorageReferenceUtils.delete(null, path);
  }

  private static String resolveMultiMimeType(Context context, List<String> references) {
    String topLevel = null;
    for (String reference : references) {
      String mimeType = StorageReferenceUtils.resolveMimeType(context, reference);
      int slashIndex = mimeType.indexOf('/');
      if (slashIndex <= 0) {
        return "*/*";
      }
      String currentTopLevel = mimeType.substring(0, slashIndex);
      if (topLevel == null) {
        topLevel = currentTopLevel;
        continue;
      }
      if (!topLevel.equals(currentTopLevel)) {
        return "*/*";
      }
    }
    return topLevel == null ? "*/*" : topLevel + "/*";
  }

  private static String safeText(@Nullable String value) {
    return value == null ? "" : value;
  }

  private static void addTaskKey(
      Set<String> keys, long resourceId, @Nullable String sourceKey, boolean includeBaseKey) {
    if (keys == null) {
      return;
    }
    String normalizedSourceKey = SourceKeyUtils.normalize(sourceKey);
    if (!normalizedSourceKey.isEmpty()) {
      keys.add(normalizedSourceKey);
      if (includeBaseKey) {
        String baseKey = SourceKeyUtils.baseOf(normalizedSourceKey);
        if (!baseKey.isEmpty()) {
          keys.add(baseKey);
        }
      }
    }
    if (resourceId > 0) {
      keys.add("id:" + resourceId);
    }
  }

  @Nullable
  private static ResourceEntity resolveExactEntity(
      @NonNull ResourceDao resourceDao, @Nullable ResourceItem item) {
    if (item == null) {
      return null;
    }
    if (item.id() != null && item.id() > 0) {
      ResourceEntity byId = resourceDao.getById(item.id());
      if (byId != null) {
        return byId;
      }
    }
    if (item.sourceKey() != null && !item.sourceKey().isBlank()) {
      return getBySourceKey(resourceDao, item.platform(), item.sourceKey());
    }
    return null;
  }

  @Nullable
  private static ResourceEntity getBySourceKey(
      @NonNull ResourceDao resourceDao, @Nullable Platform platform, @Nullable String sourceKey) {
    if (sourceKey == null || sourceKey.isBlank()) {
      return null;
    }
    return resourceDao.getBySourceKey(platform == null ? Platform.DOUYIN : platform, sourceKey);
  }

  @Nullable
  private static ResourceEntity getByParentIdAndSourceKey(
      @NonNull ResourceDao resourceDao,
      long parentId,
      @Nullable Platform platform,
      @Nullable String sourceKey) {
    if (sourceKey == null || sourceKey.isBlank()) {
      return null;
    }
    return resourceDao.getByParentIdAndSourceKey(
        parentId, platform == null ? Platform.DOUYIN : platform, sourceKey);
  }

  public record LocalMedia(
      @NonNull String displayName,
      @Nullable ResourceEntity target,
      @NonNull List<String> references) {
    public LocalMedia {
      references = List.copyOf(references);
    }

    public static LocalMedia empty(@Nullable String displayName) {
      return new LocalMedia(displayName == null ? "" : displayName, null, List.of());
    }

    public boolean canOpenWith() {
      return references.size() == 1;
    }

    public boolean canShare() {
      return !references.isEmpty();
    }
  }
}
