package com.hhst.dydownloader;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.hhst.dydownloader.adapter.ResourceAdapter;
import com.hhst.dydownloader.db.AppDatabase;
import com.hhst.dydownloader.db.ResourceDao;
import com.hhst.dydownloader.db.ResourceEntity;
import com.hhst.dydownloader.douyin.AwemeProfile;
import com.hhst.dydownloader.douyin.DouyinDownloader;
import com.hhst.dydownloader.douyin.MediaType;
import com.hhst.dydownloader.manager.DownloadQueue;
import com.hhst.dydownloader.manager.DownloadTask;
import com.hhst.dydownloader.model.CardType;
import com.hhst.dydownloader.model.Platform;
import com.hhst.dydownloader.model.ResourceItem;
import com.hhst.dydownloader.share.ResourceProbeRouter;
import com.hhst.dydownloader.share.ShareLinkResolver;
import com.hhst.dydownloader.share.ShareLinkResolver.LinkKind;
import com.hhst.dydownloader.tiktok.TikTokDownloader;
import com.hhst.dydownloader.util.MediaSourceUtils;
import com.hhst.dydownloader.util.StoragePathUtils;
import com.hhst.dydownloader.util.StorageReferenceUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ResourceFragment extends Fragment
    implements ResourceAdapter.OnResourceClickListener,
        ResourceAdapter.SelectionState,
        DownloadQueue.Listener {
  private static final String TAG = "ResourceFragment";
  private static final String STATE_SCREEN_KEY = "state_screen_key";
  private static final String STATE_TITLE = "state_title";
  private static final String STATE_REFERRER = "state_referrer";
  private static final String STATE_SHARE_LINK = "state_share_link";
  private static final String STATE_RESOURCE_ID = "state_resource_id";
  private static final String ARG_SCREEN_KEY = "screen_key";
  private static final String ARG_RESOURCE_ID = "resource_id";

  private final Set<String> selectedKeys = new HashSet<>();
  private final AtomicBoolean destroyed = new AtomicBoolean(false);
  private final Set<String> queuedKeys = new HashSet<>();
  private final Set<String> completedQueueKeys = new HashSet<>();
  private final Set<String> downloadedKeys = new HashSet<>();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private List<ResourceItem> resourceList = new ArrayList<>();
  private String title, referrer = ResourceActivity.REFERRER_RESOURCE;
  private String screenKey;
  private ResourceAdapter adapter;
  private View actionBar, btnToggleSelectAll, btnDownload, loadingProgress;
  private String shareLink;
  private long resourceId = -1L;
  private ExecutorService loadExecutor;
  private CompletableFuture<?> inFlightLoad;
  private int loadGeneration = 0;
  private int mediaStateGeneration = 0;
  private ExecutorService dbExecutor;
  private ResourceDao resourceDao;

  public static ResourceFragment newInstance(String title, String referrer, Long resourceId) {
    return newInstance(title, referrer, resourceId, null);
  }

  public static ResourceFragment newInstance(
      String title, String referrer, Long resourceId, String shareLink) {
    return newInstance(null, title, referrer, resourceId, shareLink);
  }

  public static ResourceFragment newInstance(
      List<ResourceItem> resourceList, String title, String referrer, Long resourceId) {
    return newInstance(resourceList, title, referrer, resourceId, null);
  }

  public static ResourceFragment newInstance(
      List<ResourceItem> resourceList,
      String title,
      String referrer,
      Long resourceId,
      String shareLink) {
    var fragment = new ResourceFragment();
    var args = new Bundle();
    if (resourceList != null && !resourceList.isEmpty()) {
      args.putString(ARG_SCREEN_KEY, ResourceScreenStore.put(resourceList));
    }
    args.putString("title", title);
    args.putString(ResourceActivity.EXTRA_REFERRER, referrer);
    args.putLong(ARG_RESOURCE_ID, resourceId != null ? resourceId : -1L);
    args.putString("share_link", shareLink);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (savedInstanceState != null) {
      screenKey = savedInstanceState.getString(STATE_SCREEN_KEY);
      title = savedInstanceState.getString(STATE_TITLE);
      referrer = savedInstanceState.getString(STATE_REFERRER, ResourceActivity.REFERRER_RESOURCE);
      shareLink = savedInstanceState.getString(STATE_SHARE_LINK);
      resourceId = savedInstanceState.getLong(STATE_RESOURCE_ID, -1L);
    } else if (getArguments() != null) {
      screenKey = getArguments().getString(ARG_SCREEN_KEY);
      title = getArguments().getString("title");
      referrer =
          getArguments()
              .getString(ResourceActivity.EXTRA_REFERRER, ResourceActivity.REFERRER_RESOURCE);
      shareLink = getArguments().getString("share_link");
      resourceId = getArguments().getLong(ARG_RESOURCE_ID, -1L);
    }
    resourceList = ResourceScreenStore.get(screenKey);
    loadExecutor =
        Executors.newFixedThreadPool(
            3,
            r -> {
              Thread t = new Thread(r, "dy-load");
              t.setDaemon(true);
              return t;
            });
    dbExecutor =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "dy-resource-db");
              t.setDaemon(true);
              return t;
            });
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString(STATE_SCREEN_KEY, screenKey);
    outState.putString(STATE_TITLE, title);
    outState.putString(STATE_REFERRER, referrer);
    outState.putString(STATE_SHARE_LINK, shareLink);
    outState.putLong(STATE_RESOURCE_ID, resourceId);
  }

  @Override
  public void onDestroy() {
    destroyed.set(true);
    if (inFlightLoad != null) {
      inFlightLoad.cancel(true);
      inFlightLoad = null;
    }
    if (loadExecutor != null) {
      loadExecutor.shutdownNow();
      loadExecutor = null;
    }
    if (dbExecutor != null) {
      dbExecutor.shutdownNow();
      dbExecutor = null;
    }
    super.onDestroy();
  }

  @Override
  public void onStart() {
    super.onStart();
    DownloadQueue.addListener(this);
  }

  @Override
  public void onStop() {
    DownloadQueue.removeListener(this);
    super.onStop();
  }

  @Override
  public void onQueueChanged(List<DownloadTask> tasks) {
    applyQueueState(tasks, false);
  }

  @Override
  public void onResume() {
    super.onResume();
    if (getActivity() instanceof ResourceActivity && title != null) {
      ((ResourceActivity) getActivity()).setResourceTitle(title);
    }
    refreshQueueState(true);
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_resource, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    resourceDao = AppDatabase.getDatabase(requireContext()).resourceDao();
    var recyclerView = (RecyclerView) view.findViewById(R.id.recyclerView);
    recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
    adapter = new ResourceAdapter(resourceList, this, this, isFromHome());
    recyclerView.setAdapter(adapter);

    actionBar = view.findViewById(R.id.resourceActionBar);
    btnToggleSelectAll = view.findViewById(R.id.btnToggleSelectAll);
    btnDownload = view.findViewById(R.id.btnDownload);
    loadingProgress = view.findViewById(R.id.loadingProgress);

    btnToggleSelectAll.setOnClickListener(v -> toggleSelectAll());
    btnDownload.setOnClickListener(v -> downloadSelection());
    refreshQueueState(true);

    if (shareLink != null && resourceList.isEmpty()) {
      startConcurrentLoading(shareLink);
    } else if (resourceId > 0 && resourceList.isEmpty()) {
      loadFromDatabaseAsync(resourceId);
    } else {
      loadingProgress.setVisibility(View.GONE);
    }
  }

  private void startConcurrentLoading(String text) {
    loadingProgress.setVisibility(View.VISIBLE);
    ExecutorService exec = loadExecutor;
    if (exec == null) {
      loadingProgress.setVisibility(View.GONE);
      return;
    }

    if (inFlightLoad != null) {
      inFlightLoad.cancel(true);
    }

    final int generation = ++loadGeneration;
    ResourceProbeRouter.Plan probePlan = ResourceProbeRouter.plan(text);
    if (!probePlan.supported()) {
      showLoadFailed(generation, getString(R.string.invalid_supported_link));
      return;
    }
    String cookie = AppPrefs.getCookie(requireContext(), probePlan.platform());
    List<CompletableFuture<ProbeResult>> probeFutures =
        createProbeFutures(text, probePlan, cookie, exec);
    AtomicBoolean resolved = new AtomicBoolean(false);
    AtomicInteger completed = new AtomicInteger(0);

    for (CompletableFuture<ProbeResult> probeFuture : probeFutures) {
      probeFuture.whenCompleteAsync(
          (result, throwable) -> {
            if (generation != loadGeneration || destroyed.get()) {
              return;
            }
            if (throwable != null) {
              Log.d(TAG, "Probe execution failed", throwable);
            }

            if (result != null && !result.profiles().isEmpty()) {
              if (resolved.compareAndSet(false, true)) {
                cancelOtherProbes(probeFutures, probeFuture);
                applyLoadedProfiles(result.profiles(), result.rootType(), generation);
              }
              return;
            }

            if (completed.incrementAndGet() != probeFutures.size() || resolved.get()) {
              return;
            }

            AggregatedProfiles aggregated = mergeProbeResults(probeFutures);
            if (aggregated.profiles().isEmpty()) {
              showLoadFailed(generation, getString(R.string.resource_no_works_found));
              return;
            }

            if (resolved.compareAndSet(false, true)) {
              applyLoadedProfiles(aggregated.profiles(), aggregated.rootType(), generation);
            }
          },
          exec);
    }

    inFlightLoad = CompletableFuture.allOf(probeFutures.toArray(CompletableFuture[]::new));
  }

  private List<CompletableFuture<ProbeResult>> createProbeFutures(
      String text, ResourceProbeRouter.Plan probePlan, String cookie, ExecutorService exec) {
    if (probePlan.platform() == Platform.TIKTOK) {
      TikTokDownloader downloader = new TikTokDownloader(cookie);
      return probePlan.probeKinds().stream()
          .map(kind -> CompletableFuture.supplyAsync(() -> probe(kind, text, downloader), exec))
          .collect(Collectors.toList());
    }
    DouyinDownloader downloader = new DouyinDownloader(cookie);
    return probePlan.probeKinds().stream()
        .map(kind -> CompletableFuture.supplyAsync(() -> probe(kind, text, downloader), exec))
        .collect(Collectors.toList());
  }

  private ProbeResult probe(LinkKind kind, String text, DouyinDownloader downloader) {
    return switch (kind) {
      case ACCOUNT -> probeAccount(text, downloader);
      case MIX -> probeMix(text, downloader);
      case WORK, UNKNOWN -> probeWork(text, downloader);
    };
  }

  private ProbeResult probe(LinkKind kind, String text, TikTokDownloader downloader) {
    return switch (kind) {
      case ACCOUNT -> probeAccount(text, downloader);
      case MIX -> probeMix(text, downloader);
      case WORK, UNKNOWN -> probeWork(text, downloader);
    };
  }

  private ProbeResult probeAccount(String text, DouyinDownloader downloader) {
    try {
      List<AwemeProfile> result = downloader.collectAccountWorksInfo(text);
      return new ProbeResult(deduplicateProfiles(result), CardType.COLLECTION);
    } catch (Exception e) {
      Log.d(TAG, "Account probe failed for " + text, e);
      return ProbeResult.empty(CardType.COLLECTION);
    }
  }

  private ProbeResult probeMix(String text, DouyinDownloader downloader) {
    try {
      List<AwemeProfile> result = downloader.collectMixWorksInfo(text);
      return new ProbeResult(deduplicateProfiles(result), CardType.COLLECTION);
    } catch (Exception e) {
      Log.d(TAG, "Mix probe failed for " + text, e);
      return ProbeResult.empty(CardType.COLLECTION);
    }
  }

  private ProbeResult probeWork(String text, DouyinDownloader downloader) {
    try {
      AwemeProfile result = downloader.collectWorkInfo(text);
      return new ProbeResult(List.of(result), CardType.ALBUM);
    } catch (Exception e) {
      Log.d(TAG, "Single work probe failed for " + text, e);
      return ProbeResult.empty(CardType.ALBUM);
    }
  }

  private ProbeResult probeAccount(String text, TikTokDownloader downloader) {
    try {
      List<AwemeProfile> result = downloader.collectAccountWorksInfo(text);
      return new ProbeResult(deduplicateProfiles(result), CardType.COLLECTION);
    } catch (Exception e) {
      Log.d(TAG, "TikTok account probe failed for " + text, e);
      return ProbeResult.empty(CardType.COLLECTION);
    }
  }

  private ProbeResult probeMix(String text, TikTokDownloader downloader) {
    try {
      List<AwemeProfile> result = downloader.collectMixWorksInfo(text);
      return new ProbeResult(deduplicateProfiles(result), CardType.COLLECTION);
    } catch (Exception e) {
      Log.d(TAG, "TikTok mix probe failed for " + text, e);
      return ProbeResult.empty(CardType.COLLECTION);
    }
  }

  private ProbeResult probeWork(String text, TikTokDownloader downloader) {
    try {
      AwemeProfile result = downloader.collectWorkInfo(text);
      return new ProbeResult(List.of(result), CardType.ALBUM);
    } catch (Exception e) {
      Log.d(TAG, "TikTok single work probe failed for " + text, e);
      return ProbeResult.empty(CardType.ALBUM);
    }
  }

  private void cancelOtherProbes(
      List<CompletableFuture<ProbeResult>> probeFutures,
      CompletableFuture<ProbeResult> winnerFuture) {
    for (CompletableFuture<ProbeResult> future : probeFutures) {
      if (future != winnerFuture) {
        future.cancel(true);
      }
    }
  }

  private AggregatedProfiles mergeProbeResults(List<CompletableFuture<ProbeResult>> probeFutures) {
    List<AwemeProfile> merged = new ArrayList<>();
    CardType rootType = CardType.ALBUM;
    Set<String> seenIds = new HashSet<>();
    for (CompletableFuture<ProbeResult> future : probeFutures) {
      ProbeResult result = future.getNow(null);
      if (result == null || result.profiles().isEmpty()) {
        continue;
      }
      if (result.rootType() == CardType.COLLECTION) {
        rootType = CardType.COLLECTION;
      }
      for (AwemeProfile profile : result.profiles()) {
        String profileKey =
            profile == null ? "" : profile.platform().name() + ":" + profile.awemeId();
        if (profile != null && seenIds.add(profileKey)) {
          merged.add(profile);
        }
      }
    }
    return new AggregatedProfiles(merged, rootType);
  }

  private List<AwemeProfile> deduplicateProfiles(List<AwemeProfile> profiles) {
    if (profiles == null || profiles.isEmpty()) {
      return List.of();
    }
    List<AwemeProfile> deduplicated = new ArrayList<>(profiles.size());
    Set<String> seenIds = new HashSet<>();
    for (AwemeProfile profile : profiles) {
      if (profile == null) {
        continue;
      }
      String profileKey = profile.platform().name() + ":" + profile.awemeId();
      if (seenIds.add(profileKey)) {
        deduplicated.add(profile);
      }
    }
    return deduplicated;
  }

  private void applyLoadedProfiles(
      List<AwemeProfile> allProfiles, CardType rootType, int generation) {
    if (allProfiles == null || allProfiles.isEmpty()) {
      showLoadFailed(generation, getString(R.string.resource_no_works_found));
      return;
    }

    String resultTitle = resolveLoadedTitle(allProfiles);
    String downloadGroupDir = resolveDownloadGroupDir(allProfiles, resultTitle);

    List<ResourceItem> items = new ArrayList<>(allProfiles.size());
    for (AwemeProfile profile : allProfiles) {
      String desc = profile.desc();
      if (desc == null || desc.isEmpty()) {
        desc = profile.awemeId();
      }
      List<ResourceItem> children = createChildItems(profile, desc, downloadGroupDir);
      boolean isLeaf = children.isEmpty();
      ResourceItem item =
          new ResourceItem(
              profile.platform(),
              null,
              -1L,
              rootType.getIconResId(),
              desc,
              rootType,
              profile.createTime() * 1000L,
              Math.max(1, children.size()),
              isLeaf,
              profile.thumbnailUrl(),
              isLeaf ? null : children,
              profile.awemeId(),
              profile.downloadUrls(),
              profile.mediaType() == MediaType.IMAGE,
              "",
              downloadGroupDir);
      items.add(item);
    }

    final String finalTitle = resultTitle;
    mainHandler.post(
        () -> {
          if (destroyed.get() || generation != loadGeneration || !isAdded() || getView() == null) {
            return;
          }
          inFlightLoad = null;
          loadingProgress.setVisibility(View.GONE);
          resourceList.clear();
          resourceList.addAll(items);
          title = finalTitle;

          selectedKeys.clear();
          shareLink = null;
          screenKey = ResourceScreenStore.replace(screenKey, resourceList);

          Bundle args = getArguments();
          if (args != null) {
            args.putString(ARG_SCREEN_KEY, screenKey);
            args.putString("title", title);
            args.putString("share_link", null);
          }

          if (getActivity() instanceof ResourceActivity) {
            ((ResourceActivity) getActivity()).setResourceTitle(title);
          }
          if (adapter != null) {
            adapter.submitList(resourceList);
          }
          refreshDownloadedKeysAsync();
          updateActionButtons();
        });
  }

  private String resolveLoadedTitle(List<AwemeProfile> allProfiles) {
    if (allProfiles == null || allProfiles.isEmpty()) {
      return getString(R.string.resource_collected_resources);
    }
    if (allProfiles.size() == 1) {
      String desc = allProfiles.get(0).desc();
      return desc != null && !desc.isEmpty() ? desc : getString(R.string.resource_work_detail);
    }
    if (resolveShareLinkKind() == LinkKind.MIX) {
      String collectionTitle = resolveCollectionTitle(allProfiles);
      if (!collectionTitle.isBlank()) {
        return collectionTitle;
      }
    }
    String commonNickname = resolveCommonAuthorNickname(allProfiles);
    return commonNickname.isBlank()
        ? getString(R.string.resource_collected_resources)
        : commonNickname;
  }

  private String resolveDownloadGroupDir(List<AwemeProfile> allProfiles, String fallbackTitle) {
    LinkKind shareKind = resolveShareLinkKind();
    if (shareKind == LinkKind.MIX) {
      return StoragePathUtils.joinSegments(resolveCollectionTitle(allProfiles, fallbackTitle));
    }
    if (shareKind == LinkKind.ACCOUNT) {
      return StoragePathUtils.joinSegments(resolveAccountTitle(allProfiles, fallbackTitle));
    }
    return "";
  }

  private LinkKind resolveShareLinkKind() {
    return ShareLinkResolver.resolve(shareLink).kind();
  }

  private String resolveCollectionTitle(List<AwemeProfile> allProfiles) {
    return resolveCollectionTitle(allProfiles, "");
  }

  private String resolveCollectionTitle(List<AwemeProfile> allProfiles, String fallbackTitle) {
    if (allProfiles != null) {
      for (AwemeProfile profile : allProfiles) {
        if (profile != null
            && profile.collectionTitle() != null
            && !profile.collectionTitle().isBlank()) {
          return profile.collectionTitle().trim();
        }
      }
    }
    return fallbackTitle == null ? "" : fallbackTitle.trim();
  }

  private String resolveAccountTitle(List<AwemeProfile> allProfiles, String fallbackTitle) {
    String commonNickname = resolveCommonAuthorNickname(allProfiles);
    if (!commonNickname.isBlank()) {
      return commonNickname;
    }
    return fallbackTitle == null ? "" : fallbackTitle.trim();
  }

  private String resolveCommonAuthorNickname(List<AwemeProfile> allProfiles) {
    if (allProfiles == null || allProfiles.isEmpty()) {
      return "";
    }
    String commonNickname = allProfiles.get(0).authorNickname();
    if (commonNickname == null || commonNickname.isBlank()) {
      return "";
    }
    for (AwemeProfile profile : allProfiles) {
      if (!Objects.equals(profile.authorNickname(), commonNickname)) {
        return "";
      }
    }
    return commonNickname.trim();
  }

  private void showLoadFailed(int generation, String message) {
    mainHandler.post(
        () -> {
          if (destroyed.get() || generation != loadGeneration || !isAdded() || getView() == null) {
            return;
          }
          inFlightLoad = null;
          loadingProgress.setVisibility(View.GONE);
          String safeMessage =
              message == null || message.isBlank()
                  ? getString(R.string.resource_no_works_found)
                  : message;
          Toast.makeText(
                  getContext(),
                  getString(R.string.resource_load_failed, safeMessage),
                  Toast.LENGTH_LONG)
              .show();
        });
  }

  private void loadFromDatabaseAsync(long targetResourceId) {
    ExecutorService exec = dbExecutor;
    if (resourceDao == null || exec == null) {
      loadingProgress.setVisibility(View.GONE);
      return;
    }

    loadingProgress.setVisibility(View.VISIBLE);
    final int generation = ++loadGeneration;
    exec.execute(
        () -> {
          try {
            ResourceEntity target = resourceDao.getById(targetResourceId);
            if (target == null) {
              showLoadFailed(generation, getString(R.string.contents_nonexistent));
              return;
            }

            List<ResourceItem> items;
            if (target.isLeaf) {
              items = List.of(target.toResourceItem());
            } else {
              items =
                  resourceDao.getByParentId(target.id).stream()
                      .map(ResourceEntity::toResourceItem)
                      .sorted(
                          Comparator.comparingInt(this::childDisplayPriority)
                              .thenComparing(
                                  Comparator.comparingLong(ResourceItem::createTime).reversed()))
                      .collect(Collectors.toList());
              if (items.isEmpty()) {
                items = List.of(target.toResourceItem());
              }
            }

            List<ResourceItem> finalItems = items;
            mainHandler.post(
                () -> {
                  if (destroyed.get()
                      || generation != loadGeneration
                      || !isAdded()
                      || getView() == null) {
                    return;
                  }
                  loadingProgress.setVisibility(View.GONE);
                  resourceList.clear();
                  resourceList.addAll(finalItems);
                  screenKey = ResourceScreenStore.replace(screenKey, resourceList);
                  Bundle args = getArguments();
                  if (args != null) {
                    args.putString(ARG_SCREEN_KEY, screenKey);
                  }
                  if (adapter != null) {
                    adapter.submitList(resourceList);
                  }
                  refreshDownloadedKeysAsync();
                  updateActionButtons();
                });
          } catch (Exception e) {
            Log.e(TAG, "Failed loading from database", e);
            showLoadFailed(generation, e.getMessage());
          }
        });
  }

  @Override
  public void onResourceClick(ResourceItem item, int position) {
    if (!(getActivity() instanceof ResourceActivity)) {
      return;
    }
    if (item == null) {
      return;
    }
    if (item.children() != null && !item.children().isEmpty()) {
      ((ResourceActivity) getActivity())
          .navigateToChildFragment(item.children(), item.text(), referrer, item.id());
      return;
    }
    if (!item.isLeaf()
        && item.imagePost()
        && item.downloadUrls() != null
        && !item.downloadUrls().isEmpty()) {
      List<ResourceItem> reconstructedChildren = createPhotoItems(item);
      if (!reconstructedChildren.isEmpty()) {
        ((ResourceActivity) getActivity())
            .navigateToChildFragment(reconstructedChildren, item.text(), referrer, item.id());
        return;
      }
    }
    if (shouldOpenPreview(item)) {
      openPreview(item);
      return;
    }
    if (item.id() != null && !item.isLeaf()) {
      ((ResourceActivity) getActivity()).navigateTo(item);
      return;
    }
    if (!item.isLeaf()) {
      Toast.makeText(getContext(), R.string.resource_download_to_view, Toast.LENGTH_SHORT).show();
    }
  }

  private void openPreview(ResourceItem item) {
    String source = resolvePreviewSource(item);
    if (source == null || source.isBlank()) {
      return;
    }
    boolean previewAsVideo = isVideoItem(item) || MediaSourceUtils.isLikelyVideoSource(source);
    String type = previewAsVideo ? "video" : "image";
    Intent intent =
        new Intent(requireContext(), PreviewActivity.class)
            .putExtra(PreviewActivity.EXTRA_TITLE, item.text())
            .putExtra(PreviewActivity.EXTRA_TYPE, type)
            .putExtra(PreviewActivity.EXTRA_SOURCE, source);
    if ("image".equals(type)) {
      ArrayList<String> imageSources = new ArrayList<>();
      int initialIndex = 0;
      for (ResourceItem candidate : resourceList) {
        if (candidate == null || candidate.type() != CardType.PHOTO) {
          continue;
        }
        String candidateSource = resolvePreviewSource(candidate);
        if (candidateSource == null || candidateSource.isBlank()) {
          continue;
        }
        if (MediaSourceUtils.isLikelyVideoSource(candidateSource)) {
          continue;
        }
        if (candidate.key().equals(item.key())) {
          initialIndex = imageSources.size();
        }
        imageSources.add(candidateSource);
      }
      if (!imageSources.isEmpty()) {
        intent.putStringArrayListExtra(PreviewActivity.EXTRA_IMAGE_SOURCES, imageSources);
        intent.putExtra(PreviewActivity.EXTRA_INITIAL_INDEX, initialIndex);
      }
    }
    startActivity(intent);
  }

  private String resolvePreviewSource(ResourceItem item) {
    if (item == null) {
      return "";
    }
    if (item.downloadPath() != null && !item.downloadPath().isBlank()) {
      return item.downloadPath();
    }
    if (item.downloadUrls() != null && !item.downloadUrls().isEmpty()) {
      return item.downloadUrls().get(0);
    }
    return item.thumbnailUrl();
  }

  private boolean shouldOpenPreview(ResourceItem item) {
    return item != null
        && item.isLeaf()
        && (item.type() == CardType.PHOTO || item.type() == CardType.VIDEO);
  }

  private boolean isVideoItem(ResourceItem item) {
    return item != null && item.type() == CardType.VIDEO;
  }

  private List<ResourceItem> createChildItems(
      AwemeProfile profile, String parentTitle, String storageDir) {
    if (profile.mediaType() == MediaType.IMAGE) {
      return createPhotoItems(profile, parentTitle, storageDir);
    }
    return createVideoItems(profile, parentTitle, storageDir);
  }

  private ResourceItem createVideoItem(
      AwemeProfile profile, String parentTitle, String storageDir) {
    return new ResourceItem(
        profile.platform(),
        null,
        -1L,
        CardType.VIDEO.getIconResId(),
        parentTitle,
        CardType.VIDEO,
        profile.createTime() * 1000L,
        0,
        true,
        profile.thumbnailUrl(),
        null,
        profile.awemeId() + "#video",
        profile.downloadUrls(),
        false,
        "",
        storageDir);
  }

  private List<ResourceItem> createVideoItems(
      AwemeProfile profile, String parentTitle, String storageDir) {
    List<ResourceItem> videoItems = new ArrayList<>(2);
    if (profile.thumbnailUrl() != null && !profile.thumbnailUrl().isBlank()) {
      videoItems.add(
          new ResourceItem(
              profile.platform(),
              null,
              -1L,
              CardType.PHOTO.getIconResId(),
              getString(R.string.resource_video_cover_label, parentTitle),
              CardType.PHOTO,
              profile.createTime() * 1000L,
              0,
              true,
              profile.thumbnailUrl(),
              null,
              profile.awemeId() + "#cover",
              List.of(profile.thumbnailUrl()),
              true,
              "",
              storageDir));
    }
    if (profile.downloadUrls() != null && !profile.downloadUrls().isEmpty()) {
      videoItems.add(createVideoItem(profile, parentTitle, storageDir));
    }
    return videoItems;
  }

  private List<ResourceItem> createPhotoItems(
      AwemeProfile profile, String parentTitle, String storageDir) {
    List<ResourceItem> photoItems = new ArrayList<>();
    int total = Math.max(profile.thumbnailUrls().size(), profile.downloadUrls().size());
    for (int i = 0; i < total; i++) {
      String thumbnailUrl =
          i < profile.thumbnailUrls().size() ? profile.thumbnailUrls().get(i) : "";
      MediaType mediaType = profile.mediaTypeAt(i);
      String photoDesc = getString(R.string.resource_photo_label, parentTitle, i + 1);
      String photoSourceKey = profile.awemeId() + "#photo:" + (i + 1);
      String liveSourceKey = profile.awemeId() + "#live:" + (i + 1);

      java.util.List<String> downloadUrls = java.util.List.of();
      if (i < profile.downloadUrls().size()) {
        String url = profile.downloadUrls().get(i);
        if (url != null && !url.isBlank()) {
          downloadUrls = java.util.List.of(url);
        }
      }
      if (downloadUrls.isEmpty() && thumbnailUrl != null && !thumbnailUrl.isBlank()) {
        downloadUrls = java.util.List.of(thumbnailUrl);
      }

      if (mediaType == MediaType.VIDEO) {
        java.util.List<String> coverUrls = java.util.List.of();
        if (thumbnailUrl != null && !thumbnailUrl.isBlank()) {
          coverUrls = java.util.List.of(thumbnailUrl);
        }

        photoItems.add(
            new ResourceItem(
                profile.platform(),
                null,
                -1L,
                CardType.PHOTO.getIconResId(),
                getString(R.string.resource_video_cover_label, photoDesc),
                CardType.PHOTO,
                profile.createTime() * 1000L,
                0,
                true,
                thumbnailUrl,
                null,
                photoSourceKey,
                coverUrls,
                true,
                "",
                storageDir));

        photoItems.add(
            new ResourceItem(
                profile.platform(),
                null,
                -1L,
                CardType.VIDEO.getIconResId(),
                photoDesc,
                CardType.VIDEO,
                profile.createTime() * 1000L,
                0,
                true,
                thumbnailUrl,
                null,
                liveSourceKey,
                downloadUrls,
                false,
                "",
                storageDir));
        continue;
      }

      photoItems.add(
          new ResourceItem(
              profile.platform(),
              null,
              -1L,
              CardType.PHOTO.getIconResId(),
              photoDesc,
              CardType.PHOTO,
              profile.createTime() * 1000L,
              0,
              true,
              thumbnailUrl,
              null,
              photoSourceKey,
              downloadUrls,
              true,
              "",
              storageDir));
    }
    return photoItems;
  }

  private List<ResourceItem> createPhotoItems(ResourceItem parentItem) {
    if (parentItem == null
        || parentItem.type() == CardType.PHOTO
        || parentItem.downloadUrls() == null
        || parentItem.downloadUrls().isEmpty()) {
      return List.of();
    }
    List<ResourceItem> photoItems = new ArrayList<>();
    List<String> urls = parentItem.downloadUrls();
    String storageDir = parentItem.storageDir();
    for (int i = 0; i < urls.size(); i++) {
      String photoUrl = urls.get(i);
      String photoDesc = getString(R.string.resource_photo_label, parentItem.text(), i + 1);
      boolean livePhoto = MediaSourceUtils.isLikelyVideoSource(photoUrl);
      String photoSourceKey = parentItem.sourceKey() + "#photo:" + (i + 1);
      String liveSourceKey = parentItem.sourceKey() + "#live:" + (i + 1);
      String thumbnail = parentItem.thumbnailUrl();

      if (livePhoto) {
        java.util.List<String> coverUrls = java.util.List.of();
        if (thumbnail != null && !thumbnail.isBlank()) {
          coverUrls = java.util.List.of(thumbnail);
        }

        photoItems.add(
            new ResourceItem(
                parentItem.platform(),
                null,
                -1L,
                CardType.PHOTO.getIconResId(),
                getString(R.string.resource_video_cover_label, photoDesc),
                CardType.PHOTO,
                parentItem.createTime(),
                0,
                true,
                thumbnail,
                null,
                photoSourceKey,
                coverUrls,
                true,
                "",
                storageDir));

        photoItems.add(
            new ResourceItem(
                parentItem.platform(),
                null,
                -1L,
                CardType.VIDEO.getIconResId(),
                photoDesc,
                CardType.VIDEO,
                parentItem.createTime(),
                0,
                true,
                thumbnail,
                null,
                liveSourceKey,
                java.util.List.of(photoUrl),
                false,
                "",
                storageDir));
        continue;
      }

      photoItems.add(
          new ResourceItem(
              parentItem.platform(),
              null,
              -1L,
              CardType.PHOTO.getIconResId(),
              photoDesc,
              CardType.PHOTO,
              parentItem.createTime(),
              0,
              true,
              photoUrl,
              null,
              photoSourceKey,
              java.util.List.of(photoUrl),
              true,
              "",
              storageDir));
    }
    return photoItems;
  }

  @Override
  public void onResourceLongClick(ResourceItem item, int position) {}

  @Override
  public void onResourceSelectToggle(ResourceItem item, int position) {
    if (isFromHome() || item == null) return;
    if (!selectedKeys.remove(item.key())) selectedKeys.add(item.key());
    if (adapter != null) adapter.notifyItemChanged(position);
    updateActionButtons();
  }

  @Override
  public boolean isSelected(ResourceItem item) {
    return item != null && selectedKeys.contains(item.key());
  }

  @Override
  public boolean isQueued(ResourceItem item) {
    return item != null && queuedKeys.contains(item.key());
  }

  @Override
  public boolean isDownloaded(ResourceItem item) {
    return item != null && downloadedKeys.contains(item.key());
  }

  private void toggleSelectAll() {
    var selectableCount = getSelectableCount();
    boolean shouldSelectAll = selectedKeys.size() < selectableCount;

    if (shouldSelectAll) {
      resourceList.forEach(
          item -> {
            if (item != null && !isQueued(item) && !isDownloaded(item)) {
              selectedKeys.add(item.key());
            }
          });
    } else {
      selectedKeys.clear();
    }
    if (adapter != null) adapter.refreshSelectionState();
    updateActionButtons();
  }

  private void downloadSelection() {
    if (isFromHome() || selectedKeys.isEmpty()) return;
    int skippedCount = 0;
    List<ResourceItem> selectedItems = new ArrayList<>();
    for (ResourceItem item : resourceList) {
      if (item == null || !selectedKeys.contains(item.key())) {
        continue;
      }
      if (isQueued(item) || isDownloaded(item)) {
        skippedCount++;
        continue;
      }
      selectedItems.add(prepareItemForDownload(item));
    }
    int added = DownloadQueue.addAll(selectedItems);
    selectedKeys.clear();
    refreshQueueState(true);
    if (skippedCount > 0) {
      Toast.makeText(
              getContext(),
              getResources()
                  .getQuantityString(
                      R.plurals.resource_download_skipped, skippedCount, skippedCount),
              Toast.LENGTH_SHORT)
          .show();
    } else if (added == 0) {
      Toast.makeText(getContext(), R.string.resource_already_in_downloads, Toast.LENGTH_SHORT)
          .show();
    }
  }

  private void refreshQueueState(boolean forceDownloadedRefresh) {
    applyQueueState(DownloadQueue.getTasks(), forceDownloadedRefresh);
  }

  private ResourceItem prepareItemForDownload(ResourceItem item) {
    if (item == null) {
      return null;
    }
    String relativeDir = resolveDownloadStorageDir(item);
    return new ResourceItem(
        item.platform(),
        item.id(),
        item.parentId(),
        item.imageResId(),
        item.text(),
        item.type(),
        item.createTime(),
        item.childrenNum(),
        item.isLeaf(),
        item.thumbnailUrl(),
        item.children(),
        item.sourceKey(),
        item.downloadUrls(),
        item.imagePost(),
        item.downloadPath(),
        relativeDir);
  }

  private String resolveDownloadStorageDir(ResourceItem item) {
    if (item == null) {
      return "";
    }
    if (item.storageDir() != null && !item.storageDir().isBlank()) {
      return item.storageDir();
    }
    return "";
  }

  private void applyQueueState(List<DownloadTask> tasks, boolean forceDownloadedRefresh) {
    Set<String> nextQueuedKeys = new HashSet<>();
    Set<String> nextCompletedKeys = new HashSet<>();
    if (tasks != null) {
      for (DownloadTask task : tasks) {
        if (task == null) {
          continue;
        }
        String resourceKey = task.getResourceKey();
        if (resourceKey == null || resourceKey.isBlank()) {
          continue;
        }
        switch (task.getStatus()) {
          case QUEUED, DOWNLOADING -> nextQueuedKeys.add(resourceKey);
          case COMPLETED -> nextCompletedKeys.add(resourceKey);
          case FAILED -> {
            // Failed items stay selectable and do not trigger downloaded-state refreshes.
          }
        }
      }
    }

    boolean queuedChanged = !queuedKeys.equals(nextQueuedKeys);
    boolean completedChanged = !completedQueueKeys.equals(nextCompletedKeys);

    if (queuedChanged) {
      queuedKeys.clear();
      queuedKeys.addAll(nextQueuedKeys);
      if (adapter != null) {
        adapter.refreshSelectionState();
      }
    }
    if (completedChanged) {
      completedQueueKeys.clear();
      completedQueueKeys.addAll(nextCompletedKeys);
    }

    if (queuedChanged || forceDownloadedRefresh) {
      updateActionButtons();
    }
    if (forceDownloadedRefresh || completedChanged) {
      refreshDownloadedKeysAsync();
    }
  }

  private void refreshDownloadedKeysAsync() {
    ExecutorService exec = dbExecutor;
    ResourceDao dao = resourceDao;
    if (exec == null) {
      return;
    }

    final int generation = ++mediaStateGeneration;
    final List<ResourceItem> snapshot = new ArrayList<>(resourceList);

    exec.execute(
        () -> {
          Set<String> nextDownloadedKeys = new HashSet<>();
          for (ResourceItem item : snapshot) {
            if (item == null) {
              continue;
            }
            if (dao != null) {
              if (ResourceActions.hasCompleteLocalMedia(dao, item)) {
                nextDownloadedKeys.add(item.key());
              }
              continue;
            }
            if (item.isLeaf() && item.downloadPath() != null && !item.downloadPath().isBlank()) {
              if (StorageReferenceUtils.exists(null, item.downloadPath())) {
                nextDownloadedKeys.add(item.key());
              }
            }
          }

          mainHandler.post(
              () -> {
                if (destroyed.get() || generation != mediaStateGeneration || !isAdded()) {
                  return;
                }
                downloadedKeys.clear();
                downloadedKeys.addAll(nextDownloadedKeys);
                if (adapter != null) {
                  adapter.refreshSelectionState();
                }
                updateActionButtons();
              });
        });
  }

  private void updateActionButtons() {
    if (isFromHome()) {
      if (actionBar != null) actionBar.setVisibility(View.GONE);
      return;
    }
    if (actionBar != null) actionBar.setVisibility(View.VISIBLE);
    var selectableCount = getSelectableCount();
    if (btnDownload != null) btnDownload.setEnabled(!selectedKeys.isEmpty());
    if (btnToggleSelectAll != null && btnToggleSelectAll instanceof android.widget.Button) {
      btnToggleSelectAll.setEnabled(selectableCount > 0);
      long actuallySelected =
          resourceList.stream()
              .filter(
                  item ->
                      item != null
                          && !isQueued(item)
                          && !isDownloaded(item)
                          && selectedKeys.contains(item.key()))
              .count();
      ((android.widget.Button) btnToggleSelectAll)
          .setText(
              actuallySelected < selectableCount ? R.string.select_all : R.string.unselect_all);
    }
  }

  private long getSelectableCount() {
    return resourceList.stream()
        .filter(item -> item != null && !isQueued(item) && !isDownloaded(item))
        .count();
  }

  private boolean isFromHome() {
    // REFERRER_HOME means coming from Home page - should hide selection UI
    // REFERRER_RESOURCE or null means coming from Load - should show selection UI
    return ResourceActivity.REFERRER_HOME.equals(referrer);
  }

  private int childDisplayPriority(ResourceItem item) {
    if (item == null) {
      return Integer.MAX_VALUE;
    }
    return switch (item.type()) {
      case PHOTO -> 0;
      case VIDEO -> 1;
      case ALBUM -> 2;
      case COLLECTION -> 3;
    };
  }

  private record ProbeResult(List<AwemeProfile> profiles, CardType rootType) {
    private ProbeResult {
      profiles = profiles == null ? List.of() : List.copyOf(profiles);
      rootType = rootType == null ? CardType.ALBUM : rootType;
    }

    static ProbeResult empty(CardType rootType) {
      return new ProbeResult(List.of(), rootType);
    }
  }

  private record AggregatedProfiles(List<AwemeProfile> profiles, CardType rootType) {
    private AggregatedProfiles {
      profiles = profiles == null ? List.of() : List.copyOf(profiles);
      rootType = rootType == null ? CardType.ALBUM : rootType;
    }
  }
}
