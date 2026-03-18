package com.hhst.dydownloader.manager;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.annotation.RequiresApi;
import com.hhst.dydownloader.util.StoragePathUtils;
import com.hhst.dydownloader.util.StorageReferenceUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class DownloadStorage {
  private final Context context;

  DownloadStorage(Context context) {
    this.context = context.getApplicationContext();
  }

  File tempDirectory() {
    File dir = new File(context.getExternalFilesDir(null), "downloads-temp");
    if (!dir.exists()) {
      dir.mkdirs();
    }
    return dir;
  }

  String storeDownloadedFile(File sourceFile, String relativeDir, String fileName, String mimeType)
      throws IOException {
    if (sourceFile == null || !sourceFile.exists() || !sourceFile.isFile()) {
      throw new IOException("Source file missing");
    }
    String safeName = StoragePathUtils.sanitizeFileName(fileName, sourceFile.getName());
    String safeRelativeDir = normalizeRelativeDir(relativeDir);
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return storeInMediaStore(sourceFile, safeRelativeDir, safeName, mimeType);
      }
      return storeInPublicDownloads(sourceFile, safeRelativeDir, safeName);
    } finally {
      if (sourceFile.exists()) {
        sourceFile.delete();
      }
    }
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private String storeInMediaStore(
      File sourceFile, String relativeDir, String fileName, String mimeType) throws IOException {
    String relativePath = buildRelativePath(relativeDir);
    deleteExistingMediaStoreEntries(relativePath, fileName);

    ContentValues values = new ContentValues();
    values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
    values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
    values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
    values.put(MediaStore.Downloads.IS_PENDING, 1);

    Uri uri =
        context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
    if (uri == null) {
      throw new IOException("Failed to create public download item");
    }

    try (InputStream input = new FileInputStream(sourceFile);
        OutputStream output = context.getContentResolver().openOutputStream(uri, "w")) {
      if (output == null) {
        throw new IOException("Failed to open public download output");
      }
      copy(input, output);
    } catch (Exception e) {
      context.getContentResolver().delete(uri, null, null);
      if (e instanceof IOException ioException) {
        throw ioException;
      }
      throw new IOException("Failed to store public download", e);
    }

    ContentValues publishValues = new ContentValues();
    publishValues.put(MediaStore.Downloads.IS_PENDING, 0);
    context.getContentResolver().update(uri, publishValues, null, null);
    return uri.toString();
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private void deleteExistingMediaStoreEntries(String relativePath, String fileName) {
    String selection =
        MediaStore.MediaColumns.RELATIVE_PATH
            + "=? AND "
            + MediaStore.MediaColumns.DISPLAY_NAME
            + "=?";
    String[] selectionArgs = {relativePath, fileName};
    try (Cursor cursor =
        context
            .getContentResolver()
            .query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                new String[] {MediaStore.MediaColumns._ID},
                selection,
                selectionArgs,
                null)) {
      if (cursor == null) {
        return;
      }
      while (cursor.moveToNext()) {
        long id = cursor.getLong(0);
        context
            .getContentResolver()
            .delete(
                ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                null,
                null);
      }
    }
  }

  private String storeInPublicDownloads(File sourceFile, String relativeDir, String fileName)
      throws IOException {
    File downloadsRoot = StorageReferenceUtils.publicDownloadsRootDirectory();
    File targetDir = relativeDir.isBlank() ? downloadsRoot : new File(downloadsRoot, relativeDir);
    if (!targetDir.exists() && !targetDir.mkdirs()) {
      throw new IOException("Failed to create public download directory");
    }
    File targetFile = new File(targetDir, fileName);
    try (InputStream input = new FileInputStream(sourceFile);
        OutputStream output = new FileOutputStream(targetFile, false)) {
      copy(input, output);
    }
    return targetFile.getAbsolutePath();
  }

  private String buildRelativePath(String relativeDir) {
    if (relativeDir.isBlank()) {
      return Environment.DIRECTORY_DOWNLOADS
          + "/"
          + StoragePathUtils.PUBLIC_DOWNLOADS_DIRECTORY_NAME
          + "/";
    }
    return Environment.DIRECTORY_DOWNLOADS
        + "/"
        + StoragePathUtils.PUBLIC_DOWNLOADS_DIRECTORY_NAME
        + "/"
        + relativeDir
        + "/";
  }

  private String normalizeRelativeDir(String relativeDir) {
    if (relativeDir == null || relativeDir.isBlank()) {
      return "";
    }
    String[] parts = relativeDir.replace('\\', '/').split("/");
    return StoragePathUtils.joinSegments(parts);
  }

  private void copy(InputStream input, OutputStream output) throws IOException {
    byte[] buffer = new byte[8192];
    int read;
    while ((read = input.read(buffer)) != -1) {
      output.write(buffer, 0, read);
    }
    output.flush();
  }
}
