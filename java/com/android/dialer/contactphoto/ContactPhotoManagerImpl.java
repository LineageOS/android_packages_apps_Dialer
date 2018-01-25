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

package com.android.dialer.contactphoto;

import android.app.ActivityManager;
import android.content.ComponentCallbacks2;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.media.ThumbnailUtils;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.Photo;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Directory;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.android.dialer.common.LogUtil;
import com.android.dialer.constants.Constants;
import com.android.dialer.constants.TrafficStatsTags;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.util.UriUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

class ContactPhotoManagerImpl extends ContactPhotoManager implements Callback {

  private static final String LOADER_THREAD_NAME = "ContactPhotoLoader";

  private static final int FADE_TRANSITION_DURATION = 200;

  /**
   * Type of message sent by the UI thread to itself to indicate that some photos need to be loaded.
   */
  private static final int MESSAGE_REQUEST_LOADING = 1;

  /** Type of message sent by the loader thread to indicate that some photos have been loaded. */
  private static final int MESSAGE_PHOTOS_LOADED = 2;

  private static final String[] EMPTY_STRING_ARRAY = new String[0];

  private static final String[] COLUMNS = new String[] {Photo._ID, Photo.PHOTO};

  /**
   * Dummy object used to indicate that a bitmap for a given key could not be stored in the cache.
   */
  private static final BitmapHolder BITMAP_UNAVAILABLE;
  /** Cache size for {@link #bitmapHolderCache} for devices with "large" RAM. */
  private static final int HOLDER_CACHE_SIZE = 2000000;
  /** Cache size for {@link #bitmapCache} for devices with "large" RAM. */
  private static final int BITMAP_CACHE_SIZE = 36864 * 48; // 1728K
  /** Height/width of a thumbnail image */
  private static int thumbnailSize;

  static {
    BITMAP_UNAVAILABLE = new BitmapHolder(new byte[0], 0);
    BITMAP_UNAVAILABLE.bitmapRef = new SoftReference<Bitmap>(null);
  }

  private final Context context;
  /**
   * An LRU cache for bitmap holders. The cache contains bytes for photos just as they come from the
   * database. Each holder has a soft reference to the actual bitmap.
   */
  private final LruCache<Object, BitmapHolder> bitmapHolderCache;
  /** Cache size threshold at which bitmaps will not be preloaded. */
  private final int bitmapHolderCacheRedZoneBytes;
  /**
   * Level 2 LRU cache for bitmaps. This is a smaller cache that holds the most recently used
   * bitmaps to save time on decoding them from bytes (the bytes are stored in {@link
   * #bitmapHolderCache}.
   */
  private final LruCache<Object, Bitmap> bitmapCache;
  /**
   * A map from ImageView to the corresponding photo ID or uri, encapsulated in a request. The
   * request may swapped out before the photo loading request is started.
   */
  private final ConcurrentHashMap<ImageView, Request> pendingRequests =
      new ConcurrentHashMap<ImageView, Request>();
  /** Handler for messages sent to the UI thread. */
  private final Handler mainThreadHandler = new Handler(this);
  /** For debug: How many times we had to reload cached photo for a stale entry */
  private final AtomicInteger staleCacheOverwrite = new AtomicInteger();
  /** For debug: How many times we had to reload cached photo for a fresh entry. Should be 0. */
  private final AtomicInteger freshCacheOverwrite = new AtomicInteger();
  /** {@code true} if ALL entries in {@link #bitmapHolderCache} are NOT fresh. */
  private volatile boolean bitmapHolderCacheAllUnfresh = true;
  /** Thread responsible for loading photos from the database. Created upon the first request. */
  private LoaderThread loaderThread;
  /** A gate to make sure we only send one instance of MESSAGE_PHOTOS_NEEDED at a time. */
  private boolean loadingRequested;
  /** Flag indicating if the image loading is paused. */
  private boolean paused;
  /** The user agent string to use when loading URI based photos. */
  private String userAgent;

  public ContactPhotoManagerImpl(Context context) {
    this.context = context;

    final ActivityManager am =
        ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE));

    final float cacheSizeAdjustment = (am.isLowRamDevice()) ? 0.5f : 1.0f;

    final int bitmapCacheSize = (int) (cacheSizeAdjustment * BITMAP_CACHE_SIZE);
    bitmapCache =
        new LruCache<Object, Bitmap>(bitmapCacheSize) {
          @Override
          protected int sizeOf(Object key, Bitmap value) {
            return value.getByteCount();
          }

          @Override
          protected void entryRemoved(
              boolean evicted, Object key, Bitmap oldValue, Bitmap newValue) {
            if (DEBUG) {
              dumpStats();
            }
          }
        };
    final int holderCacheSize = (int) (cacheSizeAdjustment * HOLDER_CACHE_SIZE);
    bitmapHolderCache =
        new LruCache<Object, BitmapHolder>(holderCacheSize) {
          @Override
          protected int sizeOf(Object key, BitmapHolder value) {
            return value.bytes != null ? value.bytes.length : 0;
          }

          @Override
          protected void entryRemoved(
              boolean evicted, Object key, BitmapHolder oldValue, BitmapHolder newValue) {
            if (DEBUG) {
              dumpStats();
            }
          }
        };
    bitmapHolderCacheRedZoneBytes = (int) (holderCacheSize * 0.75);
    LogUtil.i(
        "ContactPhotoManagerImpl.ContactPhotoManagerImpl", "cache adj: " + cacheSizeAdjustment);
    if (DEBUG) {
      LogUtil.d(
          "ContactPhotoManagerImpl.ContactPhotoManagerImpl",
          "Cache size: " + btk(bitmapHolderCache.maxSize()) + " + " + btk(bitmapCache.maxSize()));
    }

    thumbnailSize =
        context.getResources().getDimensionPixelSize(R.dimen.contact_browser_list_item_photo_size);

    // Get a user agent string to use for URI photo requests.
    userAgent = Constants.get().getUserAgent(context);
    if (userAgent == null) {
      userAgent = "";
    }
  }

  /** Converts bytes to K bytes, rounding up. Used only for debug log. */
  private static String btk(int bytes) {
    return ((bytes + 1023) / 1024) + "K";
  }

  private static final int safeDiv(int dividend, int divisor) {
    return (divisor == 0) ? 0 : (dividend / divisor);
  }

  private static boolean isChildView(View parent, View potentialChild) {
    return potentialChild.getParent() != null
        && (potentialChild.getParent() == parent
            || (potentialChild.getParent() instanceof ViewGroup
                && isChildView(parent, (ViewGroup) potentialChild.getParent())));
  }

  /**
   * If necessary, decodes bytes stored in the holder to Bitmap. As long as the bitmap is held
   * either by {@link #bitmapCache} or by a soft reference in the holder, it will not be necessary
   * to decode the bitmap.
   */
  private static void inflateBitmap(BitmapHolder holder, int requestedExtent) {
    final int sampleSize =
        BitmapUtil.findOptimalSampleSize(holder.originalSmallerExtent, requestedExtent);
    byte[] bytes = holder.bytes;
    if (bytes == null || bytes.length == 0) {
      return;
    }

    if (sampleSize == holder.decodedSampleSize) {
      // Check the soft reference.  If will be retained if the bitmap is also
      // in the LRU cache, so we don't need to check the LRU cache explicitly.
      if (holder.bitmapRef != null) {
        holder.bitmap = holder.bitmapRef.get();
        if (holder.bitmap != null) {
          return;
        }
      }
    }

    try {
      Bitmap bitmap = BitmapUtil.decodeBitmapFromBytes(bytes, sampleSize);

      // TODO: As a temporary workaround while framework support is being added to
      // clip non-square bitmaps into a perfect circle, manually crop the bitmap into
      // into a square if it will be displayed as a thumbnail so that it can be cropped
      // into a circle.
      final int height = bitmap.getHeight();
      final int width = bitmap.getWidth();

      // The smaller dimension of a scaled bitmap can range from anywhere from 0 to just
      // below twice the length of a thumbnail image due to the way we calculate the optimal
      // sample size.
      if (height != width && Math.min(height, width) <= thumbnailSize * 2) {
        final int dimension = Math.min(height, width);
        bitmap = ThumbnailUtils.extractThumbnail(bitmap, dimension, dimension);
      }
      // make bitmap mutable and draw size onto it
      if (DEBUG_SIZES) {
        Bitmap original = bitmap;
        bitmap = bitmap.copy(bitmap.getConfig(), true);
        original.recycle();
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setTextSize(16);
        paint.setColor(Color.BLUE);
        paint.setStyle(Style.FILL);
        canvas.drawRect(0.0f, 0.0f, 50.0f, 20.0f, paint);
        paint.setColor(Color.WHITE);
        paint.setAntiAlias(true);
        canvas.drawText(bitmap.getWidth() + "/" + sampleSize, 0, 15, paint);
      }

      holder.decodedSampleSize = sampleSize;
      holder.bitmap = bitmap;
      holder.bitmapRef = new SoftReference<Bitmap>(bitmap);
      if (DEBUG) {
        LogUtil.d(
            "ContactPhotoManagerImpl.inflateBitmap",
            "inflateBitmap "
                + btk(bytes.length)
                + " -> "
                + bitmap.getWidth()
                + "x"
                + bitmap.getHeight()
                + ", "
                + btk(bitmap.getByteCount()));
      }
    } catch (OutOfMemoryError e) {
      // Do nothing - the photo will appear to be missing
    }
  }

  /** Dump cache stats on logcat. */
  private void dumpStats() {
    if (!DEBUG) {
      return;
    }
    {
      int numHolders = 0;
      int rawBytes = 0;
      int bitmapBytes = 0;
      int numBitmaps = 0;
      for (BitmapHolder h : bitmapHolderCache.snapshot().values()) {
        numHolders++;
        if (h.bytes != null) {
          rawBytes += h.bytes.length;
        }
        Bitmap b = h.bitmapRef != null ? h.bitmapRef.get() : null;
        if (b != null) {
          numBitmaps++;
          bitmapBytes += b.getByteCount();
        }
      }
      LogUtil.d(
          "ContactPhotoManagerImpl.dumpStats",
          "L1: "
              + btk(rawBytes)
              + " + "
              + btk(bitmapBytes)
              + " = "
              + btk(rawBytes + bitmapBytes)
              + ", "
              + numHolders
              + " holders, "
              + numBitmaps
              + " bitmaps, avg: "
              + btk(safeDiv(rawBytes, numHolders))
              + ","
              + btk(safeDiv(bitmapBytes, numBitmaps)));
      LogUtil.d(
          "ContactPhotoManagerImpl.dumpStats",
          "L1 Stats: "
              + bitmapHolderCache.toString()
              + ", overwrite: fresh="
              + freshCacheOverwrite.get()
              + " stale="
              + staleCacheOverwrite.get());
    }

    {
      int numBitmaps = 0;
      int bitmapBytes = 0;
      for (Bitmap b : bitmapCache.snapshot().values()) {
        numBitmaps++;
        bitmapBytes += b.getByteCount();
      }
      LogUtil.d(
          "ContactPhotoManagerImpl.dumpStats",
          "L2: "
              + btk(bitmapBytes)
              + ", "
              + numBitmaps
              + " bitmaps"
              + ", avg: "
              + btk(safeDiv(bitmapBytes, numBitmaps)));
      // We don't get from L2 cache, so L2 stats is meaningless.
    }
  }

  @Override
  public void onTrimMemory(int level) {
    if (DEBUG) {
      LogUtil.d("ContactPhotoManagerImpl.onTrimMemory", "onTrimMemory: " + level);
    }
    if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
      // Clear the caches.  Note all pending requests will be removed too.
      clear();
    }
  }

  @Override
  public void preloadPhotosInBackground() {
    ensureLoaderThread();
    loaderThread.requestPreloading();
  }

  @Override
  public void loadThumbnail(
      ImageView view,
      long photoId,
      boolean darkTheme,
      boolean isCircular,
      DefaultImageRequest defaultImageRequest,
      DefaultImageProvider defaultProvider) {
    if (photoId == 0) {
      // No photo is needed
      defaultProvider.applyDefaultImage(view, -1, darkTheme, defaultImageRequest);
      pendingRequests.remove(view);
    } else {
      if (DEBUG) {
        LogUtil.d("ContactPhotoManagerImpl.loadThumbnail", "loadPhoto request: " + photoId);
      }
      loadPhotoByIdOrUri(
          view, Request.createFromThumbnailId(photoId, darkTheme, isCircular, defaultProvider));
    }
  }

  @Override
  public void loadPhoto(
      ImageView view,
      Uri photoUri,
      int requestedExtent,
      boolean darkTheme,
      boolean isCircular,
      DefaultImageRequest defaultImageRequest,
      DefaultImageProvider defaultProvider) {
    if (photoUri == null) {
      // No photo is needed
      defaultProvider.applyDefaultImage(view, requestedExtent, darkTheme, defaultImageRequest);
      pendingRequests.remove(view);
      return;
    }
    if (isDrawableUri(photoUri)) {
      view.setImageURI(photoUri);
      pendingRequests.remove(view);
      return;
    }
    if (DEBUG) {
      LogUtil.d("ContactPhotoManagerImpl.loadPhoto", "loadPhoto request: " + photoUri);
    }

    if (isDefaultImageUri(photoUri)) {
      createAndApplyDefaultImageForUri(
          view, photoUri, requestedExtent, darkTheme, isCircular, defaultProvider);
    } else {
      loadPhotoByIdOrUri(
          view,
          Request.createFromUri(photoUri, requestedExtent, darkTheme, isCircular, defaultProvider));
    }
  }

  private static boolean isDrawableUri(Uri uri) {
    if (!ContentResolver.SCHEME_ANDROID_RESOURCE.equals(uri.getScheme())) {
      return false;
    }
    return uri.getPathSegments().get(0).equals("drawable");
  }

  private void createAndApplyDefaultImageForUri(
      ImageView view,
      Uri uri,
      int requestedExtent,
      boolean darkTheme,
      boolean isCircular,
      DefaultImageProvider defaultProvider) {
    DefaultImageRequest request = getDefaultImageRequestFromUri(uri);
    request.isCircular = isCircular;
    defaultProvider.applyDefaultImage(view, requestedExtent, darkTheme, request);
  }

  private void loadPhotoByIdOrUri(ImageView view, Request request) {
    boolean loaded = loadCachedPhoto(view, request, false);
    if (loaded) {
      pendingRequests.remove(view);
    } else {
      pendingRequests.put(view, request);
      if (!paused) {
        // Send a request to start loading photos
        requestLoading();
      }
    }
  }

  @Override
  public void removePhoto(ImageView view) {
    view.setImageDrawable(null);
    pendingRequests.remove(view);
  }

  /**
   * Cancels pending requests to load photos asynchronously for views inside {@param
   * fragmentRootView}. If {@param fragmentRootView} is null, cancels all requests.
   */
  @Override
  public void cancelPendingRequests(View fragmentRootView) {
    if (fragmentRootView == null) {
      pendingRequests.clear();
      return;
    }
    final Iterator<Entry<ImageView, Request>> iterator = pendingRequests.entrySet().iterator();
    while (iterator.hasNext()) {
      final ImageView imageView = iterator.next().getKey();
      // If an ImageView is orphaned (currently scrap) or a child of fragmentRootView, then
      // we can safely remove its request.
      if (imageView.getParent() == null || isChildView(fragmentRootView, imageView)) {
        iterator.remove();
      }
    }
  }

  @Override
  public void refreshCache() {
    if (bitmapHolderCacheAllUnfresh) {
      if (DEBUG) {
        LogUtil.d("ContactPhotoManagerImpl.refreshCache", "refreshCache -- no fresh entries.");
      }
      return;
    }
    if (DEBUG) {
      LogUtil.d("ContactPhotoManagerImpl.refreshCache", "refreshCache");
    }
    bitmapHolderCacheAllUnfresh = true;
    for (BitmapHolder holder : bitmapHolderCache.snapshot().values()) {
      if (holder != BITMAP_UNAVAILABLE) {
        holder.fresh = false;
      }
    }
  }

  /**
   * Checks if the photo is present in cache. If so, sets the photo on the view.
   *
   * @return false if the photo needs to be (re)loaded from the provider.
   */
  @UiThread
  private boolean loadCachedPhoto(ImageView view, Request request, boolean fadeIn) {
    BitmapHolder holder = bitmapHolderCache.get(request.getKey());
    if (holder == null) {
      // The bitmap has not been loaded ==> show default avatar
      request.applyDefaultImage(view, request.isCircular);
      return false;
    }

    if (holder.bytes == null) {
      request.applyDefaultImage(view, request.isCircular);
      return holder.fresh;
    }

    Bitmap cachedBitmap = holder.bitmapRef == null ? null : holder.bitmapRef.get();
    if (cachedBitmap == null) {
      request.applyDefaultImage(view, request.isCircular);
      return false;
    }

    final Drawable previousDrawable = view.getDrawable();
    if (fadeIn && previousDrawable != null) {
      final Drawable[] layers = new Drawable[2];
      // Prevent cascade of TransitionDrawables.
      if (previousDrawable instanceof TransitionDrawable) {
        final TransitionDrawable previousTransitionDrawable = (TransitionDrawable) previousDrawable;
        layers[0] =
            previousTransitionDrawable.getDrawable(
                previousTransitionDrawable.getNumberOfLayers() - 1);
      } else {
        layers[0] = previousDrawable;
      }
      layers[1] = getDrawableForBitmap(context.getResources(), cachedBitmap, request);
      TransitionDrawable drawable = new TransitionDrawable(layers);
      view.setImageDrawable(drawable);
      drawable.startTransition(FADE_TRANSITION_DURATION);
    } else {
      view.setImageDrawable(getDrawableForBitmap(context.getResources(), cachedBitmap, request));
    }

    // Put the bitmap in the LRU cache. But only do this for images that are small enough
    // (we require that at least six of those can be cached at the same time)
    if (cachedBitmap.getByteCount() < bitmapCache.maxSize() / 6) {
      bitmapCache.put(request.getKey(), cachedBitmap);
    }

    // Soften the reference
    holder.bitmap = null;

    return holder.fresh;
  }

  /**
   * Given a bitmap, returns a drawable that is configured to display the bitmap based on the
   * specified request.
   */
  private Drawable getDrawableForBitmap(Resources resources, Bitmap bitmap, Request request) {
    if (request.isCircular) {
      final RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(resources, bitmap);
      drawable.setAntiAlias(true);
      drawable.setCornerRadius(drawable.getIntrinsicHeight() / 2);
      return drawable;
    } else {
      return new BitmapDrawable(resources, bitmap);
    }
  }

  public void clear() {
    if (DEBUG) {
      LogUtil.d("ContactPhotoManagerImpl.clear", "clear");
    }
    pendingRequests.clear();
    bitmapHolderCache.evictAll();
    bitmapCache.evictAll();
  }

  @Override
  public void pause() {
    paused = true;
  }

  @Override
  public void resume() {
    paused = false;
    if (DEBUG) {
      dumpStats();
    }
    if (!pendingRequests.isEmpty()) {
      requestLoading();
    }
  }

  /**
   * Sends a message to this thread itself to start loading images. If the current view contains
   * multiple image views, all of those image views will get a chance to request their respective
   * photos before any of those requests are executed. This allows us to load images in bulk.
   */
  private void requestLoading() {
    if (!loadingRequested) {
      loadingRequested = true;
      mainThreadHandler.sendEmptyMessage(MESSAGE_REQUEST_LOADING);
    }
  }

  /** Processes requests on the main thread. */
  @Override
  public boolean handleMessage(Message msg) {
    switch (msg.what) {
      case MESSAGE_REQUEST_LOADING:
        {
          loadingRequested = false;
          if (!paused) {
            ensureLoaderThread();
            loaderThread.requestLoading();
          }
          return true;
        }

      case MESSAGE_PHOTOS_LOADED:
        {
          if (!paused) {
            processLoadedImages();
          }
          if (DEBUG) {
            dumpStats();
          }
          return true;
        }
      default:
        return false;
    }
  }

  public void ensureLoaderThread() {
    if (loaderThread == null) {
      loaderThread = new LoaderThread(context.getContentResolver());
      loaderThread.start();
    }
  }

  /**
   * Goes over pending loading requests and displays loaded photos. If some of the photos still
   * haven't been loaded, sends another request for image loading.
   */
  private void processLoadedImages() {
    final Iterator<Entry<ImageView, Request>> iterator = pendingRequests.entrySet().iterator();
    while (iterator.hasNext()) {
      final Entry<ImageView, Request> entry = iterator.next();
      // TODO: Temporarily disable contact photo fading in, until issues with
      // RoundedBitmapDrawables overlapping the default image drawables are resolved.
      final boolean loaded = loadCachedPhoto(entry.getKey(), entry.getValue(), false);
      if (loaded) {
        iterator.remove();
      }
    }

    softenCache();

    if (!pendingRequests.isEmpty()) {
      requestLoading();
    }
  }

  /**
   * Removes strong references to loaded bitmaps to allow them to be garbage collected if needed.
   * Some of the bitmaps will still be retained by {@link #bitmapCache}.
   */
  private void softenCache() {
    for (BitmapHolder holder : bitmapHolderCache.snapshot().values()) {
      holder.bitmap = null;
    }
  }

  /** Stores the supplied bitmap in cache. */
  private void cacheBitmap(Object key, byte[] bytes, boolean preloading, int requestedExtent) {
    if (DEBUG) {
      BitmapHolder prev = bitmapHolderCache.get(key);
      if (prev != null && prev.bytes != null) {
        LogUtil.d(
            "ContactPhotoManagerImpl.cacheBitmap",
            "overwriting cache: key=" + key + (prev.fresh ? " FRESH" : " stale"));
        if (prev.fresh) {
          freshCacheOverwrite.incrementAndGet();
        } else {
          staleCacheOverwrite.incrementAndGet();
        }
      }
      LogUtil.d(
          "ContactPhotoManagerImpl.cacheBitmap",
          "caching data: key=" + key + ", " + (bytes == null ? "<null>" : btk(bytes.length)));
    }
    BitmapHolder holder =
        new BitmapHolder(bytes, bytes == null ? -1 : BitmapUtil.getSmallerExtentFromBytes(bytes));

    // Unless this image is being preloaded, decode it right away while
    // we are still on the background thread.
    if (!preloading) {
      inflateBitmap(holder, requestedExtent);
    }

    if (bytes != null) {
      bitmapHolderCache.put(key, holder);
      if (bitmapHolderCache.get(key) != holder) {
        LogUtil.w("ContactPhotoManagerImpl.cacheBitmap", "bitmap too big to fit in cache.");
        bitmapHolderCache.put(key, BITMAP_UNAVAILABLE);
      }
    } else {
      bitmapHolderCache.put(key, BITMAP_UNAVAILABLE);
    }

    bitmapHolderCacheAllUnfresh = false;
  }

  /**
   * Populates an array of photo IDs that need to be loaded. Also decodes bitmaps that we have
   * already loaded
   */
  private void obtainPhotoIdsAndUrisToLoad(
      Set<Long> photoIds, Set<String> photoIdsAsStrings, Set<Request> uris) {
    photoIds.clear();
    photoIdsAsStrings.clear();
    uris.clear();

    boolean jpegsDecoded = false;

    /*
     * Since the call is made from the loader thread, the map could be
     * changing during the iteration. That's not really a problem:
     * ConcurrentHashMap will allow those changes to happen without throwing
     * exceptions. Since we may miss some requests in the situation of
     * concurrent change, we will need to check the map again once loading
     * is complete.
     */
    Iterator<Request> iterator = pendingRequests.values().iterator();
    while (iterator.hasNext()) {
      Request request = iterator.next();
      final BitmapHolder holder = bitmapHolderCache.get(request.getKey());
      if (holder == BITMAP_UNAVAILABLE) {
        continue;
      }
      if (holder != null
          && holder.bytes != null
          && holder.fresh
          && (holder.bitmapRef == null || holder.bitmapRef.get() == null)) {
        // This was previously loaded but we don't currently have the inflated Bitmap
        inflateBitmap(holder, request.getRequestedExtent());
        jpegsDecoded = true;
      } else {
        if (holder == null || !holder.fresh) {
          if (request.isUriRequest()) {
            uris.add(request);
          } else {
            photoIds.add(request.getId());
            photoIdsAsStrings.add(String.valueOf(request.id));
          }
        }
      }
    }

    if (jpegsDecoded) {
      mainThreadHandler.sendEmptyMessage(MESSAGE_PHOTOS_LOADED);
    }
  }

  /** Maintains the state of a particular photo. */
  private static class BitmapHolder {

    final byte[] bytes;
    final int originalSmallerExtent;

    volatile boolean fresh;
    Bitmap bitmap;
    Reference<Bitmap> bitmapRef;
    int decodedSampleSize;

    public BitmapHolder(byte[] bytes, int originalSmallerExtent) {
      this.bytes = bytes;
      this.fresh = true;
      this.originalSmallerExtent = originalSmallerExtent;
    }
  }

  /**
   * A holder for either a Uri or an id and a flag whether this was requested for the dark or light
   * theme
   */
  private static final class Request {

    private final long id;
    private final Uri uri;
    private final boolean darkTheme;
    private final int requestedExtent;
    private final DefaultImageProvider defaultProvider;
    /** Whether or not the contact photo is to be displayed as a circle */
    private final boolean isCircular;

    private Request(
        long id,
        Uri uri,
        int requestedExtent,
        boolean darkTheme,
        boolean isCircular,
        DefaultImageProvider defaultProvider) {
      this.id = id;
      this.uri = uri;
      this.darkTheme = darkTheme;
      this.isCircular = isCircular;
      this.requestedExtent = requestedExtent;
      this.defaultProvider = defaultProvider;
    }

    public static Request createFromThumbnailId(
        long id, boolean darkTheme, boolean isCircular, DefaultImageProvider defaultProvider) {
      return new Request(id, null /* no URI */, -1, darkTheme, isCircular, defaultProvider);
    }

    public static Request createFromUri(
        Uri uri,
        int requestedExtent,
        boolean darkTheme,
        boolean isCircular,
        DefaultImageProvider defaultProvider) {
      return new Request(
          0 /* no ID */, uri, requestedExtent, darkTheme, isCircular, defaultProvider);
    }

    public boolean isUriRequest() {
      return uri != null;
    }

    public Uri getUri() {
      return uri;
    }

    public long getId() {
      return id;
    }

    public int getRequestedExtent() {
      return requestedExtent;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + (int) (id ^ (id >>> 32));
      result = prime * result + requestedExtent;
      result = prime * result + ((uri == null) ? 0 : uri.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final Request that = (Request) obj;
      if (id != that.id) {
        return false;
      }
      if (requestedExtent != that.requestedExtent) {
        return false;
      }
      if (!UriUtils.areEqual(uri, that.uri)) {
        return false;
      }
      // Don't compare equality of mDarkTheme because it is only used in the default contact
      // photo case. When the contact does have a photo, the contact photo is the same
      // regardless of mDarkTheme, so we shouldn't need to put the photo request on the queue
      // twice.
      return true;
    }

    public Object getKey() {
      return uri == null ? id : uri;
    }

    /**
     * Applies the default image to the current view. If the request is URI-based, looks for the
     * contact type encoded fragment to determine if this is a request for a business photo, in
     * which case we will load the default business photo.
     *
     * @param view The current image view to apply the image to.
     * @param isCircular Whether the image is circular or not.
     */
    public void applyDefaultImage(ImageView view, boolean isCircular) {
      final DefaultImageRequest request;

      if (isCircular) {
        request =
            ContactPhotoManager.isBusinessContactUri(uri)
                ? DefaultImageRequest.EMPTY_CIRCULAR_BUSINESS_IMAGE_REQUEST
                : DefaultImageRequest.EMPTY_CIRCULAR_DEFAULT_IMAGE_REQUEST;
      } else {
        request =
            ContactPhotoManager.isBusinessContactUri(uri)
                ? DefaultImageRequest.EMPTY_DEFAULT_BUSINESS_IMAGE_REQUEST
                : DefaultImageRequest.EMPTY_DEFAULT_IMAGE_REQUEST;
      }
      defaultProvider.applyDefaultImage(view, requestedExtent, darkTheme, request);
    }
  }

  /** The thread that performs loading of photos from the database. */
  private class LoaderThread extends HandlerThread implements Callback {

    private static final int BUFFER_SIZE = 1024 * 16;
    private static final int MESSAGE_PRELOAD_PHOTOS = 0;
    private static final int MESSAGE_LOAD_PHOTOS = 1;

    /** A pause between preload batches that yields to the UI thread. */
    private static final int PHOTO_PRELOAD_DELAY = 1000;

    /** Number of photos to preload per batch. */
    private static final int PRELOAD_BATCH = 25;

    /**
     * Maximum number of photos to preload. If the cache size is 2Mb and the expected average size
     * of a photo is 4kb, then this number should be 2Mb/4kb = 500.
     */
    private static final int MAX_PHOTOS_TO_PRELOAD = 100;

    private static final int PRELOAD_STATUS_NOT_STARTED = 0;
    private static final int PRELOAD_STATUS_IN_PROGRESS = 1;
    private static final int PRELOAD_STATUS_DONE = 2;
    private final ContentResolver resolver;
    private final StringBuilder stringBuilder = new StringBuilder();
    private final Set<Long> photoIds = new HashSet<>();
    private final Set<String> photoIdsAsStrings = new HashSet<>();
    private final Set<Request> photoUris = new HashSet<>();
    private final List<Long> preloadPhotoIds = new ArrayList<>();
    private Handler loaderThreadHandler;
    private byte[] buffer;
    private int preloadStatus = PRELOAD_STATUS_NOT_STARTED;

    public LoaderThread(ContentResolver resolver) {
      super(LOADER_THREAD_NAME);
      this.resolver = resolver;
    }

    public void ensureHandler() {
      if (loaderThreadHandler == null) {
        loaderThreadHandler = new Handler(getLooper(), this);
      }
    }

    /**
     * Kicks off preloading of the next batch of photos on the background thread. Preloading will
     * happen after a delay: we want to yield to the UI thread as much as possible.
     *
     * <p>If preloading is already complete, does nothing.
     */
    public void requestPreloading() {
      if (preloadStatus == PRELOAD_STATUS_DONE) {
        return;
      }

      ensureHandler();
      if (loaderThreadHandler.hasMessages(MESSAGE_LOAD_PHOTOS)) {
        return;
      }

      loaderThreadHandler.sendEmptyMessageDelayed(MESSAGE_PRELOAD_PHOTOS, PHOTO_PRELOAD_DELAY);
    }

    /**
     * Sends a message to this thread to load requested photos. Cancels a preloading request, if
     * any: we don't want preloading to impede loading of the photos we need to display now.
     */
    public void requestLoading() {
      ensureHandler();
      loaderThreadHandler.removeMessages(MESSAGE_PRELOAD_PHOTOS);
      loaderThreadHandler.sendEmptyMessage(MESSAGE_LOAD_PHOTOS);
    }

    /**
     * Receives the above message, loads photos and then sends a message to the main thread to
     * process them.
     */
    @Override
    public boolean handleMessage(Message msg) {
      switch (msg.what) {
        case MESSAGE_PRELOAD_PHOTOS:
          preloadPhotosInBackground();
          break;
        case MESSAGE_LOAD_PHOTOS:
          loadPhotosInBackground();
          break;
      }
      return true;
    }

    /**
     * The first time it is called, figures out which photos need to be preloaded. Each subsequent
     * call preloads the next batch of photos and requests another cycle of preloading after a
     * delay. The whole process ends when we either run out of photos to preload or fill up cache.
     */
    @WorkerThread
    private void preloadPhotosInBackground() {
      if (!PermissionsUtil.hasPermission(context, android.Manifest.permission.READ_CONTACTS)) {
        return;
      }

      if (preloadStatus == PRELOAD_STATUS_DONE) {
        return;
      }

      if (preloadStatus == PRELOAD_STATUS_NOT_STARTED) {
        queryPhotosForPreload();
        if (preloadPhotoIds.isEmpty()) {
          preloadStatus = PRELOAD_STATUS_DONE;
        } else {
          preloadStatus = PRELOAD_STATUS_IN_PROGRESS;
        }
        requestPreloading();
        return;
      }

      if (bitmapHolderCache.size() > bitmapHolderCacheRedZoneBytes) {
        preloadStatus = PRELOAD_STATUS_DONE;
        return;
      }

      photoIds.clear();
      photoIdsAsStrings.clear();

      int count = 0;
      int preloadSize = preloadPhotoIds.size();
      while (preloadSize > 0 && photoIds.size() < PRELOAD_BATCH) {
        preloadSize--;
        count++;
        Long photoId = preloadPhotoIds.get(preloadSize);
        photoIds.add(photoId);
        photoIdsAsStrings.add(photoId.toString());
        preloadPhotoIds.remove(preloadSize);
      }

      loadThumbnails(true);

      if (preloadSize == 0) {
        preloadStatus = PRELOAD_STATUS_DONE;
      }

      LogUtil.v(
          "ContactPhotoManagerImpl.preloadPhotosInBackground",
          "preloaded " + count + " photos.  cached bytes: " + bitmapHolderCache.size());

      requestPreloading();
    }

    @WorkerThread
    private void queryPhotosForPreload() {
      Cursor cursor = null;
      try {
        Uri uri =
            Contacts.CONTENT_URI
                .buildUpon()
                .appendQueryParameter(
                    ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(Directory.DEFAULT))
                .appendQueryParameter(
                    ContactsContract.LIMIT_PARAM_KEY, String.valueOf(MAX_PHOTOS_TO_PRELOAD))
                .build();
        cursor =
            resolver.query(
                uri,
                new String[] {Contacts.PHOTO_ID},
                Contacts.PHOTO_ID + " NOT NULL AND " + Contacts.PHOTO_ID + "!=0",
                null,
                Contacts.STARRED + " DESC, " + Contacts.LAST_TIME_CONTACTED + " DESC");

        if (cursor != null) {
          while (cursor.moveToNext()) {
            // Insert them in reverse order, because we will be taking
            // them from the end of the list for loading.
            preloadPhotoIds.add(0, cursor.getLong(0));
          }
        }
      } finally {
        if (cursor != null) {
          cursor.close();
        }
      }
    }

    @WorkerThread
    private void loadPhotosInBackground() {
      if (!PermissionsUtil.hasPermission(context, android.Manifest.permission.READ_CONTACTS)) {
        return;
      }
      obtainPhotoIdsAndUrisToLoad(photoIds, photoIdsAsStrings, photoUris);
      loadThumbnails(false);
      loadUriBasedPhotos();
      requestPreloading();
    }

    /** Loads thumbnail photos with ids */
    @WorkerThread
    private void loadThumbnails(boolean preloading) {
      if (photoIds.isEmpty()) {
        return;
      }

      // Remove loaded photos from the preload queue: we don't want
      // the preloading process to load them again.
      if (!preloading && preloadStatus == PRELOAD_STATUS_IN_PROGRESS) {
        for (Long id : photoIds) {
          preloadPhotoIds.remove(id);
        }
        if (preloadPhotoIds.isEmpty()) {
          preloadStatus = PRELOAD_STATUS_DONE;
        }
      }

      stringBuilder.setLength(0);
      stringBuilder.append(Photo._ID + " IN(");
      for (int i = 0; i < photoIds.size(); i++) {
        if (i != 0) {
          stringBuilder.append(',');
        }
        stringBuilder.append('?');
      }
      stringBuilder.append(')');

      Cursor cursor = null;
      try {
        if (DEBUG) {
          LogUtil.d(
              "ContactPhotoManagerImpl.loadThumbnails",
              "loading " + TextUtils.join(",", photoIdsAsStrings));
        }
        cursor =
            resolver.query(
                Data.CONTENT_URI,
                COLUMNS,
                stringBuilder.toString(),
                photoIdsAsStrings.toArray(EMPTY_STRING_ARRAY),
                null);

        if (cursor != null) {
          while (cursor.moveToNext()) {
            Long id = cursor.getLong(0);
            byte[] bytes = cursor.getBlob(1);
            cacheBitmap(id, bytes, preloading, -1);
            photoIds.remove(id);
          }
        }
      } finally {
        if (cursor != null) {
          cursor.close();
        }
      }

      // Remaining photos were not found in the contacts database (but might be in profile).
      for (Long id : photoIds) {
        if (ContactsContract.isProfileId(id)) {
          Cursor profileCursor = null;
          try {
            profileCursor =
                resolver.query(
                    ContentUris.withAppendedId(Data.CONTENT_URI, id), COLUMNS, null, null, null);
            if (profileCursor != null && profileCursor.moveToFirst()) {
              cacheBitmap(profileCursor.getLong(0), profileCursor.getBlob(1), preloading, -1);
            } else {
              // Couldn't load a photo this way either.
              cacheBitmap(id, null, preloading, -1);
            }
          } finally {
            if (profileCursor != null) {
              profileCursor.close();
            }
          }
        } else {
          // Not a profile photo and not found - mark the cache accordingly
          cacheBitmap(id, null, preloading, -1);
        }
      }

      mainThreadHandler.sendEmptyMessage(MESSAGE_PHOTOS_LOADED);
    }

    /**
     * Loads photos referenced with Uris. Those can be remote thumbnails (from directory searches),
     * display photos etc
     */
    @WorkerThread
    private void loadUriBasedPhotos() {
      for (Request uriRequest : photoUris) {
        // Keep the original URI and use this to key into the cache.  Failure to do so will
        // result in an image being continually reloaded into cache if the original URI
        // has a contact type encodedFragment (eg nearby places business photo URLs).
        Uri originalUri = uriRequest.getUri();

        // Strip off the "contact type" we added to the URI to ensure it was identifiable as
        // a business photo -- there is no need to pass this on to the server.
        Uri uri = ContactPhotoManager.removeContactType(originalUri);

        if (buffer == null) {
          buffer = new byte[BUFFER_SIZE];
        }
        try {
          if (DEBUG) {
            LogUtil.d("ContactPhotoManagerImpl.loadUriBasedPhotos", "loading " + uri);
          }
          final String scheme = uri.getScheme();
          InputStream is = null;
          if (scheme.equals("http") || scheme.equals("https")) {
            TrafficStats.setThreadStatsTag(TrafficStatsTags.CONTACT_PHOTO_DOWNLOAD_TAG);
            try {
              final HttpURLConnection connection =
                  (HttpURLConnection) new URL(uri.toString()).openConnection();

              // Include the user agent if it is specified.
              if (!TextUtils.isEmpty(userAgent)) {
                connection.setRequestProperty("User-Agent", userAgent);
              }
              try {
                is = connection.getInputStream();
              } catch (IOException e) {
                connection.disconnect();
                is = null;
              }
            } finally {
              TrafficStats.clearThreadStatsTag();
            }
          } else {
            is = resolver.openInputStream(uri);
          }
          if (is != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
              int size;
              while ((size = is.read(buffer)) != -1) {
                baos.write(buffer, 0, size);
              }
            } finally {
              is.close();
            }
            cacheBitmap(originalUri, baos.toByteArray(), false, uriRequest.getRequestedExtent());
            mainThreadHandler.sendEmptyMessage(MESSAGE_PHOTOS_LOADED);
          } else {
            LogUtil.v("ContactPhotoManagerImpl.loadUriBasedPhotos", "cannot load photo " + uri);
            cacheBitmap(originalUri, null, false, uriRequest.getRequestedExtent());
          }
        } catch (final Exception | OutOfMemoryError ex) {
          LogUtil.v("ContactPhotoManagerImpl.loadUriBasedPhotos", "cannot load photo " + uri, ex);
          cacheBitmap(originalUri, null, false, uriRequest.getRequestedExtent());
        }
      }
    }
  }
}
