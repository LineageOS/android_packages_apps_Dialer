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
package com.android.dialer.app.list;

import android.content.ClipData;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.net.Uri;
import android.provider.ContactsContract.PinnedPositions;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import com.android.contacts.common.MoreContactUtils;
import com.android.contacts.common.list.ContactEntry;
import com.android.contacts.common.list.ContactTileView;
import com.android.contacts.common.model.ContactLoader;
import com.android.dialer.app.R;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallSpecificAppData;
import com.android.dialer.callintent.SpeedDialContactType;
import com.android.dialer.contactphoto.ContactPhotoManager.DefaultImageRequest;
import com.android.dialer.lettertile.LetterTileDrawable;
import com.android.dialer.logging.InteractionEvent;
import com.android.dialer.logging.Logger;

/**
 * A light version of the {@link com.android.contacts.common.list.ContactTileView} that is used in
 * Dialtacts for frequently called contacts. Slightly different behavior from superclass when you
 * tap it, you want to call the frequently-called number for the contact, even if that is not the
 * default number for that contact. This abstract class is the super class to both the row and tile
 * view.
 */
public abstract class PhoneFavoriteTileView extends ContactTileView {

  // Constant to pass to the drag event so that the drag action only happens when a phone favorite
  // tile is long pressed.
  static final String DRAG_PHONE_FAVORITE_TILE = "PHONE_FAVORITE_TILE";
  private static final String TAG = PhoneFavoriteTileView.class.getSimpleName();
  // These parameters instruct the photo manager to display the default image/letter at 70% of
  // its normal size, and vertically offset upwards 12% towards the top of the letter tile, to
  // make room for the contact name and number label at the bottom of the image.
  private static final float DEFAULT_IMAGE_LETTER_OFFSET = -0.12f;
  private static final float DEFAULT_IMAGE_LETTER_SCALE = 0.70f;
  // Dummy clip data object that is attached to drag shadows so that text views
  // don't crash with an NPE if the drag shadow is released in their bounds
  private static final ClipData EMPTY_CLIP_DATA = ClipData.newPlainText("", "");
  /** View that contains the transparent shadow that is overlaid on top of the contact image. */
  private View shadowOverlay;
  /** Users' most frequent phone number. */
  private String phoneNumberString;

  private boolean isPinned;
  private boolean isStarred;
  private int position = -1;

  public PhoneFavoriteTileView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    shadowOverlay = findViewById(R.id.shadow_overlay);

    setOnLongClickListener(
        (v) -> {
          final PhoneFavoriteTileView view = (PhoneFavoriteTileView) v;
          // NOTE The drag shadow is handled in the ListView.
          view.startDragAndDrop(
              EMPTY_CLIP_DATA, new EmptyDragShadowBuilder(), DRAG_PHONE_FAVORITE_TILE, 0);
          return true;
        });
  }

  @Override
  public void loadFromContact(ContactEntry entry) {
    super.loadFromContact(entry);
    // Set phone number to null in case we're reusing the view.
    phoneNumberString = null;
    isPinned = (entry.pinned != PinnedPositions.UNPINNED);
    isStarred = entry.isFavorite;
    if (entry != null) {
      sendViewNotification(getContext(), entry.lookupUri);
      // Grab the phone-number to call directly. See {@link onClick()}.
      phoneNumberString = entry.phoneNumber;

      // If this is a blank entry, don't show anything. For this to truly look like an empty row
      // the entire ContactTileRow needs to be hidden.
      if (entry == ContactEntry.BLANK_ENTRY) {
        setVisibility(View.INVISIBLE);
      } else {
        final ImageView starIcon = (ImageView) findViewById(R.id.contact_star_icon);
        starIcon.setVisibility(entry.isFavorite ? View.VISIBLE : View.GONE);
        setVisibility(View.VISIBLE);
      }
    }
  }

  @Override
  protected boolean isDarkTheme() {
    return false;
  }

  @Override
  protected OnClickListener createClickListener() {
    return new OnClickListener() {
      @Override
      public void onClick(View v) {
        if (mListener == null) {
          return;
        }

        CallSpecificAppData.Builder callSpecificAppData =
            CallSpecificAppData.newBuilder()
                .setAllowAssistedDialing(true)
                .setCallInitiationType(CallInitiationType.Type.SPEED_DIAL)
                .setSpeedDialContactPosition(position);
        if (isStarred) {
          callSpecificAppData.addSpeedDialContactType(SpeedDialContactType.Type.STARRED_CONTACT);
        } else {
          callSpecificAppData.addSpeedDialContactType(SpeedDialContactType.Type.FREQUENT_CONTACT);
        }
        if (isPinned) {
          callSpecificAppData.addSpeedDialContactType(SpeedDialContactType.Type.PINNED_CONTACT);
        }

        if (TextUtils.isEmpty(phoneNumberString)) {
          // Don't set performance report now, since user may spend some time on picking a number

          // Copy "superclass" implementation
          Logger.get(getContext())
              .logInteraction(InteractionEvent.Type.SPEED_DIAL_CLICK_CONTACT_WITH_AMBIGUOUS_NUMBER);
          mListener.onContactSelected(
              getLookupUri(),
              MoreContactUtils.getTargetRectFromView(PhoneFavoriteTileView.this),
              callSpecificAppData.build());
        } else {
          // When you tap a frequently-called contact, you want to
          // call them at the number that you usually talk to them
          // at (i.e. the one displayed in the UI), regardless of
          // whether that's their default number.
          mListener.onCallNumberDirectly(phoneNumberString, callSpecificAppData.build());
        }
      }
    };
  }

  @Override
  protected DefaultImageRequest getDefaultImageRequest(String displayName, String lookupKey) {
    return new DefaultImageRequest(
        displayName,
        lookupKey,
        LetterTileDrawable.TYPE_DEFAULT,
        DEFAULT_IMAGE_LETTER_SCALE,
        DEFAULT_IMAGE_LETTER_OFFSET,
        false);
  }

  @Override
  protected void configureViewForImage(boolean isDefaultImage) {
    // Hide the shadow overlay if the image is a default image (i.e. colored letter tile)
    if (shadowOverlay != null) {
      shadowOverlay.setVisibility(isDefaultImage ? View.GONE : View.VISIBLE);
    }
  }

  @Override
  protected boolean isContactPhotoCircular() {
    // Unlike Contacts' tiles, the Dialer's favorites tiles are square.
    return false;
  }

  public void setPosition(int position) {
    this.position = position;
  }

  private ContactLoader loader;

  /**
   * Send a notification using a {@link ContactLoader} to inform the sync adapter that we are
   * viewing a particular contact, so that it can download the high-res photo.
   */
  private void sendViewNotification(Context context, Uri contactUri) {
    if (loader != null) {
      // Cancels the current load if it's running and clears up any memory if it's using any.
      loader.reset();
    }
    loader = new ContactLoader(context, contactUri, true /* postViewNotification */);
    // Immediately release anything we're holding in memory
    loader.registerListener(0, (loader1, contact) -> loader.reset());
    loader.startLoading();
  }

  /**
   * A {@link View.DragShadowBuilder} that doesn't draw anything. An object of this class should be
   * passed to {@link View#startDragAndDrop} to prevent the framework from drawing a drag shadow.
   */
  public static class EmptyDragShadowBuilder extends View.DragShadowBuilder {

    @Override
    public void onProvideShadowMetrics(Point size, Point touch) {
      // A workaround for P+ not accepting non-positive drag shadow sizes.
      size.set(1, 1);
      touch.set(0, 0);
    }

    @Override
    public void onDrawShadow(Canvas canvas) {
      // Don't draw anything
    }
  }
}
