package com.hhst.dydownloader.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShareUrls {
  private static final Pattern URL_PATTERN =
      Pattern.compile(
          "(https?://[^\\s\"<>^`{|}\\uFF0C\\u3002\\uFF1B\\uFF01\\uFF1F\\u3001\\u3010\\u3011\\u300A\\u300B]+)");

  private ShareUrls() {}

  public static Optional<String> firstUrl(String text) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    Matcher matcher = URL_PATTERN.matcher(text);
    return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
  }
}
