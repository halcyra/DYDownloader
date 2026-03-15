package com.hhst.dydownloader;

import com.hhst.dydownloader.model.ResourceItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ResourceScreenStore {
  private static final Map<String, ArrayList<ResourceItem>> SCREENS = new ConcurrentHashMap<>();

  private ResourceScreenStore() {}

  public static String put(List<ResourceItem> items) {
    String screenKey = UUID.randomUUID().toString();
    replace(screenKey, items);
    return screenKey;
  }

  public static String replace(String screenKey, List<ResourceItem> items) {
    String resolvedKey =
        screenKey == null || screenKey.isBlank() ? UUID.randomUUID().toString() : screenKey;
    SCREENS.put(resolvedKey, new ArrayList<>(items == null ? List.of() : items));
    return resolvedKey;
  }

  public static ArrayList<ResourceItem> get(String screenKey) {
    if (screenKey == null || screenKey.isBlank()) {
      return new ArrayList<>();
    }
    ArrayList<ResourceItem> items = SCREENS.get(screenKey);
    return items == null ? new ArrayList<>() : new ArrayList<>(items);
  }
}
