package com.hhst.dydownloader.db;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import com.hhst.dydownloader.model.CardType;
import com.hhst.dydownloader.model.ResourceItem;

@Entity(tableName = "resources")
public class ResourceEntity {
  @PrimaryKey(autoGenerate = true)
  public long id;

  public long parentId; // 0 for root

  public int imageResId;
  public String text;
  public CardType type;
  public long createTime;
  public int childrenNum;
  public boolean isLeaf;
  public String downloadPath; // null if not downloaded
  public String thumbnailUrl; // thumbnail URL for async loading
  public String sourceKey;
  public int progress;
  public String status;

  public ResourceEntity() {}

  @Ignore
  public ResourceEntity(
      long parentId,
      int imageResId,
      String text,
      CardType type,
      long createTime,
      int childrenNum,
      boolean isLeaf) {
    this.parentId = parentId;
    this.imageResId = imageResId;
    this.text = text;
    this.type = type;
    this.createTime = createTime;
    this.childrenNum = childrenNum;
    this.isLeaf = isLeaf;
    this.progress = 0;
    this.status = "pending";
  }

  @Ignore
  public static ResourceEntity fromResourceItem(long parentId, ResourceItem item) {
    ResourceEntity entity =
        new ResourceEntity(
            parentId,
            item.imageResId(),
            item.text(),
            item.type(),
            item.createTime(),
            item.childrenNum(),
            item.isLeaf());
    entity.id = item.id() != null ? item.id() : 0;
    entity.thumbnailUrl = item.thumbnailUrl();
    entity.sourceKey = item.sourceKey();
    entity.downloadPath = item.downloadPath();
    return entity;
  }

  public ResourceItem toResourceItem() {
    return new ResourceItem(
        id,
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
        java.util.List.of(),
        false,
        downloadPath); // Children should be loaded separately by parentId if needed
  }
}
