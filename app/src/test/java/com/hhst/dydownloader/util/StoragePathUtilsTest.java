package com.hhst.dydownloader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StoragePathUtilsTest {

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
}
