package com.hhst.dydownloader.home;

public final class DownloadErrorMessage {

  private DownloadErrorMessage() {}

  public static String compactError(String error) {
    String compact = error == null || error.isBlank() ? "" : error.replaceAll("\\s+", " ").trim();
    if (compact.matches("^[A-Za-z0-9_.$]+:.*")) {
      compact = compact.substring(compact.indexOf(':') + 1).trim();
    }
    int causeSeparator = compact.indexOf(" (");
    if (causeSeparator > 0) {
      compact = compact.substring(0, causeSeparator).trim();
    }
    if (compact.length() > 60) {
      compact = compact.substring(0, 57).trim() + "...";
    }
    return compact;
  }
}
