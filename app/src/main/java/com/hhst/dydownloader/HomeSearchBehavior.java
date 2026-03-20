package com.hhst.dydownloader;

final class HomeSearchBehavior {
  private HomeSearchBehavior() {}

  static String effectiveQuery(boolean searchMode, CharSequence liveInput, String persistedQuery) {
    if (!searchMode) {
      return "";
    }
    String raw = liveInput != null ? liveInput.toString() : persistedQuery;
    return raw == null ? "" : raw.trim();
  }
}
