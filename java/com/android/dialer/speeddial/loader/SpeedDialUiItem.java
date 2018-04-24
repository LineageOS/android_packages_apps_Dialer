/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.speeddial.loader;

import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.speeddial.database.SpeedDialEntry;
import com.android.dialer.speeddial.database.SpeedDialEntry.Channel;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * POJO representation of each speed dial list element.
 *
 * <p>Contains all data needed for the UI so that the UI never needs do additional contact queries.
 *
 * <p>Differs from {@link SpeedDialEntry} in that entries are specific to favorited/starred contacts
 * and {@link SpeedDialUiItem}s can be both favorites and suggested contacts.
 */
@AutoValue
public abstract class SpeedDialUiItem {

  public static final int LOOKUP_KEY = 0;
  public static final int CONTACT_ID = 1;
  public static final int DISPLAY_NAME = 2;
  public static final int STARRED = 3;
  public static final int NUMBER = 4;
  public static final int TYPE = 5;
  public static final int LABEL = 6;
  public static final int PHOTO_ID = 7;
  public static final int PHOTO_URI = 8;
  public static final int CARRIER_PRESENCE = 9;

  public static final String[] PHONE_PROJECTION = {
    Phone.LOOKUP_KEY,
    Phone.CONTACT_ID,
    Phone.DISPLAY_NAME,
    Phone.STARRED,
    Phone.NUMBER,
    Phone.TYPE,
    Phone.LABEL,
    Phone.PHOTO_ID,
    Phone.PHOTO_URI,
    Phone.CARRIER_PRESENCE
  };

  public static Builder builder() {
    return new AutoValue_SpeedDialUiItem.Builder().setChannels(ImmutableList.of());
  }

  /**
   * Convert a cursor with projection {@link #PHONE_PROJECTION} into a {@link SpeedDialUiItem}.
   *
   * <p>This cursor is structured such that contacts are grouped by contact id and lookup key and
   * each row that shares the same contact id and lookup key represents a phone number that belongs
   * to a single contact.
   *
   * <p>If the cursor started at row X, this method will advance to row Y s.t. rows X, X + 1, ... Y
   * - 1 all belong to the same contact (that is, share the same contact id and lookup key).
   */
  public static SpeedDialUiItem fromCursor(Cursor cursor) {
    Assert.checkArgument(cursor != null);
    Assert.checkArgument(cursor.getCount() != 0);
    String lookupKey = cursor.getString(LOOKUP_KEY);
    SpeedDialUiItem.Builder builder =
        SpeedDialUiItem.builder()
            .setLookupKey(lookupKey)
            .setContactId(cursor.getLong(CONTACT_ID))
            // TODO(a bug): handle last name first preference
            .setName(cursor.getString(DISPLAY_NAME))
            .setIsStarred(cursor.getInt(STARRED) == 1)
            .setPhotoId(cursor.getLong(PHOTO_ID))
            .setPhotoUri(
                TextUtils.isEmpty(cursor.getString(PHOTO_URI)) ? "" : cursor.getString(PHOTO_URI));

    // While there are more rows and the lookup keys are the same, add a channel for each of the
    // contact's phone numbers.
    List<Channel> channels = new ArrayList<>();
    do {
      Channel channel =
          Channel.builder()
              .setNumber(cursor.getString(NUMBER))
              .setPhoneType(cursor.getInt(TYPE))
              .setLabel(TextUtils.isEmpty(cursor.getString(LABEL)) ? "" : cursor.getString(LABEL))
              .setTechnology(Channel.VOICE)
              .build();
      channels.add(channel);

      if ((cursor.getInt(CARRIER_PRESENCE) & Data.CARRIER_PRESENCE_VT_CAPABLE) == 1) {
        // Add another channel if the number is ViLTE reachable
        channels.add(channel.toBuilder().setTechnology(Channel.IMS_VIDEO).build());
      }
      // TODO(a bug): add another channel for Duo (needs to happen on main thread)
    } while (cursor.moveToNext() && Objects.equals(lookupKey, cursor.getString(LOOKUP_KEY)));

    builder.setChannels(ImmutableList.copyOf(channels));
    return builder.build();
  }

  /**
   * Returns a video channel if there is exactly one video channel or the default channel is a video
   * channel.
   */
  @Nullable
  public Channel getDeterministicVideoChannel() {
    if (defaultChannel() != null && defaultChannel().isVideoTechnology()) {
      return defaultChannel();
    }

    Channel videoChannel = null;
    for (Channel channel : channels()) {
      if (channel.isVideoTechnology()) {
        if (videoChannel != null) {
          // We found two video channels, so we can't determine which one is correct..
          return null;
        }
        videoChannel = channel;
      }
    }
    // Only found one channel, so return it
    return videoChannel;
  }

  /** Returns true if any channels are video channels. */
  public boolean hasVideoChannels() {
    for (Channel channel : channels()) {
      if (channel.isVideoTechnology()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns a voice channel if there is exactly one voice channel or the default channel is a voice
   * channel.
   */
  @Nullable
  public Channel getDeterministicVoiceChannel() {
    if (defaultChannel() != null && !defaultChannel().isVideoTechnology()) {
      return defaultChannel();
    }

    Channel voiceChannel = null;
    for (Channel channel : channels()) {
      if (!channel.isVideoTechnology()) {
        if (voiceChannel != null) {
          // We found two voice channels, so we can't determine which one is correct..
          return null;
        }
        voiceChannel = channel;
      }
    }
    // Only found one channel, so return it
    return voiceChannel;
  }

  /**
   * The id of the corresponding SpeedDialEntry. Null if the UI item does not have an entry, for
   * example suggested contacts (isStarred() will also be false)
   *
   * @see SpeedDialEntry#id()
   */
  @Nullable
  public abstract Long speedDialEntryId();

  /** @see android.provider.ContactsContract.Contacts#DISPLAY_NAME */
  public abstract String name();

  /** @see android.provider.ContactsContract.Contacts#_ID */
  public abstract long contactId();

  /** @see android.provider.ContactsContract.Contacts#LOOKUP_KEY */
  public abstract String lookupKey();

  /** @see android.provider.ContactsContract.Contacts#STARRED */
  public abstract boolean isStarred();

  /** @see Phone#PHOTO_ID */
  public abstract long photoId();

  /** @see Phone#PHOTO_URI */
  public abstract String photoUri();

  /**
   * Since a contact can have multiple phone numbers and each number can have multiple technologies,
   * enumerate each one here so that the user can choose the correct one. Each channel here
   * represents a row in the {@link com.android.dialer.speeddial.DisambigDialog}.
   *
   * @see com.android.dialer.speeddial.database.SpeedDialEntry.Channel
   */
  public abstract ImmutableList<Channel> channels();

  /**
   * Will be null when the user hasn't chosen a default yet.
   *
   * @see com.android.dialer.speeddial.database.SpeedDialEntry#defaultChannel()
   */
  public abstract @Nullable Channel defaultChannel();

  public abstract Builder toBuilder();

  /** Builder class for speed dial contact. */
  @AutoValue.Builder
  public abstract static class Builder {

    /** Set to null if {@link #isStarred()} is false. */
    public abstract Builder setSpeedDialEntryId(@Nullable Long id);

    public abstract Builder setName(String name);

    public abstract Builder setContactId(long contactId);

    public abstract Builder setLookupKey(String lookupKey);

    public abstract Builder setIsStarred(boolean isStarred);

    public abstract Builder setPhotoId(long photoId);

    public abstract Builder setPhotoUri(String photoUri);

    public abstract Builder setChannels(ImmutableList<Channel> channels);

    /** Set to null if the user hasn't chosen a default or the channel no longer exists. */
    public abstract Builder setDefaultChannel(@Nullable Channel defaultChannel);

    public abstract SpeedDialUiItem build();
  }
}
