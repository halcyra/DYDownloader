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

  @Test
  public void baseOf_extractsBaseFromChildKey() {
    assertEquals("7363189720901351234", SourceKeyUtils.baseOf("7363189720901351234#photo:2"));
  }

  @Test
  public void isSameResource_treatsChildAsSameResource() {
    assertTrue(
        SourceKeyUtils.isSameResource(
            "7363189720901351234", "7363189720901351234#video"));
    assertFalse(SourceKeyUtils.isSameResource("a#photo:1", "b#photo:1"));
  }
}
