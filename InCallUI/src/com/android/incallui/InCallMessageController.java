/* Copyright (c) 2015, 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.incallui;

import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.IConnectivityManager;
import android.net.INetworkStatsService;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkState;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;

import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallVideoCallCallbackNotifier.VideoEventListener;
import com.android.incallui.InCallVideoCallCallbackNotifier.SessionModificationListener;
import org.codeaurora.ims.QtiCallConstants;
import org.codeaurora.ims.QtiCarrierConfigs;
import org.codeaurora.ims.QtiVideoCallDataUsage;
import org.codeaurora.ims.utils.QtiImsExtUtils;

/**
 * This class listens to incoming events for the listener classes it implements. It should
 * handle all UI notification to be shown to the user for any indication that is required to be
 * shown like call substate indication, video quality indication, etc.
 * For e.g., this class implements {@class InCallSubstateListener} and when call substate changes,
 * {@class CallSubstateNotifier} notifies it through the onCallSubstateChanged callback.
 */
public class InCallMessageController implements InCallSubstateListener, VideoEventListener,
        CallList.Listener, SessionModificationListener, InCallSessionModificationCauseListener,
        InCallDetailsListener {

    private INetworkStatsService mStatsService;
    private IConnectivityManager mConnManager;
    private long previousLteUsage;
    private long previousWlanUsage;

    private static InCallMessageController sInCallMessageController;

    private PrimaryCallTracker mPrimaryCallTracker;

    private Context mContext;

    /**
     * Private constructor. Must use getInstance() to get this singleton.
     */
    private InCallMessageController() {
    }

    /**
     * Handles set up of the {@class InCallMessageController}. Instantiates the context needed by
     * the class and adds a listener to listen to call substate changes, video event changes,
     * session modification cause changes, call state changes.
     */
    public void setUp(Context context) {
        mContext = context;
        mPrimaryCallTracker = new PrimaryCallTracker();
        CallSubstateNotifier.getInstance().addListener(this);
        InCallVideoCallCallbackNotifier.getInstance().addVideoEventListener(this);
        CallList.getInstance().addListener(this);
        SessionModificationCauseNotifier.getInstance().addListener(this);
        InCallPresenter.getInstance().addListener(mPrimaryCallTracker);
        InCallVideoCallCallbackNotifier.getInstance().addSessionModificationListener(this);
        InCallPresenter.getInstance().addDetailsListener(this);
        mStatsService = INetworkStatsService.Stub.asInterface(
                ServiceManager.getService(Context.NETWORK_STATS_SERVICE));
        mConnManager = IConnectivityManager.Stub.asInterface(
                ServiceManager.getService(Context.CONNECTIVITY_SERVICE));
    }

    /**
     * Handles tear down of the {@class InCallMessageController}. Sets the context to null and
     * unregisters it's call substate,  video event, session modification cause, call state
     * listeners.
     */
    public void tearDown() {
        mContext = null;
        CallSubstateNotifier.getInstance().removeListener(this);
        InCallVideoCallCallbackNotifier.getInstance().removeVideoEventListener(this);
        CallList.getInstance().removeListener(this);
        SessionModificationCauseNotifier.getInstance().removeListener(this);
        InCallPresenter.getInstance().removeListener(mPrimaryCallTracker);
        InCallVideoCallCallbackNotifier.getInstance().removeSessionModificationListener(this);
        InCallPresenter.getInstance().removeDetailsListener(this);
        mPrimaryCallTracker = null;
        mStatsService = null;
        mConnManager = null;
        previousLteUsage = 0;
        previousWlanUsage = 0;
    }

    /**
     * This method returns a singleton instance of {@class InCallMessageController}
     */
    public static synchronized InCallMessageController getInstance() {
        if (sInCallMessageController == null) {
            sInCallMessageController = new InCallMessageController();
        }
        return sInCallMessageController;
    }

    /**
     * This method overrides onCallSubstateChanged method of {@interface InCallSubstateListener}
     * We are notified when call substate changes and display a toast message on the UI.
     */
    @Override
    public void onCallSubstateChanged(final Call call, final int callSubstate) {
        Log.d(this, "onCallSubstateChanged - Call : " + call + " call substate changed to " +
                callSubstate);

        if (mContext == null || !mPrimaryCallTracker.isPrimaryCall(call)) {
            Log.e(this, "onCallSubstateChanged - Context is null/not primary call.");
            return;
        }

        String callSubstateChangedText = "";

        if (QtiCallUtils.isEnabled(
                QtiCallConstants.CALL_SUBSTATE_AUDIO_CONNECTED_SUSPENDED, callSubstate)) {
            callSubstateChangedText +=
                    mContext.getResources().getString(
                    R.string.call_substate_connected_suspended_audio);
        }

        if (QtiCallUtils.isEnabled(
                QtiCallConstants.CALL_SUBSTATE_VIDEO_CONNECTED_SUSPENDED, callSubstate)) {
            callSubstateChangedText +=
                    mContext.getResources().getString(
                    R.string.call_substate_connected_suspended_video);
        }

        if (QtiCallUtils.isEnabled(QtiCallConstants.CALL_SUBSTATE_AVP_RETRY, callSubstate)) {
            callSubstateChangedText +=
                    mContext.getResources().getString(R.string.call_substate_avp_retry);
        }

        if (QtiCallUtils.isNotEnabled(QtiCallConstants.CALL_SUBSTATE_ALL, callSubstate)) {
            callSubstateChangedText =
                    mContext.getResources().getString(R.string.call_substate_call_resumed);
        }

        if (!callSubstateChangedText.isEmpty()) {
            String callSubstateLabelText = mContext.getResources().getString(
                    R.string.call_substate_label, call.getId(), callSubstateChangedText);
            QtiCallUtils.displayToast(mContext, callSubstateLabelText);
        }
    }

    /**
     * This method overrides onVideoQualityChanged method of {@interface VideoEventListener}
     * We are notified when video quality of the call changed and display a message on the UI.
     */
    @Override
    public void onVideoQualityChanged(final Call call, final int videoQuality) {
        Log.d(this, "onVideoQualityChanged: - Call : " + call + " Video quality changed to " +
                videoQuality);

        if (mContext == null || !mPrimaryCallTracker.isPrimaryCall(call)) {
            Log.e(this, "onVideoQualityChanged - Context is null/not primary call.");
            return;
        }
        if (QtiImsExtUtils.isCarrierConfigEnabled(mContext,
                QtiCarrierConfigs.SHOW_VIDEO_QUALITY_TOAST)) {
            final Resources resources = mContext.getResources();
            final String videoQualityChangedText = resources.getString(
                    R.string.video_quality_changed, call.getId(),
                    QtiCallUtils.getVideoQualityResourceId(videoQuality));
            QtiCallUtils.displayToast(mContext, videoQualityChangedText);
        }
    }

    /**
     * This method overrides onCallSessionEvent method of {@interface VideoEventListener}
     * We are notified when a new call session event is sent and display a message on the UI.
     */
    @Override
    public void onCallSessionEvent(final int event) {
        Log.d(this, "onCallSessionEvent: event = " + event);

        if (mContext == null) {
            Log.e(this, "onCallSessionEvent - Context is null.");
            return;
        }
        if (QtiImsExtUtils.isCarrierConfigEnabled(mContext,
                QtiCarrierConfigs.SHOW_CALL_SESSION_EVENT_TOAST)) {
            QtiCallUtils.displayToast(mContext, QtiCallUtils.getCallSessionResourceId(event));
        }
    }

    /**
     * Handles changes to the details of the call.
     *
     * @param call The call for which the details changed.
     * @param details The new call details.
     */
    @Override
    public void onDetailsChanged(Call call, android.telecom.Call.Details details) {
        Log.d(this, " onDetailsChanged call=" + call + " details=" + details);

        if (mContext == null || details == null) {
            Log.e(this," onDetailsChanged: context/details is null");
            return;
        }

        final Bundle extras =  details.getExtras();
        final QtiVideoCallDataUsage dataUsage = (extras != null) ?
                (QtiVideoCallDataUsage)extras.getParcelable(
                QtiCallConstants.VIDEO_CALL_DATA_USAGE_KEY) : null;
        if (dataUsage == null) {
            return;
        }

        long lteUsage = dataUsage.getLteDataUsage();
        long wlanUsage = dataUsage.getWlanDataUsage();
        Log.i(this, "onDetailsChanged LTE data value = " + lteUsage + " WiFi data value = " +
                wlanUsage);

        if (mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_video_call_datausage_enable)) {
            recordDataUsage(lteUsage, wlanUsage);
        }


        if (QtiImsExtUtils.isCarrierConfigEnabled(mContext,
                QtiCarrierConfigs.SHOW_DATA_USAGE_TOAST)) {
            final String dataUsageChangedText = mContext.getResources().getString(
                    R.string.wlan_data_usage_label, wlanUsage);
            QtiCallUtils.displayToast(mContext, dataUsageChangedText);
        }
    }

    /**
     * This method overrides onCallDataUsageChange method of {@interface VideoEventListener}
     *  We are notified when data usage is changed and display a message on the UI.
     */
    @Override
    public void onCallDataUsageChange(final long dataUsage) {
        Log.d(this, "onCallDataUsageChange: dataUsage = " + dataUsage);
        if (QtiImsExtUtils.isCarrierConfigEnabled(mContext,
                QtiCarrierConfigs.SHOW_DATA_USAGE_TOAST)) {
            final String dataUsageChangedText = mContext.getResources().getString(
                    R.string.lte_data_usage_label, dataUsage);
            QtiCallUtils.displayToast(mContext, dataUsageChangedText);
        }
    }

    /**
     * This method overrides onPeerPauseStateChanged method of {@interface VideoEventListener}
     * Added for completeness. No implementation yet.
     */
    @Override
    public void onPeerPauseStateChanged(final Call call, final boolean paused) {
        //no-op
    }

    /**
     * This method overrides onIncomingCall method of {@interface CallList.Listener}
     * Added for completeness. No implementation yet.
     */
    @Override
    public void onIncomingCall(Call call) {
        // no-op
    }

    /**
     * This method overrides onCallListChange method of {@interface CallList.Listener}
     * Added for completeness. No implementation yet.
     */
    @Override
    public void onCallListChange(CallList list) {
        // no-op
    }

    /**
     * This method overrides onUpgradeToVideo method of {@interface CallList.Listener}
     * Added for completeness. No implementation yet.
     */
    @Override
    public void onUpgradeToVideo(Call call) {
        // no-op
    }

    /**
     * This method overrides onDisconnect method of {@interface CallList.Listener}
     */
    @Override
    public void onDisconnect(final Call call) {
        Bundle extras = call.getExtras();
        if (extras == null) {
            Log.w(this, "onDisconnect: null Extras");
            return;
        }
        final int errorCode = extras.getInt(QtiCallConstants.EXTRAS_KEY_CALL_FAIL_EXTRA_CODE,
                QtiCallConstants.DISCONNECT_CAUSE_UNSPECIFIED);
        Log.d(this, "onDisconnect: code = " + errorCode);
        showCallDisconnectInfo(errorCode);
    }

    /**
     * This method displays specific disconnect information to user
     */
    private void showCallDisconnectInfo(int errorCode) {
       if(errorCode == QtiCallConstants.DISCONNECT_CAUSE_UNSPECIFIED) {
          return;
       }

       switch (errorCode) {
         case QtiCallConstants.CALL_FAIL_EXTRA_CODE_LTE_3G_HA_FAILED:
             QtiCallUtils.displayToast(mContext, R.string.call_failed_ho_not_feasible);
             break;
         case QtiCallConstants.CALL_FAIL_EXTRA_CODE_LOCAL_LOW_BATTERY:
             QtiCallUtils.displayToast(mContext, R.string.call_failed_due_to_low_battery);
             break;
         case QtiCallConstants.CALL_FAIL_EXTRA_CODE_LOCAL_VALIDATE_NUMBER:
             QtiCallUtils.displayToast(mContext, R.string.call_failed_due_to_validate_number);
             break;
         default:
             break;
       }
    }

    /*
     * Handles any session modifictaion cause changes in the call.
     *
     * @param call The call for which orientation mode changed.
     * @param sessionModificationCause The new session modifictaion cause
     */
    @Override
    public void onSessionModificationCauseChanged(Call call, int sessionModificationCause) {
        Log.d(this, "onSessionModificationCauseChanged: Call : " + call +
                " Call modified due to "  + sessionModificationCause);

        if (mContext == null || !mPrimaryCallTracker.isPrimaryCall(call)) {
            Log.e(this,
                    "onSessionModificationCauseChanged- Context is null/not primary call.");
            return;
        }

        QtiCallUtils.displayToast(mContext,
                getSessionModificationCauseResourceId(sessionModificationCause));
    }

    /**
     * This method returns the string resource id (i.e. display string) that corresponds to the
     * session modification cause code.
     */
    private static int getSessionModificationCauseResourceId(int cause) {
        switch(cause) {
            case QtiCallConstants.CAUSE_CODE_UNSPECIFIED:
                return R.string.session_modify_cause_unspecified;
            case QtiCallConstants.CAUSE_CODE_SESSION_MODIFY_UPGRADE_LOCAL_REQ:
                return R.string.session_modify_cause_upgrade_local_request;
            case QtiCallConstants.CAUSE_CODE_SESSION_MODIFY_UPGRADE_REMOTE_REQ:
                return R.string.session_modify_cause_upgrade_remote_request;
            case QtiCallConstants.CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_LOCAL_REQ:
                return R.string.session_modify_cause_downgrade_local_request;
            case QtiCallConstants.CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_REMOTE_REQ:
                return R.string.session_modify_cause_downgrade_remote_request;
            case QtiCallConstants.CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_RTP_TIMEOUT:
                return R.string.session_modify_cause_downgrade_rtp_timeout;
            case QtiCallConstants.CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_QOS:
                return R.string.session_modify_cause_downgrade_qos;
            case QtiCallConstants.CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_PACKET_LOSS:
                return R.string.session_modify_cause_downgrade_packet_loss;
            case QtiCallConstants.CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_LOW_THRPUT:
                return R.string.session_modify_cause_downgrade_low_thrput;
            case QtiCallConstants.CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_THERM_MITIGATION:
                return R.string.session_modify_cause_downgrade_thermal_mitigation;
            case QtiCallConstants.CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_LIPSYNC:
                return R.string.session_modify_cause_downgrade_lipsync;
            case QtiCallConstants.CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_GENERIC_ERROR:
            default:
                return R.string.session_modify_cause_downgrade_generic_error;
        }
    }

    @Override
    public void onUpgradeToVideoRequest(Call call, int videoState) {
        //no-op
    }

    @Override
    public void onUpgradeToVideoFail(int error, Call call) {
        Log.d(this, "onUpgradeToVideoFail: error = " + error);
        showUpgradeFailInfo(error);
    }

    private void showUpgradeFailInfo(int errorCode) {
        switch (errorCode) {
            case QtiCallConstants.SESSION_MODIFY_REQUEST_FAILED_LOW_BATTERY:
                QtiCallUtils.displayToast(mContext,
                        R.string.modify_call_failed_due_to_low_battery);
                break;
            default:
                break;
        }
    }

     /**
      * This method is used to ignore the repeatly calling onDetailsChanged
      */
     private boolean hasDataUsageChanged(long lteUsage, long wlanUsage) {
         boolean hasChanged = false;
         if (previousLteUsage != lteUsage) {
             hasChanged = true;
             previousLteUsage = lteUsage;
         }
         if (previousWlanUsage != wlanUsage) {
             hasChanged = true;
             previousWlanUsage = wlanUsage;
         }
         return hasChanged;
     }

     private void recordDataUsage(long lteUsage, long wlanUsage) {
         String wifiIface;
         String imsIface;
         if(!hasDataUsageChanged(lteUsage, wlanUsage)) {
             return;
         }

         if (wlanUsage != 0) {
             wifiIface = getWifiIface();
             if (wifiIface != null)
                 recordUsage(wifiIface, ConnectivityManager.TYPE_WIFI, wlanUsage, 0);
         }

         if (lteUsage != 0 ) {
             imsIface = getImsIface();
             if (imsIface != null)
                 recordUsage(imsIface, ConnectivityManager.TYPE_MOBILE, lteUsage, 0);
         }
     }

     private void recordUsage(String ifaces, int ifaceType, long rx, long tx) {
         if (ifaces == null) {
             Log.d(this, "recordDataUseage ifaces is null");
             return;
         }
         Log.d(this,"recordDataUseage ifaces ="+ ifaces + "   ifaceType=" + ifaceType +
                 "rx = " + rx + " tx =" + tx);

         try {
             mStatsService.recordVideoCallData(ifaces, ifaceType, rx, tx);
         } catch (RuntimeException e) {
             Log.e(this, "recordDataUseage RuntimeException" + e);
         } catch (RemoteException e) {
             Log.e(this, "recordDataUseage RemoteException" + e);
         }
     }

    private String getImsIface() {
        final NetworkState[] states;
        try {
            states = mConnManager.getAllNetworkState();
        } catch (RemoteException e) {
            Log.e(this, "getVoiceCallIfaces RemoteException" + e);
            return null;
        }

        if (states != null) {
            for (NetworkState state : states) {
                if (state.networkInfo.isConnected() && state.networkCapabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_IMS)) {
                    final boolean isMobile = ConnectivityManager.isNetworkTypeMobile(
                            state.networkInfo.getType());
                    final String baseIface = state.linkProperties.getInterfaceName();
                    if (isMobile)
                        return baseIface;
                }
            }
        }
        return null;
    }

    private String getWifiIface() {
        final LinkProperties wifiLinkProperties;
        try {
            wifiLinkProperties =
                    mConnManager.getLinkPropertiesForType(ConnectivityManager.TYPE_WIFI);
            if (wifiLinkProperties != null) {
                return wifiLinkProperties.getInterfaceName();
            }
        } catch (RemoteException e) {
            Log.e(this, "get wifi Iface RemoteException" + e);
        }
        return null;
    }


}
