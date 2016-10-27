/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.os.Bundle;
import android.telecom.VideoProfile;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.dialer.R;

public class GlowPadAnswerFragment extends AnswerFragment {

    private GlowPadWrapper mGlowpad;

    public GlowPadAnswerFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mGlowpad = (GlowPadWrapper) inflater.inflate(R.layout.answer_fragment,
                container, false);

        Log.d(this, "Creating view for answer fragment ", this);
        Log.d(this, "Created from activity", getActivity());
        mGlowpad.setAnswerFragment(this);

        return mGlowpad;
    }

    @Override
    public void onResume() {
        super.onResume();
        mGlowpad.requestFocus();
    }

    @Override
    public void onDestroyView() {
        Log.d(this, "onDestroyView");
        if (mGlowpad != null) {
            mGlowpad.stopPing();
            mGlowpad = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onShowAnswerUi(boolean shown) {
        Log.d(this, "Show answer UI: " + shown);
        if (shown) {
            mGlowpad.startPing();
        } else {
            mGlowpad.stopPing();
        }
    }

    /**
     * Sets targets on the glowpad according to target set identified by the parameter.
     *
     * @param targetSet Integer identifying the set of targets to use.
     */
    public void showTargets(int targetSet) {
        showTargets(targetSet, VideoProfile.STATE_BIDIRECTIONAL);
    }

    /**
     * Sets targets on the glowpad according to target set identified by the parameter.
     *
     * @param targetSet Integer identifying the set of targets to use.
     */
    @Override
    public void showTargets(int targetSet, int videoState) {
        final int targetResourceId;
        final int targetDescriptionsResourceId;
        final int directionDescriptionsResourceId;
        final int handleDrawableResourceId;
        mGlowpad.setVideoState(videoState);
        final boolean isEnhanceUIEnabled = getContext().getResources().getBoolean(
                R.bool.config_enable_enhance_video_call_ui);

        switch (targetSet) {
            case TARGET_SET_FOR_AUDIO_WITH_SMS:
                targetResourceId = R.array.incoming_call_widget_audio_with_sms_targets;
                targetDescriptionsResourceId =
                        R.array.incoming_call_widget_audio_with_sms_target_descriptions;
                directionDescriptionsResourceId =
                        R.array.incoming_call_widget_audio_with_sms_direction_descriptions;
                handleDrawableResourceId = R.drawable.ic_incall_audio_handle;
                break;
            case TARGET_SET_FOR_VIDEO_WITHOUT_SMS:
                if (isEnhanceUIEnabled) {
                    targetResourceId =
                            R.array.enhance_incoming_call_widget_video_without_sms_targets;
                } else {
                    targetResourceId = R.array.incoming_call_widget_video_without_sms_targets;
                }
                targetDescriptionsResourceId =
                        R.array.incoming_call_widget_video_without_sms_target_descriptions;
                directionDescriptionsResourceId =
                        R.array.incoming_call_widget_video_without_sms_direction_descriptions;
                handleDrawableResourceId = R.drawable.ic_incall_video_handle;
                break;
            case TARGET_SET_FOR_VIDEO_WITH_SMS:
                if (isEnhanceUIEnabled) {
                    targetResourceId = R.array.enhance_incoming_call_widget_video_with_sms_targets;
                } else {
                    targetResourceId = R.array.incoming_call_widget_video_with_sms_targets;
                }
                targetDescriptionsResourceId =
                        R.array.incoming_call_widget_video_with_sms_target_descriptions;
                directionDescriptionsResourceId =
                        R.array.incoming_call_widget_video_with_sms_direction_descriptions;
                handleDrawableResourceId = R.drawable.ic_incall_video_handle;
                break;
            case TARGET_SET_FOR_VIDEO_ACCEPT_REJECT_REQUEST:
                targetResourceId =
                        R.array.incoming_call_widget_video_request_targets;
                targetDescriptionsResourceId =
                        R.array.incoming_call_widget_video_request_target_descriptions;
                directionDescriptionsResourceId = R.array
                        .incoming_call_widget_video_request_target_direction_descriptions;
                handleDrawableResourceId = R.drawable.ic_incall_video_handle;
                break;
            case TARGET_SET_FOR_QTI_VIDEO_WITHOUT_SMS:
                if (isEnhanceUIEnabled) {
                    targetResourceId =
                            R.array.enhance_incoming_call_widget_video_without_sms_targets;
                } else {
                    targetResourceId = R.array.qti_incoming_call_widget_video_without_sms_targets;
                }
                targetDescriptionsResourceId =
                        R.array.qti_incoming_call_widget_video_without_sms_target_descriptions;
                directionDescriptionsResourceId =
                        R.array.qti_incoming_call_widget_video_without_sms_direction_descriptions;
                handleDrawableResourceId = R.drawable.ic_incall_video_handle;
                break;
            case TARGET_SET_FOR_QTI_VIDEO_WITH_SMS:
                if (isEnhanceUIEnabled) {
                    targetResourceId = R.array.enhance_incoming_call_widget_video_with_sms_targets;
                } else {
                    targetResourceId = R.array.qti_incoming_call_widget_video_with_sms_targets;
                }
                targetDescriptionsResourceId =
                        R.array.qti_incoming_call_widget_video_with_sms_target_descriptions;
                directionDescriptionsResourceId =
                        R.array.qti_incoming_call_widget_video_with_sms_direction_descriptions;
                handleDrawableResourceId = R.drawable.ic_incall_video_handle;
                break;
            case TARGET_SET_FOR_QTI_VIDEO_TRANSMIT_ACCEPT_REJECT_WITHOUT_SMS:
                targetResourceId =
                    R.array.qti_incoming_call_widget_tx_video_without_sms_targets;
                targetDescriptionsResourceId =
                    R.array.qti_incoming_call_widget_tx_video_without_sms_target_descriptions;
                directionDescriptionsResourceId =
                    R.array.qti_incoming_call_widget_tx_video_without_sms_direction_descriptions;
                handleDrawableResourceId = R.drawable.ic_incall_video_handle;
                break;
            case TARGET_SET_FOR_QTI_VIDEO_TRANSMIT_ACCEPT_REJECT_WITH_SMS:
                targetResourceId =
                    R.array.qti_incoming_call_widget_tx_video_with_sms_targets;
                targetDescriptionsResourceId =
                    R.array.qti_incoming_call_widget_tx_video_with_sms_target_descriptions;
                directionDescriptionsResourceId =
                    R.array.qti_incoming_call_widget_tx_video_with_sms_direction_descriptions;
                handleDrawableResourceId = R.drawable.ic_incall_video_handle;
                break;
            case TARGET_SET_FOR_QTI_VIDEO_RECEIVE_ACCEPT_REJECT_WITHOUT_SMS:
                targetResourceId =
                    R.array.qti_incoming_call_widget_rx_video_without_sms_targets;
                targetDescriptionsResourceId =
                    R.array.qti_incoming_call_widget_rx_video_without_sms_target_descriptions;
                directionDescriptionsResourceId =
                    R.array.qti_incoming_call_widget_rx_video_without_sms_direction_descriptions;
                handleDrawableResourceId = R.drawable.ic_incall_video_handle;
                break;
            case TARGET_SET_FOR_QTI_VIDEO_RECEIVE_ACCEPT_REJECT_WITH_SMS:
                targetResourceId =
                    R.array.qti_incoming_call_widget_rx_video_with_sms_targets;
                targetDescriptionsResourceId =
                    R.array.qti_incoming_call_widget_rx_video_with_sms_target_descriptions;
                directionDescriptionsResourceId =
                    R.array.qti_incoming_call_widget_rx_video_with_sms_direction_descriptions;
                handleDrawableResourceId = R.drawable.ic_incall_video_handle;
                break;
            case TARGET_SET_FOR_QTI_VIDEO_ACCEPT_REJECT_REQUEST:
                if (isEnhanceUIEnabled) {
                    targetResourceId =
                            R.array.enhance_incoming_call_widget_video_upgrade_request_targets;
                } else {
                    targetResourceId = R.array.qti_incoming_call_widget_video_request_targets;
                }
                targetDescriptionsResourceId =
                        R.array.qti_incoming_call_widget_video_request_target_descriptions;
                directionDescriptionsResourceId = R.array.
                        qti_incoming_call_widget_video_request_target_direction_descriptions;
                handleDrawableResourceId = R.drawable.ic_incall_video_handle;
                break;
            case TARGET_SET_FOR_QTI_BIDIRECTIONAL_VIDEO_ACCEPT_REJECT_REQUEST:
                if (isEnhanceUIEnabled) {
                    targetResourceId = R.array.
                        enhance_incoming_call_bidirectional_video_accept_request_targets;
                } else {
                    targetResourceId = R.array.
                        qti_incoming_call_widget_bidirectional_video_accept_reject_request_targets;
                }
                targetDescriptionsResourceId =
                        R.array.qti_incoming_call_widget_video_request_target_descriptions;
                directionDescriptionsResourceId = R.array.
                        qti_incoming_call_widget_video_request_target_direction_descriptions;
                handleDrawableResourceId = R.drawable.ic_incall_video_handle;
                break;
            case TARGET_SET_FOR_QTI_VIDEO_TRANSMIT_ACCEPT_REJECT_REQUEST:
                if (isEnhanceUIEnabled) {
                    targetResourceId =
                            R.array.enhance_incoming_call_video_transmit_accept_request_targets;
                } else {
                    targetResourceId = R.array.
                           qti_incoming_call_widget_video_transmit_accept_reject_request_targets;
                }
                targetDescriptionsResourceId = R.array.
                        qti_incoming_call_widget_video_transmit_request_target_descriptions;
                directionDescriptionsResourceId = R.array
                        .qti_incoming_call_widget_video_request_target_direction_descriptions;
                handleDrawableResourceId = R.drawable.ic_incall_video_handle;
                break;
            case TARGET_SET_FOR_QTI_VIDEO_RECEIVE_ACCEPT_REJECT_REQUEST:
                if (isEnhanceUIEnabled) {
                    targetResourceId = R.array.
                            enhance_incoming_call_video_receive_accept_request_targets;
                } else {
                    targetResourceId = R.array.
                            qti_incoming_call_widget_video_receive_accept_reject_request_targets;
                }
                targetDescriptionsResourceId =
                        R.array.qti_incoming_call_widget_video_receive_request_target_descriptions;
                directionDescriptionsResourceId = R.array
                        .qti_incoming_call_widget_video_request_target_direction_descriptions;
                handleDrawableResourceId = R.drawable.ic_incall_video_handle;
                break;

            case TARGET_SET_FOR_QTI_AUDIO_WITH_SMS:
                targetResourceId = R.array.qti_incoming_call_widget_audio_with_sms_targets;
                targetDescriptionsResourceId =
                        R.array.qti_incoming_call_widget_audio_with_sms_target_descriptions;
                directionDescriptionsResourceId = R.array
                        .qti_incoming_call_widget_audio_with_sms_direction_descriptions;
                handleDrawableResourceId = R.drawable.ic_incall_audio_handle;
                break;
            case TARGET_SET_FOR_QTI_AUDIO_WITHOUT_SMS:
                targetResourceId = R.array.qti_incoming_call_widget_audio_without_sms_targets;
                targetDescriptionsResourceId =
                        R.array.qti_incoming_call_widget_audio_without_sms_target_descriptions;
                directionDescriptionsResourceId = R.array
                        .qti_incoming_call_widget_audio_without_sms_direction_descriptions;
                handleDrawableResourceId = R.drawable.ic_incall_audio_handle;
                break;

            case TARGET_SET_FOR_AUDIO_WITHOUT_SMS:
            default:
                targetResourceId = R.array.incoming_call_widget_audio_without_sms_targets;
                targetDescriptionsResourceId =
                        R.array.incoming_call_widget_audio_without_sms_target_descriptions;
                directionDescriptionsResourceId =
                        R.array.incoming_call_widget_audio_without_sms_direction_descriptions;
                handleDrawableResourceId = R.drawable.ic_incall_audio_handle;
                break;
        }

        if (targetResourceId != mGlowpad.getTargetResourceId()) {
            mGlowpad.setTargetResources(targetResourceId);
            mGlowpad.setTargetDescriptionsResourceId(targetDescriptionsResourceId);
            mGlowpad.setDirectionDescriptionsResourceId(directionDescriptionsResourceId);
            mGlowpad.setHandleDrawable(handleDrawableResourceId);
            mGlowpad.reset(false);
        }
    }

    @Override
    protected void onMessageDialogCancel() {
        if (mGlowpad != null) {
            mGlowpad.startPing();
        }
    }
}
