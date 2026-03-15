package com.hhst.dydownloader.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface DownloadTaskDao {
  @Query("SELECT * FROM download_tasks ORDER BY addedAt ASC")
  List<DownloadTaskEntity> getAll();

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void upsert(DownloadTaskEntity entity);

  @Query("DELETE FROM download_tasks WHERE taskKey = :taskKey")
  void deleteByKey(String taskKey);

  @Query("DELETE FROM download_tasks")
  void deleteAll();
}
