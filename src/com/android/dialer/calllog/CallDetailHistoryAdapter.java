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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.common.CallUtil;
import com.android.dialer.PhoneCallDetails;
import com.android.dialer.R;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.AppCompatConstants;
import com.android.dialer.util.PresenceHelper;
import com.android.services.callrecorder.common.CallRecording;
import com.android.services.callrecorder.CallRecorderService;
import com.android.services.callrecorder.CallRecordingDataStore;
import com.google.common.collect.Lists;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.codeaurora.ims.utils.QtiImsExtUtils;

/**
 * Adapter for a ListView containing history items from the details of a call.
 */
public class CallDetailHistoryAdapter extends BaseAdapter implements View.OnClickListener {
    /** Each history item shows the detail of a call. */
    private static final int VIEW_TYPE_HISTORY_ITEM = 1;

    private final Context mContext;
    private final LayoutInflater mLayoutInflater;
    private final CallTypeHelper mCallTypeHelper;
    private final PhoneCallDetails[] mPhoneCallDetails;

    private CallRecordingDataStore mCallRecordingDataStore;

    /**
     * List of items to be concatenated together for duration strings.
     */
    private ArrayList<CharSequence> mDurationItems = Lists.newArrayList();

    public CallDetailHistoryAdapter(Context context, LayoutInflater layoutInflater,
            CallTypeHelper callTypeHelper, PhoneCallDetails[] phoneCallDetails,
            CallRecordingDataStore callRecordingDataStore) {
        mContext = context;
        mLayoutInflater = layoutInflater;
        mCallTypeHelper = callTypeHelper;
        mPhoneCallDetails = phoneCallDetails;
        mCallRecordingDataStore = callRecordingDataStore;
    }

    @Override
    public boolean isEnabled(int position) {
        // None of history will be clickable.
        return false;
    }

    @Override
    public int getCount() {
        return mPhoneCallDetails.length;
    }

    @Override
    public Object getItem(int position) {
        return mPhoneCallDetails[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public int getItemViewType(int position) {
        return VIEW_TYPE_HISTORY_ITEM;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Make sure we have a valid convertView to start with
        final View result  = convertView == null
                ? mLayoutInflater.inflate(R.layout.call_detail_history_item, parent, false)
                : convertView;

        PhoneCallDetails details = mPhoneCallDetails[position];
        CallTypeIconsView callTypeIconView =
                (CallTypeIconsView) result.findViewById(R.id.call_type_icon);
        TextView callTypeTextView = (TextView) result.findViewById(R.id.call_type_text);
        TextView dateView = (TextView) result.findViewById(R.id.date);
        TextView durationView = (TextView) result.findViewById(R.id.duration);
        View playbackButton = result.findViewById(R.id.recording_playback_button);

        int callType = details.callTypes[0];
        boolean isPresenceEnabled = mContext.getResources().getBoolean(
                R.bool.config_regional_presence_enable);
        boolean isVideoCall = (details.features & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO;
        if (isPresenceEnabled) {
            isVideoCall &= PresenceHelper.startAvailabilityFetch(details.number.toString());
        }
        Log.d("CallDetailHistoryAdapter", "isVideoCall = " + isVideoCall
                    + ", callType = " + callType);
        callTypeIconView.clear();
        callTypeIconView.add(callType);
        /**
         * Ims icon(VoLTE/VoWifi) or CarrierOne video icon will be shown if carrierOne is supported
         * otherwise, default video icon will be shown if it is a video call.
         */
        if (QtiImsExtUtils.isCarrierOneSupported()) {
             callTypeIconView.addImsOrVideoIcon(callType, isVideoCall);
        } else {
             callTypeIconView.setShowVideo(isVideoCall);
        }
        callTypeTextView.setText(mCallTypeHelper.getCallTypeText(callType, isVideoCall));
        // Set the date.
        CharSequence dateValue = DateUtils.formatDateRange(mContext, details.date, details.date,
                DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE |
                DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_YEAR);
        dateView.setText(dateValue);
        // Set the duration
        boolean callDurationEnabled = mContext.getResources()
                .getBoolean(R.bool.call_duration_enabled);
        if (Calls.VOICEMAIL_TYPE == callType || CallTypeHelper.isMissedCallType(callType) ||
                !callDurationEnabled) {
            durationView.setVisibility(View.GONE);
        } else {
            durationView.setVisibility(View.VISIBLE);
            durationView.setText(formatDurationAndDataUsage(details.duration, details.dataUsage));
        }

        // do this synchronously to prevent recordings from "popping in"
        // after detail item is displayed
        List<CallRecording> recordings = null;
        if (CallRecorderService.isEnabled(mContext)) {
            mCallRecordingDataStore.open(mContext); // opens unless already open
            recordings = mCallRecordingDataStore.getRecordings(
                    details.number.toString(), details.date);
        }
        playbackButton.setTag(recordings);
        playbackButton.setOnClickListener(this);
        playbackButton.setVisibility(recordings != null && !recordings.isEmpty()
                ? View.VISIBLE : View.INVISIBLE);

        return result;
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.recording_playback_button) {
            final List<CallRecording> recordings = (List<CallRecording>) view.getTag();
            if (recordings.size() == 1) {
                launchMediaPlayer(recordings.get(0).getFile());
            } else {
                PopupMenu menu = new PopupMenu(mContext, view);
                SimpleDateFormat format = getTimeWithSecondsFormat();
                for (int i = 0; i < recordings.size(); i++) {
                    final long startTime = recordings.get(i).startRecordingTime;
                    final String formattedDate = format.format(new Date(startTime));
                    menu.getMenu().add(Menu.NONE, i, i, formattedDate);
                }
                menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        CallRecording recording = recordings.get(item.getItemId());
                        launchMediaPlayer(recording.getFile());
                        return true;
                    }
                });
                menu.show();
            }
        }
    }

    private SimpleDateFormat getTimeWithSecondsFormat() {
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(),
                DateFormat.is24HourFormat(mContext) ? "Hmss" : "hmssa");
        return new SimpleDateFormat(pattern);
    }

    private void launchMediaPlayer(File file) {
        Uri uri = Uri.fromFile(file);
        String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        String mime = !TextUtils.isEmpty(extension)
                ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) : "audio/*";
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime);
            mContext.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(mContext,
                    R.string.call_playback_no_app_found_toast,
                    Toast.LENGTH_LONG).show();
        }
    }

    private CharSequence formatDuration(long elapsedSeconds) {
        long minutes = 0;
        long seconds = 0;

        if (elapsedSeconds >= 60) {
            minutes = elapsedSeconds / 60;
            elapsedSeconds -= minutes * 60;
            seconds = elapsedSeconds;
            return mContext.getString(R.string.callDetailsDurationFormat, minutes, seconds);
        } else {
            seconds = elapsedSeconds;
            return mContext.getString(R.string.callDetailsShortDurationFormat, seconds);
        }
    }

    /**
     * Formats a string containing the call duration and the data usage (if specified).
     *
     * @param elapsedSeconds Total elapsed seconds.
     * @param dataUsage Data usage in bytes, or null if not specified.
     * @return String containing call duration and data usage.
     */
    private CharSequence formatDurationAndDataUsage(long elapsedSeconds, Long dataUsage) {
        CharSequence duration = formatDuration(elapsedSeconds);

        if (dataUsage != null) {
            mDurationItems.clear();
            mDurationItems.add(duration);
            mDurationItems.add(Formatter.formatShortFileSize(mContext, dataUsage));

            return DialerUtils.join(mContext.getResources(), mDurationItems);
        } else {
            return duration;
        }
    }
}
