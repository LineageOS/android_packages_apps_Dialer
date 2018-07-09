/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Trace;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler;
import com.android.dialer.feedback.FeedbackComponent;
import com.android.incallui.audiomode.AudioModeProvider;
import com.android.incallui.call.CallList;
import com.android.incallui.call.CallRecorder;
import com.android.incallui.call.ExternalCallList;
import com.android.incallui.call.TelecomAdapter;
import com.android.incallui.speakeasy.SpeakEasyCallManager;
import com.android.incallui.speakeasy.SpeakEasyComponent;

/**
 * Used to receive updates about calls from the Telecom component. This service is bound to Telecom
 * while there exist calls which potentially require UI. This includes ringing (incoming), dialing
 * (outgoing), and active calls. When the last call is disconnected, Telecom will unbind to the
 * service triggering InCallActivity (via CallList) to finish soon after.
 */
public class InCallServiceImpl extends InCallService {

  private ReturnToCallController returnToCallController;
  private CallList.Listener feedbackListener;
  // We only expect there to be one speakEasyCallManager to be instantiated at a time.
  // We did not use a singleton SpeakEasyCallManager to avoid holding on to state beyond the
  // lifecycle of this service, because the singleton is associated with the state of the
  // Application, not this service.
  private SpeakEasyCallManager speakEasyCallManager;

  @Override
  public void onCallAudioStateChanged(CallAudioState audioState) {
    Trace.beginSection("InCallServiceImpl.onCallAudioStateChanged");
    AudioModeProvider.getInstance().onAudioStateChanged(audioState);
    Trace.endSection();
  }

  @Override
  public void onBringToForeground(boolean showDialpad) {
    Trace.beginSection("InCallServiceImpl.onBringToForeground");
    InCallPresenter.getInstance().onBringToForeground(showDialpad);
    Trace.endSection();
  }

  @Override
  public void onCallAdded(Call call) {
    Trace.beginSection("InCallServiceImpl.onCallAdded");
    InCallPresenter.getInstance().onCallAdded(call);
    Trace.endSection();
  }

  @Override
  public void onCallRemoved(Call call) {
    Trace.beginSection("InCallServiceImpl.onCallRemoved");
    speakEasyCallManager.onCallRemoved(CallList.getInstance().getDialerCallFromTelecomCall(call));

    InCallPresenter.getInstance().onCallRemoved(call);
    Trace.endSection();
  }

  @Override
  public void onCanAddCallChanged(boolean canAddCall) {
    Trace.beginSection("InCallServiceImpl.onCanAddCallChanged");
    InCallPresenter.getInstance().onCanAddCallChanged(canAddCall);
    Trace.endSection();
  }

  @Override
  public void onCreate() {
    super.onCreate();
    this.speakEasyCallManager = SpeakEasyComponent.get(this).speakEasyCallManager();
  }

  @Override
  public IBinder onBind(Intent intent) {
    Trace.beginSection("InCallServiceImpl.onBind");
    final Context context = getApplicationContext();
    final ContactInfoCache contactInfoCache = ContactInfoCache.getInstance(context);
    AudioModeProvider.getInstance().initializeAudioState(this);
    InCallPresenter.getInstance()
        .setUp(
            context,
            CallList.getInstance(),
            new ExternalCallList(),
            new StatusBarNotifier(context, contactInfoCache),
            new ExternalCallNotifier(context, contactInfoCache),
            contactInfoCache,
            new ProximitySensor(
                context, AudioModeProvider.getInstance(), new AccelerometerListener(context)),
            new FilteredNumberAsyncQueryHandler(context),
            speakEasyCallManager);
    InCallPresenter.getInstance().onServiceBind();
    InCallPresenter.getInstance().maybeStartRevealAnimation(intent);
    TelecomAdapter.getInstance().setInCallService(this);
    CallRecorder.getInstance().setUp(context);
    returnToCallController =
        new ReturnToCallController(this, ContactInfoCache.getInstance(context));
    feedbackListener = FeedbackComponent.get(context).getCallFeedbackListener();
    CallList.getInstance().addListener(feedbackListener);

    IBinder iBinder = super.onBind(intent);
    Trace.endSection();
    return iBinder;
  }

  @Override
  public boolean onUnbind(Intent intent) {
    Trace.beginSection("InCallServiceImpl.onUnbind");
    super.onUnbind(intent);

    InCallPresenter.getInstance().onServiceUnbind();
    tearDown();

    Trace.endSection();
    return false;
  }

  private void tearDown() {
    Trace.beginSection("InCallServiceImpl.tearDown");
    Log.v(this, "tearDown");
    // Tear down the InCall system
    InCallPresenter.getInstance().tearDown();
    TelecomAdapter.getInstance().clearInCallService();
    if (returnToCallController != null) {
      returnToCallController.tearDown();
      returnToCallController = null;
    }
    if (feedbackListener != null) {
      CallList.getInstance().removeListener(feedbackListener);
      feedbackListener = null;
    }
    Trace.endSection();
  }
}
