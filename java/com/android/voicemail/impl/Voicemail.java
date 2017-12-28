/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.voicemail.impl;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;

/** Represents a single voicemail stored in the voicemail content provider. */
public class Voicemail implements Parcelable {

  private final Long timestamp;
  private final String number;
  private final PhoneAccountHandle phoneAccount;
  private final Long id;
  private final Long duration;
  private final String source;
  private final String providerData;
  private final Uri uri;
  private final Boolean isRead;
  private final Boolean hasContent;
  private final String transcription;

  private Voicemail(
      Long timestamp,
      String number,
      PhoneAccountHandle phoneAccountHandle,
      Long id,
      Long duration,
      String source,
      String providerData,
      Uri uri,
      Boolean isRead,
      Boolean hasContent,
      String transcription) {
    this.timestamp = timestamp;
    this.number = number;
    phoneAccount = phoneAccountHandle;
    this.id = id;
    this.duration = duration;
    this.source = source;
    this.providerData = providerData;
    this.uri = uri;
    this.isRead = isRead;
    this.hasContent = hasContent;
    this.transcription = transcription;
  }

  /**
   * Create a {@link Builder} for a new {@link Voicemail} to be inserted.
   *
   * <p>The number and the timestamp are mandatory for insertion.
   */
  public static Builder createForInsertion(long timestamp, String number) {
    return new Builder().setNumber(number).setTimestamp(timestamp);
  }

  /**
   * Create a {@link Builder} for a {@link Voicemail} to be updated (or deleted).
   *
   * <p>The id and source data fields are mandatory for update - id is necessary for updating the
   * database and source data is necessary for updating the server.
   */
  public static Builder createForUpdate(long id, String sourceData) {
    return new Builder().setId(id).setSourceData(sourceData);
  }

  /**
   * Builder pattern for creating a {@link Voicemail}. The builder must be created with the {@link
   * #createForInsertion(long, String)} method.
   *
   * <p>This class is <b>not thread safe</b>
   */
  public static class Builder {

    private Long builderTimestamp;
    private String builderNumber;
    private PhoneAccountHandle builderPhoneAccount;
    private Long builderId;
    private Long builderDuration;
    private String builderSourcePackage;
    private String builderSourceData;
    private Uri builderUri;
    private Boolean builderIsRead;
    private boolean builderHasContent;
    private String builderTranscription;

    /** You should use the correct factory method to construct a builder. */
    private Builder() {}

    public Builder setNumber(String number) {
      builderNumber = number;
      return this;
    }

    public Builder setTimestamp(long timestamp) {
      builderTimestamp = timestamp;
      return this;
    }

    public Builder setPhoneAccount(PhoneAccountHandle phoneAccount) {
      builderPhoneAccount = phoneAccount;
      return this;
    }

    public Builder setId(long id) {
      builderId = id;
      return this;
    }

    public Builder setDuration(long duration) {
      builderDuration = duration;
      return this;
    }

    public Builder setSourcePackage(String sourcePackage) {
      builderSourcePackage = sourcePackage;
      return this;
    }

    public Builder setSourceData(String sourceData) {
      builderSourceData = sourceData;
      return this;
    }

    public Builder setUri(Uri uri) {
      builderUri = uri;
      return this;
    }

    public Builder setIsRead(boolean isRead) {
      builderIsRead = isRead;
      return this;
    }

    public Builder setHasContent(boolean hasContent) {
      builderHasContent = hasContent;
      return this;
    }

    public Builder setTranscription(String transcription) {
      builderTranscription = transcription;
      return this;
    }

    public Voicemail build() {
      builderId = builderId == null ? -1 : builderId;
      builderTimestamp = builderTimestamp == null ? 0 : builderTimestamp;
      builderDuration = builderDuration == null ? 0 : builderDuration;
      builderIsRead = builderIsRead == null ? false : builderIsRead;
      return new Voicemail(
          builderTimestamp,
          builderNumber,
          builderPhoneAccount,
          builderId,
          builderDuration,
          builderSourcePackage,
          builderSourceData,
          builderUri,
          builderIsRead,
          builderHasContent,
          builderTranscription);
    }
  }

  /**
   * The identifier of the voicemail in the content provider.
   *
   * <p>This may be missing in the case of a new {@link Voicemail} that we plan to insert into the
   * content provider, since until it has been inserted we don't know what id it should have. If
   * none is specified, we return -1.
   */
  public long getId() {
    return id;
  }

  /** The number of the person leaving the voicemail, empty string if unknown, null if not set. */
  public String getNumber() {
    return number;
  }

  /** The phone account associated with the voicemail, null if not set. */
  public PhoneAccountHandle getPhoneAccount() {
    return phoneAccount;
  }

  /** The timestamp the voicemail was received, in millis since the epoch, zero if not set. */
  public long getTimestampMillis() {
    return timestamp;
  }

  /** Gets the duration of the voicemail in millis, or zero if the field is not set. */
  public long getDuration() {
    return duration;
  }

  /**
   * Returns the package name of the source that added this voicemail, or null if this field is not
   * set.
   */
  public String getSourcePackage() {
    return source;
  }

  /**
   * Returns the application-specific data type stored with the voicemail, or null if this field is
   * not set.
   *
   * <p>Source data is typically used as an identifier to uniquely identify the voicemail against
   * the voicemail server. This is likely to be something like the IMAP UID, or some other
   * server-generated identifying string.
   */
  public String getSourceData() {
    return providerData;
  }

  /**
   * Gets the Uri that can be used to refer to this voicemail, and to make it play.
   *
   * <p>Returns null if we don't know the Uri.
   */
  public Uri getUri() {
    return uri;
  }

  /**
   * Tells us if the voicemail message has been marked as read.
   *
   * <p>Always returns false if this field has not been set, i.e. if hasRead() returns false.
   */
  public boolean isRead() {
    return isRead;
  }

  /** Tells us if there is content stored at the Uri. */
  public boolean hasContent() {
    return hasContent;
  }

  /** Returns the text transcription of this voicemail, or null if this field is not set. */
  public String getTranscription() {
    return transcription;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeLong(timestamp);
    writeCharSequence(dest, number);
    if (phoneAccount == null) {
      dest.writeInt(0);
    } else {
      dest.writeInt(1);
      phoneAccount.writeToParcel(dest, flags);
    }
    dest.writeLong(id);
    dest.writeLong(duration);
    writeCharSequence(dest, source);
    writeCharSequence(dest, providerData);
    if (uri == null) {
      dest.writeInt(0);
    } else {
      dest.writeInt(1);
      uri.writeToParcel(dest, flags);
    }
    if (isRead) {
      dest.writeInt(1);
    } else {
      dest.writeInt(0);
    }
    if (hasContent) {
      dest.writeInt(1);
    } else {
      dest.writeInt(0);
    }
    writeCharSequence(dest, transcription);
  }

  public static final Creator<Voicemail> CREATOR =
      new Creator<Voicemail>() {
        @Override
        public Voicemail createFromParcel(Parcel in) {
          return new Voicemail(in);
        }

        @Override
        public Voicemail[] newArray(int size) {
          return new Voicemail[size];
        }
      };

  private Voicemail(Parcel in) {
    timestamp = in.readLong();
    number = (String) readCharSequence(in);
    if (in.readInt() > 0) {
      phoneAccount = PhoneAccountHandle.CREATOR.createFromParcel(in);
    } else {
      phoneAccount = null;
    }
    id = in.readLong();
    duration = in.readLong();
    source = (String) readCharSequence(in);
    providerData = (String) readCharSequence(in);
    if (in.readInt() > 0) {
      uri = Uri.CREATOR.createFromParcel(in);
    } else {
      uri = null;
    }
    isRead = in.readInt() > 0 ? true : false;
    hasContent = in.readInt() > 0 ? true : false;
    transcription = (String) readCharSequence(in);
  }

  private static CharSequence readCharSequence(Parcel in) {
    return TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
  }

  public static void writeCharSequence(Parcel dest, CharSequence val) {
    TextUtils.writeToParcel(val, dest, 0);
  }
}
