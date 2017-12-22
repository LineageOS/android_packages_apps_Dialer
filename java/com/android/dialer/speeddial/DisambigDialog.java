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

package com.android.dialer.speeddial;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.ArraySet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.duo.DuoComponent;
import com.android.dialer.precall.PreCall;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

/** Disambiguation dialog for favorite contacts in {@link SpeedDialFragment}. */
public class DisambigDialog extends DialogFragment {

  @VisibleForTesting public static final String DISAMBIG_DIALOG_TAG = "disambig_dialog";
  private static final String DISAMBIG_DIALOG_WORKER_TAG = "disambig_dialog_worker";

  private final Set<String> phoneNumbers = new ArraySet<>();
  private LinearLayout container;
  private String lookupKey;

  /** Show a disambiguation dialog for a starred contact without a favorite communication avenue. */
  public static DisambigDialog show(String lookupKey, FragmentManager manager) {
    DisambigDialog dialog = new DisambigDialog();
    dialog.lookupKey = lookupKey;
    dialog.show(manager, DISAMBIG_DIALOG_TAG);
    return dialog;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    LayoutInflater inflater = getActivity().getLayoutInflater();
    View view = inflater.inflate(R.layout.disambig_dialog_layout, null, false);
    container = view.findViewById(R.id.communication_avenue_container);
    return new AlertDialog.Builder(getActivity()).setView(view).create();
  }

  @Override
  public void onResume() {
    super.onResume();
    lookupContactInfo();
  }

  @Override
  public void onPause() {
    super.onPause();
    // TODO(calderwoodra): for simplicity, just dismiss the dialog on configuration change and
    // consider changing this later.
    dismiss();
  }

  private void lookupContactInfo() {
    DialerExecutorComponent.get(getContext())
        .dialerExecutorFactory()
        .createUiTaskBuilder(
            getFragmentManager(),
            DISAMBIG_DIALOG_WORKER_TAG,
            new LookupContactInfoWorker(getContext().getContentResolver()))
        .onSuccess(this::insertOptions)
        .onFailure(this::onLookupFailed)
        .build()
        .executeParallel(lookupKey);
  }

  /**
   * Inflates and inserts the following in the dialog:
   *
   * <ul>
   *   <li>Header for each unique phone number
   *   <li>Clickable video option if the phone number is video reachable (ViLTE, Duo)
   *   <li>Clickable voice option
   * </ul>
   */
  private void insertOptions(Cursor cursor) {
    if (!cursorIsValid(cursor)) {
      dismiss();
      return;
    }

    do {
      String number = cursor.getString(LookupContactInfoWorker.NUMBER_INDEX);
      // TODO(calderwoodra): improve this to include fuzzy matching
      if (phoneNumbers.add(number)) {
        insertOption(
            number,
            getLabel(getContext().getResources(), cursor),
            isVideoReachable(cursor, number));
      }
    } while (cursor.moveToNext());
    cursor.close();
    // TODO(calderwoodra): set max height of the scrollview. Might need to override onMeasure.
  }

  /** Returns true if the given number is ViLTE reachable or Duo reachable. */
  private boolean isVideoReachable(Cursor cursor, String number) {
    boolean isVideoReachable = cursor.getInt(LookupContactInfoWorker.PHONE_PRESENCE_INDEX) == 1;
    if (!isVideoReachable) {
      isVideoReachable = DuoComponent.get(getContext()).getDuo().isReachable(getContext(), number);
    }
    return isVideoReachable;
  }

  /** Inserts a group of options for a specific phone number. */
  private void insertOption(String number, String phoneType, boolean isVideoReachable) {
    View view =
        getActivity()
            .getLayoutInflater()
            .inflate(R.layout.disambig_option_layout, container, false);
    ((TextView) view.findViewById(R.id.phone_type)).setText(phoneType);
    ((TextView) view.findViewById(R.id.phone_number)).setText(number);

    if (isVideoReachable) {
      View videoOption = view.findViewById(R.id.video_call_container);
      videoOption.setOnClickListener(v -> onVideoOptionClicked(number));
      videoOption.setVisibility(View.VISIBLE);
    }
    View voiceOption = view.findViewById(R.id.voice_call_container);
    voiceOption.setOnClickListener(v -> onVoiceOptionClicked(number));
    container.addView(view);
  }

  private void onVideoOptionClicked(String number) {
    // TODO(calderwoodra): save this option if remember is checked
    // TODO(calderwoodra): place a duo call if possible
    PreCall.start(
        getContext(),
        new CallIntentBuilder(number, CallInitiationType.Type.SPEED_DIAL).setIsVideoCall(true));
  }

  private void onVoiceOptionClicked(String number) {
    // TODO(calderwoodra): save this option if remember is checked
    PreCall.start(getContext(), new CallIntentBuilder(number, CallInitiationType.Type.SPEED_DIAL));
  }

  // TODO(calderwoodra): handle CNAP and cequint types.
  // TODO(calderwoodra): unify this into a utility method with CallLogAdapter#getNumberType
  private static String getLabel(Resources resources, Cursor cursor) {
    int numberType = cursor.getInt(LookupContactInfoWorker.PHONE_TYPE_INDEX);
    String numberLabel = cursor.getString(LookupContactInfoWorker.PHONE_LABEL_INDEX);

    // Returns empty label instead of "custom" if the custom label is empty.
    if (numberType == Phone.TYPE_CUSTOM && TextUtils.isEmpty(numberLabel)) {
      return "";
    }
    return (String) Phone.getTypeLabel(resources, numberType, numberLabel);
  }

  // Checks if the cursor is valid and logs an error if there are any issues.
  private static boolean cursorIsValid(Cursor cursor) {
    if (cursor == null) {
      LogUtil.e("DisambigDialog.insertOptions", "cursor null.");
      return false;
    } else if (cursor.isClosed()) {
      LogUtil.e("DisambigDialog.insertOptions", "cursor closed.");
      cursor.close();
      return false;
    } else if (!cursor.moveToFirst()) {
      LogUtil.e("DisambigDialog.insertOptions", "cursor empty.");
      cursor.close();
      return false;
    }
    return true;
  }

  private void onLookupFailed(Throwable throwable) {
    LogUtil.e("DisambigDialog.onLookupFailed", null, throwable);
    insertOptions(null);
  }

  private static class LookupContactInfoWorker implements Worker<String, Cursor> {

    static final int NUMBER_INDEX = 0;
    static final int PHONE_TYPE_INDEX = 1;
    static final int PHONE_LABEL_INDEX = 2;
    static final int PHONE_PRESENCE_INDEX = 3;

    private static final String[] projection =
        new String[] {Phone.NUMBER, Phone.TYPE, Phone.LABEL, Phone.CARRIER_PRESENCE};
    private final ContentResolver resolver;

    LookupContactInfoWorker(ContentResolver resolver) {
      this.resolver = resolver;
    }

    @Nullable
    @Override
    public Cursor doInBackground(@Nullable String lookupKey) throws Throwable {
      if (TextUtils.isEmpty(lookupKey)) {
        LogUtil.e("LookupConctactInfoWorker.doInBackground", "contact id unsest.");
        return null;
      }
      return resolver.query(
          Phone.CONTENT_URI, projection, Phone.LOOKUP_KEY + " = ?", new String[] {lookupKey}, null);
    }
  }

  @VisibleForTesting
  public static String[] getProjectionForTesting() {
    ArrayList<String> projection =
        new ArrayList<>(Arrays.asList(LookupContactInfoWorker.projection));
    projection.add(Phone.LOOKUP_KEY);
    return projection.toArray(new String[projection.size()]);
  }

  @VisibleForTesting
  public LinearLayout getContainer() {
    return container;
  }
}
