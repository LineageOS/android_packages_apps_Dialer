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
 * limitations under the License
 */

package com.android.incallui;

import android.content.Context;
import android.net.Uri;
import android.telephony.PhoneNumberUtils;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.incallui.ContactInfoCache.ContactCacheEntry;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adapter for a ListView containing conference call participant information.
 */
public class ConferenceParticipantListAdapter extends BaseAdapter {

    /**
     * Internal class which represents a participant.  Includes a reference to the {@link Call} and
     * the corresponding {@link ContactCacheEntry} for the participant.
     */
    private class ParticipantInfo {
        private Call mCall;
        private ContactCacheEntry mContactCacheEntry;
        private boolean mCacheLookupComplete = false;

        public ParticipantInfo(Call call, ContactCacheEntry contactCacheEntry) {
            mCall = call;
            mContactCacheEntry = contactCacheEntry;
        }

        public Call getCall() {
            return mCall;
        }

        public void setCall(Call call) {
            mCall = call;
        }

        public ContactCacheEntry getContactCacheEntry() {
            return mContactCacheEntry;
        }

        public void setContactCacheEntry(ContactCacheEntry entry) {
            mContactCacheEntry = entry;
        }

        public boolean isCacheLookupComplete() {
            return mCacheLookupComplete;
        }

        public void setCacheLookupComplete(boolean cacheLookupComplete) {
            mCacheLookupComplete = cacheLookupComplete;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof ParticipantInfo) {
                ParticipantInfo p = (ParticipantInfo) o;
                return
                        Objects.equals(p.getCall().getId(), mCall.getId());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return mCall.getId().hashCode();
        }
    }

    /**
     * Callback class used when making requests to the {@link ContactInfoCache} to resolve contact
     * info and contact photos for conference participants.
     */
    public static class ContactLookupCallback implements ContactInfoCache.ContactInfoCacheCallback {
        private final WeakReference<ConferenceParticipantListAdapter> mListAdapter;

        public ContactLookupCallback(ConferenceParticipantListAdapter listAdapter) {
            mListAdapter = new WeakReference<ConferenceParticipantListAdapter>(listAdapter);
        }

        /**
         * Called when contact info has been resolved.
         *
         * @param callId The call id.
         * @param entry The new contact information.
         */
        @Override
        public void onContactInfoComplete(String callId, ContactCacheEntry entry) {
            update(callId, entry);
        }

        /**
         * Called when contact photo has been loaded into the cache.
         *
         * @param callId The call id.
         * @param entry The new contact information.
         */
        @Override
        public void onImageLoadComplete(String callId, ContactCacheEntry entry) {
            update(callId, entry);
        }

        /**
         * Updates the contact information for a participant.
         *
         * @param callId The call id.
         * @param entry The new contact information.
         */
        private void update(String callId, ContactCacheEntry entry) {
            ConferenceParticipantListAdapter listAdapter = mListAdapter.get();
            if (listAdapter != null) {
                listAdapter.updateContactInfo(callId, entry);
            }
        }
    }

    /**
     * Listener used to handle tap of the "disconnect' button for a participant.
     */
    private View.OnClickListener mDisconnectListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            View parent = (View) v.getParent();
            String callId = (String) parent.getTag();
            TelecomAdapter.getInstance().disconnectCall(callId);
        }
    };

    /**
     * Listener used to handle tap of the "separate' button for a participant.
     */
    private View.OnClickListener mSeparateListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            View parent = (View) v.getParent();
            String callId = (String) parent.getTag();
            TelecomAdapter.getInstance().separateCall(callId);
        }
    };

    /**
     * The ListView containing the participant information.
     */
    private final ListView mListView;

    /**
     * The conference participants to show in the ListView.
     */
    private List<ParticipantInfo> mConferenceParticipants = new ArrayList<>();

    /**
     * Hashmap to make accessing participant info by call Id faster.
     */
    private final HashMap<String, ParticipantInfo> mParticipantsByCallId = new HashMap<>();

    /**
     * The context.
     */
    private final Context mContext;

    /**
     * The layout inflater used to inflate new views.
     */
    private final LayoutInflater mLayoutInflater;

    /**
     * Contact photo manager to retrieve cached contact photo information.
     */
    private final ContactPhotoManager mContactPhotoManager;

    /**
     * {@code True} if the conference parent supports separating calls from the conference.
     */
    private boolean mParentCanSeparate;

    /**
     * Creates an instance of the ConferenceParticipantListAdapter.
     *
     * @param listView The listview.
     * @param context The context.
     * @param layoutInflater The layout inflater.
     * @param contactPhotoManager The contact photo manager, used to load contact photos.
     */
    public ConferenceParticipantListAdapter(ListView listView, Context context,
            LayoutInflater layoutInflater, ContactPhotoManager contactPhotoManager) {

        mListView = listView;
        mContext = context;
        mLayoutInflater = layoutInflater;
        mContactPhotoManager = contactPhotoManager;
    }

    /**
     * Updates the adapter with the new conference participant information provided.
     *
     * @param conferenceParticipants The list of conference participants.
     * @param parentCanSeparate {@code True} if the parent supports separating calls from the
     *                                      conference.
     */
    public void updateParticipants(List<Call> conferenceParticipants, boolean parentCanSeparate) {
        mParentCanSeparate = parentCanSeparate;
        updateParticipantInfo(conferenceParticipants);
    }

    /**
     * Determines the number of participants in the conference.
     *
     * @return The number of participants.
     */
    @Override
    public int getCount() {
        return mConferenceParticipants.size();
    }

    /**
     * Retrieves an item from the list of participants.
     *
     * @param position Position of the item whose data we want within the adapter's
     * data set.
     * @return The {@link ParticipantInfo}.
     */
    @Override
    public Object getItem(int position) {
        return mConferenceParticipants.get(position);
    }

    /**
     * Retreives the adapter-specific item id for an item at a specified position.
     *
     * @param position The position of the item within the adapter's data set whose row id we want.
     * @return The item id.
     */
    @Override
    public long getItemId(int position) {
        return position;
    }

    /**
     * Refreshes call information for the call passed in.
     *
     * @param call The new call information.
     */
    public void refreshCall(Call call) {
        String callId = call.getId();

        if (mParticipantsByCallId.containsKey(callId)) {
            ParticipantInfo participantInfo = mParticipantsByCallId.get(callId);
            participantInfo.setCall(call);
            refreshView(callId);
        }
    }

    /**
     * Attempts to refresh the view for the specified call ID.  This ensures the contact info and
     * photo loaded from cache are updated.
     *
     * @param callId The call id.
     */
    private void refreshView(String callId) {
        int first = mListView.getFirstVisiblePosition();
        int last = mListView.getLastVisiblePosition();

        for (int position = 0; position <= last - first; position++) {
            View view = mListView.getChildAt(position);
            String rowCallId = (String) view.getTag();
            if (rowCallId.equals(callId)) {
                getView(position+first, view, mListView);
                break;
            }
        }
    }

    /**
     * Creates or populates an existing conference participant row.
     *
     * @param position The position of the item within the adapter's data set of the item whose view
     *        we want.
     * @param convertView The old view to reuse, if possible.
     * @param parent The parent that this view will eventually be attached to
     * @return The populated view.
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Make sure we have a valid convertView to start with
        final View result = convertView == null
                        ? mLayoutInflater.inflate(R.layout.caller_in_conference, parent, false)
                        : convertView;

        ParticipantInfo participantInfo = mConferenceParticipants.get(position);
        Call call = participantInfo.getCall();
        ContactCacheEntry contactCache = participantInfo.getContactCacheEntry();

        final ContactInfoCache cache = ContactInfoCache.getInstance(mContext);

        // If a cache lookup has not yet been performed to retrieve the contact information and
        // photo, do it now.
        if (!participantInfo.isCacheLookupComplete()) {
            cache.findInfo(participantInfo.getCall(),
                    participantInfo.getCall().getState() == Call.State.INCOMING,
                    new ContactLookupCallback(this));
        }

        boolean thisRowCanSeparate = mParentCanSeparate && call.getTelecommCall().getDetails().can(
                android.telecom.Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE);
        boolean thisRowCanDisconnect = call.getTelecommCall().getDetails().can(
                android.telecom.Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE);

        setCallerInfoForRow(result, contactCache.name, contactCache.number, contactCache.label,
                contactCache.lookupKey, contactCache.displayPhotoUri, thisRowCanSeparate,
                thisRowCanDisconnect);

        // Tag the row in the conference participant list with the call id to make it easier to
        // find calls when contact cache information is loaded.
        result.setTag(call.getId());

        return result;
    }

    /**
     * Replaces the contact info for a participant and triggers a refresh of the UI.
     *
     * @param callId The call id.
     * @param entry The new contact info.
     */
    /* package */ void updateContactInfo(String callId, ContactCacheEntry entry) {
        if (mParticipantsByCallId.containsKey(callId)) {
            ParticipantInfo participantInfo = mParticipantsByCallId.get(callId);
            participantInfo.setContactCacheEntry(entry);
            participantInfo.setCacheLookupComplete(true);
            refreshView(callId);
        }
    }

    /**
     * Sets the caller information for a row in the conference participant list.
     *
     * @param view The view to set the details on.
     * @param callerName The participant's name.
     * @param callerNumber The participant's phone number.
     * @param callerNumberType The participant's phone number typ.e
     * @param lookupKey The lookup key for the participant (for photo lookup).
     * @param photoUri The URI of the contact photo.
     * @param thisRowCanSeparate {@code True} if this participant can separate from the conference.
     * @param thisRowCanDisconnect {@code True} if this participant can be disconnected.
     */
    private final void setCallerInfoForRow(View view, String callerName, String callerNumber,
            String callerNumberType, String lookupKey, Uri photoUri, boolean thisRowCanSeparate,
            boolean thisRowCanDisconnect) {

        final ImageView photoView = (ImageView) view.findViewById(R.id.callerPhoto);
        final TextView nameTextView = (TextView) view.findViewById(R.id.conferenceCallerName);
        final TextView numberTextView = (TextView) view.findViewById(R.id.conferenceCallerNumber);
        final TextView numberTypeTextView = (TextView) view.findViewById(
                R.id.conferenceCallerNumberType);
        final View endButton = view.findViewById(R.id.conferenceCallerDisconnect);
        final View separateButton = view.findViewById(R.id.conferenceCallerSeparate);

        endButton.setVisibility(thisRowCanDisconnect ? View.VISIBLE : View.GONE);
        if (thisRowCanDisconnect) {
            endButton.setOnClickListener(mDisconnectListener);
        } else {
            endButton.setOnClickListener(null);
        }

        separateButton.setVisibility(thisRowCanSeparate ? View.VISIBLE : View.GONE);
        if (thisRowCanSeparate) {
            separateButton.setOnClickListener(mSeparateListener);
        } else {
            separateButton.setOnClickListener(null);
        }

        DefaultImageRequest imageRequest = (photoUri != null) ? null :
                new DefaultImageRequest(callerName, lookupKey, true /* isCircularPhoto */);

        mContactPhotoManager.loadDirectoryPhoto(photoView, photoUri, false, true, imageRequest);

        // set the caller name
        nameTextView.setText(callerName);

        // set the caller number in subscript, or make the field disappear.
        if (TextUtils.isEmpty(callerNumber)) {
            numberTextView.setVisibility(View.GONE);
            numberTypeTextView.setVisibility(View.GONE);
        } else {
            numberTextView.setVisibility(View.VISIBLE);
            numberTextView.setText(PhoneNumberUtils.createTtsSpannable(
                    BidiFormatter.getInstance().unicodeWrap(
                            callerNumber, TextDirectionHeuristics.LTR)));
            numberTypeTextView.setVisibility(View.VISIBLE);
            numberTypeTextView.setText(callerNumberType);
        }
    }

    /**
     * Updates the participant info list which is bound to the ListView.  Stores the call and
     * contact info for all entries.  The list is sorted alphabetically by participant name.
     *
     * @param conferenceParticipants The calls which make up the conference participants.
     */
    private void updateParticipantInfo(List<Call> conferenceParticipants) {
        final ContactInfoCache cache = ContactInfoCache.getInstance(mContext);
        boolean newParticipantAdded = false;
        HashSet<String> newCallIds = new HashSet<>(conferenceParticipants.size());

        // Update or add conference participant info.
        for (Call call : conferenceParticipants) {
            String callId = call.getId();
            newCallIds.add(callId);
            ContactCacheEntry contactCache = cache.getInfo(callId);
            if (contactCache == null) {
                contactCache = ContactInfoCache.buildCacheEntryFromCall(mContext, call,
                        call.getState() == Call.State.INCOMING);
            }

            if (mParticipantsByCallId.containsKey(callId)) {
                ParticipantInfo participantInfo = mParticipantsByCallId.get(callId);
                participantInfo.setCall(call);
                participantInfo.setContactCacheEntry(contactCache);
            } else {
                newParticipantAdded = true;
                ParticipantInfo participantInfo = new ParticipantInfo(call, contactCache);
                mConferenceParticipants.add(participantInfo);
                mParticipantsByCallId.put(call.getId(), participantInfo);
            }
        }

        // Remove any participants that no longer exist.
        Iterator<Map.Entry<String, ParticipantInfo>> it =
                mParticipantsByCallId.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ParticipantInfo> entry = it.next();
            String existingCallId = entry.getKey();
            if (!newCallIds.contains(existingCallId)) {
                ParticipantInfo existingInfo = entry.getValue();
                mConferenceParticipants.remove(existingInfo);
                it.remove();
            }
        }

        if (newParticipantAdded) {
            // Sort the list of participants by contact name.
            sortParticipantList();
        }
        notifyDataSetChanged();
    }

    /**
     * Sorts the participant list by contact name.
     */
    private void sortParticipantList() {
        Collections.sort(mConferenceParticipants, new Comparator<ParticipantInfo>() {
            public int compare(ParticipantInfo p1, ParticipantInfo p2) {
                // Contact names might be null, so replace with empty string.
                String p1Name = p1.getContactCacheEntry().name;
                if (p1Name == null) {
                    p1Name = "";
                }

                String p2Name = p2.getContactCacheEntry().name;
                if (p2Name == null) {
                    p2Name = "";
                }

                return p1Name.compareToIgnoreCase(p2Name);
            }
        });
    }
}
