/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.dialer.util;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.provider.Telephony;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.common.CallUtil;
import com.android.dialer.R;

import java.util.List;

/**
 * General purpose utility methods for the Dialer.
 */
public class DialerUtils {

    /**
     * Attempts to start an activity and displays a toast with the default error message if the
     * activity is not found, instead of throwing an exception.
     *
     * @param context to start the activity with.
     * @param intent to start the activity with.
     */
    public static void startActivityWithErrorToast(Context context, Intent intent) {
        startActivityWithErrorToast(context, intent, R.string.activity_not_available);
    }

    /**
     * Attempts to start an activity and displays a toast with a provided error message if the
     * activity is not found, instead of throwing an exception.
     *
     * @param context to start the activity with.
     * @param intent to start the activity with.
     * @param msgId Resource ID of the string to display in an error message if the activity is
     *              not found.
     */
    public static void startActivityWithErrorToast(Context context, Intent intent, int msgId) {
        try {
          context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, msgId, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Returns the component name to use in order to send an SMS using the default SMS application,
     * or null if none exists.
     */
    public static ComponentName getSmsComponent(Context context) {
        String smsPackage = Telephony.Sms.getDefaultSmsPackage(context);
        if (smsPackage != null) {
            final PackageManager packageManager = context.getPackageManager();
            final Intent intent = new Intent(Intent.ACTION_SENDTO,
                    Uri.fromParts(CallUtil.SCHEME_SMSTO, "", null));
            final List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(intent, 0);
            for (ResolveInfo resolveInfo : resolveInfos) {
                if (smsPackage.equals(resolveInfo.activityInfo.packageName)) {
                    return new ComponentName(smsPackage, resolveInfo.activityInfo.name);
                }
            }
        }
        return null;
    }

    /**
     * Sets the image asset and text for an empty list view (see empty_list_view.xml).
     *
     * @param emptyListView The empty list view.
     * @param imageResId The resource id for the drawable to set as the image.
     * @param strResId The resource id for the string to set as the message.
     * @param res The resources to obtain the image and string from.
     */
    public static void configureEmptyListView(
            View emptyListView, int imageResId, int strResId, Resources res) {
        ImageView emptyListViewImage =
                (ImageView) emptyListView.findViewById(R.id.emptyListViewImage);

        emptyListViewImage.setImageDrawable(res.getDrawable(imageResId));
        emptyListViewImage.setContentDescription(res.getString(strResId));

        TextView emptyListViewMessage =
                (TextView) emptyListView.findViewById(R.id.emptyListViewMessage);
        emptyListViewMessage.setText(res.getString(strResId));
    }

    /**
     * Closes an {@link AutoCloseable}, silently ignoring any checked exceptions. Does nothing if
     * null.
     *
     * @param closeable to close.
     */
    public static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Joins a list of {@link CharSequence} into a single {@link CharSequence} seperated by a
     * localized delimiter such as ", ".
     *
     * @param resources Resources used to get list delimiter.
     * @param list List of char sequences to join.
     * @return Joined char sequences.
     */
    public static CharSequence join(Resources resources, Iterable<CharSequence> list) {
        final CharSequence separator = resources.getString(R.string.list_delimeter);
        return TextUtils.join(separator, list);
    }
}
