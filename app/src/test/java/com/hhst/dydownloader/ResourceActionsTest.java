package com.hhst.dydownloader;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.hhst.dydownloader.db.ResourceDao;
import com.hhst.dydownloader.db.ResourceEntity;
import com.hhst.dydownloader.model.CardType;
import com.hhst.dydownloader.model.Platform;
import com.hhst.dydownloader.model.ResourceItem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ResourceActionsTest {

  @Test
  public void hasCompleteLocalMedia_requiresEveryLeafForNonLeafItems() throws Exception {
    Path existingChildFile = Files.createTempFile("dy-resource-actions", ".jpg");
    existingChildFile.toFile().deleteOnExit();
    Path missingChildFile =
        existingChildFile.resolveSibling("missing-" + existingChildFile.getFileName());

    ResourceItem firstChild =
        new ResourceItem(
            11L,
            1L,
            CardType.PHOTO.getIconResId(),
            "Photo 01",
            CardType.PHOTO,
            1L,
            0,
            true,
            "",
            null,
            "aweme123#photo:1",
            List.of(),
            true,
            "");
    ResourceItem secondChild =
        new ResourceItem(
            12L,
            1L,
            CardType.VIDEO.getIconResId(),
            "Live 01",
            CardType.VIDEO,
            1L,
            0,
            true,
            "",
            null,
            "aweme123#live:1",
            List.of(),
            false,
            "");
    ResourceItem rootItem =
        new ResourceItem(
            1L,
            0L,
            CardType.ALBUM.getIconResId(),
            "Work",
            CardType.ALBUM,
            1L,
            2,
            false,
            "",
            List.of(firstChild, secondChild),
            "aweme123",
            List.of(),
            true,
            existingChildFile.toString());

    InMemoryResourceDao dao = new InMemoryResourceDao();
    dao.put(entity(11L, 1L, "aweme123#photo:1", true, existingChildFile.toString(), CardType.PHOTO));
    dao.put(entity(12L, 1L, "aweme123#live:1", true, missingChildFile.toString(), CardType.VIDEO));

    assertTrue(ResourceActions.hasCompleteLocalMedia(dao, firstChild));
    assertFalse(ResourceActions.hasCompleteLocalMedia(dao, rootItem));
  }

  @Test
  public void deleteResourceItem_missingLeafEntityDoesNotFallbackToRootDeletion() {
    ResourceEntity root = entity(1L, 0L, "aweme123", false, "", CardType.ALBUM);
    root.childrenNum = 1;

    ResourceItem missingLeaf =
        new ResourceItem(
            null,
            1L,
            CardType.PHOTO.getIconResId(),
            "Photo 01",
            CardType.PHOTO,
            1L,
            0,
            true,
            "",
            null,
            "aweme123#photo:1",
            List.of(),
            true,
            "");

    InMemoryResourceDao dao = new InMemoryResourceDao();
    dao.put(root);

    assertTrue(ResourceActions.deleteResourceItem(dao, missingLeaf, false));
    assertTrue(dao.getById(root.id) != null);
    assertTrue(dao.getBySourceKey(root.sourceKey) != null);
  }

  @Test
  public void resolveLocalMedia_singleVideoWorkPrefersVideoChildOnly() throws Exception {
    Path videoFile = Files.createTempFile("dy-video", ".mp4");
    videoFile.toFile().deleteOnExit();
    Path coverFile = Files.createTempFile("dy-cover", ".jpg");
    coverFile.toFile().deleteOnExit();

    ResourceEntity root = entity(1L, 0L, "aweme123", false, videoFile.toString(), CardType.ALBUM);
    ResourceEntity videoChild =
        entity(11L, 1L, "aweme123#video", true, videoFile.toString(), CardType.VIDEO);
    ResourceEntity coverChild =
        entity(12L, 1L, "aweme123#cover", true, coverFile.toString(), CardType.PHOTO);

    ResourceItem rootItem =
        new ResourceItem(
            1L,
            0L,
            CardType.ALBUM.getIconResId(),
            "Work",
            CardType.ALBUM,
            1L,
            2,
            false,
            "",
            null,
            "aweme123",
            List.of(),
            false,
            videoFile.toString());

    InMemoryResourceDao dao = new InMemoryResourceDao();
    dao.put(root);
    dao.put(videoChild);
    dao.put(coverChild);

    ResourceActions.LocalMedia media = ResourceActions.resolveLocalMedia(dao, rootItem);

    assertTrue(media.canOpenWith());
    assertEquals(List.of(videoFile.toString()), media.references());
  }

  @Test
  public void consolidateTopLevelResources_mergesCoverAndVideoRootsIntoSingleWork() {
    ResourceEntity coverRoot = entity(1L, 0L, "aweme123#cover", false, "cover.jpg", CardType.ALBUM);
    coverRoot.text = "Work";
    coverRoot.thumbnailUrl = "thumb-a";
    coverRoot.childrenNum = 1;

    ResourceEntity videoRoot = entity(2L, 0L, "aweme123#video", false, "video.mp4", CardType.ALBUM);
    videoRoot.text = "Work";
    videoRoot.thumbnailUrl = "thumb-b";
    videoRoot.childrenNum = 1;
    videoRoot.createTime = 2L;

    ResourceEntity coverChild =
        entity(11L, 1L, "aweme123#cover", true, "cover.jpg", CardType.PHOTO);
    ResourceEntity videoChild =
        entity(12L, 2L, "aweme123#video", true, "video.mp4", CardType.VIDEO);

    InMemoryResourceDao dao = new InMemoryResourceDao();
    dao.put(coverRoot);
    dao.put(videoRoot);
    dao.put(coverChild);
    dao.put(videoChild);

    assertTrue(ResourceActions.consolidateTopLevelResources(dao));

    List<ResourceEntity> roots = dao.getByParentId(0);
    assertEquals(1, roots.size());
    ResourceEntity mergedRoot = roots.get(0);
    assertEquals("aweme123", mergedRoot.sourceKey);
    assertEquals(CardType.ALBUM, mergedRoot.type);
    assertEquals("video.mp4", mergedRoot.downloadPath);
    List<ResourceEntity> mergedChildren = dao.getByParentId(mergedRoot.id);
    assertEquals(2, mergedChildren.size());
    assertTrue(
        mergedChildren.stream().anyMatch(child -> "aweme123#cover".equals(child.sourceKey)));
    assertTrue(
        mergedChildren.stream().anyMatch(child -> "aweme123#video".equals(child.sourceKey)));
  }

  private static ResourceEntity entity(
      long id, long parentId, String sourceKey, boolean isLeaf, String downloadPath, CardType type) {
    ResourceEntity entity =
        new ResourceEntity(parentId, type.getIconResId(), sourceKey, type, 1L, 0, isLeaf);
    entity.id = id;
    entity.sourceKey = sourceKey;
    entity.downloadPath = downloadPath;
    entity.thumbnailUrl = "";
    entity.text = sourceKey;
    return entity;
  }

  private static final class InMemoryResourceDao implements ResourceDao {
    private final Map<Long, ResourceEntity> entitiesById = new HashMap<>();
    private final Map<String, ResourceEntity> entitiesBySourceKey = new HashMap<>();

    void put(ResourceEntity entity) {
      entitiesById.put(entity.id, entity);
      if (entity.sourceKey != null) {
        entitiesBySourceKey.put(qualifiedKey(entity.platform, entity.sourceKey), entity);
      }
    }

    @Override
    public List<ResourceEntity> getByParentId(long parentId) {
      return entitiesById.values().stream().filter(item -> item.parentId == parentId).toList();
    }

    @Override
    public ResourceEntity getByParentIdAndText(long parentId, String text) {
      throw unsupported();
    }

    @Override
    public ResourceEntity getById(long id) {
      return entitiesById.get(id);
    }

    @Override
    public ResourceEntity getBySourceKey(Platform platform, String sourceKey) {
      return entitiesBySourceKey.get(qualifiedKey(platform, sourceKey));
    }

    @Override
    public ResourceEntity getByParentIdAndSourceKey(
        long parentId, Platform platform, String sourceKey) {
      return entitiesById.values().stream()
          .filter(
              item ->
                  item.parentId == parentId
                      && item.platform == platform
                      && sourceKey.equals(item.sourceKey))
          .findFirst()
          .orElse(null);
    }

    @Override
    public List<ResourceEntity> getAll() {
      return List.copyOf(entitiesById.values());
    }

    @Override
    public long insert(ResourceEntity resource) {
      throw unsupported();
    }

    @Override
    public void insertAll(List<ResourceEntity> resources) {
      throw unsupported();
    }

    @Override
    public void update(ResourceEntity resource) {
      put(resource);
    }

    @Override
    public void delete(ResourceEntity resource) {
      throw unsupported();
    }

    @Override
    public void deleteById(long id) {
      ResourceEntity removed = entitiesById.remove(id);
      if (removed != null && removed.sourceKey != null) {
        entitiesBySourceKey.remove(qualifiedKey(removed.platform, removed.sourceKey));
      }
    }

    @Override
    public void deleteByParentId(long parentId) {
      entitiesById.values().removeIf(
          item -> {
            boolean shouldRemove = item.parentId == parentId;
            if (shouldRemove && item.sourceKey != null) {
              entitiesBySourceKey.remove(qualifiedKey(item.platform, item.sourceKey));
            }
            return shouldRemove;
          });
    }

    @Override
    public void deleteAll() {
      entitiesById.clear();
      entitiesBySourceKey.clear();
    }

    private UnsupportedOperationException unsupported() {
      return new UnsupportedOperationException("Not needed in this test");
    }

    private static String qualifiedKey(Platform platform, String sourceKey) {
      return (platform == null ? Platform.DOUYIN : platform).name() + ":" + sourceKey;
    }
  }
}
