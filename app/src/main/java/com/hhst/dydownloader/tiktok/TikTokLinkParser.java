package com.hhst.dydownloader.tiktok;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TikTokLinkParser {
  private static final Pattern ITEM_ID_PATTERN =
      Pattern.compile(
          "https?://(?:www\\.)?tiktok\\.com/@[^\\s/]+/(?:video|photo)/(\\d{19})",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern SEC_UID_PATTERN =
      Pattern.compile("\"(?:verified\":(?:false|true),)?\"?secUid\"?:\"([a-zA-Z0-9_-]+)\"");
  private static final Pattern COLLECTION_ID_PATTERN =
      Pattern.compile(
          "https?://(?:www\\.)?tiktok\\.com/@[^\\s/]+/(?:playlist|collection)/[^\\s\"']*?-(\\d{19})",
          Pattern.CASE_INSENSITIVE);

  private TikTokLinkParser() {}

  public static Optional<String> extractItemId(String text) {
    return firstGroup(ITEM_ID_PATTERN, text);
  }

  public static Optional<String> extractSecUidFromHtml(String html) {
    return firstGroup(SEC_UID_PATTERN, html);
  }

  public static Optional<String> extractCollectionId(String text) {
    return firstGroup(COLLECTION_ID_PATTERN, text);
  }

  public static Optional<String> extractItemStructJson(String html, String itemId) {
    if (html == null || html.isBlank()) {
      return Optional.empty();
    }
    int searchStart = 0;
    while (searchStart >= 0 && searchStart < html.length()) {
      int tokenIndex = html.indexOf("\"itemStruct\":", searchStart);
      if (tokenIndex < 0) {
        return Optional.empty();
      }
      int objectStart = html.indexOf('{', tokenIndex);
      if (objectStart < 0) {
        return Optional.empty();
      }
      int objectEnd = findMatchingBrace(html, objectStart);
      if (objectEnd < 0) {
        return Optional.empty();
      }
      String candidate = html.substring(objectStart, objectEnd + 1);
      if (itemId == null || itemId.isBlank() || candidate.contains("\"id\":\"" + itemId + "\"")) {
        return Optional.of(candidate);
      }
      searchStart = objectEnd + 1;
    }
    return Optional.empty();
  }

  private static Optional<String> firstGroup(Pattern pattern, String text) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    Matcher matcher = pattern.matcher(text);
    return matcher.find() ? Optional.ofNullable(matcher.group(1)) : Optional.empty();
  }

  private static int findMatchingBrace(String text, int startIndex) {
    int depth = 0;
    boolean inString = false;
    boolean escaped = false;
    for (int index = startIndex; index < text.length(); index++) {
      char current = text.charAt(index);
      if (escaped) {
        escaped = false;
        continue;
      }
      if (current == '\\') {
        escaped = true;
        continue;
      }
      if (current == '"') {
        inString = !inString;
        continue;
      }
      if (inString) {
        continue;
      }
      if (current == '{') {
        depth++;
      } else if (current == '}') {
        depth--;
        if (depth == 0) {
          return index;
        }
      }
    }
    return -1;
  }
}
