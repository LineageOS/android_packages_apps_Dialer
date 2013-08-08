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

import android.content.Context;
import android.graphics.drawable.LayerDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnDismissListener;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.ToggleButton;

import com.android.services.telephony.common.AudioMode;

/**
 * Fragment for call control buttons
 */
public class CallButtonFragment extends BaseFragment<CallButtonPresenter>
        implements CallButtonPresenter.CallButtonUi, OnMenuItemClickListener,
                OnDismissListener, View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    private ToggleButton mMuteButton;
    private ToggleButton mAudioButton;
    private ToggleButton mHoldButton;
    private ToggleButton mShowDialpadButton;
    private ImageButton mMergeButton;
    private ImageButton mAddCallButton;
    private ImageButton mSwapButton;

    private PopupMenu mAudioModePopup;
    private boolean mAudioModePopupVisible;
    private View mEndCallButton;

    @Override
    CallButtonPresenter createPresenter() {
        // TODO: find a cleaner way to include audio mode provider than
        // having a singleton instance.
        return new CallButtonPresenter();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View parent = inflater.inflate(R.layout.call_button_fragment, container, false);

        mEndCallButton = parent.findViewById(R.id.endButton);
        mEndCallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPresenter().endCallClicked();
            }
        });

        mMuteButton = (ToggleButton) parent.findViewById(R.id.muteButton);
        mMuteButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                getPresenter().muteClicked(isChecked);
            }
        });

        mAudioButton = (ToggleButton) parent.findViewById(R.id.audioButton);
        mAudioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onAudioButtonClicked();
            }
        });

        mHoldButton = (ToggleButton) parent.findViewById(R.id.holdButton);
        mHoldButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                getPresenter().holdClicked(isChecked);
            }
        });

        mShowDialpadButton = (ToggleButton) parent.findViewById(R.id.dialpadButton);
        mShowDialpadButton.setOnClickListener(this);
        mAddCallButton = (ImageButton) parent.findViewById(R.id.addButton);
        mAddCallButton.setOnClickListener(this);
        mMergeButton = (ImageButton) parent.findViewById(R.id.mergeButton);
        mMergeButton.setOnClickListener(this);
        mSwapButton = (ImageButton) parent.findViewById(R.id.swapButton);
        mSwapButton.setOnClickListener(this);

        return parent;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        getPresenter().onUiReady(this);

        // set the buttons
        updateAudioButtons(getPresenter().getSupportedAudio());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getPresenter().onUiUnready(this);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        Logger.d(this, "onClick(View " + view + ", id " + id + ")...");

        switch(id) {
            case R.id.addButton:
                getPresenter().addCallClicked();
                break;
            case R.id.mergeButton:
                getPresenter().mergeClicked();
                break;
            case R.id.swapButton:
                getPresenter().swapClicked();
                break;
            case R.id.dialpadButton:
                getPresenter().showDialpadClicked(mShowDialpadButton.isChecked());
                break;
            default:
                Logger.wtf(this, "onClick: unexpected");
                break;
        }
    }

    @Override
    public void setVisible(boolean on) {
        if (on) {
            getView().setVisibility(View.VISIBLE);
        } else {
            getView().setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void setMute(boolean value) {
        mMuteButton.setChecked(value);
    }

    @Override
    public void setHold(boolean value) {
        mHoldButton.setChecked(value);
    }

    @Override
    public void showHold(boolean show) {
        mHoldButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void showMerge(boolean show) {
        mMergeButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void showSwap(boolean show) {
        mSwapButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void showAddCall(boolean show) {
        mAddCallButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void setAudio(int mode) {
    }

    @Override
    public void setSupportedAudio(int modeMask) {
        updateAudioButtons(modeMask);
        refreshAudioModePopup();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        Logger.d(this, "- onMenuItemClick: " + item);
        Logger.d(this, "  id: " + item.getItemId());
        Logger.d(this, "  title: '" + item.getTitle() + "'");

        int mode = AudioMode.WIRED_OR_EARPIECE;

        switch (item.getItemId()) {
            case R.id.audio_mode_speaker:
                mode = AudioMode.SPEAKER;
                break;
            case R.id.audio_mode_earpiece:
            case R.id.audio_mode_wired_headset:
                // InCallAudioMode.EARPIECE means either the handset earpiece,
                // or the wired headset (if connected.)
                mode = AudioMode.WIRED_OR_EARPIECE;
                break;
            case R.id.audio_mode_bluetooth:
                mode = AudioMode.BLUETOOTH;
                break;
            default:
                Logger.e(this, "onMenuItemClick:  unexpected View ID " + item.getItemId()
                        + " (MenuItem = '" + item + "')");
                break;
        }

        getPresenter().setAudioMode(mode);

        return true;
    }

    // PopupMenu.OnDismissListener implementation; see showAudioModePopup().
    // This gets called when the PopupMenu gets dismissed for *any* reason, like
    // the user tapping outside its bounds, or pressing Back, or selecting one
    // of the menu items.
    @Override
    public void onDismiss(PopupMenu menu) {
        Logger.d(this, "- onDismiss: " + menu);
        mAudioModePopupVisible = false;
    }

    /**
     * Checks for supporting modes.  If bluetooth is supported, it uses the audio
     * pop up menu.  Otherwise, it toggles the speakerphone.
     */
    private void onAudioButtonClicked() {
        Logger.d(this, "onAudioButtonClicked: " +
                AudioMode.toString(getPresenter().getSupportedAudio()));

        if (isSupported(AudioMode.BLUETOOTH)) {
            showAudioModePopup();
        } else {
            getPresenter().toggleSpeakerphone();
        }
    }

    /**
     * Refreshes the "Audio mode" popup if it's visible.  This is useful
     * (for example) when a wired headset is plugged or unplugged,
     * since we need to switch back and forth between the "earpiece"
     * and "wired headset" items.
     *
     * This is safe to call even if the popup is already dismissed, or even if
     * you never called showAudioModePopup() in the first place.
     */
    public void refreshAudioModePopup() {
        if (mAudioModePopup != null && mAudioModePopupVisible) {
            // Dismiss the previous one
            mAudioModePopup.dismiss();  // safe even if already dismissed
            // And bring up a fresh PopupMenu
            showAudioModePopup();
        }
    }

    /**
     * Updates the audio button so that the appriopriate visual layers
     * are visible based on the supported audio formats.
     */
    private void updateAudioButtons(int supportedModes) {
        final boolean bluetoothSupported = isSupported(AudioMode.BLUETOOTH);
        final boolean speakerSupported = isSupported(AudioMode.SPEAKER);

        boolean audioButtonEnabled = false;
        boolean audioButtonChecked = false;
        boolean showMoreIndicator = false;

        boolean showBluetoothIcon = false;
        boolean showSpeakerphoneOnIcon = false;
        boolean showSpeakerphoneOffIcon = false;
        boolean showHandsetIcon = false;

        boolean showToggleIndicator = false;

        if (bluetoothSupported) {
            Logger.d(this, "updateAudioButtons - popup menu mode");

            audioButtonEnabled = true;
            showMoreIndicator = true;
            // The audio button is NOT a toggle in this state.  (And its
            // setChecked() state is irrelevant since we completely hide the
            // btn_compound_background layer anyway.)

            // Update desired layers:
            if (isAudio(AudioMode.BLUETOOTH)) {
                showBluetoothIcon = true;
            } else if (isAudio(AudioMode.SPEAKER)) {
                showSpeakerphoneOnIcon = true;
            } else {
                showHandsetIcon = true;
                // TODO: if a wired headset is plugged in, that takes precedence
                // over the handset earpiece.  If so, maybe we should show some
                // sort of "wired headset" icon here instead of the "handset
                // earpiece" icon.  (Still need an asset for that, though.)
            }
        } else if (speakerSupported) {
            Logger.d(this, "updateAudioButtons - speaker toggle mode");

            audioButtonEnabled = true;

            // The audio button *is* a toggle in this state, and indicated the
            // current state of the speakerphone.
            audioButtonChecked = isAudio(AudioMode.SPEAKER);

            // update desired layers:
            showToggleIndicator = true;

            showSpeakerphoneOnIcon = isAudio(AudioMode.SPEAKER);
            showSpeakerphoneOffIcon = !showSpeakerphoneOnIcon;
        } else {
            Logger.d(this, "updateAudioButtons - disabled...");

            // The audio button is a toggle in this state, but that's mostly
            // irrelevant since it's always disabled and unchecked.
            audioButtonEnabled = false;
            audioButtonChecked = false;

            // update desired layers:
            showToggleIndicator = true;
            showSpeakerphoneOffIcon = true;
        }

        // Finally, update it all!

        Logger.v(this, "audioButtonEnabled: " + audioButtonEnabled);
        Logger.v(this, "audioButtonChecked: " + audioButtonChecked);
        Logger.v(this, "showMoreIndicator: " + showMoreIndicator);
        Logger.v(this, "showBluetoothIcon: " + showBluetoothIcon);
        Logger.v(this, "showSpeakerphoneOnIcon: " + showSpeakerphoneOnIcon);
        Logger.v(this, "showSpeakerphoneOffIcon: " + showSpeakerphoneOffIcon);
        Logger.v(this, "showHandsetIcon: " + showHandsetIcon);

        // Constants for Drawable.setAlpha()
        final int HIDDEN = 0;
        final int VISIBLE = 255;

        mAudioButton.setEnabled(audioButtonEnabled);
        mAudioButton.setChecked(audioButtonChecked);

        final LayerDrawable layers = (LayerDrawable) mAudioButton.getBackground();
        Logger.d(this, "'layers' drawable: " + layers);

        layers.findDrawableByLayerId(R.id.compoundBackgroundItem)
                .setAlpha(showToggleIndicator ? VISIBLE : HIDDEN);

        layers.findDrawableByLayerId(R.id.moreIndicatorItem)
                .setAlpha(showMoreIndicator ? VISIBLE : HIDDEN);

        layers.findDrawableByLayerId(R.id.bluetoothItem)
                .setAlpha(showBluetoothIcon ? VISIBLE : HIDDEN);

        layers.findDrawableByLayerId(R.id.handsetItem)
                .setAlpha(showHandsetIcon ? VISIBLE : HIDDEN);

        layers.findDrawableByLayerId(R.id.speakerphoneOnItem)
                .setAlpha(showSpeakerphoneOnIcon ? VISIBLE : HIDDEN);

        layers.findDrawableByLayerId(R.id.speakerphoneOffItem)
                .setAlpha(showSpeakerphoneOffIcon ? VISIBLE : HIDDEN);
    }

    private void showAudioModePopup() {
        Logger.d(this, "showAudioPopup()...");

        mAudioModePopup = new PopupMenu(getView().getContext(), mAudioButton /* anchorView */);
        mAudioModePopup.getMenuInflater().inflate(R.menu.incall_audio_mode_menu,
                mAudioModePopup.getMenu());
        mAudioModePopup.setOnMenuItemClickListener(this);
        mAudioModePopup.setOnDismissListener(this);

        final Menu menu = mAudioModePopup.getMenu();

        // TODO: Still need to have the "currently active" audio mode come
        // up pre-selected (or focused?) with a blue highlight.  Still
        // need exact visual design, and possibly framework support for this.
        // See comments below for the exact logic.

        final MenuItem speakerItem = menu.findItem(R.id.audio_mode_speaker);
        speakerItem.setEnabled(isSupported(AudioMode.SPEAKER));
        // TODO: Show speakerItem as initially "selected" if
        // speaker is on.

        // We display *either* "earpiece" or "wired headset", never both,
        // depending on whether a wired headset is physically plugged in.
        final MenuItem earpieceItem = menu.findItem(R.id.audio_mode_earpiece);
        final MenuItem wiredHeadsetItem = menu.findItem(R.id.audio_mode_wired_headset);

        final boolean usingHeadset = isSupported(AudioMode.WIRED_HEADSET);
        earpieceItem.setVisible(!usingHeadset);
        earpieceItem.setEnabled(!usingHeadset);
        wiredHeadsetItem.setVisible(usingHeadset);
        wiredHeadsetItem.setEnabled(usingHeadset);
        // TODO: Show the above item (either earpieceItem or wiredHeadsetItem)
        // as initially "selected" if speakerOn and
        // bluetoothIndicatorOn are both false.

        final MenuItem bluetoothItem = menu.findItem(R.id.audio_mode_bluetooth);
        bluetoothItem.setEnabled(isSupported(AudioMode.BLUETOOTH));
        // TODO: Show bluetoothItem as initially "selected" if
        // bluetoothIndicatorOn is true.

        mAudioModePopup.show();

        // Unfortunately we need to manually keep track of the popup menu's
        // visiblity, since PopupMenu doesn't have an isShowing() method like
        // Dialogs do.
        mAudioModePopupVisible = true;
    }

    private boolean isSupported(int mode) {
        return (mode == (getPresenter().getSupportedAudio() & mode));
    }

    private boolean isAudio(int mode) {
        return (mode == getPresenter().getAudioMode());
    }

    @Override
    public void displayDialpad(boolean value) {
        mShowDialpadButton.setChecked(value);
        if (getActivity() != null && getActivity() instanceof InCallActivity) {
            ((InCallActivity) getActivity()).displayDialpad(value);
        }
    }
}
