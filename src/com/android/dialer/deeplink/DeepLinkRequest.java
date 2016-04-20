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
import com.cyanogen.ambient.deeplink.DeepLink;
import java.util.List;

public class DeepLinkRequest {
    public static final DeepLink EMPTY = new DeepLink(null, null, null, null, -1, -1, null, null,
            false);
    /**
     * The uris for this set of number and times
     */
    private List<Uri> mUris;

    public DeepLinkRequest(List<Uri> uris) {
        mUris = uris;
    }

    public List<Uri> getUris() {
        return mUris;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof DeepLinkRequest)) return false;

        DeepLinkRequest other = (DeepLinkRequest) obj;
        return mUris.equals(other.mUris);
    }

    @Override
    public int hashCode() {
        return mUris.hashCode();
    }
}
