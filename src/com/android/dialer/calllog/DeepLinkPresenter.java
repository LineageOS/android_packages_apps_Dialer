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

import com.cyanogen.ambient.deeplink.DeepLink;

import com.android.dialer.deeplink.DeepLinkIntegrationManager;

public class DeepLinkPresenter {

    Context mContext;
    DeepLink mDeepLink;
    Drawable mDeepLinkIcon;
    private CallLogListItemViewHolder mViews;

    public DeepLinkPresenter(Context context) {
        mContext = context;
    }

    public void setCallLogViewHolder(CallLogListItemViewHolder holder) {
        mViews = holder;
    }

    private void updateViews() {
        if (mDeepLink != null && mDeepLinkIcon != null) {
            if (canUpdateImageIconViews()) {
                mViews.viewNoteActionIcon.setImageDrawable(mDeepLinkIcon);
                mViews.viewNoteButton.setVisibility(View.VISIBLE);
            }
            mViews.phoneCallDetailsViews.noteIconView.setVisibility(View.VISIBLE);
            mViews.phoneCallDetailsViews.noteIconView.setImageDrawable(mDeepLinkIcon);
        } else {
            if (canUpdateImageIconViews()) {
                mViews.viewNoteButton.setVisibility(View.GONE);
                mViews.viewNoteActionIcon.setImageDrawable(null);
            }
            mViews.phoneCallDetailsViews.noteIconView.setVisibility(View.GONE);
        }
    }

    public Drawable getDeepLinkIcon() {
        return mDeepLinkIcon;
    }

    private boolean canUpdateImageIconViews() {
        return mViews.viewNoteButton != null  && mViews.viewNoteActionIcon != null;
    }

    public void setDeepLink(DeepLink deepLink, Drawable icon) {
        mDeepLink = deepLink;
        mDeepLinkIcon = icon;
        updateViews();

    }

    public void viewNote() {
        if (mDeepLink != null) {
            DeepLinkIntegrationManager.getInstance().sendContentSentEvent(mContext, mDeepLink,
                    new ComponentName(mContext, CallLogListItemViewHolder.class));
            mContext.startActivity(mDeepLink.createViewIntent());
        }
    }
}
