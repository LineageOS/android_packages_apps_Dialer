/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.VisibleForTesting;
import android.util.AttributeSet;
import android.view.View;
import com.android.dialer.compat.AppCompatConstants;
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
  private static Resources largeResouces;
  private List<Integer> callTypes = new ArrayList<>(3);
  private boolean showVideo;
  private boolean showHd;
  private boolean showWifi;
  private boolean showAssistedDialed;
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
    if (largeResouces == null && useLargeIcons) {
      largeResouces = new Resources(context, true);
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

  @VisibleForTesting
  public boolean isHdShown() {
    return showHd;
  }

  public void setShowWifi(boolean showWifi) {
    this.showWifi = showWifi;
    if (showWifi) {
      width += resources.wifiCall.getIntrinsicWidth() + resources.iconMargin;
      height = Math.max(height, resources.wifiCall.getIntrinsicHeight());
      invalidate();
    }
  }

  public boolean isAssistedDialedShown() {
    return showAssistedDialed;
  }

  public void setShowAssistedDialed(boolean showAssistedDialed) {
    this.showAssistedDialed = showAssistedDialed;
    if (showAssistedDialed) {
      width += resources.assistedDialedCall.getIntrinsicWidth() + resources.iconMargin;
      height = Math.max(height, resources.assistedDialedCall.getIntrinsicHeight());
      invalidate();
    }
  }

  public int getCount() {
    return callTypes.size();
  }

  public int getCallType(int index) {
    return callTypes.get(index);
  }

  private Drawable getCallTypeDrawable(int callType) {
    Resources resources = useLargeIcons ? largeResouces : CallTypeIconsView.resources;
    switch (callType) {
      case AppCompatConstants.CALLS_INCOMING_TYPE:
      case AppCompatConstants.CALLS_ANSWERED_EXTERNALLY_TYPE:
        return resources.incoming;
      case AppCompatConstants.CALLS_OUTGOING_TYPE:
        return resources.outgoing;
      case AppCompatConstants.CALLS_MISSED_TYPE:
        return resources.missed;
      case AppCompatConstants.CALLS_VOICEMAIL_TYPE:
        return resources.voicemail;
      case AppCompatConstants.CALLS_BLOCKED_TYPE:
        return resources.blocked;
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
    Resources resources = useLargeIcons ? largeResouces : CallTypeIconsView.resources;
    int left = 0;
    // If we are using large icons, we should only show one icon (video, hd or call type) with
    // priority give to HD or Video. So we skip the call type icon if we plan to show them.

    if (!useLargeIcons || !(showHd || showVideo || showWifi || showAssistedDialed)) {
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

    // Drawable respresenting an outgoing call.
    public final Drawable outgoing;

    // Drawable representing an incoming missed call.
    public final Drawable missed;

    // Drawable representing a voicemail.
    public final Drawable voicemail;

    // Drawable representing a blocked call.
    public final Drawable blocked;

    // Drawable repesenting a video call.
    final Drawable videoCall;

    // Drawable represeting a hd call.
    final Drawable hdCall;

    // Drawable representing a WiFi call.
    final Drawable wifiCall;

    // Drawable representing an assisted dialed call.
    final Drawable assistedDialedCall;

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

      int iconId = R.drawable.quantum_ic_call_received_white_24;
      Drawable drawable = largeIcons ? r.getDrawable(iconId) : getScaledBitmap(context, iconId);
      incoming = drawable.mutate();
      incoming.setColorFilter(r.getColor(R.color.answered_incoming_call),
          PorterDuff.Mode.MULTIPLY);

      // Create a rotated instance of the call arrow for outgoing calls.
      iconId = R.drawable.quantum_ic_call_made_white_24;
      drawable = largeIcons ? r.getDrawable(iconId) : getScaledBitmap(context, iconId);
      outgoing = drawable.mutate();
      outgoing.setColorFilter(r.getColor(R.color.answered_outgoing_call),
          PorterDuff.Mode.MULTIPLY);

      // Need to make a copy of the arrow drawable, otherwise the same instance colored
      // above will be recolored here.
      iconId = R.drawable.quantum_ic_call_missed_white_24;
      drawable = largeIcons ? r.getDrawable(iconId) : getScaledBitmap(context, iconId);
      missed = drawable.mutate();
      missed.setColorFilter(r.getColor(R.color.missed_call), PorterDuff.Mode.MULTIPLY);

      iconId = R.drawable.quantum_ic_voicemail_white_24;
      drawable = largeIcons ? r.getDrawable(iconId) : getScaledBitmap(context, iconId);
      voicemail = drawable.mutate();
      voicemail.setColorFilter(r.getColor(R.color.icon_color_grey), PorterDuff.Mode.MULTIPLY);

      iconId = R.drawable.quantum_ic_block_white_24;
      drawable = largeIcons ? r.getDrawable(iconId) : getScaledBitmap(context, iconId);
      blocked = drawable.mutate();
      blocked.setColorFilter(r.getColor(R.color.blocked_call), PorterDuff.Mode.MULTIPLY);

      iconId = R.drawable.quantum_ic_videocam_white_24;
      drawable = largeIcons ? r.getDrawable(iconId) : getScaledBitmap(context, iconId);
      videoCall = drawable.mutate();
      videoCall.setColorFilter(r.getColor(R.color.icon_color_grey), PorterDuff.Mode.MULTIPLY);

      iconId = R.drawable.quantum_ic_hd_white_24;
      drawable = largeIcons ? r.getDrawable(iconId) : getScaledBitmap(context, iconId);
      hdCall = drawable.mutate();
      hdCall.setColorFilter(r.getColor(R.color.icon_color_grey), PorterDuff.Mode.MULTIPLY);

      iconId = R.drawable.quantum_ic_signal_wifi_4_bar_white_24;
      drawable = largeIcons ? r.getDrawable(iconId) : getScaledBitmap(context, iconId);
      wifiCall = drawable.mutate();
      wifiCall.setColorFilter(r.getColor(R.color.icon_color_grey), PorterDuff.Mode.MULTIPLY);

      iconId = R.drawable.quantum_ic_language_white_24;
      drawable = largeIcons ? r.getDrawable(iconId) : getScaledBitmap(context, iconId);
      assistedDialedCall = drawable.mutate();
      assistedDialedCall.setColorFilter(
          r.getColor(R.color.icon_color_grey), PorterDuff.Mode.MULTIPLY);

      iconMargin = largeIcons ? 0 : r.getDimensionPixelSize(R.dimen.call_log_icon_margin);
    }

    // Gets the icon, scaled to the height of the call type icons. This helps display all the
    // icons to be the same height, while preserving their width aspect ratio.
    private Drawable getScaledBitmap(Context context, int resourceId) {
      Bitmap icon = BitmapFactory.decodeResource(context.getResources(), resourceId);
      int scaledHeight = context.getResources().getDimensionPixelSize(R.dimen.call_type_icon_size);
      int scaledWidth =
          (int) ((float) icon.getWidth() * ((float) scaledHeight / (float) icon.getHeight()));
      Bitmap scaledIcon = Bitmap.createScaledBitmap(icon, scaledWidth, scaledHeight, false);
      return new BitmapDrawable(context.getResources(), scaledIcon);
    }
  }
}
