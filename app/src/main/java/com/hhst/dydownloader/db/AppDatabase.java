package com.hhst.dydownloader.db;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
    entities = {ResourceEntity.class, DownloadTaskEntity.class},
    version = 6,
    exportSchema = false)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {
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
  private static volatile AppDatabase instance;

  public static AppDatabase getDatabase(final Context context) {
    if (instance == null) {
      synchronized (AppDatabase.class) {
        if (instance == null) {
          instance =
              Room.databaseBuilder(
                      context.getApplicationContext(), AppDatabase.class, "dydownloader_db")
                  .addMigrations(
                      MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                  .build();
        }
      }
    }
    return instance;
  }

  public abstract ResourceDao resourceDao();

  public abstract DownloadTaskDao downloadTaskDao();
}
