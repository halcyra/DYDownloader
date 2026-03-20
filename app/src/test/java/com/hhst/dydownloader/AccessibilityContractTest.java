package com.hhst.dydownloader;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class AccessibilityContractTest {

  @Test
  public void mainActivity_updatesFabDescriptionForDeleteMode() throws Exception {
    String source = read("app", "src", "main", "java", "com", "hhst", "dydownloader", "MainActivity.java");

    assertTrue(source.contains("fab.setContentDescription(getString(contentDescriptionRes));"));
    assertTrue(source.contains("R.string.action_delete"));
    assertTrue(source.contains("R.string.download"));
  }

  @Test
  public void itemCardAndDownloadLayouts_useDecorativeImageSemanticsAnd48dpActions()
      throws Exception {
    String cardLayout = read("app", "src", "main", "res", "layout", "item_card.xml");
    String downloadLayout = read("app", "src", "main", "res", "layout", "item_download.xml");

    assertTrue(cardLayout.contains("android:id=\"@+id/cardImage\""));
    assertTrue(cardLayout.contains("android:contentDescription=\"@null\""));
    assertTrue(cardLayout.contains("android:importantForAccessibility=\"no\""));
    assertTrue(cardLayout.contains("android:id=\"@+id/cardMore\""));
    assertTrue(cardLayout.contains("android:layout_width=\"48dp\""));
    assertTrue(cardLayout.contains("android:layout_height=\"48dp\""));

    assertTrue(downloadLayout.contains("android:id=\"@+id/downloadThumbnail\""));
    assertTrue(downloadLayout.contains("android:contentDescription=\"@null\""));
    assertTrue(downloadLayout.contains("android:importantForAccessibility=\"no\""));
    assertTrue(downloadLayout.contains("android:id=\"@+id/downloadMore\""));
    assertTrue(downloadLayout.contains("android:id=\"@+id/downloadRetry\""));
    assertTrue(downloadLayout.contains("android:minWidth=\"48dp\""));
    assertTrue(downloadLayout.contains("android:minHeight=\"48dp\""));
  }

  @Test
  public void itemResourceAndPreviewLayouts_useAccessibleSemanticsAndTouchTargets()
      throws Exception {
    String resourceLayout = read("app", "src", "main", "res", "layout", "item_resource.xml");
    String previewLayout = read("app", "src", "main", "res", "layout", "activity_preview.xml");

    assertTrue(resourceLayout.contains("android:id=\"@+id/resourceImage\""));
    assertTrue(resourceLayout.contains("android:id=\"@+id/resourceVideoThumbnail\""));
    assertTrue(resourceLayout.contains("android:id=\"@+id/resourceTypeIcon\""));
    assertTrue(resourceLayout.contains("android:contentDescription=\"@null\""));
    assertTrue(resourceLayout.contains("android:importantForAccessibility=\"no\""));
    assertTrue(resourceLayout.contains("android:id=\"@+id/resourceCheckContainer\""));
    assertTrue(resourceLayout.contains("android:layout_width=\"48dp\""));
    assertTrue(resourceLayout.contains("android:layout_height=\"48dp\""));

    assertTrue(previewLayout.contains("android:id=\"@+id/previewClose\""));
    assertTrue(previewLayout.contains("android:layout_width=\"48dp\""));
    assertTrue(previewLayout.contains("android:layout_height=\"48dp\""));
  }

  @Test
  public void resourceCheckSelectionControl_usesContainerSemanticsAndRuntimeStateDescriptions()
      throws Exception {
    String resourceLayout = read("app", "src", "main", "res", "layout", "item_resource.xml");
    String strings = read("app", "src", "main", "res", "values", "strings.xml");
    String adapterSource =
        read(
            "app",
            "src",
            "main",
            "java",
            "com",
            "hhst",
            "dydownloader",
            "adapter",
            "ResourceAdapter.java");

    assertTrue(resourceLayout.contains("android:id=\"@+id/resourceCheckContainer\""));
    assertTrue(
        resourceLayout.contains(
            "android:contentDescription=\"@string/resource_check_container_desc\""));
    assertTrue(resourceLayout.contains("android:id=\"@+id/resourceCheckIcon\""));
    assertTrue(resourceLayout.contains("android:contentDescription=\"@null\""));
    assertTrue(resourceLayout.contains("android:importantForAccessibility=\"no\""));
    assertTrue(strings.contains("resource_check_container_desc"));
    assertTrue(strings.contains("resource_check_selected_state"));
    assertTrue(strings.contains("resource_check_unselected_state"));
    assertTrue(strings.contains("resource_check_disabled_state"));
    assertTrue(adapterSource.contains("ViewCompat.setStateDescription(holder.checkContainer"));
    assertTrue(adapterSource.contains("holder.checkContainer.setContentDescription("));
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
