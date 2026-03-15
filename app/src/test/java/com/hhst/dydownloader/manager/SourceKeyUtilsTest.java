package com.hhst.dydownloader.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SourceKeyUtilsTest {

  @Test
  public void photoAndLiveMarkers_areParsedAsZeroBasedIndex() {
    assertEquals(0, SourceKeyUtils.photoIndex("aweme123#photo:1"));
    assertEquals(2, SourceKeyUtils.photoIndex("aweme123#photo:3"));
    assertEquals(1, SourceKeyUtils.liveIndex("aweme123#live:2"));
  }

  @Test
  public void imageLeafIndex_prefersPhotoThenLive() {
    assertEquals(4, SourceKeyUtils.imageLeafIndex("aweme123#photo:5"));
    assertEquals(3, SourceKeyUtils.imageLeafIndex("aweme123#live:4"));
    assertEquals(-1, SourceKeyUtils.imageLeafIndex("aweme123#video"));
  }

  @Test
  public void hasImageLeafMarker_detectsPhotoAndLive() {
    assertTrue(SourceKeyUtils.hasImageLeafMarker("aweme123#photo:1"));
    assertTrue(SourceKeyUtils.hasImageLeafMarker("aweme123#live:1"));
    assertFalse(SourceKeyUtils.hasImageLeafMarker("aweme123#cover"));
  }
}
