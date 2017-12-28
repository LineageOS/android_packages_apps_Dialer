/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.dialer.interactions;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.support.annotation.IntDef;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.ActivityCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListAdapter;
import android.widget.TextView;
import com.android.contacts.common.Collapser;
import com.android.contacts.common.Collapser.Collapsible;
import com.android.contacts.common.MoreContactUtils;
import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.callintent.CallIntentParser;
import com.android.dialer.callintent.CallSpecificAppData;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.InteractionEvent;
import com.android.dialer.logging.Logger;
import com.android.dialer.precall.PreCall;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.util.TransactionSafeActivity;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Initiates phone calls or a text message. If there are multiple candidates, this class shows a
 * dialog to pick one. Creating one of these interactions should be done through the static factory
 * methods.
 *
 * <p>Note that this class initiates not only usual *phone* calls but also *SIP* calls.
 *
 * <p>TODO: clean up code and documents since it is quite confusing to use "phone numbers" or "phone
 * calls" here while they can be SIP addresses or SIP calls (See also issue 5039627).
 */
public class PhoneNumberInteraction implements OnLoadCompleteListener<Cursor> {

  static final String TAG = PhoneNumberInteraction.class.getSimpleName();
  /** The identifier for a permissions request if one is generated. */
  public static final int REQUEST_READ_CONTACTS = 1;

  public static final int REQUEST_CALL_PHONE = 2;

  @VisibleForTesting
  public static final String[] PHONE_NUMBER_PROJECTION =
      new String[] {
        Phone._ID,
        Phone.NUMBER,
        Phone.IS_SUPER_PRIMARY,
        RawContacts.ACCOUNT_TYPE,
        RawContacts.DATA_SET,
        Phone.TYPE,
        Phone.LABEL,
        Phone.MIMETYPE,
        Phone.CONTACT_ID,
      };

  private static final String PHONE_NUMBER_SELECTION =
      Data.MIMETYPE
          + " IN ('"
          + Phone.CONTENT_ITEM_TYPE
          + "', "
          + "'"
          + SipAddress.CONTENT_ITEM_TYPE
          + "') AND "
          + Data.DATA1
          + " NOT NULL";
  private static final int UNKNOWN_CONTACT_ID = -1;
  private final Context context;
  private final int interactionType;
  private final CallSpecificAppData callSpecificAppData;
  private long contactId = UNKNOWN_CONTACT_ID;
  private CursorLoader loader;
  private boolean isVideoCall;

  /** Error codes for interactions. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
    value = {
      InteractionErrorCode.CONTACT_NOT_FOUND,
      InteractionErrorCode.CONTACT_HAS_NO_NUMBER,
      InteractionErrorCode.USER_LEAVING_ACTIVITY,
      InteractionErrorCode.OTHER_ERROR
    }
  )
  public @interface InteractionErrorCode {

    int CONTACT_NOT_FOUND = 1;
    int CONTACT_HAS_NO_NUMBER = 2;
    int OTHER_ERROR = 3;
    int USER_LEAVING_ACTIVITY = 4;
  }

  /**
   * Activities which use this class must implement this. They will be notified if there was an
   * error performing the interaction. For example, this callback will be invoked on the activity if
   * the contact URI provided points to a deleted contact, or to a contact without a phone number.
   */
  public interface InteractionErrorListener {

    void interactionError(@InteractionErrorCode int interactionErrorCode);
  }

  /**
   * Activities which use this class must implement this. They will be notified if the phone number
   * disambiguation dialog is dismissed.
   */
  public interface DisambigDialogDismissedListener {
    void onDisambigDialogDismissed();
  }

  private PhoneNumberInteraction(
      Context context,
      int interactionType,
      boolean isVideoCall,
      CallSpecificAppData callSpecificAppData) {
    this.context = context;
    this.interactionType = interactionType;
    this.callSpecificAppData = callSpecificAppData;
    this.isVideoCall = isVideoCall;

    Assert.checkArgument(context instanceof InteractionErrorListener);
    Assert.checkArgument(context instanceof DisambigDialogDismissedListener);
    Assert.checkArgument(context instanceof ActivityCompat.OnRequestPermissionsResultCallback);
  }

  private static void performAction(
      Context context,
      String phoneNumber,
      int interactionType,
      boolean isVideoCall,
      CallSpecificAppData callSpecificAppData) {
    Intent intent;
    switch (interactionType) {
      case ContactDisplayUtils.INTERACTION_SMS:
        intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("sms", phoneNumber, null));
        break;
      default:
        intent =
            PreCall.getIntent(
                context,
                new CallIntentBuilder(phoneNumber, callSpecificAppData)
                    .setIsVideoCall(isVideoCall)
                    .setAllowAssistedDial(callSpecificAppData.getAllowAssistedDialing()));
        break;
    }
    DialerUtils.startActivityWithErrorToast(context, intent);
  }

  /**
   * @param activity that is calling this interaction. This must be of type {@link
   *     TransactionSafeActivity} because we need to check on the activity state after the phone
   *     numbers have been queried for. The activity must implement {@link InteractionErrorListener}
   *     and {@link DisambigDialogDismissedListener}.
   * @param isVideoCall {@code true} if the call is a video call, {@code false} otherwise.
   */
  public static void startInteractionForPhoneCall(
      TransactionSafeActivity activity,
      Uri uri,
      boolean isVideoCall,
      CallSpecificAppData callSpecificAppData) {
    new PhoneNumberInteraction(
            activity, ContactDisplayUtils.INTERACTION_CALL, isVideoCall, callSpecificAppData)
        .startInteraction(uri);
  }

  private void performAction(String phoneNumber) {
    PhoneNumberInteraction.performAction(
        context, phoneNumber, interactionType, isVideoCall, callSpecificAppData);
  }

  /**
   * Initiates the interaction to result in either a phone call or sms message for a contact.
   *
   * @param uri Contact Uri
   */
  private void startInteraction(Uri uri) {
    // It's possible for a shortcut to have been created, and then permissions revoked. To avoid a
    // crash when the user tries to use such a shortcut, check for this condition and ask the user
    // for the permission.
    if (!PermissionsUtil.hasPhonePermissions(context)) {
      LogUtil.i("PhoneNumberInteraction.startInteraction", "Need phone permission: CALL_PHONE");
      ActivityCompat.requestPermissions(
          (Activity) context, new String[] {permission.CALL_PHONE}, REQUEST_CALL_PHONE);
      return;
    }

    String[] deniedContactsPermissions =
        PermissionsUtil.getPermissionsCurrentlyDenied(
            context, PermissionsUtil.allContactsGroupPermissionsUsedInDialer);
    if (deniedContactsPermissions.length > 0) {
      LogUtil.i(
          "PhoneNumberInteraction.startInteraction",
          "Need contact permissions: " + Arrays.toString(deniedContactsPermissions));
      ActivityCompat.requestPermissions(
          (Activity) context, deniedContactsPermissions, REQUEST_READ_CONTACTS);
      return;
    }

    if (loader != null) {
      loader.reset();
    }
    final Uri queryUri;
    final String inputUriAsString = uri.toString();
    if (inputUriAsString.startsWith(Contacts.CONTENT_URI.toString())) {
      if (!inputUriAsString.endsWith(Contacts.Data.CONTENT_DIRECTORY)) {
        queryUri = Uri.withAppendedPath(uri, Contacts.Data.CONTENT_DIRECTORY);
      } else {
        queryUri = uri;
      }
    } else if (inputUriAsString.startsWith(Data.CONTENT_URI.toString())) {
      queryUri = uri;
    } else {
      throw new UnsupportedOperationException(
          "Input Uri must be contact Uri or data Uri (input: \"" + uri + "\")");
    }

    loader =
        new CursorLoader(
            context, queryUri, PHONE_NUMBER_PROJECTION, PHONE_NUMBER_SELECTION, null, null);
    loader.registerListener(0, this);
    loader.startLoading();
  }

  @Override
  public void onLoadComplete(Loader<Cursor> loader, Cursor cursor) {
    if (cursor == null) {
      LogUtil.i("PhoneNumberInteraction.onLoadComplete", "null cursor");
      interactionError(InteractionErrorCode.OTHER_ERROR);
      return;
    }
    try {
      ArrayList<PhoneItem> phoneList = new ArrayList<>();
      String primaryPhone = null;
      if (!isSafeToCommitTransactions()) {
        LogUtil.i("PhoneNumberInteraction.onLoadComplete", "not safe to commit transaction");
        interactionError(InteractionErrorCode.USER_LEAVING_ACTIVITY);
        return;
      }
      if (cursor.moveToFirst()) {
        int contactIdColumn = cursor.getColumnIndexOrThrow(Phone.CONTACT_ID);
        int isSuperPrimaryColumn = cursor.getColumnIndexOrThrow(Phone.IS_SUPER_PRIMARY);
        int phoneNumberColumn = cursor.getColumnIndexOrThrow(Phone.NUMBER);
        int phoneIdColumn = cursor.getColumnIndexOrThrow(Phone._ID);
        int accountTypeColumn = cursor.getColumnIndexOrThrow(RawContacts.ACCOUNT_TYPE);
        int dataSetColumn = cursor.getColumnIndexOrThrow(RawContacts.DATA_SET);
        int phoneTypeColumn = cursor.getColumnIndexOrThrow(Phone.TYPE);
        int phoneLabelColumn = cursor.getColumnIndexOrThrow(Phone.LABEL);
        int phoneMimeTpeColumn = cursor.getColumnIndexOrThrow(Phone.MIMETYPE);
        do {
          if (contactId == UNKNOWN_CONTACT_ID) {
            contactId = cursor.getLong(contactIdColumn);
          }

          if (cursor.getInt(isSuperPrimaryColumn) != 0) {
            // Found super primary, call it.
            primaryPhone = cursor.getString(phoneNumberColumn);
          }

          PhoneItem item = new PhoneItem();
          item.id = cursor.getLong(phoneIdColumn);
          item.phoneNumber = cursor.getString(phoneNumberColumn);
          item.accountType = cursor.getString(accountTypeColumn);
          item.dataSet = cursor.getString(dataSetColumn);
          item.type = cursor.getInt(phoneTypeColumn);
          item.label = cursor.getString(phoneLabelColumn);
          item.mimeType = cursor.getString(phoneMimeTpeColumn);

          phoneList.add(item);
        } while (cursor.moveToNext());
      } else {
        interactionError(InteractionErrorCode.CONTACT_NOT_FOUND);
        return;
      }

      if (primaryPhone != null) {
        performAction(primaryPhone);
        return;
      }

      Collapser.collapseList(phoneList, context);
      if (phoneList.size() == 0) {
        interactionError(InteractionErrorCode.CONTACT_HAS_NO_NUMBER);
      } else if (phoneList.size() == 1) {
        PhoneItem item = phoneList.get(0);
        performAction(item.phoneNumber);
      } else {
        // There are multiple candidates. Let the user choose one.
        showDisambiguationDialog(phoneList);
      }
    } finally {
      cursor.close();
    }
  }

  private void interactionError(@InteractionErrorCode int interactionErrorCode) {
    // mContext is really the activity -- see ctor docs.
    ((InteractionErrorListener) context).interactionError(interactionErrorCode);
  }

  private boolean isSafeToCommitTransactions() {
    return !(context instanceof TransactionSafeActivity)
        || ((TransactionSafeActivity) context).isSafeToCommitTransactions();
  }

  @VisibleForTesting
  /* package */ CursorLoader getLoader() {
    return loader;
  }

  private void showDisambiguationDialog(ArrayList<PhoneItem> phoneList) {
    // TODO(a bug): don't leak the activity
    final Activity activity = (Activity) context;
    if (activity.isFinishing()) {
      LogUtil.i("PhoneNumberInteraction.showDisambiguationDialog", "activity finishing");
      return;
    }

    if (activity.isDestroyed()) {
      // Check whether the activity is still running
      LogUtil.i("PhoneNumberInteraction.showDisambiguationDialog", "activity destroyed");
      return;
    }

    try {
      PhoneDisambiguationDialogFragment.show(
          activity.getFragmentManager(),
          phoneList,
          interactionType,
          isVideoCall,
          callSpecificAppData);
    } catch (IllegalStateException e) {
      // ignore to be safe. Shouldn't happen because we checked the
      // activity wasn't destroyed, but to be safe.
      LogUtil.e("PhoneNumberInteraction.showDisambiguationDialog", "caught exception", e);
    }
  }

  /** A model object for capturing a phone number for a given contact. */
  @VisibleForTesting
  /* package */ static class PhoneItem implements Parcelable, Collapsible<PhoneItem> {

    public static final Parcelable.Creator<PhoneItem> CREATOR =
        new Parcelable.Creator<PhoneItem>() {
          @Override
          public PhoneItem createFromParcel(Parcel in) {
            return new PhoneItem(in);
          }

          @Override
          public PhoneItem[] newArray(int size) {
            return new PhoneItem[size];
          }
        };
    long id;
    String phoneNumber;
    String accountType;
    String dataSet;
    long type;
    String label;
    /** {@link Phone#CONTENT_ITEM_TYPE} or {@link SipAddress#CONTENT_ITEM_TYPE}. */
    String mimeType;

    private PhoneItem() {}

    private PhoneItem(Parcel in) {
      this.id = in.readLong();
      this.phoneNumber = in.readString();
      this.accountType = in.readString();
      this.dataSet = in.readString();
      this.type = in.readLong();
      this.label = in.readString();
      this.mimeType = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeLong(id);
      dest.writeString(phoneNumber);
      dest.writeString(accountType);
      dest.writeString(dataSet);
      dest.writeLong(type);
      dest.writeString(label);
      dest.writeString(mimeType);
    }

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void collapseWith(PhoneItem phoneItem) {
      // Just keep the number and id we already have.
    }

    @Override
    public boolean shouldCollapseWith(PhoneItem phoneItem, Context context) {
      return MoreContactUtils.shouldCollapse(
          Phone.CONTENT_ITEM_TYPE, phoneNumber, Phone.CONTENT_ITEM_TYPE, phoneItem.phoneNumber);
    }

    @Override
    public String toString() {
      return phoneNumber;
    }
  }

  /** A list adapter that populates the list of contact's phone numbers. */
  private static class PhoneItemAdapter extends ArrayAdapter<PhoneItem> {

    private final int interactionType;

    PhoneItemAdapter(Context context, List<PhoneItem> list, int interactionType) {
      super(context, R.layout.phone_disambig_item, android.R.id.text2, list);
      this.interactionType = interactionType;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      final View view = super.getView(position, convertView, parent);

      final PhoneItem item = getItem(position);
      Assert.isNotNull(item, "Null item at position: %d", position);
      final TextView typeView = (TextView) view.findViewById(android.R.id.text1);
      CharSequence value =
          ContactDisplayUtils.getLabelForCallOrSms(
              (int) item.type, item.label, interactionType, getContext());

      typeView.setText(value);
      return view;
    }
  }

  /**
   * {@link DialogFragment} used for displaying a dialog with a list of phone numbers of which one
   * will be chosen to make a call or initiate an sms message.
   *
   * <p>It is recommended to use {@link #startInteractionForPhoneCall(TransactionSafeActivity, Uri,
   * boolean, CallSpecificAppData)} instead of directly using this class, as those methods handle
   * one or multiple data cases appropriately.
   *
   * <p>This fragment may only be attached to activities which implement {@link
   * DisambigDialogDismissedListener}.
   */
  @SuppressWarnings("WeakerAccess") // Made public to let the system reach this class
  public static class PhoneDisambiguationDialogFragment extends DialogFragment
      implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {

    private static final String ARG_PHONE_LIST = "phoneList";
    private static final String ARG_INTERACTION_TYPE = "interactionType";
    private static final String ARG_IS_VIDEO_CALL = "is_video_call";

    private int interactionType;
    private ListAdapter phonesAdapter;
    private List<PhoneItem> phoneList;
    private CallSpecificAppData callSpecificAppData;
    private boolean isVideoCall;

    public PhoneDisambiguationDialogFragment() {
      super();
    }

    public static void show(
        FragmentManager fragmentManager,
        ArrayList<PhoneItem> phoneList,
        int interactionType,
        boolean isVideoCall,
        CallSpecificAppData callSpecificAppData) {
      PhoneDisambiguationDialogFragment fragment = new PhoneDisambiguationDialogFragment();
      Bundle bundle = new Bundle();
      bundle.putParcelableArrayList(ARG_PHONE_LIST, phoneList);
      bundle.putInt(ARG_INTERACTION_TYPE, interactionType);
      bundle.putBoolean(ARG_IS_VIDEO_CALL, isVideoCall);
      CallIntentParser.putCallSpecificAppData(bundle, callSpecificAppData);
      fragment.setArguments(bundle);
      fragment.show(fragmentManager, TAG);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      final Activity activity = getActivity();
      Assert.checkState(activity instanceof DisambigDialogDismissedListener);

      phoneList = getArguments().getParcelableArrayList(ARG_PHONE_LIST);
      interactionType = getArguments().getInt(ARG_INTERACTION_TYPE);
      isVideoCall = getArguments().getBoolean(ARG_IS_VIDEO_CALL);
      callSpecificAppData = CallIntentParser.getCallSpecificAppData(getArguments());

      phonesAdapter = new PhoneItemAdapter(activity, phoneList, interactionType);
      final LayoutInflater inflater = activity.getLayoutInflater();
      @SuppressLint("InflateParams") // Allowed since dialog view is not available yet
      final View setPrimaryView = inflater.inflate(R.layout.set_primary_checkbox, null);
      return new AlertDialog.Builder(activity)
          .setAdapter(phonesAdapter, this)
          .setTitle(
              interactionType == ContactDisplayUtils.INTERACTION_SMS
                  ? R.string.sms_disambig_title
                  : R.string.call_disambig_title)
          .setView(setPrimaryView)
          .create();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
      final Activity activity = getActivity();
      if (activity == null) {
        return;
      }
      final AlertDialog alertDialog = (AlertDialog) dialog;
      if (phoneList.size() > which && which >= 0) {
        final PhoneItem phoneItem = phoneList.get(which);
        final CheckBox checkBox = (CheckBox) alertDialog.findViewById(R.id.setPrimary);
        if (checkBox.isChecked()) {
          if (callSpecificAppData.getCallInitiationType() == CallInitiationType.Type.SPEED_DIAL) {
            Logger.get(getContext())
                .logInteraction(
                    InteractionEvent.Type.SPEED_DIAL_SET_DEFAULT_NUMBER_FOR_AMBIGUOUS_CONTACT);
          }

          // Request to mark the data as primary in the background.
          final Intent serviceIntent =
              ContactUpdateService.createSetSuperPrimaryIntent(activity, phoneItem.id);
          activity.startService(serviceIntent);
        }

        PhoneNumberInteraction.performAction(
            activity, phoneItem.phoneNumber, interactionType, isVideoCall, callSpecificAppData);
      } else {
        dialog.dismiss();
      }
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
      super.onDismiss(dialogInterface);
      Activity activity = getActivity();
      if (activity != null) {
        ((DisambigDialogDismissedListener) activity).onDisambigDialogDismissed();
      }
    }
  }
}
