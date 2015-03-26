/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.dialer;

import android.accounts.Account;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Contacts.Intents.Insert;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.telecom.PhoneAccount;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.ClipboardUtils;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.format.FormatUtils;
import com.android.contacts.common.util.Constants;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialer.calllog.PhoneNumberDisplayHelper;
import com.android.dialer.calllog.PhoneNumberUtilsWrapper;

public class CallDetailHeader {
    private static final char LEFT_TO_RIGHT_EMBEDDING = '\u202A';
    private static final char POP_DIRECTIONAL_FORMATTING = '\u202C';

    private Activity mActivity;
    private Resources mResources;
    private PhoneNumberDisplayHelper mPhoneNumberHelper;
    private ContactPhotoManager mContactPhotoManager;
    private BidiFormatter mBidiFormatter = BidiFormatter.getInstance();

    private String mNumber;

    private TextView mCallerName;
    private TextView mCallerNumber;
    private TextView mAccountLabel;
    private QuickContactBadge mQuickContactBadge;

    private ActionMode mPhoneNumberActionMode;
    private boolean mHasEditNumberBeforeCallOption;
    private boolean mCanPlaceCallsTo;

    private CharSequence mPhoneNumberLabelToCopy;
    private CharSequence mPhoneNumberToCopy;

    public interface Data {
        CharSequence getName();
        CharSequence getNumber();
        int getNumberPresentation();
        int getNumberType();
        CharSequence getNumberLabel();
        CharSequence getFormattedNumber();
        Uri getContactUri();
        Uri getPhotoUri();
        CharSequence getAccountLabel();
        CharSequence getGeocode();
        int getAccountId();
    }

    public CallDetailHeader(Activity activity, PhoneNumberDisplayHelper phoneNumberHelper) {
        mActivity = activity;
        mResources = activity.getResources();
        mPhoneNumberHelper = phoneNumberHelper;
        mContactPhotoManager = ContactPhotoManager.getInstance(activity);


        mCallerName = (TextView) activity.findViewById(R.id.caller_name);
        mCallerNumber = (TextView) activity.findViewById(R.id.caller_number);
        mAccountLabel = (TextView) activity.findViewById(R.id.phone_account_label);
        mQuickContactBadge = (QuickContactBadge) activity.findViewById(R.id.quick_contact_photo);
        mQuickContactBadge.setOverlay(null);
    }

    public void updateViews(Data data) {
        // Cache the details about the phone number.
        final PhoneNumberUtilsWrapper phoneUtils = new PhoneNumberUtilsWrapper();
        final int accountId = data.getAccountId();
        final CharSequence dataName = data.getName();
        final CharSequence dataNumber = data.getNumber();
        final CharSequence dataAccount = data.getAccountLabel();
        final CharSequence callLocationOrType = getNumberTypeOrLocation(data);

        final CharSequence displayNumber = mPhoneNumberHelper.getDisplayNumber(accountId,
                dataNumber, data.getNumberPresentation(), data.getFormattedNumber());
        final String displayNumberStr = mBidiFormatter.unicodeWrap(
                displayNumber.toString(), TextDirectionHeuristics.LTR);

        if (!TextUtils.isEmpty(dataName)) {
            mCallerName.setText(dataName);
            mCallerNumber.setText(callLocationOrType + " " + displayNumberStr);
        } else {
            mCallerName.setText(displayNumberStr);
            if (!TextUtils.isEmpty(callLocationOrType)) {
                mCallerNumber.setText(callLocationOrType);
                mCallerNumber.setVisibility(View.VISIBLE);
            } else {
                mCallerNumber.setVisibility(View.GONE);
            }
        }

        if (!TextUtils.isEmpty(dataAccount)) {
            mAccountLabel.setText(dataAccount);
            mAccountLabel.setVisibility(View.VISIBLE);
        } else {
            mAccountLabel.setVisibility(View.GONE);
        }
    }

    private CharSequence getNumberTypeOrLocation(Data data) {
        if (!TextUtils.isEmpty(data.getName())) {
            return Phone.getTypeLabel(mResources, data.getNumberType(),
                    data.getNumberLabel());
        } else {
            return data.getGeocode();
        }
    }

    /** Load the contact photos and places them in the corresponding views. */
    public void loadContactPhotos(Data data, int contactType) {
        Uri contactUri = data.getContactUri();
        Account contactAccount = null;
        if (contactUri != null) {
            ContentResolver resolver = mActivity.getContentResolver();
            Uri uri = Contacts.lookupContact(resolver, contactUri);
            if (uri != null) {
                Cursor cursor = resolver.query(
                        uri,
                        new String[] { RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME },
                        null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    String accountType = cursor.getString(0);
                    String accountName = cursor.getString(1);
                    if (!TextUtils.isEmpty(accountName) && !TextUtils.isEmpty(accountType)) {
                        contactAccount = new Account(accountName, accountType);
                    }
                    cursor.close();
                }
            }
        }

        String nameForDefaultImage;
        if (TextUtils.isEmpty(data.getName())) {
            nameForDefaultImage = mPhoneNumberHelper.getDisplayNumber(data.getAccountId(),
                    data.getNumber(), data.getNumberPresentation(), data.getFormattedNumber()).toString();
        } else {
            nameForDefaultImage = data.getName().toString();
        }

        String lookupKey = contactUri == null ? null
                : ContactInfoHelper.getLookupKeyFromUri(contactUri);
        final DefaultImageRequest request = new DefaultImageRequest(nameForDefaultImage,
                lookupKey, contactType, true /* isCircular */);

        mQuickContactBadge.assignContactUri(contactUri);
        mQuickContactBadge.setContentDescription(
                mResources.getString(R.string.description_contact_details, data.getName()));

        mContactPhotoManager.loadDirectoryPhoto(mQuickContactBadge, data.getPhotoUri(),
                contactAccount, false /* darkTheme */, true /* isCircular */, request);
    }

    static final class ViewEntry {
        public final String text;
        public final Intent primaryIntent;
        /** The description for accessibility of the primary action. */
        public final String primaryDescription;

        public CharSequence label = null;
        /** Icon for the secondary action. */
        public int secondaryIcon = 0;
        /** Intent for the secondary action. If not null, an icon must be defined. */
        public Intent secondaryIntent = null;
        /** The description for accessibility of the secondary action. */
        public String secondaryDescription = null;

        public ViewEntry(String text, Intent intent, String description) {
            this.text = text;
            primaryIntent = intent;
            primaryDescription = description;
        }

        public void setSecondaryAction(int icon, Intent intent, String description) {
            secondaryIcon = icon;
            secondaryIntent = intent;
            secondaryDescription = description;
        }
    }

    public boolean handleKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                // Make sure phone isn't already busy before starting direct call
                TelephonyManager tm = (TelephonyManager)
                        mActivity.getSystemService(Context.TELEPHONY_SERVICE);
                if (tm.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
                    mActivity.startActivity(CallUtil.getCallIntent(
                            Uri.fromParts(PhoneAccount.SCHEME_TEL, mNumber, null)));
                    return true;
                }
            }
        }

        return false;
    }

    /** Returns the given text, forced to be left-to-right. */
    private static CharSequence forceLeftToRight(CharSequence text) {
        StringBuilder sb = new StringBuilder();
        sb.append(LEFT_TO_RIGHT_EMBEDDING);
        sb.append(text);
        sb.append(POP_DIRECTIONAL_FORMATTING);
        return sb.toString();
    }
}
