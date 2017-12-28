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

import static android.telecom.Call.Details.PROPERTY_HIGH_DEF_AUDIO;
import static com.android.contacts.common.compat.CallCompat.Details.PROPERTY_ENTERPRISE_CALL;
import static com.android.incallui.NotificationBroadcastReceiver.ACTION_ACCEPT_VIDEO_UPGRADE_REQUEST;
import static com.android.incallui.NotificationBroadcastReceiver.ACTION_ANSWER_VIDEO_INCOMING_CALL;
import static com.android.incallui.NotificationBroadcastReceiver.ACTION_ANSWER_VOICE_INCOMING_CALL;
import static com.android.incallui.NotificationBroadcastReceiver.ACTION_DECLINE_INCOMING_CALL;
import static com.android.incallui.NotificationBroadcastReceiver.ACTION_DECLINE_VIDEO_UPGRADE_REQUEST;
import static com.android.incallui.NotificationBroadcastReceiver.ACTION_HANG_UP_ONGOING_CALL;
import static com.android.incallui.NotificationBroadcastReceiver.ACTION_TURN_OFF_SPEAKER;
import static com.android.incallui.NotificationBroadcastReceiver.ACTION_TURN_ON_SPEAKER;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Trace;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;
import android.support.annotation.StringRes;
import android.support.annotation.VisibleForTesting;
import android.support.v4.os.BuildCompat;
import android.telecom.Call.Details;
import android.telecom.CallAudioState;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.text.BidiFormatter;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.ContactsUtils.UserType;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.contactphoto.BitmapUtil;
import com.android.dialer.enrichedcall.EnrichedCallManager;
import com.android.dialer.enrichedcall.Session;
import com.android.dialer.lettertile.LetterTileDrawable;
import com.android.dialer.lettertile.LetterTileDrawable.ContactType;
import com.android.dialer.multimedia.MultimediaData;
import com.android.dialer.notification.NotificationChannelId;
import com.android.dialer.oem.MotorolaUtils;
import com.android.dialer.util.DrawableConverter;
import com.android.incallui.ContactInfoCache.ContactCacheEntry;
import com.android.incallui.ContactInfoCache.ContactInfoCacheCallback;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.async.PausableExecutorImpl;
import com.android.incallui.audiomode.AudioModeProvider;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.DialerCallListener;
import com.android.incallui.call.TelecomAdapter;
import com.android.incallui.ringtone.DialerRingtoneManager;
import com.android.incallui.ringtone.InCallTonePlayer;
import com.android.incallui.ringtone.ToneGeneratorFactory;
import com.android.incallui.videotech.utils.SessionModificationState;
import java.util.Objects;

/** This class adds Notifications to the status bar for the in-call experience. */
public class StatusBarNotifier
    implements InCallPresenter.InCallStateListener,
        EnrichedCallManager.StateChangedListener,
        ContactInfoCacheCallback {

  private static final int NOTIFICATION_ID = 1;

  // Notification types
  // Indicates that no notification is currently showing.
  private static final int NOTIFICATION_NONE = 0;
  // Notification for an active call. This is non-interruptive, but cannot be dismissed.
  private static final int NOTIFICATION_IN_CALL = 1;
  // Notification for incoming calls. This is interruptive and will show up as a HUN.
  private static final int NOTIFICATION_INCOMING_CALL = 2;
  // Notification for incoming calls in the case where there is already an active call.
  // This is non-interruptive, but otherwise behaves the same as NOTIFICATION_INCOMING_CALL
  private static final int NOTIFICATION_INCOMING_CALL_QUIET = 3;

  private static final long[] VIBRATE_PATTERN = new long[] {0, 1000, 1000};

  private final Context context;
  private final ContactInfoCache contactInfoCache;
  private final DialerRingtoneManager dialerRingtoneManager;
  @Nullable private ContactsPreferences contactsPreferences;
  private int currentNotification = NOTIFICATION_NONE;
  private int callState = DialerCall.State.INVALID;
  private int videoState = VideoProfile.STATE_AUDIO_ONLY;
  private int savedIcon = 0;
  private String savedContent = null;
  private Bitmap savedLargeIcon;
  private String savedContentTitle;
  private CallAudioState savedCallAudioState;
  private Uri ringtone;
  private StatusBarCallListener statusBarCallListener;

  public StatusBarNotifier(@NonNull Context context, @NonNull ContactInfoCache contactInfoCache) {
    Trace.beginSection("StatusBarNotifier.Constructor");
    this.context = Assert.isNotNull(context);
    contactsPreferences = ContactsPreferencesFactory.newContactsPreferences(this.context);
    this.contactInfoCache = contactInfoCache;
    dialerRingtoneManager =
        new DialerRingtoneManager(
            new InCallTonePlayer(new ToneGeneratorFactory(), new PausableExecutorImpl()),
            CallList.getInstance());
    currentNotification = NOTIFICATION_NONE;
    Trace.endSection();
  }

  /**
   * Should only be called from a irrecoverable state where it is necessary to dismiss all
   * notifications.
   */
  static void clearAllCallNotifications() {
    LogUtil.e(
        "StatusBarNotifier.clearAllCallNotifications",
        "something terrible happened, clear all InCall notifications");

    TelecomAdapter.getInstance().stopForegroundNotification();
  }

  private static int getWorkStringFromPersonalString(int resId) {
    if (resId == R.string.notification_ongoing_call) {
      return R.string.notification_ongoing_work_call;
    } else if (resId == R.string.notification_incoming_call) {
      return R.string.notification_incoming_work_call;
    } else {
      return resId;
    }
  }

  /**
   * Returns PendingIntent for answering a phone call. This will typically be used from Notification
   * context.
   */
  private static PendingIntent createNotificationPendingIntent(Context context, String action) {
    final Intent intent = new Intent(action, null, context, NotificationBroadcastReceiver.class);
    return PendingIntent.getBroadcast(context, 0, intent, 0);
  }

  /** Creates notifications according to the state we receive from {@link InCallPresenter}. */
  @Override
  @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
  public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
    LogUtil.d("StatusBarNotifier.onStateChange", "%s->%s", oldState, newState);
    updateNotification();
  }

  @Override
  public void onEnrichedCallStateChanged() {
    LogUtil.enterBlock("StatusBarNotifier.onEnrichedCallStateChanged");
    updateNotification();
  }

  /**
   * Updates the phone app's status bar notification *and* launches the incoming call UI in response
   * to a new incoming call.
   *
   * <p>If an incoming call is ringing (or call-waiting), the notification will also include a
   * "fullScreenIntent" that will cause the InCallScreen to be launched, unless the current
   * foreground activity is marked as "immersive".
   *
   * <p>(This is the mechanism that actually brings up the incoming call UI when we receive a "new
   * ringing connection" event from the telephony layer.)
   *
   * <p>Also note that this method is safe to call even if the phone isn't actually ringing (or,
   * more likely, if an incoming call *was* ringing briefly but then disconnected). In that case,
   * we'll simply update or cancel the in-call notification based on the current phone state.
   *
   * @see #updateInCallNotification()
   */
  @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
  public void updateNotification() {
    updateInCallNotification();
  }

  /**
   * Take down the in-call notification.
   *
   * @see #updateInCallNotification()
   */
  private void cancelNotification() {
    if (statusBarCallListener != null) {
      setStatusBarCallListener(null);
    }
    if (currentNotification != NOTIFICATION_NONE) {
      TelecomAdapter.getInstance().stopForegroundNotification();
      currentNotification = NOTIFICATION_NONE;
    }
  }

  /**
   * Helper method for updateInCallNotification() and updateNotification(): Update the phone app's
   * status bar notification based on the current telephony state, or cancels the notification if
   * the phone is totally idle.
   */
  @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
  private void updateInCallNotification() {
    LogUtil.d("StatusBarNotifier.updateInCallNotification", "");

    final DialerCall call = getCallToShow(CallList.getInstance());

    if (call != null) {
      showNotification(call);
    } else {
      cancelNotification();
    }
  }

  @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
  private void showNotification(final DialerCall call) {
    Trace.beginSection("StatusBarNotifier.showNotification");
    final boolean isIncoming =
        (call.getState() == DialerCall.State.INCOMING
            || call.getState() == DialerCall.State.CALL_WAITING);
    setStatusBarCallListener(new StatusBarCallListener(call));

    // we make a call to the contact info cache to query for supplemental data to what the
    // call provides.  This includes the contact name and photo.
    // This callback will always get called immediately and synchronously with whatever data
    // it has available, and may make a subsequent call later (same thread) if it had to
    // call into the contacts provider for more data.
    contactInfoCache.findInfo(call, isIncoming, this);
    Trace.endSection();
  }

  /** Sets up the main Ui for the notification */
  @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
  private void buildAndSendNotification(
      CallList callList, DialerCall originalCall, ContactCacheEntry contactInfo) {
    Trace.beginSection("StatusBarNotifier.buildAndSendNotification");
    // This can get called to update an existing notification after contact information has come
    // back. However, it can happen much later. Before we continue, we need to make sure that
    // the call being passed in is still the one we want to show in the notification.
    final DialerCall call = getCallToShow(callList);
    if (call == null || !call.getId().equals(originalCall.getId())) {
      Trace.endSection();
      return;
    }

    Trace.beginSection("prepare work");
    final int callState = call.getState();
    final CallAudioState callAudioState = AudioModeProvider.getInstance().getAudioState();

    Trace.beginSection("read icon and strings");
    // Check if data has changed; if nothing is different, don't issue another notification.
    final int iconResId = getIconToDisplay(call);
    Bitmap largeIcon = getLargeIconToDisplay(context, contactInfo, call);
    final CharSequence content = getContentString(call, contactInfo.userType);
    final String contentTitle = getContentTitle(contactInfo, call);
    Trace.endSection();

    final boolean isVideoUpgradeRequest =
        call.getVideoTech().getSessionModificationState()
            == SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST;
    final int notificationType;
    if (callState == DialerCall.State.INCOMING
        || callState == DialerCall.State.CALL_WAITING
        || isVideoUpgradeRequest) {
      if (ConfigProviderBindings.get(context)
          .getBoolean("quiet_incoming_call_if_ui_showing", true)) {
        notificationType =
            InCallPresenter.getInstance().isShowingInCallUi()
                ? NOTIFICATION_INCOMING_CALL_QUIET
                : NOTIFICATION_INCOMING_CALL;
      } else {
        boolean alreadyActive =
            callList.getActiveOrBackgroundCall() != null
                && InCallPresenter.getInstance().isShowingInCallUi();
        notificationType =
            alreadyActive ? NOTIFICATION_INCOMING_CALL_QUIET : NOTIFICATION_INCOMING_CALL;
      }
    } else {
      notificationType = NOTIFICATION_IN_CALL;
    }
    Trace.endSection(); // prepare work

    if (!checkForChangeAndSaveData(
        iconResId,
        content.toString(),
        largeIcon,
        contentTitle,
        callState,
        call.getVideoState(),
        notificationType,
        contactInfo.contactRingtoneUri,
        callAudioState)) {
      Trace.endSection();
      return;
    }

    if (largeIcon != null) {
      largeIcon = getRoundedIcon(largeIcon);
    }

    // This builder is used for the notification shown when the device is locked and the user
    // has set their notification settings to 'hide sensitive content'
    // {@see Notification.Builder#setPublicVersion}.
    Notification.Builder publicBuilder = new Notification.Builder(context);
    publicBuilder
        .setSmallIcon(iconResId)
        .setColor(context.getResources().getColor(R.color.dialer_theme_color, context.getTheme()))
        // Hide work call state for the lock screen notification
        .setContentTitle(getContentString(call, ContactsUtils.USER_TYPE_CURRENT));
    setNotificationWhen(call, callState, publicBuilder);

    // Builder for the notification shown when the device is unlocked or the user has set their
    // notification settings to 'show all notification content'.
    final Notification.Builder builder = getNotificationBuilder();
    builder.setPublicVersion(publicBuilder.build());

    // Set up the main intent to send the user to the in-call screen
    builder.setContentIntent(createLaunchPendingIntent(false /* isFullScreen */));

    LogUtil.i("StatusBarNotifier.buildAndSendNotification", "notificationType=" + notificationType);
    switch (notificationType) {
      case NOTIFICATION_INCOMING_CALL:
        if (BuildCompat.isAtLeastO()) {
          builder.setChannelId(NotificationChannelId.INCOMING_CALL);
        }
        // Set the intent as a full screen intent as well if a call is incoming
        configureFullScreenIntent(builder, createLaunchPendingIntent(true /* isFullScreen */));
        // Set the notification category and bump the priority for incoming calls
        builder.setCategory(Notification.CATEGORY_CALL);
        // This will be ignored on O+ and handled by the channel
        builder.setPriority(Notification.PRIORITY_MAX);
        if (currentNotification != NOTIFICATION_INCOMING_CALL) {
          LogUtil.i(
              "StatusBarNotifier.buildAndSendNotification",
              "Canceling old notification so this one can be noisy");
          // Moving from a non-interuptive notification (or none) to a noisy one. Cancel the old
          // notification (if there is one) so the fullScreenIntent or HUN will show
          TelecomAdapter.getInstance().stopForegroundNotification();
        }
        break;
      case NOTIFICATION_INCOMING_CALL_QUIET:
        if (BuildCompat.isAtLeastO()) {
          builder.setChannelId(NotificationChannelId.ONGOING_CALL);
        }
        break;
      case NOTIFICATION_IN_CALL:
        if (BuildCompat.isAtLeastO()) {
          publicBuilder.setColorized(true);
          builder.setColorized(true);
          builder.setChannelId(NotificationChannelId.ONGOING_CALL);
        }
        break;
      default:
        break;
    }

    // Set the content
    builder.setContentText(content);
    builder.setSmallIcon(iconResId);
    builder.setContentTitle(contentTitle);
    builder.setLargeIcon(largeIcon);
    builder.setColor(InCallPresenter.getInstance().getThemeColorManager().getPrimaryColor());

    if (isVideoUpgradeRequest) {
      builder.setUsesChronometer(false);
      addDismissUpgradeRequestAction(builder);
      addAcceptUpgradeRequestAction(builder);
    } else {
      createIncomingCallNotification(call, callState, callAudioState, builder);
    }

    addPersonReference(builder, contactInfo, call);

    Trace.beginSection("fire notification");
    // Fire off the notification
    Notification notification = builder.build();

    if (dialerRingtoneManager.shouldPlayRingtone(callState, contactInfo.contactRingtoneUri)) {
      notification.flags |= Notification.FLAG_INSISTENT;
      notification.sound = contactInfo.contactRingtoneUri;
      AudioAttributes.Builder audioAttributes = new AudioAttributes.Builder();
      audioAttributes.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC);
      audioAttributes.setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE);
      notification.audioAttributes = audioAttributes.build();
      if (dialerRingtoneManager.shouldVibrate(context.getContentResolver())) {
        notification.vibrate = VIBRATE_PATTERN;
      }
    }
    if (dialerRingtoneManager.shouldPlayCallWaitingTone(callState)) {
      LogUtil.v("StatusBarNotifier.buildAndSendNotification", "playing call waiting tone");
      dialerRingtoneManager.playCallWaitingTone();
    }

    LogUtil.i(
        "StatusBarNotifier.buildAndSendNotification",
        "displaying notification for " + notificationType);

    // If a notification exists, this will only update it.
    TelecomAdapter.getInstance().startForegroundNotification(NOTIFICATION_ID, notification);

    Trace.endSection();
    call.getLatencyReport().onNotificationShown();
    currentNotification = notificationType;
    Trace.endSection();
  }

  private void createIncomingCallNotification(
      DialerCall call, int state, CallAudioState callAudioState, Notification.Builder builder) {
    setNotificationWhen(call, state, builder);

    // Add hang up option for any active calls (active | onhold), outgoing calls (dialing).
    if (state == DialerCall.State.ACTIVE
        || state == DialerCall.State.ONHOLD
        || DialerCall.State.isDialing(state)) {
      addHangupAction(builder);
      addSpeakerAction(builder, callAudioState);
    } else if (state == DialerCall.State.INCOMING || state == DialerCall.State.CALL_WAITING) {
      addDismissAction(builder);
      if (call.isVideoCall()) {
        addVideoCallAction(builder);
      } else {
        addAnswerAction(builder);
      }
    }
  }

  /**
   * Sets the notification's when section as needed. For active calls, this is explicitly set as the
   * duration of the call. For all other states, the notification will automatically show the time
   * at which the notification was created.
   */
  private void setNotificationWhen(DialerCall call, int state, Notification.Builder builder) {
    if (state == DialerCall.State.ACTIVE) {
      builder.setUsesChronometer(true);
      builder.setWhen(call.getConnectTimeMillis());
    } else {
      builder.setUsesChronometer(false);
    }
  }

  /**
   * Checks the new notification data and compares it against any notification that we are already
   * displaying. If the data is exactly the same, we return false so that we do not issue a new
   * notification for the exact same data.
   */
  private boolean checkForChangeAndSaveData(
      int icon,
      String content,
      Bitmap largeIcon,
      String contentTitle,
      int state,
      int videoState,
      int notificationType,
      Uri ringtone,
      CallAudioState callAudioState) {

    // The two are different:
    // if new title is not null, it should be different from saved version OR
    // if new title is null, the saved version should not be null
    final boolean contentTitleChanged =
        (contentTitle != null && !contentTitle.equals(savedContentTitle))
            || (contentTitle == null && savedContentTitle != null);

    boolean largeIconChanged;
    if (savedLargeIcon == null) {
      largeIconChanged = largeIcon != null;
    } else {
      largeIconChanged = largeIcon == null || !savedLargeIcon.sameAs(largeIcon);
    }

    // any change means we are definitely updating
    boolean retval =
        (savedIcon != icon)
            || !Objects.equals(savedContent, content)
            || (callState != state)
            || (this.videoState != videoState)
            || largeIconChanged
            || contentTitleChanged
            || !Objects.equals(this.ringtone, ringtone)
            || !Objects.equals(savedCallAudioState, callAudioState);

    LogUtil.d(
        "StatusBarNotifier.checkForChangeAndSaveData",
        "data changed: icon: %b, content: %b, state: %b, videoState: %b, largeIcon: %b, title: %b,"
            + "ringtone: %b, audioState: %b, type: %b",
        (savedIcon != icon),
        !Objects.equals(savedContent, content),
        (callState != state),
        (this.videoState != videoState),
        largeIconChanged,
        contentTitleChanged,
        !Objects.equals(this.ringtone, ringtone),
        !Objects.equals(savedCallAudioState, callAudioState),
        currentNotification != notificationType);
    // If we aren't showing a notification right now or the notification type is changing,
    // definitely do an update.
    if (currentNotification != notificationType) {
      if (currentNotification == NOTIFICATION_NONE) {
        LogUtil.d(
            "StatusBarNotifier.checkForChangeAndSaveData", "showing notification for first time.");
      }
      retval = true;
    }

    savedIcon = icon;
    savedContent = content;
    callState = state;
    this.videoState = videoState;
    savedLargeIcon = largeIcon;
    savedContentTitle = contentTitle;
    this.ringtone = ringtone;
    savedCallAudioState = callAudioState;

    if (retval) {
      LogUtil.d(
          "StatusBarNotifier.checkForChangeAndSaveData", "data changed.  Showing notification");
    }

    return retval;
  }

  /** Returns the main string to use in the notification. */
  @VisibleForTesting
  @Nullable
  String getContentTitle(ContactCacheEntry contactInfo, DialerCall call) {
    if (call.isConferenceCall()) {
      return CallerInfoUtils.getConferenceString(
          context, call.hasProperty(Details.PROPERTY_GENERIC_CONFERENCE));
    }

    String preferredName =
        ContactDisplayUtils.getPreferredDisplayName(
            contactInfo.namePrimary, contactInfo.nameAlternative, contactsPreferences);
    if (TextUtils.isEmpty(preferredName)) {
      return TextUtils.isEmpty(contactInfo.number)
          ? null
          : BidiFormatter.getInstance()
              .unicodeWrap(contactInfo.number, TextDirectionHeuristics.LTR);
    }
    return preferredName;
  }

  private void addPersonReference(
      Notification.Builder builder, ContactCacheEntry contactInfo, DialerCall call) {
    // Query {@link Contacts#CONTENT_LOOKUP_URI} directly with work lookup key is not allowed.
    // So, do not pass {@link Contacts#CONTENT_LOOKUP_URI} to NotificationManager to avoid
    // NotificationManager using it.
    if (contactInfo.lookupUri != null && contactInfo.userType != ContactsUtils.USER_TYPE_WORK) {
      builder.addPerson(contactInfo.lookupUri.toString());
    } else if (!TextUtils.isEmpty(call.getNumber())) {
      builder.addPerson(Uri.fromParts(PhoneAccount.SCHEME_TEL, call.getNumber(), null).toString());
    }
  }

  /** Gets a large icon from the contact info object to display in the notification. */
  private static Bitmap getLargeIconToDisplay(
      Context context, ContactCacheEntry contactInfo, DialerCall call) {
    Trace.beginSection("StatusBarNotifier.getLargeIconToDisplay");
    Resources resources = context.getResources();
    Bitmap largeIcon = null;
    if (contactInfo.photo != null && (contactInfo.photo instanceof BitmapDrawable)) {
      largeIcon = ((BitmapDrawable) contactInfo.photo).getBitmap();
    }
    if (contactInfo.photo == null) {
      int width = (int) resources.getDimension(android.R.dimen.notification_large_icon_width);
      int height = (int) resources.getDimension(android.R.dimen.notification_large_icon_height);
      @ContactType
      int contactType =
          LetterTileDrawable.getContactTypeFromPrimitives(
              call.isVoiceMailNumber(),
              call.isSpam(),
              contactInfo.isBusiness,
              call.getNumberPresentation(),
              call.isConferenceCall() && !call.hasProperty(Details.PROPERTY_GENERIC_CONFERENCE));
      LetterTileDrawable lettertile = new LetterTileDrawable(resources);

      lettertile.setCanonicalDialerLetterTileDetails(
          contactInfo.namePrimary == null ? contactInfo.number : contactInfo.namePrimary,
          contactInfo.lookupKey,
          LetterTileDrawable.SHAPE_CIRCLE,
          contactType);
      largeIcon = lettertile.getBitmap(width, height);
    }

    if (call.isSpam()) {
      Drawable drawable = resources.getDrawable(R.drawable.blocked_contact, context.getTheme());
      largeIcon = DrawableConverter.drawableToBitmap(drawable);
    }
    Trace.endSection();
    return largeIcon;
  }

  private Bitmap getRoundedIcon(Bitmap bitmap) {
    if (bitmap == null) {
      return null;
    }
    final int height =
        (int) context.getResources().getDimension(android.R.dimen.notification_large_icon_height);
    final int width =
        (int) context.getResources().getDimension(android.R.dimen.notification_large_icon_width);
    return BitmapUtil.getRoundedBitmap(bitmap, width, height);
  }

  /**
   * Returns the appropriate icon res Id to display based on the call for which we want to display
   * information.
   */
  @VisibleForTesting
  public int getIconToDisplay(DialerCall call) {
    // Even if both lines are in use, we only show a single item in
    // the expanded Notifications UI.  It's labeled "Ongoing call"
    // (or "On hold" if there's only one call, and it's on hold.)
    // Also, we don't have room to display caller-id info from two
    // different calls.  So if both lines are in use, display info
    // from the foreground call.  And if there's a ringing call,
    // display that regardless of the state of the other calls.
    if (call.getState() == DialerCall.State.ONHOLD) {
      return R.drawable.quantum_ic_phone_paused_vd_theme_24;
    } else if (call.getVideoTech().getSessionModificationState()
            == SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST
        || call.isVideoCall()) {
      return R.drawable.quantum_ic_videocam_white_24;
    } else if (call.hasProperty(PROPERTY_HIGH_DEF_AUDIO)
        && MotorolaUtils.shouldShowHdIconInNotification(context)) {
      // Normally when a call is ongoing the status bar displays an icon of a phone. This is a
      // helpful hint for users so they know how to get back to the call. For Sprint HD calls, we
      // replace this icon with an icon of a phone with a HD badge. This is a carrier requirement.
      return R.drawable.ic_hd_call;
    } else if (call.hasProperty(Details.PROPERTY_HAS_CDMA_VOICE_PRIVACY)) {
      return R.drawable.quantum_ic_phone_locked_vd_theme_24;
    }
    // If ReturnToCall is enabled, use the static icon. The animated one will show in the bubble.
    if (ReturnToCallController.isEnabled(context) || NewReturnToCallController.isEnabled(context)) {
      return R.drawable.quantum_ic_call_vd_theme_24;
    } else {
      return R.drawable.on_going_call;
    }
  }

  /** Returns the message to use with the notification. */
  private CharSequence getContentString(DialerCall call, @UserType long userType) {
    boolean isIncomingOrWaiting =
        call.getState() == DialerCall.State.INCOMING
            || call.getState() == DialerCall.State.CALL_WAITING;

    if (isIncomingOrWaiting
        && call.getNumberPresentation() == TelecomManager.PRESENTATION_ALLOWED) {

      if (!TextUtils.isEmpty(call.getChildNumber())) {
        return context.getString(R.string.child_number, call.getChildNumber());
      } else if (!TextUtils.isEmpty(call.getCallSubject()) && call.isCallSubjectSupported()) {
        return call.getCallSubject();
      }
    }

    int resId = R.string.notification_ongoing_call;
    String wifiBrand = context.getString(R.string.notification_call_wifi_brand);
    if (call.hasProperty(Details.PROPERTY_WIFI)) {
      resId = R.string.notification_ongoing_call_wifi_template;
    }

    if (isIncomingOrWaiting) {
      if (call.isSpam()) {
        resId = R.string.notification_incoming_spam_call;
      } else if (shouldShowEnrichedCallNotification(call.getEnrichedCallSession())) {
        resId = getECIncomingCallText(call.getEnrichedCallSession());
      } else if (call.hasProperty(Details.PROPERTY_WIFI)) {
        resId = R.string.notification_incoming_call_wifi_template;
      } else if (call.getAccountHandle() != null && hasMultiplePhoneAccounts(call)) {
        return getMultiSimIncomingText(call);
      } else if (call.isVideoCall()) {
        resId = R.string.notification_incoming_video_call;
      } else {
        resId = R.string.notification_incoming_call;
      }
    } else if (call.getState() == DialerCall.State.ONHOLD) {
      resId = R.string.notification_on_hold;
    } else if (call.isVideoCall()) {
      resId =
          call.getVideoTech().isPaused()
              ? R.string.notification_ongoing_paused_video_call
              : R.string.notification_ongoing_video_call;
    } else if (DialerCall.State.isDialing(call.getState())) {
      resId = R.string.notification_dialing;
    } else if (call.getVideoTech().getSessionModificationState()
        == SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
      resId = R.string.notification_requesting_video_call;
    }

    // Is the call placed through work connection service.
    boolean isWorkCall = call.hasProperty(PROPERTY_ENTERPRISE_CALL);
    if (userType == ContactsUtils.USER_TYPE_WORK || isWorkCall) {
      resId = getWorkStringFromPersonalString(resId);
      wifiBrand = context.getString(R.string.notification_call_wifi_work_brand);
    }

    if (resId == R.string.notification_incoming_call_wifi_template
        || resId == R.string.notification_ongoing_call_wifi_template) {
      // TODO(a bug): Potentially apply this template logic everywhere.
      return context.getString(resId, wifiBrand);
    }

    return context.getString(resId);
  }

  private boolean shouldShowEnrichedCallNotification(Session session) {
    if (session == null) {
      return false;
    }
    return session.getMultimediaData().hasData() || session.getMultimediaData().isImportant();
  }

  private int getECIncomingCallText(Session session) {
    int resId;
    MultimediaData data = session.getMultimediaData();
    boolean hasImage = data.hasImageData();
    boolean hasSubject = !TextUtils.isEmpty(data.getText());
    boolean hasMap = data.getLocation() != null;
    if (data.isImportant()) {
      if (hasMap) {
        if (hasImage) {
          if (hasSubject) {
            resId = R.string.important_notification_incoming_call_with_photo_message_location;
          } else {
            resId = R.string.important_notification_incoming_call_with_photo_location;
          }
        } else if (hasSubject) {
          resId = R.string.important_notification_incoming_call_with_message_location;
        } else {
          resId = R.string.important_notification_incoming_call_with_location;
        }
      } else if (hasImage) {
        if (hasSubject) {
          resId = R.string.important_notification_incoming_call_with_photo_message;
        } else {
          resId = R.string.important_notification_incoming_call_with_photo;
        }
      } else if (hasSubject) {
        resId = R.string.important_notification_incoming_call_with_message;
      } else {
        resId = R.string.important_notification_incoming_call;
      }
      if (context.getString(resId).length() > 50) {
        resId = R.string.important_notification_incoming_call_attachments;
      }
    } else {
      if (hasMap) {
        if (hasImage) {
          if (hasSubject) {
            resId = R.string.notification_incoming_call_with_photo_message_location;
          } else {
            resId = R.string.notification_incoming_call_with_photo_location;
          }
        } else if (hasSubject) {
          resId = R.string.notification_incoming_call_with_message_location;
        } else {
          resId = R.string.notification_incoming_call_with_location;
        }
      } else if (hasImage) {
        if (hasSubject) {
          resId = R.string.notification_incoming_call_with_photo_message;
        } else {
          resId = R.string.notification_incoming_call_with_photo;
        }
      } else {
        resId = R.string.notification_incoming_call_with_message;
      }
    }
    if (context.getString(resId).length() > 50) {
      resId = R.string.notification_incoming_call_attachments;
    }
    return resId;
  }

  private CharSequence getMultiSimIncomingText(DialerCall call) {
    PhoneAccount phoneAccount =
        context.getSystemService(TelecomManager.class).getPhoneAccount(call.getAccountHandle());
    SpannableString string =
        new SpannableString(
            context.getString(
                R.string.notification_incoming_call_mutli_sim, phoneAccount.getLabel()));
    int accountStart = string.toString().lastIndexOf(phoneAccount.getLabel().toString());
    int accountEnd = accountStart + phoneAccount.getLabel().length();

    string.setSpan(
        new ForegroundColorSpan(phoneAccount.getHighlightColor()),
        accountStart,
        accountEnd,
        Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
    return string;
  }

  /** Gets the most relevant call to display in the notification. */
  private DialerCall getCallToShow(CallList callList) {
    if (callList == null) {
      return null;
    }
    DialerCall call = callList.getIncomingCall();
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

  private Spannable getActionText(@StringRes int stringRes, @ColorRes int colorRes) {
    Spannable spannable = new SpannableString(context.getText(stringRes));
    if (VERSION.SDK_INT >= VERSION_CODES.N_MR1) {
      // This will only work for cases where the Notification.Builder has a fullscreen intent set
      // Notification.Builder that does not have a full screen intent will take the color of the
      // app and the following leads to a no-op.
      spannable.setSpan(
          new ForegroundColorSpan(context.getColor(colorRes)), 0, spannable.length(), 0);
    }
    return spannable;
  }

  private void addAnswerAction(Notification.Builder builder) {
    LogUtil.d(
        "StatusBarNotifier.addAnswerAction",
        "will show \"answer\" action in the incoming call Notification");
    PendingIntent answerVoicePendingIntent =
        createNotificationPendingIntent(context, ACTION_ANSWER_VOICE_INCOMING_CALL);
    builder.addAction(
        new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.quantum_ic_call_white_24),
                getActionText(
                    R.string.notification_action_answer, R.color.notification_action_accept),
                answerVoicePendingIntent)
            .build());
  }

  private void addDismissAction(Notification.Builder builder) {
    LogUtil.d(
        "StatusBarNotifier.addDismissAction",
        "will show \"decline\" action in the incoming call Notification");
    PendingIntent declinePendingIntent =
        createNotificationPendingIntent(context, ACTION_DECLINE_INCOMING_CALL);
    builder.addAction(
        new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.quantum_ic_close_white_24),
                getActionText(
                    R.string.notification_action_dismiss, R.color.notification_action_dismiss),
                declinePendingIntent)
            .build());
  }

  private void addHangupAction(Notification.Builder builder) {
    LogUtil.d(
        "StatusBarNotifier.addHangupAction",
        "will show \"hang-up\" action in the ongoing active call Notification");
    PendingIntent hangupPendingIntent =
        createNotificationPendingIntent(context, ACTION_HANG_UP_ONGOING_CALL);
    builder.addAction(
        new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.quantum_ic_call_end_white_24),
                context.getText(R.string.notification_action_end_call),
                hangupPendingIntent)
            .build());
  }

  private void addSpeakerAction(Notification.Builder builder, CallAudioState callAudioState) {
    if ((callAudioState.getSupportedRouteMask() & CallAudioState.ROUTE_BLUETOOTH)
        == CallAudioState.ROUTE_BLUETOOTH) {
      // Don't add speaker button if bluetooth is connected
      return;
    }
    if (callAudioState.getRoute() == CallAudioState.ROUTE_SPEAKER) {
      addSpeakerOffAction(builder);
    } else if ((callAudioState.getRoute() & CallAudioState.ROUTE_WIRED_OR_EARPIECE) != 0) {
      addSpeakerOnAction(builder);
    }
  }

  private void addSpeakerOnAction(Notification.Builder builder) {
    LogUtil.d(
        "StatusBarNotifier.addSpeakerOnAction",
        "will show \"Speaker on\" action in the ongoing active call Notification");
    PendingIntent speakerOnPendingIntent =
        createNotificationPendingIntent(context, ACTION_TURN_ON_SPEAKER);
    builder.addAction(
        new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.quantum_ic_volume_up_white_24),
                context.getText(R.string.notification_action_speaker_on),
                speakerOnPendingIntent)
            .build());
  }

  private void addSpeakerOffAction(Notification.Builder builder) {
    LogUtil.d(
        "StatusBarNotifier.addSpeakerOffAction",
        "will show \"Speaker off\" action in the ongoing active call Notification");
    PendingIntent speakerOffPendingIntent =
        createNotificationPendingIntent(context, ACTION_TURN_OFF_SPEAKER);
    builder.addAction(
        new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.quantum_ic_phone_in_talk_white_24),
                context.getText(R.string.notification_action_speaker_off),
                speakerOffPendingIntent)
            .build());
  }

  private void addVideoCallAction(Notification.Builder builder) {
    LogUtil.i(
        "StatusBarNotifier.addVideoCallAction",
        "will show \"video\" action in the incoming call Notification");
    PendingIntent answerVideoPendingIntent =
        createNotificationPendingIntent(context, ACTION_ANSWER_VIDEO_INCOMING_CALL);
    builder.addAction(
        new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.quantum_ic_videocam_white_24),
                getActionText(
                    R.string.notification_action_answer_video,
                    R.color.notification_action_answer_video),
                answerVideoPendingIntent)
            .build());
  }

  private void addAcceptUpgradeRequestAction(Notification.Builder builder) {
    LogUtil.i(
        "StatusBarNotifier.addAcceptUpgradeRequestAction",
        "will show \"accept upgrade\" action in the incoming call Notification");
    PendingIntent acceptVideoPendingIntent =
        createNotificationPendingIntent(context, ACTION_ACCEPT_VIDEO_UPGRADE_REQUEST);
    builder.addAction(
        new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.quantum_ic_videocam_white_24),
                getActionText(
                    R.string.notification_action_accept, R.color.notification_action_accept),
                acceptVideoPendingIntent)
            .build());
  }

  private void addDismissUpgradeRequestAction(Notification.Builder builder) {
    LogUtil.i(
        "StatusBarNotifier.addDismissUpgradeRequestAction",
        "will show \"dismiss upgrade\" action in the incoming call Notification");
    PendingIntent declineVideoPendingIntent =
        createNotificationPendingIntent(context, ACTION_DECLINE_VIDEO_UPGRADE_REQUEST);
    builder.addAction(
        new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.quantum_ic_videocam_white_24),
                getActionText(
                    R.string.notification_action_dismiss, R.color.notification_action_dismiss),
                declineVideoPendingIntent)
            .build());
  }

  /** Adds fullscreen intent to the builder. */
  private void configureFullScreenIntent(Notification.Builder builder, PendingIntent intent) {
    // Ok, we actually want to launch the incoming call
    // UI at this point (in addition to simply posting a notification
    // to the status bar).  Setting fullScreenIntent will cause
    // the InCallScreen to be launched immediately *unless* the
    // current foreground activity is marked as "immersive".
    LogUtil.d("StatusBarNotifier.configureFullScreenIntent", "setting fullScreenIntent: " + intent);
    builder.setFullScreenIntent(intent, true);
  }

  private Notification.Builder getNotificationBuilder() {
    final Notification.Builder builder = new Notification.Builder(context);
    builder.setOngoing(true);
    builder.setOnlyAlertOnce(true);
    // This will be ignored on O+ and handled by the channel
    // noinspection deprecation
    builder.setPriority(Notification.PRIORITY_HIGH);

    return builder;
  }

  private PendingIntent createLaunchPendingIntent(boolean isFullScreen) {
    Intent intent =
        InCallActivity.getIntent(
            context, false /* showDialpad */, false /* newOutgoingCall */, isFullScreen);

    int requestCode = InCallActivity.PendingIntentRequestCodes.NON_FULL_SCREEN;
    if (isFullScreen) {
      // Use a unique request code so that the pending intent isn't clobbered by the
      // non-full screen pending intent.
      requestCode = InCallActivity.PendingIntentRequestCodes.FULL_SCREEN;
    }

    // PendingIntent that can be used to launch the InCallActivity.  The
    // system fires off this intent if the user pulls down the windowshade
    // and clicks the notification's expanded view.  It's also used to
    // launch the InCallActivity immediately when when there's an incoming
    // call (see the "fullScreenIntent" field below).
    return PendingIntent.getActivity(context, requestCode, intent, 0);
  }

  private void setStatusBarCallListener(StatusBarCallListener listener) {
    if (statusBarCallListener != null) {
      statusBarCallListener.cleanup();
    }
    statusBarCallListener = listener;
  }

  private boolean hasMultiplePhoneAccounts(DialerCall call) {
    if (call.getCallCapableAccounts() == null) {
      return false;
    }
    return call.getCallCapableAccounts().size() > 1;
  }

  @Override
  @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
  public void onContactInfoComplete(String callId, ContactCacheEntry entry) {
    DialerCall call = CallList.getInstance().getCallById(callId);
    if (call != null) {
      call.getLogState().contactLookupResult = entry.contactLookupResult;
      buildAndSendNotification(CallList.getInstance(), call, entry);
    }
  }

  @Override
  @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
  public void onImageLoadComplete(String callId, ContactCacheEntry entry) {
    DialerCall call = CallList.getInstance().getCallById(callId);
    if (call != null) {
      buildAndSendNotification(CallList.getInstance(), call, entry);
    }
  }

  private class StatusBarCallListener implements DialerCallListener {

    private DialerCall dialerCall;

    StatusBarCallListener(DialerCall dialerCall) {
      this.dialerCall = dialerCall;
      this.dialerCall.addListener(this);
    }

    void cleanup() {
      dialerCall.removeListener(this);
    }

    @Override
    public void onDialerCallDisconnect() {}

    @Override
    public void onDialerCallUpdate() {
      if (CallList.getInstance().getIncomingCall() == null) {
        dialerRingtoneManager.stopCallWaitingTone();
      }
    }

    @Override
    public void onDialerCallChildNumberChange() {}

    @Override
    public void onDialerCallLastForwardedNumberChange() {}

    @Override
    public void onDialerCallUpgradeToVideo() {}

    @Override
    public void onWiFiToLteHandover() {}

    @Override
    public void onHandoverToWifiFailure() {}

    @Override
    public void onInternationalCallOnWifi() {}

    @Override
    public void onEnrichedCallSessionUpdate() {}

    /**
     * Responds to changes in the session modification state for the call by dismissing the status
     * bar notification as required.
     */
    @Override
    public void onDialerCallSessionModificationStateChange() {
      if (dialerCall.getVideoTech().getSessionModificationState()
          == SessionModificationState.NO_REQUEST) {
        cleanup();
        updateNotification();
      }
    }
  }
}
