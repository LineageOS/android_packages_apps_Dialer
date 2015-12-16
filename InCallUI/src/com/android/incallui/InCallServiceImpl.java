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
import android.os.Build;
import android.os.IBinder;
import android.telecom.AudioState;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import android.telecom.Phone;

import com.android.contacts.common.compat.SdkVersionOverride;

/**
 * Used to receive updates about calls from the Telecom component.  This service is bound to
 * Telecom while there exist calls which potentially require UI. This includes ringing (incoming),
 * dialing (outgoing), and active calls. When the last call is disconnected, Telecom will unbind to
 * the service triggering InCallActivity (via CallList) to finish soon after.
 */
public class InCallServiceImpl extends InCallService {

    @Override
    public void onCallAudioStateChanged(CallAudioState audioState) {
        AudioModeProvider.getInstance().onAudioStateChanged(audioState.isMuted(),
                audioState.getRoute(), audioState.getSupportedRouteMask());
    }

    @Override
    public void onBringToForeground(boolean showDialpad) {
        InCallPresenter.getInstance().onBringToForeground(showDialpad);
    }

    @Override
    public void onCallAdded(Call call) {
        InCallPresenter.getInstance().onCallAdded(call);
    }

    @Override
    public void onCallRemoved(Call call) {
        InCallPresenter.getInstance().onCallRemoved(call);
    }

    @Override
    public void onCanAddCallChanged(boolean canAddCall) {
        InCallPresenter.getInstance().onCanAddCallChanged(canAddCall);
    }

    @Override
    public IBinder onBind(Intent intent) {
        final Context context = getApplicationContext();
        final ContactInfoCache contactInfoCache = ContactInfoCache.getInstance(context);
        InCallPresenter.getInstance().setUp(
                getApplicationContext(),
                CallList.getInstance(),
                AudioModeProvider.getInstance(),
                new StatusBarNotifier(context, contactInfoCache),
                contactInfoCache,
                new ProximitySensor(
                        context,
                        AudioModeProvider.getInstance(),
                        new AccelerometerListener(context))
                );
        InCallPresenter.getInstance().onServiceBind();
        InCallPresenter.getInstance().maybeStartRevealAnimation(intent);
        TelecomAdapter.getInstance().setInCallService(this);

        return super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        super.onUnbind(intent);

        InCallPresenter.getInstance().onServiceUnbind();
        tearDown();

        return false;
    }

    private void tearDown() {
        Log.v(this, "tearDown");
        // Tear down the InCall system
        TelecomAdapter.getInstance().clearInCallService();
        InCallPresenter.getInstance().tearDown();
    }

    /*
     * Compatibility code for devices running the L sdk. In that version of the sdk, InCallService
     * callbacks were registered via a android.telecom.Phone$Listener. These callbacks typically
     * correspond 1:1 to callbacks now found in android.telecom.InCallService so the compatibility
     * code forwards to those methods.
     */
    private Phone.Listener mPhoneListener = new Phone.Listener() {
        @Override
        public void onAudioStateChanged(Phone phone, AudioState audioState) {
            /*
             * Need to use reflection here; in M these are private fields retrieved through getters,
             * but in L they are public fields without getters.
             */
            try {
                boolean isMuted = AudioState.class.getField("isMuted").getBoolean(audioState);
                int route = AudioState.class.getField("route").getInt(audioState);
                int supportedRouteMask = AudioState.class.getField("supportedRouteMask")
                        .getInt(audioState);
                AudioModeProvider.getInstance()
                        .onAudioStateChanged(isMuted, route, supportedRouteMask);
            } catch (ReflectiveOperationException e) {
                Log.e(this, "Unable to use reflection to retrieve AudioState fields", e);
            }
        }

        @Override
        public void onBringToForeground(Phone phone, boolean showDialpad) {
            InCallServiceImpl.this.onBringToForeground(showDialpad);
        }

        @Override
        public void onCallAdded(Phone phone, Call call) {
            InCallServiceImpl.this.onCallAdded(call);
        }

        @Override
        public void onCallRemoved(Phone phone, Call call) {
            InCallServiceImpl.this.onCallRemoved(call);
        }
    };

    private Phone mPhone;

    @Override
    public void onPhoneCreated(Phone phone) {
        if (SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.M) >= Build.VERSION_CODES.M) {
            return;
        }
        mPhone = phone;
        mPhone.addListener(mPhoneListener);
    }

    @Override
    public void onPhoneDestroyed(Phone phone) {
        if (SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.M) >= Build.VERSION_CODES.M) {
            return;
        }
        mPhone.removeListener(mPhoneListener);
        mPhone = null;
    }

    /*
     * setMuted and setAudioRoute are final in InCallService so compat methods are
     * used to perform the needed branching logic based on sdk version
     */
    public void setMutedCompat(boolean state) {
        if (SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.M) >= Build.VERSION_CODES.M) {
            super.setMuted(state);
            return;
        }
        mPhone.setMuted(state);
    }

    public void setAudioRouteCompat(int route) {
        if (SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.M) >= Build.VERSION_CODES.M) {
            super.setAudioRoute(route);
            return;
        }
        mPhone.setAudioRoute(route);
    }
}
