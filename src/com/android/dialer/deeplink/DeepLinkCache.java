/*
 * Copyright (C) 2016 CyanogenMod
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
 * limitations under the License
 */

package com.android.dialer.deeplink;

import android.net.Uri;
import android.os.Handler;
import android.os.Message;

import com.android.dialer.util.ExpirableCache;

import com.cyanogen.ambient.common.api.ResultCallback;
import com.cyanogen.ambient.deeplink.DeepLink;
import com.cyanogen.ambient.deeplink.applicationtype.DeepLinkApplicationType;
import com.cyanogen.ambient.deeplink.linkcontent.DeepLinkContentType;

import com.android.incallui.DeepLinkIntegrationManager;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Cache for managing DeepLinks for the CallLogAdapter and anyone else interested in caching
 * DeepLinks.
 *
 * Expects queries to provide a number and call time.
 */
public class DeepLinkCache {

    private static final int START_THREAD = 0;
    private static final int REDRAW = 1;
    private static final int DEEP_LINK_CACHE_SIZE = 100;
    private static final int START_PROCESSING_REQUESTS_DELAY_MS = 1000;

    private DeepLinkListener mDeepLinkListener;
    private final LinkedList<DeepLinkRequest> mRequests;
    private ExpirableCache<String, DeepLink> mCache;

    private QueryThread mDeepLinkQueryThread;
    private boolean mRequestProcessingDisabled = false;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REDRAW:
                    mDeepLinkListener.onDeepLinkCacheChanged();
                    break;
                case START_THREAD:
                    startRequestProcessing();
                    break;
            }
        }
    };

    /**
     * Handles requests for DeepLinks as related to a number and call time.
     */
    private class QueryThread extends Thread {
        private volatile boolean mDone = false;

        public QueryThread() {
            super("DeepLinkCache.QueryThread");
        }

        public void stopProcessing() {
            mDone = true;
            interrupt();
        }

        @Override
        public void run() {
            while (true) {
                // Check if thread is finished, and if so return immediately.
                if (mDone) return;
                // Obtain next request, if any is available.
                // Keep synchronized section small.
                DeepLinkRequest req = null;
                synchronized (mRequests) {
                    if (!mRequests.isEmpty()) {
                        req = mRequests.removeFirst();
                    }
                }
                if (req != null) {
                    // Process the request.
                    queryDeepLinks(req);
                } else {
                    // Wait until another request is available, or until this
                    // thread is no longer needed (as indicated by being
                    // interrupted).
                    try {
                        synchronized (mRequests) {
                            mRequests.wait(1000);
                        }
                    } catch (InterruptedException ie) {
                        // Ignore, and attempt to continue processing requests.
                    }
                }
            }
        }
    }

    public DeepLinkCache(DeepLinkListener listener) {
        mRequests = new LinkedList<DeepLinkRequest>();
        mCache = ExpirableCache.create(DEEP_LINK_CACHE_SIZE);
        mDeepLinkListener = listener;
    }

    public DeepLink getValue(String number, long[] times) {
        long mostRecentCall = Long.MIN_VALUE;
        DeepLink toReturn = null;
        List<Uri> urisToRequest = new ArrayList<Uri>();
        boolean immediate = false;
        // for all calls
        for(Long callTime : times) {
            // generate the URI
            Uri uri = DeepLinkIntegrationManager.generateCallUri(number, callTime);
            String uriString = uri.toString();
            // hit the cache, do we have a link for this call?
            ExpirableCache.CachedValue<DeepLink> cachedInfo =
                    mCache.getCachedValue(uriString);
            // if so is that a null object?
            DeepLink info = cachedInfo == null ? null : cachedInfo.getValue();
            if(cachedInfo == null) {
                // if its null we need to add a uri to our requests
                mCache.put(uriString, DeepLinkRequest.EMPTY);
                urisToRequest.add(uri);
                // if we get any uris that haven't been handled we need to immediately do this query
                immediate = true;
            } else if (cachedInfo.isExpired()) {
                urisToRequest.add(uri);
            }
            if(info != null && info != DeepLinkRequest.EMPTY && callTime > mostRecentCall) {
                mostRecentCall = callTime;
                toReturn = info;
            }
        }
        // issue new requests for any uri's we haven't handled previously
        if(urisToRequest.size() > 0) {
            enqueueRequest(urisToRequest, immediate);
        }
        return toReturn;
    }


    protected void enqueueRequest(List<Uri> uris,
                                  boolean immediate) {
        DeepLinkRequest request = new DeepLinkRequest(uris);
        synchronized (mRequests) {
            if (!mRequests.contains(request)) {
                mRequests.add(request);
                mRequests.notifyAll();
            }
        }
        if (immediate) {
            startRequestProcessing();
        }
    }



    private synchronized void startRequestProcessing() {
        if (mRequestProcessingDisabled) return;

        // If a thread is already started, don't start another.
        if (mDeepLinkQueryThread != null) {
            return;
        }

        mDeepLinkQueryThread = new QueryThread();
        mDeepLinkQueryThread.setPriority(Thread.MIN_PRIORITY);
        mDeepLinkQueryThread.start();
    }

    /**
     * Stops the background thread that processes updates and cancels any
     * pending requests to start it.
     */
    private synchronized void stopRequestProcessing() {
        // Remove any pending requests to start the processing thread.
        mHandler.removeMessages(START_THREAD);
        if (mDeepLinkQueryThread != null) {
            // Stop the thread; we are finished with it.
            mDeepLinkQueryThread.stopProcessing();
            mDeepLinkQueryThread = null;
        }
    }

    /**
     * Expire the cache in its entirety.
     */
    public void invalidate() {
        mCache.expireAll();
        stopRequestProcessing();
    }

    /**
     * After a delay, start the thread to begin processing requests. We perform lookups on a
     * background thread, but this must be called to indicate the thread should be running.
     */
    public void start() {
        // Schedule a thread-creation message if the thread hasn't been created yet, as an
        // optimization to queue fewer messages.
        if (mDeepLinkQueryThread == null) {
            // TODO: Check whether this delay before starting to process is necessary.
            mHandler.sendEmptyMessageDelayed(START_THREAD, START_PROCESSING_REQUESTS_DELAY_MS);
        }
    }

    /**
     * Stops the thread and clears the queue of messages to process. This cleans up the thread
     * for lookups so that it is not perpetually running.
     */
    public void stop() {
        stopRequestProcessing();
    }

    ResultCallback<DeepLink.DeepLinkResultList> mDeepLinkCallback = new ResultCallback<DeepLink
            .DeepLinkResultList>() {
        @Override
        public void onResult(DeepLink.DeepLinkResultList result) {
            if(result.getResults() != null) {
                for(DeepLink link: result.getResults()) {
                    if(link != null && link.getApplicationType() == DeepLinkApplicationType.NOTE &&
                            link.getAlreadyHasContent()) {
                        cachLink(link);
                        break;
                    }
                }
            }
        }
    };

    private void cachLink(DeepLink link) {
        DeepLink oldLink = mCache.getPossiblyExpired(link.getUri().toString());
        if(!oldLink.equals(link)) {
            mCache.put(link.getUri().toString(), link);
            mDeepLinkListener.onDeepLinkCacheChanged();
        }
    }

    /**
     * Kick off an ambient query for a given request.
     * @param request - the DeepLinkRequest to query against.
     */
    private void queryDeepLinks(DeepLinkRequest request) {
        for(Uri uri: request.getUris()) {
            DeepLinkIntegrationManager.getInstance().getPreferredLinksFor(mDeepLinkCallback,
                    DeepLinkContentType.CALL, uri);
        }
    }
}
