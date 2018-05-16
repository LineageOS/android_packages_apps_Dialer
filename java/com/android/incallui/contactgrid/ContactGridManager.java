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
import android.text.TextUtils;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewAnimator;
import com.android.contacts.common.compat.PhoneNumberUtilsCompat;
import com.android.contacts.common.lettertiles.LetterTileDrawable;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.util.DrawableConverter;
import com.android.incallui.call.DialerCall;
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
  private final TextView bottomTextView;
  private final Chronometer bottomTimerView;
  private int avatarSize;
  private boolean hideAvatar;
  private boolean showAnonymousAvatar;
  private boolean middleRowVisible = true;
  private boolean isTimerStarted;

  private PrimaryInfo primaryInfo = PrimaryInfo.createEmptyPrimaryInfo();
  private PrimaryCallState primaryCallState = PrimaryCallState.createEmptyPrimaryCallState();
  private final LetterTileDrawable letterTile;
  // Define as static due to recreate this object after resume
  private static long previousConnectTimeMillis = 0;
  private static long timeBase = 0;

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

    contactGridLayout = (View) contactNameTextView.getParent();
    letterTile = new LetterTileDrawable(context.getResources());
    isTimerStarted = false;
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
  }

  public void setCallState(PrimaryCallState primaryCallState) {
    this.primaryCallState = primaryCallState;
    updatePrimaryNameAndPhoto();
    updateBottomRow();
    updateTopRow();
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
        primaryInfo.photo != null && primaryInfo.photoType == ContactPhotoType.CONTACT;
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
    }

    if (info.icon == null) {
      connectionIconImageView.setVisibility(View.GONE);
    } else {
      connectionIconImageView.setVisibility(View.VISIBLE);
      connectionIconImageView.setImageDrawable(info.icon);
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
    if (TextUtils.isEmpty(primaryInfo.name)) {
      contactNameTextView.setText(null);
    } else {
      contactNameTextView.setText(
          primaryInfo.nameIsNumber
              ? PhoneNumberUtilsCompat.createTtsSpannable(primaryInfo.name)
              : primaryInfo.name);

      // Set direction of the name field
      int nameDirection = View.TEXT_DIRECTION_INHERIT;
      if (primaryInfo.nameIsNumber) {
        nameDirection = View.TEXT_DIRECTION_LTR;
      }
      contactNameTextView.setTextDirection(nameDirection);
    }

    if (avatarImageView != null) {
      if (hideAvatar) {
        avatarImageView.setVisibility(View.GONE);
      } else if (avatarSize > 0 && updateAvatarVisibility()) {
        boolean hasPhoto =
            primaryInfo.photo != null && primaryInfo.photoType == ContactPhotoType.CONTACT;
        // Contact has a photo, don't render a letter tile.
        if (hasPhoto) {
          avatarImageView.setBackground(
              DrawableConverter.getRoundedDrawable(
                  context, primaryInfo.photo, avatarSize, avatarSize));
          // Contact has a name, that isn't a number.
        } else {
          letterTile.setCanonicalDialerLetterTileDetails(
              primaryInfo.name,
              primaryInfo.contactInfoLookupKey,
              LetterTileDrawable.SHAPE_CIRCLE,
              LetterTileDrawable.getContactTypeFromPrimitives(
                  primaryCallState.isVoiceMailNumber,
                  primaryInfo.isSpam,
                  primaryCallState.isBusinessNumber,
                  primaryInfo.numberPresentation,
                  primaryCallState.isConference));
          // By invalidating the avatarImageView we force a redraw of the letter tile.
          // This is required to properly display the updated letter tile iconography based on the
          // contact type, because the background drawable reference cached in the view, and the
          // view is not aware of the mutations made to the background.
          avatarImageView.invalidate();
          avatarImageView.setBackground(letterTile);
        }
      }
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
        hdIconImageView.setVisibility(View.VISIBLE);
        hdIconImageView.setActivated(false);
        Drawable drawableCurrent = hdIconImageView.getDrawable().getCurrent();
        if (drawableCurrent instanceof Animatable && !((Animatable) drawableCurrent).isRunning()) {
          ((Animatable) drawableCurrent).start();
        }
      } else if (info.isHdIconVisible) {
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
    }

    if (info.isTimerVisible) {
      bottomTextSwitcher.setVisibility(View.VISIBLE);
      bottomTextSwitcher.setDisplayedChild(1);
      if (primaryCallState.connectTimeMillis > 0
          && previousConnectTimeMillis != primaryCallState.connectTimeMillis) {
          previousConnectTimeMillis = primaryCallState.connectTimeMillis;
        timeBase = primaryCallState.connectTimeMillis - System.currentTimeMillis()
            + SystemClock.elapsedRealtime();
      }
      if (timeBase > 0) {
        bottomTimerView.setBase(timeBase);
        if (!isTimerStarted) {
          LogUtil.i(
              "ContactGridManager.updateBottomRow",
              "starting timer with base: %d",
              bottomTimerView.getBase());
          bottomTimerView.start();
          isTimerStarted = true;
        }
      }
    } else {
      bottomTextSwitcher.setVisibility(View.GONE);
      bottomTextSwitcher.setDisplayedChild(0);
      bottomTimerView.stop();
      isTimerStarted = false;
      if (primaryCallState.state == DialerCall.State.DISCONNECTED) {
        previousConnectTimeMillis = 0;
        timeBase = 0;
      }
    }
  }
}
