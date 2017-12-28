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

package com.android.dialer.lettertile;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telecom.TelecomManager;
import android.text.TextUtils;
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
  @IntDef({TYPE_PERSON, TYPE_BUSINESS, TYPE_VOICEMAIL, TYPE_GENERIC_AVATAR, TYPE_SPAM})
  public @interface ContactType {}

  /** Contact type constants */
  public static final int TYPE_PERSON = 1;

  public static final int TYPE_BUSINESS = 2;
  public static final int TYPE_VOICEMAIL = 3;
  /**
   * A generic avatar that features the default icon, default color, and no letter. Useful for
   * situations where a contact is anonymous.
   */
  public static final int TYPE_GENERIC_AVATAR = 4;

  public static final int TYPE_SPAM = 5;
  public static final int TYPE_CONFERENCE = 6;
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
  /** 100% opacity */
  private static final int SPAM_ALPHA = 255;
  /** Default icon scale for vector drawable. */
  private static final float VECTOR_ICON_SCALE = 0.7f;

  /** Reusable components to avoid new allocations */
  private final Paint paint = new Paint();

  private final Rect rect = new Rect();
  private final char[] firstChar = new char[1];

  /** Letter tile */
  @NonNull private final TypedArray colors;

  private final int spamColor;
  private final int defaultColor;
  private final int tileFontColor;
  private final float letterToTileRatio;
  @NonNull private final Drawable defaultPersonAvatar;
  @NonNull private final Drawable defaultBusinessAvatar;
  @NonNull private final Drawable defaultVoicemailAvatar;
  @NonNull private final Drawable defaultSpamAvatar;
  @NonNull private final Drawable defaultConferenceAvatar;

  @ContactType private int contactType = TYPE_DEFAULT;
  private float scale = 1.0f;
  private float offset = 0.0f;
  private boolean isCircle = false;

  private int color;
  private Character letter = null;

  private String displayName;

  public LetterTileDrawable(final Resources res) {
    colors = res.obtainTypedArray(R.array.letter_tile_colors);
    spamColor = res.getColor(R.color.spam_contact_background);
    defaultColor = res.getColor(R.color.letter_tile_default_color);
    tileFontColor = res.getColor(R.color.letter_tile_font_color);
    letterToTileRatio = res.getFraction(R.dimen.letter_to_tile_ratio, 1, 1);
    defaultPersonAvatar =
        res.getDrawable(R.drawable.product_logo_avatar_anonymous_white_color_120, null);
    defaultBusinessAvatar = res.getDrawable(R.drawable.quantum_ic_business_vd_theme_24, null);
    defaultVoicemailAvatar = res.getDrawable(R.drawable.quantum_ic_voicemail_vd_theme_24, null);
    defaultSpamAvatar = res.getDrawable(R.drawable.quantum_ic_report_vd_theme_24, null);
    defaultConferenceAvatar = res.getDrawable(R.drawable.quantum_ic_group_vd_theme_24, null);

    paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    paint.setTextAlign(Align.CENTER);
    paint.setAntiAlias(true);
    paint.setFilterBitmap(true);
    paint.setDither(true);
    color = defaultColor;
  }

  private Rect getScaledBounds(float scale, float offset) {
    // The drawable should be drawn in the middle of the canvas without changing its width to
    // height ratio.
    final Rect destRect = copyBounds();
    // Crop the destination bounds into a square, scaled and offset as appropriate
    final int halfLength = (int) (scale * Math.min(destRect.width(), destRect.height()) / 2);

    destRect.set(
        destRect.centerX() - halfLength,
        (int) (destRect.centerY() - halfLength + offset * destRect.height()),
        destRect.centerX() + halfLength,
        (int) (destRect.centerY() + halfLength + offset * destRect.height()));
    return destRect;
  }

  private Drawable getDrawableForContactType(int contactType) {
    switch (contactType) {
      case TYPE_BUSINESS:
        scale = VECTOR_ICON_SCALE;
        return defaultBusinessAvatar;
      case TYPE_VOICEMAIL:
        scale = VECTOR_ICON_SCALE;
        return defaultVoicemailAvatar;
      case TYPE_SPAM:
        scale = VECTOR_ICON_SCALE;
        return defaultSpamAvatar;
      case TYPE_CONFERENCE:
        scale = VECTOR_ICON_SCALE;
        return defaultConferenceAvatar;
      case TYPE_PERSON:
      case TYPE_GENERIC_AVATAR:
      default:
        return defaultPersonAvatar;
    }
  }

  private static boolean isEnglishLetter(final char c) {
    return ('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z');
  }

  @Override
  public void draw(@NonNull final Canvas canvas) {
    final Rect bounds = getBounds();
    if (!isVisible() || bounds.isEmpty()) {
      return;
    }
    // Draw letter tile.
    drawLetterTile(canvas);
  }

  public Bitmap getBitmap(int width, int height) {
    Bitmap bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
    this.setBounds(0, 0, width, height);
    Canvas canvas = new Canvas(bitmap);
    this.draw(canvas);
    return bitmap;
  }

  private void drawLetterTile(final Canvas canvas) {
    // Draw background color.
    paint.setColor(color);

    final Rect bounds = getBounds();
    final int minDimension = Math.min(bounds.width(), bounds.height());

    if (isCircle) {
      canvas.drawCircle(bounds.centerX(), bounds.centerY(), minDimension / 2, paint);
    } else {
      canvas.drawRect(bounds, paint);
    }

    // Draw letter/digit only if the first character is an english letter or there's a override
    if (letter != null) {
      // Draw letter or digit.
      firstChar[0] = letter;

      // Scale text by canvas bounds and user selected scaling factor
      paint.setTextSize(scale * letterToTileRatio * minDimension);
      paint.getTextBounds(firstChar, 0, 1, rect);
      paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
      paint.setColor(tileFontColor);
      paint.setAlpha(ALPHA);

      // Draw the letter in the canvas, vertically shifted up or down by the user-defined
      // offset
      canvas.drawText(
          firstChar,
          0,
          1,
          bounds.centerX(),
          bounds.centerY() + offset * bounds.height() - rect.exactCenterY(),
          paint);
    } else {
      // Draw the default image if there is no letter/digit to be drawn
      Drawable drawable = getDrawableForContactType(contactType);
      if (drawable == null) {
        throw Assert.createIllegalStateFailException(
            "Unable to find drawable for contact type " + contactType);
      }

      drawable.setBounds(getScaledBounds(scale, offset));
      drawable.setAlpha(drawable == defaultSpamAvatar ? SPAM_ALPHA : ALPHA);
      drawable.draw(canvas);
    }
  }

  public int getColor() {
    return color;
  }

  public LetterTileDrawable setColor(int color) {
    this.color = color;
    return this;
  }

  /** Returns a deterministic color based on the provided contact identifier string. */
  private int pickColor(final String identifier) {
    if (contactType == TYPE_SPAM) {
      return spamColor;
    }

    if (contactType == TYPE_VOICEMAIL
        || contactType == TYPE_BUSINESS
        || TextUtils.isEmpty(identifier)) {
      return defaultColor;
    }

    // String.hashCode() implementation is not supposed to change across java versions, so
    // this should guarantee the same email address always maps to the same color.
    // The email should already have been normalized by the ContactRequest.
    final int color = Math.abs(identifier.hashCode()) % colors.length();
    return colors.getColor(color, defaultColor);
  }

  @Override
  public void setAlpha(final int alpha) {
    paint.setAlpha(alpha);
  }

  @Override
  public void setColorFilter(final ColorFilter cf) {
    paint.setColorFilter(cf);
  }

  @Override
  public int getOpacity() {
    return android.graphics.PixelFormat.OPAQUE;
  }

  @Override
  public void getOutline(Outline outline) {
    if (isCircle) {
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
    this.scale = scale;
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
    this.offset = offset;
    return this;
  }

  public LetterTileDrawable setLetter(Character letter) {
    this.letter = letter;
    return this;
  }

  public Character getLetter() {
    return this.letter;
  }

  private LetterTileDrawable setLetterAndColorFromContactDetails(
      final String displayName, final String identifier) {
    if (!TextUtils.isEmpty(displayName) && isEnglishLetter(displayName.charAt(0))) {
      letter = Character.toUpperCase(displayName.charAt(0));
    } else {
      letter = null;
    }
    color = pickColor(identifier);
    return this;
  }

  private LetterTileDrawable setContactType(@ContactType int contactType) {
    this.contactType = contactType;
    return this;
  }

  @ContactType
  public int getContactType() {
    return this.contactType;
  }

  public LetterTileDrawable setIsCircular(boolean isCircle) {
    this.isCircle = isCircle;
    return this;
  }

  public boolean tileIsCircular() {
    return this.isCircle;
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

    this.setIsCircular(shape == SHAPE_CIRCLE);

    /**
     * We return quickly under the following conditions: 1. We are asked to draw a default tile, and
     * no coloring information is provided, meaning no further initialization is necessary OR 2.
     * We've already invoked this method before, set mDisplayName, and found that it has not
     * changed. This is useful during events like hangup, when we lose the call state for special
     * types of contacts, like voicemail. We keep track of the special case until we encounter a new
     * display name.
     */
    if (contactType == TYPE_DEFAULT
        && ((displayName == null && identifierForTileColor == null)
            || (displayName != null && displayName.equals(this.displayName)))) {
      return this;
    }

    this.displayName = displayName;
    setContactType(contactType);

    // Special contact types receive default color and no letter tile, but special iconography.
    if (contactType != TYPE_PERSON) {
      this.setLetterAndColorFromContactDetails(null, null);
    } else {
      if (identifierForTileColor != null) {
        this.setLetterAndColorFromContactDetails(displayName, identifierForTileColor);
      } else {
        this.setLetterAndColorFromContactDetails(displayName, displayName);
      }
    }
    return this;
  }

  /**
   * Returns the appropriate LetterTileDrawable.TYPE_ based on the given primitive conditions.
   *
   * <p>If no special state is detected, yields TYPE_DEFAULT
   */
  public static @ContactType int getContactTypeFromPrimitives(
      boolean isVoicemailNumber,
      boolean isSpam,
      boolean isBusiness,
      int numberPresentation,
      boolean isConference) {
    if (isVoicemailNumber) {
      return LetterTileDrawable.TYPE_VOICEMAIL;
    } else if (isSpam) {
      return LetterTileDrawable.TYPE_SPAM;
    } else if (isBusiness) {
      return LetterTileDrawable.TYPE_BUSINESS;
    } else if (numberPresentation == TelecomManager.PRESENTATION_RESTRICTED) {
      return LetterTileDrawable.TYPE_GENERIC_AVATAR;
    } else if (isConference) {
      return LetterTileDrawable.TYPE_CONFERENCE;
    } else {
      return LetterTileDrawable.TYPE_DEFAULT;
    }
  }
}
