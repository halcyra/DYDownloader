package com.hhst.dydownloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hhst.dydownloader.manager.SourceKeyUtils;
import org.junit.Test;

public class ExampleUnitTest {
  @Test
  public void sourceKey_baseIsExtractedFromChildKey() {
    assertEquals("7363189720901351234", SourceKeyUtils.baseOf("7363189720901351234#photo:2"));
  }

  @Test
  public void sourceKey_resourceMatchingTreatsChildAsSameResource() {
    assertTrue(
        SourceKeyUtils.isSameResource(
            "7363189720901351234",
            "7363189720901351234#video"));
    assertFalse(SourceKeyUtils.isSameResource("a#photo:1", "b#photo:1"));
  }
}
