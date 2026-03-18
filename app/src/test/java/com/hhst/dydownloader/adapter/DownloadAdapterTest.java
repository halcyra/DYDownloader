package com.hhst.dydownloader.adapter;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DownloadAdapterTest {

  @Test
  public void summarizeErrorMessage_stripsExceptionPrefixAndNestedCause() {
    String summarized =
        DownloadAdapter.summarizeErrorMessage(
            "IOException: Download failed: 403 (SocketTimeoutException: timed out)",
            "Unknown error");

    assertEquals("Download failed: 403", summarized);
  }

  @Test
  public void summarizeErrorMessage_fallsBackForBlankInput() {
    assertEquals(
        "Unknown error", DownloadAdapter.summarizeErrorMessage("   ", "Unknown error"));
  }
}
