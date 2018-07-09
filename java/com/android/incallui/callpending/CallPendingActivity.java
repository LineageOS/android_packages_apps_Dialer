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
 * limitations under the License.
 */

package com.android.incallui.callpending;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.telecom.CallAudioState;
import android.telecom.TelecomManager;
import com.android.dialer.common.LogUtil;
import com.android.dialer.enrichedcall.EnrichedCallComponent;
import com.android.dialer.enrichedcall.Session;
import com.android.dialer.multimedia.MultimediaData;
import com.android.incallui.audiomode.AudioModeProvider;
import com.android.incallui.call.DialerCall.State;
import com.android.incallui.incall.bindings.InCallBindings;
import com.android.incallui.incall.protocol.ContactPhotoType;
import com.android.incallui.incall.protocol.InCallButtonIds;
import com.android.incallui.incall.protocol.InCallButtonUi;
import com.android.incallui.incall.protocol.InCallButtonUiDelegate;
import com.android.incallui.incall.protocol.InCallButtonUiDelegateFactory;
import com.android.incallui.incall.protocol.InCallScreen;
import com.android.incallui.incall.protocol.InCallScreenDelegate;
import com.android.incallui.incall.protocol.InCallScreenDelegateFactory;
import com.android.incallui.incall.protocol.PrimaryCallState;
import com.android.incallui.incall.protocol.PrimaryInfo;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * Activity useful for showing the incall ui without an actual call being placed.
 *
 * <p>The UI currently displays the following:
 *
 * <ul>
 *   <li>Contact info
 *   <li>"Dialing..." call state
 *   <li>Enriched calling data
 * </ul>
 *
 * If the user presses the back or disconnect buttons, {@link #finish()} is called.
 */
public class CallPendingActivity extends FragmentActivity
    implements InCallButtonUiDelegateFactory, InCallScreenDelegateFactory {

  private static final String TAG_IN_CALL_SCREEN = "tag_in_call_screen";
  private static final String ACTION_FINISH_BROADCAST =
      "dialer.intent.action.CALL_PENDING_ACTIVITY_FINISH";

  private static final String EXTRA_SESSION_ID = "extra_session_id";
  private static final String EXTRA_NUMBER = "extra_number";
  private static final String EXTRA_NAME = "extra_name";
  private static final String EXTRA_LABEL = "extra_label";
  private static final String EXTRA_LOOKUP_KEY = "extra_lookup_key";
  private static final String EXTRA_CALL_PENDING_LABEL = "extra_call_pending_label";
  private static final String EXTRA_PHOTO_URI = "extra_photo_uri";

  private final BroadcastReceiver finishReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent intent) {
          LogUtil.i("CallPendingActivity.onReceive", "finish broadcast received");
          String action = intent.getAction();
          if (action.equals(ACTION_FINISH_BROADCAST)) {
            finish();
          }
        }
      };

  private InCallButtonUiDelegate inCallButtonUiDelegate;
  private InCallScreenDelegate inCallScreenDelegate;

  public static Intent getIntent(
      Context context,
      String name,
      String number,
      String label,
      String lookupKey,
      String callPendingLabel,
      Uri photoUri,
      long sessionId) {
    Intent intent = new Intent(context, CallPendingActivity.class);
    intent.putExtra(EXTRA_NAME, name);
    intent.putExtra(EXTRA_NUMBER, number);
    intent.putExtra(EXTRA_LABEL, label);
    intent.putExtra(EXTRA_LOOKUP_KEY, lookupKey);
    intent.putExtra(EXTRA_CALL_PENDING_LABEL, callPendingLabel);
    intent.putExtra(EXTRA_PHOTO_URI, photoUri);
    intent.putExtra(EXTRA_SESSION_ID, sessionId);
    return intent;
  }

  public static Intent getFinishBroadcast() {
    return new Intent(ACTION_FINISH_BROADCAST);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.pending_incall_screen);
    registerReceiver(finishReceiver, new IntentFilter(ACTION_FINISH_BROADCAST));
  }

  @Override
  protected void onStart() {
    super.onStart();
    InCallScreen inCallScreen = InCallBindings.createInCallScreen();
    getSupportFragmentManager()
        .beginTransaction()
        .add(R.id.main, inCallScreen.getInCallScreenFragment(), TAG_IN_CALL_SCREEN)
        .commit();
  }

  @Override
  protected void onResume() {
    super.onResume();
    setupInCallScreen();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    unregisterReceiver(finishReceiver);
  }

  private void setupInCallScreen() {
    InCallScreen inCallScreen =
        (InCallScreen) getSupportFragmentManager().findFragmentByTag(TAG_IN_CALL_SCREEN);
    inCallScreen.setPrimary(createPrimaryInfo());
    inCallScreen.setCallState(
        PrimaryCallState.builder()
            .setState(State.CALL_PENDING)
            .setCustomLabel(getCallPendingLabel())
            .build());
    inCallScreen.setEndCallButtonEnabled(true, true);
  }

  private PrimaryInfo createPrimaryInfo() {
    Session session =
        EnrichedCallComponent.get(this).getEnrichedCallManager().getSession(getSessionId());
    MultimediaData multimediaData;
    if (session == null) {
      LogUtil.i("CallPendingActivity.createPrimaryInfo", "Null session.");
      multimediaData = null;
    } else {
      multimediaData = session.getMultimediaData();
    }

    Drawable photo = null;
    try {
      // TODO(calderwoodra) move to background thread
      Uri photoUri = getPhotoUri();
      InputStream is = getContentResolver().openInputStream(photoUri);
      photo = Drawable.createFromStream(is, photoUri.toString());
    } catch (FileNotFoundException e) {
      LogUtil.e("CallPendingActivity.createPrimaryInfo", "Contact photo not found", e);
    }

    String name = getName();
    String number = getNumber();

    // DialerCall with caller that is a work contact.
    return PrimaryInfo.builder()
        .setNumber(number)
        .setName(name)
        .setNameIsNumber(name != null && name.equals(number))
        .setLabel(getPhoneLabel())
        .setPhoto(photo)
        .setPhotoType(ContactPhotoType.CONTACT)
        .setIsSipCall(false)
        .setIsContactPhotoShown(true)
        .setIsWorkCall(false)
        .setIsSpam(false)
        .setIsLocalContact(true)
        .setAnsweringDisconnectsOngoingCall(false)
        .setShouldShowLocation(false)
        .setContactInfoLookupKey(getLookupKey())
        .setMultimediaData(multimediaData)
        .setShowInCallButtonGrid(false)
        .setNumberPresentation(TelecomManager.PRESENTATION_ALLOWED)
        .build();
  }

  @Override
  public InCallButtonUiDelegate newInCallButtonUiDelegate() {
    if (inCallButtonUiDelegate != null) {
      return inCallButtonUiDelegate;
    }
    return inCallButtonUiDelegate =
        new InCallButtonUiDelegate() {

          @Override
          public void onInCallButtonUiReady(InCallButtonUi inCallButtonUi) {
            inCallButtonUi.showButton(InCallButtonIds.BUTTON_DIALPAD, true);
            inCallButtonUi.showButton(InCallButtonIds.BUTTON_MUTE, true);
            inCallButtonUi.showButton(InCallButtonIds.BUTTON_AUDIO, true);
            inCallButtonUi.showButton(InCallButtonIds.BUTTON_ADD_CALL, true);

            inCallButtonUi.enableButton(InCallButtonIds.BUTTON_DIALPAD, false);
            inCallButtonUi.enableButton(InCallButtonIds.BUTTON_MUTE, false);
            inCallButtonUi.enableButton(InCallButtonIds.BUTTON_AUDIO, false);
            inCallButtonUi.enableButton(InCallButtonIds.BUTTON_ADD_CALL, false);
          }

          @Override
          public void onInCallButtonUiUnready() {}

          @Override
          public void onSaveInstanceState(Bundle outState) {}

          @Override
          public void onRestoreInstanceState(Bundle savedInstanceState) {}

          @Override
          public void refreshMuteState() {}

          @Override
          public void addCallClicked() {}

          @Override
          public void muteClicked(boolean checked, boolean clickedByUser) {}

          @Override
          public void mergeClicked() {}

          @Override
          public void holdClicked(boolean checked) {}

          @Override
          public void swapClicked() {}

          @Override
          public void showDialpadClicked(boolean checked) {}

          @Override
          public void changeToVideoClicked() {}

          @Override
          public void switchCameraClicked(boolean useFrontFacingCamera) {}

          @Override
          public void toggleCameraClicked() {}

          @Override
          public void pauseVideoClicked(boolean pause) {}

          @Override
          public void toggleSpeakerphone() {}

          @Override
          public CallAudioState getCurrentAudioState() {
            return AudioModeProvider.getInstance().getAudioState();
          }

          @Override
          public void setAudioRoute(int route) {}

          @Override
          public void onEndCallClicked() {}

          @Override
          public void showAudioRouteSelector() {}

          @Override
          public void swapSimClicked() {}

          @Override
          public void callRecordClicked(boolean checked) {}

          @Override
          public Context getContext() {
            return CallPendingActivity.this;
          }
        };
  }

  @Override
  public InCallScreenDelegate newInCallScreenDelegate() {
    if (inCallScreenDelegate != null) {
      return inCallScreenDelegate;
    }
    return inCallScreenDelegate =
        new InCallScreenDelegate() {

          @Override
          public void onInCallScreenDelegateInit(InCallScreen inCallScreen) {}

          @Override
          public void onInCallScreenReady() {}

          @Override
          public void onInCallScreenUnready() {}

          @Override
          public void onEndCallClicked() {
            finish();
          }

          @Override
          public void onSecondaryInfoClicked() {}

          @Override
          public void onCallStateButtonClicked() {}

          @Override
          public void onManageConferenceClicked() {}

          @Override
          public void onShrinkAnimationComplete() {}

          @Override
          public void onInCallScreenResumed() {}

          @Override
          public void onInCallScreenPaused() {}
        };
  }

  private long getSessionId() {
    return getIntent().getLongExtra(EXTRA_SESSION_ID, -1);
  }

  private String getNumber() {
    return getIntent().getStringExtra(EXTRA_NUMBER);
  }

  private String getName() {
    return getIntent().getStringExtra(EXTRA_NAME);
  }

  private String getPhoneLabel() {
    return getIntent().getStringExtra(EXTRA_LABEL);
  }

  private String getLookupKey() {
    return getIntent().getStringExtra(EXTRA_LOOKUP_KEY);
  }

  private String getCallPendingLabel() {
    return getIntent().getStringExtra(EXTRA_CALL_PENDING_LABEL);
  }

  private Uri getPhotoUri() {
    return getIntent().getParcelableExtra(EXTRA_PHOTO_URI);
  }
}
