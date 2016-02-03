package com.android.dialer.incall;

import android.content.ComponentName;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;

import com.android.phone.common.incall.CallMethodInfo;
import com.android.phone.common.incall.CallMethodHelper;
import com.android.phone.common.incall.CallMethodSpinnerAdapter;
import com.android.phone.common.incall.CallMethodUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Helper class for generating and managing multiple provider selection spinners across the
 * Dialer UI
 */
public class CallMethodSpinnerHelper {

    /**
     * Listener that is called when the spinners are used
     */
    public interface OnCallMethodChangedListener {
        void onCallMethodChangedListener(CallMethodInfo cmi);
    }


    /**
     * Creates the initial spinner configurations
     * @param context
     * @param callMethodSpinner The spinner
     * @param callMethodChanged the listener to be called when the call method is changed
     */
    public static void setupCallMethodSpinner(Context context,
                                              Spinner callMethodSpinner,
                                              final OnCallMethodChangedListener callMethodChanged) {
        CallMethodSpinnerAdapter callMethodSpinnerAdapter =
                new CallMethodSpinnerAdapter(context, new ArrayList<CallMethodInfo>());

        callMethodSpinner.setAdapter(callMethodSpinnerAdapter);
        callMethodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                CallMethodInfo callMethodInfo = (CallMethodInfo) parent.getItemAtPosition(position);
                callMethodChanged.onCallMethodChangedListener(callMethodInfo);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Nothing was selected, do not change current selection
            }
        });
    }

    /**
     * Updates the spinner with new call methods when available.
     * @param context
     * @param callMethodSpinner the spinner
     * @param changeListener the listener called when something is changed
     * @param lastKnownCallMethod the previous call method of either this spinner
     *                            or the other spinners
     */
    public static void updateCallMethodSpinnerAdapter(Context context, Spinner callMethodSpinner,
                                               final OnCallMethodChangedListener changeListener,
                                               String lastKnownCallMethod) {
        CallMethodSpinnerAdapter callMethodSpinnerAdapter = (CallMethodSpinnerAdapter)
                callMethodSpinner.getAdapter();
        callMethodSpinnerAdapter.clear();

        List<CallMethodInfo> sims = CallMethodUtils.getSimInfoList(context);
        HashMap<ComponentName, CallMethodInfo> availableProviders =
                CallMethodHelper.getAllCallMethods();

        // Add available SIMs or EmergencyCallMethod
        if (sims.isEmpty() && !availableProviders.isEmpty()) {
            // Show "emergency call" option in spinner
            callMethodSpinnerAdapter.add(CallMethodInfo.getEmergencyCallMethod(context));
        } else {
            // Show available SIMs in spinner
            callMethodSpinnerAdapter.addAll(sims);
        }

        callMethodSpinnerAdapter.addAll(availableProviders);

        // Set currently selected CallMethod
        if (sims.size() <= 1 && availableProviders.isEmpty()) {
            // zero or one sim and no providers
            callMethodSpinner.setVisibility(View.GONE);
            CallMethodInfo info = (callMethodSpinnerAdapter.getCount() > 0) ?
                    callMethodSpinnerAdapter.getItem(0) : null;
            changeListener.onCallMethodChangedListener(info);
        } else {
            // multiple call methods or single provider
            int position = 0;
            if (!TextUtils.isEmpty(lastKnownCallMethod)) {
                position = callMethodSpinnerAdapter.getPosition(lastKnownCallMethod);
            } else {
                CallMethodInfo defaultSim = CallMethodUtils.getDefaultSimInfo(context);
                if (defaultSim != null) {
                    position = callMethodSpinnerAdapter.getPosition(
                            CallMethodSpinnerAdapter.getCallMethodKey(defaultSim));
                }
            }
            
            changeListener.onCallMethodChangedListener(callMethodSpinnerAdapter.getItem(position));
            callMethodSpinner.setSelection(position);
        }
    }

}
