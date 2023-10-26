/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dialer.calllogutils;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.provider.CallLog.Calls;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.res.ResourcesCompat;

import com.android.dialer.R;
import com.android.dialer.theme.base.Theme;
import com.android.dialer.theme.base.ThemeComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * View that draws one or more symbols for different types of calls (missed calls, outgoing etc).
 * The symbols are set up horizontally. If {@code useLargeIcons} is set in the xml attributes,
 * alternatively this view will only render one icon (Call Type, HD or Video).
 *
 * <p>As this view doesn't create subviews, it is better suited for ListView-recycling than a
 * regular LinearLayout using ImageViews.
 */
public class CallTypeIconsView extends View {

  private final boolean useLargeIcons;

  private static Resources resources;
  private static Resources largeResources;
  private final List<Integer> callTypes = new ArrayList<>(3);
  private boolean showVideo;
  private boolean showHd;
  private boolean showWifi;
  private boolean showAssistedDialed;
  private boolean showRtt;
  private int width;
  private int height;

  public CallTypeIconsView(Context context) {
    this(context, null);
  }

  public CallTypeIconsView(Context context, AttributeSet attrs) {
    super(context, attrs);
    TypedArray typedArray =
        context.getTheme().obtainStyledAttributes(attrs, R.styleable.CallTypeIconsView, 0, 0);
    useLargeIcons = typedArray.getBoolean(R.styleable.CallTypeIconsView_useLargeIcons, false);
    typedArray.recycle();
    if (resources == null) {
      resources = new Resources(context, false);
    }
    if (largeResources == null && useLargeIcons) {
      largeResources = new Resources(context, true);
    }
  }

  public void clear() {
    callTypes.clear();
    width = 0;
    height = 0;
    invalidate();
  }

  public void add(int callType) {
    callTypes.add(callType);

    final Drawable drawable = getCallTypeDrawable(callType);
    width += drawable.getIntrinsicWidth() + resources.iconMargin;
    height = Math.max(height, drawable.getIntrinsicWidth());
    invalidate();
  }

  /**
   * Determines whether the video call icon will be shown.
   *
   * @param showVideo True where the video icon should be shown.
   */
  public void setShowVideo(boolean showVideo) {
    this.showVideo = showVideo;
    if (showVideo) {
      width += resources.videoCall.getIntrinsicWidth() + resources.iconMargin;
      height = Math.max(height, resources.videoCall.getIntrinsicHeight());
      invalidate();
    }
  }

  /**
   * Determines if the video icon should be shown.
   *
   * @return True if the video icon should be shown.
   */
  public boolean isVideoShown() {
    return showVideo;
  }

  public void setShowHd(boolean showHd) {
    this.showHd = showHd;
    if (showHd) {
      width += resources.hdCall.getIntrinsicWidth() + resources.iconMargin;
      height = Math.max(height, resources.hdCall.getIntrinsicHeight());
      invalidate();
    }
  }

  public void setShowWifi(boolean showWifi) {
    this.showWifi = showWifi;
    if (showWifi) {
      width += resources.wifiCall.getIntrinsicWidth() + resources.iconMargin;
      height = Math.max(height, resources.wifiCall.getIntrinsicHeight());
      invalidate();
    }
  }

  public void setShowAssistedDialed(boolean showAssistedDialed) {
    this.showAssistedDialed = showAssistedDialed;
    if (showAssistedDialed) {
      width += resources.assistedDialedCall.getIntrinsicWidth() + resources.iconMargin;
      height = Math.max(height, resources.assistedDialedCall.getIntrinsicHeight());
      invalidate();
    }
  }

  public void setShowRtt(boolean showRtt) {
    this.showRtt = showRtt;
    if (showRtt) {
      width += resources.rttCall.getIntrinsicWidth() + resources.iconMargin;
      height = Math.max(height, resources.rttCall.getIntrinsicHeight());
      invalidate();
    }
  }

  public int getCount() {
    return callTypes.size();
  }

  private Drawable getCallTypeDrawable(int callType) {
    Resources resources = useLargeIcons ? largeResources : CallTypeIconsView.resources;
    switch (callType) {
      case Calls.INCOMING_TYPE:
      case Calls.ANSWERED_EXTERNALLY_TYPE:
        return resources.incoming;
      case Calls.OUTGOING_TYPE:
        return resources.outgoing;
      case Calls.VOICEMAIL_TYPE:
        return resources.voicemail;
      case Calls.BLOCKED_TYPE:
        return resources.blocked;
      case Calls.MISSED_TYPE:
      default:
        // It is possible for users to end up with calls with unknown call types in their
        // call history, possibly due to 3rd party call log implementations (e.g. to
        // distinguish between rejected and missed calls). Instead of crashing, just
        // assume that all unknown call types are missed calls.
        return resources.missed;
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(width, height);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    Resources resources = useLargeIcons ? largeResources : CallTypeIconsView.resources;
    int left = 0;
    // If we are using large icons, we should only show one icon (video, hd or call type) with
    // priority give to HD or Video. So we skip the call type icon if we plan to show them.

    if (!useLargeIcons || !(showHd || showVideo || showWifi || showAssistedDialed || showRtt)) {
      for (Integer callType : callTypes) {
        final Drawable drawable = getCallTypeDrawable(callType);
        final int right = left + drawable.getIntrinsicWidth();
        drawable.setBounds(left, 0, right, drawable.getIntrinsicHeight());
        drawable.draw(canvas);
        left = right + resources.iconMargin;
      }
    }

    // If showing the video call icon, draw it scaled appropriately.
    if (showVideo) {
      left = addDrawable(canvas, resources.videoCall, left) + resources.iconMargin;
    }
    // If showing HD call icon, draw it scaled appropriately.
    if (showHd) {
      left = addDrawable(canvas, resources.hdCall, left) + resources.iconMargin;
    }
    // If showing HD call icon, draw it scaled appropriately.
    if (showWifi) {
      left = addDrawable(canvas, resources.wifiCall, left) + resources.iconMargin;
    }
    // If showing assisted dial call icon, draw it scaled appropriately.
    if (showAssistedDialed) {
      left = addDrawable(canvas, resources.assistedDialedCall, left) + resources.iconMargin;
    }
    // If showing RTT call icon, draw it scaled appropriately.
    if (showRtt) {
      left = addDrawable(canvas, resources.rttCall, left) + resources.iconMargin;
    }
  }

  private int addDrawable(Canvas canvas, Drawable drawable, int left) {
    int right = left + drawable.getIntrinsicWidth();
    drawable.setBounds(left, 0, right, drawable.getIntrinsicHeight());
    drawable.draw(canvas);
    return right;
  }

  private static class Resources {

    // Drawable representing an incoming answered call.
    public final Drawable incoming;

    // Drawable representing an outgoing call.
    public final Drawable outgoing;

    // Drawable representing an incoming missed call.
    public final Drawable missed;

    // Drawable representing a voicemail.
    public final Drawable voicemail;

    // Drawable representing a blocked call.
    public final Drawable blocked;

    // Drawable representing a video call.
    final Drawable videoCall;

    // Drawable representing a hd call.
    final Drawable hdCall;

    // Drawable representing a WiFi call.
    final Drawable wifiCall;

    // Drawable representing an assisted dialed call.
    final Drawable assistedDialedCall;

    // Drawable representing a RTT call.
    final Drawable rttCall;

    /** The margin to use for icons. */
    final int iconMargin;

    /**
     * Configures the call icon drawables. A single white call arrow which points down and left is
     * used as a basis for all of the call arrow icons, applying rotation and colors as needed.
     *
     * <p>For each drawable we call mutate so that a new instance of the drawable is created. This
     * is done so that when we apply a color filter to the drawables, they are recolored across
     * dialer.
     *
     * @param context The current context.
     */
    public Resources(Context context, boolean largeIcons) {
      final android.content.res.Resources r = context.getResources();
      android.content.res.Resources.Theme contextTheme = context.getTheme();

      incoming = getBitmap(context, R.drawable.quantum_ic_call_received_vd_theme_24,
              r.getColor(R.color.answered_incoming_call, contextTheme), largeIcons);

      // Create a rotated instance of the call arrow for outgoing calls.
      outgoing = getBitmap(context, R.drawable.quantum_ic_call_made_vd_theme_24,
              r.getColor(R.color.answered_outgoing_call, contextTheme), largeIcons);

      // Need to make a copy of the arrow drawable, otherwise the same instance colored
      // above will be recolored here.
      missed = getBitmap(context, R.drawable.quantum_ic_call_missed_vd_theme_24,
              r.getColor(R.color.dialer_red, contextTheme), largeIcons);

      Theme theme = ThemeComponent.get(context).theme();
      int iconColor = theme.getColorIcon();

      voicemail = getBitmap(context, R.drawable.quantum_ic_voicemail_vd_theme_24, iconColor,
              largeIcons);
      blocked = getBitmap(context, R.drawable.quantum_ic_block_vd_theme_24, iconColor,
              largeIcons);
      videoCall = getBitmap(context, R.drawable.quantum_ic_videocam_vd_white_24, iconColor,
              largeIcons);
      hdCall = getBitmap(context, R.drawable.quantum_ic_hd_vd_theme_24, iconColor,
              largeIcons);
      wifiCall = getBitmap(context, R.drawable.quantum_ic_signal_wifi_4_bar_vd_theme_24,
              iconColor, largeIcons);
      assistedDialedCall = getBitmap(context, R.drawable.quantum_ic_language_vd_theme_24,
              iconColor, largeIcons);
      rttCall = getBitmap(context, R.drawable.quantum_ic_rtt_vd_theme_24, iconColor,
              largeIcons);

      iconMargin = largeIcons ? 0 : r.getDimensionPixelSize(R.dimen.call_log_icon_margin);
    }

    private Drawable getBitmap(Context context, @DrawableRes int iconId, @ColorInt int color,
                               boolean largeIcons) {
      final android.content.res.Resources r = context.getResources();
      Drawable drawable = largeIcons
              ? ResourcesCompat.getDrawable(r, iconId, context.getTheme())
              : getScaledBitmap(context, iconId);
      drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
      return drawable;
    }

    // Gets the icon, scaled to the height of the call type icons. This helps display all the
    // icons to be the same height, while preserving their width aspect ratio.
    private Drawable getScaledBitmap(Context context, int resourceId) {
      Drawable drawable = AppCompatResources.getDrawable(context, resourceId);

      int scaledHeight = context.getResources().getDimensionPixelSize(R.dimen.call_type_icon_size);
      int scaledWidth = (int) ((float) drawable.getIntrinsicWidth()
              * ((float) scaledHeight / (float) drawable.getIntrinsicHeight()));

      Bitmap icon = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(icon);
      drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
      drawable.draw(canvas);

      return new BitmapDrawable(context.getResources(), icon);
    }
  }
}
