/*
  * Copyright (C) 2013 Xiao-Long Chen <chenxiaolong@cxl.epac.to>
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

/*
 * This is a reverse-engineered implementation of com.google.android.Dialer.
 * There is no guarantee that this implementation will work correctly or even
 * work at all. Use at your own risk.
 */

package com.google.android.dialer.reverselookup;

import com.android.incallui.service.PhoneNumberService;
import com.google.common.base.Objects;

public class PhoneNumberInfoImpl implements PhoneNumberService.PhoneNumberInfo {
    private String mDisplayName;
    private String mImageUrl;
    private String mLabel;
    private String mLookupKey;
    private String mNormalizedNumber;
    private String mNumber;
    private int mType;
    private boolean mIsBusiness;

    public PhoneNumberInfoImpl(String name, String normalizedNumber, String number,
            int type, String label, String imageUrl, String lookupKey, boolean isBusiness) {
        mDisplayName = name;
        mNormalizedNumber = normalizedNumber;
        mNumber = number;
        mType = type;
        mLabel = label;
        mImageUrl = imageUrl;
        mLookupKey = lookupKey;
        mIsBusiness = isBusiness;
    }

    @Override
    public String getDisplayName() {
        return mDisplayName;
    }

    @Override
    public String getImageUrl() {
        return mImageUrl;
    }

    public String getLookupKey() {
        return mLookupKey;
    }

    @Override
    public String getNormalizedNumber() {
        return mNormalizedNumber;
    }

    @Override
    public String getNumber() {
        return mNumber;
    }

    @Override
    public String getPhoneLabel() {
        return mLabel;
    }

    @Override
    public int getPhoneType() {
        return mType;
    }

    @Override
    public boolean isBusiness() {
        return this.mIsBusiness;
    }

    public String toString() {
        return Objects.toStringHelper(this)
                .add("mDisplayName", mDisplayName)
                .add("mImageUrl", mImageUrl)
                .add("mNormalizedNumber", mNormalizedNumber)
                .toString();
    }
}
