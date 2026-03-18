package com.hhst.dydownloader.tiktok;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TikTokLinkParserTest {

  @Test
  public void extractItemId_readsIdFromCanonicalWorkUrl() {
    assertEquals(
        "7345678901234567890",
        TikTokLinkParser
            .extractItemId("https://www.tiktok.com/@name/video/7345678901234567890")
            .orElseThrow());
  }

  @Test
  public void extractItemId_readsIdFromCanonicalPhotoUrl() {
    assertEquals(
        "7345678901234567890",
        TikTokLinkParser
            .extractItemId("https://www.tiktok.com/@name/photo/7345678901234567890")
            .orElseThrow());
  }

  @Test
  public void extractSecUid_readsSecUidFromHtml() {
    String html = "\"verified\":true,\"secUid\":\"MS4wLjABAAAA1234\"";

    assertEquals("MS4wLjABAAAA1234", TikTokLinkParser.extractSecUidFromHtml(html).orElseThrow());
  }

  @Test
  public void extractCollectionId_readsCollectionIdFromCanonicalHtml() {
    String html = "\"canonical\":\"https://www.tiktok.com/@user/collection/name-7345678901234567890\"";

    assertEquals("7345678901234567890", TikTokLinkParser.extractCollectionId(html).orElseThrow());
  }

  @Test
  public void extractCollectionId_readsPlaylistIdFromUrl() {
    assertEquals(
        "7345678901234567890",
        TikTokLinkParser
            .extractCollectionId("https://www.tiktok.com/@user/playlist/name-7345678901234567890")
            .orElseThrow());
  }

  @Test
  public void extractItemStructJson_readsEmbeddedItemStructObject() {
    String html =
        "<script>window.__UNIVERSAL_DATA_FOR_REHYDRATION__={\"itemStruct\":{\"id\":\"7345678901234567890\",\"desc\":\"hello\"}}</script>";

    assertEquals(
        "{\"id\":\"7345678901234567890\",\"desc\":\"hello\"}",
        TikTokLinkParser.extractItemStructJson(html, "7345678901234567890").orElseThrow());
  }
}
