package com.hhst.dydownloader.home;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 卡片四周的进度描边：0 为全暗，100 整圈点亮，其余按周长比例从左上角顺时针点亮。
 * 失败态用错误色整圈显示。
 */
public class CardProgressDrawable extends Drawable {

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path edgePath = new Path();
  private final Path drawPath = new Path();
  private final PathMeasure measure = new PathMeasure();
  private final RectF bounds = new RectF();
  private final int progressColor;
  private final int errorColor;
  private final float cornerRadius;

  private float progress;
  private boolean failed;
  private int alpha = 255;

  public CardProgressDrawable(
      int progressColor, int errorColor, float strokeWidth, float cornerRadius) {
    this.progressColor = progressColor;
    this.errorColor = errorColor;
    this.cornerRadius = cornerRadius;
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(strokeWidth);
    paint.setStrokeCap(Paint.Cap.ROUND);
    trackPaint.setStyle(Paint.Style.STROKE);
    trackPaint.setStrokeWidth(strokeWidth);
    trackPaint.setColor(
        Color.argb(
            56, Color.red(progressColor), Color.green(progressColor), Color.blue(progressColor)));
  }

  public void setProgress(float progress) {
    this.progress = Math.max(0f, Math.min(1f, progress));
    invalidateSelf();
  }

  public void setFailed(boolean failed) {
    this.failed = failed;
    invalidateSelf();
  }

  @Override
  public void draw(@NonNull Canvas canvas) {
    float length = measure.getLength();
    if (length <= 0f) {
      return;
    }
    if (!failed && progress < 1f) {
      canvas.drawPath(edgePath, trackPaint);
    }
    if (progress <= 0f && !failed) {
      return;
    }
    float drawn = failed ? length : length * progress;
    drawPath.reset();
    measure.getSegment(0f, drawn, drawPath, true);
    paint.setColor(failed ? errorColor : progressColor);
    paint.setAlpha(alpha);
    canvas.drawPath(drawPath, paint);
  }

  @Override
  protected void onBoundsChange(@NonNull android.graphics.Rect bounds) {
    float halfStroke = paint.getStrokeWidth() / 2f;
    this.bounds.set(
        bounds.left + halfStroke,
        bounds.top + halfStroke,
        bounds.right - halfStroke,
        bounds.bottom - halfStroke);
    float radius = Math.max(0f, cornerRadius - halfStroke);
    edgePath.reset();
    edgePath.addRoundRect(this.bounds, radius, radius, Path.Direction.CW);
    measure.setPath(edgePath, false);
  }

  @Override
  public void setAlpha(int alpha) {
    this.alpha = alpha;
    paint.setAlpha(alpha);
    trackPaint.setAlpha(Math.round(56 * alpha / 255f));
    invalidateSelf();
  }

  @Override
  public void setColorFilter(@Nullable ColorFilter colorFilter) {
    paint.setColorFilter(colorFilter);
    trackPaint.setColorFilter(colorFilter);
    invalidateSelf();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
