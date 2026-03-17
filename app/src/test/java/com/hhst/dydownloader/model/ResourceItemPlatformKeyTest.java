package com.hhst.dydownloader.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.List;
import org.junit.Test;

public class ResourceItemPlatformKeyTest {

  @Test
  public void key_prefixesPlatformToAvoidCrossPlatformCollisions() {
    ResourceItem douyin =
        new ResourceItem(
            Platform.DOUYIN,
            null,
            0L,
            0,
            "item",
            CardType.ALBUM,
            1L,
            0,
            true,
            "",
            null,
            "1234567890",
            List.of(),
            false,
            "");
    ResourceItem tiktok =
        new ResourceItem(
            Platform.TIKTOK,
            null,
            0L,
            0,
            "item",
            CardType.ALBUM,
            1L,
            0,
            true,
            "",
            null,
            "1234567890",
            List.of(),
            false,
            "");

    assertEquals("DOUYIN:1234567890", douyin.key());
    assertEquals("TIKTOK:1234567890", tiktok.key());
    assertNotEquals(douyin.key(), tiktok.key());
  }
}
