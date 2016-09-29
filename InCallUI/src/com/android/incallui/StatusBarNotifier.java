/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.incallui;

import static com.android.contacts.common.compat.CallSdkCompat.Details.PROPERTY_ENTERPRISE_CALL;
import static com.android.incallui.NotificationBroadcastReceiver.ACTION_ACCEPT_VIDEO_UPGRADE_REQUEST;
import static com.android.incallui.NotificationBroadcastReceiver.ACTION_ANSWER_VIDEO_INCOMING_CALL;
import static com.android.incallui.NotificationBroadcastReceiver.ACTION_ANSWER_VOICE_INCOMING_CALL;
import static com.android.incallui.NotificationBroadcastReceiver.ACTION_DECLINE_INCOMING_CALL;
import static com.android.incallui.NotificationBroadcastReceiver.ACTION_DECLINE_VIDEO_UPGRADE_REQUEST;
import static com.android.incallui.NotificationBroadcastReceiver.ACTION_HANG_UP_ONGOING_CALL;

import com.google.common.base.Preconditions;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioAttributes;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.support.annotation.Nullable;
import android.os.SystemClock;
import android.telecom.Call.Details;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;

import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.ContactsUtils.UserType;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.contacts.common.testing.NeededForTesting;
import com.android.contacts.common.util.BitmapUtil;
import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.dialer.R;
import com.android.incallui.ContactInfoCache.ContactCacheEntry;
import com.android.incallui.ContactInfoCache.ContactInfoCacheCallback;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.async.PausableExecutorImpl;
import com.android.incallui.ringtone.DialerRingtoneManager;
import com.android.incallui.ringtone.InCallTonePlayer;
import com.android.incallui.ringtone.ToneGeneratorFactory;

import java.util.Objects;
import org.codeaurora.ims.QtiCallConstants;

/**
 * This class adds Notifications to the status bar for the in-call experience.
 */
public class StatusBarNotifier implements InCallPresenter.InCallStateListener,
        CallList.CallUpdateListener {

    // Notification types
    // Indicates that no notification is currently showing.
    private static final int NOTIFICATION_NONE = 0;
    // Notification for an active call. This is non-interruptive, but cannot be dismissed.
    private static final int NOTIFICATION_IN_CALL = 1;
    // Notification for incoming calls. This is interruptive and will show up as a HUN.
    private static final int NOTIFICATION_INCOMING_CALL = 2;
    //If voice privacy is on this property will be added to the call associated with the connection.
    private static final int CAPABILITY_VOICE_PRIVACY = 0x01000000;

    private static final long[] VIBRATE_PATTERN = new long[] {0, 1000, 1000};

    private final Context mContext;
    @Nullable private ContactsPreferences mContactsPreferences;
    private final ContactInfoCache mContactInfoCache;
    private final NotificationManager mNotificationManager;
    private final TelephonyManager mTelephonyManager;
    private final DialerRingtoneManager mDialerRingtoneManager;
    private int mCurrentNotification = NOTIFICATION_NONE;
    private int mCallState = Call.State.INVALID;
    private int mVideoState = VideoProfile.STATE_AUDIO_ONLY;
    private int mSavedIcon = 0;
    private String mSavedContent = null;
    private Bitmap mSavedLargeIcon;
    private String mSavedContentTitle;
    private String mCallId = null;
    private InCallState mInCallState;
    private Uri mRingtone;
    private static final String EXTRA_KEY_SHOW = "showCallStatusBar";
    private static final String EXTRA_KEY_CALL_STATE = "callState";
    private static final String EXTRA_KEY_CHRONOMETER_TIME = "baseChronometerMillis";
    private static final String EXTRA_KEY_AUDIO_MODE = "audioMode";
    private static final String EXTRA_KEY_MUTE = "mute";

    public StatusBarNotifier(Context context, ContactInfoCache contactInfoCache) {
        Preconditions.checkNotNull(context);
        mContext = context;
        mContactsPreferences = ContactsPreferencesFactory.newContactsPreferences(mContext);
        mContactInfoCache = contactInfoCache;
        mTelephonyManager =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mNotificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mDialerRingtoneManager = new DialerRingtoneManager(
                new InCallTonePlayer(new ToneGeneratorFactory(), new PausableExecutorImpl()),
                CallList.getInstance());
        mCurrentNotification = NOTIFICATION_NONE;
    }

    /**
     * Creates notifications according to the state we receive from {@link InCallPresenter}.
     */
    @Override
    public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
        Log.d(this, "onStateChange");
        mInCallState = newState;
        updateNotification(newState, callList);
    }

    private int getVoWiFiQualityIcon(int voWifiCallQuality) {
        switch (voWifiCallQuality) {
            case QtiCallConstants.VOWIFI_QUALITY_EXCELLENT:
                return R.drawable.vowifi_in_call_good;

            case QtiCallConstants.VOWIFI_QUALITY_FAIR:
                return R.drawable.vowifi_in_call_fair;

            case QtiCallConstants.VOWIFI_QUALITY_POOR:
                return R.drawable.vowifi_in_call_poor;
        }
       return QtiCallConstants.VOWIFI_QUALITY_NONE;
    }

    private String getVoWiFiQualityText(int voWifiCallQuality) {
        switch (voWifiCallQuality) {
            case QtiCallConstants.VOWIFI_QUALITY_EXCELLENT:
                return mContext.getResources().getString(R.string.vowifi_call_quality_good);

            case QtiCallConstants.VOWIFI_QUALITY_FAIR:
                return mContext.getResources().getString(R.string.vowifi_call_quality_fair);

            case QtiCallConstants.VOWIFI_QUALITY_POOR:
                return mContext.getResources().getString(R.string.vowifi_call_quality_poor);
        }
      return null;
    }

    /**
     * Updates the phone app's status bar notification *and* launches the
     * incoming call UI in response to a new incoming call.
     *
     * If an incoming call is ringing (or call-waiting), the notification
     * will also include a "fullScreenIntent" that will cause the
     * InCallScreen to be launched, unless the current foreground activity
     * is marked as "immersive".
     *
     * (This is the mechanism that actually brings up the incoming call UI
     * when we receive a "new ringing connection" event from the telephony
     * layer.)
     *
     * Also note that this method is safe to call even if the phone isn't
     * actually ringing (or, more likely, if an incoming call *was*
     * ringing briefly but then disconnected).  In that case, we'll simply
     * update or cancel the in-call notification based on the current
     * phone state.
     *
     * @see #updateInCallNotification(InCallState,CallList)
     */
    public void updateNotification(InCallState state, CallList callList) {
        updateInCallNotification(state, callList);
    }

    /**
     * Take down the in-call notification.
     * @see #updateInCallNotification(InCallState,CallList)
     */
    private void cancelNotification() {
        if (!TextUtils.isEmpty(mCallId)) {
            CallList.getInstance().removeCallUpdateListener(mCallId, this);
            mCallId = null;
        }
        if (mCurrentNotification != NOTIFICATION_NONE) {
            Log.d(this, "cancelInCall()...");
            mNotificationManager.cancel(mCurrentNotification);
        }
        mCurrentNotification = NOTIFICATION_NONE;
    }

    /**
     * Should only be called from a irrecoverable state where it is necessary to dismiss all
     * notifications.
     */
    static void clearAllCallNotifications(Context backupContext) {
        Log.i(StatusBarNotifier.class.getSimpleName(),
                "Something terrible happened. Clear all InCall notifications");

        NotificationManager notificationManager =
                (NotificationManager) backupContext.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_IN_CALL);
        notificationManager.cancel(NOTIFICATION_INCOMING_CALL);
    }

    /**
     * Helper method for updateInCallNotification() and
     * updateNotification(): Update the phone app's
     * status bar notification based on the current telephony state, or
     * cancels the notification if the phone is totally idle.
     */
    private void updateInCallNotification(final InCallState state, CallList callList) {
        Log.d(this, "updateInCallNotification...");

        final Call call = getCallToShow(callList);

        if (call != null) {
            showNotification(call);
        } else {
            cancelNotification();
        }
        if (!state.isIncoming()) {
            updateCallStatusBar(call);
        }
    }

    public void updateCallStatusBar() {
        CallList callList = InCallPresenter.getInstance().getCallList();
        updateCallStatusBar(callList);
    }

    public void updateCallStatusBar(CallList callList) {
        updateCallStatusBar(getCallToShow(callList));
    }

    private void updateCallStatusBar(Call call) {
        Intent intent = new Intent(
                "com.android.incallui.UPDATE_CALL_STATUS_BAR");
        boolean isShowingInCallUi = InCallPresenter.getInstance().isShowingInCallUi();
        boolean show = (call != null) && !isShowingInCallUi;
        intent.putExtra(EXTRA_KEY_SHOW, show);
        if (call != null) {
            int state = call.getState();
            intent.putExtra(EXTRA_KEY_CALL_STATE, state);
            if (state == Call.State.ACTIVE) {
                long chronometerTime = call.getConnectTimeMillis() - System.currentTimeMillis()
                        + SystemClock.elapsedRealtime();
                intent.putExtra(EXTRA_KEY_CHRONOMETER_TIME, chronometerTime);
            }
            AudioModeProvider audioModeProvider = AudioModeProvider.getInstance();
            intent.putExtra(EXTRA_KEY_AUDIO_MODE, audioModeProvider.getAudioMode());
            intent.putExtra(EXTRA_KEY_MUTE, audioModeProvider.getMute());
        }
        mContext.sendBroadcast(intent);
    }

    private void showNotification(final Call call) {
        final boolean isGeocoderLocationNeeded = (call.getState() == Call.State.INCOMING ||
                call.getState() == Call.State.CALL_WAITING ||
                call.getState() == Call.State.DIALING ||
                call.getState() == Call.State.CONNECTING ||
                call.getState() == Call.State.SELECT_PHONE_ACCOUNT);
        Log.d(this, "showNotification isGeocoderLocationNeeded = " + isGeocoderLocationNeeded);
        if (!TextUtils.isEmpty(mCallId)) {
            CallList.getInstance().removeCallUpdateListener(mCallId, this);
        }
        mCallId = call.getId();
        CallList.getInstance().addCallUpdateListener(call.getId(), this);

        // we make a call to the contact info cache to query for supplemental data to what the
        // call provides.  This includes the contact name and photo.
        // This callback will always get called immediately and synchronously with whatever data
        // it has available, and may make a subsequent call later (same thread) if it had to
        // call into the contacts provider for more data.
        mContactInfoCache.findInfo(call, isGeocoderLocationNeeded, new ContactInfoCacheCallback() {
            @Override
            public void onContactInfoComplete(String callId, ContactCacheEntry entry) {
                Call call = CallList.getInstance().getCallById(callId);
                if (call != null) {
                    call.getLogState().contactLookupResult = entry.contactLookupResult;
                    buildAndSendNotification(call, entry);
                }
            }

            @Override
            public void onImageLoadComplete(String callId, ContactCacheEntry entry) {
                Call call = CallList.getInstance().getCallById(callId);
                if (call != null) {
                    buildAndSendNotification(call, entry);
                }
            }

            @Override
            public void onContactInteractionsInfoComplete(String callId, ContactCacheEntry entry) {}
        });
    }

    /**
     * Sets up the main Ui for the notification
     */
    private void buildAndSendNotification(Call originalCall, ContactCacheEntry contactInfo) {
        // This can get called to update an existing notification after contact information has come
        // back. However, it can happen much later. Before we continue, we need to make sure that
        // the call being passed in is still the one we want to show in the notification.
        final Call call = getCallToShow(CallList.getInstance());
        if (call == null || !call.getId().equals(originalCall.getId())) {
            return;
        }

        final int callState = call.getState();

        // Check if data has changed; if nothing is different, don't issue another notification.
        final int iconResId;
        Bitmap largeIcon = getLargeIconToDisplay(contactInfo, call);
        String content =
                getContentString(call, contactInfo.userType);
        int wifiQualityValue = call.getWifiQuality();
        if (wifiQualityValue != QtiCallConstants.VOWIFI_QUALITY_NONE) {
            iconResId = getVoWiFiQualityIcon(wifiQualityValue);
            content += " " + getVoWiFiQualityText(wifiQualityValue);
        } else {
            iconResId = getIconToDisplay(call);
        }
        final String contentTitle = getContentTitle(contactInfo, call);

        final boolean isVideoUpgradeRequest = call.getSessionModificationState()
                == Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST;
        final Call pendingAccountSelectionCall = CallList.getInstance()
                .getWaitingForAccountCall();
        final int notificationType;
        if ((callState == Call.State.INCOMING || callState == Call.State.CALL_WAITING
                || isVideoUpgradeRequest) && (!InCallPresenter.getInstance().isShowingInCallUi()
                || pendingAccountSelectionCall != null)) {
            notificationType = NOTIFICATION_INCOMING_CALL;
        } else {
            notificationType = NOTIFICATION_IN_CALL;
        }

        if (!checkForChangeAndSaveData(iconResId, content, largeIcon, contentTitle, callState,
                call.getVideoState(), notificationType, contactInfo.contactRingtoneUri)) {
            return;
        }

        if (largeIcon != null) {
            largeIcon = getRoundedIcon(largeIcon);
        }

        //set the content
        boolean isMultiSimDevice = mTelephonyManager.isMultiSimEnabled();
        if (isMultiSimDevice) {
            SubscriptionInfo info =
                    SubscriptionManager.from(mContext).getActiveSubscriptionInfo(call.getSubId());
            if (info != null) {
                content += " (" + info.getDisplayName() + ")";
            }
        }

        /*
         * This builder is used for the notification shown when the device is locked and the user
         * has set their notification settings to 'hide sensitive content'
         * {@see Notification.Builder#setPublicVersion}.
         */
        Notification.Builder publicBuilder = new Notification.Builder(mContext);
        publicBuilder.setSmallIcon(iconResId)
                .setColor(mContext.getResources().getColor(R.color.dialer_theme_color))
                // Hide work call state for the lock screen notification
                .setContentTitle(getContentString(call, ContactsUtils.USER_TYPE_CURRENT));
        setNotificationWhen(call, callState, publicBuilder);

        /*
         * Builder for the notification shown when the device is unlocked or the user has set their
         * notification settings to 'show all notification content'.
         */
        final Notification.Builder builder = getNotificationBuilder();
        builder.setPublicVersion(publicBuilder.build());

        // Set up the main intent to send the user to the in-call screen
        final PendingIntent inCallPendingIntent = createLaunchPendingIntent();
        builder.setContentIntent(inCallPendingIntent);

        // Set the intent as a full screen intent as well if a call is incoming
        if (notificationType == NOTIFICATION_INCOMING_CALL
                && (!InCallPresenter.getInstance().isShowingInCallUi() ||
                   (pendingAccountSelectionCall != null))) {
            configureFullScreenIntent(builder, inCallPendingIntent, call);
            // Set the notification category for incoming calls
            builder.setCategory(Notification.CATEGORY_CALL);
        }

        // Set the content
        builder.setContentText(content);
        builder.setSmallIcon(iconResId);
        builder.setContentTitle(contentTitle);
        builder.setLargeIcon(largeIcon);
        builder.setColor(mContext.getResources().getColor(R.color.dialer_theme_color));

        if (isVideoUpgradeRequest) {
            builder.setUsesChronometer(false);
            addDismissUpgradeRequestAction(builder);
            addAcceptUpgradeRequestAction(builder);
        } else {
            createIncomingCallNotification(call, callState, builder);
        }

        addPersonReference(builder, contactInfo, call);

        /*
         * Fire off the notification
         */
        Notification notification = builder.build();

        if (mDialerRingtoneManager.shouldPlayRingtone(callState, contactInfo.contactRingtoneUri)) {
            notification.flags |= Notification.FLAG_INSISTENT;
            notification.sound = contactInfo.contactRingtoneUri;
            AudioAttributes.Builder audioAttributes = new AudioAttributes.Builder();
            audioAttributes.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC);
            audioAttributes.setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE);
            notification.audioAttributes = audioAttributes.build();
            if (mDialerRingtoneManager.shouldVibrate(mContext.getContentResolver())) {
                notification.vibrate = VIBRATE_PATTERN;
            }
        }
        if (mDialerRingtoneManager.shouldPlayCallWaitingTone(callState)) {
            Log.v(this, "Playing call waiting tone");
            mDialerRingtoneManager.playCallWaitingTone();
        }
        if (mCurrentNotification != notificationType && mCurrentNotification != NOTIFICATION_NONE) {
            Log.i(this, "Previous notification already showing - cancelling "
                    + mCurrentNotification);
            mNotificationManager.cancel(mCurrentNotification);
        }
        Log.i(this, "Displaying notification for " + notificationType);
        mNotificationManager.notify(notificationType, notification);
        mCurrentNotification = notificationType;
    }

    private void createIncomingCallNotification(
            Call call, int state, Notification.Builder builder) {
        setNotificationWhen(call, state, builder);

        // Add hang up option for any active calls (active | onhold), outgoing calls (dialing).
        if (state == Call.State.ACTIVE ||
                state == Call.State.ONHOLD ||
                Call.State.isDialing(state)) {
            addHangupAction(builder);
        } else if (state == Call.State.INCOMING || state == Call.State.CALL_WAITING) {
            addDismissAction(builder);
            if (call.isVideoCall(mContext)) {
                addVoiceAction(builder);
                addVideoCallAction(builder, call.getVideoState());
            } else {
                addAnswerAction(builder);
            }
        }
    }

    /*
     * Sets the notification's when section as needed. For active calls, this is explicitly set as
     * the duration of the call. For all other states, the notification will automatically show the
     * time at which the notification was created.
     */
    private void setNotificationWhen(Call call, int state, Notification.Builder builder) {
        if (state == Call.State.ACTIVE) {
            builder.setUsesChronometer(true);
            builder.setWhen(call.getConnectTimeMillis());
        } else {
            builder.setUsesChronometer(false);
        }
    }

    /**
     * Checks the new notification data and compares it against any notification that we
     * are already displaying. If the data is exactly the same, we return false so that
     * we do not issue a new notification for the exact same data.
     */
    private boolean checkForChangeAndSaveData(int icon, String content, Bitmap largeIcon,
            String contentTitle, int state, int videoState, int notificationType, Uri ringtone) {

        // The two are different:
        // if new title is not null, it should be different from saved version OR
        // if new title is null, the saved version should not be null
        final boolean contentTitleChanged =
                (contentTitle != null && !contentTitle.equals(mSavedContentTitle)) ||
                (contentTitle == null && mSavedContentTitle != null);

        // any change means we are definitely updating
        boolean retval = (mSavedIcon != icon) || !Objects.equals(mSavedContent, content)
                || (mCallState != state) || (mVideoState != videoState)
                || (mSavedLargeIcon != largeIcon) || contentTitleChanged
                || !Objects.equals(mRingtone, ringtone);

        // If we aren't showing a notification right now or the notification type is changing,
        // definitely do an update.
        if (mCurrentNotification != notificationType) {
            if (mCurrentNotification == NOTIFICATION_NONE) {
                Log.d(this, "Showing notification for first time.");
            }
            retval = true;
        }

        mSavedIcon = icon;
        mSavedContent = content;
        mCallState = state;
        mVideoState = videoState;
        mSavedLargeIcon = largeIcon;
        mSavedContentTitle = contentTitle;
        mRingtone = ringtone;

        if (retval) {
            Log.d(this, "Data changed.  Showing notification");
        }

        return retval;
    }

    /**
     * Returns the main string to use in the notification.
     */
    @NeededForTesting
    String getContentTitle(ContactCacheEntry contactInfo, Call call) {
        if (call.isConferenceCall() || call.hasProperty(Details.PROPERTY_GENERIC_CONFERENCE)) {
            return mContext.getResources().getString(R.string.card_title_conf_call);
        }
        if (TextUtils.isEmpty(contactInfo.namePrimary)) {
            String contactNumberDisplayed = TextUtils.isEmpty(contactInfo.number) ?
                    "" : contactInfo.number.toString();
            String location_info = GeoUtil.getGeocodedLocationFor(mContext, contactNumberDisplayed);
            if (!TextUtils.isEmpty(location_info)){
                contactNumberDisplayed =  contactNumberDisplayed + " " + location_info;
            }
            return TextUtils.isEmpty(contactNumberDisplayed) ? null
                    : BidiFormatter.getInstance().unicodeWrap(
                    contactNumberDisplayed, TextDirectionHeuristics.LTR);
        }

        return contactInfo.namePrimary;
    }

    private void addPersonReference(Notification.Builder builder, ContactCacheEntry contactInfo,
            Call call) {
        // Query {@link Contacts#CONTENT_LOOKUP_URI} directly with work lookup key is not allowed.
        // So, do not pass {@link Contacts#CONTENT_LOOKUP_URI} to NotificationManager to avoid
        // NotificationManager using it.
        if (contactInfo.lookupUri != null && contactInfo.userType != ContactsUtils.USER_TYPE_WORK) {
            builder.addPerson(contactInfo.lookupUri.toString());
        } else if (!TextUtils.isEmpty(call.getNumber())) {
            builder.addPerson(Uri.fromParts(PhoneAccount.SCHEME_TEL,
                    call.getNumber(), null).toString());
        }
    }

    /**
     * Gets a large icon from the contact info object to display in the notification.
     */
    private Bitmap getLargeIconToDisplay(ContactCacheEntry contactInfo, Call call) {
        Bitmap largeIcon = null;
        if (call.isConferenceCall() && !call.hasProperty(Details.PROPERTY_GENERIC_CONFERENCE)) {
            largeIcon = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.img_conference);
        }
        if (contactInfo.photo != null && (contactInfo.photo instanceof BitmapDrawable)) {
            largeIcon = ((BitmapDrawable) contactInfo.photo).getBitmap();
        }
        return largeIcon;
    }

    private Bitmap getRoundedIcon(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        final int height = (int) mContext.getResources().getDimension(
                android.R.dimen.notification_large_icon_height);
        final int width = (int) mContext.getResources().getDimension(
                android.R.dimen.notification_large_icon_width);
        return BitmapUtil.getRoundedBitmap(bitmap, width, height);
    }

    /**
     * Returns the appropriate icon res Id to display based on the call for which
     * we want to display information.
     */
    private int getIconToDisplay(Call call) {
        // Even if both lines are in use, we only show a single item in
        // the expanded Notifications UI.  It's labeled "Ongoing call"
        // (or "On hold" if there's only one call, and it's on hold.)
        // Also, we don't have room to display caller-id info from two
        // different calls.  So if both lines are in use, display info
        // from the foreground call.  And if there's a ringing call,
        // display that regardless of the state of the other calls.
        int resId;
        boolean supportsVoicePrivacy = call.can(CAPABILITY_VOICE_PRIVACY);
        if (call.getState() == Call.State.ONHOLD) {
            if (supportsVoicePrivacy) {
                resId = R.drawable.stat_sys_vp_phone_call_on_hold;
            } else {
                resId =  R.drawable.ic_phone_paused_white_24dp;
            }
        } else if (call.getSessionModificationState()
                == Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
            resId =  R.drawable.ic_videocam;
        } else {
            if (supportsVoicePrivacy) {
                resId =  R.drawable.stat_sys_vp_phone_call;
            } else {
                resId =  R.drawable.ic_call_white_24dp;
            }
        }
        return resId;
    }

    /**
     * Returns the message to use with the notification.
     */
    private String getContentString(Call call, @UserType long userType) {
        boolean isIncomingOrWaiting = call.getState() == Call.State.INCOMING ||
                call.getState() == Call.State.CALL_WAITING;

        if (isIncomingOrWaiting &&
                call.getNumberPresentation() == TelecomManager.PRESENTATION_ALLOWED) {

            if (!TextUtils.isEmpty(call.getChildNumber())) {
                return mContext.getString(R.string.child_number, call.getChildNumber());
            } else if (!TextUtils.isEmpty(call.getCallSubject()) && call.isCallSubjectSupported()) {
                return call.getCallSubject();
            }
        }

        int resId = R.string.notification_ongoing_call;
        if (call.hasProperty(Details.PROPERTY_WIFI)) {
            resId = R.string.notification_ongoing_call_wifi;
        }

        if (isIncomingOrWaiting) {
            if (call.isIncomingConfCall()) {
                resId = R.string.notification_incoming_conf_call;
            } else if (call.hasProperty(Details.PROPERTY_WIFI)) {
                resId = R.string.notification_incoming_call_wifi;
            } else {
                resId = R.string.notification_incoming_call;
            }
        } else if (call.getState() == Call.State.ONHOLD) {
            resId = R.string.notification_on_hold;
        } else if (Call.State.isDialing(call.getState())) {
            resId = R.string.notification_dialing;
        } else if (call.getSessionModificationState()
                == Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
            resId = R.string.notification_requesting_video_call;
        }

        // Is the call placed through work connection service.
        boolean isWorkCall = call.hasProperty(PROPERTY_ENTERPRISE_CALL);
        if(userType == ContactsUtils.USER_TYPE_WORK || isWorkCall) {
            resId = getWorkStringFromPersonalString(resId);
        }

        return mContext.getString(resId);
    }

    private static int getWorkStringFromPersonalString(int resId) {
        if (resId == R.string.notification_ongoing_call) {
            return R.string.notification_ongoing_work_call;
        } else if (resId == R.string.notification_ongoing_call_wifi) {
            return R.string.notification_ongoing_work_call_wifi;
        } else if (resId == R.string.notification_incoming_call_wifi) {
            return R.string.notification_incoming_work_call_wifi;
        } else if (resId == R.string.notification_incoming_call) {
            return R.string.notification_incoming_work_call;
        } else {
            return resId;
        }
    }

    /**
     * Gets the most relevant call to display in the notification.
     */
    private Call getCallToShow(CallList callList) {
        if (callList == null) {
            return null;
        }
        Call call = callList.getIncomingCall();
        if (call == null) {
            call = callList.getOutgoingCall();
        }
        if (call == null) {
            call = callList.getVideoUpgradeRequestCall();
        }
        if (call == null) {
            call = callList.getActiveOrBackgroundCall();
        }
        return call;
    }

    private void addAnswerAction(Notification.Builder builder) {
        Log.d(this, "Will show \"answer\" action in the incoming call Notification");

        PendingIntent answerVoicePendingIntent = createNotificationPendingIntent(
                mContext, ACTION_ANSWER_VOICE_INCOMING_CALL);
        builder.addAction(R.drawable.ic_call_white_24dp,
                mContext.getText(R.string.notification_action_answer),
                answerVoicePendingIntent);
    }

    private void addDismissAction(Notification.Builder builder) {
        Log.d(this, "Will show \"dismiss\" action in the incoming call Notification");

        PendingIntent declinePendingIntent =
                createNotificationPendingIntent(mContext, ACTION_DECLINE_INCOMING_CALL);
        builder.addAction(R.drawable.ic_close_dk,
                mContext.getText(R.string.notification_action_dismiss),
                declinePendingIntent);
    }

    private void addHangupAction(Notification.Builder builder) {
        Log.d(this, "Will show \"hang-up\" action in the ongoing active call Notification");

        PendingIntent hangupPendingIntent =
                createNotificationPendingIntent(mContext, ACTION_HANG_UP_ONGOING_CALL);
        builder.addAction(R.drawable.ic_call_end_white_24dp,
                mContext.getText(R.string.notification_action_end_call),
                hangupPendingIntent);
    }

    private void addVideoCallAction(Notification.Builder builder, int videoState) {
        Log.i(this, "Will show \"video\" action in the incoming call Notification");

        PendingIntent answerVideoPendingIntent = createNotificationPendingIntent(
                mContext, ACTION_ANSWER_VIDEO_INCOMING_CALL);
        builder.addAction(QtiCallUtils.toVideoIcon(videoState),
                mContext.getText(R.string.notification_action_answer_video),
                answerVideoPendingIntent);
    }

    private void addVoiceAction(Notification.Builder builder) {
        Log.d(this, "Will show \"voice\" action in the incoming call Notification");

        PendingIntent answerVoicePendingIntent = createNotificationPendingIntent(
                mContext, ACTION_ANSWER_VOICE_INCOMING_CALL);
        builder.addAction(R.drawable.ic_call_white_24dp,
                mContext.getText(R.string.notification_action_answer_voice),
                answerVoicePendingIntent);
    }

    private void addAcceptUpgradeRequestAction(Notification.Builder builder) {
        Log.i(this, "Will show \"accept upgrade\" action in the incoming call Notification");

        PendingIntent acceptVideoPendingIntent = createNotificationPendingIntent(
                mContext, ACTION_ACCEPT_VIDEO_UPGRADE_REQUEST);
        builder.addAction(0, mContext.getText(R.string.notification_action_accept),
                acceptVideoPendingIntent);
    }

    private void addDismissUpgradeRequestAction(Notification.Builder builder) {
        Log.i(this, "Will show \"dismiss upgrade\" action in the incoming call Notification");

        PendingIntent declineVideoPendingIntent = createNotificationPendingIntent(
                mContext, ACTION_DECLINE_VIDEO_UPGRADE_REQUEST);
        builder.addAction(0, mContext.getText(R.string.notification_action_dismiss),
                declineVideoPendingIntent);
    }

    /**
     * Adds fullscreen intent to the builder.
     */
    private void configureFullScreenIntent(Notification.Builder builder, PendingIntent intent,
            Call call) {
        // Ok, we actually want to launch the incoming call
        // UI at this point (in addition to simply posting a notification
        // to the status bar).  Setting fullScreenIntent will cause
        // the InCallScreen to be launched immediately *unless* the
        // current foreground activity is marked as "immersive".
        Log.d(this, "- Setting fullScreenIntent: " + intent);
        builder.setFullScreenIntent(intent, true);

        // Ugly hack alert:
        //
        // The NotificationManager has the (undocumented) behavior
        // that it will *ignore* the fullScreenIntent field if you
        // post a new Notification that matches the ID of one that's
        // already active.  Unfortunately this is exactly what happens
        // when you get an incoming call-waiting call:  the
        // "ongoing call" notification is already visible, so the
        // InCallScreen won't get launched in this case!
        // (The result: if you bail out of the in-call UI while on a
        // call and then get a call-waiting call, the incoming call UI
        // won't come up automatically.)
        //
        // The workaround is to just notice this exact case (this is a
        // call-waiting call *and* the InCallScreen is not in the
        // foreground) and manually cancel the in-call notification
        // before (re)posting it.
        //
        // TODO: there should be a cleaner way of avoiding this
        // problem (see discussion in bug 3184149.)

        // If a call is onhold during an incoming call, the call actually comes in as
        // INCOMING.  For that case *and* traditional call-waiting, we want to
        // cancel the notification.

        // For DSDA, we want to cancel the notification if we get an incoming call on
        // one sub and there is a live call on another sub.
        CallList callList = CallList.getInstance();
        boolean isCallWaiting = (call.getState() == Call.State.CALL_WAITING ||
                (call.getState() == Call.State.INCOMING &&
                (callList.getBackgroundCall() != null ||
                callList.isAnyOtherSubActive(callList.getActiveSubId()))));

        if (isCallWaiting) {
            Log.i(this, "configureFullScreenIntent: call-waiting or dsda incoming call!"
                    + " force relaunch. Active sub:" + callList.getActiveSubId());
            // Cancel the IN_CALL_NOTIFICATION immediately before
            // (re)posting it; this seems to force the
            // NotificationManager to launch the fullScreenIntent.
            mNotificationManager.cancel(NOTIFICATION_IN_CALL);
        }
    }

    private Notification.Builder getNotificationBuilder() {
        final Notification.Builder builder = new Notification.Builder(mContext);
        builder.setOngoing(true);

        // Make the notification prioritized over the other normal notifications.
        builder.setPriority(Notification.PRIORITY_HIGH);

        return builder;
    }

    private PendingIntent createLaunchPendingIntent() {

        final Intent intent = InCallPresenter.getInstance().getInCallIntent(
                false /* showDialpad */, false /* newOutgoingCall */);

        // PendingIntent that can be used to launch the InCallActivity.  The
        // system fires off this intent if the user pulls down the windowshade
        // and clicks the notification's expanded view.  It's also used to
        // launch the InCallActivity immediately when when there's an incoming
        // call (see the "fullScreenIntent" field below).
        PendingIntent inCallPendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0);

        return inCallPendingIntent;
    }

    /**
     * Returns PendingIntent for answering a phone call. This will typically be used from
     * Notification context.
     */
    private static PendingIntent createNotificationPendingIntent(Context context, String action) {
        final Intent intent = new Intent(action, null,
                context, NotificationBroadcastReceiver.class);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    @Override
    public void onCallChanged(Call call) {
        if (CallList.getInstance().getIncomingCall() == null) {
            mDialerRingtoneManager.stopCallWaitingTone();
        }
    }

    /**
     * Responds to changes in the session modification state for the call by dismissing the
     * status bar notification as required.
     *
     * @param sessionModificationState The new session modification state.
     */
    @Override
    public void onSessionModificationStateChange(Call call, int sessionModificationState) {
        if (sessionModificationState == Call.SessionModificationState.NO_REQUEST) {
            if (mCallId != null) {
                CallList.getInstance().removeCallUpdateListener(mCallId, this);
            }

            updateNotification(mInCallState, CallList.getInstance());
        }
    }

    @Override
    public void onLastForwardedNumberChange() {
        // no-op
    }

    @Override
    public void onChildNumberChange() {
        // no-op
    }
}
