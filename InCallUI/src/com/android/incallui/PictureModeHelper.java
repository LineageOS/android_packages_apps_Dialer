/**
 * Copyright (c) 2016 The Linux Foundation. All rights reserved.
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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.ListView;

import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallPresenter.InCallStateListener;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

import com.google.common.base.Preconditions;

/**
 * This class displays the picture mode alert dialog and registers listener who wish to listen to
 * user selection for the preview video and the incoming video.
 */
public class PictureModeHelper extends AlertDialog implements InCallDetailsListener,
        InCallStateListener, CallList.Listener {

    private AlertDialog mAlertDialog;

    /**
     * Indicates whether we should display camera preview video view
     */
    private static boolean mShowPreviewVideoView = true;

    /**
     * Indicates whether we should display incoming video view
     */
    private static boolean mShowIncomingVideoView = true;

    public PictureModeHelper(Context context) {
        super(context);
    }

    public void setUp(VideoCallPresenter videoCallPresenter) {
        InCallPresenter.getInstance().addDetailsListener(this);
        InCallPresenter.getInstance().addListener(this);
        CallList.getInstance().addListener(this);
        addListener(videoCallPresenter);
    }

    public void tearDown(VideoCallPresenter videoCallPresenter) {
        InCallPresenter.getInstance().removeDetailsListener(this);
        InCallPresenter.getInstance().removeListener(this);
        CallList.getInstance().removeListener(this);
        removeListener(videoCallPresenter);
        mAlertDialog = null;
    }

    /**
     * Displays the alert dialog
     */
    public void show() {
        if (mAlertDialog != null) {
            mAlertDialog.show();
        }
    }

    /**
     * Listener interface to implement if any class is interested in listening to preview
     * video or incoming video selection changed
     */
    public interface Listener {
        public void onPreviewVideoSelectionChanged();
        public void onIncomingVideoSelectionChanged();
    }

    private final List<Listener> mListeners = new CopyOnWriteArrayList<>();

    /**
     * This method adds a new Listener. Users interested in listening to preview video selection
     * and incoming video selection changes must register to this class
     * @param Listener listener - the listener to be registered
     */
    public void addListener(Listener listener) {
        Preconditions.checkNotNull(listener);
        mListeners.add(listener);
    }

    /**
     * This method unregisters the listener listening to preview video selection and incoming
     * video selection
     * @param Listener listener - the listener to be un-registered
     */
    public void removeListener(Listener listener) {
        Preconditions.checkNotNull(listener);
        mListeners.remove(listener);
    }

    /**
     * Creates and displays the picture mode alert dialog to enable the user to switch
     * between picture modes - Picture in picture, Incoming mode or Camera preview mode
     * Once users makes their choice, they can save or cancel. Saving will apply the
     * new picture mode to the video call by notifying video call presenter of the change.
     * Cancel will dismiss the alert dialog without making any changes. Alert dialog is
     * cancelable so pressing home/back key will dismiss the dialog.
     * @param Context context - The application context.
     */
    public void create(Context context) {
        final ArrayList<CharSequence> items = new ArrayList<CharSequence>();
        final Resources res = context.getResources();

        final InCallActivity inCallActivity = InCallPresenter.getInstance().getActivity();
        if (inCallActivity == null) {
            return;
        }

        final View checkboxView = inCallActivity.getLayoutInflater().
                inflate(R.layout.qti_video_call_picture_mode_menu, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.video_call_picture_mode_menu_title);
        builder.setView(checkboxView);
        builder.setCancelable(true);

        CheckBox previewVideo = (CheckBox) checkboxView.findViewById(R.id.preview_video);
        CheckBox incomingVideo = (CheckBox) checkboxView.findViewById(R.id.incoming_video);

        if (previewVideo == null || incomingVideo == null) {
            return;
        }

        // Intialize the checkboxes with the proper checked values
        previewVideo.setChecked(mShowPreviewVideoView);
        incomingVideo.setChecked(mShowIncomingVideoView);

        // Ensure that at least one of the check boxes is enabled. Disable the other checkbox
        // if checkbox is un-checked and vice versa. Say for e.g: if preview video was unchecked,
        // disble incoming video and make it unclickable
        enable(previewVideo, mShowIncomingVideoView);
        enable(incomingVideo, mShowPreviewVideoView);

        previewVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enable(incomingVideo, ((CheckBox) view).isChecked());
            }
        });

        incomingVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enable(previewVideo, ((CheckBox) view).isChecked());
            }
        });

        builder.setPositiveButton(res.getText(R.string.video_call_picture_mode_save_option),
                new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int item) {
                    mShowPreviewVideoView = previewVideo.isChecked();
                    mShowIncomingVideoView = incomingVideo.isChecked();
                    notifyOnSelectionChanged();
                }
        });

        builder.setNegativeButton(res.getText(R.string.video_call_picture_mode_cancel_option),
                new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int item) {
                }
        });

        mAlertDialog = builder.create();
        setDismissListener();
    }

    private void setDismissListener() {
        mAlertDialog.setOnDismissListener(new OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    mAlertDialog = null;
                }
        });
    }

    /**
     * This method enables or disables the checkbox passed in based on whether the flag enable
     * is set to true or false. Also toggle the checkbox being clickable.
     * @param CheckBox checkBox - the check Box to enable/disable
     * @param boolean enable - Flag to enable/disable checkbox (true/false)
     */
    private void enable(CheckBox checkBox, boolean enable) {
        checkBox.setEnabled(enable);
        checkBox.setClickable(enable);
    }

    /**
     * Determines if we can show the preview video view
     */
    public boolean canShowPreviewVideoView() {
        return mShowPreviewVideoView;
    }

    /**
     * Determines if we can show the incoming video view
     */
    public boolean canShowIncomingVideoView() {
        return mShowIncomingVideoView;
    }

    /**
     * Determines whether we are in Picture in Picture mode
     */
    public boolean isPipMode() {
        return canShowPreviewVideoView() && canShowIncomingVideoView();
    }

    /**
     * Notifies all registered classes of preview video or incoming video selection changed
     */
    public void notifyOnSelectionChanged() {
        Preconditions.checkNotNull(mListeners);
        for (Listener listener : mListeners) {
            listener.onPreviewVideoSelectionChanged();
            listener.onIncomingVideoSelectionChanged();
        }
    }

    /**
     * Listens to call details changed and dismisses the dialog if call has been downgraded to
     * voice
     * @param Call call - The call for which details changed
     * @param android.telecom.Call.Details details - The changed details
     */
    @Override
    public void onDetailsChanged(Call call, android.telecom.Call.Details details) {
        if (call == null) {
            return;
        }
        if (!VideoUtils.isVideoCall(call) && mAlertDialog != null) {
            mAlertDialog.dismiss();
        }
    }

    /**
     * Handles call state changes
     *
     * @param InCallPresenter.InCallState oldState - The old call state
     * @param InCallPresenter.InCallState newState - The new call state
     * @param CallList callList - The call list.
     */
    @Override
    public void onStateChange(InCallPresenter.InCallState oldState,
            InCallPresenter.InCallState newState, CallList callList) {
        Log.d(this, "onStateChange oldState" + oldState + " newState=" + newState);

        if (newState == InCallPresenter.InCallState.NO_CALLS) {
            // Set both display preview video and incoming video to true as default display mode is
            // to show picture in picture.
            mShowPreviewVideoView = true;
            mShowIncomingVideoView = true;
        }
    }

    /**
     * Overrides onIncomingCall method of {@interface CallList.Listener}
     * @param Call call - The incoming call
     */
    @Override
    public void onIncomingCall(Call call) {
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
        }
    }

    /**
     * Overrides onCallListChange method of {@interface CallList.Listener}
     * Added for completeness
     */
    @Override
    public void onCallListChange(CallList list) {
        // no-op
    }

    /**
     * Overrides onUpgradeToVideo method of {@interface CallList.Listener}
     * @param Call call - The call to be upgraded
     */
    @Override
    public void onUpgradeToVideo(Call call) {
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
        }
    }

    /**
     * Overrides onDisconnect method of {@interface CallList.Listener}
     * @param Call call - The call to be disconnected
     */
    @Override
    public void onDisconnect(Call call) {
        // no-op
    }
}
