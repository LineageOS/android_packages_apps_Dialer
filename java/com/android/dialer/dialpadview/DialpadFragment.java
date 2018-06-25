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

package com.android.dialer.dialpadview;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.Trace;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.Contacts.PhonesColumns;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.design.widget.FloatingActionButton;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.Selection;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.android.contacts.common.dialog.CallSubjectDialog;
import com.android.contacts.common.util.StopWatch;
import com.android.dialer.animation.AnimUtils;
import com.android.dialer.animation.AnimUtils.AnimationCallback;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.Assert;
import com.android.dialer.common.FragmentUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.location.GeoUtil;
import com.android.dialer.logging.UiAction;
import com.android.dialer.oem.MotorolaUtils;
import com.android.dialer.performancereport.PerformanceReport;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.precall.PreCall;
import com.android.dialer.proguard.UsedByReflection;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.CallUtil;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.util.ViewUtil;
import com.android.dialer.widget.FloatingActionButtonController;
import com.google.common.base.Ascii;
import com.google.common.base.Optional;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fragment that displays a twelve-key phone dialpad. */
public class DialpadFragment extends Fragment
    implements View.OnClickListener,
        View.OnLongClickListener,
        View.OnKeyListener,
        AdapterView.OnItemClickListener,
        TextWatcher,
        PopupMenu.OnMenuItemClickListener,
        DialpadKeyButton.OnPressedListener {

  private static final String TAG = "DialpadFragment";
  private static final String EMPTY_NUMBER = "";
  private static final char PAUSE = ',';
  private static final char WAIT = ';';
  /** The length of DTMF tones in milliseconds */
  private static final int TONE_LENGTH_MS = 150;

  private static final int TONE_LENGTH_INFINITE = -1;
  /** The DTMF tone volume relative to other sounds in the stream */
  private static final int TONE_RELATIVE_VOLUME = 80;
  /** Stream type used to play the DTMF tones off call, and mapped to the volume control keys */
  private static final int DIAL_TONE_STREAM_TYPE = AudioManager.STREAM_DTMF;
  /** Identifier for the "Add Call" intent extra. */
  private static final String ADD_CALL_MODE_KEY = "add_call_mode";
  /**
   * Identifier for intent extra for sending an empty Flash message for CDMA networks. This message
   * is used by the network to simulate a press/depress of the "hookswitch" of a landline phone. Aka
   * "empty flash".
   *
   * <p>TODO: Using an intent extra to tell the phone to send this flash is a temporary measure. To
   * be replaced with an Telephony/TelecomManager call in the future. TODO: Keep in sync with the
   * string defined in OutgoingCallBroadcaster.java in Phone app until this is replaced with the
   * Telephony/Telecom API.
   */
  private static final String EXTRA_SEND_EMPTY_FLASH = "com.android.phone.extra.SEND_EMPTY_FLASH";

  private static final String PREF_DIGITS_FILLED_BY_INTENT = "pref_digits_filled_by_intent";
  private static final String PREF_IS_DIALPAD_SLIDE_OUT = "pref_is_dialpad_slide_out";

  /**
   * Hidden key in carrier config to determine if no emergency call over wifi warning is required.
   *
   * <p>"Time delay (in ms) after which we show the notification for emergency calls, while the
   * device is registered over WFC. Default value is -1, which indicates that this notification is
   * not pertinent for a particular carrier. We've added a delay to prevent false positives."
   */
  @VisibleForTesting
  static final String KEY_EMERGENCY_NOTIFICATION_DELAY_INT = "emergency_notification_delay_int";

  private static Optional<String> currentCountryIsoForTesting = Optional.absent();
  private static Boolean showEmergencyCallWarningForTest = null;

  private final Object toneGeneratorLock = new Object();
  /** Set of dialpad keys that are currently being pressed */
  private final HashSet<View> pressedDialpadKeys = new HashSet<>(12);

  private OnDialpadQueryChangedListener dialpadQueryListener;
  private DialpadView dialpadView;
  private EditText digits;
  private TextView digitsHint;
  private int dialpadSlideInDuration;
  /** Remembers if we need to clear digits field when the screen is completely gone. */
  private boolean clearDigitsOnStop;

  private View overflowMenuButton;
  private PopupMenu overflowPopupMenu;
  private View delete;
  private ToneGenerator toneGenerator;
  private FloatingActionButtonController floatingActionButtonController;
  private FloatingActionButton floatingActionButton;
  private ListView dialpadChooser;
  private DialpadChooserAdapter dialpadChooserAdapter;
  /** Regular expression prohibiting manual phone call. Can be empty, which means "no rule". */
  private String prohibitedPhoneNumberRegexp;

  private PseudoEmergencyAnimator pseudoEmergencyAnimator;
  private String lastNumberDialed = EMPTY_NUMBER;

  // determines if we want to playback local DTMF tones.
  private boolean dTMFToneEnabled;
  private CallStateReceiver callStateReceiver;
  private boolean wasEmptyBeforeTextChange;
  /**
   * This field is set to true while processing an incoming DIAL intent, in order to make sure that
   * SpecialCharSequenceMgr actions can be triggered by user input but *not* by a tel: URI passed by
   * some other app. It will be set to false when all digits are cleared.
   */
  private boolean digitsFilledByIntent;

  private boolean startedFromNewIntent = false;
  private boolean firstLaunch = false;
  private boolean animate = false;

  private boolean isLayoutRtl;
  private boolean isLandscape;

  private PhoneAccountHandle selectedAccount;

  private DialerExecutor<String> initPhoneNumberFormattingTextWatcherExecutor;
  private boolean isDialpadSlideUp;

  /**
   * Determines whether an add call operation is requested.
   *
   * @param intent The intent.
   * @return {@literal true} if add call operation was requested. {@literal false} otherwise.
   */
  public static boolean isAddCallMode(Intent intent) {
    if (intent == null) {
      return false;
    }
    final String action = intent.getAction();
    if (Intent.ACTION_DIAL.equals(action) || Intent.ACTION_VIEW.equals(action)) {
      // see if we are "adding a call" from the InCallScreen; false by default.
      return intent.getBooleanExtra(ADD_CALL_MODE_KEY, false);
    } else {
      return false;
    }
  }

  /**
   * Format the provided string of digits into one that represents a properly formatted phone
   * number.
   *
   * @param dialString String of characters to format
   * @param normalizedNumber the E164 format number whose country code is used if the given
   *     phoneNumber doesn't have the country code.
   * @param countryIso The country code representing the format to use if the provided normalized
   *     number is null or invalid.
   * @return the provided string of digits as a formatted phone number, retaining any post-dial
   *     portion of the string.
   */
  String getFormattedDigits(String dialString, String normalizedNumber, String countryIso) {
    String number = PhoneNumberUtils.extractNetworkPortion(dialString);
    // Also retrieve the post dial portion of the provided data, so that the entire dial
    // string can be reconstituted later.
    final String postDial = PhoneNumberUtils.extractPostDialPortion(dialString);

    if (TextUtils.isEmpty(number)) {
      return postDial;
    }

    number = PhoneNumberHelper.formatNumber(getContext(), number, normalizedNumber, countryIso);

    if (TextUtils.isEmpty(postDial)) {
      return number;
    }

    return number.concat(postDial);
  }

  /**
   * Returns true of the newDigit parameter can be added at the current selection point, otherwise
   * returns false. Only prevents input of WAIT and PAUSE digits at an unsupported position. Fails
   * early if start == -1 or start is larger than end.
   */
  @VisibleForTesting
  /* package */ static boolean canAddDigit(CharSequence digits, int start, int end, char newDigit) {
    if (newDigit != WAIT && newDigit != PAUSE) {
      throw new IllegalArgumentException(
          "Should not be called for anything other than PAUSE & WAIT");
    }

    // False if no selection, or selection is reversed (end < start)
    if (start == -1 || end < start) {
      return false;
    }

    // unsupported selection-out-of-bounds state
    if (start > digits.length() || end > digits.length()) {
      return false;
    }

    // Special digit cannot be the first digit
    if (start == 0) {
      return false;
    }

    if (newDigit == WAIT) {
      // preceding char is ';' (WAIT)
      if (digits.charAt(start - 1) == WAIT) {
        return false;
      }

      // next char is ';' (WAIT)
      if ((digits.length() > end) && (digits.charAt(end) == WAIT)) {
        return false;
      }
    }

    return true;
  }

  private TelephonyManager getTelephonyManager() {
    return (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
  }

  @Override
  public Context getContext() {
    return getActivity();
  }

  @Override
  public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    wasEmptyBeforeTextChange = TextUtils.isEmpty(s);
  }

  @Override
  public void onTextChanged(CharSequence input, int start, int before, int changeCount) {
    if (wasEmptyBeforeTextChange != TextUtils.isEmpty(input)) {
      final Activity activity = getActivity();
      if (activity != null) {
        activity.invalidateOptionsMenu();
        updateMenuOverflowButton(wasEmptyBeforeTextChange);
      }
      updateDialpadHint();
    }

    // DTMF Tones do not need to be played here any longer -
    // the DTMF dialer handles that functionality now.
  }

  @Override
  public void afterTextChanged(Editable input) {
    // When DTMF dialpad buttons are being pressed, we delay SpecialCharSequenceMgr sequence,
    // since some of SpecialCharSequenceMgr's behavior is too abrupt for the "touch-down"
    // behavior.
    if (!digitsFilledByIntent
        && SpecialCharSequenceMgr.handleChars(getActivity(), input.toString(), digits)) {
      // A special sequence was entered, clear the digits
      digits.getText().clear();
    }

    if (isDigitsEmpty()) {
      digitsFilledByIntent = false;
      digits.setCursorVisible(false);
    }

    if (dialpadQueryListener != null) {
      dialpadQueryListener.onDialpadQueryChanged(digits.getText().toString());
    }

    updateDeleteButtonEnabledState();
  }

  @Override
  public void onCreate(Bundle state) {
    Trace.beginSection(TAG + " onCreate");
    LogUtil.enterBlock("DialpadFragment.onCreate");
    super.onCreate(state);

    firstLaunch = state == null;

    prohibitedPhoneNumberRegexp =
        getResources().getString(R.string.config_prohibited_phone_number_regexp);

    if (state != null) {
      digitsFilledByIntent = state.getBoolean(PREF_DIGITS_FILLED_BY_INTENT);
      isDialpadSlideUp = state.getBoolean(PREF_IS_DIALPAD_SLIDE_OUT);
    }

    dialpadSlideInDuration = getResources().getInteger(R.integer.dialpad_slide_in_duration);

    if (callStateReceiver == null) {
      IntentFilter callStateIntentFilter =
          new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
      callStateReceiver = new CallStateReceiver();
      getActivity().registerReceiver(callStateReceiver, callStateIntentFilter);
    }

    initPhoneNumberFormattingTextWatcherExecutor =
        DialerExecutorComponent.get(getContext())
            .dialerExecutorFactory()
            .createUiTaskBuilder(
                getFragmentManager(),
                "DialpadFragment.initPhoneNumberFormattingTextWatcher",
                new InitPhoneNumberFormattingTextWatcherWorker())
            .onSuccess(watcher -> dialpadView.getDigits().addTextChangedListener(watcher))
            .build();
    Trace.endSection();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
    Trace.beginSection(TAG + " onCreateView");
    LogUtil.enterBlock("DialpadFragment.onCreateView");
    Trace.beginSection(TAG + " inflate view");
    View fragmentView = inflater.inflate(R.layout.dialpad_fragment, container, false);
    Trace.endSection();
    Trace.beginSection(TAG + " buildLayer");
    fragmentView.buildLayer();
    Trace.endSection();

    Trace.beginSection(TAG + " setup views");

    dialpadView = fragmentView.findViewById(R.id.dialpad_view);
    dialpadView.setCanDigitsBeEdited(true);
    digits = dialpadView.getDigits();
    digitsHint = dialpadView.getDigitsHint();
    digits.setKeyListener(UnicodeDialerKeyListener.INSTANCE);
    digits.setOnClickListener(this);
    digits.setOnKeyListener(this);
    digits.setOnLongClickListener(this);
    digits.addTextChangedListener(this);
    digits.setElegantTextHeight(false);

    if (!MotorolaUtils.shouldDisablePhoneNumberFormatting(getContext())) {
      initPhoneNumberFormattingTextWatcherExecutor.executeSerial(getCurrentCountryIso());
    }

    // Check for the presence of the keypad
    View oneButton = fragmentView.findViewById(R.id.one);
    if (oneButton != null) {
      configureKeypadListeners(fragmentView);
    }

    delete = dialpadView.getDeleteButton();

    if (delete != null) {
      delete.setOnClickListener(this);
      delete.setOnLongClickListener(this);
    }

    fragmentView
        .findViewById(R.id.spacer)
        .setOnTouchListener(
            (v, event) -> {
              if (isDigitsEmpty()) {
                if (getActivity() != null) {
                  LogUtil.i("DialpadFragment.onCreateView", "dialpad spacer touched");
                  return FragmentUtils.getParentUnsafe(this, HostInterface.class)
                      .onDialpadSpacerTouchWithEmptyQuery();
                }
                return true;
              }
              return false;
            });

    digits.setCursorVisible(false);

    // Set up the "dialpad chooser" UI; see showDialpadChooser().
    dialpadChooser = fragmentView.findViewById(R.id.dialpadChooser);
    dialpadChooser.setOnItemClickListener(this);

    floatingActionButton = fragmentView.findViewById(R.id.dialpad_floating_action_button);
    floatingActionButton.setOnClickListener(this);
    floatingActionButtonController =
        new FloatingActionButtonController(getActivity(), floatingActionButton);
    Trace.endSection();
    Trace.endSection();
    return fragmentView;
  }

  /**
   * The dialpad hint is a TextView overlaid above the digit EditText. {@link EditText#setHint(int)}
   * is not used because the digits has auto resize and makes setting the size of the hint
   * difficult.
   */
  private void updateDialpadHint() {
    if (!TextUtils.isEmpty(digits.getText())) {
      digitsHint.setVisibility(View.GONE);
      return;
    }

    if (shouldShowEmergencyCallWarning(getContext())) {
      String hint = getContext().getString(R.string.dialpad_hint_emergency_calling_not_available);
      digits.setContentDescription(hint);
      digitsHint.setText(hint);
      digitsHint.setVisibility(View.VISIBLE);
      return;
    }
    digits.setContentDescription(null);

    digitsHint.setVisibility(View.GONE);
  }

  /**
   * Only show the "emergency call not available" warning when on wifi call and carrier requires it.
   *
   * <p>internal method tested because the conditions cannot be setup in espresso, and the layout
   * cannot be inflated in robolectric.
   */
  @SuppressWarnings("missingPermission")
  @TargetApi(VERSION_CODES.O)
  @VisibleForTesting
  static boolean shouldShowEmergencyCallWarning(Context context) {
    if (showEmergencyCallWarningForTest != null) {
      return showEmergencyCallWarningForTest;
    }
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return false;
    }
    if (!PermissionsUtil.hasReadPhoneStatePermissions(context)) {
      return false;
    }
    TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
    PersistableBundle config = telephonyManager.getCarrierConfig();
    // A delay of -1 means wifi emergency call is available/the warning is not required.
    if (config == null || config.getInt(KEY_EMERGENCY_NOTIFICATION_DELAY_INT, -1) == -1) {
      return false;
    }

    // TelephonyManager.getVoiceNetworkType() Doesn't always return NETWORK_TYPE_IWLAN when on wifi.
    // other wifi calling checks are hidden API. Emergency calling is not available without service
    // regardless of the wifi state so this check is omitted.

    switch (telephonyManager.getServiceState().getState()) {
      case ServiceState.STATE_OUT_OF_SERVICE:
      case ServiceState.STATE_POWER_OFF:
        return true;
      case ServiceState.STATE_EMERGENCY_ONLY:
      case ServiceState.STATE_IN_SERVICE:
        return false;
      default:
        throw new AssertionError("unknown state " + telephonyManager.getServiceState().getState());
    }
  }

  @VisibleForTesting
  static void setShowEmergencyCallWarningForTest(Boolean value) {
    showEmergencyCallWarningForTest = value;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    isLayoutRtl = ViewUtil.isRtl();
    isLandscape =
        getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
  }

  private String getCurrentCountryIso() {
    if (currentCountryIsoForTesting.isPresent()) {
      return currentCountryIsoForTesting.get();
    }

    return GeoUtil.getCurrentCountryIso(getActivity());
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  public static void setCurrentCountryIsoForTesting(String countryCode) {
    currentCountryIsoForTesting = Optional.of(countryCode);
  }

  private boolean isLayoutReady() {
    return digits != null;
  }

  public EditText getDigitsWidget() {
    return digits;
  }

  /** @return true when {@link #digits} is actually filled by the Intent. */
  private boolean fillDigitsIfNecessary(Intent intent) {
    // Only fills digits from an intent if it is a new intent.
    // Otherwise falls back to the previously used number.
    if (!firstLaunch && !startedFromNewIntent) {
      return false;
    }

    final String action = intent.getAction();
    if (Intent.ACTION_DIAL.equals(action) || Intent.ACTION_VIEW.equals(action)) {
      Uri uri = intent.getData();
      if (uri != null) {
        if (PhoneAccount.SCHEME_TEL.equals(uri.getScheme())) {
          // Put the requested number into the input area
          String data = uri.getSchemeSpecificPart();
          // Remember it is filled via Intent.
          digitsFilledByIntent = true;
          final String converted =
              PhoneNumberUtils.convertKeypadLettersToDigits(
                  PhoneNumberUtils.replaceUnicodeDigits(data));
          setFormattedDigits(converted, null);
          return true;
        } else {
          if (!PermissionsUtil.hasContactsReadPermissions(getActivity())) {
            return false;
          }
          String type = intent.getType();
          if (People.CONTENT_ITEM_TYPE.equals(type) || Phones.CONTENT_ITEM_TYPE.equals(type)) {
            // Query the phone number
            Cursor c =
                getActivity()
                    .getContentResolver()
                    .query(
                        intent.getData(),
                        new String[] {PhonesColumns.NUMBER, PhonesColumns.NUMBER_KEY},
                        null,
                        null,
                        null);
            if (c != null) {
              try {
                if (c.moveToFirst()) {
                  // Remember it is filled via Intent.
                  digitsFilledByIntent = true;
                  // Put the number into the input area
                  setFormattedDigits(c.getString(0), c.getString(1));
                  return true;
                }
              } finally {
                c.close();
              }
            }
          }
        }
      }
    }
    return false;
  }

  /**
   * Checks the given Intent and changes dialpad's UI state.
   *
   * <p>There are three modes:
   *
   * <ul>
   *   <li>Empty Dialpad shown via "Add Call" in the in call ui
   *   <li>Dialpad (digits filled), shown by {@link Intent#ACTION_DIAL} with a number.
   *   <li>Return to call view, shown when a call is ongoing without {@link Intent#ACTION_DIAL}
   * </ul>
   *
   * For example, if the user...
   *
   * <ul>
   *   <li>clicks a number in gmail, this method will show the dialpad filled with the number,
   *       regardless of whether a call is ongoing.
   *   <li>places a call, presses home and opens dialer, this method will show the return to call
   *       prompt to confirm what they want to do.
   * </ul>
   */
  private void configureScreenFromIntent(@NonNull Intent intent) {
    LogUtil.i("DialpadFragment.configureScreenFromIntent", "action: %s", intent.getAction());
    if (!isLayoutReady()) {
      // This happens typically when parent's Activity#onNewIntent() is called while
      // Fragment#onCreateView() isn't called yet, and thus we cannot configure Views at
      // this point. onViewCreate() should call this method after preparing layouts, so
      // just ignore this call now.
      LogUtil.i(
          "DialpadFragment.configureScreenFromIntent",
          "Screen configuration is requested before onCreateView() is called. Ignored");
      return;
    }

    // If "Add call" was selected, show the dialpad instead of the dialpad chooser prompt
    if (isAddCallMode(intent)) {
      LogUtil.i("DialpadFragment.configureScreenFromIntent", "Add call mode");
      showDialpadChooser(false);
      setStartedFromNewIntent(true);
      return;
    }

    // Don't show the chooser when called via onNewIntent() and phone number is present.
    // i.e. User clicks a telephone link from gmail for example.
    // In this case, we want to show the dialpad with the phone number.
    boolean digitsFilled = fillDigitsIfNecessary(intent);
    if (!(startedFromNewIntent && digitsFilled) && isPhoneInUse()) {
      // If there's already an active call, bring up an intermediate UI to
      // make the user confirm what they really want to do.
      LogUtil.i("DialpadFragment.configureScreenFromIntent", "Dialpad chooser mode");
      showDialpadChooser(true);
      setStartedFromNewIntent(false);
      return;
    }

    LogUtil.i("DialpadFragment.configureScreenFromIntent", "Nothing to show");
    showDialpadChooser(false);
    setStartedFromNewIntent(false);
  }

  public void setStartedFromNewIntent(boolean value) {
    startedFromNewIntent = value;
  }

  public void clearCallRateInformation() {
    setCallRateInformation(null, null);
  }

  public void setCallRateInformation(String countryName, String displayRate) {
    dialpadView.setCallRateInformation(countryName, displayRate);
  }

  /** Sets formatted digits to digits field. */
  private void setFormattedDigits(String data, String normalizedNumber) {
    final String formatted = getFormattedDigits(data, normalizedNumber, getCurrentCountryIso());
    if (!TextUtils.isEmpty(formatted)) {
      Editable digits = this.digits.getText();
      digits.replace(0, digits.length(), formatted);
      // for some reason this isn't getting called in the digits.replace call above..
      // but in any case, this will make sure the background drawable looks right
      afterTextChanged(digits);
    }
  }

  private void configureKeypadListeners(View fragmentView) {
    final int[] buttonIds =
        new int[] {
          R.id.one,
          R.id.two,
          R.id.three,
          R.id.four,
          R.id.five,
          R.id.six,
          R.id.seven,
          R.id.eight,
          R.id.nine,
          R.id.star,
          R.id.zero,
          R.id.pound
        };

    DialpadKeyButton dialpadKey;

    for (int buttonId : buttonIds) {
      dialpadKey = fragmentView.findViewById(buttonId);
      dialpadKey.setOnPressedListener(this);
    }

    // Long-pressing one button will initiate Voicemail.
    final DialpadKeyButton one = fragmentView.findViewById(R.id.one);
    one.setOnLongClickListener(this);

    // Long-pressing zero button will enter '+' instead.
    final DialpadKeyButton zero = fragmentView.findViewById(R.id.zero);
    zero.setOnLongClickListener(this);
  }

  @Override
  public void onStart() {
    LogUtil.i("DialpadFragment.onStart", "first launch: %b", firstLaunch);
    Trace.beginSection(TAG + " onStart");
    super.onStart();
    Resources res = getResources();
    int iconId = R.drawable.quantum_ic_call_vd_theme_24;
    if (MotorolaUtils.isWifiCallingAvailable(getContext())) {
      iconId = R.drawable.ic_wifi_calling;
    }
    floatingActionButtonController.changeIcon(
        getContext(), iconId, res.getString(R.string.description_dial_button));

    // if the mToneGenerator creation fails, just continue without it.  It is
    // a local audio signal, and is not as important as the dtmf tone itself.
    final long start = System.currentTimeMillis();
    synchronized (toneGeneratorLock) {
      if (toneGenerator == null) {
        try {
          toneGenerator = new ToneGenerator(DIAL_TONE_STREAM_TYPE, TONE_RELATIVE_VOLUME);
        } catch (RuntimeException e) {
          LogUtil.e(
              "DialpadFragment.onStart",
              "Exception caught while creating local tone generator: " + e);
          toneGenerator = null;
        }
      }
    }
    final long total = System.currentTimeMillis() - start;
    if (total > 50) {
      LogUtil.i("DialpadFragment.onStart", "Time for ToneGenerator creation: " + total);
    }
    Trace.endSection();
  }

  @Override
  public void onResume() {
    LogUtil.enterBlock("DialpadFragment.onResume");
    Trace.beginSection(TAG + " onResume");
    super.onResume();

    dialpadQueryListener = FragmentUtils.getParentUnsafe(this, OnDialpadQueryChangedListener.class);

    final StopWatch stopWatch = StopWatch.start("Dialpad.onResume");

    // Query the last dialed number. Do it first because hitting
    // the DB is 'slow'. This call is asynchronous.
    queryLastOutgoingCall();

    stopWatch.lap("qloc");

    final ContentResolver contentResolver = getActivity().getContentResolver();

    // retrieve the DTMF tone play back setting.
    dTMFToneEnabled =
        Settings.System.getInt(contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1;

    stopWatch.lap("dtwd");

    stopWatch.lap("hptc");

    pressedDialpadKeys.clear();

    configureScreenFromIntent(getActivity().getIntent());

    stopWatch.lap("fdin");

    if (!isPhoneInUse()) {
      LogUtil.i("DialpadFragment.onResume", "phone not in use");
      // A sanity-check: the "dialpad chooser" UI should not be visible if the phone is idle.
      showDialpadChooser(false);
    }

    stopWatch.lap("hnt");

    updateDeleteButtonEnabledState();

    stopWatch.lap("bes");

    stopWatch.stopAndLog(TAG, 50);

    // Populate the overflow menu in onResume instead of onCreate, so that if the SMS activity
    // is disabled while Dialer is paused, the "Send a text message" option can be correctly
    // removed when resumed.
    overflowMenuButton = dialpadView.getOverflowMenuButton();
    overflowPopupMenu = buildOptionsMenu(overflowMenuButton);
    overflowMenuButton.setOnTouchListener(overflowPopupMenu.getDragToOpenListener());
    overflowMenuButton.setOnClickListener(this);
    overflowMenuButton.setVisibility(isDigitsEmpty() ? View.INVISIBLE : View.VISIBLE);

    updateDialpadHint();

    if (firstLaunch) {
      // The onHiddenChanged callback does not get called the first time the fragment is
      // attached, so call it ourselves here.
      onHiddenChanged(false);
    }

    firstLaunch = false;
    Trace.endSection();
  }

  @Override
  public void onPause() {
    super.onPause();

    // Make sure we don't leave this activity with a tone still playing.
    stopTone();
    pressedDialpadKeys.clear();

    // TODO: I wonder if we should not check if the AsyncTask that
    // lookup the last dialed number has completed.
    lastNumberDialed = EMPTY_NUMBER; // Since we are going to query again, free stale number.

    SpecialCharSequenceMgr.cleanup();
    overflowPopupMenu.dismiss();
  }

  @Override
  public void onStop() {
    LogUtil.enterBlock("DialpadFragment.onStop");
    super.onStop();

    floatingActionButtonController.scaleOut();
    synchronized (toneGeneratorLock) {
      if (toneGenerator != null) {
        toneGenerator.release();
        toneGenerator = null;
      }
    }

    if (clearDigitsOnStop) {
      clearDigitsOnStop = false;
      clearDialpad();
    }
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean(PREF_DIGITS_FILLED_BY_INTENT, digitsFilledByIntent);
    outState.putBoolean(PREF_IS_DIALPAD_SLIDE_OUT, isDialpadSlideUp);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (pseudoEmergencyAnimator != null) {
      pseudoEmergencyAnimator.destroy();
      pseudoEmergencyAnimator = null;
    }
    getActivity().unregisterReceiver(callStateReceiver);
  }

  private void keyPressed(int keyCode) {
    if (getView() == null || getView().getTranslationY() != 0) {
      return;
    }
    switch (keyCode) {
      case KeyEvent.KEYCODE_1:
        playTone(ToneGenerator.TONE_DTMF_1, TONE_LENGTH_INFINITE);
        break;
      case KeyEvent.KEYCODE_2:
        playTone(ToneGenerator.TONE_DTMF_2, TONE_LENGTH_INFINITE);
        break;
      case KeyEvent.KEYCODE_3:
        playTone(ToneGenerator.TONE_DTMF_3, TONE_LENGTH_INFINITE);
        break;
      case KeyEvent.KEYCODE_4:
        playTone(ToneGenerator.TONE_DTMF_4, TONE_LENGTH_INFINITE);
        break;
      case KeyEvent.KEYCODE_5:
        playTone(ToneGenerator.TONE_DTMF_5, TONE_LENGTH_INFINITE);
        break;
      case KeyEvent.KEYCODE_6:
        playTone(ToneGenerator.TONE_DTMF_6, TONE_LENGTH_INFINITE);
        break;
      case KeyEvent.KEYCODE_7:
        playTone(ToneGenerator.TONE_DTMF_7, TONE_LENGTH_INFINITE);
        break;
      case KeyEvent.KEYCODE_8:
        playTone(ToneGenerator.TONE_DTMF_8, TONE_LENGTH_INFINITE);
        break;
      case KeyEvent.KEYCODE_9:
        playTone(ToneGenerator.TONE_DTMF_9, TONE_LENGTH_INFINITE);
        break;
      case KeyEvent.KEYCODE_0:
        playTone(ToneGenerator.TONE_DTMF_0, TONE_LENGTH_INFINITE);
        break;
      case KeyEvent.KEYCODE_POUND:
        playTone(ToneGenerator.TONE_DTMF_P, TONE_LENGTH_INFINITE);
        break;
      case KeyEvent.KEYCODE_STAR:
        playTone(ToneGenerator.TONE_DTMF_S, TONE_LENGTH_INFINITE);
        break;
      default:
        break;
    }

    getView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
    digits.onKeyDown(keyCode, event);

    // If the cursor is at the end of the text we hide it.
    final int length = digits.length();
    if (length == digits.getSelectionStart() && length == digits.getSelectionEnd()) {
      digits.setCursorVisible(false);
    }
  }

  @Override
  public boolean onKey(View view, int keyCode, KeyEvent event) {
    if (view.getId() == R.id.digits) {
      if (keyCode == KeyEvent.KEYCODE_ENTER) {
        handleDialButtonPressed();
        return true;
      }
    }
    return false;
  }

  /**
   * When a key is pressed, we start playing DTMF tone, do vibration, and enter the digit
   * immediately. When a key is released, we stop the tone. Note that the "key press" event will be
   * delivered by the system with certain amount of delay, it won't be synced with user's actual
   * "touch-down" behavior.
   */
  @Override
  public void onPressed(View view, boolean pressed) {
    if (pressed) {
      int resId = view.getId();
      if (resId == R.id.one) {
        keyPressed(KeyEvent.KEYCODE_1);
      } else if (resId == R.id.two) {
        keyPressed(KeyEvent.KEYCODE_2);
      } else if (resId == R.id.three) {
        keyPressed(KeyEvent.KEYCODE_3);
      } else if (resId == R.id.four) {
        keyPressed(KeyEvent.KEYCODE_4);
      } else if (resId == R.id.five) {
        keyPressed(KeyEvent.KEYCODE_5);
      } else if (resId == R.id.six) {
        keyPressed(KeyEvent.KEYCODE_6);
      } else if (resId == R.id.seven) {
        keyPressed(KeyEvent.KEYCODE_7);
      } else if (resId == R.id.eight) {
        keyPressed(KeyEvent.KEYCODE_8);
      } else if (resId == R.id.nine) {
        keyPressed(KeyEvent.KEYCODE_9);
      } else if (resId == R.id.zero) {
        keyPressed(KeyEvent.KEYCODE_0);
      } else if (resId == R.id.pound) {
        keyPressed(KeyEvent.KEYCODE_POUND);
      } else if (resId == R.id.star) {
        keyPressed(KeyEvent.KEYCODE_STAR);
      } else {
        LogUtil.e(
            "DialpadFragment.onPressed", "Unexpected onTouch(ACTION_DOWN) event from: " + view);
      }
      pressedDialpadKeys.add(view);
    } else {
      pressedDialpadKeys.remove(view);
      if (pressedDialpadKeys.isEmpty()) {
        stopTone();
      }
    }
  }

  /**
   * Called by the containing Activity to tell this Fragment to build an overflow options menu for
   * display by the container when appropriate.
   *
   * @param invoker the View that invoked the options menu, to act as an anchor location.
   */
  private PopupMenu buildOptionsMenu(View invoker) {
    final PopupMenu popupMenu =
        new PopupMenu(getActivity(), invoker) {
          @Override
          public void show() {
            final Menu menu = getMenu();

            boolean enable = !isDigitsEmpty();
            for (int i = 0; i < menu.size(); i++) {
              MenuItem item = menu.getItem(i);
              item.setEnabled(enable);
              if (item.getItemId() == R.id.menu_call_with_note) {
                item.setVisible(CallUtil.isCallWithSubjectSupported(getContext()));
              }
            }

            final MenuItem callWithItem = menu.findItem(R.id.call_with);
            List<PhoneAccount> accounts =
                CallUtil.getCallCapablePhoneAccounts(getContext(), PhoneAccount.SCHEME_TEL);
            if (accounts != null && accounts.size() > 1) {
              final PhoneAccountHandle selected;
              if (selectedAccount != null) {
                selected = selectedAccount;
              } else {
                selected = TelecomUtil.getDefaultOutgoingPhoneAccount(getContext(),
                    PhoneAccount.SCHEME_TEL);
              }

              SubMenu callWithMenu = callWithItem.getSubMenu();
              callWithMenu.clear();

              for (PhoneAccount account : accounts) {
                final PhoneAccountHandle handle = account.getAccountHandle();
                final Intent intent = new Intent()
                    .putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle);

                callWithMenu.add(Menu.FIRST, Menu.NONE, Menu.NONE, account.getLabel())
                    .setIntent(intent)
                    .setChecked(handle.equals(selected));
              }
              callWithMenu.setGroupCheckable(Menu.FIRST, true, true);
              callWithItem.setVisible(callWithMenu.hasVisibleItems());
            } else {
              callWithItem.setVisible(false);
            }

            super.show();
          }
        };
    popupMenu.inflate(R.menu.dialpad_options);
    popupMenu.setOnMenuItemClickListener(this);
    return popupMenu;
  }

  @Override
  public void onClick(View view) {
    int resId = view.getId();
    if (resId == R.id.dialpad_floating_action_button) {
      view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
      handleDialButtonPressed();
    } else if (resId == R.id.deleteButton) {
      keyPressed(KeyEvent.KEYCODE_DEL);
    } else if (resId == R.id.digits) {
      if (!isDigitsEmpty()) {
        digits.setCursorVisible(true);
      }
    } else if (resId == R.id.dialpad_overflow) {
      overflowPopupMenu.show();
    } else {
      LogUtil.w("DialpadFragment.onClick", "Unexpected event from: " + view);
    }
  }

  @Override
  public boolean onLongClick(View view) {
    final Editable digits = this.digits.getText();
    final int id = view.getId();
    if (id == R.id.deleteButton) {
      digits.clear();
      return true;
    } else if (id == R.id.one) {
      // For non-talkback users: check for empty
      // For linear navigation users: check for "1"
      // For explore by touch users: check for "11"
      if (isDigitsEmpty()
          || TextUtils.equals(this.digits.getText(), "1")
          || TextUtils.equals(this.digits.getText(), "11")) {
        // We'll try to initiate voicemail and thus we want to remove irrelevant string.
        removePreviousDigitIfPossible('1');
        removePreviousDigitIfPossible('1');

        List<PhoneAccountHandle> subscriptionAccountHandles =
            TelecomUtil.getSubscriptionPhoneAccounts(getActivity());
        boolean hasUserSelectedDefault =
            subscriptionAccountHandles.contains(
                TelecomUtil.getDefaultOutgoingPhoneAccount(
                    getActivity(), PhoneAccount.SCHEME_VOICEMAIL));
        boolean needsAccountDisambiguation =
            subscriptionAccountHandles.size() > 1 && !hasUserSelectedDefault;

        if (needsAccountDisambiguation || isVoicemailAvailable()) {
          // On a multi-SIM phone, if the user has not selected a default
          // subscription, initiate a call to voicemail so they can select an account
          // from the "Call with" dialog.
          callVoicemail();
        } else if (getActivity() != null) {
          // Voicemail is unavailable maybe because Airplane mode is turned on.
          // Check the current status and show the most appropriate error message.
          final boolean isAirplaneModeOn =
              Settings.System.getInt(
                      getActivity().getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0)
                  != 0;
          if (isAirplaneModeOn) {
            DialogFragment dialogFragment =
                ErrorDialogFragment.newInstance(R.string.dialog_voicemail_airplane_mode_message);
            dialogFragment.show(getFragmentManager(), "voicemail_request_during_airplane_mode");
          } else {
            DialogFragment dialogFragment =
                ErrorDialogFragment.newInstance(R.string.dialog_voicemail_not_ready_message);
            dialogFragment.show(getFragmentManager(), "voicemail_not_ready");
          }
        }
        return true;
      }
      return false;
    } else if (id == R.id.zero) {
      if (pressedDialpadKeys.contains(view)) {
        // If the zero key is currently pressed, then the long press occurred by touch
        // (and not via other means like certain accessibility input methods).
        // Remove the '0' that was input when the key was first pressed.
        removePreviousDigitIfPossible('0');
        removePreviousDigitIfPossible('0');
      }
      keyPressed(KeyEvent.KEYCODE_PLUS);
      stopTone();
      pressedDialpadKeys.remove(view);
      return true;
    } else if (id == R.id.digits) {
      this.digits.setCursorVisible(true);
      return false;
    }
    return false;
  }

  /**
   * Remove the digit just before the current position of the cursor, iff the following conditions
   * are true: 1) The cursor is not positioned at index 0. 2) The digit before the current cursor
   * position matches the current digit.
   *
   * @param digit to remove from the digits view.
   */
  private void removePreviousDigitIfPossible(char digit) {
    final int currentPosition = digits.getSelectionStart();
    if (currentPosition > 0 && digit == digits.getText().charAt(currentPosition - 1)) {
      digits.setSelection(currentPosition);
      digits.getText().delete(currentPosition - 1, currentPosition);
    }
  }

  public void callVoicemail() {
    PreCall.start(
        getContext(), CallIntentBuilder.forVoicemail(CallInitiationType.Type.DIALPAD));
    hideAndClearDialpad();
  }

  private void hideAndClearDialpad() {
    LogUtil.enterBlock("DialpadFragment.hideAndClearDialpad");
    FragmentUtils.getParentUnsafe(this, DialpadListener.class).onCallPlacedFromDialpad();
  }

  /**
   * In most cases, when the dial button is pressed, there is a number in digits area. Pack it in
   * the intent, start the outgoing call broadcast as a separate task and finish this activity.
   *
   * <p>When there is no digit and the phone is CDMA and off hook, we're sending a blank flash for
   * CDMA. CDMA networks use Flash messages when special processing needs to be done, mainly for
   * 3-way or call waiting scenarios. Presumably, here we're in a special 3-way scenario where the
   * network needs a blank flash before being able to add the new participant. (This is not the case
   * with all 3-way calls, just certain CDMA infrastructures.)
   *
   * <p>Otherwise, there is no digit, display the last dialed number. Don't finish since the user
   * may want to edit it. The user needs to press the dial button again, to dial it (general case
   * described above).
   */
  private void handleDialButtonPressed() {
    if (isDigitsEmpty()) { // No number entered.
      // No real call made, so treat it as a click
      PerformanceReport.recordClick(UiAction.Type.PRESS_CALL_BUTTON_WITHOUT_CALLING);
      handleDialButtonClickWithEmptyDigits();
    } else {
      final String number = digits.getText().toString();

      // "persist.radio.otaspdial" is a temporary hack needed for one carrier's automated
      // test equipment.
      // TODO: clean it up.
      if (number != null
          && !TextUtils.isEmpty(prohibitedPhoneNumberRegexp)
          && number.matches(prohibitedPhoneNumberRegexp)) {
        PerformanceReport.recordClick(UiAction.Type.PRESS_CALL_BUTTON_WITHOUT_CALLING);
        LogUtil.i(
            "DialpadFragment.handleDialButtonPressed",
            "The phone number is prohibited explicitly by a rule.");
        if (getActivity() != null) {
          DialogFragment dialogFragment =
              ErrorDialogFragment.newInstance(R.string.dialog_phone_call_prohibited_message);
          dialogFragment.show(getFragmentManager(), "phone_prohibited_dialog");
        }

        // Clear the digits just in case.
        clearDialpad();
      } else {
        CallIntentBuilder builder = new CallIntentBuilder(number, CallInitiationType.Type.DIALPAD)
            .setPhoneAccountHandle(selectedAccount);
        PreCall.start(getContext(), builder);
        hideAndClearDialpad();
      }
    }
  }

  public void clearDialpad() {
    if (digits != null) {
      digits.getText().clear();
    }
    selectedAccount = null;
  }

  private void handleDialButtonClickWithEmptyDigits() {
    if (phoneIsCdma() && isPhoneInUse()) {
      // TODO: Move this logic into services/Telephony
      //
      // This is really CDMA specific. On GSM is it possible
      // to be off hook and wanted to add a 3rd party using
      // the redial feature.
      startActivity(newFlashIntent());
    } else {
      if (!TextUtils.isEmpty(lastNumberDialed)) {
        // Dialpad will be filled with last called number,
        // but we don't want to record it as user action
        PerformanceReport.setIgnoreActionOnce(UiAction.Type.TEXT_CHANGE_WITH_INPUT);

        // Recall the last number dialed.
        digits.setText(lastNumberDialed);

        // ...and move the cursor to the end of the digits string,
        // so you'll be able to delete digits using the Delete
        // button (just as if you had typed the number manually.)
        //
        // Note we use mDigits.getText().length() here, not
        // mLastNumberDialed.length(), since the EditText widget now
        // contains a *formatted* version of mLastNumberDialed (due to
        // mTextWatcher) and its length may have changed.
        digits.setSelection(digits.getText().length());
      } else {
        // There's no "last number dialed" or the
        // background query is still running. There's
        // nothing useful for the Dial button to do in
        // this case.  Note: with a soft dial button, this
        // can never happens since the dial button is
        // disabled under these conditons.
        playTone(ToneGenerator.TONE_PROP_NACK);
      }
    }
  }

  /** Plays the specified tone for TONE_LENGTH_MS milliseconds. */
  private void playTone(int tone) {
    playTone(tone, TONE_LENGTH_MS);
  }

  /**
   * Play the specified tone for the specified milliseconds
   *
   * <p>The tone is played locally, using the audio stream for phone calls. Tones are played only if
   * the "Audible touch tones" user preference is checked, and are NOT played if the device is in
   * silent mode.
   *
   * <p>The tone length can be -1, meaning "keep playing the tone." If the caller does so, it should
   * call stopTone() afterward.
   *
   * @param tone a tone code from {@link ToneGenerator}
   * @param durationMs tone length.
   */
  private void playTone(int tone, int durationMs) {
    // if local tone playback is disabled, just return.
    if (!dTMFToneEnabled) {
      return;
    }

    // Also do nothing if the phone is in silent mode.
    // We need to re-check the ringer mode for *every* playTone()
    // call, rather than keeping a local flag that's updated in
    // onResume(), since it's possible to toggle silent mode without
    // leaving the current activity (via the ENDCALL-longpress menu.)
    AudioManager audioManager =
        (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
    int ringerMode = audioManager.getRingerMode();
    if ((ringerMode == AudioManager.RINGER_MODE_SILENT)
        || (ringerMode == AudioManager.RINGER_MODE_VIBRATE)) {
      return;
    }

    synchronized (toneGeneratorLock) {
      if (toneGenerator == null) {
        LogUtil.w("DialpadFragment.playTone", "mToneGenerator == null, tone: " + tone);
        return;
      }

      // Start the new tone (will stop any playing tone)
      toneGenerator.startTone(tone, durationMs);
    }
  }

  /** Stop the tone if it is played. */
  private void stopTone() {
    // if local tone playback is disabled, just return.
    if (!dTMFToneEnabled) {
      return;
    }
    synchronized (toneGeneratorLock) {
      if (toneGenerator == null) {
        LogUtil.w("DialpadFragment.stopTone", "mToneGenerator == null");
        return;
      }
      toneGenerator.stopTone();
    }
  }

  /**
   * Brings up the "dialpad chooser" UI in place of the usual Dialer elements (the textfield/button
   * and the dialpad underneath).
   *
   * <p>We show this UI if the user brings up the Dialer while a call is already in progress, since
   * there's a good chance we got here accidentally (and the user really wanted the in-call dialpad
   * instead). So in this situation we display an intermediate UI that lets the user explicitly
   * choose between the in-call dialpad ("Use touch tone keypad") and the regular Dialer ("Add
   * call"). (Or, the option "Return to call in progress" just goes back to the in-call UI with no
   * dialpad at all.)
   *
   * @param enabled If true, show the "dialpad chooser" instead of the regular Dialer UI
   */
  private void showDialpadChooser(boolean enabled) {
    if (getActivity() == null) {
      return;
    }
    // Check if onCreateView() is already called by checking one of View objects.
    if (!isLayoutReady()) {
      return;
    }

    if (enabled) {
      LogUtil.i("DialpadFragment.showDialpadChooser", "Showing dialpad chooser!");
      if (dialpadView != null) {
        dialpadView.setVisibility(View.GONE);
      }

      if (overflowPopupMenu != null) {
        overflowPopupMenu.dismiss();
      }

      floatingActionButtonController.scaleOut();
      dialpadChooser.setVisibility(View.VISIBLE);

      // Instantiate the DialpadChooserAdapter and hook it up to the
      // ListView.  We do this only once.
      if (dialpadChooserAdapter == null) {
        dialpadChooserAdapter = new DialpadChooserAdapter(getActivity());
      }
      dialpadChooser.setAdapter(dialpadChooserAdapter);
    } else {
      LogUtil.i("DialpadFragment.showDialpadChooser", "Displaying normal Dialer UI.");
      if (dialpadView != null) {
        LogUtil.i("DialpadFragment.showDialpadChooser", "mDialpadView not null");
        dialpadView.setVisibility(View.VISIBLE);
        if (isDialpadSlideUp()) {
          floatingActionButtonController.scaleIn();
        }
      } else {
        LogUtil.i("DialpadFragment.showDialpadChooser", "mDialpadView null");
        digits.setVisibility(View.VISIBLE);
      }

      dialpadChooser.setVisibility(View.GONE);
    }
  }

  /** @return true if we're currently showing the "dialpad chooser" UI. */
  private boolean isDialpadChooserVisible() {
    return dialpadChooser.getVisibility() == View.VISIBLE;
  }

  /** Handle clicks from the dialpad chooser. */
  @Override
  public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
    DialpadChooserAdapter.ChoiceItem item =
        (DialpadChooserAdapter.ChoiceItem) parent.getItemAtPosition(position);
    int itemId = item.id;
    if (itemId == DialpadChooserAdapter.DIALPAD_CHOICE_USE_DTMF_DIALPAD) {
      // Fire off an intent to go back to the in-call UI
      // with the dialpad visible.
      returnToInCallScreen(true);
    } else if (itemId == DialpadChooserAdapter.DIALPAD_CHOICE_RETURN_TO_CALL) {
      // Fire off an intent to go back to the in-call UI
      // (with the dialpad hidden).
      returnToInCallScreen(false);
    } else if (itemId == DialpadChooserAdapter.DIALPAD_CHOICE_ADD_NEW_CALL) {
      // Ok, guess the user really did want to be here (in the
      // regular Dialer) after all.  Bring back the normal Dialer UI.
      showDialpadChooser(false);
    } else {
      LogUtil.w("DialpadFragment.onItemClick", "Unexpected itemId: " + itemId);
    }
  }

  /**
   * Returns to the in-call UI (where there's presumably a call in progress) in response to the user
   * selecting "use touch tone keypad" or "return to call" from the dialpad chooser.
   */
  private void returnToInCallScreen(boolean showDialpad) {
    TelecomUtil.showInCallScreen(getActivity(), showDialpad);

    // Finally, finish() ourselves so that we don't stay on the
    // activity stack.
    // Note that we do this whether or not the showCallScreenWithDialpad()
    // call above had any effect or not!  (That call is a no-op if the
    // phone is idle, which can happen if the current call ends while
    // the dialpad chooser is up.  In this case we can't show the
    // InCallScreen, and there's no point staying here in the Dialer,
    // so we just take the user back where he came from...)
    getActivity().finish();
  }

  /**
   * @return true if the phone is "in use", meaning that at least one line is active (ie. off hook
   *     or ringing or dialing, or on hold).
   */
  private boolean isPhoneInUse() {
    return getContext() != null
        && TelecomUtil.isInManagedCall(getContext())
        && FragmentUtils.getParentUnsafe(this, HostInterface.class).shouldShowDialpadChooser();
  }

  /** @return true if the phone is a CDMA phone type */
  private boolean phoneIsCdma() {
    return getTelephonyManager().getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA;
  }

  @Override
  public boolean onMenuItemClick(MenuItem item) {
    if (item.getGroupId() == Menu.FIRST) {
      Intent intent = item.getIntent();
      selectedAccount = intent.getParcelableExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE);
      return true;
    }
    int resId = item.getItemId();
    if (resId == R.id.menu_2s_pause) {
      updateDialString(PAUSE);
      return true;
    } else if (resId == R.id.menu_add_wait) {
      updateDialString(WAIT);
      return true;
    } else if (resId == R.id.menu_call_with_note) {
      CallSubjectDialog.start(getActivity(), digits.getText().toString());
      hideAndClearDialpad();
      return true;
    } else {
      return false;
    }
  }

  /**
   * Updates the dial string (mDigits) after inserting a Pause character (,) or Wait character (;).
   */
  private void updateDialString(char newDigit) {
    if (newDigit != WAIT && newDigit != PAUSE) {
      throw new IllegalArgumentException("Not expected for anything other than PAUSE & WAIT");
    }

    int selectionStart;
    int selectionEnd;

    // SpannableStringBuilder editable_text = new SpannableStringBuilder(mDigits.getText());
    int anchor = this.digits.getSelectionStart();
    int point = this.digits.getSelectionEnd();

    selectionStart = Math.min(anchor, point);
    selectionEnd = Math.max(anchor, point);

    if (selectionStart == -1) {
      selectionStart = selectionEnd = this.digits.length();
    }

    Editable digits = this.digits.getText();

    if (canAddDigit(digits, selectionStart, selectionEnd, newDigit)) {
      digits.replace(selectionStart, selectionEnd, Character.toString(newDigit));

      if (selectionStart != selectionEnd) {
        // Unselect: back to a regular cursor, just pass the character inserted.
        this.digits.setSelection(selectionStart + 1);
      }
    }
  }

  /** Update the enabledness of the "Dial" and "Backspace" buttons if applicable. */
  private void updateDeleteButtonEnabledState() {
    if (getActivity() == null) {
      return;
    }
    final boolean digitsNotEmpty = !isDigitsEmpty();
    delete.setEnabled(digitsNotEmpty);
  }

  /**
   * Handle transitions for the menu button depending on the state of the digits edit text.
   * Transition out when going from digits to no digits and transition in when the first digit is
   * pressed.
   *
   * @param transitionIn True if transitioning in, False if transitioning out
   */
  private void updateMenuOverflowButton(boolean transitionIn) {
    overflowMenuButton = dialpadView.getOverflowMenuButton();
    if (transitionIn) {
      AnimUtils.fadeIn(overflowMenuButton, AnimUtils.DEFAULT_DURATION);
    } else {
      AnimUtils.fadeOut(
          overflowMenuButton,
          AnimUtils.DEFAULT_DURATION,
          new AnimationCallback() {
            @Override
            public void onAnimationEnd() {
              // AnimUtils will set the visibility to GONE and cause the layout to move around.
              overflowMenuButton.setVisibility(View.INVISIBLE);
            }
          });
    }
  }

  /**
   * Check if voicemail is enabled/accessible.
   *
   * @return true if voicemail is enabled and accessible. Note that this can be false "temporarily"
   *     after the app boot.
   */
  private boolean isVoicemailAvailable() {
    try {
      PhoneAccountHandle defaultUserSelectedAccount =
          TelecomUtil.getDefaultOutgoingPhoneAccount(getActivity(), PhoneAccount.SCHEME_VOICEMAIL);
      if (defaultUserSelectedAccount == null) {
        // In a single-SIM phone, there is no default outgoing phone account selected by
        // the user, so just call TelephonyManager#getVoicemailNumber directly.
        return !TextUtils.isEmpty(getTelephonyManager().getVoiceMailNumber());
      } else {
        return !TextUtils.isEmpty(
            TelecomUtil.getVoicemailNumber(getActivity(), defaultUserSelectedAccount));
      }
    } catch (SecurityException se) {
      // Possibly no READ_PHONE_STATE privilege.
      LogUtil.w(
          "DialpadFragment.isVoicemailAvailable",
          "SecurityException is thrown. Maybe privilege isn't sufficient.");
    }
    return false;
  }

  /** @return true if the widget with the phone number digits is empty. */
  private boolean isDigitsEmpty() {
    return digits.length() == 0;
  }

  /**
   * Starts the asyn query to get the last dialed/outgoing number. When the background query
   * finishes, mLastNumberDialed is set to the last dialed number or an empty string if none exists
   * yet.
   */
  private void queryLastOutgoingCall() {
    lastNumberDialed = EMPTY_NUMBER;
    if (!PermissionsUtil.hasCallLogReadPermissions(getContext())) {
      return;
    }
    FragmentUtils.getParentUnsafe(this, DialpadListener.class)
        .getLastOutgoingCall(
            number -> {
              // TODO: Filter out emergency numbers if the carrier does not want redial for these.

              // If the fragment has already been detached since the last time we called
              // queryLastOutgoingCall in onResume there is no point doing anything here.
              if (getActivity() == null) {
                return;
              }
              lastNumberDialed = number;
              updateDeleteButtonEnabledState();
            });
  }

  private Intent newFlashIntent() {
    Intent intent = new CallIntentBuilder(EMPTY_NUMBER, CallInitiationType.Type.DIALPAD).build();
    intent.putExtra(EXTRA_SEND_EMPTY_FLASH, true);
    return intent;
  }

  @Override
  public void onHiddenChanged(boolean hidden) {
    super.onHiddenChanged(hidden);
    if (getActivity() == null || getView() == null) {
      return;
    }
    if (!hidden && !isDialpadChooserVisible()) {
      if (animate) {
        dialpadView.animateShow();
      }
      FragmentUtils.getParentUnsafe(this, DialpadListener.class).onDialpadShown();
      digits.requestFocus();
    }
  }

  public boolean getAnimate() {
    return animate;
  }

  public void setAnimate(boolean value) {
    animate = value;
  }

  public void setYFraction(float yFraction) {
    ((DialpadSlidingRelativeLayout) getView()).setYFraction(yFraction);
  }

  public int getDialpadHeight() {
    if (dialpadView == null) {
      return 0;
    }
    return dialpadView.getHeight();
  }

  public void process_quote_emergency_unquote(String query) {
    if (PseudoEmergencyAnimator.PSEUDO_EMERGENCY_NUMBER.equals(query)) {
      if (pseudoEmergencyAnimator == null) {
        pseudoEmergencyAnimator =
            new PseudoEmergencyAnimator(
                new PseudoEmergencyAnimator.ViewProvider() {
                  @Override
                  public View getFab() {
                    return floatingActionButton;
                  }

                  @Override
                  public Context getContext() {
                    return DialpadFragment.this.getContext();
                  }
                });
      }
      pseudoEmergencyAnimator.start();
    } else {
      if (pseudoEmergencyAnimator != null) {
        pseudoEmergencyAnimator.end();
      }
    }
  }

  /** Animate the dialpad down off the screen. */
  public void slideDown(boolean animate, AnimationListener listener) {
    Assert.checkArgument(isDialpadSlideUp);
    isDialpadSlideUp = false;
    int animation;
    if (isLandscape) {
      animation = isLayoutRtl ? R.anim.dialpad_slide_out_left : R.anim.dialpad_slide_out_right;
    } else {
      animation = R.anim.dialpad_slide_out_bottom;
    }
    Animation slideDown = AnimationUtils.loadAnimation(getContext(), animation);
    slideDown.setInterpolator(AnimUtils.EASE_OUT);
    slideDown.setAnimationListener(listener);
    slideDown.setDuration(animate ? dialpadSlideInDuration : 0);
    getView().startAnimation(slideDown);
    floatingActionButtonController.scaleOut();
  }

  /** Animate the dialpad up onto the screen. */
  public void slideUp(boolean animate) {
    Assert.checkArgument(!isDialpadSlideUp);
    isDialpadSlideUp = true;
    int animation;
    if (isLandscape) {
      animation = isLayoutRtl ? R.anim.dialpad_slide_in_left : R.anim.dialpad_slide_in_right;
    } else {
      animation = R.anim.dialpad_slide_in_bottom;
    }
    Animation slideUp = AnimationUtils.loadAnimation(getContext(), animation);
    slideUp.setInterpolator(AnimUtils.EASE_IN);
    slideUp.setDuration(animate ? dialpadSlideInDuration : 0);
    slideUp.setAnimationListener(
        new AnimationListener() {
          @Override
          public void onAnimationStart(Animation animation) {}

          @Override
          public void onAnimationEnd(Animation animation) {
            floatingActionButtonController.scaleIn();
          }

          @Override
          public void onAnimationRepeat(Animation animation) {}
        });
    getView().startAnimation(slideUp);
  }

  public boolean isDialpadSlideUp() {
    return isDialpadSlideUp;
  }

  /** Returns the text in the dialpad */
  public String getQuery() {
    return digits.getText().toString();
  }

  public interface OnDialpadQueryChangedListener {

    void onDialpadQueryChanged(String query);
  }

  public interface HostInterface {

    /**
     * Notifies the parent activity that the space above the dialpad has been tapped with no query
     * in the dialpad present. In most situations this will cause the dialpad to be dismissed,
     * unless there happens to be content showing.
     */
    boolean onDialpadSpacerTouchWithEmptyQuery();

    /** Returns true if this fragment's parent want the dialpad to show the dialpad chooser. */
    boolean shouldShowDialpadChooser();
  }

  /**
   * LinearLayout with getter and setter methods for the translationY property using floats, for
   * animation purposes.
   */
  public static class DialpadSlidingRelativeLayout extends RelativeLayout {

    public DialpadSlidingRelativeLayout(Context context) {
      super(context);
    }

    public DialpadSlidingRelativeLayout(Context context, AttributeSet attrs) {
      super(context, attrs);
    }

    public DialpadSlidingRelativeLayout(Context context, AttributeSet attrs, int defStyle) {
      super(context, attrs, defStyle);
    }

    @UsedByReflection(value = "dialpad_fragment.xml")
    public float getYFraction() {
      final int height = getHeight();
      if (height == 0) {
        return 0;
      }
      return getTranslationY() / height;
    }

    @UsedByReflection(value = "dialpad_fragment.xml")
    public void setYFraction(float yFraction) {
      setTranslationY(yFraction * getHeight());
    }
  }

  public static class ErrorDialogFragment extends DialogFragment {

    private static final String ARG_TITLE_RES_ID = "argTitleResId";
    private static final String ARG_MESSAGE_RES_ID = "argMessageResId";
    private int titleResId;
    private int messageResId;

    public static ErrorDialogFragment newInstance(int messageResId) {
      return newInstance(0, messageResId);
    }

    public static ErrorDialogFragment newInstance(int titleResId, int messageResId) {
      final ErrorDialogFragment fragment = new ErrorDialogFragment();
      final Bundle args = new Bundle();
      args.putInt(ARG_TITLE_RES_ID, titleResId);
      args.putInt(ARG_MESSAGE_RES_ID, messageResId);
      fragment.setArguments(args);
      return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      titleResId = getArguments().getInt(ARG_TITLE_RES_ID);
      messageResId = getArguments().getInt(ARG_MESSAGE_RES_ID);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
      if (titleResId != 0) {
        builder.setTitle(titleResId);
      }
      if (messageResId != 0) {
        builder.setMessage(messageResId);
      }
      builder.setPositiveButton(android.R.string.ok, (dialog, which) -> dismiss());
      return builder.create();
    }
  }

  /**
   * Simple list adapter, binding to an icon + text label for each item in the "dialpad chooser"
   * list.
   */
  private static class DialpadChooserAdapter extends BaseAdapter {

    // IDs for the possible "choices":
    static final int DIALPAD_CHOICE_USE_DTMF_DIALPAD = 101;
    static final int DIALPAD_CHOICE_RETURN_TO_CALL = 102;
    static final int DIALPAD_CHOICE_ADD_NEW_CALL = 103;
    private static final int NUM_ITEMS = 3;
    private LayoutInflater inflater;
    private ChoiceItem[] choiceItems = new ChoiceItem[NUM_ITEMS];

    DialpadChooserAdapter(Context context) {
      // Cache the LayoutInflate to avoid asking for a new one each time.
      inflater = LayoutInflater.from(context);

      // Initialize the possible choices.
      // TODO: could this be specified entirely in XML?

      // - "Use touch tone keypad"
      choiceItems[0] =
          new ChoiceItem(
              context.getString(R.string.dialer_useDtmfDialpad),
              BitmapFactory.decodeResource(
                  context.getResources(), R.drawable.ic_dialer_fork_tt_keypad),
              DIALPAD_CHOICE_USE_DTMF_DIALPAD);

      // - "Return to call in progress"
      choiceItems[1] =
          new ChoiceItem(
              context.getString(R.string.dialer_returnToInCallScreen),
              BitmapFactory.decodeResource(
                  context.getResources(), R.drawable.ic_dialer_fork_current_call),
              DIALPAD_CHOICE_RETURN_TO_CALL);

      // - "Add call"
      choiceItems[2] =
          new ChoiceItem(
              context.getString(R.string.dialer_addAnotherCall),
              BitmapFactory.decodeResource(
                  context.getResources(), R.drawable.ic_dialer_fork_add_call),
              DIALPAD_CHOICE_ADD_NEW_CALL);
    }

    @Override
    public int getCount() {
      return NUM_ITEMS;
    }

    /** Return the ChoiceItem for a given position. */
    @Override
    public Object getItem(int position) {
      return choiceItems[position];
    }

    /** Return a unique ID for each possible choice. */
    @Override
    public long getItemId(int position) {
      return position;
    }

    /** Make a view for each row. */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      // When convertView is non-null, we can reuse it (there's no need
      // to reinflate it.)
      if (convertView == null) {
        convertView = inflater.inflate(R.layout.dialpad_chooser_list_item, null);
      }

      TextView text = convertView.findViewById(R.id.text);
      text.setText(choiceItems[position].text);

      ImageView icon = convertView.findViewById(R.id.icon);
      icon.setImageBitmap(choiceItems[position].icon);

      return convertView;
    }

    // Simple struct for a single "choice" item.
    static class ChoiceItem {

      String text;
      Bitmap icon;
      int id;

      ChoiceItem(String s, Bitmap b, int i) {
        text = s;
        icon = b;
        id = i;
      }
    }
  }

  private class CallStateReceiver extends BroadcastReceiver {

    /**
     * Receive call state changes so that we can take down the "dialpad chooser" if the phone
     * becomes idle while the chooser UI is visible.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
      String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
      if ((TextUtils.equals(state, TelephonyManager.EXTRA_STATE_IDLE)
              || TextUtils.equals(state, TelephonyManager.EXTRA_STATE_OFFHOOK))
          && isDialpadChooserVisible()) {
        // Note there's a race condition in the UI here: the
        // dialpad chooser could conceivably disappear (on its
        // own) at the exact moment the user was trying to select
        // one of the choices, which would be confusing.  (But at
        // least that's better than leaving the dialpad chooser
        // onscreen, but useless...)
        LogUtil.i("CallStateReceiver.onReceive", "hiding dialpad chooser, state: %s", state);
        showDialpadChooser(false);
      }
    }
  }

  /** Listener for dialpad's parent. */
  public interface DialpadListener {
    void getLastOutgoingCall(LastOutgoingCallCallback callback);

    void onDialpadShown();

    void onCallPlacedFromDialpad();
  }

  /** Callback for async lookup of the last number dialed. */
  public interface LastOutgoingCallCallback {

    void lastOutgoingCall(String number);
  }

  /**
   * A worker that helps formatting the phone number as the user types it in.
   *
   * <p>Input: the ISO 3166-1 two-letter country code of the country the user is in.
   *
   * <p>Output: an instance of {@link DialerPhoneNumberFormattingTextWatcher}. Note: It is unusual
   * to return a non-data value from a worker. But {@link DialerPhoneNumberFormattingTextWatcher}
   * depends on libphonenumber API, which cannot be initialized on the main thread.
   */
  private static class InitPhoneNumberFormattingTextWatcherWorker
      implements Worker<String, DialerPhoneNumberFormattingTextWatcher> {

    @Nullable
    @Override
    public DialerPhoneNumberFormattingTextWatcher doInBackground(@Nullable String countryCode) {
      return new DialerPhoneNumberFormattingTextWatcher(countryCode);
    }
  }

  /**
   * An extension of Android telephony's {@link PhoneNumberFormattingTextWatcher}. This watcher
   * skips formatting Argentina mobile numbers for domestic calls.
   *
   * <p>As of Nov. 28, 2017, the as-you-type-formatting provided by libphonenumber's
   * AsYouTypeFormatter (which {@link PhoneNumberFormattingTextWatcher} depends on) can't correctly
   * format Argentina mobile numbers for domestic calls (a bug). We temporarily disable the
   * formatting for such numbers until libphonenumber is fixed (which will come as early as the next
   * Android release).
   */
  @VisibleForTesting
  public static class DialerPhoneNumberFormattingTextWatcher
      extends PhoneNumberFormattingTextWatcher {
    private static final Pattern AR_DOMESTIC_CALL_MOBILE_NUMBER_PATTERN;

    // This static initialization block builds a pattern for domestic calls to Argentina mobile
    // numbers:
    // (1) Local calls: 15 <local number>
    // (2) Long distance calls: <area code> 15 <local number>
    // See https://en.wikipedia.org/wiki/Telephone_numbers_in_Argentina for detailed explanations.
    static {
      String regex =
          "0?("
              + "  ("
              + "   11|"
              + "   2("
              + "     2("
              + "       02?|"
              + "       [13]|"
              + "       2[13-79]|"
              + "       4[1-6]|"
              + "       5[2457]|"
              + "       6[124-8]|"
              + "       7[1-4]|"
              + "       8[13-6]|"
              + "       9[1267]"
              + "     )|"
              + "     3("
              + "       02?|"
              + "       1[467]|"
              + "       2[03-6]|"
              + "       3[13-8]|"
              + "       [49][2-6]|"
              + "       5[2-8]|"
              + "       [67]"
              + "     )|"
              + "     4("
              + "       7[3-578]|"
              + "       9"
              + "     )|"
              + "     6("
              + "       [0136]|"
              + "       2[24-6]|"
              + "       4[6-8]?|"
              + "       5[15-8]"
              + "     )|"
              + "     80|"
              + "     9("
              + "       0[1-3]|"
              + "       [19]|"
              + "       2\\d|"
              + "       3[1-6]|"
              + "       4[02568]?|"
              + "       5[2-4]|"
              + "       6[2-46]|"
              + "       72?|"
              + "       8[23]?"
              + "     )"
              + "   )|"
              + "   3("
              + "     3("
              + "       2[79]|"
              + "       6|"
              + "       8[2578]"
              + "     )|"
              + "     4("
              + "       0[0-24-9]|"
              + "       [12]|"
              + "       3[5-8]?|"
              + "       4[24-7]|"
              + "       5[4-68]?|"
              + "       6[02-9]|"
              + "       7[126]|"
              + "       8[2379]?|"
              + "       9[1-36-8]"
              + "     )|"
              + "     5("
              + "       1|"
              + "       2[1245]|"
              + "       3[237]?|"
              + "       4[1-46-9]|"
              + "       6[2-4]|"
              + "       7[1-6]|"
              + "       8[2-5]?"
              + "     )|"
              + "     6[24]|"
              + "     7("
              + "       [069]|"
              + "       1[1568]|"
              + "       2[15]|"
              + "       3[145]|"
              + "       4[13]|"
              + "       5[14-8]|"
              + "       7[2-57]|"
              + "       8[126]"
              + "     )|"
              + "     8("
              + "       [01]|"
              + "       2[15-7]|"
              + "       3[2578]?|"
              + "       4[13-6]|"
              + "       5[4-8]?|"
              + "       6[1-357-9]|"
              + "       7[36-8]?|"
              + "       8[5-8]?|"
              + "       9[124]"
              + "     )"
              + "   )"
              + " )?15"
              + ").*";
      AR_DOMESTIC_CALL_MOBILE_NUMBER_PATTERN = Pattern.compile(regex.replaceAll("\\s+", ""));
    }

    private final String countryCode;

    DialerPhoneNumberFormattingTextWatcher(String countryCode) {
      super(countryCode);
      this.countryCode = countryCode;
    }

    @Override
    public synchronized void afterTextChanged(Editable s) {
      // When the country code is NOT "AR", Android telephony's PhoneNumberFormattingTextWatcher can
      // correctly handle the input so we will let it do its job.
      if (!Ascii.toUpperCase(countryCode).equals("AR")) {
        super.afterTextChanged(s);
        return;
      }

      // When the country code is "AR", PhoneNumberFormattingTextWatcher can also format the input
      // correctly if the number is NOT for a domestic call to a mobile phone.
      String rawNumber = getRawNumber(s);
      Matcher matcher = AR_DOMESTIC_CALL_MOBILE_NUMBER_PATTERN.matcher(rawNumber);
      if (!matcher.matches()) {
        super.afterTextChanged(s);
        return;
      }

      // As modifying the input will trigger another call to afterTextChanged(Editable), we must
      // check whether the input's format has already been removed and return if it has
      // been to avoid infinite recursion.
      if (rawNumber.contentEquals(s)) {
        return;
      }

      // If we reach this point, the country code must be "AR" and variable "s" represents a number
      // for a domestic call to a mobile phone. "s" is incorrectly formatted by Android telephony's
      // PhoneNumberFormattingTextWatcher so we remove its format by replacing it with the raw
      // number.
      s.replace(0, s.length(), rawNumber);

      // Make sure the cursor is at the end of the text.
      Selection.setSelection(s, s.length());

      PhoneNumberUtils.addTtsSpan(s, 0 /* start */, s.length() /* endExclusive */);
    }

    private static String getRawNumber(Editable s) {
      StringBuilder rawNumberBuilder = new StringBuilder();

      for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (PhoneNumberUtils.isNonSeparator(c)) {
          rawNumberBuilder.append(c);
        }
      }

      return rawNumberBuilder.toString();
    }
  }
}
