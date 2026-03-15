package com.hhst.dydownloader;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.hhst.dydownloader.model.ResourceItem;
import java.util.List;

public class ResourceActivity extends AppCompatActivity {

  public static final String EXTRA_REFERRER = "referrer";
  public static final String EXTRA_RESOURCE_ID = "resource_id";
  public static final String REFERRER_HOME = "home";
  public static final String REFERRER_RESOURCE = "resource";

  private String referrer = REFERRER_RESOURCE;
  private TextView resourceName;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    androidx.activity.EdgeToEdge.enable(this);
    setContentView(R.layout.activity_resource);

    String sourceUrl = getIntent().getStringExtra("source_url");
    if (sourceUrl == null) sourceUrl = getString(R.string.resources);

    var toolbar = (androidx.appcompat.widget.Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

    resourceName = findViewById(R.id.resourceName);
    resourceName.setText(sourceUrl);

    var appBarLayout = findViewById(R.id.appBarLayout);
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.main_resource_layout),
        (v, insets) -> {
          var systemBars =
              insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
          v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
          appBarLayout.setPadding(
              0, systemBars.top, 0, 0); // Apply padding to AppBarLayout container
          return insets;
        });

    referrer = getIntent().getStringExtra(EXTRA_REFERRER);
    if (referrer == null) referrer = REFERRER_RESOURCE;

    if (savedInstanceState == null) {
      long resourceId = getIntent().getLongExtra(EXTRA_RESOURCE_ID, -1L);
      String shareLink = getIntent().getStringExtra("share_link");

      getSupportFragmentManager()
          .beginTransaction()
          .replace(
              R.id.fragment_container,
              ResourceFragment.newInstance(sourceUrl, referrer, resourceId, shareLink))
          .commit();
    }
  }

  public void setResourceTitle(String title) {
    if (resourceName != null) resourceName.setText(title);
  }

  public void navigateTo(ResourceItem item) {
    if (item.id() != null) {
      getSupportFragmentManager()
          .beginTransaction()
          .replace(
              R.id.fragment_container,
              ResourceFragment.newInstance(item.text(), referrer, item.id(), null))
          .addToBackStack(null)
          .commit();
    }
  }

  public void navigateToChildFragment(
      List<ResourceItem> children, String title, String referrer, Long resourceId) {
    getSupportFragmentManager()
        .beginTransaction()
        .replace(
            R.id.fragment_container,
            ResourceFragment.newInstance(children, title, referrer, resourceId))
        .addToBackStack(null)
        .commit();
  }
}
