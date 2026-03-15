package com.hhst.dydownloader.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.VideoView;
import androidx.annotation.Nullable;

public class AspectRatioVideoView extends VideoView {
  private int videoWidth;
  private int videoHeight;

  public AspectRatioVideoView(Context context) {
    super(context);
  }

  public AspectRatioVideoView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public AspectRatioVideoView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void setVideoSize(int width, int height) {
    videoWidth = width;
    videoHeight = height;
    requestLayout();
  }

  @Override
  public boolean performClick() {
    return super.performClick();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int width = getDefaultSize(videoWidth, widthMeasureSpec);
    int height = getDefaultSize(videoHeight, heightMeasureSpec);

    if (videoWidth > 0 && videoHeight > 0) {
      int measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
      int measuredHeight = MeasureSpec.getSize(heightMeasureSpec);
      float videoRatio = (float) videoWidth / (float) videoHeight;
      float viewRatio = (float) measuredWidth / (float) measuredHeight;

      if (videoRatio > viewRatio) {
        width = measuredWidth;
        height = (int) (measuredWidth / videoRatio);
      } else {
        height = measuredHeight;
        width = (int) (measuredHeight * videoRatio);
      }
    }

    setMeasuredDimension(width, height);
  }
}
