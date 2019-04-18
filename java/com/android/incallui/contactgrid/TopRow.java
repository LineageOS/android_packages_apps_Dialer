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

package com.android.incallui.contactgrid;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.telephony.PhoneNumberUtils;
import android.text.BidiFormatter;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.incallui.call.state.DialerCallState;
import com.android.incallui.incall.protocol.PrimaryCallState;
import com.android.incallui.incall.protocol.PrimaryInfo;
import com.android.incallui.videotech.utils.SessionModificationState;
import com.android.incallui.videotech.utils.VideoUtils;

/**
 * Gets the content of the top row. For example:
 *
 * <ul>
 *   <li>Captain Holt ON HOLD
 *   <li>Calling...
 *   <li>[Wi-Fi icon] Calling via Starbucks Wi-Fi
 *   <li>[Wi-Fi icon] Starbucks Wi-Fi
 *   <li>Call from
 * </ul>
 */
public class TopRow {

  /** Content of the top row. */
  public static class Info {

    @Nullable public final CharSequence label;
    @Nullable public final Drawable icon;
    public final boolean labelIsSingleLine;

    public Info(@Nullable CharSequence label, @Nullable Drawable icon, boolean labelIsSingleLine) {
      this.label = label;
      this.icon = icon;
      this.labelIsSingleLine = labelIsSingleLine;
    }
  }

  private TopRow() {}

  public static Info getInfo(Context context, PrimaryCallState state, PrimaryInfo primaryInfo) {
    CharSequence label = null;
    Drawable icon = state.connectionIcon();
    boolean labelIsSingleLine = true;

    if (state.isWifi() && icon == null) {
      icon = context.getDrawable(R.drawable.quantum_ic_network_wifi_vd_theme_24);
    }

    if (state.state() == DialerCallState.INCOMING
        || state.state() == DialerCallState.CALL_WAITING) {
      // Call from
      // [Wi-Fi icon] Video call from
      // Hey Jake, pick up!
      if (!TextUtils.isEmpty(state.callSubject())) {
        label = state.callSubject();
        labelIsSingleLine = false;
      } else {
        label = getLabelForIncoming(context, state);
        // Show phone number if it's not displayed in name (center row) or location field (bottom
        // row).
        if (shouldShowNumber(primaryInfo, true /* isIncoming */)) {
          label = TextUtils.concat(label, " ", spanDisplayNumber(primaryInfo.number()));
        }
      }
    } else if (VideoUtils.hasSentVideoUpgradeRequest(state.sessionModificationState())
        || VideoUtils.hasReceivedVideoUpgradeRequest(state.sessionModificationState())) {
      label = getLabelForVideoRequest(context, state);
    } else if (state.sessionModificationState() == SessionModificationState.REQUEST_FAILED) {
      label = context.getString(R.string.incall_video_call_operation_failed);
    } else if (state.state() == DialerCallState.PULLING) {
      label = context.getString(R.string.incall_transferring);
    } else if (state.state() == DialerCallState.DIALING
        || state.state() == DialerCallState.CONNECTING) {
      // [Wi-Fi icon] Calling via Google Guest
      // Calling...
      label = getLabelForDialing(context, state);
    } else if (state.state() == DialerCallState.ACTIVE && state.isRemotelyHeld()) {
      label = context.getString(R.string.incall_remotely_held);
    } else if (state.state() == DialerCallState.ACTIVE
        && shouldShowNumber(primaryInfo, false /* isIncoming */)) {
      label = spanDisplayNumber(primaryInfo.number());
    } else if (state.state() == DialerCallState.CALL_PENDING
        && !TextUtils.isEmpty(state.customLabel())) {
      label = state.customLabel();
    } else {
      // Video calling...
      // [Wi-Fi icon] Starbucks Wi-Fi
      label = getConnectionLabel(state);
    }

    return new Info(label, icon, labelIsSingleLine);
  }

  private static CharSequence spanDisplayNumber(String displayNumber) {
    return PhoneNumberUtils.createTtsSpannable(
        BidiFormatter.getInstance().unicodeWrap(displayNumber, TextDirectionHeuristics.LTR));
  }

  private static boolean shouldShowNumber(PrimaryInfo primaryInfo, boolean isIncoming) {
    if (primaryInfo.nameIsNumber()) {
      return false;
    }
    // Don't show number since it's already shown in bottom row of incoming screen if there is no
    // location info.
    if (primaryInfo.location() == null && isIncoming) {
      return false;
    }
    if (primaryInfo.isLocalContact() && !isIncoming) {
      return false;
    }
    if (TextUtils.isEmpty(primaryInfo.number())) {
      return false;
    }
    return true;
  }

  private static CharSequence getLabelForIncoming(Context context, PrimaryCallState state) {
    if (state.isVideoCall()) {
      return getLabelForIncomingVideo(context, state.sessionModificationState(), state.isWifi());
    } else if (state.isWifi() && !TextUtils.isEmpty(state.connectionLabel())) {
      return state.connectionLabel();
    } else if (isAccount(state)) {
      return getColoredConnectionLabel(context, state);
    } else if (state.isWorkCall()) {
      return context.getString(R.string.contact_grid_incoming_work_call);
    } else {
      return context.getString(R.string.contact_grid_incoming_voice_call);
    }
  }

  private static Spannable getColoredConnectionLabel(Context context, PrimaryCallState state) {
    Assert.isNotNull(state.connectionLabel());
    String label =
        context.getString(R.string.contact_grid_incoming_via_template, state.connectionLabel());
    Spannable spannable = new SpannableString(label);

    int start = label.indexOf(state.connectionLabel());
    int end = start + state.connectionLabel().length();
    spannable.setSpan(
        new ForegroundColorSpan(state.primaryColor()),
        start,
        end,
        Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
    return spannable;
  }

  private static CharSequence getLabelForIncomingVideo(
      Context context, @SessionModificationState int sessionModificationState, boolean isWifi) {
    if (sessionModificationState == SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
      if (isWifi) {
        return context.getString(R.string.contact_grid_incoming_wifi_video_call);
      } else {
        return context.getString(R.string.contact_grid_incoming_video_call);
      }
    } else {
      if (isWifi) {
        return context.getString(R.string.contact_grid_incoming_wifi_video_call);
      } else {
        return context.getString(R.string.contact_grid_incoming_video_call);
      }
    }
  }

  private static CharSequence getLabelForDialing(Context context, PrimaryCallState state) {
    if (!TextUtils.isEmpty(state.connectionLabel()) && !state.isWifi()) {
      CharSequence label = getCallingViaLabel(context, state);

      if (state.isAssistedDialed() && state.assistedDialingExtras() != null) {
        LogUtil.i("TopRow.getLabelForDialing", "using assisted dialing with via label.");
        String countryCode =
            String.valueOf(state.assistedDialingExtras().transformedNumberCountryCallingCode());
        label =
            TextUtils.concat(
                label,
                " • ",
                context.getString(
                    R.string.incall_connecting_assited_dialed_component,
                    countryCode,
                    state.assistedDialingExtras().userHomeCountryCode()));
      }
      return label;
    } else {
      if (state.isVideoCall()) {
        if (state.isWifi()) {
          return context.getString(R.string.incall_wifi_video_call_requesting);
        } else {
          return context.getString(R.string.incall_video_call_requesting);
        }
      }

      if (state.isAssistedDialed() && state.assistedDialingExtras() != null) {
        LogUtil.i("TopRow.getLabelForDialing", "using assisted dialing label.");
        String countryCode =
            String.valueOf(state.assistedDialingExtras().transformedNumberCountryCallingCode());
        return context.getString(
            R.string.incall_connecting_assited_dialed,
            countryCode,
            state.assistedDialingExtras().userHomeCountryCode());
      }
      return context.getString(R.string.incall_connecting);
    }
  }

  private static CharSequence getCallingViaLabel(Context context, PrimaryCallState state) {
    if (state.simSuggestionReason() != null) {
      switch (state.simSuggestionReason()) {
        case FREQUENT:
          return context.getString(
              R.string.incall_calling_on_recent_choice_template, state.connectionLabel());
        case INTRA_CARRIER:
          return context.getString(
              R.string.incall_calling_on_same_carrier_template, state.connectionLabel());
        default:
          break;
      }
    }
    return context.getString(R.string.incall_calling_via_template, state.connectionLabel());
  }

  private static CharSequence getConnectionLabel(PrimaryCallState state) {
    if (!TextUtils.isEmpty(state.connectionLabel())
        && (isAccount(state) || state.isWifi() || state.isConference())) {
      // We normally don't show a "call state label" at all when active
      // (but we can use the call state label to display the provider name).
      return state.connectionLabel();
    } else {
      return null;
    }
  }

  private static CharSequence getLabelForVideoRequest(Context context, PrimaryCallState state) {
    switch (state.sessionModificationState()) {
      case SessionModificationState.WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE:
        return context.getString(R.string.incall_video_call_upgrade_request);
      case SessionModificationState.REQUEST_FAILED:
      case SessionModificationState.UPGRADE_TO_VIDEO_REQUEST_FAILED:
        return context.getString(R.string.incall_video_call_request_failed);
      case SessionModificationState.REQUEST_REJECTED:
        return context.getString(R.string.incall_video_call_request_rejected);
      case SessionModificationState.UPGRADE_TO_VIDEO_REQUEST_TIMED_OUT:
        return context.getString(R.string.incall_video_call_request_timed_out);
      case SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST:
        return getLabelForIncomingVideo(context, state.sessionModificationState(), state.isWifi());
      case SessionModificationState.NO_REQUEST:
      default:
        Assert.fail();
        return null;
    }
  }

  private static boolean isAccount(PrimaryCallState state) {
    return !TextUtils.isEmpty(state.connectionLabel()) && TextUtils.isEmpty(state.gatewayNumber());
  }
}
