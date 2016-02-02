/*
 * Copyright (C) 2016 The CyanogenMod Project
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

package com.android.dialer.calllog;

import android.content.Context;
import android.net.Uri;
import android.view.View;


import com.android.dialer.DeepLinkIntegrationManager;

import com.cyanogen.ambient.common.api.ResultCallback;
import com.cyanogen.ambient.deeplink.applicationtype.DeepLinkApplicationType;
import com.cyanogen.ambient.deeplink.DeepLink;
import com.cyanogen.ambient.deeplink.DeepLink.DeepLinkResultList;
import com.cyanogen.ambient.deeplink.linkcontent.DeepLinkContentType;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class DeepLinkAssistant {
    private WeakReference<CallLogListItemViewHolder> mViews;
    private WeakReference<Context> mContext;
    private static DeepLinkAssistant sInstance;
    public static DeepLinkAssistant getInstance(CallLogListItemViewHolder holder, Context context) {
        if(sInstance == null) {
            sInstance = new DeepLinkAssistant();
        }
        sInstance.setContext(context);
        sInstance.setViews(holder);
        return sInstance;
    }

    private DeepLinkAssistant() {}

    private  void setContext(Context context) {
        mContext = new WeakReference<Context>(context);
    }

    private  void setViews(CallLogListItemViewHolder holder) {
        mViews = new WeakReference<CallLogListItemViewHolder>(holder);
    }

    public void handleDeepLink(List<DeepLink> links) {
        if(referencesAreValid() && links != null && links.size() > 0) {
            CallLogListItemViewHolder holder = mViews.get();
            for(DeepLink link: links) {
                if(link != null && link.getApplicationType() == DeepLinkApplicationType.NOTE &&
                        link.getIcon()!= DeepLink.DEFAULT_ICON) {
                    holder.mDeepLink = link;
                    holder.phoneCallDetailsViews.noteIconView.setVisibility(
                            android.view.View.VISIBLE);
                    holder.phoneCallDetailsViews.noteIconView
                            .setImageBitmap(holder.mDeepLink.getBitmapIcon(mContext.get()));
                }
            }
        }
    }

    private boolean referencesAreValid() {
        return mViews.get() != null && mContext.get() != null;
    }

    private void handleReadyForRequests(String number,
                                        ResultCallback<DeepLinkResultList> callback) {
        if(referencesAreValid() && mViews.get().mDeepLink == null) {
            List<Uri> uris = buildCallUris(number);
            DeepLinkIntegrationManager.getInstance()
                    .getPreferredLinksForList(callback, DeepLinkContentType.CALL, uris);
        } else {
            updateViews();
        }
    }

    private List<Uri> buildCallUris(String number) {

        List<Uri> uris = new ArrayList<Uri>();
        if(referencesAreValid() ) {
            Uri toUse;
            long[] callTimes = mViews.get().callTimes;
            for (int i = 0; i < callTimes.length; i++) {
                toUse = DeepLinkIntegrationManager.generateCallUri(number, callTimes[i]);
                uris.add(toUse);
            }
        }
        return uris;
    }

    private void updateViews() {
        if(referencesAreValid()) {
            CallLogListItemViewHolder holder = mViews.get();

            holder.phoneCallDetailsViews.noteIconView.setVisibility(View.VISIBLE);
            holder.phoneCallDetailsViews.nameWrapper.requestLayout();
            holder.phoneCallDetailsViews.noteIconView
                    .setImageBitmap(holder.mDeepLink.getBitmapIcon
                            (mContext.get()));
        }
    }
    private final ResultCallback<DeepLinkResultList> mDeepLinkCallback = new
            ResultCallback<DeepLinkResultList> () {
                @Override
                public void onResult(DeepLinkResultList deepLinkResult) {
                    handleDeepLink(deepLinkResult.getResults());
                }
            };

    public void prepareUi(final String number) {
        handleReadyForRequests(number, mDeepLinkCallback);
    }

}
