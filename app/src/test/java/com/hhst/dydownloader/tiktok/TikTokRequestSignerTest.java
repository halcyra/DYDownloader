package com.hhst.dydownloader.tiktok;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.junit.Test;

public class TikTokRequestSignerTest {

  @Test
  public void sign_appendsRequiredTikTokSignatureFields() {
    TikTokRequestSigner signer = new TikTokRequestSigner(() -> 1710600000L);

    String query =
        signer.sign(Map.of("itemId", "7345678901234567890"), "ua", "device123", "token456");

    assertTrue(query.contains("itemId=7345678901234567890"));
    assertTrue(query.contains("device_id=device123"));
    assertTrue(query.contains("msToken=token456"));
    assertTrue(query.contains("X-Bogus="));
    assertTrue(query.contains("X-Gnarly="));
  }

  @Test
  public void sign_isDeterministicForFixedClock() {
    String first =
        new TikTokRequestSigner(() -> 1710600000L)
            .sign(Map.of("itemId", "7345678901234567890"), "ua", "device123", "token456");
    String second =
        new TikTokRequestSigner(() -> 1710600000L)
            .sign(Map.of("itemId", "7345678901234567890"), "ua", "device123", "token456");

    assertEquals(first, second);
  }
}
