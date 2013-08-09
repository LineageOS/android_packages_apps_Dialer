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

import com.google.common.base.Preconditions;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.android.incallui.InCallApp.NotificationBroadcastReceiver;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.services.telephony.common.Call;

/**
 * This class adds Notifications to the status bar for the in-call experience.
 */
public class StatusBarNotifier implements InCallPresenter.InCallStateListener {
    // notification types
    private static final int IN_CALL_NOTIFICATION = 1;

    private final Context mContext;
    private final NotificationManager mNotificationManager;
    private InCallState mInCallState = InCallState.HIDDEN;

    public StatusBarNotifier(Context context) {
        Preconditions.checkNotNull(context);

        mContext = context;
        mNotificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /**
     * Creates notifications according to the state we receive from {@link InCallPresenter}.
     */
    @Override
    public void onStateChange(InCallState state, CallList callList) {
        updateInCallNotification(state);
    }

    /**
     * Updates the phone app's status bar notification based on the
     * current telephony state, or cancels the notification if the phone
     * is totally idle.
     *
     * This method will never actually launch the incoming-call UI.
     * (Use updateNotificationAndLaunchIncomingCallUi() for that.)
     */
    private void updateInCallNotification(InCallState state) {
        // allowFullScreenIntent=false means *don't* allow the incoming
        // call UI to be launched.
        updateInCallNotification(false, state);
    }

    /**
     * Updates the phone app's status bar notification *and* launches the
     * incoming call UI in response to a new incoming call.
     *
     * This is just like updateInCallNotification(), with one exception:
     * If an incoming call is ringing (or call-waiting), the notification
     * will also include a "fullScreenIntent" that will cause the
     * InCallScreen to be launched immediately, unless the current
     * foreground activity is marked as "immersive".
     *
     * (This is the mechanism that actually brings up the incoming call UI
     * when we receive a "new ringing connection" event from the telephony
     * layer.)
     *
     * Watch out: this method should ONLY be called directly from the code
     * path in CallNotifier that handles the "new ringing connection"
     * event from the telephony layer.  All other places that update the
     * in-call notification (like for phone state changes) should call
     * updateInCallNotification() instead.  (This ensures that we don't
     * end up launching the InCallScreen multiple times for a single
     * incoming call, which could cause slow responsiveness and/or visible
     * glitches.)
     *
     * Also note that this method is safe to call even if the phone isn't
     * actually ringing (or, more likely, if an incoming call *was*
     * ringing briefly but then disconnected).  In that case, we'll simply
     * update or cancel the in-call notification based on the current
     * phone state.
     *
     * @see #updateInCallNotification(boolean)
     */
    public void updateNotificationAndLaunchIncomingCallUi(InCallState state) {
        // Set allowFullScreenIntent=true to indicate that we *should*
        // launch the incoming call UI if necessary.
        updateInCallNotification(true, state);
    }


    /**
     * Take down the in-call notification.
     * @see updateInCallNotification()
     */
    private void cancelInCall() {
        Logger.d(this, "cancelInCall()...");
        mNotificationManager.cancel(IN_CALL_NOTIFICATION);
    }

    /**
     * Helper method for updateInCallNotification() and
     * updateNotificationAndLaunchIncomingCallUi(): Update the phone app's
     * status bar notification based on the current telephony state, or
     * cancels the notification if the phone is totally idle.
     *
     * @param allowFullScreenIntent If true, *and* an incoming call is
     *   ringing, the notification will include a "fullScreenIntent"
     *   pointing at the InCallActivity (which will cause the InCallActivity
     *   to be launched.)
     *   Watch out: This should be set to true *only* when directly
     *   handling a new incoming call for the first time.
     */
    private void updateInCallNotification(boolean allowFullScreenIntent, InCallState state) {
        Logger.d(this, "updateInCallNotification(allowFullScreenIntent = "
                     + allowFullScreenIntent + ")...");

        // First, we dont need to continue issuing new notifications if the state hasn't
        // changed from the last time we did this.
        if (mInCallState == state) {
            return;
        }
        mInCallState = state;

        if (!state.isConnectingOrConnected()) {
            cancelInCall();
            return;
        }

        final PendingIntent inCallPendingIntent = createLaunchPendingIntent();
        final Notification.Builder builder = getNotificationBuilder();
        builder.setContentIntent(inCallPendingIntent);

        /*
         * Set up the Intents that will get fired when the user interacts with the notificaiton.
         */
        if (allowFullScreenIntent) {
            configureFullScreenIntent(builder, inCallPendingIntent);
        }

        /*
         * Set up notification Ui.
         */
        setUpNotification(builder, state);

        /*
         * Fire off the notification
         */
        Notification notification = builder.build();
        Logger.d(this, "Notifying IN_CALL_NOTIFICATION: " + notification);
        mNotificationManager.notify(IN_CALL_NOTIFICATION, notification);
    }

    /**
     * Sets up the main Ui for the notification
     */
    private void setUpNotification(Notification.Builder builder, InCallState state) {

        // Add special Content for calls that are ongoing
        if (InCallState.INCALL == state || InCallState.OUTGOING == state) {
            addActiveCallIntents(builder);
        }

        // set the content
        builder.setContentText(mContext.getString(R.string.notification_ongoing_call));
        builder.setSmallIcon(R.drawable.stat_sys_phone_call);
    }

    private void addActiveCallIntents(Notification.Builder builder) {
        Logger.i(this, "Will show \"hang-up\" action in the ongoing active call Notification");

        // TODO: use better asset.
        builder.addAction(R.drawable.stat_sys_phone_call_end,
                mContext.getText(R.string.notification_action_end_call),
                createHangUpOngoingCallPendingIntent(mContext));
    }

    /**
     * Adds fullscreen intent to the builder.
     */
    private void configureFullScreenIntent(Notification.Builder builder, PendingIntent intent) {
        // Ok, we actually want to launch the incoming call
        // UI at this point (in addition to simply posting a notification
        // to the status bar).  Setting fullScreenIntent will cause
        // the InCallScreen to be launched immediately *unless* the
        // current foreground activity is marked as "immersive".
        Logger.d(this, "- Setting fullScreenIntent: " + intent);
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

        // TODO(klp): reenable this for klp
        /*if (incomingCall.getState() == Call.State.CALL_WAITING) {
            Logger.i(this, "updateInCallNotification: call-waiting! force relaunch...");
            // Cancel the IN_CALL_NOTIFICATION immediately before
            // (re)posting it; this seems to force the
            // NotificationManager to launch the fullScreenIntent.
            mNotificationManager.cancel(IN_CALL_NOTIFICATION);
        }*/
    }

    private Notification.Builder getNotificationBuilder() {
        final Notification.Builder builder = new Notification.Builder(mContext);
        builder.setOngoing(true);

        // Make the notification prioritized over the other normal notifications.
        builder.setPriority(Notification.PRIORITY_HIGH);

        return builder;
    }

    private PendingIntent createLaunchPendingIntent() {

        final Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        intent.setClass(mContext, InCallActivity.class);

        // PendingIntent that can be used to launch the InCallActivity.  The
        // system fires off this intent if the user pulls down the windowshade
        // and clicks the notification's expanded view.  It's also used to
        // launch the InCallActivity immediately when when there's an incoming
        // call (see the "fullScreenIntent" field below).
        PendingIntent inCallPendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0);

        return inCallPendingIntent;
    }

    /**
     * Returns PendingIntent for hanging up ongoing phone call. This will typically be used from
     * Notification context.
     */
    private static PendingIntent createHangUpOngoingCallPendingIntent(Context context) {
        final Intent intent = new Intent(InCallApp.ACTION_HANG_UP_ONGOING_CALL, null,
                context, NotificationBroadcastReceiver.class);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }
}
