/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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

package com.android.dialer.app.calllog;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telecom.PhoneAccount;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;

import com.android.dialer.app.R;
import com.android.dialer.app.calllog.calllogcache.CallLogCache;
import com.android.dialer.calllogutils.PhoneCallDetails;
import com.android.dialer.compat.telephony.TelephonyManagerCompat;
import com.android.dialer.logging.ContactSource;
import com.android.dialer.oem.MotorolaUtils;
import com.android.dialer.phonenumbercache.CachedNumberLookupService;
import com.android.dialer.phonenumbercache.PhoneNumberCache;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.theme.base.ThemeComponent;
import com.android.dialer.util.DialerUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/** Helper class to fill in the views in {@link PhoneCallDetailsViews}. */
public class PhoneCallDetailsHelper {
  /** The maximum number of icons will be shown to represent the call types in a group. */
  private static final int MAX_CALL_TYPE_ICONS = 3;

  private final Context context;
  private final Resources resources;
  private final CallLogCache callLogCache;
  /** Calendar used to construct dates */
  private final Calendar calendar;

  private final CachedNumberLookupService cachedNumberLookupService;

  /** List of items to be concatenated together for accessibility descriptions */
  private ArrayList<CharSequence> descriptionItems = new ArrayList<>();

  /**
   * Creates a new instance of the helper.
   *
   * <p>Generally you should have a single instance of this helper in any context.
   *
   * @param resources used to look up strings
   */
  public PhoneCallDetailsHelper(Context context, Resources resources, CallLogCache callLogCache) {
    this.context = context;
    this.resources = resources;
    this.callLogCache = callLogCache;
    calendar = Calendar.getInstance();
    cachedNumberLookupService = PhoneNumberCache.get(context).getCachedNumberLookupService();
  }

  /** Returns true if primary name is empty or the data is from Cequint Caller ID. */
  private boolean shouldShowLocation(PhoneCallDetails details) {
    if (TextUtils.isEmpty(details.geocode)) {
      return false;
    }
    // For caller ID provided by Cequint we want to show the geo location.
    if (details.sourceType == ContactSource.Type.SOURCE_TYPE_CEQUINT_CALLER_ID) {
      return true;
    }
    if (cachedNumberLookupService != null
        && cachedNumberLookupService.isBusiness(details.sourceType)) {
      return true;
    }

    // Don't bother showing geo location for contacts.
    if (!TextUtils.isEmpty(details.namePrimary)) {
      return false;
    }
    return true;
  }

  /** Fills the call details views with content. */
  public void setPhoneCallDetails(PhoneCallDetailsViews views, PhoneCallDetails details) {
    // Display up to a given number of icons.
    views.callTypeIcons.clear();
    int count = details.callTypes.length;
    boolean isVoicemail = false;
    for (int index = 0; index < count && index < MAX_CALL_TYPE_ICONS; ++index) {
      views.callTypeIcons.add(details.callTypes[index]);
      if (index == 0) {
        isVoicemail = details.callTypes[index] == Calls.VOICEMAIL_TYPE;
      }
    }

    // Show the video icon if the call had video enabled.
    views.callTypeIcons.setShowVideo(
        (details.features & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO);
    views.callTypeIcons.setShowHd(
        (details.features & Calls.FEATURES_HD_CALL) == Calls.FEATURES_HD_CALL);
    views.callTypeIcons.setShowWifi(
        MotorolaUtils.shouldShowWifiIconInCallLog(context, details.features));
    views.callTypeIcons.setShowAssistedDialed(
        (details.features & TelephonyManagerCompat.FEATURES_ASSISTED_DIALING)
            == TelephonyManagerCompat.FEATURES_ASSISTED_DIALING);
    views.callTypeIcons.setShowRtt((details.features & Calls.FEATURES_RTT) == Calls.FEATURES_RTT);
    views.callTypeIcons.requestLayout();
    views.callTypeIcons.setVisibility(View.VISIBLE);

    // Show the total call count only if there are more than the maximum number of icons.
    final Integer callCount;
    if (count > MAX_CALL_TYPE_ICONS) {
      callCount = count;
    } else {
      callCount = null;
    }

    // Set the call count, location, date and if voicemail, set the duration.
    setDetailText(views, callCount, details);

    // Set the account label if it exists.
    String accountLabel = callLogCache.getAccountLabel(details.accountHandle);
    if (!TextUtils.isEmpty(details.viaNumber)) {
      if (!TextUtils.isEmpty(accountLabel)) {
        accountLabel =
            resources.getString(
                R.string.call_log_via_number_phone_account, accountLabel, details.viaNumber);
      } else {
        accountLabel = resources.getString(R.string.call_log_via_number, details.viaNumber);
      }
    }
    if (!TextUtils.isEmpty(accountLabel)) {
      views.callAccountLabel.setVisibility(View.VISIBLE);
      views.callAccountLabel.setText(accountLabel);
      int color = callLogCache.getAccountColor(details.accountHandle);
      if (color == PhoneAccount.NO_HIGHLIGHT_COLOR) {
        int defaultColor = R.color.dialer_secondary_text_color;
        views.callAccountLabel.setTextColor(context.getResources().getColor(defaultColor));
      } else {
        views.callAccountLabel.setTextColor(color);
      }
    } else {
      views.callAccountLabel.setVisibility(View.GONE);
    }

    setNameView(views, details);

    // Bold if not read
    Typeface typeface = details.isRead ? Typeface.SANS_SERIF : Typeface.DEFAULT_BOLD;
    views.nameView.setTypeface(typeface);
    views.callLocationAndDate.setTypeface(typeface);
    views.callLocationAndDate.setTextColor(
        details.isRead
            ? ThemeComponent.get(context).theme().getTextColorSecondary()
            : ThemeComponent.get(context).theme().getTextColorPrimary());
  }

  private void setNameView(PhoneCallDetailsViews views, PhoneCallDetails details) {
    if (!TextUtils.isEmpty(details.getPreferredName())) {
      views.nameView.setText(details.getPreferredName());
      // "nameView" is updated from phone number to contact name after number matching.
      // Since TextDirection remains at View.TEXT_DIRECTION_LTR, initialize it.
      views.nameView.setTextDirection(View.TEXT_DIRECTION_INHERIT);
      return;
    }

    if (PhoneNumberUtils.isEmergencyNumber(details.displayNumber)) {
      views.nameView.setText(R.string.emergency_number);
      views.nameView.setTextDirection(View.TEXT_DIRECTION_INHERIT);
      return;
    }

    views.nameView.setText(details.displayNumber);
    // We have a real phone number as "nameView" so make it always LTR
    views.nameView.setTextDirection(View.TEXT_DIRECTION_LTR);
  }

  /**
   * Builds a string containing the call location and date. For voicemail logs only the call date is
   * returned because location information is displayed in the call action button
   *
   * @param details The call details.
   * @return The call location and date string.
   */
  public CharSequence getCallLocationAndDate(PhoneCallDetails details) {
    descriptionItems.clear();

    if (details.callTypes[0] != Calls.VOICEMAIL_TYPE) {
      // Get type of call (ie mobile, home, etc) if known, or the caller's location.
      CharSequence callTypeOrLocation = getCallTypeOrLocation(details);

      // Only add the call type or location if its not empty.  It will be empty for unknown
      // callers.
      if (!TextUtils.isEmpty(callTypeOrLocation)) {
        descriptionItems.add(callTypeOrLocation);
      }
    }

    // The date of this call
    descriptionItems.add(getCallDate(details));

    // Create a comma separated list from the call type or location, and call date.
    return DialerUtils.join(descriptionItems);
  }

  /**
   * For a call, if there is an associated contact for the caller, return the known call type (e.g.
   * mobile, home, work). If there is no associated contact, attempt to use the caller's location if
   * known.
   *
   * @param details Call details to use.
   * @return Type of call (mobile/home) if known, or the location of the caller (if known).
   */
  public CharSequence getCallTypeOrLocation(PhoneCallDetails details) {
    if (details.isSpam) {
      return resources.getString(R.string.spam_number_call_log_label);
    } else if (details.isBlocked) {
      return resources.getString(R.string.blocked_number_call_log_label);
    }

    CharSequence numberFormattedLabel = null;
    // Only show a label if the number is shown and it is not a SIP address.
    if (!TextUtils.isEmpty(details.number)
        && !PhoneNumberHelper.isUriNumber(details.number.toString())
        && !callLogCache.isVoicemailNumber(details.accountHandle, details.number)) {

      if (shouldShowLocation(details)) {
        numberFormattedLabel = details.geocode;
      } else if (!(details.numberType == Phone.TYPE_CUSTOM
          && TextUtils.isEmpty(details.numberLabel))) {
        // Get type label only if it will not be "Custom" because of an empty number label.
        numberFormattedLabel =
                Phone.getTypeLabel(resources, details.numberType, details.numberLabel);
      }
    }
    if (!TextUtils.isEmpty(details.namePrimary) && TextUtils.isEmpty(numberFormattedLabel)) {
      numberFormattedLabel = details.displayNumber;
    }
    return numberFormattedLabel;
  }

  /**
   * Get the call date/time of the call. For the call log this is relative to the current time. e.g.
   * 3 minutes ago. For voicemail, see {@link #getGranularDateTime(PhoneCallDetails)}
   *
   * @param details Call details to use.
   * @return String representing when the call occurred.
   */
  public CharSequence getCallDate(PhoneCallDetails details) {
    if (details.callTypes[0] == Calls.VOICEMAIL_TYPE) {
      return getGranularDateTime(details);
    }

    return DateUtils.getRelativeTimeSpanString(
        details.date,
        getCurrentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE);
  }

  /**
   * Get the granular version of the call date/time of the call. The result is always in the form
   * 'DATE at TIME'. The date value changes based on when the call was created.
   *
   * <p>If created today, DATE is 'Today' If created this year, DATE is 'MMM dd' Otherwise, DATE is
   * 'MMM dd, yyyy'
   *
   * <p>TIME is the localized time format, e.g. 'hh:mm a' or 'HH:mm'
   *
   * @param details Call details to use
   * @return String representing when the call occurred
   */
  public CharSequence getGranularDateTime(PhoneCallDetails details) {
    return resources.getString(
        R.string.voicemailCallLogDateTimeFormat,
        getGranularDate(details.date),
        DateUtils.formatDateTime(context, details.date, DateUtils.FORMAT_SHOW_TIME));
  }

  /**
   * Get the granular version of the call date. See {@link #getGranularDateTime(PhoneCallDetails)}
   */
  private String getGranularDate(long date) {
    if (DateUtils.isToday(date)) {
      return resources.getString(R.string.voicemailCallLogToday);
    }
    return DateUtils.formatDateTime(
        context,
        date,
        DateUtils.FORMAT_SHOW_DATE
            | DateUtils.FORMAT_ABBREV_MONTH
            | (shouldShowYear(date) ? DateUtils.FORMAT_SHOW_YEAR : DateUtils.FORMAT_NO_YEAR));
  }

  /**
   * Determines whether the year should be shown for the given date
   *
   * @return {@code true} if date is within the current year, {@code false} otherwise
   */
  private boolean shouldShowYear(long date) {
    calendar.setTimeInMillis(getCurrentTimeMillis());
    int currentYear = calendar.get(Calendar.YEAR);
    calendar.setTimeInMillis(date);
    return currentYear != calendar.get(Calendar.YEAR);
  }

  /**
   * Returns the current time in milliseconds since the epoch.
   */
  private long getCurrentTimeMillis() {
    return System.currentTimeMillis();
  }

  /** Sets the call count, date, and if it is a voicemail, sets the duration. */
  private void setDetailText(
      PhoneCallDetailsViews views, Integer callCount, PhoneCallDetails details) {
    // Combine the count (if present) and the date.
    CharSequence dateText = details.callLocationAndDate;
    final CharSequence text;
    if (callCount != null) {
      text = resources.getString(R.string.call_log_item_count_and_date, callCount, dateText);
    } else {
      text = dateText;
    }

    if (details.callTypes[0] == Calls.VOICEMAIL_TYPE && details.duration > 0) {
      views.callLocationAndDate.setText(
          resources.getString(
              R.string.voicemailCallLogDateTimeFormatWithDuration,
              text,
              getVoicemailDuration(details)));
    } else {
      views.callLocationAndDate.setText(text);
    }
  }

  private String getVoicemailDuration(PhoneCallDetails details) {
    long minutes = TimeUnit.SECONDS.toMinutes(details.duration);
    long seconds = details.duration - TimeUnit.MINUTES.toSeconds(minutes);
    if (minutes > 99) {
      minutes = 99;
    }
    return resources.getString(R.string.voicemailDurationFormat, minutes, seconds);
  }
}
