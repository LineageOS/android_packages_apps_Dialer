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

package com.android.dialer.app.calllog;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import com.android.contacts.common.model.Contact;
import com.android.contacts.common.model.ContactLoader;
import com.android.dialer.app.AccountSelectionActivity;
import com.android.dialer.calldetails.CallDetailsActivity;
import com.android.dialer.calldetails.CallDetailsEntries;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.dialercontact.DialerContact;
import com.android.dialer.duo.DuoComponent;
import com.android.dialer.duo.DuoConstants;
import com.android.dialer.precall.PreCall;
import com.android.dialer.util.IntentUtil;
import java.util.ArrayList;

/**
 * Used to create an intent to attach to an action in the call log.
 *
 * <p>The intent is constructed lazily with the given information.
 */
public abstract class IntentProvider {

  private static final String TAG = IntentProvider.class.getSimpleName();

  public static IntentProvider getReturnCallIntentProvider(final String number) {
    return getReturnCallIntentProvider(number, null);
  }

  public static IntentProvider getReturnCallIntentProvider(
      final String number, final PhoneAccountHandle accountHandle) {
    return new IntentProvider() {
      @Override
      public Intent getClickIntent(Context context) {
        return PreCall.getIntent(
            context,
            new CallIntentBuilder(number, CallInitiationType.Type.CALL_LOG)
                .setPhoneAccountHandle(accountHandle));
      }

      @Override
      public Intent getLongClickIntent(Context context) {
        return AccountSelectionActivity.createIntent(context, number,
            CallInitiationType.Type.CALL_LOG);
      }
    };
  }

  public static IntentProvider getAssistedDialIntentProvider(
      final String number, final Context context, final TelephonyManager telephonyManager) {
    return new IntentProvider() {
      @Override
      public Intent getClickIntent(Context context) {
        return PreCall.getIntent(
            context,
            new CallIntentBuilder(number, CallInitiationType.Type.CALL_LOG)
                .setAllowAssistedDial(true));
      }

    };
  }

  public static IntentProvider getReturnVideoCallIntentProvider(final String number) {
    return getReturnVideoCallIntentProvider(number, null);
  }

  public static IntentProvider getReturnVideoCallIntentProvider(
      final String number, final PhoneAccountHandle accountHandle) {
    return new IntentProvider() {
      @Override
      public Intent getClickIntent(Context context) {
        return PreCall.getIntent(
            context,
            new CallIntentBuilder(number, CallInitiationType.Type.CALL_LOG)
                .setPhoneAccountHandle(accountHandle)
                .setIsVideoCall(true));
      }
    };
  }

  public static IntentProvider getDuoVideoIntentProvider(String number) {
    return new IntentProvider() {
      @Override
      public Intent getClickIntent(Context context) {
        return DuoComponent.get(context).getDuo().getIntent(context, number);
      }
    };
  }

  public static IntentProvider getInstallDuoIntentProvider() {
    return new IntentProvider() {
      @Override
      public Intent getClickIntent(Context context) {
        return new Intent(
            Intent.ACTION_VIEW,
            new Uri.Builder()
                .scheme("https")
                .authority("play.google.com")
                .appendEncodedPath("store/apps/details")
                .appendQueryParameter("id", DuoConstants.PACKAGE_NAME)
                .appendQueryParameter(
                    "referrer",
                    "utm_source=dialer&utm_medium=text&utm_campaign=product") // This string is from
                // the Duo team
                .build());
      }
    };
  }

  public static IntentProvider getSetUpDuoIntentProvider() {
    return new IntentProvider() {
      @Override
      public Intent getClickIntent(Context context) {
        return new Intent(DuoConstants.DUO_ACTIVATE_ACTION).setPackage(DuoConstants.PACKAGE_NAME);
      }
    };
  }

  public static IntentProvider getDuoInviteIntentProvider(String number) {
    return new IntentProvider() {
      @Override
      public Intent getClickIntent(Context context) {
        Intent intent =
            new Intent(DuoConstants.DUO_INVITE_ACTION)
                .setPackage(DuoConstants.PACKAGE_NAME)
                .setData(Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null /* fragment */));
        return intent;
      }
    };
  }

  public static IntentProvider getReturnVoicemailCallIntentProvider(
      @Nullable PhoneAccountHandle phoneAccountHandle) {
    return new IntentProvider() {
      @Override
      public Intent getClickIntent(Context context) {
        return PreCall.getIntent(
            context,
            CallIntentBuilder.forVoicemail(phoneAccountHandle, CallInitiationType.Type.CALL_LOG));
      }
    };
  }

  public static IntentProvider getSendSmsIntentProvider(final String number) {
    return new IntentProvider() {
      @Override
      public Intent getClickIntent(Context context) {
        return IntentUtil.getSendSmsIntent(number);
      }
    };
  }

  /**
   * Retrieves the call details intent provider for an entry in the call log.
   *
   * @param callDetailsEntries The call details of the other calls grouped together with the call.
   * @param contact The contact with which this call details intent pertains to.
   * @param canReportCallerId Whether reporting a caller ID is supported.
   * @param canSupportAssistedDialing Whether assisted dialing is supported.
   * @return The call details intent provider.
   */
  public static IntentProvider getCallDetailIntentProvider(
      CallDetailsEntries callDetailsEntries,
      DialerContact contact,
      boolean canReportCallerId,
      boolean canSupportAssistedDialing) {
    return new IntentProvider() {
      @Override
      public Intent getClickIntent(Context context) {
        return CallDetailsActivity.newInstance(
            context, callDetailsEntries, contact, canReportCallerId, canSupportAssistedDialing);
      }
    };
  }

  /** Retrieves an add contact intent for the given contact and phone call details. */
  public static IntentProvider getAddContactIntentProvider(
      final Uri lookupUri,
      final CharSequence name,
      final CharSequence number,
      final int numberType,
      final boolean isNewContact) {
    return new IntentProvider() {
      @Override
      public Intent getClickIntent(Context context) {
        Contact contactToSave = null;

        if (lookupUri != null) {
          contactToSave = ContactLoader.parseEncodedContactEntity(lookupUri);
        }

        if (contactToSave != null) {
          // Populate the intent with contact information stored in the lookup URI.
          // Note: This code mirrors code in Contacts/QuickContactsActivity.
          final Intent intent;
          if (isNewContact) {
            intent = IntentUtil.getNewContactIntent();
          } else {
            intent = IntentUtil.getAddToExistingContactIntent();
          }

          ArrayList<ContentValues> values = contactToSave.getContentValues();
          // Only pre-fill the name field if the provided display name is an nickname
          // or better (e.g. structured name, nickname)
          if (contactToSave.getDisplayNameSource()
              >= ContactsContract.DisplayNameSources.NICKNAME) {
            intent.putExtra(ContactsContract.Intents.Insert.NAME, contactToSave.getDisplayName());
          } else if (contactToSave.getDisplayNameSource()
              == ContactsContract.DisplayNameSources.ORGANIZATION) {
            // This is probably an organization. Instead of copying the organization
            // name into a name entry, copy it into the organization entry. This
            // way we will still consider the contact an organization.
            final ContentValues organization = new ContentValues();
            organization.put(
                ContactsContract.CommonDataKinds.Organization.COMPANY,
                contactToSave.getDisplayName());
            organization.put(
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE);
            values.add(organization);
          }

          // Last time used and times used are aggregated values from the usage stat
          // table. They need to be removed from data values so the SQL table can insert
          // properly
          for (ContentValues value : values) {
            value.remove(ContactsContract.Data.LAST_TIME_USED);
            value.remove(ContactsContract.Data.TIMES_USED);
          }

          intent.putExtra(ContactsContract.Intents.Insert.DATA, values);

          return intent;
        } else {
          // If no lookup uri is provided, rely on the available phone number and name.
          if (isNewContact) {
            return IntentUtil.getNewContactIntent(name, number, numberType);
          } else {
            return IntentUtil.getAddToExistingContactIntent(name, number, numberType);
          }
        }
      }
    };
  }

  public abstract Intent getClickIntent(Context context);
  public Intent getLongClickIntent(Context context) {
    return null;
  }
}
