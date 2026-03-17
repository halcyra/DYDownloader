package com.hhst.dydownloader.tiktok;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TikTokLinkParser {
  private static final Pattern ITEM_ID_PATTERN =
      Pattern.compile(
          "https?://(?:www\\.)?tiktok\\.com/@[^\\s/]+/(?:(?:video|photo)/(\\d{19}))",
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

  private static Optional<String> firstGroup(Pattern pattern, String text) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    Matcher matcher = pattern.matcher(text);
    return matcher.find() ? Optional.ofNullable(matcher.group(1)) : Optional.empty();
  }
}
