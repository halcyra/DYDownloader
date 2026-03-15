package com.hhst.dydownloader.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface ResourceDao {
  @Query("SELECT * FROM resources WHERE parentId = :parentId ORDER BY createTime DESC")
  List<ResourceEntity> getByParentId(long parentId);

  @Query("SELECT * FROM resources WHERE parentId = :parentId AND text = :text LIMIT 1")
  ResourceEntity getByParentIdAndText(long parentId, String text);

  @Query("SELECT * FROM resources WHERE id = :id LIMIT 1")
  ResourceEntity getById(long id);

  @Query("SELECT * FROM resources WHERE sourceKey = :sourceKey LIMIT 1")
  ResourceEntity getBySourceKey(String sourceKey);

  @Query("SELECT * FROM resources WHERE parentId = :parentId AND sourceKey = :sourceKey LIMIT 1")
  ResourceEntity getByParentIdAndSourceKey(long parentId, String sourceKey);

  @Query("SELECT * FROM resources ORDER BY createTime DESC")
  List<ResourceEntity> getAll();

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  long insert(ResourceEntity resource);

  @Insert
  void insertAll(List<ResourceEntity> resources);

  @Update
  void update(ResourceEntity resource);

  @Delete
  void delete(ResourceEntity resource);

  @Query("DELETE FROM resources WHERE id = :id")
  void deleteById(long id);

  @Query("DELETE FROM resources WHERE parentId = :parentId")
  void deleteByParentId(long parentId);

  @Query("DELETE FROM resources")
  void deleteAll();
}
