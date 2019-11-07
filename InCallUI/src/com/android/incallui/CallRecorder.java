/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package com.android.incallui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.XmlResourceParser;
import android.location.Country;
import android.location.CountryDetector;
import android.location.CountryListener;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.services.callrecorder.CallRecorderService;
import com.android.services.callrecorder.CallRecordingDataStore;
import com.android.services.callrecorder.common.CallRecording;
import com.android.services.callrecorder.common.ICallRecorderService;

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
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private static final HashMap<String, Boolean> RECORD_ALLOWED_STATE_BY_COUNTRY = new HashMap<>();

    private Object mLock = new Object();
    private String mCurrentCountryIso;

    private static CallRecorder sInstance = null;

    private Context mContext;
    private boolean mInitialized = false;
    private ICallRecorderService mService = null;

    private HashSet<RecordingProgressListener> mProgressListeners =
            new HashSet<RecordingProgressListener>();
    private Handler mHandler = new Handler();

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = ICallRecorderService.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };

    public static CallRecorder getInstance() {
        if (sInstance == null) {
            sInstance = new CallRecorder();
        }
        return sInstance;
    }

    public boolean isEnabled() {
        return CallRecorderService.isEnabled(mContext);
    }

    private String getCountryIsoFromCountry(Country country) {
        if(country == null) {
            // Fallback to Locale if there are issues with CountryDetector
            Log.w(TAG, "Value for country was null. Falling back to Locale.");
            return Locale.getDefault().getCountry();
        }
        return country.getCountryIso();
    }
    /**
     * Get the current country code
     *
     * @return the ISO 3166-1 two letters country code of current country.
     */
    public String getCurrentCountryIso() {
        synchronized (mLock) {
            if (mCurrentCountryIso == null) {
                Log.i(TAG, "Country cache is null. Detecting Country and Setting Cache...");
                final CountryDetector countryDetector =
                        (CountryDetector) mContext.getSystemService(Context.COUNTRY_DETECTOR);
                Country country = null;
                if (countryDetector != null) {
                    country = countryDetector.detectCountry();
                    countryDetector.addCountryListener((newCountry) -> {
                        synchronized (mLock) {
                            Log.i(TAG, "Country ISO changed. Retrieving new ISO...");
                            mCurrentCountryIso = getCountryIsoFromCountry(newCountry);
                            Log.i(TAG, "Detected country ISO: " + mCurrentCountryIso);
                        }
                    }, Looper.getMainLooper());
                }
                mCurrentCountryIso = getCountryIsoFromCountry(country);
                Log.i(TAG, "Detected country ISO: " + mCurrentCountryIso);
            }
            return mCurrentCountryIso;
        }
    }

    public boolean canRecordInCurrentCountry() {
        if (!isEnabled()) {
            return false;
        }
        if (RECORD_ALLOWED_STATE_BY_COUNTRY.isEmpty()) {
            loadAllowedStates();
        }

        String currentCountryIso = getCurrentCountryIso();
        Boolean allowedState = RECORD_ALLOWED_STATE_BY_COUNTRY.get(currentCountryIso);

        return allowedState != null && allowedState;
    }

    private CallRecorder() {
        CallList.getInstance().addListener(this);
    }

    public void setUp(Context context) {
        mContext = context.getApplicationContext();
    }

    private void initialize() {
        if (isEnabled() && !mInitialized) {
            Intent serviceIntent = new Intent(mContext, CallRecorderService.class);
            mContext.bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);
            mInitialized = true;
        }
    }

    private void uninitialize() {
        if (mInitialized) {
            mContext.unbindService(mConnection);
            mInitialized = false;
        }
    }

    public boolean startRecording(final String phoneNumber, final long creationTime) {
        if (mService == null) {
            return false;
        }

        try {
            if (mService.startRecording(phoneNumber, creationTime)) {
                for (RecordingProgressListener l : mProgressListeners) {
                    l.onStartRecording();
                }
                mUpdateRecordingProgressTask.run();
                return true;
            } else {
                Toast.makeText(mContext, R.string.call_recording_failed_message,
                        Toast.LENGTH_SHORT).show();
            }
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to start recording " + phoneNumber + ", " +
                    new Date(creationTime), e);
        }

        return false;
    }

    public boolean isRecording() {
        if (mService == null) {
            return false;
        }

        try {
            return mService.isRecording();
        } catch (RemoteException e) {
            Log.w(TAG, "Exception checking recording status", e);
        }
        return false;
    }

    public CallRecording getActiveRecording() {
        if (mService == null) {
            return null;
        }

        try {
            return mService.getActiveRecording();
        } catch (RemoteException e) {
            Log.w("Exception getting active recording", e);
        }
        return null;
    }

    public void finishRecording() {
        if (mService != null) {
            try {
                final CallRecording recording = mService.stopRecording();
                if (recording != null) {
                    if (!TextUtils.isEmpty(recording.phoneNumber)) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                CallRecordingDataStore dataStore = new CallRecordingDataStore();
                                dataStore.open(mContext);
                                dataStore.putRecording(recording);
                                dataStore.close();
                            }
                        }).start();
                    } else {
                        // Data store is an index by number so that we can link recordings in the
                        // call detail page.  If phone number is not available (conference call or
                        // unknown number) then just display a toast.
                        String msg = mContext.getResources().getString(
                                R.string.call_recording_file_location, recording.fileName);
                        Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to stop recording", e);
            }
        }

        for (RecordingProgressListener l : mProgressListeners) {
            l.onStopRecording();
        }
        mHandler.removeCallbacks(mUpdateRecordingProgressTask);
    }

    //
    // Call list listener methods.
    //
    @Override
    public void onIncomingCall(Call call) {
        // do nothing
    }

    @Override
    public void onCallListChange(final CallList callList) {
        if (!mInitialized && callList.getActiveCall() != null) {
            // we'll come here if this is the first active call
            initialize();
        } else {
            // we can come down this branch to resume a call that was on hold
            CallRecording active = getActiveRecording();
            if (active != null) {
                Call call = callList.getCallWithStateAndNumber(Call.State.ONHOLD,
                        active.phoneNumber);
                if (call != null) {
                    // The call associated with the active recording has been placed
                    // on hold, so stop the recording.
                    finishRecording();
                }
            }
        }
    }

    @Override
    public void onDisconnect(final Call call) {
        CallRecording active = getActiveRecording();
        if (active != null && TextUtils.equals(call.getNumber(), active.phoneNumber)) {
            // finish the current recording if the call gets disconnected
            finishRecording();
        }

        // tear down the service if there are no more active calls
        if (CallList.getInstance().getActiveCall() == null) {
            uninitialize();
        }
    }

    @Override
    public void onUpgradeToVideo(Call call) {}

    // allow clients to listen for recording progress updates
    public interface RecordingProgressListener {
        public void onStartRecording();
        public void onStopRecording();
        public void onRecordingTimeProgress(long elapsedTimeMs);
    }

    public void addRecordingProgressListener(RecordingProgressListener listener) {
        mProgressListeners.add(listener);
    }

    public void removeRecordingProgressListener(RecordingProgressListener listener) {
        mProgressListeners.remove(listener);
    }

    private static final int UPDATE_INTERVAL = 500;

    private Runnable mUpdateRecordingProgressTask = new Runnable() {
        @Override
        public void run() {
            CallRecording active = getActiveRecording();
            if (active != null) {
                long elapsed = System.currentTimeMillis() - active.startRecordingTime;
                for (RecordingProgressListener l : mProgressListeners) {
                    l.onRecordingTimeProgress(elapsed);
                }
            }
            mHandler.postDelayed(mUpdateRecordingProgressTask, UPDATE_INTERVAL);
        }
    };

    private void loadAllowedStates() {
        XmlResourceParser parser = mContext.getResources().getXml(R.xml.call_record_states);
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
}
