package com.hhst.dydownloader;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.hhst.dydownloader.adapter.CardAdapter;
import com.hhst.dydownloader.db.AppDatabase;
import com.hhst.dydownloader.db.ResourceDao;
import com.hhst.dydownloader.db.ResourceEntity;
import com.hhst.dydownloader.model.CardType;
import com.hhst.dydownloader.model.ResourceItem;
import com.squareup.picasso.Picasso;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class HomeFragment extends Fragment implements CardAdapter.OnCardClickListener {
  private static final String STATE_SEARCH_TEXT = "state_search_text";
  private static final String STATE_SEARCH_MODE = "state_search_mode";
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final AtomicBoolean destroyed = new AtomicBoolean(false);
  private List<ResourceItem> fullList = new ArrayList<>();
  private CardAdapter adapter;
  private MaterialToolbar toolbar;
  private View searchContainer;
  private TextInputEditText searchInput;
  private View searchCloseButton;
  private String searchText = "";
  private CardType filterType = null;
  private int sortType = 0;
  private boolean searchMode;
  private AppDatabase database;
  private ResourceDao resourceDao;
  private ExecutorService dbExecutor;
  private int dataLoadGeneration = 0;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_home, container, false);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    dbExecutor =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "dy-home-db");
              t.setDaemon(true);
              return t;
            });
    if (savedInstanceState != null) {
      searchText = savedInstanceState.getString(STATE_SEARCH_TEXT, "");
      searchMode = savedInstanceState.getBoolean(STATE_SEARCH_MODE, false);
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString(STATE_SEARCH_TEXT, searchText);
    outState.putBoolean(STATE_SEARCH_MODE, searchMode);
  }

  @Override
  public void onDestroy() {
    destroyed.set(true);
    if (dbExecutor != null) {
      dbExecutor.shutdownNow();
      dbExecutor = null;
    }
    super.onDestroy();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    database = AppDatabase.getDatabase(requireContext());
    resourceDao = database.resourceDao();
    sortType = AppPrefs.getHomeSort(requireContext());
    filterType = AppPrefs.getHomeFilter(requireContext());
    loadFromDbAsync(false);

    toolbar = view.findViewById(R.id.toolbar);
    searchContainer = view.findViewById(R.id.searchContainer);
    searchInput = view.findViewById(R.id.searchInput);
    searchCloseButton = view.findViewById(R.id.searchCloseButton);

    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
        toolbar,
        (v, insets) -> {
          var systemBars =
              insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
          int searchBottomSafe = searchMode ? dpToPx(8) : 0;
          v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), searchBottomSafe);
          if (searchContainer != null
              && searchContainer.getLayoutParams() instanceof ViewGroup.MarginLayoutParams params) {
            params.topMargin = systemBars.top + dpToPx(8);
            searchContainer.setLayoutParams(params);
          }
          return insets;
        });

    setupToolbar();
    setupSearchUi();

    RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
    recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
    adapter = new CardAdapter(this);
    recyclerView.setAdapter(adapter);

    OnBackPressedCallback backPressedCallback =
        new OnBackPressedCallback(true) {
          @Override
          public void handleOnBackPressed() {
            if (searchMode) {
              exitSearchMode(false);
              return;
            }
            if (adapter != null && adapter.isSelectionMode()) {
              exitSelectionMode();
              return;
            }
            if (getActivity() instanceof MainActivity mainActivity) {
              mainActivity.handleRootBackPressed();
            }
          }
        };
    requireActivity()
        .getOnBackPressedDispatcher()
        .addCallback(getViewLifecycleOwner(), backPressedCallback);

    updateDisplayList();
  }

  @Override
  public void onResume() {
    super.onResume();
    loadFromDbAsync(true);
  }

  private void loadFromDbAsync(boolean refreshOnly) {
    ExecutorService exec = dbExecutor;
    if (resourceDao == null || exec == null) {
      return;
    }
    int generation = ++dataLoadGeneration;
    exec.execute(
        () -> {
          if (database != null) {
            database.runInTransaction(
                () -> ResourceActions.consolidateTopLevelResources(resourceDao));
          } else {
            ResourceActions.consolidateTopLevelResources(resourceDao);
          }
          List<ResourceItem> items =
              resourceDao.getByParentId(0).stream()
                  .map(ResourceEntity::toResourceItem)
                  .collect(Collectors.toList());
          mainHandler.post(
              () -> {
                if (destroyed.get() || generation != dataLoadGeneration || !isAdded()) {
                  return;
                }
                fullList = items;
                updateDisplayList();
                if (refreshOnly && adapter != null && adapter.isSelectionMode()) {
                  exitSelectionMode();
                }
              });
        });
  }

  private void setupToolbar() {
    toolbar.setNavigationIcon(R.drawable.ic_settings);
    toolbar.setNavigationOnClickListener(
        v -> startActivity(new Intent(requireContext(), SettingsActivity.class)));
    toolbar.setTitle("");
    toolbar.getMenu().clear();
    toolbar.inflateMenu(R.menu.toolbar_menu);
    toolbar.setOnMenuItemClickListener(
        item -> {
          int id = item.getItemId();
          if (id == R.id.action_search) {
            enterSearchMode();
            return true;
          }
          if (id == R.id.action_filter) {
            showFilterMenu();
            return true;
          }
          if (id == R.id.action_sort) {
            showSortMenu();
            return true;
          }
          if (id == R.id.action_delete) {
            enterSelectionMode();
            return true;
          }
          return false;
        });
    tintMenuIcons();
  }

  private void setupSearchUi() {
    if (searchInput != null) {
      searchInput.setText(searchText);
      searchInput.addTextChangedListener(
          new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
              searchText = s == null ? "" : s.toString().trim();
              updateDisplayList();
            }

            @Override
            public void afterTextChanged(Editable s) {}
          });
    }
    if (searchCloseButton != null) {
      searchCloseButton.setOnClickListener(
          v -> {
            if (getCurrentSearchQuery().isEmpty()) {
              exitSearchMode(false);
              return;
            }
            if (searchInput != null) {
              searchInput.setText("");
            } else {
              searchText = "";
              updateDisplayList();
            }
          });
    }
    if (searchMode) {
      enterSearchMode();
    } else {
      exitSearchMode(false);
    }
  }

  private void enterSelectionMode() {
    adapter.setSelectionMode(true);
    toolbar.setNavigationIcon(R.drawable.ic_close);
    toolbar.setNavigationOnClickListener(v -> exitSelectionMode());
    toolbar.setTitle(getString(R.string.home_selected_count, 0));
    toolbar.getMenu().clear();
    if (getActivity() instanceof MainActivity main) {
      main.setFabConfig(R.drawable.ic_delete, v -> confirmDelete());
    }
    tintMenuIcons();
  }

  private void exitSelectionMode() {
    adapter.setSelectionMode(false);
    setupToolbar();
    if (getActivity() instanceof MainActivity main) {
      main.setFabConfig(R.drawable.ic_download, null);
    }
  }

  private void enterSearchMode() {
    searchMode = true;
    toolbar.setVisibility(View.INVISIBLE);
    androidx.core.view.ViewCompat.requestApplyInsets(toolbar);
    if (searchContainer != null) {
      searchContainer.setVisibility(View.VISIBLE);
    }
    if (searchInput != null) {
      searchInput.setText(searchText);
      requestSearchInputFocus();
    }
  }

  private void exitSearchMode(boolean clearText) {
    searchMode = false;
    if (clearText) {
      searchText = "";
      if (searchInput != null) {
        searchInput.setText("");
      }
    }
    if (shouldRefreshDisplayAfterExit(clearText)) {
      updateDisplayList();
    }
    clearSearchInputFocus();
    toolbar.setVisibility(View.VISIBLE);
    androidx.core.view.ViewCompat.requestApplyInsets(toolbar);
    if (searchContainer != null) {
      searchContainer.setVisibility(View.GONE);
    }
  }

  boolean shouldRefreshDisplayAfterExit(boolean clearText) {
    return true;
  }

  @Override
  public void onSelectionChanged(int count) {
    if (adapter != null && adapter.isSelectionMode() && toolbar != null) {
      toolbar.setTitle(getString(R.string.home_selected_count, count));
    }
  }

  private void confirmDelete() {
    var selected = new ArrayList<>(adapter.getSelectedItems());
    if (selected.isEmpty()) {
      exitSelectionMode();
      return;
    }
    ExecutorService exec = dbExecutor;
    if (exec == null) {
      return;
    }
    exec.execute(
        () -> {
          boolean hasLocalFiles =
              selected.stream()
                  .map(item -> ResourceActions.resolveLocalMedia(resourceDao, item))
                  .anyMatch(ResourceActions.LocalMedia::canShare);
          mainHandler.post(
              () -> {
                if (destroyed.get() || !isAdded()) {
                  return;
                }
                showDeleteDialog(
                    R.string.dialog_batch_delete_title,
                    getResources()
                        .getQuantityString(
                            R.plurals.dialog_batch_delete_message,
                            selected.size(),
                            selected.size()),
                    hasLocalFiles,
                    deleteLocalFiles -> deleteItemsAsync(selected, deleteLocalFiles, true));
              });
        });
  }

  private void deleteItemsAsync(
      List<ResourceItem> items, boolean deleteLocalFiles, boolean exitSelection) {
    ExecutorService exec = dbExecutor;
    if (exec == null) {
      return;
    }
    List<ResourceItem> snapshot = new ArrayList<>(items);
    exec.execute(
        () -> {
          for (ResourceItem item : snapshot) {
            ResourceActions.deleteResourceItem(resourceDao, item, deleteLocalFiles);
          }
          mainHandler.post(
              () -> {
                if (destroyed.get() || !isAdded()) {
                  return;
                }
                loadFromDbAsync(true);
                if (exitSelection) {
                  exitSelectionMode();
                }
              });
        });
  }

  private void updateDisplayList() {
    String activeQuery =
        HomeSearchBehavior.effectiveQuery(
            searchMode, searchInput == null ? null : searchInput.getText(), searchText);
    Comparator<ResourceItem> comparator =
        switch (sortType) {
          case 1 -> Comparator.comparing(ResourceItem::createTime);
          case 2 -> Comparator.comparing(ResourceItem::text);
          case 3 -> Comparator.comparing(ResourceItem::text).reversed();
          default -> Comparator.comparing(ResourceItem::createTime).reversed();
        };

    List<ResourceItem> filtered =
        fullList.stream()
            .filter(
                item ->
                    item.text()
                        .toLowerCase(Locale.getDefault())
                        .contains(activeQuery.toLowerCase(Locale.getDefault())))
            .filter(item -> filterType == null || item.type() == filterType)
            .sorted(comparator)
            .collect(Collectors.toList());

    if (adapter != null) {
      adapter.submitList(filtered);
    }
  }

  private void showSortMenu() {
    View anchor = toolbar.findViewById(R.id.action_sort);
    PopupMenu popupMenu = new PopupMenu(requireContext(), anchor != null ? anchor : toolbar);
    popupMenu.getMenu().add(0, 100, 0, R.string.sort_newest);
    popupMenu.getMenu().add(0, 101, 1, R.string.sort_oldest);
    popupMenu.getMenu().add(0, 102, 2, R.string.sort_name_asc);
    popupMenu.getMenu().add(0, 103, 3, R.string.sort_name_desc);
    popupMenu.setOnMenuItemClickListener(
        item -> {
          if (item.getItemId() == 101) {
            sortType = 1;
          } else if (item.getItemId() == 102) {
            sortType = 2;
          } else if (item.getItemId() == 103) {
            sortType = 3;
          } else {
            sortType = 0;
          }
          AppPrefs.setHomeSort(requireContext(), sortType);
          updateDisplayList();
          return true;
        });
    popupMenu.show();
  }

  private void showFilterMenu() {
    View anchor = toolbar.findViewById(R.id.action_filter);
    PopupMenu popupMenu = new PopupMenu(requireContext(), anchor != null ? anchor : toolbar);
    popupMenu.getMenu().add(0, 200, 0, R.string.filter_all);
    popupMenu.getMenu().add(0, 201, 1, R.string.filter_album);
    popupMenu.getMenu().add(0, 202, 2, R.string.filter_collection);
    popupMenu.setOnMenuItemClickListener(
        item -> {
          if (item.getItemId() == 201) {
            filterType = CardType.ALBUM;
          } else if (item.getItemId() == 202) {
            filterType = CardType.COLLECTION;
          } else {
            filterType = null;
          }
          AppPrefs.setHomeFilter(requireContext(), filterType);
          updateDisplayList();
          return true;
        });
    popupMenu.show();
  }

  private void tintMenuIcons() {
    int color = MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface);
    for (int i = 0; i < toolbar.getMenu().size(); i++) {
      MenuItem menuItem = toolbar.getMenu().getItem(i);
      if (menuItem.getIcon() != null) {
        menuItem.getIcon().setTint(color);
      }
    }
  }

  private int dpToPx(int dp) {
    return (int)
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
  }

  private String getCurrentSearchQuery() {
    if (searchInput == null || searchInput.getText() == null) {
      return searchText == null ? "" : searchText.trim();
    }
    return searchInput.getText().toString().trim();
  }

  private void requestSearchInputFocus() {
    if (searchInput == null) {
      return;
    }
    searchInput.requestFocus();
    searchInput.post(
        () -> {
          var controller =
              WindowCompat.getInsetsController(requireActivity().getWindow(), searchInput);
          controller.show(androidx.core.view.WindowInsetsCompat.Type.ime());
        });
  }

  private void clearSearchInputFocus() {
    if (searchInput == null) {
      return;
    }
    searchInput.clearFocus();
    var controller = WindowCompat.getInsetsController(requireActivity().getWindow(), searchInput);
    controller.hide(androidx.core.view.WindowInsetsCompat.Type.ime());
  }

  @Override
  public void onCardClick(ResourceItem item, int position) {
    if (searchMode) {
      exitSearchMode(false);
    }
    if (item.id() == null || item.id() <= 0) {
      Toast.makeText(getContext(), R.string.contents_nonexistent, Toast.LENGTH_SHORT).show();
      return;
    }
    ExecutorService exec = dbExecutor;
    if (exec == null) {
      return;
    }
    long parentId = item.id();
    exec.execute(
        () -> {
          List<ResourceItem> children =
              resourceDao.getByParentId(parentId).stream()
                  .map(ResourceEntity::toResourceItem)
                  .collect(Collectors.toList());
          mainHandler.post(
              () -> {
                if (destroyed.get() || !isAdded()) {
                  return;
                }
                if (children.isEmpty()) {
                  Toast.makeText(getContext(), R.string.contents_nonexistent, Toast.LENGTH_SHORT)
                      .show();
                  return;
                }
                startActivity(
                    new Intent(requireContext(), ResourceActivity.class)
                        .putExtra("source_url", item.text())
                        .putExtra(ResourceActivity.EXTRA_RESOURCE_ID, item.id())
                        .putExtra(ResourceActivity.EXTRA_REFERRER, ResourceActivity.REFERRER_HOME));
              });
        });
  }

  @Override
  public void onCardLongClick(ResourceItem item, int position) {
    View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_card_detail, null);
    ImageView imageView = view.findViewById(R.id.dialogImage);
    if (item.thumbnailUrl() != null && !item.thumbnailUrl().isEmpty()) {
      Picasso.get()
          .load(item.thumbnailUrl())
          .placeholder(R.drawable.ic_placeholder)
          .error(item.imageResId())
          .into(imageView);
    } else {
      imageView.setImageResource(item.imageResId());
    }
    ((TextView) view.findViewById(R.id.dialogText)).setText(item.text());
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.dialog_detail_title)
        .setView(view)
        .setPositiveButton(R.string.dialog_ok, null)
        .show();
  }

  @Override
  public void onCardMoreClick(ResourceItem item, int position, View anchorView) {
    ExecutorService exec = dbExecutor;
    if (exec == null) {
      return;
    }
    exec.execute(
        () -> {
          ResourceActions.LocalMedia media = ResourceActions.resolveLocalMedia(resourceDao, item);
          mainHandler.post(
              () -> {
                if (destroyed.get() || !isAdded()) {
                  return;
                }
                PopupMenu popupMenu = new PopupMenu(requireContext(), anchorView);
                popupMenu.inflate(R.menu.item_more_actions);
                boolean canOpenDirectory = ResourceActions.hasDownloadDirectory(item);
                popupMenu
                    .getMenu()
                    .findItem(R.id.action_open_with)
                    .setEnabled(media.canOpenWith() || canOpenDirectory);
                popupMenu.getMenu().findItem(R.id.action_share).setEnabled(media.canShare());
                popupMenu.setOnMenuItemClickListener(
                    menuItem -> {
                      int id = menuItem.getItemId();
                      if (id == R.id.action_open_with) {
                        return media.canOpenWith()
                            ? ResourceActions.openWith(requireContext(), media)
                            : ResourceActions.openDownloadDirectory(requireContext(), item);
                      }
                      if (id == R.id.action_share) {
                        return ResourceActions.share(requireContext(), media);
                      }
                      if (id == R.id.action_delete_item) {
                        showDeleteDialog(
                            R.string.action_delete,
                            getString(R.string.dialog_delete_single_message),
                            media.canShare(),
                            deleteLocalFiles ->
                                deleteItemsAsync(List.of(item), deleteLocalFiles, false));
                        return true;
                      }
                      return false;
                    });
                popupMenu.show();
              });
        });
  }

  private void showDeleteDialog(
      int titleResId,
      CharSequence message,
      boolean showDeleteLocalFiles,
      Consumer<Boolean> onConfirm) {
    View dialogView =
        LayoutInflater.from(requireContext()).inflate(R.layout.dialog_delete_confirmation, null);
    TextView messageView = dialogView.findViewById(R.id.deleteDialogMessage);
    MaterialCheckBox deleteLocalFilesCheck = dialogView.findViewById(R.id.deleteLocalFilesCheck);
    messageView.setText(message);
    deleteLocalFilesCheck.setVisibility(showDeleteLocalFiles ? View.VISIBLE : View.GONE);
    deleteLocalFilesCheck.setChecked(false);

    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(titleResId)
        .setView(dialogView)
        .setPositiveButton(
            R.string.action_delete,
            (dialog, which) -> onConfirm.accept(deleteLocalFilesCheck.isChecked()))
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }
}
