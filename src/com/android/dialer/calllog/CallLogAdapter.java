/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.common.widget.GroupingListAdapter;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.util.UriUtils;
import com.android.dialer.PhoneCallDetails;
import com.android.dialer.PhoneCallDetailsHelper;
import com.android.dialer.R;
import com.android.dialer.calllog.CallLogAdapterHelper.NumberWithCountryIso;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;

import java.util.LinkedList;

/**
 * Adapter class to fill in data for the Call Log.
 */
public class CallLogAdapter extends GroupingListAdapter
        implements CallLogAdapterHelper.Callback, CallLogGroupBuilder.GroupCreator {

    /** Interface used to initiate a refresh of the content. */
    public interface CallFetcher {
        public void fetchCalls();
    }

    protected final Context mContext;
    private final ContactInfoHelper mContactInfoHelper;
    private final CallFetcher mCallFetcher;

    private boolean mLoading = true;

    /** Instance of helper class for managing views. */
    private final CallLogListItemHelper mCallLogViewsHelper;

    /** Helper to set up contact photos. */
    private final ContactPhotoManager mContactPhotoManager;
    /** Helper to parse and process phone numbers. */
    private PhoneNumberHelper mPhoneNumberHelper;
    /** Helper to group call log entries. */
    private final CallLogGroupBuilder mCallLogGroupBuilder;

    private final CallLogAdapterHelper mAdapterHelper;

    /** True if CallLogAdapter is created from the PhoneFavoriteFragment, where the primary
     * action should be set to call a number instead of opening the detail page. */
    private boolean mUseCallAsPrimaryAction = false;

    private boolean mIsCallLog = true;
    private int mNumMissedCalls = 0;
    private int mNumMissedCallsShown = 0;

    private View mBadgeContainer;
    private ImageView mBadgeImageView;
    private TextView mBadgeText;

    /** Listener for the primary or secondary actions in the list.
     *  Primary opens the call details.
     *  Secondary calls or plays.
     **/
    private final View.OnClickListener mActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            startActivityForAction(view);
        }
    };

    private void startActivityForAction(View view) {
        final IntentProvider intentProvider = (IntentProvider) view.getTag();
        if (intentProvider != null) {
            final Intent intent = intentProvider.getIntent(mContext);
            // See IntentProvider.getCallDetailIntentProvider() for why this may be null.
            if (intent != null) {
                mContext.startActivity(intent);
            }
        }
    }

    public CallLogAdapter(Context context, CallFetcher callFetcher,
            ContactInfoHelper contactInfoHelper, boolean useCallAsPrimaryAction,
            boolean isCallLog) {
        super(context);

        mContext = context;
        mCallFetcher = callFetcher;
        mContactInfoHelper = contactInfoHelper;
        mUseCallAsPrimaryAction = useCallAsPrimaryAction;
        mIsCallLog = isCallLog;

        Resources resources = mContext.getResources();
        CallTypeHelper callTypeHelper = new CallTypeHelper(resources);

        mContactPhotoManager = ContactPhotoManager.getInstance(mContext);
        mPhoneNumberHelper = new PhoneNumberHelper(resources);
        mAdapterHelper = new CallLogAdapterHelper(context, this,
                contactInfoHelper, mPhoneNumberHelper);
        PhoneCallDetailsHelper phoneCallDetailsHelper = new PhoneCallDetailsHelper(
                resources, callTypeHelper, new PhoneNumberUtilsWrapper());
        mCallLogViewsHelper =
                new CallLogListItemHelper(
                        phoneCallDetailsHelper, mPhoneNumberHelper, resources);
        mCallLogGroupBuilder = new CallLogGroupBuilder(this);
    }

    /**
     * Requery on background thread when {@link Cursor} changes.
     */
    @Override
    protected void onContentChanged() {
        mCallFetcher.fetchCalls();
    }

    public void setLoading(boolean loading) {
        mLoading = loading;
    }

    @Override
    public boolean isEmpty() {
        if (mLoading) {
            // We don't want the empty state to show when loading.
            return false;
        } else {
            return super.isEmpty();
        }
    }

    @Override
    protected void addGroups(Cursor cursor) {
        mCallLogGroupBuilder.addGroups(cursor);
    }

    @Override
    protected View newStandAloneView(Context context, ViewGroup parent) {
        return newChildView(context, parent);
    }

    @Override
    protected View newGroupView(Context context, ViewGroup parent) {
        return newChildView(context, parent);
    }

    @Override
    protected View newChildView(Context context, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.call_log_list_item, parent, false);
        findAndCacheViews(view);
        return view;
    }

    @Override
    protected void bindStandAloneView(View view, Context context, Cursor cursor) {
        bindView(view, cursor, 1);
    }

    @Override
    protected void bindChildView(View view, Context context, Cursor cursor) {
        bindView(view, cursor, 1);
    }

    @Override
    protected void bindGroupView(View view, Context context, Cursor cursor, int groupSize,
            boolean expanded) {
        bindView(view, cursor, groupSize);
    }

    private void findAndCacheViews(View view) {
        // Get the views to bind to.
        CallLogListItemViews views = CallLogListItemViews.fromView(view);
        views.primaryActionView.setOnClickListener(mActionListener);
        views.secondaryActionView.setOnClickListener(mActionListener);
        view.setTag(views);
    }

    /**
     * Binds the views in the entry to the data in the call log.
     *
     * @param view the view corresponding to this entry
     * @param c the cursor pointing to the entry in the call log
     * @param count the number of entries in the current item, greater than 1 if it is a group
     */
    private void bindView(View view, Cursor c, int count) {
        final CallLogListItemViews views = (CallLogListItemViews) view.getTag();

        // Default case: an item in the call log.
        views.primaryActionView.setVisibility(View.VISIBLE);
        views.listHeaderTextView.setVisibility(View.GONE);

        final String number = c.getString(CallLogQuery.NUMBER);
        final int numberPresentation = c.getInt(CallLogQuery.NUMBER_PRESENTATION);
        final long date = c.getLong(CallLogQuery.DATE);
        final long duration = c.getLong(CallLogQuery.DURATION);
        final int callType = c.getInt(CallLogQuery.CALL_TYPE);
        final String countryIso = c.getString(CallLogQuery.COUNTRY_ISO);

        final ContactInfo cachedContactInfo = getContactInfoFromCallLog(c);

        if (!mUseCallAsPrimaryAction) {
            // Sets the primary action to open call detail page.
            views.primaryActionView.setTag(
                    IntentProvider.getCallDetailIntentProvider(
                            getCursor(), c.getPosition(), c.getLong(CallLogQuery.ID), count));
        } else if (PhoneNumberUtilsWrapper.canPlaceCallsTo(number, numberPresentation)) {
            // Sets the primary action to call the number.
            views.primaryActionView.setTag(IntentProvider.getReturnCallIntentProvider(number));
        } else {
            views.primaryActionView.setTag(null);
        }

        // Store away the voicemail information so we can play it directly.
        if (callType == Calls.VOICEMAIL_TYPE) {
            String voicemailUri = c.getString(CallLogQuery.VOICEMAIL_URI);
            final long rowId = c.getLong(CallLogQuery.ID);
            views.secondaryActionView.setTag(
                    IntentProvider.getPlayVoicemailIntentProvider(rowId, voicemailUri));
        } else if (!TextUtils.isEmpty(number)) {
            // Store away the number so we can call it directly if you click on the call icon.
            views.secondaryActionView.setTag(
                    IntentProvider.getReturnCallIntentProvider(number));
        } else {
            // No action enabled.
            views.secondaryActionView.setTag(null);
        }

        // Lookup contacts with this number
        final ContactInfo info = mAdapterHelper.lookupContact(
                number, numberPresentation, countryIso, cachedContactInfo);
        final Uri lookupUri = info.lookupUri;
        final String name = info.name;
        final int ntype = info.type;
        final String label = info.label;
        final long photoId = info.photoId;
        final Uri photoUri = info.photoUri;
        CharSequence formattedNumber = info.formattedNumber;
        final int[] callTypes = getCallTypes(c, count);
        final String geocode = c.getString(CallLogQuery.GEOCODED_LOCATION);
        final PhoneCallDetails details;

        if (TextUtils.isEmpty(name)) {
            details = new PhoneCallDetails(number, numberPresentation,
                    formattedNumber, countryIso, geocode, callTypes, date,
                    duration);
        } else {
            details = new PhoneCallDetails(number, numberPresentation,
                    formattedNumber, countryIso, geocode, callTypes, date,
                    duration, name, ntype, label, lookupUri, photoUri);
        }

        final boolean isNew = c.getInt(CallLogQuery.IS_READ) == 0;
        // New items also use the highlighted version of the text.
        final boolean isHighlighted = isNew;
        mCallLogViewsHelper.setPhoneCallDetails(views, details, isHighlighted,
                mUseCallAsPrimaryAction);

        if (photoId == 0 && photoUri != null) {
            setPhoto(views, photoUri, lookupUri);
        } else {
            setPhoto(views, photoId, lookupUri);
        }

        views.quickContactView.setContentDescription(views.phoneCallDetailsViews.nameView.
                getText());

        // Listen for the first draw
        mAdapterHelper.registerOnPreDrawListener(view);

        bindBadge(view, info, details, callType);
    }

    protected void bindBadge(View view, ContactInfo info, PhoneCallDetails details, int callType) {

        // Do not show badge in call log.
        if (!mIsCallLog) {
            final int numMissed = getNumMissedCalls(callType);
            final ViewStub stub = (ViewStub) view.findViewById(R.id.link_stub);

            if (shouldShowBadge(numMissed, info, details)) {
                // Do not process if the data has not changed (optimization since bind view is
                // called multiple times due to contact lookup).
                if (numMissed == mNumMissedCallsShown) {
                    return;
                }

                // stub will be null if it was already inflated.
                if (stub != null) {
                    final View inflated = stub.inflate();
                    inflated.setVisibility(View.VISIBLE);
                    mBadgeContainer = inflated.findViewById(R.id.badge_link_container);
                    mBadgeImageView = (ImageView) inflated.findViewById(R.id.badge_image);
                    mBadgeText = (TextView) inflated.findViewById(R.id.badge_text);
                }

                mBadgeContainer.setOnClickListener(getBadgeClickListener());
                mBadgeImageView.setImageResource(getBadgeImageResId());
                mBadgeText.setText(getBadgeText(numMissed));

                mNumMissedCallsShown = numMissed;
            } else {
                // Hide badge if it was previously shown.
                if (stub == null) {
                    final View container = view.findViewById(R.id.badge_container);
                    if (container != null) {
                        container.setVisibility(View.GONE);
                    }
                }
            }
        }
    }

    public void setMissedCalls(Cursor data) {
        final int missed;
        if (data == null) {
            missed = 0;
        } else {
            missed = data.getCount();
        }
        // Only need to update if the number of calls changed.
        if (missed != mNumMissedCalls) {
            mNumMissedCalls = missed;
            notifyDataSetChanged();
        }
    }

    protected View.OnClickListener getBadgeClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Intent intent = new Intent(mContext, CallLogActivity.class);
                mContext.startActivity(intent);
            }
        };
    }

    /**
     * Get the resource id for the image to be shown for the badge.
     */
    protected int getBadgeImageResId() {
        return R.drawable.ic_call_log_blue;
    }

    /**
     * Get the text to be shown for the badge.
     *
     * @param numMissed The number of missed calls.
     */
    protected String getBadgeText(int numMissed) {
        return mContext.getResources().getString(R.string.num_missed_calls, numMissed);
    }

    /**
     * Whether to show the badge.
     *
     * @param numMissedCalls The number of missed calls.
     * @param info The contact info.
     * @param details The call detail.
     * @return {@literal true} if badge should be shown.  {@literal false} otherwise.
     */
    protected boolean shouldShowBadge(int numMissedCalls, ContactInfo info,
            PhoneCallDetails details) {
        return numMissedCalls > 0;
    }

    private int getNumMissedCalls(int callType) {
        if (callType == Calls.MISSED_TYPE) {
            // Exclude the current missed call shown in the shortcut.
            return mNumMissedCalls - 1;
        }
        return mNumMissedCalls;
    }

    @Override
    public void dataSetChanged() {
        notifyDataSetChanged();
    }

    /** Stores the updated contact info in the call log if it is different from the current one. */
    @Override
    public void updateContactInfo(String number, String countryIso,
            ContactInfo updatedInfo, ContactInfo callLogInfo) {
        final ContentValues values = new ContentValues();
        boolean needsUpdate = false;

        if (callLogInfo != null) {
            if (!TextUtils.equals(updatedInfo.name, callLogInfo.name)) {
                values.put(Calls.CACHED_NAME, updatedInfo.name);
                needsUpdate = true;
            }

            if (updatedInfo.type != callLogInfo.type) {
                values.put(Calls.CACHED_NUMBER_TYPE, updatedInfo.type);
                needsUpdate = true;
            }

            if (!TextUtils.equals(updatedInfo.label, callLogInfo.label)) {
                values.put(Calls.CACHED_NUMBER_LABEL, updatedInfo.label);
                needsUpdate = true;
            }
            if (!UriUtils.areEqual(updatedInfo.lookupUri, callLogInfo.lookupUri)) {
                values.put(Calls.CACHED_LOOKUP_URI, UriUtils.uriToString(updatedInfo.lookupUri));
                needsUpdate = true;
            }
            if (!TextUtils.equals(updatedInfo.normalizedNumber, callLogInfo.normalizedNumber)) {
                values.put(Calls.CACHED_NORMALIZED_NUMBER, updatedInfo.normalizedNumber);
                needsUpdate = true;
            }
            if (!TextUtils.equals(updatedInfo.number, callLogInfo.number)) {
                values.put(Calls.CACHED_MATCHED_NUMBER, updatedInfo.number);
                needsUpdate = true;
            }
            if (updatedInfo.photoId != callLogInfo.photoId) {
                values.put(Calls.CACHED_PHOTO_ID, updatedInfo.photoId);
                needsUpdate = true;
            }
            if (!TextUtils.equals(updatedInfo.formattedNumber, callLogInfo.formattedNumber)) {
                values.put(Calls.CACHED_FORMATTED_NUMBER, updatedInfo.formattedNumber);
                needsUpdate = true;
            }
        } else {
            // No previous values, store all of them.
            values.put(Calls.CACHED_NAME, updatedInfo.name);
            values.put(Calls.CACHED_NUMBER_TYPE, updatedInfo.type);
            values.put(Calls.CACHED_NUMBER_LABEL, updatedInfo.label);
            values.put(Calls.CACHED_LOOKUP_URI, UriUtils.uriToString(updatedInfo.lookupUri));
            values.put(Calls.CACHED_MATCHED_NUMBER, updatedInfo.number);
            values.put(Calls.CACHED_NORMALIZED_NUMBER, updatedInfo.normalizedNumber);
            values.put(Calls.CACHED_PHOTO_ID, updatedInfo.photoId);
            values.put(Calls.CACHED_FORMATTED_NUMBER, updatedInfo.formattedNumber);
            needsUpdate = true;
        }

        if (!needsUpdate) return;

        if (countryIso == null) {
            mContext.getContentResolver().update(Calls.CONTENT_URI_WITH_VOICEMAIL, values,
                    Calls.NUMBER + " = ? AND " + Calls.COUNTRY_ISO + " IS NULL",
                    new String[]{ number });
        } else {
            mContext.getContentResolver().update(Calls.CONTENT_URI_WITH_VOICEMAIL, values,
                    Calls.NUMBER + " = ? AND " + Calls.COUNTRY_ISO + " = ?",
                    new String[]{ number, countryIso });
        }
    }

    /** Returns the contact information as stored in the call log. */
    private ContactInfo getContactInfoFromCallLog(Cursor c) {
        ContactInfo info = new ContactInfo();
        info.lookupUri = UriUtils.parseUriOrNull(c.getString(CallLogQuery.CACHED_LOOKUP_URI));
        info.name = c.getString(CallLogQuery.CACHED_NAME);
        info.type = c.getInt(CallLogQuery.CACHED_NUMBER_TYPE);
        info.label = c.getString(CallLogQuery.CACHED_NUMBER_LABEL);
        String matchedNumber = c.getString(CallLogQuery.CACHED_MATCHED_NUMBER);
        info.number = matchedNumber == null ? c.getString(CallLogQuery.NUMBER) : matchedNumber;
        info.normalizedNumber = c.getString(CallLogQuery.CACHED_NORMALIZED_NUMBER);
        info.photoId = c.getLong(CallLogQuery.CACHED_PHOTO_ID);
        info.photoUri = null;  // We do not cache the photo URI.
        info.formattedNumber = c.getString(CallLogQuery.CACHED_FORMATTED_NUMBER);
        return info;
    }

    /**
     * Returns the call types for the given number of items in the cursor.
     * <p>
     * It uses the next {@code count} rows in the cursor to extract the types.
     * <p>
     * It position in the cursor is unchanged by this function.
     */
    private int[] getCallTypes(Cursor cursor, int count) {
        int position = cursor.getPosition();
        int[] callTypes = new int[count];
        for (int index = 0; index < count; ++index) {
            callTypes[index] = cursor.getInt(CallLogQuery.CALL_TYPE);
            cursor.moveToNext();
        }
        cursor.moveToPosition(position);
        return callTypes;
    }

    private void setPhoto(CallLogListItemViews views, long photoId, Uri contactUri) {
        views.quickContactView.assignContactUri(contactUri);
        mContactPhotoManager.loadThumbnail(views.quickContactView, photoId, false /* darkTheme */);
    }

    private void setPhoto(CallLogListItemViews views, Uri photoUri, Uri contactUri) {
        views.quickContactView.assignContactUri(contactUri);
        mContactPhotoManager.loadDirectoryPhoto(views.quickContactView, photoUri,
                false /* darkTheme */);
    }


    /**
     * Sets whether processing of requests for contact details should be enabled.
     * <p>
     * This method should be called in tests to disable such processing of requests when not
     * needed.
     */
    @VisibleForTesting
    void disableRequestProcessingForTest() {
        mAdapterHelper.disableRequestProcessingForTest();
    }

    @VisibleForTesting
    void injectContactInfoForTest(String number, String countryIso, ContactInfo contactInfo) {
        mAdapterHelper.injectContactInfoForTest(number, countryIso, contactInfo);
    }

    @VisibleForTesting
    protected void enqueueRequest(String number, String countryIso, ContactInfo callLogInfo,
            boolean immediate) {
        mAdapterHelper.enqueueRequest(number, countryIso, callLogInfo, immediate);
    }

    @Override
    public void addGroup(int cursorPosition, int size, boolean expanded) {
        super.addGroup(cursorPosition, size, expanded);
    }

    public void stopRequestProcessing() {
        mAdapterHelper.stopRequestProcessing();
    }

    public void invalidateCache() {
        mAdapterHelper.invalidateCache();
    }

    public String getBetterNumberFromContacts(String number, String countryIso) {
        return mAdapterHelper.getBetterNumberFromContacts(number, countryIso);
    }
}
