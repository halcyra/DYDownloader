package com.hhst.dydownloader;

import static org.junit.Assert.assertEquals;
import java.lang.reflect.Method;
import org.junit.Test;

public class HomeSearchBehaviorTest {

  @Test
  public void effectiveQuery_ignoresPersistedQueryWhenSearchUiIsHidden() throws Exception {
    assertEquals("", invokeEffectiveQuery(false, null, "cats"));
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
}
