package com.hhst.dydownloader.douyin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WebSignGeneratorTest {

  /** 与上游 Python 实现逐字节比对的固定向量（DouK-Downloader src/encrypt/websign.py）。 */
  @Test
  public void sign_matchesUpstreamReferenceVector() {
    String signed =
        WebSignGenerator.sign(
            "aid=6383&uifid=test_uifid&count=10&a_bogus=AbC123", "test_uifid", 1788848901L);
    assertEquals(
        "aid=6383&uifid=test_uifid&count=10&a_bogus=AbC123&timestamp=1788848901"
            + "&x-secsdk-web-signature=78cb958affede27d03041b4ae627219a",
        signed);
  }

  @Test
  public void sign_appendsTimestampAndSignatureAtEnd() {
    String signed =
        WebSignGenerator.sign("aid=6383&uifid=test_uifid&count=10", "test_uifid", 1788848901L);
    assertEquals(
        "x-secsdk-web-signature", signed.substring(signed.lastIndexOf('&') + 1).split("=")[0]);
    assertEquals(32, signed.substring(signed.lastIndexOf('=') + 1).length());
    assertTrue(signed.contains("timestamp=1788848901"));
  }

  @Test
  public void normalizeQuery_reencodesQueryLikeNativeSigner() {
    String normalized =
        WebSignGenerator.normalizeQuery("keyword=hello+world&uifid=test_uifid&a_bogus=sig%2Bwith%2Fchars");
    assertEquals(
        "keyword=hello%2Bworld&uifid=test_uifid&a_bogus=sig%2Bwith%2Fchars", normalized);
  }

  @Test
  public void normalizeQuery_keepsLiteralPlusInValue() {
    String normalized = WebSignGenerator.normalizeQuery("aid=6383&uifid=visitor+id&count=10");
    assertTrue(normalized.contains("uifid=visitor%2Bid"));
    assertFalse(normalized.contains("visitor+id"));
  }

  @Test
  public void normalizeQuery_preservesUnreservedCharacters() {
    assertEquals("a=1-._~b", WebSignGenerator.normalizeQuery("a=1-._~b"));
  }
}
