/*
 * Copyright (C) 2014 The CyanogenMod Project
 * Copyright (C) 2023-2024 The LineageOS Project
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

package com.android.incallui.call;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.XmlResourceParser;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.dialer.R;
import com.android.dialer.callrecord.CallRecording;
import com.android.dialer.callrecord.CallRecordingDataStore;
import com.android.dialer.callrecord.ICallRecorderService;
import com.android.dialer.callrecord.impl.CallRecorderService;
import com.android.dialer.location.GeoUtil;
import com.android.incallui.call.state.DialerCallState;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

/**
 * InCall UI's interface to the call recorder
 *
 * Manages the call recorder service lifecycle.  We bind to the service whenever an active call
 * is established, and unbind when all calls have been disconnected.
 */
public class CallRecorder implements CallList.Listener {
  public static final String TAG = "CallRecorder";

  public static final String[] REQUIRED_PERMISSIONS = new String[] {
    android.Manifest.permission.RECORD_AUDIO
  };
  private static final HashMap<String, Boolean> RECORD_ALLOWED_STATE_BY_COUNTRY = new HashMap<>();

  private static CallRecorder instance = null;
  private Context context;
  private boolean initialized = false;
  private ICallRecorderService service = null;
  private Pair<String, Long> pendingRecordLaunch = null;

  private final HashSet<RecordingProgressListener> progressListeners = new HashSet<>();
  private final Handler handler = new Handler(Looper.getMainLooper());

  private final ServiceConnection connection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      CallRecorder.this.service = ICallRecorderService.Stub.asInterface(service);
      Pair<String, Long> pending = CallRecorder.this.pendingRecordLaunch;
      if (pending != null) {
        CallRecorder.this.startRecording(pending.first, pending.second);
        CallRecorder.this.pendingRecordLaunch = null;
      }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      CallRecorder.this.service = null;
    }
  };

  public static CallRecorder getInstance() {
    if (instance == null) {
      instance = new CallRecorder();
    }
    return instance;
  }

  public boolean isEnabled() {
    return CallRecorderService.isEnabled(context);
  }

  public boolean canRecordInCurrentCountry() {
      if (!isEnabled()) {
          return false;
      }
      if (RECORD_ALLOWED_STATE_BY_COUNTRY.isEmpty()) {
          loadAllowedStates();
      }

      String currentCountryIso = GeoUtil.getCurrentCountryIso(context);
      Boolean allowedState = RECORD_ALLOWED_STATE_BY_COUNTRY.get(currentCountryIso);

      return allowedState != null && allowedState;
  }

  private CallRecorder() {
    CallList.getInstance().addListener(this);
  }

  public void setUp(Context context) {
    this.context = context.getApplicationContext();
  }

  private void initialize() {
    if (isEnabled() && !initialized) {
      Intent serviceIntent = new Intent(context, CallRecorderService.class);
      context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
      initialized = true;
    }
  }

  private void uninitialize() {
    if (initialized) {
      context.unbindService(connection);
      service = null;
      initialized = false;
    }
  }

  public boolean startRecording(final String phoneNumber, final long creationTime) {
    if (service == null) {
      pendingRecordLaunch = Pair.create(phoneNumber, creationTime);
      return false;
    }

    try {
      if (service.startRecording(phoneNumber, creationTime)) {
        for (RecordingProgressListener l : progressListeners) {
          l.onStartRecording();
        }
        updateRecordingProgressTask.run();
        return true;
      } else {
        Toast.makeText(context, R.string.call_recording_failed_message, Toast.LENGTH_SHORT)
            .show();
      }
    } catch (RemoteException e) {
      Log.w(TAG, "Failed to start recording " + phoneNumber + ", " + new Date(creationTime), e);
    }

    return false;
  }

  public boolean isRecording() {
    if (service == null) {
      return false;
    }

    try {
      return service.isRecording();
    } catch (RemoteException e) {
      Log.w(TAG, "Exception checking recording status", e);
    }
    return false;
  }

  public CallRecording getActiveRecording() {
    if (service == null) {
      return null;
    }

    try {
      return service.getActiveRecording();
    } catch (RemoteException e) {
      Log.w("Exception getting active recording", e);
    }
    return null;
  }

  public void finishRecording() {
    pendingRecordLaunch = null;
    if (service != null) {
      try {
        final CallRecording recording = service.stopRecording();
        if (recording != null) {
          if (!TextUtils.isEmpty(recording.phoneNumber)) {
            new Thread(() -> {
              CallRecordingDataStore dataStore = new CallRecordingDataStore();
              dataStore.open(context);
              dataStore.putRecording(recording);
              dataStore.close();
            }).start();
          } else {
            // Data store is an index by number so that we can link recordings in the
            // call detail page.  If phone number is not available (conference call or
            // unknown number) then just display a toast.
            String msg = context.getResources().getString(
                R.string.call_recording_file_location, recording.fileName);
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
          }
        }
      } catch (RemoteException e) {
        Log.w(TAG, "Failed to stop recording", e);
      }
    }

    for (RecordingProgressListener l : progressListeners) {
      l.onStopRecording();
    }
    handler.removeCallbacks(updateRecordingProgressTask);
  }

  //
  // Call list listener methods.
  //
  @Override
  public void onIncomingCall(DialerCall call) {
    // do nothing
  }

  @Override
  public void onCallListChange(final CallList callList) {
    DialerCall activeCall = callList.getActiveCall();
    if (!initialized && activeCall != null) {
      // we'll come here if this is the first active call
      initialize();
      if (isCallRecordAutostart()) {
        startRecording(activeCall.getNumber(), activeCall.getCreationTimeMillis());
      }
    } else {
      String activePhoneNumber = getActiveRecordingPhoneNumber();
      if (activeCall != null) {
        if (!TextUtils.equals(activeCall.getNumber(), activePhoneNumber)) {
          // the call is replaced by another one
          if (activePhoneNumber != null) {
            finishRecording();
          }
          if (isCallRecordAutostart()) {
            startRecording(activeCall.getNumber(), activeCall.getCreationTimeMillis());
          }
        }
      } else {
        // the active call disappeared
        if (activePhoneNumber != null) {
          DialerCall call =
                  callList.getCallWithStateAndNumber(DialerCallState.ONHOLD, activePhoneNumber);
          if (call != null) {
            // The call associated with the active recording has been placed
            // on hold, so stop the recording.
            finishRecording();
          }
        }
      }
    }
  }

  @Nullable
  private String getActiveRecordingPhoneNumber() {
    String activePhoneNumber = null;
    CallRecording activeRecording = getActiveRecording();
    if (activeRecording != null) {
      activePhoneNumber = activeRecording.phoneNumber;
    } else if (pendingRecordLaunch != null) {
      activePhoneNumber = pendingRecordLaunch.first;
    }
    return activePhoneNumber;
  }

  @Override
  public void onDisconnect(final DialerCall call) {
    String activePhoneNumber = getActiveRecordingPhoneNumber();
    if (activePhoneNumber != null && TextUtils.equals(call.getNumber(), activePhoneNumber)) {
      // finish the current recording if the call gets disconnected
      finishRecording();
    }

    // tear down the service if there are no more active calls
    if (CallList.getInstance().getActiveCall() == null) {
      uninitialize();
    }
  }

  @Override
  public void onUpgradeToVideo(DialerCall call) {}

  @Override
  public void onSessionModificationStateChange(DialerCall call) {}

  @Override
  public void onWiFiToLteHandover(DialerCall call) {}

  @Override
  public void onHandoverToWifiFailed(DialerCall call) {}

  @Override
  public void onInternationalCallOnWifi(DialerCall call) {}

  // allow clients to listen for recording progress updates
  public interface RecordingProgressListener {
    void onStartRecording();
    void onStopRecording();
    void onRecordingTimeProgress(long elapsedTimeMs);
  }

  public void addRecordingProgressListener(RecordingProgressListener listener) {
    progressListeners.add(listener);
  }

  public void removeRecordingProgressListener(RecordingProgressListener listener) {
    progressListeners.remove(listener);
  }

  private static final int UPDATE_INTERVAL = 500;

  private final Runnable updateRecordingProgressTask = new Runnable() {
    @Override
    public void run() {
      CallRecording active = getActiveRecording();
      if (active != null) {
        long elapsed = System.currentTimeMillis() - active.startRecordingTime;
        for (RecordingProgressListener l : progressListeners) {
          l.onRecordingTimeProgress(elapsed);
        }
      }
      handler.postDelayed(this, UPDATE_INTERVAL);
    }
  };

  private void loadAllowedStates() {
    XmlResourceParser parser = context.getResources().getXml(R.xml.call_record_states);
    try {
        // Consume all START_DOCUMENT which can appear more than once.
        while (parser.next() == XmlPullParser.START_DOCUMENT) {}

        parser.require(XmlPullParser.START_TAG, null, "call-record-allowed-flags");

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            parser.require(XmlPullParser.START_TAG, null, "country");

            String iso = parser.getAttributeValue(null, "iso");
            String allowed = parser.getAttributeValue(null, "allowed");
            if (iso != null && ("true".equals(allowed) || "false".equals(allowed))) {
                for (String splittedIso : iso.split(",")) {
                    RECORD_ALLOWED_STATE_BY_COUNTRY.put(
                            splittedIso.toUpperCase(Locale.US), Boolean.valueOf(allowed));
                }
            } else {
                throw new XmlPullParserException("Unexpected country specification", parser, null);
            }
        }
        Log.d(TAG, "Loaded " + RECORD_ALLOWED_STATE_BY_COUNTRY.size() + " country records");
    } catch (XmlPullParserException | IOException e) {
        Log.e(TAG, "Could not parse allowed country list", e);
        RECORD_ALLOWED_STATE_BY_COUNTRY.clear();
    } finally {
        parser.close();
    }
  }

  private boolean isCallRecordAutostart() {
    if (!canRecordInCurrentCountry()) {
      return false;
    }

    // This replicates PreferenceManager.getDefaultSharedPreferences, except
    // that we need multi process preferences, as the pref is written in a separate
    // process (com.android.dialer vs. com.android.incallui)
    final String prefName = context.getPackageName() + "_preferences";
    final SharedPreferences prefs = context.createDeviceProtectedStorageContext()
            .getSharedPreferences(prefName, Context.MODE_MULTI_PROCESS);

    return prefs.getBoolean(context.getString(R.string.call_recording_autostart_key), false);
  }
}
