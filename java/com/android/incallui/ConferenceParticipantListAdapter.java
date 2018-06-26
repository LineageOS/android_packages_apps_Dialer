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
import android.support.v4.util.ArrayMap;
import android.telephony.PhoneNumberUtils;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import com.android.dialer.common.LogUtil;
import com.android.dialer.contactphoto.ContactPhotoManager;
import com.android.dialer.contactphoto.ContactPhotoManager.DefaultImageRequest;
import com.android.dialer.contacts.ContactsComponent;
import com.android.incallui.ContactInfoCache.ContactCacheEntry;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.state.DialerCallState;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Adapter for a ListView containing conference call participant information. */
public class ConferenceParticipantListAdapter extends BaseAdapter {

  /** The ListView containing the participant information. */
  private final ListView listView;
  /** Hashmap to make accessing participant info by call Id faster. */
  private final Map<String, ParticipantInfo> participantsByCallId = new ArrayMap<>();
  /** Contact photo manager to retrieve cached contact photo information. */
  private final ContactPhotoManager contactPhotoManager;
  /** Listener used to handle tap of the "disconnect' button for a participant. */
  private View.OnClickListener disconnectListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          DialerCall call = getCallFromView(view);
          LogUtil.i(
              "ConferenceParticipantListAdapter.mDisconnectListener.onClick", "call: " + call);
          if (call != null) {
            call.disconnect();
          }
        }
      };
  /** Listener used to handle tap of the "separate' button for a participant. */
  private View.OnClickListener separateListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          DialerCall call = getCallFromView(view);
          LogUtil.i("ConferenceParticipantListAdapter.mSeparateListener.onClick", "call: " + call);
          if (call != null) {
            call.splitFromConference();
          }
        }
      };
  /** The conference participants to show in the ListView. */
  private List<ParticipantInfo> conferenceParticipants = new ArrayList<>();
  /** {@code True} if the conference parent supports separating calls from the conference. */
  private boolean parentCanSeparate;

  /**
   * Creates an instance of the ConferenceParticipantListAdapter.
   *
   * @param listView The listview.
   * @param contactPhotoManager The contact photo manager, used to load contact photos.
   */
  public ConferenceParticipantListAdapter(
      ListView listView, ContactPhotoManager contactPhotoManager) {

    this.listView = listView;
    this.contactPhotoManager = contactPhotoManager;
  }

  /**
   * Updates the adapter with the new conference participant information provided.
   *
   * @param conferenceParticipants The list of conference participants.
   * @param parentCanSeparate {@code True} if the parent supports separating calls from the
   *     conference.
   */
  public void updateParticipants(
      List<DialerCall> conferenceParticipants, boolean parentCanSeparate) {
    this.parentCanSeparate = parentCanSeparate;
    updateParticipantInfo(conferenceParticipants);
  }

  /**
   * Determines the number of participants in the conference.
   *
   * @return The number of participants.
   */
  @Override
  public int getCount() {
    return conferenceParticipants.size();
  }

  /**
   * Retrieves an item from the list of participants.
   *
   * @param position Position of the item whose data we want within the adapter's data set.
   * @return The {@link ParticipantInfo}.
   */
  @Override
  public Object getItem(int position) {
    return conferenceParticipants.get(position);
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
  public void refreshCall(DialerCall call) {
    String callId = call.getId();

    if (participantsByCallId.containsKey(callId)) {
      ParticipantInfo participantInfo = participantsByCallId.get(callId);
      participantInfo.setCall(call);
      refreshView(callId);
    }
  }

  private Context getContext() {
    return listView.getContext();
  }

  /**
   * Attempts to refresh the view for the specified call ID. This ensures the contact info and photo
   * loaded from cache are updated.
   *
   * @param callId The call id.
   */
  private void refreshView(String callId) {
    int first = listView.getFirstVisiblePosition();
    int last = listView.getLastVisiblePosition();

    for (int position = 0; position <= last - first; position++) {
      View view = listView.getChildAt(position);
      String rowCallId = (String) view.getTag();
      if (rowCallId.equals(callId)) {
        getView(position + first, view, listView);
        break;
      }
    }
  }

  /**
   * Creates or populates an existing conference participant row.
   *
   * @param position The position of the item within the adapter's data set of the item whose view
   *     we want.
   * @param convertView The old view to reuse, if possible.
   * @param parent The parent that this view will eventually be attached to
   * @return The populated view.
   */
  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    // Make sure we have a valid convertView to start with
    final View result =
        convertView == null
            ? LayoutInflater.from(parent.getContext())
                .inflate(R.layout.caller_in_conference, parent, false)
            : convertView;

    ParticipantInfo participantInfo = conferenceParticipants.get(position);
    DialerCall call = participantInfo.getCall();
    ContactCacheEntry contactCache = participantInfo.getContactCacheEntry();

    final ContactInfoCache cache = ContactInfoCache.getInstance(getContext());

    // If a cache lookup has not yet been performed to retrieve the contact information and
    // photo, do it now.
    if (!participantInfo.isCacheLookupComplete()) {
      cache.findInfo(
          participantInfo.getCall(),
          participantInfo.getCall().getState() == DialerCallState.INCOMING,
          new ContactLookupCallback(this));
    }

    boolean thisRowCanSeparate =
        parentCanSeparate
            && call.can(android.telecom.Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE);
    boolean thisRowCanDisconnect =
        call.can(android.telecom.Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE);

    String name =
        ContactsComponent.get(getContext())
            .contactDisplayPreferences()
            .getDisplayName(contactCache.namePrimary, contactCache.nameAlternative);

    setCallerInfoForRow(
        result,
        contactCache.namePrimary,
        call.updateNameIfRestricted(name),
        contactCache.number,
        contactCache.lookupKey,
        contactCache.displayPhotoUri,
        thisRowCanSeparate,
        thisRowCanDisconnect,
        call.getNonConferenceState());

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
    if (participantsByCallId.containsKey(callId)) {
      ParticipantInfo participantInfo = participantsByCallId.get(callId);
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
   * @param lookupKey The lookup key for the participant (for photo lookup).
   * @param photoUri The URI of the contact photo.
   * @param thisRowCanSeparate {@code True} if this participant can separate from the conference.
   * @param thisRowCanDisconnect {@code True} if this participant can be disconnected.
   */
  private void setCallerInfoForRow(
      View view,
      String callerName,
      String preferredName,
      String callerNumber,
      String lookupKey,
      Uri photoUri,
      boolean thisRowCanSeparate,
      boolean thisRowCanDisconnect,
      int callState) {

    final ImageView photoView = (ImageView) view.findViewById(R.id.callerPhoto);
    final TextView statusTextView = (TextView) view.findViewById(R.id.conferenceCallerStatus);
    final TextView nameTextView = (TextView) view.findViewById(R.id.conferenceCallerName);
    final TextView numberTextView = (TextView) view.findViewById(R.id.conferenceCallerNumber);
    final View endButton = view.findViewById(R.id.conferenceCallerDisconnect);
    final View separateButton = view.findViewById(R.id.conferenceCallerSeparate);

    if (callState == DialerCallState.ONHOLD) {
      setViewsOnHold(photoView, statusTextView, nameTextView, numberTextView);
    } else {
      setViewsNotOnHold(photoView, statusTextView, nameTextView, numberTextView);
    }

    endButton.setVisibility(thisRowCanDisconnect ? View.VISIBLE : View.GONE);
    if (thisRowCanDisconnect) {
      endButton.setOnClickListener(disconnectListener);
    } else {
      endButton.setOnClickListener(null);
    }

    separateButton.setVisibility(thisRowCanSeparate ? View.VISIBLE : View.GONE);
    if (thisRowCanSeparate) {
      separateButton.setOnClickListener(separateListener);
    } else {
      separateButton.setOnClickListener(null);
    }

    String displayNameForImage = TextUtils.isEmpty(callerName) ? callerNumber : callerName;
    DefaultImageRequest imageRequest =
        (photoUri != null)
            ? null
            : new DefaultImageRequest(displayNameForImage, lookupKey, true /* isCircularPhoto */);

    contactPhotoManager.loadDirectoryPhoto(photoView, photoUri, false, true, imageRequest);

    // set the caller name
    if (TextUtils.isEmpty(preferredName)) {
      nameTextView.setVisibility(View.GONE);
    } else {
      nameTextView.setVisibility(View.VISIBLE);
      nameTextView.setText(preferredName);
    }

    // set the caller number in subscript, or make the field disappear.
    if (TextUtils.isEmpty(callerNumber)) {
      numberTextView.setVisibility(View.GONE);
    } else {
      numberTextView.setVisibility(View.VISIBLE);
      numberTextView.setText(
          PhoneNumberUtils.createTtsSpannable(
              BidiFormatter.getInstance().unicodeWrap(callerNumber, TextDirectionHeuristics.LTR)));
    }
  }

  private void setViewsOnHold(
      ImageView photoView,
      TextView statusTextView,
      TextView nameTextView,
      TextView numberTextView) {
    CharSequence onHoldText =
        TextUtils.concat(getContext().getText(R.string.notification_on_hold).toString(), " • ");
    statusTextView.setText(onHoldText);
    statusTextView.setVisibility(View.VISIBLE);

    nameTextView.setEnabled(false);
    numberTextView.setEnabled(false);

    TypedValue alpha = new TypedValue();
    getContext().getResources().getValue(R.dimen.alpha_hiden, alpha, true);
    photoView.setAlpha(alpha.getFloat());
  }

  private void setViewsNotOnHold(
      ImageView photoView,
      TextView statusTextView,
      TextView nameTextView,
      TextView numberTextView) {
    statusTextView.setVisibility(View.GONE);

    nameTextView.setEnabled(true);
    numberTextView.setEnabled(true);

    TypedValue alpha = new TypedValue();
    getContext().getResources().getValue(R.dimen.alpha_enabled, alpha, true);
    photoView.setAlpha(alpha.getFloat());
  }

  /**
   * Updates the participant info list which is bound to the ListView. Stores the call and contact
   * info for all entries. The list is sorted alphabetically by participant name.
   *
   * @param conferenceParticipants The calls which make up the conference participants.
   */
  private void updateParticipantInfo(List<DialerCall> conferenceParticipants) {
    final ContactInfoCache cache = ContactInfoCache.getInstance(getContext());
    boolean newParticipantAdded = false;
    Set<String> newCallIds = new ArraySet<>(conferenceParticipants.size());

    // Update or add conference participant info.
    for (DialerCall call : conferenceParticipants) {
      String callId = call.getId();
      newCallIds.add(callId);
      ContactCacheEntry contactCache = cache.getInfo(callId);
      if (contactCache == null) {
        contactCache = ContactInfoCache.buildCacheEntryFromCall(getContext(), call);
      }

      if (participantsByCallId.containsKey(callId)) {
        ParticipantInfo participantInfo = participantsByCallId.get(callId);
        participantInfo.setCall(call);
        participantInfo.setContactCacheEntry(contactCache);
      } else {
        newParticipantAdded = true;
        ParticipantInfo participantInfo = new ParticipantInfo(call, contactCache);
        this.conferenceParticipants.add(participantInfo);
        participantsByCallId.put(call.getId(), participantInfo);
      }
    }

    // Remove any participants that no longer exist.
    Iterator<Map.Entry<String, ParticipantInfo>> it = participantsByCallId.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, ParticipantInfo> entry = it.next();
      String existingCallId = entry.getKey();
      if (!newCallIds.contains(existingCallId)) {
        ParticipantInfo existingInfo = entry.getValue();
        this.conferenceParticipants.remove(existingInfo);
        it.remove();
      }
    }

    if (newParticipantAdded) {
      // Sort the list of participants by contact name.
      sortParticipantList();
    }
    notifyDataSetChanged();
  }

  /** Sorts the participant list by contact name. */
  private void sortParticipantList() {
    Collections.sort(
        conferenceParticipants,
        new Comparator<ParticipantInfo>() {
          @Override
          public int compare(ParticipantInfo p1, ParticipantInfo p2) {
            // Contact names might be null, so replace with empty string.
            ContactCacheEntry c1 = p1.getContactCacheEntry();
            String p1Name =
                ContactsComponent.get(getContext())
                    .contactDisplayPreferences()
                    .getSortName(c1.namePrimary, c1.nameAlternative);
            p1Name = p1Name != null ? p1Name : "";

            ContactCacheEntry c2 = p2.getContactCacheEntry();
            String p2Name =
                ContactsComponent.get(getContext())
                    .contactDisplayPreferences()
                    .getSortName(c2.namePrimary, c2.nameAlternative);
            p2Name = p2Name != null ? p2Name : "";

            return p1Name.compareToIgnoreCase(p2Name);
          }
        });
  }

  private DialerCall getCallFromView(View view) {
    View parent = (View) view.getParent();
    String callId = (String) parent.getTag();
    return CallList.getInstance().getCallById(callId);
  }

  /**
   * Callback class used when making requests to the {@link ContactInfoCache} to resolve contact
   * info and contact photos for conference participants.
   */
  public static class ContactLookupCallback implements ContactInfoCache.ContactInfoCacheCallback {

    private final WeakReference<ConferenceParticipantListAdapter> listAdapter;

    public ContactLookupCallback(ConferenceParticipantListAdapter listAdapter) {
      this.listAdapter = new WeakReference<>(listAdapter);
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
      ConferenceParticipantListAdapter listAdapter = this.listAdapter.get();
      if (listAdapter != null) {
        listAdapter.updateContactInfo(callId, entry);
      }
    }
  }

  /**
   * Internal class which represents a participant. Includes a reference to the {@link DialerCall}
   * and the corresponding {@link ContactCacheEntry} for the participant.
   */
  private static class ParticipantInfo {

    private DialerCall call;
    private ContactCacheEntry contactCacheEntry;
    private boolean cacheLookupComplete = false;

    public ParticipantInfo(DialerCall call, ContactCacheEntry contactCacheEntry) {
      this.call = call;
      this.contactCacheEntry = contactCacheEntry;
    }

    public DialerCall getCall() {
      return call;
    }

    public void setCall(DialerCall call) {
      this.call = call;
    }

    public ContactCacheEntry getContactCacheEntry() {
      return contactCacheEntry;
    }

    public void setContactCacheEntry(ContactCacheEntry entry) {
      contactCacheEntry = entry;
    }

    public boolean isCacheLookupComplete() {
      return cacheLookupComplete;
    }

    public void setCacheLookupComplete(boolean cacheLookupComplete) {
      this.cacheLookupComplete = cacheLookupComplete;
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof ParticipantInfo) {
        ParticipantInfo p = (ParticipantInfo) o;
        return Objects.equals(p.getCall().getId(), call.getId());
      }
      return false;
    }

    @Override
    public int hashCode() {
      return call.getId().hashCode();
    }
  }
}
