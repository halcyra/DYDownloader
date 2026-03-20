package com.hhst.dydownloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class HomeSearchBehaviorTest {

  @Test
  public void effectiveQuery_ignoresPersistedQueryWhenSearchUiIsHidden() throws Exception {
    assertEquals("", invokeEffectiveQuery(false, null, "cats"));
  }

  @Test
  public void shouldAlwaysRefreshDisplayWhenSearchUiIsHidden() throws Exception {
    String source =
        read(
            "app",
            "src",
            "main",
            "java",
            "com",
            "hhst",
            "dydownloader",
            "HomeFragment.java");

    assertTrue(source.contains("if (shouldRefreshDisplayAfterExit(clearText)) {"));
    assertTrue(source.contains("boolean shouldRefreshDisplayAfterExit(boolean clearText) {"));
    assertTrue(source.contains("return true;"));
  }

  @Test
  public void effectiveQuery_prefersLiveInputWhenSearchUiIsVisible() throws Exception {
    assertEquals("dogs", invokeEffectiveQuery(true, "  dogs  ", "cats"));
  }

  @Test
  public void effectiveQuery_fallsBackToPersistedQueryWhenInputMissing() throws Exception {
    assertEquals("birds", invokeEffectiveQuery(true, null, " birds "));
  }

  private static String invokeEffectiveQuery(
      boolean searchMode, CharSequence liveInput, String persistedQuery) throws Exception {
    Class<?> behaviorClass = Class.forName("com.hhst.dydownloader.HomeSearchBehavior");
    Method method =
        behaviorClass.getDeclaredMethod(
            "effectiveQuery", boolean.class, CharSequence.class, String.class);
    method.setAccessible(true);
    return (String) method.invoke(null, searchMode, liveInput, persistedQuery);
  }

  private static String read(String... segments) throws Exception {
    Path directPath = Path.of("", segments);
    if (Files.exists(directPath)) {
      return new String(Files.readAllBytes(directPath), StandardCharsets.UTF_8);
    }
    String[] prefixedSegments = new String[segments.length - 1];
    System.arraycopy(segments, 1, prefixedSegments, 0, prefixedSegments.length);
    return new String(Files.readAllBytes(Path.of("", prefixedSegments)), StandardCharsets.UTF_8);
  }
}
