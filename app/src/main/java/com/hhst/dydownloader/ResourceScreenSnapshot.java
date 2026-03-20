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

final class ResourceScreenSnapshot {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<List<ResourceItem>> RESOURCE_LIST_TYPE =
      new TypeReference<>() {};

  private ResourceScreenSnapshot() {}

  static String persist(File directory, String token, List<ResourceItem> items) {
    if (items == null || items.isEmpty()) {
      return "";
    }
    try {
      String resolvedToken =
          token == null || token.isBlank() ? UUID.randomUUID().toString() : token.trim();
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
      if (directory == null) {
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
      return outputStream.toString(StandardCharsets.UTF_8);
    }
  }

  private static String serialize(List<ResourceItem> items) throws Exception {
    return OBJECT_MAPPER.writeValueAsString(items);
  }

  private static ArrayList<ResourceItem> deserialize(String json) throws Exception {
    if (json == null || json.isBlank()) {
      return new ArrayList<>();
    }
    return new ArrayList<>(OBJECT_MAPPER.readValue(json, RESOURCE_LIST_TYPE));
  }
}
