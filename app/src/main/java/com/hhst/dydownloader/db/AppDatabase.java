package com.hhst.dydownloader.db;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
    entities = {ResourceEntity.class, DownloadTaskEntity.class},
    version = 7,
    exportSchema = false)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {
  private static final String DATABASE_NAME = "dydownloader_db";
  private static final String NORMALIZED_RESOURCE_PLATFORM_SQL =
      "CASE WHEN platform = 'TIKTOK' THEN 'TIKTOK' ELSE 'DOUYIN' END";
  private static final String NORMALIZED_TASK_PLATFORM_SQL =
      "CASE "
          + "WHEN platform IN ('DOUYIN', 'TIKTOK') THEN platform "
          + "WHEN taskKey LIKE 'TIKTOK:%' THEN 'TIKTOK' "
          + "ELSE 'DOUYIN' END";
  private static final String CREATE_RESOURCES_V7_SQL =
      "CREATE TABLE IF NOT EXISTS resources_new ("
          + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
          + "parentId INTEGER NOT NULL,"
          + "platform TEXT NOT NULL DEFAULT 'DOUYIN',"
          + "imageResId INTEGER NOT NULL,"
          + "text TEXT,"
          + "type TEXT,"
          + "createTime INTEGER NOT NULL,"
          + "childrenNum INTEGER NOT NULL,"
          + "isLeaf INTEGER NOT NULL,"
          + "downloadPath TEXT,"
          + "thumbnailUrl TEXT,"
          + "sourceKey TEXT,"
          + "progress INTEGER NOT NULL,"
          + "status TEXT"
          + ")";
  private static final String CREATE_DOWNLOAD_TASKS_V7_SQL =
      "CREATE TABLE IF NOT EXISTS download_tasks_new ("
          + "taskKey TEXT NOT NULL PRIMARY KEY,"
          + "platform TEXT NOT NULL DEFAULT 'DOUYIN',"
          + "resourceId INTEGER,"
          + "parentId INTEGER NOT NULL,"
          + "imageResId INTEGER NOT NULL,"
          + "text TEXT,"
          + "type TEXT,"
          + "createTime INTEGER NOT NULL,"
          + "childrenNum INTEGER NOT NULL,"
          + "isLeaf INTEGER NOT NULL,"
          + "thumbnailUrl TEXT,"
          + "sourceKey TEXT,"
          + "downloadUrlsJson TEXT,"
          + "imagePost INTEGER NOT NULL,"
          + "storageDir TEXT,"
          + "status TEXT,"
          + "progress INTEGER NOT NULL,"
          + "error TEXT,"
          + "addedAt INTEGER NOT NULL"
          + ")";
  private static final Migration MIGRATION_1_2 =
      new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
          database.execSQL("ALTER TABLE resources ADD COLUMN thumbnailUrl TEXT");
          database.execSQL("ALTER TABLE resources ADD COLUMN progress INTEGER NOT NULL DEFAULT 0");
          database.execSQL("ALTER TABLE resources ADD COLUMN status TEXT");
        }
      };
  private static final Migration MIGRATION_2_3 =
      new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
          database.execSQL("ALTER TABLE resources ADD COLUMN sourceKey TEXT");
        }
      };
  private static final Migration MIGRATION_3_4 =
      new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
          database.execSQL(
              "CREATE TABLE IF NOT EXISTS download_tasks ("
                  + "taskKey TEXT NOT NULL PRIMARY KEY,"
                  + "resourceId INTEGER,"
                  + "parentId INTEGER NOT NULL,"
                  + "imageResId INTEGER NOT NULL,"
                  + "text TEXT,"
                  + "type TEXT,"
                  + "createTime INTEGER NOT NULL,"
                  + "childrenNum INTEGER NOT NULL,"
                  + "isLeaf INTEGER NOT NULL,"
                  + "thumbnailUrl TEXT,"
                  + "sourceKey TEXT,"
                  + "downloadUrlsJson TEXT,"
                  + "imagePost INTEGER NOT NULL,"
                  + "status TEXT,"
                  + "progress INTEGER NOT NULL,"
                  + "error TEXT,"
                  + "addedAt INTEGER NOT NULL"
                  + ")");
        }
      };
  private static final Migration MIGRATION_4_5 =
      new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
          database.execSQL("ALTER TABLE download_tasks ADD COLUMN storageDir TEXT");
        }
      };
  private static final Migration MIGRATION_5_6 =
      new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
          database.execSQL(
              "ALTER TABLE resources ADD COLUMN platform TEXT NOT NULL DEFAULT 'DOUYIN'");
          database.execSQL(
              "ALTER TABLE download_tasks ADD COLUMN platform TEXT NOT NULL DEFAULT 'DOUYIN'");
        }
      };
  private static final Migration MIGRATION_6_7 =
      new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
          replaceTable(
              database,
              CREATE_RESOURCES_V7_SQL,
              "INSERT INTO resources_new ("
                  + "id, parentId, platform, imageResId, text, type, createTime, childrenNum, "
                  + "isLeaf, downloadPath, thumbnailUrl, sourceKey, progress, status"
                  + ") SELECT "
                  + "id, parentId, "
                  + NORMALIZED_RESOURCE_PLATFORM_SQL
                  + ", imageResId, text, type, createTime, childrenNum, isLeaf, downloadPath, "
                  + "thumbnailUrl, sourceKey, progress, status FROM resources",
              "resources",
              "resources_new");
          replaceTable(
              database,
              CREATE_DOWNLOAD_TASKS_V7_SQL,
              "INSERT INTO download_tasks_new ("
                  + "taskKey, platform, resourceId, parentId, imageResId, text, type, "
                  + "createTime, childrenNum, isLeaf, thumbnailUrl, sourceKey, "
                  + "downloadUrlsJson, imagePost, storageDir, status, progress, error, addedAt"
                  + ") SELECT "
                  + "taskKey, "
                  + NORMALIZED_TASK_PLATFORM_SQL
                  + ", resourceId, parentId, imageResId, text, type, createTime, childrenNum, "
                  + "isLeaf, thumbnailUrl, sourceKey, downloadUrlsJson, imagePost, storageDir, "
                  + "status, progress, error, addedAt FROM download_tasks",
              "download_tasks",
              "download_tasks_new");
        }
      };
  static final Migration[] ALL_MIGRATIONS = {
    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
  };
  private static volatile AppDatabase instance;

  static Migration[] migrations() {
    return ALL_MIGRATIONS.clone();
  }

  private static void replaceTable(
      SupportSQLiteDatabase database,
      String createSql,
      String copySql,
      String oldTableName,
      String newTableName) {
    database.execSQL(createSql);
    database.execSQL(copySql);
    database.execSQL("DROP TABLE " + oldTableName);
    database.execSQL("ALTER TABLE " + newTableName + " RENAME TO " + oldTableName);
  }

  public static AppDatabase getDatabase(final Context context) {
    if (instance == null) {
      synchronized (AppDatabase.class) {
        if (instance == null) {
          instance =
              Room.databaseBuilder(
                      context.getApplicationContext(), AppDatabase.class, DATABASE_NAME)
                  .addMigrations(ALL_MIGRATIONS)
                  .build();
        }
      }
    }
    return instance;
  }

  public abstract ResourceDao resourceDao();

  public abstract DownloadTaskDao downloadTaskDao();
}
