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

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.telecom.CallAudioState;
import android.text.TextUtils;
import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.lettertile.LetterTileDrawable;
import com.android.dialer.telecom.TelecomUtil;
import com.android.incallui.ContactInfoCache.ContactCacheEntry;
import com.android.incallui.ContactInfoCache.ContactInfoCacheCallback;
import com.android.incallui.InCallPresenter.InCallUiListener;
import com.android.incallui.audiomode.AudioModeProvider;
import com.android.incallui.audiomode.AudioModeProvider.AudioModeListener;
import com.android.incallui.call.CallList;
import com.android.incallui.call.CallList.Listener;
import com.android.incallui.call.DialerCall;
import com.android.incallui.speakerbuttonlogic.SpeakerButtonInfo;
import com.android.incallui.speakerbuttonlogic.SpeakerButtonInfo.IconSize;
import com.android.newbubble.NewBubble;
import com.android.newbubble.NewBubbleInfo;
import com.android.newbubble.NewBubbleInfo.Action;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Listens for events relevant to the return-to-call bubble and updates the bubble's state as
 * necessary
 */
public class NewReturnToCallController implements InCallUiListener, Listener, AudioModeListener {

  private final Context context;

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  NewBubble bubble;

  private CallAudioState audioState;

  private final PendingIntent toggleSpeaker;
  private final PendingIntent showSpeakerSelect;
  private final PendingIntent toggleMute;
  private final PendingIntent endCall;
  private final PendingIntent fullScreen;

  private final ContactInfoCache contactInfoCache;

  public static boolean isEnabled(Context context) {
    return ConfigProviderBindings.get(context).getBoolean("enable_return_to_call_bubble_v2", false);
  }

  public NewReturnToCallController(Context context, ContactInfoCache contactInfoCache) {
    this.context = context;
    this.contactInfoCache = contactInfoCache;

    toggleSpeaker = createActionIntent(NewReturnToCallActionReceiver.ACTION_TOGGLE_SPEAKER);
    showSpeakerSelect =
        createActionIntent(NewReturnToCallActionReceiver.ACTION_SHOW_AUDIO_ROUTE_SELECTOR);
    toggleMute = createActionIntent(NewReturnToCallActionReceiver.ACTION_TOGGLE_MUTE);
    endCall = createActionIntent(NewReturnToCallActionReceiver.ACTION_END_CALL);
    fullScreen = createActionIntent(NewReturnToCallActionReceiver.ACTION_RETURN_TO_CALL);

    InCallPresenter.getInstance().addInCallUiListener(this);
    CallList.getInstance().addListener(this);
    AudioModeProvider.getInstance().addListener(this);
    audioState = AudioModeProvider.getInstance().getAudioState();
  }

  public void tearDown() {
    hide();
    InCallPresenter.getInstance().removeInCallUiListener(this);
    CallList.getInstance().removeListener(this);
    AudioModeProvider.getInstance().removeListener(this);
  }

  @Override
  public void onUiShowing(boolean showing) {
    if (showing) {
      hide();
    } else {
      if (getCall() != null) {
        show();
      }
    }
  }

  private void hide() {
    if (bubble != null) {
      bubble.hide();
    } else {
      LogUtil.i("ReturnToCallController.hide", "hide() called without calling show()");
    }
  }

  private void hideAndReset() {
    if (bubble != null) {
      bubble.hideAndReset();
    } else {
      LogUtil.i("ReturnToCallController.reset", "reset() called without calling show()");
    }
  }

  private void show() {
    if (bubble == null) {
      bubble = startBubble();
    } else {
      bubble.show();
    }
    startContactInfoSearch();
  }

  @VisibleForTesting
  public NewBubble startBubble() {
    if (!NewBubble.canShowBubbles(context)) {
      LogUtil.i("ReturnToCallController.startNewBubble", "can't show bubble, no permission");
      return null;
    }
    NewBubble returnToCallBubble = NewBubble.createBubble(context, generateBubbleInfo());
    returnToCallBubble.show();
    return returnToCallBubble;
  }

  @Override
  public void onIncomingCall(DialerCall call) {}

  @Override
  public void onUpgradeToVideo(DialerCall call) {}

  @Override
  public void onSessionModificationStateChange(DialerCall call) {}

  @Override
  public void onCallListChange(CallList callList) {}

  @Override
  public void onDisconnect(DialerCall call) {
    LogUtil.enterBlock("ReturnToCallController.onDisconnect");
    if (bubble != null && bubble.isVisible() && (getCall() == null)) {
      // Show "Call ended" and hide bubble when there is no outgoing, active or background call
      LogUtil.i("ReturnToCallController.onDisconnect", "show call ended and hide bubble");
      // Don't show text if it's Duo upgrade
      // It doesn't work for Duo fallback upgrade since we're not considered in call
      if (!TelecomUtil.isInCall(context) || CallList.getInstance().getIncomingCall() != null) {
        bubble.showText(context.getText(R.string.incall_call_ended));
      }
      hideAndReset();
    } else {
      startContactInfoSearch();
    }
  }

  @Override
  public void onWiFiToLteHandover(DialerCall call) {}

  @Override
  public void onHandoverToWifiFailed(DialerCall call) {}

  @Override
  public void onInternationalCallOnWifi(@NonNull DialerCall call) {}

  @Override
  public void onAudioStateChanged(CallAudioState audioState) {
    this.audioState = audioState;
    if (bubble != null) {
      bubble.updateActions(generateActions());
    }
  }

  private void startContactInfoSearch() {
    DialerCall dialerCall = getCall();
    if (dialerCall != null) {
      contactInfoCache.findInfo(
          dialerCall, false /* isIncoming */, new ReturnToCallContactInfoCacheCallback(this));
    }
  }

  private DialerCall getCall() {
    DialerCall dialerCall = CallList.getInstance().getOutgoingCall();
    if (dialerCall == null) {
      dialerCall = CallList.getInstance().getActiveOrBackgroundCall();
    }
    return dialerCall;
  }

  private void onPhotoAvatarReceived(@NonNull Drawable photo) {
    if (bubble != null) {
      bubble.updatePhotoAvatar(photo);
    }
  }

  private void onLetterTileAvatarReceived(@NonNull Drawable photo) {
    if (bubble != null) {
      bubble.updateAvatar(photo);
    }
  }

  private NewBubbleInfo generateBubbleInfo() {
    return NewBubbleInfo.builder()
        .setPrimaryColor(context.getResources().getColor(R.color.dialer_theme_color, null))
        .setPrimaryIcon(Icon.createWithResource(context, R.drawable.on_going_call))
        .setStartingYPosition(
            context.getResources().getDimensionPixelOffset(R.dimen.return_to_call_initial_offset_y))
        .setActions(generateActions())
        .build();
  }

  @NonNull
  private List<Action> generateActions() {
    List<Action> actions = new ArrayList<>();
    SpeakerButtonInfo speakerButtonInfo = new SpeakerButtonInfo(audioState, IconSize.SIZE_24_DP);

    // Return to call
    actions.add(
        Action.builder()
            .setIconDrawable(context.getDrawable(R.drawable.quantum_ic_exit_to_app_vd_theme_24))
            .setIntent(fullScreen)
            .setName(context.getText(R.string.bubble_return_to_call))
            .build());
    // Mute/unmute
    actions.add(
        Action.builder()
            .setIconDrawable(context.getDrawable(R.drawable.quantum_ic_mic_off_white_24))
            .setChecked(audioState.isMuted())
            .setIntent(toggleMute)
            .setName(context.getText(R.string.incall_label_mute))
            .build());
    // Speaker/audio selector
    actions.add(
        Action.builder()
            .setIconDrawable(context.getDrawable(speakerButtonInfo.icon))
            .setName(context.getText(speakerButtonInfo.label))
            .setChecked(speakerButtonInfo.isChecked)
            .setIntent(speakerButtonInfo.checkable ? toggleSpeaker : showSpeakerSelect)
            .build());
    // End call
    actions.add(
        Action.builder()
            .setIconDrawable(context.getDrawable(R.drawable.quantum_ic_call_end_vd_theme_24))
            .setIntent(endCall)
            .setName(context.getText(R.string.incall_label_end_call))
            .build());
    return actions;
  }

  @NonNull
  private PendingIntent createActionIntent(String action) {
    Intent intent = new Intent(context, NewReturnToCallActionReceiver.class);
    intent.setAction(action);
    return PendingIntent.getBroadcast(context, 0, intent, 0);
  }

  @NonNull
  private LetterTileDrawable createLettleTileDrawable(
      DialerCall dialerCall, ContactCacheEntry entry) {
    String preferredName =
        ContactDisplayUtils.getPreferredDisplayName(
            entry.namePrimary,
            entry.nameAlternative,
            ContactsPreferencesFactory.newContactsPreferences(context));
    if (TextUtils.isEmpty(preferredName)) {
      preferredName = entry.number;
    }

    LetterTileDrawable letterTile = new LetterTileDrawable(context.getResources());
    letterTile.setCanonicalDialerLetterTileDetails(
        dialerCall.updateNameIfRestricted(preferredName),
        entry.lookupKey,
        LetterTileDrawable.SHAPE_CIRCLE,
        LetterTileDrawable.getContactTypeFromPrimitives(
            dialerCall.isVoiceMailNumber(),
            dialerCall.isSpam(),
            entry.isBusiness,
            dialerCall.getNumberPresentation(),
            dialerCall.isConferenceCall()));
    return letterTile;
  }

  private static class ReturnToCallContactInfoCacheCallback implements ContactInfoCacheCallback {

    private final WeakReference<NewReturnToCallController> newReturnToCallControllerWeakReference;

    private ReturnToCallContactInfoCacheCallback(
        NewReturnToCallController newReturnToCallController) {
      newReturnToCallControllerWeakReference = new WeakReference<>(newReturnToCallController);
    }

    @Override
    public void onContactInfoComplete(String callId, ContactCacheEntry entry) {
      NewReturnToCallController newReturnToCallController =
          newReturnToCallControllerWeakReference.get();
      if (newReturnToCallController == null) {
        return;
      }
      if (entry.photo != null) {
        newReturnToCallController.onPhotoAvatarReceived(entry.photo);
      } else {
        DialerCall dialerCall = CallList.getInstance().getCallById(callId);
        newReturnToCallController.onLetterTileAvatarReceived(
            newReturnToCallController.createLettleTileDrawable(dialerCall, entry));
      }
    }

    @Override
    public void onImageLoadComplete(String callId, ContactCacheEntry entry) {
      NewReturnToCallController newReturnToCallController =
          newReturnToCallControllerWeakReference.get();
      if (newReturnToCallController == null) {
        return;
      }
      if (entry.photo != null) {
        newReturnToCallController.onPhotoAvatarReceived(entry.photo);
      } else {
        DialerCall dialerCall = CallList.getInstance().getCallById(callId);
        newReturnToCallController.onLetterTileAvatarReceived(
            newReturnToCallController.createLettleTileDrawable(dialerCall, entry));
      }
    }
  }
}
