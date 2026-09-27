package com.hhst.dydownloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.hhst.dydownloader.model.CardType;
import com.hhst.dydownloader.model.ResourceItem;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ResourceStateInstrumentedTest {
  @Test
  public void recreationKeepsResourcesAndFinishReleasesScreenCache() {
    AtomicReference<String> screenKey = new AtomicReference<>();
    AtomicReference<File> snapshot = new AtomicReference<>();
    try (ActivityScenario<ResourceActivity> scenario = ActivityScenario.launch(ResourceActivity.class)) {
      scenario.onActivity(activity -> {
        ResourceFragment fragment = ResourceFragment.newInstance(
            List.of(item("photo", CardType.PHOTO)), "Review", ResourceActivity.REFERRER_RESOURCE, null);
        activity.getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_container, fragment).commitNow();
        Bundle state = new Bundle();
        fragment.onSaveInstanceState(state);
        screenKey.set(state.getString("state_screen_key"));
        snapshot.set(new File(new File(activity.getCacheDir(), "resource-screen-snapshots"),
            state.getString("state_resource_snapshot") + ".json"));
        assertFalse(ResourceScreenStore.get(screenKey.get()).isEmpty());
      });
      scenario.recreate();
      scenario.onActivity(activity -> {
        RecyclerView list = activity.findViewById(R.id.recyclerView);
        assertEquals(1, list.getAdapter().getItemCount());
        assertTrue(snapshot.get().isFile());
      });
    }
    assertTrue(ResourceScreenStore.get(screenKey.get()).isEmpty());
    assertFalse(snapshot.get().exists());
  }

  @Test
  public void filteringKeepsSelectionCountsAndPersistedSnapshotConsistent() {
    ResourceItem photo = item("photo", CardType.PHOTO);
    ResourceItem video = item("video", CardType.VIDEO);
    try (ActivityScenario<ResourceActivity> scenario = ActivityScenario.launch(ResourceActivity.class)) {
      scenario.onActivity(activity -> {
        ResourceFragment fragment = ResourceFragment.newInstance(
            List.of(photo, video), "Review", ResourceActivity.REFERRER_RESOURCE, null);
        activity.getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_container, fragment).commitNow();
        MaterialButtonToggleGroup filters = activity.findViewById(R.id.resourceFilterGroup);
        filters.check(R.id.resourceFilterImages);
        fragment.onResourceSelectToggle(photo, 0);
        assertTrue(activity.findViewById(R.id.btnDownload).isEnabled());
        filters.check(R.id.resourceFilterVideos);
        assertFalse(activity.findViewById(R.id.btnDownload).isEnabled());
        activity.findViewById(R.id.btnToggleSelectAll).performClick();
        assertTrue(fragment.isSelected(video));
        activity.findViewById(R.id.btnToggleSelectAll).performClick();
        assertFalse(fragment.isSelected(video));
        assertTrue(fragment.isSelected(photo));

        Bundle state = new Bundle();
        fragment.onSaveInstanceState(state);
        List<ResourceItem> restored = ResourceScreenSnapshot.restore(
            new File(activity.getCacheDir(), "resource-screen-snapshots"),
            state.getString("state_resource_snapshot"));
        assertEquals(List.of(photo, video), restored);
      });
    }
  }

  @Test
  public void emptyFilteredPageStillPersistsAllResources() {
    ResourceItem photo = item("photo", CardType.PHOTO);
    try (ActivityScenario<ResourceActivity> scenario = ActivityScenario.launch(ResourceActivity.class)) {
      scenario.onActivity(activity -> {
        ResourceFragment fragment = ResourceFragment.newInstance(
            List.of(photo), "Review", ResourceActivity.REFERRER_RESOURCE, null);
        activity.getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_container, fragment).commitNow();
        ((MaterialButtonToggleGroup) activity.findViewById(R.id.resourceFilterGroup))
            .check(R.id.resourceFilterVideos);
        Bundle state = new Bundle();
        fragment.onSaveInstanceState(state);
        assertEquals(List.of(photo), ResourceScreenSnapshot.restore(
            new File(activity.getCacheDir(), "resource-screen-snapshots"),
            state.getString("state_resource_snapshot")));
      });
    }
  }

  private ResourceItem item(String key, CardType type) {
    return new ResourceItem(null, 0L, type.getIconResId(), key, type, 1L,
        0, true, "", null, key, List.of(), type == CardType.PHOTO, "");
  }
}
