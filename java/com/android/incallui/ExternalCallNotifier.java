/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.incallui;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.os.BuildCompat;
import android.telecom.Call;
import android.telecom.PhoneAccount;
import android.telecom.VideoProfile;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.util.ArrayMap;
import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.compat.CallCompat;
import com.android.dialer.common.Assert;
import com.android.dialer.contactphoto.BitmapUtil;
import com.android.dialer.contacts.ContactsComponent;
import com.android.dialer.notification.DialerNotificationManager;
import com.android.dialer.notification.NotificationChannelId;
import com.android.dialer.telecom.TelecomCallUtil;
import com.android.dialer.theme.base.ThemeComponent;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.DialerCallDelegate;
import com.android.incallui.call.ExternalCallList;
import com.android.incallui.latencyreport.LatencyReport;
import java.util.Map;

/**
 * Handles the display of notifications for "external calls".
 *
 * <p>External calls are a representation of a call which is in progress on the user's other device
 * (e.g. another phone, or a watch).
 */
public class ExternalCallNotifier implements ExternalCallList.ExternalCallListener {

  /**
   * Common tag for all external call notifications. Unlike other grouped notifications in Dialer,
   * external call notifications are uniquely identified by ID.
   */
  private static final String NOTIFICATION_TAG = "EXTERNAL_CALL";

  private static final int GROUP_SUMMARY_NOTIFICATION_ID = -1;
  private static final String GROUP_SUMMARY_NOTIFICATION_TAG = "GroupSummary_ExternalCall";
  /**
   * Key used to associate all external call notifications and the summary as belonging to a single
   * group.
   */
  private static final String GROUP_KEY = "ExternalCallGroup";

  private final Context context;
  private final ContactInfoCache contactInfoCache;
  private Map<Call, NotificationInfo> notifications = new ArrayMap<>();
  private int nextUniqueNotificationId;

  /** Initializes a new instance of the external call notifier. */
  public ExternalCallNotifier(
      @NonNull Context context, @NonNull ContactInfoCache contactInfoCache) {
    this.context = context;
    this.contactInfoCache = contactInfoCache;
  }

  /**
   * Handles the addition of a new external call by showing a new notification. Triggered by {@link
   * CallList#onCallAdded(android.telecom.Call)}.
   */
  @Override
  public void onExternalCallAdded(android.telecom.Call call) {
    Log.i(this, "onExternalCallAdded " + call);
    Assert.checkArgument(!notifications.containsKey(call));
    NotificationInfo info = new NotificationInfo(call, nextUniqueNotificationId++);
    notifications.put(call, info);

    showNotifcation(info);
  }

  /**
   * Handles the removal of an external call by hiding its associated notification. Triggered by
   * {@link CallList#onCallRemoved(android.telecom.Call)}.
   */
  @Override
  public void onExternalCallRemoved(android.telecom.Call call) {
    Log.i(this, "onExternalCallRemoved " + call);

    dismissNotification(call);
  }

  /** Handles updates to an external call. */
  @Override
  public void onExternalCallUpdated(Call call) {
    Assert.checkArgument(notifications.containsKey(call));
    postNotification(notifications.get(call));
  }

  @Override
  public void onExternalCallPulled(Call call) {
    // no-op; if an external call is pulled, it will be removed via onExternalCallRemoved.
  }

  /**
   * Initiates a call pull given a notification ID.
   *
   * @param notificationId The notification ID associated with the external call which is to be
   *     pulled.
   */
  @TargetApi(VERSION_CODES.N_MR1)
  public void pullExternalCall(int notificationId) {
    for (NotificationInfo info : notifications.values()) {
      if (info.getNotificationId() == notificationId
          && CallCompat.canPullExternalCall(info.getCall())) {
        info.getCall().pullExternalCall();
        return;
      }
    }
  }

  /**
   * Shows a notification for a new external call. Performs a contact cache lookup to find any
   * associated photo and information for the call.
   */
  private void showNotifcation(final NotificationInfo info) {
    // We make a call to the contact info cache to query for supplemental data to what the
    // call provides.  This includes the contact name and photo.
    // This callback will always get called immediately and synchronously with whatever data
    // it has available, and may make a subsequent call later (same thread) if it had to
    // call into the contacts provider for more data.
    DialerCall dialerCall =
        new DialerCall(
            context,
            new DialerCallDelegateStub(),
            info.getCall(),
            new LatencyReport(),
            false /* registerCallback */);

    contactInfoCache.findInfo(
        dialerCall,
        false /* isIncoming */,
        new ContactInfoCache.ContactInfoCacheCallback() {
          @Override
          public void onContactInfoComplete(
              String callId, ContactInfoCache.ContactCacheEntry entry) {

            // Ensure notification still exists as the external call could have been
            // removed during async contact info lookup.
            if (notifications.containsKey(info.getCall())) {
              saveContactInfo(info, entry);
            }
          }

          @Override
          public void onImageLoadComplete(String callId, ContactInfoCache.ContactCacheEntry entry) {

            // Ensure notification still exists as the external call could have been
            // removed during async contact info lookup.
            if (notifications.containsKey(info.getCall())) {
              savePhoto(info, entry);
            }
          }
        });
  }

  /** Dismisses a notification for an external call. */
  private void dismissNotification(Call call) {
    Assert.checkArgument(notifications.containsKey(call));

    // This will also dismiss the group summary if there are no more external call notifications.
    DialerNotificationManager.cancel(
        context, NOTIFICATION_TAG, notifications.get(call).getNotificationId());

    notifications.remove(call);
  }

  /**
   * Attempts to build a large icon to use for the notification based on the contact info and post
   * the updated notification to the notification manager.
   */
  private void savePhoto(NotificationInfo info, ContactInfoCache.ContactCacheEntry entry) {
    Bitmap largeIcon = getLargeIconToDisplay(context, entry, info.getCall());
    if (largeIcon != null) {
      largeIcon = getRoundedIcon(context, largeIcon);
    }
    info.setLargeIcon(largeIcon);
    postNotification(info);
  }

  /**
   * Builds and stores the contact information the notification will display and posts the updated
   * notification to the notification manager.
   */
  private void saveContactInfo(NotificationInfo info, ContactInfoCache.ContactCacheEntry entry) {
    info.setContentTitle(getContentTitle(context, entry, info.getCall()));
    info.setPersonReference(getPersonReference(entry, info.getCall()));
    postNotification(info);
  }

  /** Rebuild an existing or show a new notification given {@link NotificationInfo}. */
  private void postNotification(NotificationInfo info) {
    Notification.Builder builder = new Notification.Builder(context);
    // Set notification as ongoing since calls are long-running versus a point-in-time notice.
    builder.setOngoing(true);
    // Make the notification prioritized over the other normal notifications.
    builder.setPriority(Notification.PRIORITY_HIGH);
    builder.setGroup(GROUP_KEY);

    boolean isVideoCall = VideoProfile.isVideo(info.getCall().getDetails().getVideoState());
    // Set the content ("Ongoing call on another device")
    builder.setContentText(
        context.getString(
            isVideoCall
                ? R.string.notification_external_video_call
                : R.string.notification_external_call));
    builder.setSmallIcon(R.drawable.quantum_ic_call_white_24);
    builder.setContentTitle(info.getContentTitle());
    builder.setLargeIcon(info.getLargeIcon());
    builder.setColor(ThemeComponent.get(context).theme().getColorPrimary());
    builder.addPerson(info.getPersonReference());
    if (BuildCompat.isAtLeastO()) {
      builder.setChannelId(NotificationChannelId.DEFAULT);
    }

    // Where the external call supports being transferred to the local device, add an action
    // to the notification to initiate the call pull process.
    if (CallCompat.canPullExternalCall(info.getCall())) {

      Intent intent =
          new Intent(
              NotificationBroadcastReceiver.ACTION_PULL_EXTERNAL_CALL,
              null,
              context,
              NotificationBroadcastReceiver.class);
      intent.putExtra(
          NotificationBroadcastReceiver.EXTRA_NOTIFICATION_ID, info.getNotificationId());
      builder.addAction(
          new Notification.Action.Builder(
                  R.drawable.quantum_ic_call_white_24,
                  context.getString(
                      isVideoCall
                          ? R.string.notification_take_video_call
                          : R.string.notification_take_call),
                  PendingIntent.getBroadcast(context, info.getNotificationId(), intent, 0))
              .build());
    }

    /**
     * This builder is used for the notification shown when the device is locked and the user has
     * set their notification settings to 'hide sensitive content' {@see
     * Notification.Builder#setPublicVersion}.
     */
    Notification.Builder publicBuilder = new Notification.Builder(context);
    publicBuilder.setSmallIcon(R.drawable.quantum_ic_call_white_24);
    publicBuilder.setColor(ThemeComponent.get(context).theme().getColorPrimary());
    if (BuildCompat.isAtLeastO()) {
      publicBuilder.setChannelId(NotificationChannelId.DEFAULT);
    }

    builder.setPublicVersion(publicBuilder.build());
    Notification notification = builder.build();

    DialerNotificationManager.notify(
        context, NOTIFICATION_TAG, info.getNotificationId(), notification);

    showGroupSummaryNotification(context);
  }

  /**
   * Finds a large icon to display in a notification for a call. For conference calls, a conference
   * call icon is used, otherwise if contact info is specified, the user's contact photo or avatar
   * is used.
   *
   * @param context The context.
   * @param contactInfo The contact cache info.
   * @param call The call.
   * @return The large icon to use for the notification.
   */
  private @Nullable Bitmap getLargeIconToDisplay(
      Context context, ContactInfoCache.ContactCacheEntry contactInfo, android.telecom.Call call) {

    Bitmap largeIcon = null;
    if (call.getDetails().hasProperty(android.telecom.Call.Details.PROPERTY_CONFERENCE)
        && !call.getDetails()
            .hasProperty(android.telecom.Call.Details.PROPERTY_GENERIC_CONFERENCE)) {

      largeIcon =
          BitmapFactory.decodeResource(
              context.getResources(), R.drawable.quantum_ic_group_vd_theme_24);
    }
    if (contactInfo.photo != null && (contactInfo.photo instanceof BitmapDrawable)) {
      largeIcon = ((BitmapDrawable) contactInfo.photo).getBitmap();
    }
    return largeIcon;
  }

  /**
   * Given a bitmap, returns a rounded version of the icon suitable for display in a notification.
   *
   * @param context The context.
   * @param bitmap The bitmap to round.
   * @return The rounded bitmap.
   */
  private @Nullable Bitmap getRoundedIcon(Context context, @Nullable Bitmap bitmap) {
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
   * Builds a notification content title for a call. If the call is a conference call, it is
   * identified as such. Otherwise an attempt is made to show an associated contact name or phone
   * number.
   *
   * @param context The context.
   * @param contactInfo The contact info which was looked up in the contact cache.
   * @param call The call to generate a title for.
   * @return The content title.
   */
  private @Nullable String getContentTitle(
      Context context, ContactInfoCache.ContactCacheEntry contactInfo, android.telecom.Call call) {

    if (call.getDetails().hasProperty(android.telecom.Call.Details.PROPERTY_CONFERENCE)) {
      return CallerInfoUtils.getConferenceString(
          context,
          call.getDetails().hasProperty(android.telecom.Call.Details.PROPERTY_GENERIC_CONFERENCE));
    }

    String preferredName =
        ContactsComponent.get(context)
            .contactDisplayPreferences()
            .getDisplayName(contactInfo.namePrimary, contactInfo.nameAlternative);
    if (TextUtils.isEmpty(preferredName)) {
      return TextUtils.isEmpty(contactInfo.number)
          ? null
          : BidiFormatter.getInstance()
              .unicodeWrap(contactInfo.number, TextDirectionHeuristics.LTR);
    }
    return preferredName;
  }

  /**
   * Gets a "person reference" for a notification, used by the system to determine whether the
   * notification should be allowed past notification interruption filters.
   *
   * @param contactInfo The contact info from cache.
   * @param call The call.
   * @return the person reference.
   */
  private String getPersonReference(ContactInfoCache.ContactCacheEntry contactInfo, Call call) {

    String number = TelecomCallUtil.getNumber(call);
    // Query {@link Contacts#CONTENT_LOOKUP_URI} directly with work lookup key is not allowed.
    // So, do not pass {@link Contacts#CONTENT_LOOKUP_URI} to NotificationManager to avoid
    // NotificationManager using it.
    if (contactInfo.lookupUri != null && contactInfo.userType != ContactsUtils.USER_TYPE_WORK) {
      return contactInfo.lookupUri.toString();
    } else if (!TextUtils.isEmpty(number)) {
      return Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null).toString();
    }
    return "";
  }

  private static class DialerCallDelegateStub implements DialerCallDelegate {

    @Override
    public DialerCall getDialerCallFromTelecomCall(Call telecomCall) {
      return null;
    }
  }

  /** Represents a call and associated cached notification data. */
  private static class NotificationInfo {

    @NonNull private final Call call;
    private final int notificationId;
    @Nullable private String contentTitle;
    @Nullable private Bitmap largeIcon;
    @Nullable private String personReference;

    public NotificationInfo(@NonNull Call call, int notificationId) {
      this.call = call;
      this.notificationId = notificationId;
    }

    public Call getCall() {
      return call;
    }

    public int getNotificationId() {
      return notificationId;
    }

    public @Nullable String getContentTitle() {
      return contentTitle;
    }

    public void setContentTitle(@Nullable String contentTitle) {
      this.contentTitle = contentTitle;
    }

    public @Nullable Bitmap getLargeIcon() {
      return largeIcon;
    }

    public void setLargeIcon(@Nullable Bitmap largeIcon) {
      this.largeIcon = largeIcon;
    }

    public @Nullable String getPersonReference() {
      return personReference;
    }

    public void setPersonReference(@Nullable String personReference) {
      this.personReference = personReference;
    }
  }

  private static void showGroupSummaryNotification(@NonNull Context context) {
    Notification.Builder summary = new Notification.Builder(context);
    // Set notification as ongoing since calls are long-running versus a point-in-time notice.
    summary.setOngoing(true);
    // Make the notification prioritized over the other normal notifications.
    summary.setPriority(Notification.PRIORITY_HIGH);
    summary.setGroup(GROUP_KEY);
    summary.setGroupSummary(true);
    summary.setSmallIcon(R.drawable.quantum_ic_call_white_24);
    if (BuildCompat.isAtLeastO()) {
      summary.setChannelId(NotificationChannelId.DEFAULT);
    }
    DialerNotificationManager.notify(
        context, GROUP_SUMMARY_NOTIFICATION_TAG, GROUP_SUMMARY_NOTIFICATION_ID, summary.build());
  }
}
