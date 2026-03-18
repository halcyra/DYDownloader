package com.hhst.dydownloader.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppDatabaseMigrationTest {
  private static final String DATABASE_NAME = "app-database-migration-test.db";
  private static final String CREATE_RESOURCES_SQL_PREFIX =
      "CREATE TABLE IF NOT EXISTS resources ("
          + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
          + "parentId INTEGER NOT NULL,"
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
          + "status TEXT,";
  private static final String CREATE_DOWNLOAD_TASKS_SQL_PREFIX =
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
          + "addedAt INTEGER NOT NULL,"
          + "storageDir TEXT,";

  private final Context appContext =
      InstrumentationRegistry.getInstrumentation().getTargetContext();

  @After
  public void tearDown() {
    appContext.deleteDatabase(DATABASE_NAME);
  }

  @Test
  public void openFreshInstalledVersion6Database_succeeds() {
    createFreshInstalledVersion6Database();

    openMigratedDatabase();
  }

  @Test
  public void openMigratedVersion6Database_succeeds() {
    createMigratedVersion6Database();

    openMigratedDatabase();
  }

  private void openMigratedDatabase() {
    AppDatabase database =
        Room.databaseBuilder(appContext, AppDatabase.class, DATABASE_NAME)
            .addMigrations(AppDatabase.migrations())
            .build();
    try {
      database.getOpenHelper().getWritableDatabase();
    } finally {
      database.close();
    }
  }

  private void createFreshInstalledVersion6Database() {
    createVersion6Database(
        "platform TEXT",
        "platform TEXT NOT NULL",
        "NULL",
        "'TIKTOK:aweme-1#video'",
        "'aweme-1#video'",
        "'TIKTOK'");
  }

  private void createMigratedVersion6Database() {
    createVersion6Database(
        "platform TEXT NOT NULL DEFAULT 'DOUYIN'",
        "platform TEXT NOT NULL DEFAULT 'DOUYIN'",
        "'DOUYIN'",
        "'DOUYIN:aweme-2#video'",
        "'aweme-2#video'",
        "'DOUYIN'");
  }

  private void createVersion6Database(
      String resourcesPlatformColumn,
      String tasksPlatformColumn,
      String resourcePlatformValue,
      String taskKey,
      String sourceKey,
      String taskPlatformValue) {
    SQLiteDatabase database = openVersion6Database();
    try {
      database.execSQL(CREATE_RESOURCES_SQL_PREFIX + resourcesPlatformColumn + ")");
      database.execSQL(CREATE_DOWNLOAD_TASKS_SQL_PREFIX + tasksPlatformColumn + ")");
      database.execSQL(
          "INSERT INTO resources ("
              + "parentId, imageResId, text, type, createTime, childrenNum, isLeaf, "
              + "downloadPath, thumbnailUrl, sourceKey, progress, status, platform"
              + ") VALUES (0, 0, 'item', 'ALBUM', 1, 0, 1, '', '', "
              + sourceKey
              + ", 0, 'pending', "
              + resourcePlatformValue
              + ")");
      database.execSQL(
          "INSERT INTO download_tasks ("
              + "taskKey, resourceId, parentId, imageResId, text, type, createTime, "
              + "childrenNum, isLeaf, thumbnailUrl, sourceKey, downloadUrlsJson, imagePost, "
              + "status, progress, error, addedAt, storageDir, platform"
              + ") VALUES ("
              + taskKey
              + ", NULL, 0, 0, 'item', 'ALBUM', 1, 0, 1, '', "
              + sourceKey
              + ", '[]', 0, 'QUEUED', 0, NULL, 1, '', "
              + taskPlatformValue
              + ")");
    } finally {
      database.close();
    }
  }

  private SQLiteDatabase openVersion6Database() {
    File databaseFile = appContext.getDatabasePath(DATABASE_NAME);
    File parent = databaseFile.getParentFile();
    if (parent != null && !parent.exists()) {
      parent.mkdirs();
    }
    SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);
    database.setVersion(6);
    return database;
  }
}
