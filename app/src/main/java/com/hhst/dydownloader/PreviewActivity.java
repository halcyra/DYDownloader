package com.hhst.dydownloader;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.hhst.dydownloader.util.StorageReferenceUtils;
import com.hhst.dydownloader.widget.AspectRatioVideoView;
import com.squareup.picasso.Picasso;
import io.getstream.photoview.PhotoView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PreviewActivity extends AppCompatActivity {
  public static final String EXTRA_TITLE = "title";
  public static final String EXTRA_TYPE = "type";
  public static final String EXTRA_SOURCE = "source";
  public static final String EXTRA_IMAGE_SOURCES = "image_sources";
  public static final String EXTRA_INITIAL_INDEX = "initial_index";
  private static final long CONTROLS_AUTO_HIDE_DELAY_MS = 2400L;
  private static final int SEEK_INTERVAL_MS = 5_000;
  private final Handler progressHandler = new Handler(Looper.getMainLooper());
  private Runnable progressRunnable;
  private Runnable hideControlsRunnable;
  private AspectRatioVideoView videoView;
  private AppCompatImageButton closeButton;
  private AppCompatImageButton playPauseButton;
  private View controls;
  private AppCompatSeekBar progressBar;
  private TextView currentTimeView;
  private int videoDuration;
  private int bufferedPosition;
  private boolean isScrubbing;
  private boolean videoPrepared;
  private boolean resumePlaybackOnResume;
  private int lastPlaybackPosition;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    androidx.activity.EdgeToEdge.enable(this);
    setContentView(R.layout.activity_preview);
    applyImmersiveMode();

    String type = getIntent().getStringExtra(EXTRA_TYPE);
    String source = getIntent().getStringExtra(EXTRA_SOURCE);

    View root = findViewById(R.id.previewRoot);
    ViewPager2 imagePager = findViewById(R.id.previewImagePager);
    videoView = findViewById(R.id.previewVideo);
    closeButton = findViewById(R.id.previewClose);
    playPauseButton = findViewById(R.id.previewPlayPause);
    AppCompatImageButton rewindButton = findViewById(R.id.previewRewind);
    AppCompatImageButton forwardButton = findViewById(R.id.previewForward);
    controls = findViewById(R.id.previewControls);
    progressBar = findViewById(R.id.previewProgressBar);
    currentTimeView = findViewById(R.id.previewCurrentTime);
    TextView durationTime = findViewById(R.id.previewDuration);

    setupInsets(root, closeButton, controls);
    closeButton.setOnClickListener(v -> finish());

    if ("video".equals(type)) {
      imagePager.setVisibility(View.GONE);
      videoView.setVisibility(View.VISIBLE);
      controls.setVisibility(View.VISIBLE);
      closeButton.setVisibility(View.VISIBLE);
      if (source != null && !source.isBlank()) {
        videoView.setVideoURI(resolveUri(source));
        videoView.setOnPreparedListener(
            mp -> {
              videoPrepared = true;
              mp.setLooping(true);
              mp.setOnBufferingUpdateListener(
                  (mediaPlayer, percent) -> {
                    bufferedPosition = videoDuration > 0 ? (videoDuration * percent / 100) : 0;
                    updateProgressViews(progressBar, videoView.getCurrentPosition());
                  });
              videoView.setVideoSize(mp.getVideoWidth(), mp.getVideoHeight());
              videoDuration = videoView.getDuration();
              if (!looksLikeRemote(source)) {
                bufferedPosition = videoDuration;
              }
              progressBar.setMax(Math.max(videoDuration, 1));
              progressBar.setProgress(0);
              progressBar.setSecondaryProgress(Math.max(bufferedPosition, 0));
              currentTimeView.setText(formatTime(0));
              durationTime.setText(formatTime(videoDuration));
              updateProgressViews(progressBar, 0);
              if (lastPlaybackPosition > 0) {
                videoView.seekTo(lastPlaybackPosition);
                currentTimeView.setText(formatTime(lastPlaybackPosition));
              }
              resumePlaybackOnResume = true;
              startVideoPlaybackIfNeeded();
            });
        View.OnClickListener togglePlayback =
            v -> {
              if (videoView.isPlaying()) {
                lastPlaybackPosition = Math.max(0, videoView.getCurrentPosition());
                resumePlaybackOnResume = false;
                videoView.pause();
              } else {
                resumePlaybackOnResume = true;
                videoView.start();
                lastPlaybackPosition = 0;
              }
              updatePlaybackUi(videoView, playPauseButton, controls, closeButton);
            };
        GestureDetector gestureDetector =
            new GestureDetector(
                this,
                new GestureDetector.SimpleOnGestureListener() {
                  @Override
                  public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                    toggleControlsVisibility(controls, closeButton, videoView);
                    return true;
                  }

                  @Override
                  public boolean onDoubleTap(@NonNull MotionEvent e) {
                    togglePlayback.onClick(videoView);
                    return true;
                  }
                });
        videoView.setOnTouchListener(
            (v, event) -> {
              gestureDetector.onTouchEvent(event);
              if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
              }
              return true;
            });
        playPauseButton.setOnClickListener(
            v -> {
              togglePlayback.onClick(v);
              showControlsTemporarily(controls, closeButton, videoView);
            });
        rewindButton.setOnClickListener(
            v -> {
              videoView.seekTo(Math.max(0, videoView.getCurrentPosition() - SEEK_INTERVAL_MS));
              showControlsTemporarily(controls, closeButton, videoView);
            });
        forwardButton.setOnClickListener(
            v -> {
              videoView.seekTo(
                  Math.min(
                      videoView.getDuration(), videoView.getCurrentPosition() + SEEK_INTERVAL_MS));
              showControlsTemporarily(controls, closeButton, videoView);
            });
        progressBar.setOnSeekBarChangeListener(
            new SeekBar.OnSeekBarChangeListener() {
              @Override
              public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || videoDuration <= 0) {
                  return;
                }
                int position = Math.max(0, Math.min(videoDuration, progress));
                videoView.seekTo(position);
                currentTimeView.setText(formatTime(position));
              }

              @Override
              public void onStartTrackingTouch(SeekBar seekBar) {
                if (videoDuration <= 0) {
                  return;
                }
                isScrubbing = true;
                cancelControlsAutoHide();
              }

              @Override
              public void onStopTrackingTouch(SeekBar seekBar) {
                if (videoDuration <= 0) {
                  return;
                }
                int position = Math.max(0, Math.min(videoDuration, seekBar.getProgress()));
                videoView.seekTo(position);
                currentTimeView.setText(formatTime(position));
                isScrubbing = false;
                showControlsTemporarily(controls, closeButton, videoView);
              }
            });
      }
    } else {
      videoView.setVisibility(View.GONE);
      imagePager.setVisibility(View.VISIBLE);
      controls.setVisibility(View.GONE);
      closeButton.setVisibility(View.VISIBLE);
      ArrayList<String> imageSources = getIntent().getStringArrayListExtra(EXTRA_IMAGE_SOURCES);
      List<String> sources = new ArrayList<>();
      if (imageSources != null) {
        for (String candidate : imageSources) {
          if (candidate != null && !candidate.isBlank()) {
            sources.add(candidate);
          }
        }
      }
      if (sources.isEmpty() && source != null && !source.isBlank()) {
        sources.add(source);
      }
      if (sources.isEmpty()) {
        sources.add("");
      }
      imagePager.setAdapter(new ImagePagerAdapter(sources));
      int initialIndex = getIntent().getIntExtra(EXTRA_INITIAL_INDEX, 0);
      imagePager.setCurrentItem(Math.max(0, Math.min(initialIndex, sources.size() - 1)), false);
    }
  }

  private void applyImmersiveMode() {
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    hideSystemBars();
  }

  private void hideSystemBars() {
    WindowInsetsControllerCompat controller =
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
    controller.setSystemBarsBehavior(
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    controller.hide(WindowInsetsCompat.Type.systemBars());
  }

  private void setupInsets(View root, View closeButton, View controls) {
    if (root == null) {
      return;
    }
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          var safeInsets =
              insets.getInsetsIgnoringVisibility(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());

          if (closeButton != null
              && closeButton.getLayoutParams() instanceof ViewGroup.MarginLayoutParams closeLp) {
            closeLp.topMargin = safeInsets.top + dpToPx(12);
            closeLp.rightMargin = safeInsets.right + dpToPx(12);
            closeButton.setLayoutParams(closeLp);
          }

          if (controls != null
              && controls.getLayoutParams() instanceof ViewGroup.MarginLayoutParams controlsLp) {
            controlsLp.leftMargin = 0;
            controlsLp.rightMargin = 0;
            controlsLp.bottomMargin = 0;
            controls.setLayoutParams(controlsLp);
            controls.setPadding(safeInsets.left, 0, safeInsets.right, safeInsets.bottom);
          }
          return insets;
        });
  }

  private int dpToPx(int dp) {
    return Math.round(dp * getResources().getDisplayMetrics().density);
  }

  private void updatePlaybackUi(
      AspectRatioVideoView videoView,
      AppCompatImageButton playPauseButton,
      View controls,
      View closeButton) {
    if (videoView.isPlaying()) {
      playPauseButton.setImageResource(R.drawable.ic_pause);
      showControlsTemporarily(controls, closeButton, videoView);
    } else {
      playPauseButton.setImageResource(R.drawable.ic_play);
      cancelControlsAutoHide();
      setChromeVisibility(controls, closeButton, true);
    }
  }

  private void toggleControlsVisibility(
      View controls, View closeButton, AspectRatioVideoView videoView) {
    if (controls.getVisibility() == View.VISIBLE) {
      cancelControlsAutoHide();
      setChromeVisibility(controls, closeButton, false);
      return;
    }
    showControlsTemporarily(controls, closeButton, videoView);
  }

  private void showControlsTemporarily(
      View controls, View closeButton, AspectRatioVideoView videoView) {
    setChromeVisibility(controls, closeButton, true);
    if (videoView.isPlaying()) {
      scheduleControlsAutoHide(controls, closeButton);
    } else {
      cancelControlsAutoHide();
    }
  }

  private void setChromeVisibility(View controls, View closeButton, boolean visible) {
    animateVisibility(controls, visible);
    animateVisibility(closeButton, visible);
  }

  private void animateVisibility(View view, boolean visible) {
    if (view == null) {
      return;
    }
    view.animate().cancel();
    if (visible) {
      if (view.getVisibility() != View.VISIBLE) {
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
      }
      view.animate().alpha(1f).setDuration(180L).start();
      return;
    }
    if (view.getVisibility() != View.VISIBLE) {
      return;
    }
    view.animate()
        .alpha(0f)
        .setDuration(150L)
        .withEndAction(
            () -> {
              view.setVisibility(View.GONE);
              view.setAlpha(1f);
            })
        .start();
  }

  private void scheduleControlsAutoHide(View controls, View closeButton) {
    cancelControlsAutoHide();
    hideControlsRunnable = () -> setChromeVisibility(controls, closeButton, false);
    progressHandler.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_DELAY_MS);
  }

  private void cancelControlsAutoHide() {
    if (hideControlsRunnable != null) {
      progressHandler.removeCallbacks(hideControlsRunnable);
      hideControlsRunnable = null;
    }
  }

  private Uri resolveUri(String source) {
    if (looksLikeRemote(source)) {
      return Uri.parse(source);
    }
    return StorageReferenceUtils.parseReference(source);
  }

  private boolean looksLikeRemote(String source) {
    return source.startsWith("http://") || source.startsWith("https://");
  }

  private void startVideoPlaybackIfNeeded() {
    if (videoView == null || !videoPrepared || !resumePlaybackOnResume || !hasWindowFocus()) {
      return;
    }
    if (lastPlaybackPosition > 0) {
      videoView.seekTo(lastPlaybackPosition);
      currentTimeView.setText(formatTime(lastPlaybackPosition));
      lastPlaybackPosition = 0;
    }
    videoView.start();
    updatePlaybackUi(videoView, playPauseButton, controls, closeButton);
    startProgressUpdates(videoView, progressBar, currentTimeView);
  }

  private void pauseVideoPlaybackForLifecycle() {
    if (videoView == null || !videoPrepared) {
      return;
    }
    resumePlaybackOnResume = videoView.isPlaying();
    lastPlaybackPosition = Math.max(0, videoView.getCurrentPosition());
    if (videoView.isPlaying()) {
      videoView.pause();
    }
    cancelControlsAutoHide();
    stopProgressUpdates();
  }

  private void stopProgressUpdates() {
    if (progressRunnable != null) {
      progressHandler.removeCallbacks(progressRunnable);
      progressRunnable = null;
    }
  }

  private void startProgressUpdates(
      AspectRatioVideoView videoView, AppCompatSeekBar progressBar, TextView currentTime) {
    if (progressRunnable != null) {
      progressHandler.removeCallbacks(progressRunnable);
    }
    progressRunnable =
        new Runnable() {
          @Override
          public void run() {
            if (videoView.isShown()) {
              int position = videoView.getCurrentPosition();
              updateProgressViews(progressBar, position);
              currentTime.setText(formatTime(position));
              progressHandler.postDelayed(this, 400);
            }
          }
        };
    progressHandler.post(progressRunnable);
  }

  private void updateProgressViews(AppCompatSeekBar progressBar, int position) {
    if (progressBar == null || isScrubbing) {
      return;
    }
    progressBar.setMax(Math.max(videoDuration, 1));
    progressBar.setProgress(Math.max(0, Math.min(videoDuration, position)));
    progressBar.setSecondaryProgress(Math.max(0, Math.min(videoDuration, bufferedPosition)));
  }

  private String formatTime(int millis) {
    int totalSeconds = Math.max(0, millis / 1000);
    int minutes = totalSeconds / 60;
    int seconds = totalSeconds % 60;
    return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds);
  }

  @Override
  protected void onResume() {
    super.onResume();
    hideSystemBars();
    startVideoPlaybackIfNeeded();
  }

  @Override
  protected void onPause() {
    pauseVideoPlaybackForLifecycle();
    super.onPause();
  }

  @Override
  protected void onStop() {
    stopProgressUpdates();
    super.onStop();
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) {
      hideSystemBars();
      startVideoPlaybackIfNeeded();
    }
  }

  @Override
  protected void onDestroy() {
    cancelControlsAutoHide();
    stopProgressUpdates();
    if (videoView != null) {
      videoView.stopPlayback();
    }
    super.onDestroy();
  }

  private final class ImagePagerAdapter
      extends RecyclerView.Adapter<ImagePagerAdapter.ImagePageViewHolder> {
    private final List<String> sources;

    private ImagePagerAdapter(List<String> sources) {
      this.sources = sources == null ? List.of() : List.copyOf(sources);
    }

    @NonNull
    @Override
    public ImagePageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      View view =
          LayoutInflater.from(parent.getContext())
              .inflate(R.layout.item_preview_image_page, parent, false);
      return new ImagePageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImagePageViewHolder holder, int position) {
      String itemSource = sources.get(position);
      if (itemSource == null || itemSource.isBlank()) {
        holder.imageView.setImageResource(R.drawable.ic_placeholder);
        return;
      }
      if (looksLikeRemote(itemSource)) {
        Picasso.get()
            .load(itemSource)
            .placeholder(R.drawable.ic_placeholder)
            .into(holder.imageView);
      } else if (StorageReferenceUtils.isContentReference(itemSource)) {
        Picasso.get()
            .load(Uri.parse(itemSource))
            .placeholder(R.drawable.ic_placeholder)
            .into(holder.imageView);
      } else {
        Picasso.get()
            .load(new File(itemSource))
            .placeholder(R.drawable.ic_placeholder)
            .into(holder.imageView);
      }
    }

    @Override
    public int getItemCount() {
      return sources.size();
    }

    final class ImagePageViewHolder extends RecyclerView.ViewHolder {
      final PhotoView imageView;

      private ImagePageViewHolder(@NonNull View itemView) {
        super(itemView);
        imageView = itemView.findViewById(R.id.previewPageImage);
        imageView.setOnViewTapListener((view, x, y) -> finish());
      }
    }
  }
}
