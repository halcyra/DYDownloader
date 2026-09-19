package com.hhst.dydownloader.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DownloadErrorMessageTest {

  @Test
  public void compactError_stripsExceptionPrefixAndNestedCause() {
    assertEquals(
        "Download failed: 403",
        DownloadErrorMessage.compactError(
            "IOException: Download failed: 403 (SocketTimeoutException: timed out)"));
  }

  @Test
  public void compactError_returnsEmptyForBlankInput() {
    assertEquals("", DownloadErrorMessage.compactError("   "));
  }

  @Test
  public void compactError_truncatesOverlongMessage() {
    String longError = "x".repeat(80);
    assertEquals(60, DownloadErrorMessage.compactError(longError).length());
    assertTrue(DownloadErrorMessage.compactError(longError).endsWith("..."));
  }
}
