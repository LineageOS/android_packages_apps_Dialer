/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2013 Android Open Kang Project
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

package com.android.dialer.callstats;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.CallLog.Calls;
import android.telecom.PhoneAccountHandle;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;

import com.android.contacts.common.preference.ContactsPreferences;
import com.android.dialer.calllogutils.PhoneNumberDisplayUtil;
import com.android.dialer.logging.ContactSource;
import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;

/**
 * Class to store statistical details for a given contact/number.
 */
public class CallStatsDetails implements Parcelable {
  public final String number;
  public final String postDialDigits;
  public final int numberPresentation;
  public String formattedNumber;
  public final String countryIso;
  public final String geocode;
  public final long date;
  public String name;
  public String nameAlternative;
  public int numberType;
  public String numberLabel;
  public Uri contactUri;
  public Uri photoUri;
  public long photoId;
  public long inDuration;
  public long outDuration;
  public int incomingCount;
  public int outgoingCount;
  public int missedCount;
  public int blockedCount;
  public PhoneAccountHandle accountHandle;
  public ContactSource.Type sourceType = ContactSource.Type.UNKNOWN_SOURCE_TYPE;

  public boolean isVoicemailNumber;
  public String displayNumber;
  public String displayName;

  public CallStatsDetails(CharSequence number, int numberPresentation,
      String postDialDigits, PhoneAccountHandle accountHandle,
      ContactInfo info, String countryIso, String geocode, long date) {
    this.number = number != null ? number.toString() : null;
    this.numberPresentation = numberPresentation;
    this.postDialDigits = postDialDigits;
    this.countryIso = countryIso;
    this.geocode = geocode;
    this.date = date;

    reset();

    if (info != null) {
      updateFromInfo(info);
    }
  }

  public void updateFromInfo(ContactInfo info) {
    this.displayName = info.name;
    this.nameAlternative = info.nameAlternative;
    this.name = info.name;
    this.numberType = info.type;
    this.numberLabel = info.label;
    this.photoId = info.photoId;
    this.photoUri = info.photoUri;
    this.formattedNumber = info.formattedNumber;
    this.contactUri = info.lookupUri;
    this.photoUri = info.photoUri;
    this.photoId = info.photoId;
    this.sourceType = info.sourceType;
    this.displayNumber = null;
  }


  public void updateDisplayProperties(Context context, int nameDisplayOrder) {
    if (nameDisplayOrder == ContactsPreferences.DISPLAY_ORDER_PRIMARY
        || TextUtils.isEmpty(nameAlternative)) {
      this.displayName = this.name;
    } else {
      this.displayName = this.nameAlternative;
    }

    if (displayNumber == null) {
      isVoicemailNumber = PhoneNumberHelper.isVoicemailNumber(context, accountHandle, number);
      final CharSequence displayNumber = PhoneNumberDisplayUtil.getDisplayNumber(context,
          number, numberPresentation, formattedNumber, postDialDigits, isVoicemailNumber);
      this.displayNumber = BidiFormatter.getInstance().unicodeWrap(
          displayNumber.toString(), TextDirectionHeuristics.LTR);
    }
  }

  public long getFullDuration() {
    return inDuration + outDuration;
  }

  public int getTotalCount() {
    return incomingCount + outgoingCount + missedCount + blockedCount;
  }

  public void addTimeOrMissed(int type, long time) {
    switch (type) {
      case Calls.INCOMING_TYPE:
        incomingCount++;
        inDuration += time;
        break;
      case Calls.OUTGOING_TYPE:
        outgoingCount++;
        outDuration += time;
        break;
      case Calls.MISSED_TYPE:
        missedCount++;
        break;
      case Calls.BLOCKED_TYPE:
        blockedCount++;
        break;
    }
  }

  public int getDurationPercentage(int type) {
    long duration = getRequestedDuration(type);
    return Math.round((float) duration * 100F / getFullDuration());
  }

  public int getCountPercentage(int type) {
    int count = getRequestedCount(type);
    return Math.round((float) count * 100F / getTotalCount());
  }

  public long getRequestedDuration(int type) {
    switch (type) {
      case Calls.INCOMING_TYPE:
        return inDuration;
      case Calls.OUTGOING_TYPE:
        return outDuration;
      case Calls.MISSED_TYPE:
        return (long) missedCount;
      case Calls.BLOCKED_TYPE:
        return (long) blockedCount;
      default:
        return getFullDuration();
    }
  }

  public int getRequestedCount(int type) {
    switch (type) {
      case Calls.INCOMING_TYPE:
        return incomingCount;
      case Calls.OUTGOING_TYPE:
        return outgoingCount;
      case Calls.MISSED_TYPE:
        return missedCount;
      case Calls.BLOCKED_TYPE:
        return blockedCount;
      default:
        return getTotalCount();
    }
  }

  public void mergeWith(CallStatsDetails other) {
    this.inDuration += other.inDuration;
    this.outDuration += other.outDuration;
    this.incomingCount += other.incomingCount;
    this.outgoingCount += other.outgoingCount;
    this.missedCount += other.missedCount;
    this.blockedCount += other.blockedCount;
  }

  public void reset() {
    this.inDuration = this.outDuration = 0;
    this.incomingCount = this.outgoingCount = this.missedCount = this.blockedCount = 0;
  }

  /* Parcelable interface */

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel out, int flags) {
    out.writeString(number);
    out.writeInt(numberPresentation);
    out.writeString(postDialDigits);
    out.writeString(formattedNumber);
    out.writeString(countryIso);
    out.writeString(geocode);
    out.writeLong(date);
    out.writeString(name);
    out.writeInt(numberType);
    out.writeString(numberLabel);
    out.writeParcelable(contactUri, flags);
    out.writeParcelable(photoUri, flags);
    out.writeLong(photoId);
    out.writeLong(inDuration);
    out.writeLong(outDuration);
    out.writeInt(incomingCount);
    out.writeInt(outgoingCount);
    out.writeInt(missedCount);
    out.writeInt(blockedCount);
  }

  public static final Parcelable.Creator<CallStatsDetails> CREATOR =
      new Parcelable.Creator<CallStatsDetails>() {
    public CallStatsDetails createFromParcel(Parcel in) {
      return new CallStatsDetails(in);
    }

    public CallStatsDetails[] newArray(int size) {
      return new CallStatsDetails[size];
    }
  };

  private CallStatsDetails (Parcel in) {
    number = in.readString();
    numberPresentation = in.readInt();
    postDialDigits = in.readString();
    formattedNumber = in.readString();
    countryIso = in.readString();
    geocode = in.readString();
    date = in.readLong();
    name = in.readString();
    numberType = in.readInt();
    numberLabel = in.readString();
    contactUri = in.readParcelable(null);
    photoUri = in.readParcelable(null);
    photoId = in.readLong();
    inDuration = in.readLong();
    outDuration = in.readLong();
    incomingCount = in.readInt();
    outgoingCount = in.readInt();
    missedCount = in.readInt();
    blockedCount = in.readInt();
  }
}
