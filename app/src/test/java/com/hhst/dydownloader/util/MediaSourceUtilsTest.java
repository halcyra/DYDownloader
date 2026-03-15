package com.hhst.dydownloader.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MediaSourceUtilsTest {

  @Test
  public void likelyVideoSource_matchesTypicalVideoPatterns() {
    assertTrue(MediaSourceUtils.isLikelyVideoSource("https://xx.com/a.mp4"));
    assertTrue(MediaSourceUtils.isLikelyVideoSource("https://xx.com/path/video_mp4"));
    assertTrue(MediaSourceUtils.isLikelyVideoSource("https://xx.com/play/?mime_type=video"));
    assertTrue(MediaSourceUtils.isLikelyVideoSource("https://xx.com/aweme/v1/play/"));
  }

  @Test
  public void likelyVideoSource_rejectsImageLinks() {
    assertFalse(MediaSourceUtils.isLikelyVideoSource("https://xx.com/image.jpg"));
    assertFalse(MediaSourceUtils.isLikelyVideoSource("https://xx.com/image.webp"));
    assertFalse(MediaSourceUtils.isLikelyVideoSource(""));
    assertFalse(MediaSourceUtils.isLikelyVideoSource(null));
  }
}
