package com.hhst.dydownloader.util;

import java.io.File;

public final class CacheFiles {
  private CacheFiles() {}

  public static long size(File directory) {
    File[] entries = directory.listFiles();
    if (entries == null) return 0;
    long total = 0;
    for (File entry : entries) {
      if (Thread.currentThread().isInterrupted()) break;
      total += entry.isDirectory() ? size(entry) : entry.length();
    }
    return total;
  }

  public static void clear(File directory) {
    File[] entries = directory.listFiles();
    if (entries == null) return;
    for (File entry : entries) {
      if (Thread.currentThread().isInterrupted()) break;
      if (entry.isDirectory()) clear(entry);
      entry.delete();
    }
  }
}
