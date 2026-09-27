package com.hhst.dydownloader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CacheFilesTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void clearsNestedCacheWhileKeepingRootUsable() throws Exception {
    File root = temporaryFolder.newFolder("cache");
    File nested = new File(root, "nested");
    assertTrue(nested.mkdir());
    Files.write(new File(root, "image").toPath(), new byte[3]);
    Files.write(new File(nested, "snapshot").toPath(), new byte[5]);
    assertEquals(8, CacheFiles.size(root));
    CacheFiles.clear(root);
    assertTrue(root.isDirectory());
    assertEquals(0, root.list().length);
    assertEquals(0, CacheFiles.size(root));
  }

  @Test
  public void missingCacheIsSafeToInspectAndClear() {
    File missing = new File(temporaryFolder.getRoot(), "missing");
    CacheFiles.clear(missing);
    assertEquals(0, CacheFiles.size(missing));
  }
}
