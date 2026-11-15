package com.hhst.dydownloader;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhst.dydownloader.model.ResourceItem;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

final class ResourceScreenSnapshot {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");
  private static final TypeReference<List<ResourceItem>> RESOURCE_LIST_TYPE =
      new TypeReference<>() {};

  private ResourceScreenSnapshot() {}

  static String persist(File directory, String token, List<ResourceItem> items) {
    if (directory == null || items == null || items.isEmpty()) {
      return "";
    }
    try {
      String resolvedToken =
          isFileToken(token) ? token.trim() : UUID.randomUUID().toString();
      if (!directory.exists() && !directory.mkdirs()) {
        return "";
      }
      File snapshotFile = new File(directory, resolvedToken + ".json");
      OBJECT_MAPPER.writeValue(snapshotFile, items);
      return resolvedToken;
    } catch (Exception ignored) {
      return "";
    }
  }

  static ArrayList<ResourceItem> restore(File directory, String token) {
    if (token == null || token.isBlank()) {
      return new ArrayList<>();
    }
    try {
      String trimmedToken = token.trim();
      List<ResourceItem> items;
      if (trimmedToken.startsWith("[") || trimmedToken.startsWith("{")) {
        items = OBJECT_MAPPER.readValue(trimmedToken, RESOURCE_LIST_TYPE);
      } else if (directory != null && isFileToken(trimmedToken)) {
        items = OBJECT_MAPPER.readValue(new File(directory, trimmedToken + ".json"), RESOURCE_LIST_TYPE);
      } else {
        return new ArrayList<>();
      }
      return validItems(items) ? new ArrayList<>(items) : new ArrayList<>();
    } catch (Exception ignored) {
      return new ArrayList<>();
    }
  }

  static void delete(File directory, String token) {
    if (directory != null && isFileToken(token)) {
      new File(directory, token.trim() + ".json").delete();
    }
  }

  private static boolean isFileToken(String token) {
    return token != null && TOKEN_PATTERN.matcher(token.trim()).matches();
  }

  private static boolean validItems(List<ResourceItem> items) {
    if (items == null) return false;
    for (ResourceItem item : items) {
      if (item == null || item.type() == null
          || (item.children() != null && !validItems(item.children()))) {
        return false;
      }
    }
    return true;
  }
}
