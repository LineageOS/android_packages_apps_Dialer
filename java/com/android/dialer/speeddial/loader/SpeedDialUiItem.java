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

import android.content.res.Resources;
import android.database.Cursor;
import android.os.Trace;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.ArraySet;
import com.android.dialer.common.Assert;
import com.android.dialer.glidephotomanager.PhotoInfo;
import com.android.dialer.speeddial.database.SpeedDialEntry;
import com.android.dialer.speeddial.database.SpeedDialEntry.Channel;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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

  private static final String[] PHONE_PROJECTION = {
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

  private static final String[] PHONE_PROJECTION_ALTERNATIVE = {
    Phone.LOOKUP_KEY,
    Phone.CONTACT_ID,
    Phone.DISPLAY_NAME_ALTERNATIVE,
    Phone.STARRED,
    Phone.NUMBER,
    Phone.TYPE,
    Phone.LABEL,
    Phone.PHOTO_ID,
    Phone.PHOTO_URI,
    Phone.CARRIER_PRESENCE
  };

  public static String[] getPhoneProjection(boolean primaryDisplayOrder) {
    return primaryDisplayOrder ? PHONE_PROJECTION : PHONE_PROJECTION_ALTERNATIVE;
  }

  public static Builder builder() {
    return new AutoValue_SpeedDialUiItem.Builder()
        .setChannels(ImmutableList.of())
        .setPinnedPosition(Optional.absent());
  }

  /**
   * Convert a cursor with projection {@link #getPhoneProjection(boolean)} into a {@link
   * SpeedDialUiItem}.
   *
   * <p>This cursor is structured such that contacts are grouped by contact id and lookup key and
   * each row that shares the same contact id and lookup key represents a phone number that belongs
   * to a single contact.
   *
   * <p>If the cursor started at row X, this method will advance to row Y s.t. rows X, X + 1, ... Y
   * - 1 all belong to the same contact (that is, share the same contact id and lookup key).
   */
  public static SpeedDialUiItem fromCursor(
      Resources resources, Cursor cursor, boolean isImsEnabled) {
    Trace.beginSection("fromCursor");
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
    Set<String> numbers = new ArraySet<>();
    do {
      String number = cursor.getString(NUMBER);
      // TODO(78492722): consider using lib phone number to compare numbers
      if (!numbers.add(number)) {
        // Number is identical to an existing number, skip this number
        continue;
      }

      Channel channel =
          Channel.builder()
              .setNumber(number)
              .setPhoneType(cursor.getInt(TYPE))
              .setLabel(getLabel(resources, cursor))
              .setTechnology(Channel.VOICE)
              .build();
      channels.add(channel);

      if (isImsEnabled
          && (cursor.getInt(CARRIER_PRESENCE) & Data.CARRIER_PRESENCE_VT_CAPABLE) == 1) {
        // Add another channel if the number is ViLTE reachable
        channels.add(channel.toBuilder().setTechnology(Channel.IMS_VIDEO).build());
      }
      // TODO(a bug): add another channel for Duo (needs to happen on main thread)
    } while (cursor.moveToNext() && Objects.equals(lookupKey, cursor.getString(LOOKUP_KEY)));

    builder.setChannels(ImmutableList.copyOf(channels));
    Trace.endSection();
    return builder.build();
  }

  private static String getLabel(Resources resources, Cursor cursor) {
    int numberType = cursor.getInt(TYPE);
    String numberLabel = cursor.getString(LABEL);

    // Returns empty label instead of "custom" if the custom label is empty.
    if (numberType == Phone.TYPE_CUSTOM && TextUtils.isEmpty(numberLabel)) {
      return "";
    }
    return (String) Phone.getTypeLabel(resources, numberType, numberLabel);
  }

  public PhotoInfo getPhotoInfo() {
    return PhotoInfo.newBuilder()
        .setPhotoId(photoId())
        .setPhotoUri(photoUri())
        .setName(name())
        .setIsVideo(defaultChannel() != null && defaultChannel().isVideoTechnology())
        .setLookupUri(Contacts.getLookupUri(contactId(), lookupKey()).toString())
        .build();
  }

  public SpeedDialEntry buildSpeedDialEntry() {
    return SpeedDialEntry.builder()
        .setId(speedDialEntryId())
        .setPinnedPosition(pinnedPosition())
        .setLookupKey(lookupKey())
        .setContactId(contactId())
        .setDefaultChannel(defaultChannel())
        .build();
  }

  /**
   * Returns one of the following:
   *
   * <ul>
   *   <li>The default channel if it's a video channel.
   *   <li>A video channel if it has the same attributes as the default channel, OR
   *   <li>null. (This is a deliberate product decision, even if there is only a single video
   *       reachable channel, we should still return null if it has different attributes from those
   *       in the default channel).
   * </ul>
   */
  @Nullable
  public Channel getDefaultVideoChannel() {
    if (defaultChannel() == null) {
      return null;
    }

    if (defaultChannel().isVideoTechnology()) {
      return defaultChannel();
    }

    if (channels().size() == 1) {
      // If there is only a single channel, it can't be a video channel
      return null;
    }

    // At this point, the default channel is a *voice* channel and there are more than
    // one channel in total.
    //
    // Our defined assumptions about the channel list include that if a video channel
    // follows a voice channel, it has the same attributes as that voice channel
    // (see comments on method channels() for details).
    //
    // Therefore, if the default video channel exists, it must be the immediate successor
    // of the default channel in the list.
    //
    // Note that we don't have to check if the last channel in the list is the default
    // channel because even if it is, there will be no video channel under the assumption
    // above.
    for (int i = 0; i < channels().size() - 1; i++) {
      // Find the default channel
      if (Objects.equals(defaultChannel(), channels().get(i))) {
        // Our defined assumptions about the list of channels is that if a video channel follows a
        // voice channel, it has the same attributes as that voice channel.
        Channel channel = channels().get(i + 1);
        if (channel.isVideoTechnology()) {
          return channel;
        }
        // Since the default voice channel isn't video reachable, we can't video call this number
        return null;
      }
    }
    throw Assert.createIllegalStateFailException("channels() doesn't contain defaultChannel().");
  }

  /**
   * Returns a voice channel if there is exactly one channel or the default channel is a voice
   * channel.
   */
  @Nullable
  public Channel getDefaultVoiceChannel() {
    if (channels().size() == 1) {
      // If there is only a single channel, it must be a voice channel as per our defined
      // assumptions (detailed in comments on method channels()).
      return channels().get(0);
    }

    if (defaultChannel() == null) {
      return null;
    }

    if (!defaultChannel().isVideoTechnology()) {
      return defaultChannel();
    }

    // Default channel is a video channel, so find it's corresponding voice channel by number since
    // unreachable channels may not be in the list
    for (Channel currentChannel : channels()) {
      if (currentChannel.number().equals(defaultChannel().number())
          && currentChannel.technology() == Channel.VOICE) {
        return currentChannel;
      }
    }
    return null;
  }

  /**
   * The id of the corresponding SpeedDialEntry. Null if the UI item does not have an entry, for
   * example suggested contacts (isStarred() will also be false)
   *
   * @see SpeedDialEntry#id()
   */
  @Nullable
  public abstract Long speedDialEntryId();

  /** @see SpeedDialEntry#pinnedPosition() */
  public abstract Optional<Integer> pinnedPosition();

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
   * Returns a list of channels available. A Duo channel is included iff it is reachable. Since a
   * contact can have multiple phone numbers and each number can have multiple technologies,
   * enumerate each one here so that the user can choose the correct one. Each channel here
   * represents a row in the {@link com.android.dialer.speeddial.DisambigDialog}.
   *
   * <p>These channels have a few very strictly enforced assumption that are used heavily throughout
   * the codebase. Those assumption are that:
   *
   * <ol>
   *   <li>Each of the contact's numbers are voice reachable. So if a channel has it's technology
   *       set to anything other than {@link Channel#VOICE}, there is gaurenteed to be another
   *       channel with the exact same attributes, but technology will be {@link Channel#VOICE}.
   *   <li>For each of the contact's phone numbers, there will be a voice channel, then the next
   *       channel will either be the same phone number but a video channel, or a new number.
   * </ol>
   *
   * For example: Say a contact has two phone numbers (A & B) and A is duo reachable. Then you can
   * assume the list of channels will be ordered as either {A_voice, A_duo, B_voice} or {B_voice,
   * A_voice, A_duo}.
   *
   * @see com.android.dialer.speeddial.database.SpeedDialEntry.Channel
   */
  public abstract ImmutableList<Channel> channels();

  /**
   * Will be null when the user hasn't chosen a default yet. Note that a default channel may not be
   * in the list returned by {@link #channels()}. This is because that list does not contain an
   * unreachable Duo channel. When the default channel is a Duo channel and it becomes unreachable,
   * it will remain as the default channel but disappear in the list returned by {@link
   * #channels()}.
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

    public abstract Builder setPinnedPosition(Optional<Integer> pinnedPosition);

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
