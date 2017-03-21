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
import android.util.AttributeSet;
import android.view.View;
import com.android.contacts.common.util.BitmapUtil;
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

  private static Resources sResources;
  private static Resources sLargeResouces;
  private List<Integer> mCallTypes = new ArrayList<>(3);
  private boolean mShowVideo = false;
  private boolean mShowHd = false;
  private int mWidth;
  private int mHeight;

  public CallTypeIconsView(Context context) {
    this(context, null);
  }

  public CallTypeIconsView(Context context, AttributeSet attrs) {
    super(context, attrs);
    TypedArray typedArray =
        context.getTheme().obtainStyledAttributes(attrs, R.styleable.CallTypeIconsView, 0, 0);
    useLargeIcons = typedArray.getBoolean(R.styleable.CallTypeIconsView_useLargeIcons, false);
    typedArray.recycle();
    if (sResources == null) {
      sResources = new Resources(context, false);
    }
    if (sLargeResouces == null && useLargeIcons) {
      sLargeResouces = new Resources(context, true);
    }
  }

  public void clear() {
    mCallTypes.clear();
    mWidth = 0;
    mHeight = 0;
    invalidate();
  }

  public void add(int callType) {
    mCallTypes.add(callType);

    final Drawable drawable = getCallTypeDrawable(callType);
    mWidth += drawable.getIntrinsicWidth() + sResources.iconMargin;
    mHeight = Math.max(mHeight, drawable.getIntrinsicWidth());
    invalidate();
  }

  /**
   * Determines whether the video call icon will be shown.
   *
   * @param showVideo True where the video icon should be shown.
   */
  public void setShowVideo(boolean showVideo) {
    mShowVideo = showVideo;
    if (showVideo) {
      mWidth += sResources.videoCall.getIntrinsicWidth();
      mHeight = Math.max(mHeight, sResources.videoCall.getIntrinsicHeight());
      invalidate();
    }
  }

  /**
   * Determines if the video icon should be shown.
   *
   * @return True if the video icon should be shown.
   */
  public boolean isVideoShown() {
    return mShowVideo;
  }

  public void setShowHd(boolean showHd) {
    mShowHd = showHd;
    if (showHd) {
      mWidth += sResources.hdCall.getIntrinsicWidth();
      mHeight = Math.max(mHeight, sResources.hdCall.getIntrinsicHeight());
      invalidate();
    }
  }

  public int getCount() {
    return mCallTypes.size();
  }

  public int getCallType(int index) {
    return mCallTypes.get(index);
  }

  private Drawable getCallTypeDrawable(int callType) {
    Resources resources = useLargeIcons ? sLargeResouces : sResources;
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
    setMeasuredDimension(mWidth, mHeight);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    Resources resources = useLargeIcons ? sLargeResouces : sResources;
    int left = 0;
    // If we are using large icons, we should only show one icon (video, hd or call type) with
    // priority give to HD or Video. So we skip the call type icon if we plan to show them.
    if (!useLargeIcons || !(mShowHd || mShowVideo)) {
      for (Integer callType : mCallTypes) {
        final Drawable drawable = getCallTypeDrawable(callType);
        final int right = left + drawable.getIntrinsicWidth();
        drawable.setBounds(left, 0, right, drawable.getIntrinsicHeight());
        drawable.draw(canvas);
        left = right + resources.iconMargin;
      }
    }

    // If showing the video call icon, draw it scaled appropriately.
    if (mShowVideo) {
      final Drawable drawable = resources.videoCall;
      final int right = left + resources.videoCall.getIntrinsicWidth();
      drawable.setBounds(left, 0, right, resources.videoCall.getIntrinsicHeight());
      drawable.draw(canvas);
    }
    // If showing HD call icon, draw it scaled appropriately.
    if (mShowHd) {
      final Drawable drawable = resources.hdCall;
      final int right = left + resources.hdCall.getIntrinsicWidth();
      drawable.setBounds(left, 0, right, resources.hdCall.getIntrinsicHeight());
      drawable.draw(canvas);
    }
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
    public final Drawable videoCall;

    // Drawable represeting a hd call.
    public final Drawable hdCall;

    /** The margin to use for icons. */
    public final int iconMargin;

    /**
     * Configures the call icon drawables. A single white call arrow which points down and left is
     * used as a basis for all of the call arrow icons, applying rotation and colors as needed.
     *
     * @param context The current context.
     */
    public Resources(Context context, boolean largeIcons) {
      final android.content.res.Resources r = context.getResources();

      int iconId =
          largeIcons ? R.drawable.quantum_ic_call_received_white_24 : R.drawable.ic_call_arrow;
      incoming = r.getDrawable(iconId);
      incoming.setColorFilter(r.getColor(R.color.answered_call), PorterDuff.Mode.MULTIPLY);

      // Create a rotated instance of the call arrow for outgoing calls.
      outgoing = BitmapUtil.getRotatedDrawable(r, iconId, 180f);
      outgoing.setColorFilter(r.getColor(R.color.answered_call), PorterDuff.Mode.MULTIPLY);

      // Need to make a copy of the arrow drawable, otherwise the same instance colored
      // above will be recolored here.
      iconId = largeIcons ? R.drawable.quantum_ic_call_missed_white_24 : R.drawable.ic_call_arrow;
      missed = r.getDrawable(iconId).mutate();
      missed.setColorFilter(r.getColor(R.color.missed_call), PorterDuff.Mode.MULTIPLY);

      iconId = R.drawable.quantum_ic_voicemail_white_24;
      voicemail = largeIcons ? r.getDrawable(iconId) : getScaledBitmap(context, iconId);
      voicemail.setColorFilter(
          r.getColor(R.color.dialer_secondary_text_color), PorterDuff.Mode.MULTIPLY);

      iconId = R.drawable.quantum_ic_block_white_24;
      blocked = largeIcons ? r.getDrawable(iconId) : getScaledBitmap(context, iconId);
      blocked.setColorFilter(r.getColor(R.color.blocked_call), PorterDuff.Mode.MULTIPLY);

      iconId = R.drawable.quantum_ic_videocam_white_24;
      videoCall = largeIcons ? r.getDrawable(iconId) : getScaledBitmap(context, iconId);
      videoCall.setColorFilter(
          r.getColor(R.color.dialer_secondary_text_color), PorterDuff.Mode.MULTIPLY);

      iconId = R.drawable.quantum_ic_hd_white_24;
      hdCall = largeIcons ? r.getDrawable(iconId) : getScaledBitmap(context, iconId);
      hdCall.setColorFilter(
          r.getColor(R.color.dialer_secondary_text_color), PorterDuff.Mode.MULTIPLY);

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
