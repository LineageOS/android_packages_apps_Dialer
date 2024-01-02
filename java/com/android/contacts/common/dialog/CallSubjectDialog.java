/*
 * Copyright (C) 2015 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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

package com.android.contacts.common.dialog;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.android.dialer.R;
import com.android.dialer.animation.AnimUtils;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.LogUtil;
import com.android.dialer.contactphoto.ContactPhotoManager;
import com.android.dialer.lettertile.LetterTileDrawable;
import com.android.dialer.precall.PreCall;
import com.android.dialer.util.ViewUtil;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements a dialog which prompts for a call subject for an outgoing call. The dialog includes a
 * pop up list of historical call subjects.
 */
public class CallSubjectDialog extends AppCompatActivity {

  public static final String PREF_KEY_SUBJECT_HISTORY_COUNT = "subject_history_count";
  public static final String PREF_KEY_SUBJECT_HISTORY_ITEM = "subject_history_item";
  /** Activity intent argument bundle keys: */
  public static final String ARG_PHOTO_ID = "PHOTO_ID";
  public static final String ARG_PHOTO_URI = "PHOTO_URI";
  public static final String ARG_CONTACT_URI = "CONTACT_URI";
  public static final String ARG_NAME_OR_NUMBER = "NAME_OR_NUMBER";
  public static final String ARG_NUMBER = "NUMBER";
  public static final String ARG_DISPLAY_NUMBER = "DISPLAY_NUMBER";
  public static final String ARG_NUMBER_LABEL = "NUMBER_LABEL";
  public static final String ARG_PHONE_ACCOUNT_HANDLE = "PHONE_ACCOUNT_HANDLE";
  public static final String ARG_CONTACT_TYPE = "CONTACT_TYPE";
  private static final int CALL_SUBJECT_LIMIT = 16;
  private static final int CALL_SUBJECT_HISTORY_SIZE = 5;
  private int mAnimationDuration;
  private Charset mMessageEncoding;
  private View mBackgroundView;
  private View mDialogView;
  private QuickContactBadge mContactPhoto;
  private TextView mNameView;
  private TextView mNumberView;
  private EditText mCallSubjectView;
  private TextView mCharacterLimitView;
  private ListView mSubjectList;

  private int mLimit = CALL_SUBJECT_LIMIT;
  /** Handles changes to the text in the subject box. Ensures the character limit is updated. */
  private final TextWatcher mTextWatcher =
      new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
          // no-op
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
          updateCharacterLimit();
        }

        @Override
        public void afterTextChanged(Editable s) {
          // no-op
        }
      };

  private SharedPreferences mPrefs;
  private List<String> mSubjectHistory;
  /** Handles displaying the list of past call subjects. */
  private final View.OnClickListener mHistoryOnClickListener = v -> {
    hideSoftKeyboard(CallSubjectDialog.this, mCallSubjectView);
    showCallHistory(mSubjectList.getVisibility() == View.GONE);
  };
  /**
   * Handles auto-hiding the call history when user clicks in the call subject field to give it
   * focus.
   */
  private final View.OnClickListener mCallSubjectClickListener = v -> {
    if (mSubjectList.getVisibility() == View.VISIBLE) {
      showCallHistory(false);
    }
  };

  private long mPhotoID;
  private Uri mPhotoUri;
  private Uri mContactUri;
  private String mNameOrNumber;
  private String mNumber;
  private String mDisplayNumber;
  private String mNumberLabel;
  private int mContactType;
  private PhoneAccountHandle mPhoneAccountHandle;
  /** Handles starting a call with a call subject specified. */
  private final View.OnClickListener mSendAndCallOnClickListener = v -> {
    String subject = mCallSubjectView.getText().toString();
    PreCall.start(
            CallSubjectDialog.this,
            new CallIntentBuilder(mNumber, CallInitiationType.Type.CALL_SUBJECT_DIALOG)
                    .setPhoneAccountHandle(mPhoneAccountHandle)
                    .setCallSubject(subject));

    mSubjectHistory.add(subject);
    saveSubjectHistory(mSubjectHistory);
    finish();
  };

  /** Click listener which handles user clicks outside of the dialog. */
  private final View.OnClickListener mBackgroundListener = v -> finish();
  /**
   * Item click listener which handles user clicks on the items in the list view. Dismisses the
   * activity, returning the subject to the caller and closing the activity with the {@link
   * Activity#RESULT_OK} result code.
   */
  private final AdapterView.OnItemClickListener mItemClickListener =
          (arg0, view, position, arg3) -> {
    mCallSubjectView.setText(mSubjectHistory.get(position));
    showCallHistory(false);
  };

  /**
   * Show the call subject dialog given a phone number to dial (e.g. from the dialpad).
   *
   * @param activity The activity.
   * @param number The number to dial.
   */
  public static void start(Activity activity, String number) {
    start(
        activity,
        -1 /* photoId */,
        null /* photoUri */,
        null /* contactUri */,
        number /* nameOrNumber */,
        number /* number */,
        null /* displayNumber */,
        null /* numberLabel */,
        LetterTileDrawable.TYPE_DEFAULT,
        null /* phoneAccountHandle */);
  }

  /**
   * Creates a call subject dialog.
   *
   * @param activity The current activity.
   * @param photoId The photo ID (used to populate contact photo).
   * @param contactUri The Contact URI (used so quick contact can be invoked from contact photo).
   * @param nameOrNumber The name or number of the callee.
   * @param number The raw number to dial.
   * @param displayNumber The number to dial, formatted for display.
   * @param numberLabel The label for the number (if from a contact).
   * @param contactType The contact type according to {@link ContactPhotoManager}.
   * @param phoneAccountHandle The phone account handle.
   */
  public static void start(
      Activity activity,
      long photoId,
      Uri photoUri,
      Uri contactUri,
      String nameOrNumber,
      String number,
      String displayNumber,
      String numberLabel,
      int contactType,
      PhoneAccountHandle phoneAccountHandle) {
    Bundle arguments = new Bundle();
    arguments.putLong(ARG_PHOTO_ID, photoId);
    arguments.putParcelable(ARG_PHOTO_URI, photoUri);
    arguments.putParcelable(ARG_CONTACT_URI, contactUri);
    arguments.putString(ARG_NAME_OR_NUMBER, nameOrNumber);
    arguments.putString(ARG_NUMBER, number);
    arguments.putString(ARG_DISPLAY_NUMBER, displayNumber);
    arguments.putString(ARG_NUMBER_LABEL, numberLabel);
    arguments.putInt(ARG_CONTACT_TYPE, contactType);
    arguments.putParcelable(ARG_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
    start(activity, arguments);
  }

  /**
   * Shows the call subject dialog given a Bundle containing all the arguments required to display
   * the dialog (e.g. from Quick Contacts).
   *
   * @param activity The activity.
   * @param arguments The arguments bundle.
   */
  public static void start(Activity activity, Bundle arguments) {
    Intent intent = new Intent(activity, CallSubjectDialog.class);
    intent.putExtras(arguments);
    activity.startActivity(intent);
  }

  /**
   * Loads the subject history from shared preferences.
   *
   * @param prefs Shared preferences.
   * @return List of subject history strings.
   */
  public static List<String> loadSubjectHistory(SharedPreferences prefs) {
    int historySize = prefs.getInt(PREF_KEY_SUBJECT_HISTORY_COUNT, 0);
    List<String> subjects = new ArrayList(historySize);

    for (int ix = 0; ix < historySize; ix++) {
      String historyItem = prefs.getString(PREF_KEY_SUBJECT_HISTORY_ITEM + ix, null);
      if (!TextUtils.isEmpty(historyItem)) {
        subjects.add(historyItem);
      }
    }

    return subjects;
  }

  /**
   * Creates the dialog, inflating the layout and populating it with the name and phone number.
   *
   * @param savedInstanceState The last saved instance state of the Fragment, or null if this is a
   *     freshly created Fragment.
   * @return Dialog instance.
   */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mAnimationDuration = getResources().getInteger(R.integer.call_subject_animation_duration);
    mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
    readArguments();
    loadConfiguration();
    mSubjectHistory = loadSubjectHistory(mPrefs);

    setContentView(R.layout.dialog_call_subject);
    getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    mBackgroundView = findViewById(R.id.call_subject_dialog);
    mBackgroundView.setOnClickListener(mBackgroundListener);
    mDialogView = findViewById(R.id.dialog_view);
    mContactPhoto = findViewById(R.id.contact_photo);
    mNameView = findViewById(R.id.name);
    mNumberView = findViewById(R.id.number);
    mCallSubjectView = findViewById(R.id.call_subject);
    mCallSubjectView.addTextChangedListener(mTextWatcher);
    mCallSubjectView.setOnClickListener(mCallSubjectClickListener);
    InputFilter[] filters = new InputFilter[1];
    filters[0] = new InputFilter.LengthFilter(mLimit);
    mCallSubjectView.setFilters(filters);
    mCharacterLimitView = findViewById(R.id.character_limit);
    View historyButton = findViewById(R.id.history_button);
    historyButton.setOnClickListener(mHistoryOnClickListener);
    historyButton.setVisibility(mSubjectHistory.isEmpty() ? View.GONE : View.VISIBLE);
    View sendAndCallButton = findViewById(R.id.send_and_call_button);
    sendAndCallButton.setOnClickListener(mSendAndCallOnClickListener);
    mSubjectList = findViewById(R.id.subject_list);
    mSubjectList.setOnItemClickListener(mItemClickListener);
    mSubjectList.setVisibility(View.GONE);

    updateContactInfo();
    updateCharacterLimit();
  }

  /** Populates the contact info fields based on the current contact information. */
  private void updateContactInfo() {
    if (mContactUri != null) {
      ContactPhotoManager.getInstance(this)
          .loadDialerThumbnailOrPhoto(
              mContactPhoto, mContactUri, mPhotoID, mPhotoUri, mNameOrNumber, mContactType);
    } else {
      mContactPhoto.setVisibility(View.GONE);
    }
    mNameView.setText(mNameOrNumber);
    if (!TextUtils.isEmpty(mDisplayNumber)) {
      mNumberView.setVisibility(View.VISIBLE);
      mNumberView.setText(
          TextUtils.isEmpty(mNumberLabel)
              ? mDisplayNumber
              : getString(R.string.old_call_subject_type_and_number, mNumberLabel, mDisplayNumber));
    } else {
      mNumberView.setVisibility(View.GONE);
      mNumberView.setText(null);
    }
  }

  /** Reads arguments from the fragment arguments and populates the necessary instance variables. */
  private void readArguments() {
    Bundle arguments = getIntent().getExtras();
    if (arguments == null) {
      LogUtil.e("CallSubjectDialog.readArguments", "arguments cannot be null");
      return;
    }
    mPhotoID = arguments.getLong(ARG_PHOTO_ID);
    mPhotoUri = arguments.getParcelable(ARG_PHOTO_URI, Uri.class);
    mContactUri = arguments.getParcelable(ARG_CONTACT_URI, Uri.class);
    mNameOrNumber = arguments.getString(ARG_NAME_OR_NUMBER);
    mNumber = arguments.getString(ARG_NUMBER);
    mDisplayNumber = arguments.getString(ARG_DISPLAY_NUMBER);
    mNumberLabel = arguments.getString(ARG_NUMBER_LABEL);
    mContactType = arguments.getInt(ARG_CONTACT_TYPE, LetterTileDrawable.TYPE_DEFAULT);
    mPhoneAccountHandle = arguments.getParcelable(ARG_PHONE_ACCOUNT_HANDLE,
            PhoneAccountHandle.class);
  }

  /**
   * Updates the character limit display, coloring the text RED when the limit is reached or
   * exceeded.
   */
  private void updateCharacterLimit() {
    String subjectText = mCallSubjectView.getText().toString();
    final int length;

    // If a message encoding is specified, use that to count bytes in the message.
    if (mMessageEncoding != null) {
      length = subjectText.getBytes(mMessageEncoding).length;
    } else {
      // No message encoding specified, so just count characters entered.
      length = subjectText.length();
    }

    mCharacterLimitView.setText(getString(R.string.call_subject_limit,
            String.valueOf(length), String.valueOf(mLimit)));
    if (length >= mLimit) {
      mCharacterLimitView.setTextColor(
          getResources().getColor(R.color.call_subject_limit_exceeded, getTheme()));
    } else {
      mCharacterLimitView.setTextColor(
          getResources().getColor(R.color.dialer_secondary_text_color, getTheme()));
    }
  }

  /**
   * Saves the subject history list to shared prefs, removing older items so that there are only
   * {@link #CALL_SUBJECT_HISTORY_SIZE} items at most.
   *
   * @param history The history.
   */
  private void saveSubjectHistory(List<String> history) {
    // Remove oldest subject(s).
    while (history.size() > CALL_SUBJECT_HISTORY_SIZE) {
      history.remove(0);
    }

    SharedPreferences.Editor editor = mPrefs.edit();
    int historyCount = 0;
    for (String subject : history) {
      if (!TextUtils.isEmpty(subject)) {
        editor.putString(PREF_KEY_SUBJECT_HISTORY_ITEM + historyCount, subject);
        historyCount++;
      }
    }
    editor.putInt(PREF_KEY_SUBJECT_HISTORY_COUNT, historyCount);
    editor.apply();
  }

  /** Hide software keyboard for the given {@link View}. */
  public void hideSoftKeyboard(Context context, View view) {
    InputMethodManager imm =
        (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
    }
  }

  /**
   * Hides or shows the call history list.
   *
   * @param show {@code true} if the call history should be shown, {@code false} otherwise.
   */
  private void showCallHistory(final boolean show) {
    // Bail early if the visibility has not changed.
    if ((show && mSubjectList.getVisibility() == View.VISIBLE)
        || (!show && mSubjectList.getVisibility() == View.GONE)) {
      return;
    }

    final int dialogStartingBottom = mDialogView.getBottom();
    if (show) {
      // Showing the subject list; bind the list of history items to the list and show it.
      ArrayAdapter<String> adapter = new ArrayAdapter<>(CallSubjectDialog.this,
              R.layout.call_subject_history_list_item, mSubjectHistory);
      mSubjectList.setAdapter(adapter);
      mSubjectList.setVisibility(View.VISIBLE);
    } else {
      // Hiding the subject list.
      mSubjectList.setVisibility(View.GONE);
    }

    // Use a ViewTreeObserver so that we can animate between the pre-layout and post-layout
    // states.
    ViewUtil.doOnPreDraw(
        mBackgroundView,
        true,
        new Runnable() {
          @Override
          public void run() {
            // Determine the amount the dialog has shifted due to the relayout.
            int shiftAmount = dialogStartingBottom - mDialogView.getBottom();

            // If the dialog needs to be shifted, do that now.
            if (shiftAmount != 0) {
              // Start animation in translated state and animate to translationY 0.
              mDialogView.setTranslationY(shiftAmount);
              mDialogView
                  .animate()
                  .translationY(0)
                  .setInterpolator(AnimUtils.EASE_OUT_EASE_IN)
                  .setDuration(mAnimationDuration)
                  .start();
            }

            if (show) {
              // Show the subject list.
              mSubjectList.setTranslationY(mSubjectList.getHeight());

              mSubjectList
                  .animate()
                  .translationY(0)
                  .setInterpolator(AnimUtils.EASE_OUT_EASE_IN)
                  .setDuration(mAnimationDuration)
                  .setListener(
                      new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                          super.onAnimationEnd(animation);
                        }

                        @Override
                        public void onAnimationStart(Animator animation) {
                          super.onAnimationStart(animation);
                          mSubjectList.setVisibility(View.VISIBLE);
                        }
                      })
                  .start();
            } else {
              // Hide the subject list.
              mSubjectList.setTranslationY(0);

              mSubjectList
                  .animate()
                  .translationY(mSubjectList.getHeight())
                  .setInterpolator(AnimUtils.EASE_OUT_EASE_IN)
                  .setDuration(mAnimationDuration)
                  .setListener(
                      new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                          super.onAnimationEnd(animation);
                          mSubjectList.setVisibility(View.GONE);
                        }

                        @Override
                        public void onAnimationStart(Animator animation) {
                          super.onAnimationStart(animation);
                        }
                      })
                  .start();
            }
          }
        });
  }

  /**
   * Loads the message encoding and maximum message length from the phone account extras for the
   * current phone account.
   */
  private void loadConfiguration() {
    if (mPhoneAccountHandle == null) {
      return;
    }

    TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
    final PhoneAccount account = telecomManager.getPhoneAccount(mPhoneAccountHandle);
    if (account == null) {
      return;
    }

    Bundle phoneAccountExtras = account.getExtras();
    if (phoneAccountExtras == null) {
      return;
    }

    // Get limit, if provided; otherwise default to existing value.
    mLimit = phoneAccountExtras.getInt(PhoneAccount.EXTRA_CALL_SUBJECT_MAX_LENGTH, mLimit);

    // Get charset; default to none (e.g. count characters 1:1).
    String charsetName =
        phoneAccountExtras.getString(PhoneAccount.EXTRA_CALL_SUBJECT_CHARACTER_ENCODING);

    if (!TextUtils.isEmpty(charsetName)) {
      try {
        mMessageEncoding = Charset.forName(charsetName);
      } catch (java.nio.charset.UnsupportedCharsetException uce) {
        // Character set was invalid; log warning and fallback to none.
        LogUtil.e("CallSubjectDialog.loadConfiguration", "invalid charset: " + charsetName);
        mMessageEncoding = null;
      }
    } else {
      // No character set specified, so count characters 1:1.
      mMessageEncoding = null;
    }
  }
}
