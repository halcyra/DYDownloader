package com.hhst.dydownloader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class StoragePathUtilsTest {
  @Test
  public void normalizeRelativeDirHandlesMixedSeparatorsAndTraversalSegments() {
    assertEquals("Creator/Sub/bad name",
        StoragePathUtils.normalizeRelativeDir("/../Creator /..//Sub\\bad:name"));
    assertEquals("", StoragePathUtils.normalizeRelativeDir(null));
  }

  @Test
  public void sanitizeNamesAlsoCleansFallbacks() {
    assertEquals("fallback name", StoragePathUtils.sanitizeSegment("...", "../fallback/name"));
    assertEquals("fallback name.jpg", StoragePathUtils.sanitizeFileName("...", "../fallback/name.jpg"));
    assertEquals("", StoragePathUtils.sanitizeSegment("...", ".."));
  }

  @Test
  public void expandFileNameTemplate_doesNotExpandPlaceholdersInsideMetadata() {
    assertEquals(
        "A{desc}_Price $5 {id}_123",
        StoragePathUtils.expandFileNameTemplate(
            "{author}_{desc}_{id}", "A{desc}", "Price $5 {id}", "2026-09-26", "123"));
  }


  @Test
  public void sanitizeSegment_shortensLongNamesWithStableSuffix() {
    String longTitle =
        "This is a very long work title that should be shortened before being used as a directory name";

    String sanitized = StoragePathUtils.sanitizeSegment(longTitle, "Work");

    assertTrue(sanitized.length() <= 48);
    assertTrue(sanitized.contains("_"));
  }

  @Test
  public void sanitizeFileName_preservesExtension() {
    String fileName =
        "This is a very long work title that should be shortened before being used as a filename.jpg";

    String sanitized = StoragePathUtils.sanitizeFileName(fileName, "fallback.jpg");

    assertTrue(sanitized.length() <= 96);
    assertTrue(sanitized.endsWith(".jpg"));
  }

  @Test
  public void sanitizeSegment_doesNotSplitEmojiAtTruncationBoundary() {
    String prefix = "中".repeat(38);
    String title = prefix + "\uD83C\uDDF3\uD83C\uDDFF" + "山水".repeat(10);

    String sanitized = StoragePathUtils.sanitizeSegment(title, "Work");

    assertEquals(prefix + "_" + StoragePathUtils.stableToken(title), sanitized);
    assertTrue(sanitized.length() <= 48);
    assertTrue(StandardCharsets.UTF_8.newEncoder().canEncode(sanitized));
  }

  @Test
  public void sanitizeFileName_doesNotSplitEmojiAndPreservesExtension() {
    String prefix = "a".repeat(82);
    String baseName = prefix + "\uD83D\uDE00" + "tail".repeat(8);

    String sanitized = StoragePathUtils.sanitizeFileName(baseName + ".jpg", "fallback.jpg");

    assertEquals(prefix + "_" + StoragePathUtils.stableToken(baseName) + ".jpg", sanitized);
    assertTrue(sanitized.length() <= 96);
    assertTrue(StandardCharsets.UTF_8.newEncoder().canEncode(sanitized));
  }

  @Test
  public void shortenWithHash_keepsSupplementaryCharactersValidAtEveryBoundary() {
    String title = "山水\uD83C\uDDF3\uD83C\uDDFF\uD83D\uDE00\uD842\uDFB7".repeat(12);

    for (int limit = 24; limit <= 96; limit++) {
      String shortened = StoragePathUtils.shortenWithHash(title, limit);

      assertTrue("Invalid Unicode at limit " + limit,
          StandardCharsets.UTF_8.newEncoder().canEncode(shortened));
      assertTrue(shortened.length() <= limit);
      assertTrue(shortened.endsWith("_" + StoragePathUtils.stableToken(title)));
    }
  }

  @Test
  public void sanitizeNames_cleansUnpairedSurrogatesFromMetadataAndFallbacks() {
    assertEquals("标题 \uD83D\uDE00 结尾",
        StoragePathUtils.sanitizeSegment("标题\uD83C \uD83D\uDE00 \uDDFF结尾", "fallback"));
    assertEquals("photo.jpg",
        StoragePathUtils.sanitizeFileName("photo.jpg\uD83C", "fallback.jpg"));
    assertEquals("fallback.jpg",
        StoragePathUtils.sanitizeFileName("\uDDFF", "\uD83Cfallback.jpg\uDDFF"));
    assertEquals("作者/作品 \uD83D\uDE00",
        StoragePathUtils.normalizeRelativeDir("作者\uD83C/作品 \uD83D\uDE00\uDDFF"));
  }

  @Test
  public void sanitizeNames_preservesCompleteEmojiWithoutShortening() {
    String title = "\uD83C\uDDF3\uD83C\uDDFF 新西兰 \uD83D\uDE00\uD842\uDFB7";

    assertEquals(title, StoragePathUtils.sanitizeSegment(title, "fallback"));
    assertEquals(title + ".jpg",
        StoragePathUtils.sanitizeFileName(title + ".jpg", "fallback.jpg"));
  }

  @Test
  public void expandFileNameTemplate_defaultsToDescription() {
    assertEquals(
        "分享日常",
        StoragePathUtils.expandFileNameTemplate(null, "作者", "分享日常", "2026-09-19", "709384"));
  }

  @Test
  public void expandFileNameTemplate_expandsAllPlaceholders() {
    assertEquals(
        "作者_分享日常_2026-09-19_709384",
        StoragePathUtils.expandFileNameTemplate(
            "{author}_{desc}_{date}_{id}", "作者", "分享日常", "2026-09-19", "709384"));
  }

  @Test
  public void expandFileNameTemplate_collapsesSeparatorsLeftByEmptyPlaceholders() {
    assertEquals(
        "分享日常_709384",
        StoragePathUtils.expandFileNameTemplate("{author}_{desc}_{id}", "", "分享日常", "", "709384"));
  }

  @Test
  public void expandFileNameTemplate_keepsUnknownPlaceholdersVerbatim() {
    assertEquals(
        "{title}_分享日常",
        StoragePathUtils.expandFileNameTemplate("{title}_{desc}", "作者", "分享日常", "2026", "709"));
  }
}
