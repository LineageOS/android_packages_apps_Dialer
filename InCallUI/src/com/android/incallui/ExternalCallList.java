package com.android.incallui;

import com.google.common.base.Preconditions;

import com.android.contacts.common.compat.CallSdkCompat;

import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.util.ArraySet;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the external calls known to the InCall UI.
 *
 * External calls are those with {@link android.telecom.Call.Details#PROPERTY_IS_EXTERNAL_CALL}.
 */
public class ExternalCallList {

    public interface ExternalCallListener {
        void onExternalCallAdded(Call call);
        void onExternalCallRemoved(Call call);
        void onExternalCallUpdated(Call call);
    }

    /**
     * Handles {@link android.telecom.Call.Callback} callbacks.
     */
    private final Call.Callback mTelecomCallCallback = new Call.Callback() {
        @Override
        public void onDetailsChanged(Call call, Call.Details details) {
            notifyExternalCallUpdated(call);
        }
    };

    private final Set<Call> mExternalCalls = new ArraySet<>();
    private final Set<ExternalCallListener> mExternalCallListeners = Collections.newSetFromMap(
            new ConcurrentHashMap<ExternalCallListener, Boolean>(8, 0.9f, 1));

    /**
     * Begins tracking an external call and notifies listeners of the new call.
     */
    public void onCallAdded(Call telecomCall) {
        Preconditions.checkArgument(telecomCall.getDetails()
                .hasProperty(CallSdkCompat.Details.PROPERTY_IS_EXTERNAL_CALL));
        mExternalCalls.add(telecomCall);
        telecomCall.registerCallback(mTelecomCallCallback, new Handler(Looper.getMainLooper()));
        notifyExternalCallAdded(telecomCall);
    }

    /**
     * Stops tracking an external call and notifies listeners of the removal of the call.
     */
    public void onCallRemoved(Call telecomCall) {
        Preconditions.checkArgument(mExternalCalls.contains(telecomCall));
        mExternalCalls.remove(telecomCall);
        telecomCall.unregisterCallback(mTelecomCallCallback);
        notifyExternalCallRemoved(telecomCall);
    }

    /**
     * Adds a new listener to external call events.
     */
    public void addExternalCallListener(ExternalCallListener listener) {
        mExternalCallListeners.add(Preconditions.checkNotNull(listener));
    }

    /**
     * Removes a listener to external call events.
     */
    public void removeExternalCallListener(ExternalCallListener listener) {
        Preconditions.checkArgument(mExternalCallListeners.contains(listener));
        mExternalCallListeners.remove(Preconditions.checkNotNull(listener));
    }

    /**
     * Notifies listeners of the addition of a new external call.
     */
    private void notifyExternalCallAdded(Call call) {
        for (ExternalCallListener listener : mExternalCallListeners) {
            listener.onExternalCallAdded(call);
        }
    }

    /**
     * Notifies listeners of the removal of an external call.
     */
    private void notifyExternalCallRemoved(Call call) {
        for (ExternalCallListener listener : mExternalCallListeners) {
            listener.onExternalCallRemoved(call);
        }
    }

    /**
     * Notifies listeners of changes to an external call.
     */
    private void notifyExternalCallUpdated(Call call) {
        for (ExternalCallListener listener : mExternalCallListeners) {
            listener.onExternalCallUpdated(call);
        }
    }
}
