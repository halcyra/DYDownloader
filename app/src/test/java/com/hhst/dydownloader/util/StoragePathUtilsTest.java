package com.hhst.dydownloader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StoragePathUtilsTest {
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
  public void buildPublicDownloadDisplayPath_joinsRelativeDirectory() {
    String displayPath =
        StoragePathUtils.buildPublicDownloadDisplayPath("Creator Name/Work Title");

    assertEquals("Download/DYDownloader/Creator Name/Work Title", displayPath);
  }

  @Test
  public void buildPublicDownloadDisplayPath_usesUnifiedRootForBlankRelativeDirectory() {
    assertEquals("Download/DYDownloader", StoragePathUtils.buildPublicDownloadDisplayPath(""));
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
