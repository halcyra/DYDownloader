package com.hhst.dydownloader;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhst.dydownloader.model.ResourceItem;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
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
    if (items == null || items.isEmpty()) {
      return "";
    }
    try {
      String resolvedToken =
          isFileToken(token) ? token.trim() : UUID.randomUUID().toString();
      if (directory != null && !directory.exists()) {
        directory.mkdirs();
      }
      if (directory == null) {
        return "";
      }
      File snapshotFile = new File(directory, resolvedToken + ".json");
      writeSnapshot(snapshotFile, serialize(items));
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
      if (trimmedToken.startsWith("[") || trimmedToken.startsWith("{")) {
        return deserialize(trimmedToken);
      }
      if (directory == null || !isFileToken(trimmedToken)) {
        return new ArrayList<>();
      }
      File snapshotFile = new File(directory, trimmedToken + ".json");
      if (!snapshotFile.exists()) {
        return new ArrayList<>();
      }
      return deserialize(readSnapshot(snapshotFile));
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

  private static void writeSnapshot(File snapshotFile, String payload) throws Exception {
    try (FileOutputStream outputStream = new FileOutputStream(snapshotFile)) {
      outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
    }
  }

  private static String readSnapshot(File snapshotFile) throws Exception {
    try (FileInputStream inputStream = new FileInputStream(snapshotFile);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[4096];
      int read;
      while ((read = inputStream.read(buffer)) != -1) {
        outputStream.write(buffer, 0, read);
      }
      return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }
  }

  private static String serialize(List<ResourceItem> items) throws Exception {
    return OBJECT_MAPPER.writeValueAsString(items);
  }

  private static ArrayList<ResourceItem> deserialize(String json) throws Exception {
    if (json == null || json.isBlank()) {
      return new ArrayList<>();
    }
    List<ResourceItem> items = OBJECT_MAPPER.readValue(json, RESOURCE_LIST_TYPE);
    return validItems(items) ? new ArrayList<>(items) : new ArrayList<>();
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
