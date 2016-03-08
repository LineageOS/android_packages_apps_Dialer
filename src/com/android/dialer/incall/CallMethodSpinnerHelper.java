package com.android.dialer.incall;

import android.content.ComponentName;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;

import com.android.dialer.util.TelecomUtil;
import com.android.phone.common.incall.CallMethodInfo;
import com.android.phone.common.incall.CallMethodHelper;
import com.android.phone.common.incall.CallMethodSpinnerAdapter;
import com.android.phone.common.incall.CallMethodUtils;
import com.android.phone.common.util.VolteUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.android.phone.common.incall.CallMethodSpinnerAdapter.POSITION_UNKNOWN;

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
     * Creates the initial spinner configurations without volte icon
     * @param context
     * @param callMethodSpinner The spinner
     * @param callMethodChanged the listener to be called when the call method is changed
     */
    public static void setupCallMethodSpinner(Context context,
                                              Spinner callMethodSpinner,
                                              final OnCallMethodChangedListener callMethodChanged) {
        setupCallMethodSpinner(context, false, callMethodSpinner, callMethodChanged);
    }

    /**
     * Creates the initial spinner configurations with optional volte icon
     * @param context
     * @param showVolte Whether to show Volte icon
     * @param callMethodSpinner The spinner
     * @param callMethodChanged the listener to be called when the call method is changed
     */
    public static void setupCallMethodSpinner(Context context, boolean showVolte,
                                              Spinner callMethodSpinner,
                                              final OnCallMethodChangedListener callMethodChanged) {
        CallMethodSpinnerAdapter callMethodSpinnerAdapter =
                new CallMethodSpinnerAdapter(context, new ArrayList<CallMethodInfo>(), showVolte);

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
     * Updates the spinner with new call methods when available. Don't show volte label view
     * @param context
     * @param callMethodSpinner the spinner
     * @param changeListener the listener called when something is changed
     * @param lastKnownCallMethod the previous call method of either this spinner
     *                            or the other spinners
     */
    public static void updateCallMethodUI(Context context, Spinner callMethodSpinner,
                                          final OnCallMethodChangedListener changeListener,
                                          String lastKnownCallMethod,
                                          HashMap<ComponentName, CallMethodInfo>
                                                  availableProviders) {
        updateCallMethodUI(context, null, callMethodSpinner, changeListener, lastKnownCallMethod,
                availableProviders);
    }


    /**
     * Updates the spinner with new call methods when available. Show volte label view when
     * appropriate
     * @param context
     * @param volteLabel label for VoLTE text
     * @param callMethodSpinner the spinner
     * @param changeListener the listener called when something is changed
     * @param lastKnownCallMethod the previous call method of either this spinner
     *                            or the other spinners
     */
    public static void updateCallMethodUI(Context context, View volteLabel,
                                          Spinner callMethodSpinner,
                                          final OnCallMethodChangedListener changeListener,
                                          String lastKnownCallMethod,
                                          HashMap<ComponentName, CallMethodInfo>
                                                  availableProviders) {
        CallMethodSpinnerAdapter callMethodSpinnerAdapter = (CallMethodSpinnerAdapter)
                callMethodSpinner.getAdapter();
        int lastKnownPosition = callMethodSpinnerAdapter.getPosition(lastKnownCallMethod);
        callMethodSpinnerAdapter.clear();

        List<CallMethodInfo> sims = new ArrayList<CallMethodInfo>();
        if (TelecomUtil.hasReadPhoneStatus(context)) {
            // Todo: tell user why we need this and properly request and restart spinner if they
            // grant it.
            sims.clear();
            sims.addAll(CallMethodUtils.getSimInfoList(context));
        }

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

            if (volteLabel != null){
                if (info != null && VolteUtils.isVolteInUse(context, info.mSubId)) {
                    volteLabel.setVisibility(View.VISIBLE);
                } else {
                    volteLabel.setVisibility(View.GONE);
                }
            }
        } else {
            // multiple call methods or single provider
            int position = POSITION_UNKNOWN;

            if (volteLabel != null) {
                volteLabel.setVisibility(View.GONE);
            }
            callMethodSpinner.setVisibility(View.VISIBLE);
            if (!TextUtils.isEmpty(lastKnownCallMethod)) {
                position = callMethodSpinnerAdapter.getPosition(lastKnownCallMethod);
            }

            if (position == POSITION_UNKNOWN) {
                CallMethodInfo defaultSim = null;
                if (TelecomUtil.hasReadPhoneStatus(context)) {
                    defaultSim = CallMethodUtils.getDefaultSimInfo(context);
                }
                if (defaultSim != null) {
                    position = callMethodSpinnerAdapter.getPosition(
                            CallMethodSpinnerAdapter.getCallMethodKey(defaultSim));
                }

                if (position == POSITION_UNKNOWN) {
                    // If all else fails, first position
                    position = 0;
                }
            }

            if (position < callMethodSpinnerAdapter.getCount()) {
                if (lastKnownPosition != position) {
                    callMethodSpinner.setSelection(position);
                } else {
                    // Adapter doesn't call onChanged if position dosn't change
                    CallMethodInfo info = callMethodSpinnerAdapter.getItem(position);
                    changeListener.onCallMethodChangedListener(info);
                }
            }
        }
    }

    public static void setSelectedCallMethod(Spinner callMethodSpinner,
                                             CallMethodInfo callMethodInfo) {
        CallMethodSpinnerAdapter callMethodSpinnerAdapter =
                (CallMethodSpinnerAdapter) callMethodSpinner.getAdapter();
        int position = callMethodSpinnerAdapter.getPosition(callMethodInfo);
        if (position > POSITION_UNKNOWN && position < callMethodSpinnerAdapter.getCount()) {
            callMethodSpinner.setSelection(position);
        }
    }
}
