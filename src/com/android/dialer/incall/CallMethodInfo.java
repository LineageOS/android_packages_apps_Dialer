/*
 * Copyright (C) 2015 The CyanogenMod Project
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

package com.android.dialer.incall;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import com.android.dialer.DialerApplication;
import com.android.dialer.incall.CallMethodHelper;
import com.android.phone.common.util.StartInCallCallReceiver;
import com.cyanogen.ambient.incall.InCallServices;
import com.cyanogen.ambient.incall.extension.CreditInfo;
import com.cyanogen.ambient.incall.extension.OriginCodes;
import com.cyanogen.ambient.incall.extension.StartCallRequest;
import com.google.common.base.Objects;

public class CallMethodInfo {

    public String mId;
    public UserHandle mUserHandle;
    public ComponentName mComponent;
    public String mName;
    public String mSummary;
    public int mSlotId;
    public int mSubId;
    public int mColor;
    public int mStatus;
    public boolean mIsAuthenticated;
    public String mMimeType;
    public String mSubscriptionButtonText;
    public String mCreditButtonText;
    public String mT9HintDescription;
    public PendingIntent mSettingsIntent;
    public Drawable mBrandIcon;
    public Drawable mBadgeIcon;
    public Drawable mLoginIcon;
    public Drawable mActionOneIcon;
    public Drawable mActionTwoIcon;
    public Resources pluginResources;
    public String mActionOneText;
    public String mActionTwoText;
    public boolean mIsInCallProvider;
    public PendingIntent mManageCreditIntent;
    public CreditInfo mProviderCreditInfo;
    public float mCreditWarn = 0.0f;

    @Override
    public int hashCode() {
        return Objects.hashCode(mId, mUserHandle, mComponent, mName, mSummary, mSlotId, mSubId,
                mColor, mStatus, mIsAuthenticated, mMimeType, mSubscriptionButtonText,
                mCreditButtonText, mT9HintDescription, mSettingsIntent, mBrandIcon, mBadgeIcon,
                mLoginIcon, mActionOneIcon, mActionTwoIcon, pluginResources, mActionOneText,
                mActionTwoText, mIsInCallProvider);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object instanceof CallMethodInfo) {
            final CallMethodInfo info = (CallMethodInfo) object;
            return Objects.equal(this.mId, info.mId)
                    && Objects.equal(this.mUserHandle, info.mUserHandle)
                    && Objects.equal(this.mComponent, info.mComponent)
                    && Objects.equal(this.mName, info.mName)
                    && Objects.equal(this.mSummary, info.mSummary)
                    && Objects.equal(this.mSlotId, info.mSlotId)
                    && Objects.equal(this.mSubId, info.mSubId)
                    && Objects.equal(this.mColor, info.mColor)
                    && Objects.equal(this.mStatus, info.mStatus)
                    && Objects.equal(this.mIsAuthenticated, info.mIsAuthenticated)
                    && Objects.equal(this.mMimeType, info.mMimeType)
                    && Objects.equal(this.mSubscriptionButtonText, info.mSubscriptionButtonText)
                    && Objects.equal(this.mCreditButtonText, info.mCreditButtonText)
                    && Objects.equal(this.mT9HintDescription, info.mT9HintDescription)
                    && Objects.equal(this.mSettingsIntent, info.mSettingsIntent)
                    && Objects.equal(this.mBrandIcon, info.mBrandIcon)
                    && Objects.equal(this.mBadgeIcon, info.mBadgeIcon)
                    && Objects.equal(this.mLoginIcon, info.mLoginIcon)
                    && Objects.equal(this.mActionOneIcon, info.mActionOneIcon)
                    && Objects.equal(this.mActionTwoIcon, info.mActionTwoIcon)
                    && Objects.equal(this.pluginResources, info.pluginResources)
                    && Objects.equal(this.mActionOneText, info.mActionOneText)
                    && Objects.equal(this.mActionTwoText, info.mActionTwoText)
                    && Objects.equal(this.mIsInCallProvider, info.mIsInCallProvider);
        }
        return false;
    }

    public void placeCall(String origin, String number, Context c) {
        StartInCallCallReceiver svcrr = CallMethodHelper.getVoIPResultReceiver(this, origin);

        StartCallRequest request = new StartCallRequest(
                number, OriginCodes.DIALPAD_DIRECT_DIAL, 0, svcrr);

        InCallServices.getInstance().startOutCall(
                DialerApplication.ACLIENT.get(c), this.mComponent, request);
    }
}
