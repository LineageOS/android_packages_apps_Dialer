/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.dialer.contactphoto;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.Uri.Builder;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import com.android.dialer.common.LogUtil;
import com.android.dialer.lettertile.LetterTileDrawable;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.util.UriUtils;

/** Asynchronously loads contact photos and maintains a cache of photos. */
public abstract class ContactPhotoManager implements ComponentCallbacks2 {

  /** Scale and offset default constants used for default letter images */
  public static final float SCALE_DEFAULT = 1.0f;

  public static final float OFFSET_DEFAULT = 0.0f;
  public static final boolean IS_CIRCULAR_DEFAULT = false;
  // TODO: Use LogUtil.isVerboseEnabled for DEBUG branches instead of a lint check.
  // LINT.DoNotSubmitIf(true)
  static final boolean DEBUG = false;
  // LINT.DoNotSubmitIf(true)
  static final boolean DEBUG_SIZES = false;
  /** Uri-related constants used for default letter images */
  private static final String DISPLAY_NAME_PARAM_KEY = "display_name";

  private static final String IDENTIFIER_PARAM_KEY = "identifier";
  private static final String CONTACT_TYPE_PARAM_KEY = "contact_type";
  private static final String SCALE_PARAM_KEY = "scale";
  private static final String OFFSET_PARAM_KEY = "offset";
  private static final String IS_CIRCULAR_PARAM_KEY = "is_circular";
  private static final String DEFAULT_IMAGE_URI_SCHEME = "defaultimage";
  private static final Uri DEFAULT_IMAGE_URI = Uri.parse(DEFAULT_IMAGE_URI_SCHEME + "://");
  public static final DefaultImageProvider DEFAULT_AVATAR = new LetterTileDefaultImageProvider();
  private static ContactPhotoManager instance;

  /**
   * Given a {@link DefaultImageRequest}, returns an Uri that can be used to request a letter tile
   * avatar when passed to the {@link ContactPhotoManager}. The internal implementation of this uri
   * is not guaranteed to remain the same across application versions, so the actual uri should
   * never be persisted in long-term storage and reused.
   *
   * @param request A {@link DefaultImageRequest} object with the fields configured to return a
   * @return A Uri that when later passed to the {@link ContactPhotoManager} via {@link
   *     #loadPhoto(ImageView, Uri, int, boolean, boolean, DefaultImageRequest)}, can be used to
   *     request a default contact image, drawn as a letter tile using the parameters as configured
   *     in the provided {@link DefaultImageRequest}
   */
  public static Uri getDefaultAvatarUriForContact(DefaultImageRequest request) {
    final Builder builder = DEFAULT_IMAGE_URI.buildUpon();
    if (request != null) {
      if (!TextUtils.isEmpty(request.displayName)) {
        builder.appendQueryParameter(DISPLAY_NAME_PARAM_KEY, request.displayName);
      }
      if (!TextUtils.isEmpty(request.identifier)) {
        builder.appendQueryParameter(IDENTIFIER_PARAM_KEY, request.identifier);
      }
      if (request.contactType != LetterTileDrawable.TYPE_DEFAULT) {
        builder.appendQueryParameter(CONTACT_TYPE_PARAM_KEY, String.valueOf(request.contactType));
      }
      if (request.scale != SCALE_DEFAULT) {
        builder.appendQueryParameter(SCALE_PARAM_KEY, String.valueOf(request.scale));
      }
      if (request.offset != OFFSET_DEFAULT) {
        builder.appendQueryParameter(OFFSET_PARAM_KEY, String.valueOf(request.offset));
      }
      if (request.isCircular != IS_CIRCULAR_DEFAULT) {
        builder.appendQueryParameter(IS_CIRCULAR_PARAM_KEY, String.valueOf(request.isCircular));
      }
    }
    return builder.build();
  }

  /**
   * Adds a business contact type encoded fragment to the URL. Used to ensure photo URLS from Nearby
   * Places can be identified as business photo URLs rather than URLs for personal contact photos.
   *
   * @param photoUrl The photo URL to modify.
   * @return URL with the contact type parameter added and set to TYPE_BUSINESS.
   */
  public static String appendBusinessContactType(String photoUrl) {
    Uri uri = Uri.parse(photoUrl);
    Builder builder = uri.buildUpon();
    builder.encodedFragment(String.valueOf(LetterTileDrawable.TYPE_BUSINESS));
    return builder.build().toString();
  }

  /**
   * Removes the contact type information stored in the photo URI encoded fragment.
   *
   * @param photoUri The photo URI to remove the contact type from.
   * @return The photo URI with contact type removed.
   */
  public static Uri removeContactType(Uri photoUri) {
    String encodedFragment = photoUri.getEncodedFragment();
    if (!TextUtils.isEmpty(encodedFragment)) {
      Builder builder = photoUri.buildUpon();
      builder.encodedFragment(null);
      return builder.build();
    }
    return photoUri;
  }

  /**
   * Inspects a photo URI to determine if the photo URI represents a business.
   *
   * @param photoUri The URI to inspect.
   * @return Whether the URI represents a business photo or not.
   */
  public static boolean isBusinessContactUri(Uri photoUri) {
    if (photoUri == null) {
      return false;
    }

    String encodedFragment = photoUri.getEncodedFragment();
    return !TextUtils.isEmpty(encodedFragment)
        && encodedFragment.equals(String.valueOf(LetterTileDrawable.TYPE_BUSINESS));
  }

  protected static DefaultImageRequest getDefaultImageRequestFromUri(Uri uri) {
    final DefaultImageRequest request =
        new DefaultImageRequest(
            uri.getQueryParameter(DISPLAY_NAME_PARAM_KEY),
            uri.getQueryParameter(IDENTIFIER_PARAM_KEY),
            false);
    try {
      String contactType = uri.getQueryParameter(CONTACT_TYPE_PARAM_KEY);
      if (!TextUtils.isEmpty(contactType)) {
        request.contactType = Integer.valueOf(contactType);
      }

      String scale = uri.getQueryParameter(SCALE_PARAM_KEY);
      if (!TextUtils.isEmpty(scale)) {
        request.scale = Float.valueOf(scale);
      }

      String offset = uri.getQueryParameter(OFFSET_PARAM_KEY);
      if (!TextUtils.isEmpty(offset)) {
        request.offset = Float.valueOf(offset);
      }

      String isCircular = uri.getQueryParameter(IS_CIRCULAR_PARAM_KEY);
      if (!TextUtils.isEmpty(isCircular)) {
        request.isCircular = Boolean.valueOf(isCircular);
      }
    } catch (NumberFormatException e) {
      LogUtil.w(
          "ContactPhotoManager.getDefaultImageRequestFromUri",
          "Invalid DefaultImageRequest image parameters provided, ignoring and using "
              + "defaults.");
    }

    return request;
  }

  public static ContactPhotoManager getInstance(Context context) {
    if (instance == null) {
      Context applicationContext = context.getApplicationContext();
      instance = createContactPhotoManager(applicationContext);
      applicationContext.registerComponentCallbacks(instance);
      if (PermissionsUtil.hasContactsReadPermissions(context)) {
        instance.preloadPhotosInBackground();
      }
    }
    return instance;
  }

  public static synchronized ContactPhotoManager createContactPhotoManager(Context context) {
    return new ContactPhotoManagerImpl(context);
  }

  @VisibleForTesting
  public static void injectContactPhotoManagerForTesting(ContactPhotoManager photoManager) {
    instance = photoManager;
  }

  protected boolean isDefaultImageUri(Uri uri) {
    return DEFAULT_IMAGE_URI_SCHEME.equals(uri.getScheme());
  }

  /**
   * Load thumbnail image into the supplied image view. If the photo is already cached, it is
   * displayed immediately. Otherwise a request is sent to load the photo from the database.
   */
  public abstract void loadThumbnail(
      ImageView view,
      long photoId,
      boolean darkTheme,
      boolean isCircular,
      DefaultImageRequest defaultImageRequest,
      DefaultImageProvider defaultProvider);

  /**
   * Calls {@link #loadThumbnail(ImageView, long, boolean, boolean, DefaultImageRequest,
   * DefaultImageProvider)} using the {@link DefaultImageProvider} {@link #DEFAULT_AVATAR}.
   */
  public final void loadThumbnail(
      ImageView view,
      long photoId,
      boolean darkTheme,
      boolean isCircular,
      DefaultImageRequest defaultImageRequest) {
    loadThumbnail(view, photoId, darkTheme, isCircular, defaultImageRequest, DEFAULT_AVATAR);
  }

  public final void loadDialerThumbnailOrPhoto(
      QuickContactBadge badge,
      Uri contactUri,
      long photoId,
      Uri photoUri,
      String displayName,
      int contactType) {
    badge.assignContactUri(contactUri);
    badge.setOverlay(null);

    badge.setContentDescription(
        badge.getContext().getString(R.string.description_quick_contact_for, displayName));

    String lookupKey = contactUri == null ? null : UriUtils.getLookupKeyFromUri(contactUri);
    ContactPhotoManager.DefaultImageRequest request =
        new ContactPhotoManager.DefaultImageRequest(
            displayName, lookupKey, contactType, true /* isCircular */);
    if (photoId == 0 && photoUri != null) {
      loadDirectoryPhoto(badge, photoUri, false /* darkTheme */, true /* isCircular */, request);
    } else {
      loadThumbnail(badge, photoId, false /* darkTheme */, true /* isCircular */, request);
    }
  }

  /**
   * Load photo into the supplied image view. If the photo is already cached, it is displayed
   * immediately. Otherwise a request is sent to load the photo from the location specified by the
   * URI.
   *
   * @param view The target view
   * @param photoUri The uri of the photo to load
   * @param requestedExtent Specifies an approximate Max(width, height) of the targetView. This is
   *     useful if the source image can be a lot bigger that the target, so that the decoding is
   *     done using efficient sampling. If requestedExtent is specified, no sampling of the image is
   *     performed
   * @param darkTheme Whether the background is dark. This is used for default avatars
   * @param defaultImageRequest {@link DefaultImageRequest} object that specifies how a default
   *     letter tile avatar should be drawn.
   * @param defaultProvider The provider of default avatars (this is used if photoUri doesn't refer
   *     to an existing image)
   */
  public abstract void loadPhoto(
      ImageView view,
      Uri photoUri,
      int requestedExtent,
      boolean darkTheme,
      boolean isCircular,
      DefaultImageRequest defaultImageRequest,
      DefaultImageProvider defaultProvider);

  /**
   * Calls {@link #loadPhoto(ImageView, Uri, int, boolean, boolean, DefaultImageRequest,
   * DefaultImageProvider)} with {@link #DEFAULT_AVATAR} and {@code null} display names and lookup
   * keys.
   *
   * @param defaultImageRequest {@link DefaultImageRequest} object that specifies how a default
   *     letter tile avatar should be drawn.
   */
  public final void loadPhoto(
      ImageView view,
      Uri photoUri,
      int requestedExtent,
      boolean darkTheme,
      boolean isCircular,
      DefaultImageRequest defaultImageRequest) {
    loadPhoto(
        view,
        photoUri,
        requestedExtent,
        darkTheme,
        isCircular,
        defaultImageRequest,
        DEFAULT_AVATAR);
  }

  /**
   * Calls {@link #loadPhoto(ImageView, Uri, int, boolean, boolean, DefaultImageRequest,
   * DefaultImageProvider)} with {@link #DEFAULT_AVATAR} and with the assumption, that the image is
   * a thumbnail.
   *
   * @param defaultImageRequest {@link DefaultImageRequest} object that specifies how a default
   *     letter tile avatar should be drawn.
   */
  public final void loadDirectoryPhoto(
      ImageView view,
      Uri photoUri,
      boolean darkTheme,
      boolean isCircular,
      DefaultImageRequest defaultImageRequest) {
    loadPhoto(view, photoUri, -1, darkTheme, isCircular, defaultImageRequest, DEFAULT_AVATAR);
  }

  /**
   * Remove photo from the supplied image view. This also cancels current pending load request
   * inside this photo manager.
   */
  public abstract void removePhoto(ImageView view);

  /** Cancels all pending requests to load photos asynchronously. */
  public abstract void cancelPendingRequests(View fragmentRootView);

  /** Temporarily stops loading photos from the database. */
  public abstract void pause();

  /** Resumes loading photos from the database. */
  public abstract void resume();

  /**
   * Marks all cached photos for reloading. We can continue using cache but should also make sure
   * the photos haven't changed in the background and notify the views if so.
   */
  public abstract void refreshCache();

  /** Initiates a background process that over time will fill up cache with preload photos. */
  public abstract void preloadPhotosInBackground();

  // ComponentCallbacks2
  @Override
  public void onConfigurationChanged(Configuration newConfig) {}

  // ComponentCallbacks2
  @Override
  public void onLowMemory() {}

  // ComponentCallbacks2
  @Override
  public void onTrimMemory(int level) {}

  /**
   * Contains fields used to contain contact details and other user-defined settings that might be
   * used by the ContactPhotoManager to generate a default contact image. This contact image takes
   * the form of a letter or bitmap drawn on top of a colored tile.
   */
  public static class DefaultImageRequest {

    /**
     * Used to indicate that a drawable that represents a contact without any contact details should
     * be returned.
     */
    public static final DefaultImageRequest EMPTY_DEFAULT_IMAGE_REQUEST = new DefaultImageRequest();
    /**
     * Used to indicate that a drawable that represents a business without a business photo should
     * be returned.
     */
    public static final DefaultImageRequest EMPTY_DEFAULT_BUSINESS_IMAGE_REQUEST =
        new DefaultImageRequest(null, null, LetterTileDrawable.TYPE_BUSINESS, false);
    /**
     * Used to indicate that a circular drawable that represents a contact without any contact
     * details should be returned.
     */
    public static final DefaultImageRequest EMPTY_CIRCULAR_DEFAULT_IMAGE_REQUEST =
        new DefaultImageRequest(null, null, true);
    /**
     * Used to indicate that a circular drawable that represents a business without a business photo
     * should be returned.
     */
    public static final DefaultImageRequest EMPTY_CIRCULAR_BUSINESS_IMAGE_REQUEST =
        new DefaultImageRequest(null, null, LetterTileDrawable.TYPE_BUSINESS, true);
    /** The contact's display name. The display name is used to */
    public String displayName;
    /**
     * A unique and deterministic string that can be used to identify this contact. This is usually
     * the contact's lookup key, but other contact details can be used as well, especially for
     * non-local or temporary contacts that might not have a lookup key. This is used to determine
     * the color of the tile.
     */
    public String identifier;
    /**
     * The type of this contact. This contact type may be used to decide the kind of image to use in
     * the case where a unique letter cannot be generated from the contact's display name and
     * identifier.
     */
    public @LetterTileDrawable.ContactType int contactType = LetterTileDrawable.TYPE_DEFAULT;
    /**
     * The amount to scale the letter or bitmap to, as a ratio of its default size (from a range of
     * 0.0f to 2.0f). The default value is 1.0f.
     */
    public float scale = SCALE_DEFAULT;
    /**
     * The amount to vertically offset the letter or image to within the tile. The provided offset
     * must be within the range of -0.5f to 0.5f. If set to -0.5f, the letter will be shifted
     * upwards by 0.5 times the height of the canvas it is being drawn on, which means it will be
     * drawn with the center of the letter starting at the top edge of the canvas. If set to 0.5f,
     * the letter will be shifted downwards by 0.5 times the height of the canvas it is being drawn
     * on, which means it will be drawn with the center of the letter starting at the bottom edge of
     * the canvas. The default is 0.0f, which means the letter is drawn in the exact vertical center
     * of the tile.
     */
    public float offset = OFFSET_DEFAULT;
    /** Whether or not to draw the default image as a circle, instead of as a square/rectangle. */
    public boolean isCircular = false;

    public DefaultImageRequest() {}

    public DefaultImageRequest(String displayName, String identifier, boolean isCircular) {
      this(
          displayName,
          identifier,
          LetterTileDrawable.TYPE_DEFAULT,
          SCALE_DEFAULT,
          OFFSET_DEFAULT,
          isCircular);
    }

    public DefaultImageRequest(
        String displayName, String identifier, int contactType, boolean isCircular) {
      this(displayName, identifier, contactType, SCALE_DEFAULT, OFFSET_DEFAULT, isCircular);
    }

    public DefaultImageRequest(
        String displayName,
        String identifier,
        int contactType,
        float scale,
        float offset,
        boolean isCircular) {
      this.displayName = displayName;
      this.identifier = identifier;
      this.contactType = contactType;
      this.scale = scale;
      this.offset = offset;
      this.isCircular = isCircular;
    }
  }

  public abstract static class DefaultImageProvider {

    /**
     * Applies the default avatar to the ImageView. Extent is an indicator for the size (width or
     * height). If darkTheme is set, the avatar is one that looks better on dark background
     *
     * @param defaultImageRequest {@link DefaultImageRequest} object that specifies how a default
     *     letter tile avatar should be drawn.
     */
    public abstract void applyDefaultImage(
        ImageView view, int extent, boolean darkTheme, DefaultImageRequest defaultImageRequest);
  }

  /**
   * A default image provider that applies a letter tile consisting of a colored background and a
   * letter in the foreground as the default image for a contact. The color of the background and
   * the type of letter is decided based on the contact's details.
   */
  private static class LetterTileDefaultImageProvider extends DefaultImageProvider {

    public static Drawable getDefaultImageForContact(
        Resources resources, DefaultImageRequest defaultImageRequest) {
      final LetterTileDrawable drawable = new LetterTileDrawable(resources);
      final int tileShape =
          defaultImageRequest.isCircular
              ? LetterTileDrawable.SHAPE_CIRCLE
              : LetterTileDrawable.SHAPE_RECTANGLE;
      if (defaultImageRequest != null) {
        // If the contact identifier is null or empty, fallback to the
        // displayName. In that case, use {@code null} for the contact's
        // display name so that a default bitmap will be used instead of a
        // letter
        if (TextUtils.isEmpty(defaultImageRequest.identifier)) {
          drawable.setCanonicalDialerLetterTileDetails(
              null, defaultImageRequest.displayName, tileShape, defaultImageRequest.contactType);
        } else {
          drawable.setCanonicalDialerLetterTileDetails(
              defaultImageRequest.displayName,
              defaultImageRequest.identifier,
              tileShape,
              defaultImageRequest.contactType);
        }
        drawable.setScale(defaultImageRequest.scale);
        drawable.setOffset(defaultImageRequest.offset);
      }
      return drawable;
    }

    @Override
    public void applyDefaultImage(
        ImageView view, int extent, boolean darkTheme, DefaultImageRequest defaultImageRequest) {
      final Drawable drawable = getDefaultImageForContact(view.getResources(), defaultImageRequest);
      view.setImageDrawable(drawable);
    }
  }
}
