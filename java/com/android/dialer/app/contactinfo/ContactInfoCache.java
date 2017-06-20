/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.dialer.app.contactinfo;

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.ContactSource.Type;
import com.android.dialer.oem.CequintCallerIdManager;
import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.phonenumbercache.ContactInfoHelper;
import com.android.dialer.util.ExpirableCache;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * This is a cache of contact details for the phone numbers in the call log. The key is the phone
 * number with the country in which the call was placed or received. The content of the cache is
 * expired (but not purged) whenever the application comes to the foreground.
 *
 * <p>This cache queues request for information and queries for information on a background thread,
 * so {@code start()} and {@code stop()} must be called to initiate or halt that thread's exeuction
 * as needed.
 *
 * <p>TODO: Explore whether there is a pattern to remove external dependencies for starting and
 * stopping the query thread.
 */
public class ContactInfoCache {

  private static final int REDRAW = 1;
  private static final int START_THREAD = 2;
  private static final int START_PROCESSING_REQUESTS_DELAY_MS = 1000;

  private final ExpirableCache<NumberWithCountryIso, ContactInfo> mCache;
  private final ContactInfoHelper mContactInfoHelper;
  private final OnContactInfoChangedListener mOnContactInfoChangedListener;
  private final BlockingQueue<ContactInfoRequest> mUpdateRequests;
  private final Handler mHandler;
  private CequintCallerIdManager mCequintCallerIdManager;
  private QueryThread mContactInfoQueryThread;
  private volatile boolean mRequestProcessingDisabled = false;

  private static class InnerHandler extends Handler {

    private final WeakReference<ContactInfoCache> contactInfoCacheWeakReference;

    public InnerHandler(WeakReference<ContactInfoCache> contactInfoCacheWeakReference) {
      this.contactInfoCacheWeakReference = contactInfoCacheWeakReference;
    }

    @Override
    public void handleMessage(Message msg) {
      ContactInfoCache reference = contactInfoCacheWeakReference.get();
      if (reference == null) {
        return;
      }
      switch (msg.what) {
        case REDRAW:
          reference.mOnContactInfoChangedListener.onContactInfoChanged();
          break;
        case START_THREAD:
          reference.startRequestProcessing();
          break;
        default: // fall out
      }
    }
  }

  public ContactInfoCache(
      @NonNull ExpirableCache<NumberWithCountryIso, ContactInfo> internalCache,
      @NonNull ContactInfoHelper contactInfoHelper,
      @NonNull OnContactInfoChangedListener listener) {
    mCache = internalCache;
    mContactInfoHelper = contactInfoHelper;
    mOnContactInfoChangedListener = listener;
    mUpdateRequests = new PriorityBlockingQueue<>();
    mHandler = new InnerHandler(new WeakReference<>(this));
  }

  public void setCequintCallerIdManager(CequintCallerIdManager cequintCallerIdManager) {
    mCequintCallerIdManager = cequintCallerIdManager;
  }

  public ContactInfo getValue(
      String number,
      String countryIso,
      ContactInfo callLogContactInfo,
      boolean remoteLookupIfNotFoundLocally) {
    NumberWithCountryIso numberCountryIso = new NumberWithCountryIso(number, countryIso);
    ExpirableCache.CachedValue<ContactInfo> cachedInfo = mCache.getCachedValue(numberCountryIso);
    ContactInfo info = cachedInfo == null ? null : cachedInfo.getValue();
    int requestType =
        remoteLookupIfNotFoundLocally
            ? ContactInfoRequest.TYPE_LOCAL_AND_REMOTE
            : ContactInfoRequest.TYPE_LOCAL;
    if (cachedInfo == null) {
      mCache.put(numberCountryIso, ContactInfo.EMPTY);
      // Use the cached contact info from the call log.
      info = callLogContactInfo;
      // The db request should happen on a non-UI thread.
      // Request the contact details immediately since they are currently missing.
      enqueueRequest(number, countryIso, callLogContactInfo, /* immediate */ true, requestType);
      // We will format the phone number when we make the background request.
    } else {
      if (cachedInfo.isExpired()) {
        // The contact info is no longer up to date, we should request it. However, we
        // do not need to request them immediately.
        enqueueRequest(number, countryIso, callLogContactInfo, /* immediate */ false, requestType);
      } else if (!callLogInfoMatches(callLogContactInfo, info)) {
        // The call log information does not match the one we have, look it up again.
        // We could simply update the call log directly, but that needs to be done in a
        // background thread, so it is easier to simply request a new lookup, which will, as
        // a side-effect, update the call log.
        enqueueRequest(number, countryIso, callLogContactInfo, /* immediate */ false, requestType);
      }

      if (Objects.equals(info, ContactInfo.EMPTY)) {
        // Use the cached contact info from the call log.
        info = callLogContactInfo;
      }
    }
    return info;
  }

  /**
   * Queries the appropriate content provider for the contact associated with the number.
   *
   * <p>Upon completion it also updates the cache in the call log, if it is different from {@code
   * callLogInfo}.
   *
   * <p>The number might be either a SIP address or a phone number.
   *
   * <p>It returns true if it updated the content of the cache and we should therefore tell the view
   * to update its content.
   */
  private boolean queryContactInfo(ContactInfoRequest request) {
    LogUtil.d(
        "ContactInfoCache.queryContactInfo",
        "request number: %s, type: %d",
        LogUtil.sanitizePhoneNumber(request.number),
        request.type);
    ContactInfo info;
    if (request.isLocalRequest()) {
      info = mContactInfoHelper.lookupNumber(request.number, request.countryIso);
      if (info != null && !info.contactExists) {
        // TODO: Maybe skip look up if it's already available in cached number lookup
        // service.
        long start = SystemClock.elapsedRealtime();
        mContactInfoHelper.updateFromCequintCallerId(mCequintCallerIdManager, info, request.number);
        long time = SystemClock.elapsedRealtime() - start;
        LogUtil.d(
            "ContactInfoCache.queryContactInfo", "Cequint Caller Id look up takes %d ms", time);
      }
      if (request.type == ContactInfoRequest.TYPE_LOCAL_AND_REMOTE) {
        if (!mContactInfoHelper.hasName(info)) {
          enqueueRequest(
              request.number,
              request.countryIso,
              request.callLogInfo,
              true,
              ContactInfoRequest.TYPE_REMOTE);
          return false;
        }
      }
    } else {
      info = mContactInfoHelper.lookupNumberInRemoteDirectory(request.number, request.countryIso);
    }

    if (info == null) {
      // The lookup failed, just return without requesting to update the view.
      return false;
    }

    // Check the existing entry in the cache: only if it has changed we should update the
    // view.
    NumberWithCountryIso numberCountryIso =
        new NumberWithCountryIso(request.number, request.countryIso);
    ContactInfo existingInfo = mCache.getPossiblyExpired(numberCountryIso);

    final boolean isRemoteSource = info.sourceType != Type.UNKNOWN_SOURCE_TYPE;

    // Don't force redraw if existing info in the cache is equal to {@link ContactInfo#EMPTY}
    // to avoid updating the data set for every new row that is scrolled into view.

    // Exception: Photo uris for contacts from remote sources are not cached in the call log
    // cache, so we have to force a redraw for these contacts regardless.
    boolean updated =
        (!Objects.equals(existingInfo, ContactInfo.EMPTY) || isRemoteSource)
            && !info.equals(existingInfo);

    // Store the data in the cache so that the UI thread can use to display it. Store it
    // even if it has not changed so that it is marked as not expired.
    mCache.put(numberCountryIso, info);

    // Update the call log even if the cache it is up-to-date: it is possible that the cache
    // contains the value from a different call log entry.
    mContactInfoHelper.updateCallLogContactInfo(
        request.number, request.countryIso, info, request.callLogInfo);
    if (!request.isLocalRequest()) {
      mContactInfoHelper.updateCachedNumberLookupService(info);
    }
    return updated;
  }

  /**
   * After a delay, start the thread to begin processing requests. We perform lookups on a
   * background thread, but this must be called to indicate the thread should be running.
   */
  public void start() {
    // Schedule a thread-creation message if the thread hasn't been created yet, as an
    // optimization to queue fewer messages.
    if (mContactInfoQueryThread == null) {
      // TODO: Check whether this delay before starting to process is necessary.
      mHandler.sendEmptyMessageDelayed(START_THREAD, START_PROCESSING_REQUESTS_DELAY_MS);
    }
  }

  /**
   * Stops the thread and clears the queue of messages to process. This cleans up the thread for
   * lookups so that it is not perpetually running.
   */
  public void stop() {
    stopRequestProcessing();
  }

  /**
   * Starts a background thread to process contact-lookup requests, unless one has already been
   * started.
   */
  private synchronized void startRequestProcessing() {
    // For unit-testing.
    if (mRequestProcessingDisabled) {
      return;
    }

    // If a thread is already started, don't start another.
    if (mContactInfoQueryThread != null) {
      return;
    }

    mContactInfoQueryThread = new QueryThread();
    mContactInfoQueryThread.setPriority(Thread.MIN_PRIORITY);
    mContactInfoQueryThread.start();
  }

  public void invalidate() {
    mCache.expireAll();
    stopRequestProcessing();
  }

  /**
   * Stops the background thread that processes updates and cancels any pending requests to start
   * it.
   */
  private synchronized void stopRequestProcessing() {
    // Remove any pending requests to start the processing thread.
    mHandler.removeMessages(START_THREAD);
    if (mContactInfoQueryThread != null) {
      // Stop the thread; we are finished with it.
      mContactInfoQueryThread.stopProcessing();
      mContactInfoQueryThread.interrupt();
      mContactInfoQueryThread = null;
    }
  }

  /**
   * Enqueues a request to look up the contact details for the given phone number.
   *
   * <p>It also provides the current contact info stored in the call log for this number.
   *
   * <p>If the {@code immediate} parameter is true, it will start immediately the thread that looks
   * up the contact information (if it has not been already started). Otherwise, it will be started
   * with a delay. See {@link #START_PROCESSING_REQUESTS_DELAY_MS}.
   */
  private void enqueueRequest(
      String number,
      String countryIso,
      ContactInfo callLogInfo,
      boolean immediate,
      @ContactInfoRequest.TYPE int type) {
    ContactInfoRequest request = new ContactInfoRequest(number, countryIso, callLogInfo, type);
    if (!mUpdateRequests.contains(request)) {
      mUpdateRequests.offer(request);
    }

    if (immediate) {
      startRequestProcessing();
    }
  }

  /** Checks whether the contact info from the call log matches the one from the contacts db. */
  private boolean callLogInfoMatches(ContactInfo callLogInfo, ContactInfo info) {
    // The call log only contains a subset of the fields in the contacts db. Only check those.
    return TextUtils.equals(callLogInfo.name, info.name)
        && callLogInfo.type == info.type
        && TextUtils.equals(callLogInfo.label, info.label);
  }

  /** Sets whether processing of requests for contact details should be enabled. */
  public void disableRequestProcessing() {
    mRequestProcessingDisabled = true;
  }

  @VisibleForTesting
  public void injectContactInfoForTest(String number, String countryIso, ContactInfo contactInfo) {
    NumberWithCountryIso numberCountryIso = new NumberWithCountryIso(number, countryIso);
    mCache.put(numberCountryIso, contactInfo);
  }

  public interface OnContactInfoChangedListener {

    void onContactInfoChanged();
  }

  /*
   * Handles requests for contact name and number type.
   */
  private class QueryThread extends Thread {

    private volatile boolean mDone = false;

    public QueryThread() {
      super("ContactInfoCache.QueryThread");
    }

    public void stopProcessing() {
      mDone = true;
    }

    @Override
    public void run() {
      boolean shouldRedraw = false;
      while (true) {
        // Check if thread is finished, and if so return immediately.
        if (mDone) {
          return;
        }

        try {
          ContactInfoRequest request = mUpdateRequests.take();
          shouldRedraw |= queryContactInfo(request);
          if (shouldRedraw
              && (mUpdateRequests.isEmpty()
                  || (request.isLocalRequest() && !mUpdateRequests.peek().isLocalRequest()))) {
            shouldRedraw = false;
            mHandler.sendEmptyMessage(REDRAW);
          }
        } catch (InterruptedException e) {
          // Ignore and attempt to continue processing requests
        }
      }
    }
  }
}
