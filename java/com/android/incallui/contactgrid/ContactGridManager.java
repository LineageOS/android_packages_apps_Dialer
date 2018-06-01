/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.incallui.contactgrid;

import android.content.Context;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.telephony.PhoneNumberUtils;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.ViewAnimator;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.configprovider.ConfigProviderComponent;
import com.android.dialer.glidephotomanager.GlidePhotoManagerComponent;
import com.android.dialer.glidephotomanager.PhotoInfo;
import com.android.dialer.lettertile.LetterTileDrawable;
import com.android.dialer.util.DrawableConverter;
import com.android.dialer.widget.BidiTextView;
import com.android.incallui.incall.protocol.ContactPhotoType;
import com.android.incallui.incall.protocol.PrimaryCallState;
import com.android.incallui.incall.protocol.PrimaryInfo;
import java.util.List;

/** Utility to manage the Contact grid */
public class ContactGridManager {

  private final Context context;
  private final View contactGridLayout;

  // Row 0: Captain Holt        ON HOLD
  // Row 0: Calling...
  // Row 0: [Wi-Fi icon] Calling via Starbucks Wi-Fi
  // Row 0: [Wi-Fi icon] Starbucks Wi-Fi
  // Row 0: Hey Jake, pick up!
  private final ImageView connectionIconImageView;
  private final TextView statusTextView;

  // Row 1: Jake Peralta        [Contact photo]
  // Row 1: Walgreens
  // Row 1: +1 (650) 253-0000
  private final TextView contactNameTextView;
  @Nullable private ImageView avatarImageView;

  // Row 2: Mobile +1 (650) 253-0000
  // Row 2: [HD attempting icon]/[HD icon] 00:15
  // Row 2: Call ended
  // Row 2: Hanging up
  // Row 2: [Alert sign] Suspected spam caller
  // Row 2: Your emergency callback number: +1 (650) 253-0000
  private final ImageView workIconImageView;
  private final ImageView hdIconImageView;
  private final ImageView forwardIconImageView;
  private final TextView forwardedNumberView;
  private final ImageView spamIconImageView;
  private final ViewAnimator bottomTextSwitcher;
  private final BidiTextView bottomTextView;
  private final Chronometer bottomTimerView;
  private final Space topRowSpace;
  private int avatarSize;
  private boolean hideAvatar;
  private boolean showAnonymousAvatar;
  private boolean middleRowVisible = true;
  private boolean isTimerStarted;

  // Row in emergency call: This phone's number: +1 (650) 253-0000
  private final TextView deviceNumberTextView;
  private final View deviceNumberDivider;

  private PrimaryInfo primaryInfo = PrimaryInfo.empty();
  private PrimaryCallState primaryCallState = PrimaryCallState.empty();
  private final LetterTileDrawable letterTile;
  private boolean isInMultiWindowMode;

  public ContactGridManager(
      View view, @Nullable ImageView avatarImageView, int avatarSize, boolean showAnonymousAvatar) {
    context = view.getContext();
    Assert.isNotNull(context);

    this.avatarImageView = avatarImageView;
    this.avatarSize = avatarSize;
    this.showAnonymousAvatar = showAnonymousAvatar;
    connectionIconImageView = view.findViewById(R.id.contactgrid_connection_icon);
    statusTextView = view.findViewById(R.id.contactgrid_status_text);
    contactNameTextView = view.findViewById(R.id.contactgrid_contact_name);
    workIconImageView = view.findViewById(R.id.contactgrid_workIcon);
    hdIconImageView = view.findViewById(R.id.contactgrid_hdIcon);
    forwardIconImageView = view.findViewById(R.id.contactgrid_forwardIcon);
    forwardedNumberView = view.findViewById(R.id.contactgrid_forwardNumber);
    spamIconImageView = view.findViewById(R.id.contactgrid_spamIcon);
    bottomTextSwitcher = view.findViewById(R.id.contactgrid_bottom_text_switcher);
    bottomTextView = view.findViewById(R.id.contactgrid_bottom_text);
    bottomTimerView = view.findViewById(R.id.contactgrid_bottom_timer);
    topRowSpace = view.findViewById(R.id.contactgrid_top_row_space);

    contactGridLayout = (View) contactNameTextView.getParent();
    letterTile = new LetterTileDrawable(context.getResources());
    isTimerStarted = false;

    deviceNumberTextView = view.findViewById(R.id.contactgrid_device_number_text);
    deviceNumberDivider = view.findViewById(R.id.contactgrid_location_divider);
  }

  public void show() {
    contactGridLayout.setVisibility(View.VISIBLE);
  }

  public void hide() {
    contactGridLayout.setVisibility(View.GONE);
  }

  public void setAvatarHidden(boolean hide) {
    if (hide != hideAvatar) {
      hideAvatar = hide;
      updatePrimaryNameAndPhoto();
    }
  }

  public boolean isAvatarHidden() {
    return hideAvatar;
  }

  public View getContainerView() {
    return contactGridLayout;
  }

  public void setIsMiddleRowVisible(boolean isMiddleRowVisible) {
    if (middleRowVisible == isMiddleRowVisible) {
      return;
    }
    middleRowVisible = isMiddleRowVisible;

    contactNameTextView.setVisibility(isMiddleRowVisible ? View.VISIBLE : View.GONE);
    updateAvatarVisibility();
  }

  public void setPrimary(PrimaryInfo primaryInfo) {
    this.primaryInfo = primaryInfo;
    updatePrimaryNameAndPhoto();
    updateBottomRow();
    updateDeviceNumberRow();
  }

  public void setCallState(PrimaryCallState primaryCallState) {
    this.primaryCallState = primaryCallState;
    updatePrimaryNameAndPhoto();
    updateBottomRow();
    updateTopRow();
    updateDeviceNumberRow();
  }

  public void dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
    dispatchPopulateAccessibilityEvent(event, statusTextView);
    dispatchPopulateAccessibilityEvent(event, contactNameTextView);
    BottomRow.Info info = BottomRow.getInfo(context, primaryCallState, primaryInfo);
    if (info.shouldPopulateAccessibilityEvent) {
      dispatchPopulateAccessibilityEvent(event, bottomTextView);
    }
  }

  public void setAvatarImageView(
      @Nullable ImageView avatarImageView, int avatarSize, boolean showAnonymousAvatar) {
    this.avatarImageView = avatarImageView;
    this.avatarSize = avatarSize;
    this.showAnonymousAvatar = showAnonymousAvatar;
    updatePrimaryNameAndPhoto();
  }

  public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
    if (this.isInMultiWindowMode == isInMultiWindowMode) {
      return;
    }
    this.isInMultiWindowMode = isInMultiWindowMode;
    updateDeviceNumberRow();
  }

  private void dispatchPopulateAccessibilityEvent(AccessibilityEvent event, View view) {
    final List<CharSequence> eventText = event.getText();
    int size = eventText.size();
    view.dispatchPopulateAccessibilityEvent(event);
    // If no text added write null to keep relative position.
    if (size == eventText.size()) {
      eventText.add(null);
    }
  }

  private boolean updateAvatarVisibility() {
    if (avatarImageView == null) {
      return false;
    }

    if (!middleRowVisible) {
      avatarImageView.setVisibility(View.GONE);
      return false;
    }

    boolean hasPhoto =
        (primaryInfo.photo() != null || primaryInfo.photoUri() != null)
            && primaryInfo.photoType() == ContactPhotoType.CONTACT;
    if (!hasPhoto && !showAnonymousAvatar) {
      avatarImageView.setVisibility(View.GONE);
      return false;
    }

    avatarImageView.setVisibility(View.VISIBLE);
    return true;
  }

  /**
   * Updates row 0. For example:
   *
   * <ul>
   *   <li>Captain Holt ON HOLD
   *   <li>Calling...
   *   <li>[Wi-Fi icon] Calling via Starbucks Wi-Fi
   *   <li>[Wi-Fi icon] Starbucks Wi-Fi
   *   <li>Call from
   * </ul>
   */
  private void updateTopRow() {
    TopRow.Info info = TopRow.getInfo(context, primaryCallState, primaryInfo);
    if (TextUtils.isEmpty(info.label)) {
      // Use INVISIBLE here to prevent the rows below this one from moving up and down.
      statusTextView.setVisibility(View.INVISIBLE);
      statusTextView.setText(null);
    } else {
      statusTextView.setText(info.label);
      statusTextView.setVisibility(View.VISIBLE);
      statusTextView.setSingleLine(info.labelIsSingleLine);
      // Required to start the marquee
      // This will send a AccessibilityEvent.TYPE_VIEW_SELECTED, but has no observable effect on
      // talkback.
      statusTextView.setSelected(true);
    }

    if (info.icon == null) {
      connectionIconImageView.setVisibility(View.GONE);
      topRowSpace.setVisibility(View.GONE);
    } else {
      connectionIconImageView.setVisibility(View.VISIBLE);
      connectionIconImageView.setImageDrawable(info.icon);
      if (statusTextView.getVisibility() == View.VISIBLE
          && !TextUtils.isEmpty(statusTextView.getText())) {
        topRowSpace.setVisibility(View.VISIBLE);
      } else {
        topRowSpace.setVisibility(View.GONE);
      }
    }
  }

  /**
   * Updates row 1. For example:
   *
   * <ul>
   *   <li>Jake Peralta [Contact photo]
   *   <li>Walgreens
   *   <li>+1 (650) 253-0000
   * </ul>
   */
  private void updatePrimaryNameAndPhoto() {
    if (TextUtils.isEmpty(primaryInfo.name())) {
      contactNameTextView.setText(null);
    } else {
      contactNameTextView.setText(
          primaryInfo.nameIsNumber()
              ? PhoneNumberUtils.createTtsSpannable(primaryInfo.name())
              : primaryInfo.name());

      // Set direction of the name field
      int nameDirection = View.TEXT_DIRECTION_INHERIT;
      if (primaryInfo.nameIsNumber()) {
        nameDirection = View.TEXT_DIRECTION_LTR;
      }
      contactNameTextView.setTextDirection(nameDirection);
    }

    if (avatarImageView != null) {
      if (hideAvatar) {
        avatarImageView.setVisibility(View.GONE);
      } else if (avatarSize > 0 && updateAvatarVisibility()) {
        if (ConfigProviderComponent.get(context)
            .getConfigProvider()
            .getBoolean("enable_glide_photo", false)) {
          loadPhotoWithGlide();
        } else {
          loadPhotoWithLegacy();
        }
      }
    }
  }

  private void loadPhotoWithGlide() {
    PhotoInfo.Builder photoInfoBuilder =
        PhotoInfo.newBuilder()
            .setIsBusiness(primaryInfo.photoType() == ContactPhotoType.BUSINESS)
            .setIsVoicemail(primaryCallState.isVoiceMailNumber())
            .setIsSpam(primaryInfo.isSpam())
            .setIsConference(primaryCallState.isConference());

    // Contact has a name, that is a number.
    if (primaryInfo.nameIsNumber() && primaryInfo.number() != null) {
      photoInfoBuilder.setName(primaryInfo.number());
    } else if (primaryInfo.name() != null) {
      photoInfoBuilder.setName(primaryInfo.name());
    }

    if (primaryInfo.number() != null) {
      photoInfoBuilder.setFormattedNumber(primaryInfo.number());
    }

    if (primaryInfo.photoUri() != null) {
      photoInfoBuilder.setPhotoUri(primaryInfo.photoUri().toString());
    }

    if (primaryInfo.contactInfoLookupKey() != null) {
      photoInfoBuilder.setLookupUri(primaryInfo.contactInfoLookupKey());
    }

    GlidePhotoManagerComponent.get(context)
        .glidePhotoManager()
        .loadContactPhoto(avatarImageView, photoInfoBuilder.build());
  }

  private void loadPhotoWithLegacy() {
    boolean hasPhoto =
        primaryInfo.photo() != null && primaryInfo.photoType() == ContactPhotoType.CONTACT;
    if (hasPhoto) {
      avatarImageView.setBackground(
          DrawableConverter.getRoundedDrawable(
              context, primaryInfo.photo(), avatarSize, avatarSize));
    } else {
      // Contact has a photo, don't render a letter tile.
      letterTile.setCanonicalDialerLetterTileDetails(
          primaryInfo.name(),
          primaryInfo.contactInfoLookupKey(),
          LetterTileDrawable.SHAPE_CIRCLE,
          LetterTileDrawable.getContactTypeFromPrimitives(
              primaryCallState.isVoiceMailNumber(),
              primaryInfo.isSpam(),
              primaryCallState.isBusinessNumber(),
              primaryInfo.numberPresentation(),
              primaryCallState.isConference()));
      // By invalidating the avatarImageView we force a redraw of the letter tile.
      // This is required to properly display the updated letter tile iconography based on the
      // contact type, because the background drawable reference cached in the view, and the
      // view is not aware of the mutations made to the background.
      avatarImageView.invalidate();
      avatarImageView.setBackground(letterTile);
    }
  }
  /**
   * Updates row 2. For example:
   *
   * <ul>
   *   <li>Mobile +1 (650) 253-0000
   *   <li>[HD attempting icon]/[HD icon] 00:15
   *   <li>Call ended
   *   <li>Hanging up
   * </ul>
   */
  private void updateBottomRow() {
    BottomRow.Info info = BottomRow.getInfo(context, primaryCallState, primaryInfo);

    bottomTextView.setText(info.label);
    bottomTextView.setAllCaps(info.isSpamIconVisible);
    workIconImageView.setVisibility(info.isWorkIconVisible ? View.VISIBLE : View.GONE);
    if (hdIconImageView.getVisibility() == View.GONE) {
      if (info.isHdAttemptingIconVisible) {
        hdIconImageView.setImageResource(R.drawable.asd_hd_icon);
        hdIconImageView.setVisibility(View.VISIBLE);
        hdIconImageView.setActivated(false);
        Drawable drawableCurrent = hdIconImageView.getDrawable().getCurrent();
        if (drawableCurrent instanceof Animatable && !((Animatable) drawableCurrent).isRunning()) {
          ((Animatable) drawableCurrent).start();
        }
      } else if (info.isHdIconVisible) {
        hdIconImageView.setImageResource(R.drawable.asd_hd_icon);
        hdIconImageView.setVisibility(View.VISIBLE);
        hdIconImageView.setActivated(true);
      }
    } else if (info.isHdIconVisible) {
      hdIconImageView.setActivated(true);
    } else if (!info.isHdAttemptingIconVisible) {
      hdIconImageView.setVisibility(View.GONE);
    }
    spamIconImageView.setVisibility(info.isSpamIconVisible ? View.VISIBLE : View.GONE);

    if (info.isForwardIconVisible) {
      forwardIconImageView.setVisibility(View.VISIBLE);
      forwardedNumberView.setVisibility(View.VISIBLE);
      if (info.isTimerVisible) {
        bottomTextSwitcher.setVisibility(View.VISIBLE);
        if (ViewCompat.getLayoutDirection(contactGridLayout) == ViewCompat.LAYOUT_DIRECTION_LTR) {
          forwardedNumberView.setText(TextUtils.concat(info.label, " • "));
        } else {
          forwardedNumberView.setText(TextUtils.concat(" • ", info.label));
        }
      } else {
        bottomTextSwitcher.setVisibility(View.GONE);
        forwardedNumberView.setText(info.label);
      }
    } else {
      forwardIconImageView.setVisibility(View.GONE);
      forwardedNumberView.setVisibility(View.GONE);
      bottomTextSwitcher.setVisibility(View.VISIBLE);
    }

    if (info.isTimerVisible) {
      bottomTextSwitcher.setDisplayedChild(1);
      bottomTimerView.setBase(
          primaryCallState.connectTimeMillis()
              - System.currentTimeMillis()
              + SystemClock.elapsedRealtime());
      if (!isTimerStarted) {
        LogUtil.i(
            "ContactGridManager.updateBottomRow",
            "starting timer with base: %d",
            bottomTimerView.getBase());
        bottomTimerView.start();
        isTimerStarted = true;
      }
    } else {
      bottomTextSwitcher.setDisplayedChild(0);
      bottomTimerView.stop();
      isTimerStarted = false;
    }
  }

  private void updateDeviceNumberRow() {
    // It might not be available, e.g. in video call.
    if (deviceNumberTextView == null) {
      return;
    }
    if (isInMultiWindowMode || TextUtils.isEmpty(primaryCallState.callbackNumber())) {
      deviceNumberTextView.setVisibility(View.GONE);
      deviceNumberDivider.setVisibility(View.GONE);
      return;
    }
    // This is used for carriers like Project Fi to show the callback number for emergency calls.
    deviceNumberTextView.setText(
        context.getString(
            R.string.contact_grid_callback_number,
            BidiFormatter.getInstance()
                .unicodeWrap(primaryCallState.callbackNumber(), TextDirectionHeuristics.LTR)));
    deviceNumberTextView.setVisibility(View.VISIBLE);
    if (primaryInfo.shouldShowLocation()) {
      deviceNumberDivider.setVisibility(View.VISIBLE);
    }
  }
}
