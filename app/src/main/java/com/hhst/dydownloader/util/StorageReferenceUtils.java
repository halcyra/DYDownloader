package com.hhst.dydownloader.util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import com.hhst.dydownloader.DYDownloaderApp;
import java.io.File;
import java.io.InputStream;
import java.util.Locale;

public final class StorageReferenceUtils {
  private StorageReferenceUtils() {}

  public static boolean isContentReference(@Nullable String reference) {
    return reference != null && reference.startsWith("content://");
  }

  public static boolean isRemoteReference(@Nullable String reference) {
    return reference != null
        && (reference.startsWith("http://") || reference.startsWith("https://"));
  }

  public static Uri parseReference(@Nullable String reference) {
    if (reference == null || reference.isBlank()) {
      return Uri.EMPTY;
    }
    if (isContentReference(reference) || isRemoteReference(reference)) {
      return Uri.parse(reference);
    }
    return Uri.fromFile(new File(reference));
  }

  public static Uri buildPublicDownloadDirectoryUri(@Nullable String relativeDir) {
    String relativePath =
        Environment.DIRECTORY_DOWNLOADS + "/" + StoragePathUtils.PUBLIC_DOWNLOADS_DIRECTORY_NAME;
    String normalizedRelativeDir = normalizeRelativeDir(relativeDir);
    if (!normalizedRelativeDir.isBlank()) {
      relativePath += "/" + normalizedRelativeDir;
    }
    return DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents", "primary:" + relativePath);
  }

  public static File buildPublicDownloadDirectoryFile(@Nullable String relativeDir) {
    File rootDirectory = publicDownloadsRootDirectory();
    String normalizedRelativeDir = normalizeRelativeDir(relativeDir);
    return normalizedRelativeDir.isBlank()
        ? rootDirectory
        : new File(rootDirectory, normalizedRelativeDir);
  }

  public static Uri buildPublicDownloadDirectoryShareUri(
      @NonNull Context context, @Nullable String relativeDir) {
    return FileProvider.getUriForFile(
        context,
        context.getPackageName() + ".fileprovider",
        buildPublicDownloadDirectoryFile(relativeDir));
  }

  public static String resolvePublicDownloadRelativeDir(
      @Nullable Context context, @Nullable String reference) {
    if (reference == null || reference.isBlank()) {
      return "";
    }
    if (!isContentReference(reference)) {
      return relativeDirFromFileReference(reference);
    }

    Context safeContext = context != null ? context : DYDownloaderApp.getInstance();
    if (safeContext == null) {
      return "";
    }
    try (Cursor cursor =
        safeContext
            .getContentResolver()
            .query(
                Uri.parse(reference),
                new String[] {android.provider.MediaStore.MediaColumns.RELATIVE_PATH},
                null,
                null,
                null)) {
      if (cursor == null || !cursor.moveToFirst()) {
        return "";
      }
      int columnIndex =
          cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.RELATIVE_PATH);
      if (columnIndex < 0) {
        return "";
      }
      return normalizePublicDownloadsRelativePath(cursor.getString(columnIndex));
    } catch (Exception ignored) {
      return "";
    }
  }

  public static boolean exists(@Nullable Context context, @Nullable String reference) {
    if (reference == null || reference.isBlank()) {
      return false;
    }
    if (!isContentReference(reference)) {
      File file = new File(reference);
      return file.exists() && file.isFile();
    }
    Context safeContext = context != null ? context : DYDownloaderApp.getInstance();
    if (safeContext == null) {
      return false;
    }
    try (InputStream inputStream =
        safeContext.getContentResolver().openInputStream(Uri.parse(reference))) {
      return inputStream != null;
    } catch (Exception ignored) {
      return false;
    }
  }

  public static boolean delete(@Nullable Context context, @Nullable String reference) {
    if (reference == null || reference.isBlank()) {
      return false;
    }
    if (!isContentReference(reference)) {
      File file = new File(reference);
      return !file.exists() || file.delete();
    }
    Context safeContext = context != null ? context : DYDownloaderApp.getInstance();
    if (safeContext == null) {
      return false;
    }
    try {
      return safeContext.getContentResolver().delete(Uri.parse(reference), null, null) > 0;
    } catch (Exception ignored) {
      return false;
    }
  }

  public static Uri toShareableUri(Context context, String reference) {
    if (isContentReference(reference)) {
      return Uri.parse(reference);
    }
    return FileProvider.getUriForFile(
        context, context.getPackageName() + ".fileprovider", new File(reference));
  }

  public static String resolveMimeType(Context context, String reference) {
    if (reference == null || reference.isBlank()) {
      return "*/*";
    }
    Uri uri = parseReference(reference);
    String fromResolver = context.getContentResolver().getType(uri);
    if (fromResolver != null && !fromResolver.isBlank()) {
      return fromResolver;
    }
    String fileName = displayName(context, reference);
    String extension = MimeTypeMap.getFileExtensionFromUrl(fileName);
    if (extension != null && !extension.isBlank()) {
      String mimeType =
          MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase(Locale.ROOT));
      if (mimeType != null && !mimeType.isBlank()) {
        return mimeType;
      }
    }
    return "*/*";
  }

  public static String displayName(@Nullable Context context, @Nullable String reference) {
    if (reference == null || reference.isBlank()) {
      return "";
    }
    if (!isContentReference(reference)) {
      return new File(reference).getName();
    }
    Context safeContext = context != null ? context : DYDownloaderApp.getInstance();
    if (safeContext == null) {
      return "";
    }
    try (Cursor cursor =
        safeContext
            .getContentResolver()
            .query(
                Uri.parse(reference),
                new String[] {OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null)) {
      if (cursor != null && cursor.moveToFirst()) {
        int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if (columnIndex >= 0) {
          return cursor.getString(columnIndex);
        }
      }
    } catch (Exception ignored) {
    }
    return "";
  }

  private static String normalizeRelativeDir(@Nullable String relativeDir) {
    if (relativeDir == null || relativeDir.isBlank()) {
      return "";
    }
    String[] parts = relativeDir.replace('\\', '/').split("/");
    return StoragePathUtils.joinSegments(parts);
  }

  private static String relativeDirFromFileReference(@NonNull String reference) {
    File file = new File(reference);
    File directory = file.isDirectory() ? file : file.getParentFile();
    if (directory == null) {
      return "";
    }

    File downloadsRoot = publicDownloadsRootDirectory();
    String rootPath = downloadsRoot.getAbsolutePath().replace('\\', '/');
    String directoryPath = directory.getAbsolutePath().replace('\\', '/');
    if (!directoryPath.startsWith(rootPath)) {
      return "";
    }
    String relative = directoryPath.substring(rootPath.length());
    if (relative.startsWith("/")) {
      relative = relative.substring(1);
    }
    return normalizeRelativeDir(relative);
  }

  private static String normalizePublicDownloadsRelativePath(@Nullable String relativePath) {
    String normalized = relativePath == null ? "" : relativePath.replace('\\', '/').trim();
    if (normalized.isBlank()) {
      return "";
    }
    String publicRoot =
        Environment.DIRECTORY_DOWNLOADS + "/" + StoragePathUtils.PUBLIC_DOWNLOADS_DIRECTORY_NAME;
    if (normalized.startsWith(publicRoot + "/")) {
      normalized = normalized.substring(publicRoot.length() + 1);
    } else if (normalized.equals(publicRoot)) {
      normalized = "";
    }
    if (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalizeRelativeDir(normalized);
  }

  public static File publicDownloadsRootDirectory() {
    // Keep using the shared public Downloads root so existing visible storage paths remain
    // unchanged.
    return new File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        StoragePathUtils.PUBLIC_DOWNLOADS_DIRECTORY_NAME);
  }
}
