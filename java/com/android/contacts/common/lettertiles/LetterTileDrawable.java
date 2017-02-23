/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.contacts.common.lettertiles;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.android.contacts.common.R;
import com.android.dialer.common.Assert;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A drawable that encapsulates all the functionality needed to display a letter tile to represent a
 * contact image.
 */
public class LetterTileDrawable extends Drawable {

  /**
   * ContactType indicates the avatar type of the contact. For a person or for the default when no
   * name is provided, it is {@link #TYPE_DEFAULT}, otherwise, for a business it is {@link
   * #TYPE_BUSINESS}, and voicemail contacts should use {@link #TYPE_VOICEMAIL}.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({TYPE_PERSON, TYPE_BUSINESS, TYPE_VOICEMAIL})
  public @interface ContactType {}

  /** Contact type constants */
  public static final int TYPE_PERSON = 1;
  public static final int TYPE_BUSINESS = 2;
  public static final int TYPE_VOICEMAIL = 3;
  @ContactType public static final int TYPE_DEFAULT = TYPE_PERSON;

  /**
   * Shape indicates the letter tile shape. It can be either a {@link #SHAPE_CIRCLE}, otherwise, it
   * is a {@link #SHAPE_RECTANGLE}.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({SHAPE_CIRCLE, SHAPE_RECTANGLE})
  public @interface Shape {}

  /** Shape constants */
  public static final int SHAPE_CIRCLE = 1;

  public static final int SHAPE_RECTANGLE = 2;

  /** 54% opacity */
  private static final int ALPHA = 138;

  /** Reusable components to avoid new allocations */
  private static final Paint sPaint = new Paint();

  private static final Rect sRect = new Rect();
  private static final char[] sFirstChar = new char[1];
  /** Letter tile */
  private static TypedArray sColors;

  private static int sDefaultColor;
  private static int sTileFontColor;
  private static float sLetterToTileRatio;
  private static Bitmap sDefaultPersonAvatar;
  private static Bitmap sDefaultBusinessAvatar;
  private static Bitmap sDefaultVoicemailAvatar;
  private static final String TAG = LetterTileDrawable.class.getSimpleName();
  private final Paint mPaint;
  private int mContactType = TYPE_DEFAULT;
  private float mScale = 1.0f;
  private float mOffset = 0.0f;
  private boolean mIsCircle = false;

  private int mColor;
  private Character mLetter = null;

  private boolean mAvatarWasVoicemailOrBusiness = false;
  private String mDisplayName;

  public LetterTileDrawable(final Resources res) {
    if (sColors == null) {
      sColors = res.obtainTypedArray(R.array.letter_tile_colors);
      sDefaultColor = res.getColor(R.color.letter_tile_default_color);
      sTileFontColor = res.getColor(R.color.letter_tile_font_color);
      sLetterToTileRatio = res.getFraction(R.dimen.letter_to_tile_ratio, 1, 1);
      sDefaultPersonAvatar =
          BitmapFactory.decodeResource(
              res, R.drawable.product_logo_avatar_anonymous_white_color_120);
      sDefaultBusinessAvatar =
          BitmapFactory.decodeResource(res, R.drawable.ic_business_white_120dp);
      sDefaultVoicemailAvatar = BitmapFactory.decodeResource(res, R.drawable.ic_voicemail_avatar);
      sPaint.setTypeface(
          Typeface.create(res.getString(R.string.letter_tile_letter_font_family), Typeface.NORMAL));
      sPaint.setTextAlign(Align.CENTER);
      sPaint.setAntiAlias(true);
    }
    mPaint = new Paint();
    mPaint.setFilterBitmap(true);
    mPaint.setDither(true);
    mColor = sDefaultColor;
  }

  private static Bitmap getBitmapForContactType(int contactType) {
    switch (contactType) {
      case TYPE_BUSINESS:
        return sDefaultBusinessAvatar;
      case TYPE_VOICEMAIL:
        return sDefaultVoicemailAvatar;
      case TYPE_PERSON:
      default:
        return sDefaultPersonAvatar;
    }
  }

  private static boolean isEnglishLetter(final char c) {
    return ('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z');
  }

  @Override
  public void draw(final Canvas canvas) {
    final Rect bounds = getBounds();
    if (!isVisible() || bounds.isEmpty()) {
      return;
    }
    // Draw letter tile.
    drawLetterTile(canvas);
  }

  /**
   * Draw the bitmap onto the canvas at the current bounds taking into account the current scale.
   */
  private void drawBitmap(
      final Bitmap bitmap, final int width, final int height, final Canvas canvas) {
    // The bitmap should be drawn in the middle of the canvas without changing its width to
    // height ratio.
    final Rect destRect = copyBounds();

    // Crop the destination bounds into a square, scaled and offset as appropriate
    final int halfLength = (int) (mScale * Math.min(destRect.width(), destRect.height()) / 2);

    destRect.set(
        destRect.centerX() - halfLength,
        (int) (destRect.centerY() - halfLength + mOffset * destRect.height()),
        destRect.centerX() + halfLength,
        (int) (destRect.centerY() + halfLength + mOffset * destRect.height()));

    // Source rectangle remains the entire bounds of the source bitmap.
    sRect.set(0, 0, width, height);

    sPaint.setTextAlign(Align.CENTER);
    sPaint.setAntiAlias(true);
    sPaint.setAlpha(ALPHA);

    canvas.drawBitmap(bitmap, sRect, destRect, sPaint);
  }

  private void drawLetterTile(final Canvas canvas) {
    // Draw background color.
    sPaint.setColor(mColor);

    sPaint.setAlpha(mPaint.getAlpha());
    final Rect bounds = getBounds();
    final int minDimension = Math.min(bounds.width(), bounds.height());

    if (mIsCircle) {
      canvas.drawCircle(bounds.centerX(), bounds.centerY(), minDimension / 2, sPaint);
    } else {
      canvas.drawRect(bounds, sPaint);
    }

    // Draw letter/digit only if the first character is an english letter or there's a override

    if (mLetter != null) {
      // Draw letter or digit.
      sFirstChar[0] = mLetter;

      // Scale text by canvas bounds and user selected scaling factor
      sPaint.setTextSize(mScale * sLetterToTileRatio * minDimension);
      sPaint.getTextBounds(sFirstChar, 0, 1, sRect);
      sPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
      sPaint.setColor(sTileFontColor);
      sPaint.setAlpha(ALPHA);

      // Draw the letter in the canvas, vertically shifted up or down by the user-defined
      // offset
      canvas.drawText(
          sFirstChar,
          0,
          1,
          bounds.centerX(),
          bounds.centerY() + mOffset * bounds.height() - sRect.exactCenterY(),
          sPaint);
    } else {
      // Draw the default image if there is no letter/digit to be drawn
      final Bitmap bitmap = getBitmapForContactType(mContactType);
      drawBitmap(bitmap, bitmap.getWidth(), bitmap.getHeight(), canvas);
    }
  }

  public int getColor() {
    return mColor;
  }

  public LetterTileDrawable setColor(int color) {
    mColor = color;
    return this;
  }

  /** Returns a deterministic color based on the provided contact identifier string. */
  private int pickColor(final String identifier) {
    if (TextUtils.isEmpty(identifier) || mContactType == TYPE_VOICEMAIL) {
      return sDefaultColor;
    }
    // String.hashCode() implementation is not supposed to change across java versions, so
    // this should guarantee the same email address always maps to the same color.
    // The email should already have been normalized by the ContactRequest.
    final int color = Math.abs(identifier.hashCode()) % sColors.length();
    return sColors.getColor(color, sDefaultColor);
  }

  @Override
  public void setAlpha(final int alpha) {
    mPaint.setAlpha(alpha);
  }

  @Override
  public void setColorFilter(final ColorFilter cf) {
    mPaint.setColorFilter(cf);
  }

  @Override
  public int getOpacity() {
    return android.graphics.PixelFormat.OPAQUE;
  }

  @Override
  public void getOutline(Outline outline) {
    if (mIsCircle) {
      outline.setOval(getBounds());
    } else {
      outline.setRect(getBounds());
    }

    outline.setAlpha(1);
  }

  /**
   * Scale the drawn letter tile to a ratio of its default size
   *
   * @param scale The ratio the letter tile should be scaled to as a percentage of its default size,
   *     from a scale of 0 to 2.0f. The default is 1.0f.
   */
  public LetterTileDrawable setScale(float scale) {
    mScale = scale;
    return this;
  }

  /**
   * Assigns the vertical offset of the position of the letter tile to the ContactDrawable
   *
   * @param offset The provided offset must be within the range of -0.5f to 0.5f. If set to -0.5f,
   *     the letter will be shifted upwards by 0.5 times the height of the canvas it is being drawn
   *     on, which means it will be drawn with the center of the letter starting at the top edge of
   *     the canvas. If set to 0.5f, the letter will be shifted downwards by 0.5 times the height of
   *     the canvas it is being drawn on, which means it will be drawn with the center of the letter
   *     starting at the bottom edge of the canvas. The default is 0.0f.
   */
  public LetterTileDrawable setOffset(float offset) {
    Assert.checkArgument(offset >= -0.5f && offset <= 0.5f);
    mOffset = offset;
    return this;
  }

  public LetterTileDrawable setLetter(Character letter) {
    mLetter = letter;
    return this;
  }

  public Character getLetter() {
    return this.mLetter;
  }

  private LetterTileDrawable setLetterAndColorFromContactDetails(
      final String displayName, final String identifier) {
    if (displayName != null && displayName.length() > 0 && isEnglishLetter(displayName.charAt(0))) {
      mLetter = Character.toUpperCase(displayName.charAt(0));
    } else {
      mLetter = null;
    }
    mColor = pickColor(identifier);
    return this;
  }

  public LetterTileDrawable setContactType(@ContactType int contactType) {
    mContactType = contactType;
    return this;
  }

  @ContactType
  public int getContactType() {
    return this.mContactType;
  }

  public LetterTileDrawable setIsCircular(boolean isCircle) {
    mIsCircle = isCircle;
    return this;
  }

  /**
   * Creates a canonical letter tile for use across dialer fragments.
   *
   * @param displayName The display name to produce the letter in the tile. Null values or numbers
   *     yield no letter.
   * @param identifierForTileColor The string used to produce the tile color.
   * @param shape The shape of the tile.
   * @param contactType The type of contact, e.g. TYPE_VOICEMAIL.
   * @return this
   */
  public LetterTileDrawable setCanonicalDialerLetterTileDetails(
      @Nullable final String displayName,
      @Nullable final String identifierForTileColor,
      @Shape final int shape,
      final int contactType) {
    setContactType(contactType);
    /**
     * During hangup, we lose the call state for special types of contacts, like voicemail. To help
     * callers avoid extraneous LetterTileDrawable allocations, we keep track of the special case
     * until we encounter a new display name.
     */
    if (contactType == TYPE_VOICEMAIL || contactType == TYPE_BUSINESS) {
      this.mAvatarWasVoicemailOrBusiness = true;
    } else if (displayName != null && !displayName.equals(mDisplayName)) {
      this.mAvatarWasVoicemailOrBusiness = false;
    }
    this.mDisplayName = displayName;
    if (shape == SHAPE_CIRCLE) {
      this.setIsCircular(true);
    } else {
      this.setIsCircular(false);
    }

    /**
     * To preserve style, we don't use contactType to set the tile icon. In the future, when all
     * callers surface this detail, we can use this to better style the tile icon.
     */
    if (mAvatarWasVoicemailOrBusiness) {
      this.setLetterAndColorFromContactDetails(null, displayName);
      return this;
    } else {
      if (identifierForTileColor != null) {
        this.setLetterAndColorFromContactDetails(displayName, identifierForTileColor);
        return this;
      } else {
        this.setLetterAndColorFromContactDetails(displayName, displayName);
        return this;
      }
    }
  }
}
