package com.hhst.dydownloader.douyin;

public record DownloadProgress(
    String scope, int completed, int total, String currentId, boolean success, String message) {

  public double progress() {
    return total > 0 ? (double) completed / total : 0;
  }
}
