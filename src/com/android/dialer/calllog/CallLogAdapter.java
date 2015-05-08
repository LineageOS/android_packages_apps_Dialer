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

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.telecom.PhoneAccountHandle;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityEvent;

import com.android.dialer.PhoneCallDetails;
import com.android.dialer.PhoneCallDetailsHelper;
import com.android.dialer.R;
import com.android.dialer.contactinfo.ContactInfoCache;
import com.android.dialer.contactinfo.ContactInfoCache.OnContactInfoChangedListener;
import com.android.dialer.util.DialerUtils;

import com.google.common.annotations.VisibleForTesting;

import java.util.HashMap;

/**
 * Adapter class to fill in data for the Call Log.
 */
public class CallLogAdapter extends GroupingListAdapter
        implements ViewTreeObserver.OnPreDrawListener, CallLogGroupBuilder.GroupCreator {

    /** Interface used to inform a parent UI element that a list item has been expanded. */
    public interface CallItemExpandedListener {
        /**
         * @param view The {@link View} that represents the item that was clicked
         *         on.
         */
        public void onItemExpanded(View view);

        /**
         * Retrieves the call log view for the specified call Id.  If the view is not currently
         * visible, returns null.
         *
         * @param callId The call Id.
         * @return The call log view.
         */
        public View getViewForCallId(long callId);
    }

    /** Interface used to initiate a refresh of the content. */
    public interface CallFetcher {
        public void fetchCalls();
    }

    /** Implements onClickListener for the report button. */
    public interface OnReportButtonClickListener {
        public void onReportButtonClick(String number);
    }

    private static final int VIEW_TYPE_SHOW_CALL_HISTORY_LIST_ITEM = 10;

    /** Constant used to indicate no row is expanded. */
    private static final long NONE_EXPANDED = -1;

    protected final Context mContext;
    private final ContactInfoHelper mContactInfoHelper;
    private final CallFetcher mCallFetcher;
    private final OnReportButtonClickListener mOnReportButtonClickListener;
    private ViewTreeObserver mViewTreeObserver = null;

    protected ContactInfoCache mContactInfoCache;

    private boolean mShowCallHistoryListItem = false;

    /**
     * Tracks the currently expanded call log row.
     */
    private long mCurrentlyExpanded = NONE_EXPANDED;

    /**
     *  Hashmap, keyed by call Id, used to track the day group for a call.  As call log entries are
     *  put into the primary call groups in {@link com.android.dialer.calllog.CallLogGroupBuilder},
     *  they are also assigned a secondary "day group".  This hashmap tracks the day group assigned
     *  to all calls in the call log.  This information is used to trigger the display of a day
     *  group header above the call log entry at the start of a day group.
     *  Note: Multiple calls are grouped into a single primary "call group" in the call log, and
     *  the cursor used to bind rows includes all of these calls.  When determining if a day group
     *  change has occurred it is necessary to look at the last entry in the call log to determine
     *  its day group.  This hashmap provides a means of determining the previous day group without
     *  having to reverse the cursor to the start of the previous day call log entry.
     */
    private HashMap<Long,Integer> mDayGroups = new HashMap<Long, Integer>();

    private boolean mLoading = true;

    /** Instance of helper class for managing views. */
    private final CallLogListItemHelper mCallLogViewsHelper;

    /** Helper to parse and process phone numbers. */
    private PhoneNumberDisplayHelper mPhoneNumberHelper;
    /** Helper to access Telephony phone number utils class */
    protected final PhoneNumberUtilsWrapper mPhoneNumberUtilsWrapper;
    /** Helper to group call log entries. */
    private final CallLogGroupBuilder mCallLogGroupBuilder;

    /** Listener for the primary or secondary actions in the list.
     *  Primary opens the call details.
     *  Secondary calls or plays.
     **/
    private final View.OnClickListener mActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            final IntentProvider intentProvider = (IntentProvider) view.getTag();
            if (intentProvider != null) {
                final Intent intent = intentProvider.getIntent(mContext);
                // See IntentProvider.getCallDetailIntentProvider() for why this may be null.
                if (intent != null) {
                    DialerUtils.startActivityWithErrorToast(mContext, intent);
                }
            }
        }
    };

    /**
     * The onClickListener used to expand or collapse the action buttons section for a call log
     * entry.
     */
    private final View.OnClickListener mExpandCollapseListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            handleRowExpanded(v, false /* forceExpand */);
        }
    };

    protected final OnContactInfoChangedListener mOnContactInfoChangedListener =
            new OnContactInfoChangedListener() {
                @Override
                public void onContactInfoChanged() {
                    notifyDataSetChanged();
                }
            };

    private AccessibilityDelegate mAccessibilityDelegate = new AccessibilityDelegate() {
        @Override
        public boolean onRequestSendAccessibilityEvent(ViewGroup host, View child,
                AccessibilityEvent event) {
            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                handleRowExpanded(host, true /* forceExpand */);
            }
            return super.onRequestSendAccessibilityEvent(host, child, event);
        }
    };

    @Override
    public boolean onPreDraw() {
        // We only wanted to listen for the first draw (and this is it).
        unregisterPreDrawListener();

        mContactInfoCache.start();
        return true;
    }

    public CallLogAdapter(
            Context context,
            CallFetcher callFetcher,
            ContactInfoHelper contactInfoHelper,
            OnReportButtonClickListener onReportButtonClickListener) {
        super(context);

        mContext = context;
        mCallFetcher = callFetcher;
        mContactInfoHelper = contactInfoHelper;

        mOnReportButtonClickListener = onReportButtonClickListener;

        mContactInfoCache = new ContactInfoCache(
                mContactInfoHelper, mOnContactInfoChangedListener);

        Resources resources = mContext.getResources();
        CallTypeHelper callTypeHelper = new CallTypeHelper(resources);

        mPhoneNumberHelper = new PhoneNumberDisplayHelper(mContext, resources);
        mPhoneNumberUtilsWrapper = new PhoneNumberUtilsWrapper(mContext);
        PhoneCallDetailsHelper phoneCallDetailsHelper =
                new PhoneCallDetailsHelper(mContext, resources, mPhoneNumberUtilsWrapper);
        mCallLogViewsHelper =
                new CallLogListItemHelper(phoneCallDetailsHelper, mPhoneNumberHelper, resources);
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

    public boolean isEmpty() {
        if (mLoading) {
            // We don't want the empty state to show when loading.
            return false;
        } else {
            return getItemCount() == 0;
        }
    }

    /**
     * Stop receiving onPreDraw() notifications.
     */
    private void unregisterPreDrawListener() {
        if (mViewTreeObserver != null && mViewTreeObserver.isAlive()) {
            mViewTreeObserver.removeOnPreDrawListener(this);
        }
        mViewTreeObserver = null;
    }

    public void invalidateCache() {
        mContactInfoCache.invalidate();

        // Restart the request-processing thread after the next draw.
        unregisterPreDrawListener();
    }

    public void pauseCache() {
        mContactInfoCache.stop();
    }

    @Override
    protected void addGroups(Cursor cursor) {
        mCallLogGroupBuilder.addGroups(cursor);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SHOW_CALL_HISTORY_LIST_ITEM) {
            return ShowCallHistoryViewHolder.create(mContext, parent);
        }
        return createCallLogEntryViewHolder(parent);
    }

    /**
     * Creates a new call log entry {@link ViewHolder}.
     *
     * @param parent the parent view.
     * @return The {@link ViewHolder}.
     */
    private ViewHolder createCallLogEntryViewHolder(ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(mContext);
        View view = inflater.inflate(R.layout.call_log_list_item, parent, false);

        CallLogListItemViewHolder viewHolder = CallLogListItemViewHolder.create(
                view,
                mContext,
                mActionListener,
                mPhoneNumberUtilsWrapper,
                mCallLogViewsHelper);
        viewHolder.primaryActionView.setTag(viewHolder);

        return viewHolder;
    }

    /**
     * Binds the views in the entry to the data in the call log.
     * TODO: This gets called 20-30 times when Dialer starts up for a single call log entry and
     * should not. It invokes cross-process methods and the repeat execution can get costly.
     *
     * @param callLogItemView the view corresponding to this entry
     * @param count the number of entries in the current item, greater than 1 if it is a group
     */
    public void onBindViewHolder(ViewHolder viewHolder, int position) {
        if (getItemViewType(position) == VIEW_TYPE_SHOW_CALL_HISTORY_LIST_ITEM) {
            return;
        }

        Cursor c = (Cursor) getItem(position);
        if (c == null) {
            return;
        }
        int count = getGroupSize(position);

        CallLogListItemViewHolder views = (CallLogListItemViewHolder) viewHolder;
        views.rootView.setAccessibilityDelegate(mAccessibilityDelegate);

        // Default case: an item in the call log.
        views.primaryActionView.setVisibility(View.VISIBLE);

        final String number = c.getString(CallLogQuery.NUMBER);
        final int numberPresentation = c.getInt(CallLogQuery.NUMBER_PRESENTATION);
        final long date = c.getLong(CallLogQuery.DATE);
        final long duration = c.getLong(CallLogQuery.DURATION);
        final int callType = c.getInt(CallLogQuery.CALL_TYPE);
        final PhoneAccountHandle accountHandle = PhoneAccountUtils.getAccount(
                c.getString(CallLogQuery.ACCOUNT_COMPONENT_NAME),
                c.getString(CallLogQuery.ACCOUNT_ID));
        final String countryIso = c.getString(CallLogQuery.COUNTRY_ISO);

        final long rowId = c.getLong(CallLogQuery.ID);
        views.rowId = rowId;

        // Check if the day group has changed and display a header if necessary.
        int currentGroup = getDayGroupForCall(rowId);
        int previousGroup = getPreviousDayGroup(c);
        if (currentGroup != previousGroup) {
            views.dayGroupHeader.setVisibility(View.VISIBLE);
            views.dayGroupHeader.setText(getGroupDescription(currentGroup));
        } else {
            views.dayGroupHeader.setVisibility(View.GONE);
        }

        // Store some values used when the actions ViewStub is inflated on expansion of the actions
        // section.
        views.number = number;
        views.numberPresentation = numberPresentation;
        views.callType = callType;
        views.accountHandle = accountHandle;
        views.voicemailUri = c.getString(CallLogQuery.VOICEMAIL_URI);
        // Stash away the Ids of the calls so that we can support deleting a row in the call log.
        views.callIds = getCallIds(c, count);

        final ContactInfo cachedContactInfo = mContactInfoHelper.getContactInfo(c);

        final boolean isVoicemailNumber =
                mPhoneNumberUtilsWrapper.isVoicemailNumber(accountHandle, number);

        // Expand/collapse an actions section for the call log entry when the primary view is tapped.
        views.primaryActionView.setOnClickListener(mExpandCollapseListener);

        // Note: Binding of the action buttons is done as required in configureActionViews when the
        // user expands the actions ViewStub.

        ContactInfo info = ContactInfo.EMPTY;
        if (PhoneNumberUtilsWrapper.canPlaceCallsTo(number, numberPresentation)
                && !isVoicemailNumber) {
            // Lookup contacts with this number
            info = mContactInfoCache.getValue(number, countryIso, cachedContactInfo);
        }

        final Uri lookupUri = info.lookupUri;
        final String name = info.name;
        final int ntype = info.type;
        final String label = info.label;
        final long photoId = info.photoId;
        final Uri photoUri = info.photoUri;
        CharSequence formattedNumber = info.formattedNumber == null
                ? null : PhoneNumberUtils.getPhoneTtsSpannable(info.formattedNumber);
        final int[] callTypes = getCallTypes(c, count);
        final String geocode = c.getString(CallLogQuery.GEOCODED_LOCATION);
        final int sourceType = info.sourceType;
        final int features = getCallFeatures(c, count);
        final String transcription = c.getString(CallLogQuery.TRANSCRIPTION);
        Long dataUsage = null;
        if (!c.isNull(CallLogQuery.DATA_USAGE)) {
            dataUsage = c.getLong(CallLogQuery.DATA_USAGE);
        }

        final PhoneCallDetails details;

        views.info = info;

        // The entry can only be reported as invalid if it has a valid ID and the source of the
        // entry supports marking entries as invalid.
        views.canBeReportedAsInvalid = mContactInfoHelper.canReportAsInvalid(info.sourceType,
                info.objectId);

        // Restore expansion state of the row on rebind.  Inflate the actions ViewStub if required,
        // and set its visibility state accordingly.
        views.showActions(isExpanded(rowId), mOnReportButtonClickListener);

        if (TextUtils.isEmpty(name)) {
            details = new PhoneCallDetails(number, numberPresentation, formattedNumber, countryIso,
                    geocode, callTypes, date, duration, accountHandle, features, dataUsage,
                    transcription);
        } else {
            details = new PhoneCallDetails(number, numberPresentation, formattedNumber, countryIso,
                    geocode, callTypes, date, duration, name, ntype, label, lookupUri, photoUri,
                    sourceType, accountHandle, features, dataUsage, transcription);
        }

        mCallLogViewsHelper.setPhoneCallDetails(mContext, views, details);

        String nameForDefaultImage = null;
        if (TextUtils.isEmpty(name)) {
            nameForDefaultImage = mPhoneNumberHelper.getDisplayNumber(details.accountHandle,
                    details.number, details.numberPresentation, details.formattedNumber).toString();
        } else {
            nameForDefaultImage = name;
        }

        views.setPhoto(photoId, photoUri, lookupUri, nameForDefaultImage, isVoicemailNumber,
                mContactInfoHelper.isBusiness(info.sourceType));

        views.updateCallButton();

        // Listen for the first draw
        if (mViewTreeObserver == null) {
            mViewTreeObserver = views.rootView.getViewTreeObserver();
            mViewTreeObserver.addOnPreDrawListener(this);
        }
    }

    @Override
    public int getItemCount() {
        return super.getItemCount() + (mShowCallHistoryListItem ? 1 : 0);
    }

    @Override
    public int getItemViewType(int position) {
        if (position == getItemCount() - 1 && mShowCallHistoryListItem) {
            return VIEW_TYPE_SHOW_CALL_HISTORY_LIST_ITEM;
        }
        return super.getItemViewType(position);
    }

    public void setShowCallHistoryListItem(boolean show) {
        mShowCallHistoryListItem = show;
    }

    /**
     * Retrieves the day group of the previous call in the call log.  Used to determine if the day
     * group has changed and to trigger display of the day group text.
     *
     * @param cursor The call log cursor.
     * @return The previous day group, or DAY_GROUP_NONE if this is the first call.
     */
    private int getPreviousDayGroup(Cursor cursor) {
        // We want to restore the position in the cursor at the end.
        int startingPosition = cursor.getPosition();
        int dayGroup = CallLogGroupBuilder.DAY_GROUP_NONE;
        if (cursor.moveToPrevious()) {
            long previousRowId = cursor.getLong(CallLogQuery.ID);
            dayGroup = getDayGroupForCall(previousRowId);
        }
        cursor.moveToPosition(startingPosition);
        return dayGroup;
    }

    /**
     * Given a call Id, look up the day group that the call belongs to.  The day group data is
     * populated in {@link com.android.dialer.calllog.CallLogGroupBuilder}.
     *
     * @param callId The call to retrieve the day group for.
     * @return The day group for the call.
     */
    private int getDayGroupForCall(long callId) {
        if (mDayGroups.containsKey(callId)) {
            return mDayGroups.get(callId);
        }
        return CallLogGroupBuilder.DAY_GROUP_NONE;
    }

    /**
     * Determines if a call log row with the given Id is expanded.
     * @param rowId The row Id of the call.
     * @return True if the row should be expanded.
     */
    private boolean isExpanded(long rowId) {
        return mCurrentlyExpanded == rowId;
    }

    /**
     * Toggles the expansion state tracked for the call log row identified by rowId and returns
     * the new expansion state.  Assumes that only a single call log row will be expanded at any
     * one point and tracks the current and previous expanded item.
     *
     * @param rowId The row Id associated with the call log row to expand/collapse.
     * @return True where the row is now expanded, false otherwise.
     */
    private boolean toggleExpansion(long rowId) {
        if (rowId == mCurrentlyExpanded) {
            // Collapsing currently expanded row.
            mCurrentlyExpanded = NONE_EXPANDED;
            return false;
        } else {
            // Expanding a row (collapsing current expanded one).
            mCurrentlyExpanded = rowId;
            return true;
        }
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

    /**
     * Determine the features which were enabled for any of the calls that make up a call log
     * entry.
     *
     * @param cursor The cursor.
     * @param count The number of calls for the current call log entry.
     * @return The features.
     */
    private int getCallFeatures(Cursor cursor, int count) {
        int features = 0;
        int position = cursor.getPosition();
        for (int index = 0; index < count; ++index) {
            features |= cursor.getInt(CallLogQuery.FEATURES);
            cursor.moveToNext();
        }
        cursor.moveToPosition(position);
        return features;
    }

    /**
     * Sets whether processing of requests for contact details should be enabled.
     *
     * This method should be called in tests to disable such processing of requests when not
     * needed.
     */
    @VisibleForTesting
    void disableRequestProcessingForTest() {
        // TODO: Remove this and test the cache directly.
        mContactInfoCache.disableRequestProcessingForTest();
    }

    @VisibleForTesting
    void injectContactInfoForTest(String number, String countryIso, ContactInfo contactInfo) {
        // TODO: Remove this and test the cache directly.
        mContactInfoCache.injectContactInfoForTest(number, countryIso, contactInfo);
    }

    @Override
    public void addGroup(int cursorPosition, int size, boolean expanded) {
        super.addGroup(cursorPosition, size, expanded);
    }

    /**
     * Stores the day group associated with a call in the call log.
     *
     * @param rowId The row Id of the current call.
     * @param dayGroup The day group the call belongs in.
     */
    @Override
    public void setDayGroup(long rowId, int dayGroup) {
        if (!mDayGroups.containsKey(rowId)) {
            mDayGroups.put(rowId, dayGroup);
        }
    }

    /**
     * Clears the day group associations on re-bind of the call log.
     */
    @Override
    public void clearDayGroups() {
        mDayGroups.clear();
    }

    /**
     * Retrieves the call Ids represented by the current call log row.
     *
     * @param cursor Call log cursor to retrieve call Ids from.
     * @param groupSize Number of calls associated with the current call log row.
     * @return Array of call Ids.
     */
    private long[] getCallIds(final Cursor cursor, final int groupSize) {
        // We want to restore the position in the cursor at the end.
        int startingPosition = cursor.getPosition();
        long[] ids = new long[groupSize];
        // Copy the ids of the rows in the group.
        for (int index = 0; index < groupSize; ++index) {
            ids[index] = cursor.getLong(CallLogQuery.ID);
            cursor.moveToNext();
        }
        cursor.moveToPosition(startingPosition);
        return ids;
    }

    /**
     * Determines the description for a day group.
     *
     * @param group The day group to retrieve the description for.
     * @return The day group description.
     */
    private CharSequence getGroupDescription(int group) {
       if (group == CallLogGroupBuilder.DAY_GROUP_TODAY) {
           return mContext.getResources().getString(R.string.call_log_header_today);
       } else if (group == CallLogGroupBuilder.DAY_GROUP_YESTERDAY) {
           return mContext.getResources().getString(R.string.call_log_header_yesterday);
       } else {
           return mContext.getResources().getString(R.string.call_log_header_other);
       }
    }

    /**
     * Manages the state changes for the UI interaction where a call log row is expanded.
     *
     * @param view The view that was tapped
     * @param forceExpand Whether or not to force the call log row into an expanded state regardless
     *        of its previous state
     */
    private void handleRowExpanded(View view, boolean forceExpand) {
        final CallLogListItemViewHolder views = (CallLogListItemViewHolder) view.getTag();

        if (views == null || (forceExpand && isExpanded(views.rowId))) {
            return;
        }

        boolean expanded = toggleExpansion(views.rowId);
        views.showActions(expanded, mOnReportButtonClickListener);
    }
}
