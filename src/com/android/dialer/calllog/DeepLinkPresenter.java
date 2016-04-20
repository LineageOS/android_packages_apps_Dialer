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

import com.cyanogen.ambient.common.api.ResultCallback;
import com.cyanogen.ambient.deeplink.DeepLink;
import com.cyanogen.ambient.deeplink.DeepLink.DeepLinkResultList;
import com.cyanogen.ambient.deeplink.linkcontent.DeepLinkContentType;
import com.cyanogen.ambient.deeplink.applicationtype.DeepLinkApplicationType;

import com.android.dialer.deeplink.DeepLinkIntegrationManager;

import java.util.List;
import java.util.ArrayList;

public class DeepLinkPresenter {

    private CallLogListItemViewHolder mViews;
    Context mContext;

    public DeepLinkPresenter(Context context) {
        mContext = context;
    }
    public void setCallLogViewHolder(CallLogListItemViewHolder holder) {
        mViews = holder;
    }

    public void handleDeepLink(List<DeepLink> links) {
        if (links != null) {
            for (DeepLink link : links) {
                if (link != null && link.getApplicationType() == DeepLinkApplicationType.NOTE
                        && link.getIcon() != DeepLink.DEFAULT_ICON) {
                    mViews.mDeepLink = link;
                    updateViews();
                    break;
                }
            }
        }
    }

    public void handleReadyForRequests(String number,
            ResultCallback<DeepLinkResultList> deepLinkCallback) {
        if (mViews.mDeepLink == null) {
            List<Uri> uris = buildCallUris(number);
            DeepLinkIntegrationManager.getInstance().getPreferredLinksForList(deepLinkCallback,
                    DeepLinkContentType.CALL, uris);
        } else {
            updateViews();
        }
    }

    private List<Uri> buildCallUris(String number) {
        List<Uri> uris = new ArrayList<Uri>(mViews.callTimes.length);
        for (int i = 0; i < mViews.callTimes.length; i++) {
            uris.add(DeepLinkIntegrationManager.generateCallUri(number, mViews.callTimes[i]));
        }
        return uris;
    }

    private void updateViews() {
        mViews.phoneCallDetailsViews.noteIconView.setVisibility(View.VISIBLE);
        mViews.phoneCallDetailsViews.noteIconView.setImageDrawable(
                mViews.mDeepLink.getDrawableIcon(mContext));
    }

    private final ResultCallback<DeepLinkResultList> deepLinkCallback = new
            ResultCallback<DeepLinkResultList>() {
                @Override
                public void onResult(DeepLinkResultList deepLinkResult) {
                    handleDeepLink(deepLinkResult.getResults());
                }
            };

    public void prepareUi(final String number) {
        handleReadyForRequests(number, deepLinkCallback);
    }

}
