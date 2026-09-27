package com.hhst.dydownloader.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.hhst.dydownloader.model.Platform;
import java.util.List;

@Dao
public interface ResourceDao {
  @Query("SELECT * FROM resources WHERE parentId = :parentId ORDER BY createTime DESC")
  List<ResourceEntity> getByParentId(long parentId);

  @Query("SELECT * FROM resources WHERE id = :id LIMIT 1")
  ResourceEntity getById(long id);

  @Query("SELECT * FROM resources WHERE platform = :platform AND sourceKey = :sourceKey LIMIT 1")
  ResourceEntity getBySourceKey(Platform platform, String sourceKey);

  @Query(
      "SELECT * FROM resources "
          + "WHERE parentId = :parentId AND platform = :platform AND sourceKey = :sourceKey "
          + "LIMIT 1")
  ResourceEntity getByParentIdAndSourceKey(long parentId, Platform platform, String sourceKey);

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  long insert(ResourceEntity resource);

  @Update
  void update(ResourceEntity resource);

  @Query("DELETE FROM resources WHERE id = :id")
  void deleteById(long id);

  @Query("DELETE FROM resources WHERE parentId = :parentId")
  void deleteByParentId(long parentId);
}
