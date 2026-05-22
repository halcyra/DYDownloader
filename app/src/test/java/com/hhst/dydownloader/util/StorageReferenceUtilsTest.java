package com.hhst.dydownloader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class StorageReferenceUtilsTest {

  @Test
  public void sizeOfReference_returnsFileLengthForPlainFilePath() throws Exception {
    Path tempFile = Files.createTempFile("dy-storage-size", ".bin");
    Files.write(tempFile, new byte[] {1, 2, 3, 4, 5});

    long size = StorageReferenceUtils.sizeOfReference(null, tempFile.toString());

    assertEquals(5L, size);
    assertTrue(Files.exists(tempFile));
  }
}
