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

import android.content.ComponentName;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import com.android.dialer.R;
import com.android.dialer.deeplink.DeepLinkCache;
import com.android.dialer.deeplink.DeepLinkRequest;
import com.cyanogen.ambient.deeplink.DeepLink;

import com.android.dialer.deeplink.DeepLinkIntegrationManager;

public class DeepLinkPresenter {
    private final Context mContext;
    private DeepLink mDeepLink;
    private final CallLogListItemViewHolder mViews;
    private final DeepLinkCache mCache;

    public DeepLinkPresenter(Context context, CallLogListItemViewHolder holder,
            DeepLinkCache cache) {
        mContext = context;
        mViews = holder;
        mCache = cache;
    }

    public void bindActionButton() {
        if (canUpdateImageIconViews()) {
            if (hasValidLink()) {
                mViews.viewNoteActionIcon.setImageDrawable(getLinkIcon());
                mViews.viewNoteButton.setVisibility(View.VISIBLE);
            } else {
                mViews.viewNoteButton.setVisibility(View.GONE);
            }
        }
    }

    private void updateViews() {
        if (hasValidLink()) {
            mViews.phoneCallDetailsViews.noteIconView.setVisibility(View.VISIBLE);
            mViews.phoneCallDetailsViews.noteIconView.setImageDrawable(getLinkIcon());
        } else {
            mViews.phoneCallDetailsViews.noteIconView.setVisibility(View.GONE);
        }
        bindActionButton();
    }

    private boolean hasValidLink() {
        return mDeepLink != null && mDeepLink != DeepLinkRequest.EMPTY;
    }

    private Drawable getLinkIcon() {
        return mCache != null ? mCache.getDrawable(mDeepLink) : null;
    }

    private boolean canUpdateImageIconViews() {
        return mViews.viewNoteButton != null && mViews.viewNoteActionIcon != null;
    }

    public void setDeepLink(DeepLink deepLink) {
        mDeepLink = deepLink;
        updateViews();
    }

    public void viewNote() {
        if (mDeepLink != null) {
            DeepLinkIntegrationManager.getInstance().viewNote(mContext, mDeepLink,
                    new ComponentName(mContext, CallLogListItemViewHolder.class));
        }
    }
}
