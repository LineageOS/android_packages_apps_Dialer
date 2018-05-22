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
 * limitations under the License
 */

package com.android.incallui.spam;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.telephony.PhoneNumberUtils;
import com.android.dialer.blocking.BlockedNumbersMigrator;
import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler;
import com.android.dialer.blocking.FilteredNumberCompat;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs.DialogFragmentForBlockingNumberAndOptionallyReportingAsSpam;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs.DialogFragmentForReportingNotSpam;
import com.android.dialer.common.LogUtil;
import com.android.dialer.location.GeoUtil;
import com.android.dialer.logging.ContactLookupResult;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.ReportingLocation;
import com.android.dialer.notification.DialerNotificationManager;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.spam.SpamComponent;
import com.android.dialer.spam.SpamSettings;
import com.android.dialer.spam.promo.SpamBlockingPromoHelper;
import com.android.incallui.call.DialerCall;

/** Creates the after call notification dialogs. */
public class SpamNotificationActivity extends FragmentActivity {

  /** Action to add number to contacts. */
  static final String ACTION_ADD_TO_CONTACTS = "com.android.incallui.spam.ACTION_ADD_TO_CONTACTS";
  /** Action to show dialog. */
  static final String ACTION_SHOW_DIALOG = "com.android.incallui.spam.ACTION_SHOW_DIALOG";
  /** Action to mark a number as spam. */
  static final String ACTION_MARK_NUMBER_AS_SPAM =
      "com.android.incallui.spam.ACTION_MARK_NUMBER_AS_SPAM";
  /** Action to mark a number as not spam. */
  static final String ACTION_MARK_NUMBER_AS_NOT_SPAM =
      "com.android.incallui.spam.ACTION_MARK_NUMBER_AS_NOT_SPAM";

  static final String ACTION_ENABLE_SPAM_BLOCKING =
      "com.android.incallui.spam.ACTION_ENABLE_SPAM_BLOCKING";

  static final String ACTION_SHOW_SPAM_BLOCKING_PROMO_DIALOG =
      "com.android.incallui.spam.ACTION_SHOW_SPAM_BLOCKING_PROMO_DIALOG";

  private static final String TAG = "SpamNotifications";
  private static final String EXTRA_NOTIFICATION_TAG = "notification_tag";
  private static final String EXTRA_NOTIFICATION_ID = "notification_id";
  private static final String EXTRA_CALL_INFO = "call_info";

  private static final String CALL_INFO_KEY_PHONE_NUMBER = "phone_number";
  private static final String CALL_INFO_KEY_IS_SPAM = "is_spam";
  private static final String CALL_INFO_KEY_CALL_ID = "call_id";
  private static final String CALL_INFO_KEY_START_TIME_MILLIS = "call_start_time_millis";
  private static final String CALL_INFO_CONTACT_LOOKUP_RESULT_TYPE = "contact_lookup_result_type";
  private final DialogInterface.OnDismissListener dismissListener =
      new DialogInterface.OnDismissListener() {
        @Override
        public void onDismiss(DialogInterface dialog) {
          if (!isFinishing()) {
            finish();
          }
        }
      };
  private FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler;
  private SpamSettings spamSettings;
  private SpamBlockingPromoHelper spamBlockingPromoHelper;

  /**
   * Creates an intent to start this activity.
   *
   * @return Intent intent that starts this activity.
   */
  public static Intent createActivityIntent(
      Context context,
      @Nullable DialerCall call,
      String action,
      String notificationTag,
      int notificationId) {
    Intent intent = new Intent(context, SpamNotificationActivity.class);
    intent.setAction(action);
    // This ensures only one activity of this kind exists at a time.
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    intent.putExtra(EXTRA_NOTIFICATION_TAG, notificationTag);
    intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);

    if (call != null) {
      intent.putExtra(EXTRA_CALL_INFO, newCallInfoBundle(call));
    }
    return intent;
  }

  /** Creates the intent to insert a contact. */
  private static Intent createInsertContactsIntent(String number) {
    Intent intent = new Intent(ContactsContract.Intents.Insert.ACTION);
    // This ensures that the edit contact number field gets updated if called more than once.
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    intent.setType(ContactsContract.RawContacts.CONTENT_TYPE);
    intent.putExtra(ContactsContract.Intents.Insert.PHONE, number);
    return intent;
  }

  /** Returns the formatted version of the given number. */
  private static String getFormattedNumber(String number, Context context) {
    String formattedNumber =
        PhoneNumberHelper.formatNumber(context, number, GeoUtil.getCurrentCountryIso(context));
    return PhoneNumberUtils.createTtsSpannable(formattedNumber).toString();
  }

  private void logCallImpression(DialerImpression.Type impression) {
    logCallImpression(this, getCallInfo(), impression);
  }

  private static void logCallImpression(
      Context context, Bundle bundle, DialerImpression.Type impression) {
    Logger.get(context)
        .logCallImpression(
            impression,
            bundle.getString(CALL_INFO_KEY_CALL_ID),
            bundle.getLong(CALL_INFO_KEY_START_TIME_MILLIS, 0));
  }

  private static Bundle newCallInfoBundle(DialerCall call) {
    Bundle bundle = new Bundle();
    bundle.putString(CALL_INFO_KEY_PHONE_NUMBER, call.getNumber());
    bundle.putBoolean(CALL_INFO_KEY_IS_SPAM, call.isSpam());
    bundle.putString(CALL_INFO_KEY_CALL_ID, call.getUniqueCallId());
    bundle.putLong(CALL_INFO_KEY_START_TIME_MILLIS, call.getTimeAddedMs());
    bundle.putInt(
        CALL_INFO_CONTACT_LOOKUP_RESULT_TYPE, call.getLogState().contactLookupResult.getNumber());
    return bundle;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    LogUtil.i(TAG, "onCreate");
    super.onCreate(savedInstanceState);
    setFinishOnTouchOutside(true);
    filteredNumberAsyncQueryHandler = new FilteredNumberAsyncQueryHandler(this);
    spamSettings = SpamComponent.get(this).spamSettings();
    spamBlockingPromoHelper = new SpamBlockingPromoHelper(getApplicationContext(), spamSettings);
    cancelNotification();
  }

  @Override
  protected void onResume() {
    LogUtil.i(TAG, "onResume");
    super.onResume();
    Intent intent = getIntent();
    String number = getCallInfo().getString(CALL_INFO_KEY_PHONE_NUMBER);
    boolean isSpam = getCallInfo().getBoolean(CALL_INFO_KEY_IS_SPAM);
    ContactLookupResult.Type contactLookupResultType =
        ContactLookupResult.Type.forNumber(
            getCallInfo().getInt(CALL_INFO_CONTACT_LOOKUP_RESULT_TYPE, 0));
    switch (intent.getAction()) {
      case ACTION_ADD_TO_CONTACTS:
        logCallImpression(DialerImpression.Type.SPAM_AFTER_CALL_NOTIFICATION_ADD_TO_CONTACTS);
        startActivity(createInsertContactsIntent(number));
        finish();
        break;
      case ACTION_MARK_NUMBER_AS_SPAM:
        assertDialogsEnabled();
        maybeShowBlockReportSpamDialog(number, contactLookupResultType);
        break;
      case ACTION_MARK_NUMBER_AS_NOT_SPAM:
        assertDialogsEnabled();
        maybeShowNotSpamDialog(number, contactLookupResultType);
        break;
      case ACTION_SHOW_DIALOG:
        if (isSpam) {
          showSpamFullDialog();
        } else {
          showNonSpamDialog();
        }
        break;
      case ACTION_SHOW_SPAM_BLOCKING_PROMO_DIALOG:
        showSpamBlockingPromoDialog();
        break;
      default: // fall out
    }
  }

  @Override
  protected void onPause() {
    LogUtil.d(TAG, "onPause");
    // Finish activity on pause (e.g: orientation change or back button pressed)
    filteredNumberAsyncQueryHandler = null;
    if (!isFinishing()) {
      finish();
    }
    super.onPause();
  }

  /** Creates and displays the dialog for whitelisting a number. */
  private void maybeShowNotSpamDialog(
      final String number, final ContactLookupResult.Type contactLookupResultType) {
    if (SpamComponent.get(this).spamSettings().isDialogEnabledForSpamNotification()) {
      DialogFragmentForReportingNotSpam.newInstance(
              getFormattedNumber(number, this),
              new BlockReportSpamDialogs.OnConfirmListener() {
                @Override
                public void onClick() {
                  reportNotSpamAndFinish(number, contactLookupResultType);
                }
              },
              dismissListener)
          .show(getSupportFragmentManager(), BlockReportSpamDialogs.NOT_SPAM_DIALOG_TAG);
    } else {
      reportNotSpamAndFinish(number, contactLookupResultType);
    }
  }

  /** Creates and displays the dialog for blocking/reporting a number as spam. */
  private void maybeShowBlockReportSpamDialog(
      final String number, final ContactLookupResult.Type contactLookupResultType) {
    if (SpamComponent.get(this).spamSettings().isDialogEnabledForSpamNotification()) {
      String displayNumber = getFormattedNumber(number, this);
      maybeShowBlockNumberMigrationDialog(
          new BlockedNumbersMigrator.Listener() {
            @Override
            public void onComplete() {
              DialogFragmentForBlockingNumberAndOptionallyReportingAsSpam.newInstance(
                      displayNumber,
                      SpamComponent.get(SpamNotificationActivity.this)
                          .spamSettings()
                          .isDialogReportSpamCheckedByDefault(),
                      new BlockReportSpamDialogs.OnSpamDialogClickListener() {
                        @Override
                        public void onClick(boolean isSpamChecked) {
                          blockReportNumber(number, isSpamChecked, contactLookupResultType);
                        }
                      },
                      dismissListener)
                  .show(
                      getSupportFragmentManager(),
                      BlockReportSpamDialogs.BLOCK_REPORT_SPAM_DIALOG_TAG);
            }
          });
    } else {
      blockReportNumber(number, true, contactLookupResultType);
    }
  }

  /**
   * Displays the dialog for the first time unknown calls with actions "Add contact", "Block/report
   * spam", and "Dismiss".
   */
  private void showNonSpamDialog() {
    logCallImpression(DialerImpression.Type.SPAM_AFTER_CALL_NOTIFICATION_SHOW_NON_SPAM_DIALOG);
    FirstTimeNonSpamCallDialogFragment.newInstance(getCallInfo())
        .show(getFragmentManager(), FirstTimeNonSpamCallDialogFragment.TAG);
  }

  /**
   * Displays the dialog for first time spam calls with actions "Not spam", "Block", and "Dismiss".
   */
  private void showSpamFullDialog() {
    logCallImpression(DialerImpression.Type.SPAM_AFTER_CALL_NOTIFICATION_SHOW_SPAM_DIALOG);
    FirstTimeSpamCallDialogFragment.newInstance(getCallInfo())
        .show(getFragmentManager(), FirstTimeSpamCallDialogFragment.TAG);
  }

  /** Checks if the user has migrated to the new blocking and display a dialog if necessary. */
  private void maybeShowBlockNumberMigrationDialog(BlockedNumbersMigrator.Listener listener) {
    if (!FilteredNumberCompat.maybeShowBlockNumberMigrationDialog(
        this, getFragmentManager(), listener)) {
      listener.onComplete();
    }
  }

  /** Block and report the number as spam. */
  private void blockReportNumber(
      String number, boolean reportAsSpam, ContactLookupResult.Type contactLookupResultType) {
    if (reportAsSpam) {
      logCallImpression(DialerImpression.Type.SPAM_AFTER_CALL_NOTIFICATION_MARKED_NUMBER_AS_SPAM);
      SpamComponent.get(this)
          .spam()
          .reportSpamFromAfterCallNotification(
              number,
              getCountryIso(),
              CallLog.Calls.INCOMING_TYPE,
              ReportingLocation.Type.FEEDBACK_PROMPT,
              contactLookupResultType);
    }

    logCallImpression(DialerImpression.Type.SPAM_AFTER_CALL_NOTIFICATION_BLOCK_NUMBER);
    filteredNumberAsyncQueryHandler.blockNumber(null, number, getCountryIso());
  }

  /** Report the number as not spam. */
  private void reportNotSpamAndFinish(
      String number, ContactLookupResult.Type contactLookupResultType) {
    logCallImpression(DialerImpression.Type.SPAM_AFTER_CALL_NOTIFICATION_REPORT_NUMBER_AS_NOT_SPAM);
    SpamComponent.get(this)
        .spam()
        .reportNotSpamFromAfterCallNotification(
            number,
            getCountryIso(),
            CallLog.Calls.INCOMING_TYPE,
            ReportingLocation.Type.FEEDBACK_PROMPT,
            contactLookupResultType);
    // TODO: DialerCall finish() after async task completes (a bug)
    finish();
  }

  /** Cancels the notification associated with the number. */
  private void cancelNotification() {
    String notificationTag = getIntent().getStringExtra(EXTRA_NOTIFICATION_TAG);
    int notificationId = getIntent().getIntExtra(EXTRA_NOTIFICATION_ID, 1);
    DialerNotificationManager.cancel(this, notificationTag, notificationId);
  }

  private String getCountryIso() {
    return GeoUtil.getCurrentCountryIso(this);
  }

  private void assertDialogsEnabled() {
    if (!SpamComponent.get(this).spamSettings().isDialogEnabledForSpamNotification()) {
      throw new IllegalStateException(
          "Cannot start this activity with given action because dialogs are not enabled.");
    }
  }

  private Bundle getCallInfo() {
    return getIntent().hasExtra(EXTRA_CALL_INFO)
        ? getIntent().getBundleExtra(EXTRA_CALL_INFO)
        : new Bundle();
  }

  /** Dialog that displays "Not spam", "Block/report spam" and "Dismiss". */
  public static class FirstTimeSpamCallDialogFragment extends DialogFragment {

    public static final String TAG = "FirstTimeSpamDialog";

    private boolean dismissed;
    private Context applicationContext;

    private static DialogFragment newInstance(Bundle bundle) {
      FirstTimeSpamCallDialogFragment fragment = new FirstTimeSpamCallDialogFragment();
      fragment.setArguments(bundle);
      return fragment;
    }

    @Override
    public void onPause() {
      dismiss();
      super.onPause();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
      logCallImpression(
          applicationContext,
          getArguments(),
          DialerImpression.Type.SPAM_AFTER_CALL_NOTIFICATION_ON_DISMISS_SPAM_DIALOG);
      super.onDismiss(dialog);
      // If dialog was not dismissed by user pressing one of the buttons, finish activity
      if (!dismissed && getActivity() != null && !getActivity().isFinishing()) {
        getActivity().finish();
      }
    }

    @Override
    public void onAttach(Context context) {
      super.onAttach(context);
      applicationContext = context.getApplicationContext();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      super.onCreateDialog(savedInstanceState);
      final SpamNotificationActivity spamNotificationActivity =
          (SpamNotificationActivity) getActivity();
      final String number = getArguments().getString(CALL_INFO_KEY_PHONE_NUMBER);
      final ContactLookupResult.Type contactLookupResultType =
          ContactLookupResult.Type.forNumber(
              getArguments().getInt(CALL_INFO_CONTACT_LOOKUP_RESULT_TYPE, 0));

      return new AlertDialog.Builder(getActivity())
          .setCancelable(false)
          .setTitle(
              getString(
                  SpamAlternativeExperimentUtil.getResourceIdByName(
                      "spam_notification_title", applicationContext),
                  getFormattedNumber(number, applicationContext)))
          .setNeutralButton(
              getString(R.string.spam_notification_action_dismiss),
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  dismiss();
                }
              })
          .setPositiveButton(
              getString(R.string.spam_notification_block_spam_action_text),
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  dismissed = true;
                  dismiss();
                  spamNotificationActivity.maybeShowBlockReportSpamDialog(
                      number, contactLookupResultType);
                  spamNotificationActivity.maybeShowSpamBlockingPromoAndFinish();
                }
              })
          .setNegativeButton(
              getString(
                  SpamAlternativeExperimentUtil.getResourceIdByName(
                      "spam_notification_was_not_spam_action_text", applicationContext)),
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  dismissed = true;
                  dismiss();
                  spamNotificationActivity.maybeShowNotSpamDialog(number, contactLookupResultType);
                }
              })
          .create();
    }
  }

  /** Dialog that displays "Add contact", "Block/report spam" and "Dismiss". */
  public static class FirstTimeNonSpamCallDialogFragment extends DialogFragment {

    public static final String TAG = "FirstTimeNonSpamDialog";

    private boolean dismissed;
    private Context context;

    private static DialogFragment newInstance(Bundle bundle) {
      FirstTimeNonSpamCallDialogFragment fragment = new FirstTimeNonSpamCallDialogFragment();
      fragment.setArguments(bundle);
      return fragment;
    }

    @Override
    public void onPause() {
      // Dismiss on pause e.g: orientation change
      dismiss();
      super.onPause();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
      super.onDismiss(dialog);
      logCallImpression(
          context,
          getArguments(),
          DialerImpression.Type.SPAM_AFTER_CALL_NOTIFICATION_ON_DISMISS_NON_SPAM_DIALOG);
      // If dialog was not dismissed by user pressing one of the buttons, finish activity
      if (!dismissed && getActivity() != null && !getActivity().isFinishing()) {
        getActivity().finish();
      }
    }

    @Override
    public void onAttach(Context context) {
      super.onAttach(context);
      this.context = context.getApplicationContext();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      super.onCreateDialog(savedInstanceState);
      final SpamNotificationActivity spamNotificationActivity =
          (SpamNotificationActivity) getActivity();
      final String number = getArguments().getString(CALL_INFO_KEY_PHONE_NUMBER);
      final ContactLookupResult.Type contactLookupResultType =
          ContactLookupResult.Type.forNumber(
              getArguments().getInt(CALL_INFO_CONTACT_LOOKUP_RESULT_TYPE, 0));
      return new AlertDialog.Builder(getActivity())
          .setTitle(
              getString(R.string.non_spam_notification_title, getFormattedNumber(number, context)))
          .setCancelable(false)
          .setMessage(
              getString(
                  SpamAlternativeExperimentUtil.getResourceIdByName(
                      "spam_notification_non_spam_call_expanded_text", context)))
          .setNeutralButton(
              getString(R.string.spam_notification_action_dismiss),
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  dismiss();
                }
              })
          .setPositiveButton(
              getString(R.string.spam_notification_dialog_add_contact_action_text),
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  dismissed = true;
                  dismiss();
                  startActivity(createInsertContactsIntent(number));
                }
              })
          .setNegativeButton(
              getString(
                  SpamAlternativeExperimentUtil.getResourceIdByName(
                      "spam_notification_dialog_block_report_spam_action_text", context)),
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  dismissed = true;
                  dismiss();
                  spamNotificationActivity.maybeShowBlockReportSpamDialog(
                      number, contactLookupResultType);
                  spamNotificationActivity.maybeShowSpamBlockingPromoAndFinish();
                }
              })
          .create();
    }
  }

  private void maybeShowSpamBlockingPromoAndFinish() {
    if (!spamBlockingPromoHelper.shouldShowAfterCallSpamBlockingPromo()) {
      finish();
      return;
    }
    Logger.get(this)
        .logImpression(DialerImpression.Type.SPAM_BLOCKING_AFTER_CALL_NOTIFICATION_PROMO_SHOWN);
    showSpamBlockingPromoDialog();
  }

  private void showSpamBlockingPromoDialog() {
    spamBlockingPromoHelper.showSpamBlockingPromoDialog(
        getSupportFragmentManager(),
        () -> {
          Logger.get(this)
              .logImpression(
                  DialerImpression.Type
                      .SPAM_BLOCKING_ENABLED_THROUGH_AFTER_CALL_NOTIFICATION_PROMO);
          spamSettings.modifySpamBlockingSetting(
              true,
              success -> {
                if (!success) {
                  Logger.get(this)
                      .logImpression(
                          DialerImpression.Type
                              .SPAM_BLOCKING_MODIFY_FAILURE_THROUGH_AFTER_CALL_NOTIFICATION_PROMO);
                }
                spamBlockingPromoHelper.showModifySettingOnCompleteToast(success);
              });
        },
        dialog -> finish());
  }
}
