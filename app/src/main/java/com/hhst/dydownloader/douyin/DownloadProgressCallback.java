package com.hhst.dydownloader.douyin;

@FunctionalInterface
public interface DownloadProgressCallback {
  void onProgress(DownloadProgress progress);
}
