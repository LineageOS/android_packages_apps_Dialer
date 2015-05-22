/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.dialer.voicemail;

import static com.android.dialer.CallDetailActivity.EXTRA_VOICEMAIL_START_PLAYBACK;
import static com.android.dialer.CallDetailActivity.EXTRA_VOICEMAIL_URI;

import android.app.Activity;
import android.app.Fragment;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.VoicemailContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.common.io.MoreCloseables;
import com.android.contacts.commonbind.analytics.AnalyticsUtil;
import com.android.dialer.R;

import com.google.common.base.Preconditions;

import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Displays and plays back a single voicemail.
 * <p>
 * When the Activity containing this Fragment is created, voicemail playback
 * will begin immediately. The Activity is expected to be started via an intent
 * containing a suitable voicemail uri to playback.
 * <p>
 * This class is not thread-safe, it is thread-confined. All calls to all public
 * methods on this class are expected to come from the main ui thread.
 */
@NotThreadSafe
public class VoicemailPlaybackFragment extends Fragment {
    private static final String TAG = VoicemailPlaybackFragment.class.getSimpleName();
    private static final int NUMBER_OF_THREADS_IN_POOL = 2;
    private static final String[] HAS_CONTENT_PROJECTION = new String[] {
        VoicemailContract.Voicemails.HAS_CONTENT,
    };

    private VoicemailPlaybackPresenter mPresenter;
    private View mPlaybackLayout;

    private PowerManager.WakeLock mProximityWakeLock;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mPlaybackLayout = inflater.inflate(R.layout.playback_layout, null);
        return mPlaybackLayout;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Bundle arguments = getArguments();
        Preconditions.checkNotNull(arguments, "fragment must be started with arguments");
        Uri voicemailUri = arguments.getParcelable(EXTRA_VOICEMAIL_URI);
        Preconditions.checkNotNull(voicemailUri, "fragment must contain EXTRA_VOICEMAIL_URI");
        boolean startPlayback = arguments.getBoolean(EXTRA_VOICEMAIL_START_PLAYBACK, false);

        PowerManager powerManager =
                (PowerManager) getActivity().getSystemService(Context.POWER_SERVICE);
        if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            mProximityWakeLock = powerManager.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, TAG);
        } else {
            mProximityWakeLock = null;
        }

        mPresenter = new VoicemailPlaybackPresenter(
                createPlaybackViewImpl(),
                voicemailUri,
                startPlayback,
                mProximityWakeLock);
        mPresenter.onCreate(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        mPresenter.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onStart() {
        super.onStart();
        AnalyticsUtil.sendScreenView(this);
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        mPresenter.onRestoreInstanceState(savedInstanceState);
        super.onViewStateRestored(savedInstanceState);
    }

    @Override
    public void onPause() {
        mPresenter.onPause();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mPresenter.onDestroy();
        super.onDestroy();
    }

    private PlaybackViewImpl createPlaybackViewImpl() {
        return new PlaybackViewImpl(new ActivityReference(), getActivity().getApplicationContext(),
                mPlaybackLayout);
    }

     /**
     * Formats a number of milliseconds as something that looks like {@code 00:05}.
     * <p>
     * We always use four digits, two for minutes two for seconds.  In the very unlikely event
     * that the voicemail duration exceeds 99 minutes, the display is capped at 99 minutes.
     */
    private static String formatAsMinutesAndSeconds(int millis) {
        int seconds = millis / 1000;
        int minutes = seconds / 60;
        seconds -= minutes * 60;
        if (minutes > 99) {
            minutes = 99;
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * An object that can provide us with an Activity.
     * <p>
     * Fragments suffer the drawback that the Activity they belong to may sometimes be null. This
     * can happen if the Fragment is detached, for example. In that situation a call to
     * {@link Fragment#getString(int)} will throw and {@link IllegalStateException}. Also, calling
     * {@link Fragment#getActivity()} is dangerous - it may sometimes return null. And thus blindly
     * calling a method on the result of getActivity() is dangerous too.
     * <p>
     * To work around this, I have made the {@link PlaybackViewImpl} class static, so that it does
     * not have access to any Fragment methods directly. Instead it uses an application Context for
     * things like accessing strings, accessing system services. It only uses the Activity when it
     * absolutely needs it - and does so through this class. This makes it easy to see where we have
     * to check for null properly.
     */
    private final class ActivityReference {
        /** Gets this Fragment's Activity: <b>may be null</b>. */
        public final Activity get() {
            return getActivity();
        }
    }

    /**  Methods required by the PlaybackView for the VoicemailPlaybackPresenter. */
    private final class PlaybackViewImpl implements VoicemailPlaybackPresenter.PlaybackView {
        private final ActivityReference mActivityReference;
        private final Context mApplicationContext;
        private final SeekBar mPlaybackSeek;
        private final ImageButton mStartStopButton;
        private final ImageButton mPlaybackSpeakerphone;
        private final TextView mPlaybackPosition;

        public PlaybackViewImpl(ActivityReference activityReference, Context applicationContext,
                View playbackLayout) {
            Preconditions.checkNotNull(activityReference);
            Preconditions.checkNotNull(applicationContext);
            Preconditions.checkNotNull(playbackLayout);
            mActivityReference = activityReference;
            mApplicationContext = applicationContext;
            mPlaybackSeek = (SeekBar) playbackLayout.findViewById(R.id.playback_seek);
            mStartStopButton = (ImageButton) playbackLayout.findViewById(
                    R.id.playback_start_stop);
            mPlaybackSpeakerphone = (ImageButton) playbackLayout.findViewById(
                    R.id.playback_speakerphone);
            mPlaybackPosition =
                    (TextView) playbackLayout.findViewById(R.id.playback_position_text);
        }

        @Override
        public void finish() {
            Activity activity = mActivityReference.get();
            if (activity != null) {
                activity.finish();
            }
        }

        @Override
        public void runOnUiThread(Runnable runnable) {
            Activity activity = mActivityReference.get();
            if (activity != null) {
                activity.runOnUiThread(runnable);
            }
        }

        @Override
        public Context getDataSourceContext() {
            return mApplicationContext;
        }

        @Override
        public void setStartStopListener(View.OnClickListener listener) {
            mStartStopButton.setOnClickListener(listener);
        }

        @Override
        public void setSpeakerphoneListener(View.OnClickListener listener) {
            mPlaybackSpeakerphone.setOnClickListener(listener);
        }

        @Override
        public void setPositionSeekListener(SeekBar.OnSeekBarChangeListener listener) {
            mPlaybackSeek.setOnSeekBarChangeListener(listener);
        }

        @Override
        public void playbackStarted() {
            mStartStopButton.setImageResource(R.drawable.ic_hold_pause);
        }

        @Override
        public void playbackStopped() {
            mStartStopButton.setImageResource(R.drawable.ic_play);
        }

        @Override
        public void registerContentObserver(Uri uri, ContentObserver observer) {
            mApplicationContext.getContentResolver().registerContentObserver(uri, false, observer);
        }

        @Override
        public void unregisterContentObserver(ContentObserver observer) {
            mApplicationContext.getContentResolver().unregisterContentObserver(observer);
        }

        @Override
        public void setClipPosition(int clipPositionInMillis, int clipLengthInMillis) {
            int seekBarPosition = Math.max(0, clipPositionInMillis);
            int seekBarMax = Math.max(seekBarPosition, clipLengthInMillis);
            if (mPlaybackSeek.getMax() != seekBarMax) {
                mPlaybackSeek.setMax(seekBarMax);
            }
            mPlaybackSeek.setProgress(seekBarPosition);
            mPlaybackPosition.setText(formatAsMinutesAndSeconds(seekBarMax - seekBarPosition));
        }

        private String getString(int resId) {
            return mApplicationContext.getString(resId);
        }

        @Override
        public void setIsBuffering() {
            disableUiElements();
            mPlaybackPosition.setText(getString(R.string.voicemail_buffering));
        }

        @Override
        public void setIsFetchingContent() {
            disableUiElements();
            mPlaybackPosition.setText(getString(R.string.voicemail_fetching_content));
        }

        @Override
        public void setFetchContentTimeout() {
            disableUiElements();
            mPlaybackPosition.setText(getString(R.string.voicemail_fetching_timout));
        }

        @Override
        public int getDesiredClipPosition() {
            return mPlaybackSeek.getProgress();
        }

        @Override
        public void disableUiElements() {
            mStartStopButton.setEnabled(false);
            mPlaybackSpeakerphone.setEnabled(false);
            mPlaybackSeek.setProgress(0);
            mPlaybackSeek.setEnabled(false);
        }

        @Override
        public void playbackError(Exception e) {
            disableUiElements();
            mPlaybackPosition.setText(getString(R.string.voicemail_playback_error));
            Log.e(TAG, "Could not play voicemail", e);
        }

        @Override
        public void enableUiElements() {
            mStartStopButton.setEnabled(true);
            mPlaybackSpeakerphone.setEnabled(true);
            mPlaybackSeek.setEnabled(true);
        }

        @Override
        public void sendFetchVoicemailRequest(Uri voicemailUri) {
            Intent intent = new Intent(VoicemailContract.ACTION_FETCH_VOICEMAIL, voicemailUri);
            mApplicationContext.sendBroadcast(intent);
        }

        @Override
        public boolean queryHasContent(Uri voicemailUri) {
            ContentResolver contentResolver = mApplicationContext.getContentResolver();
            Cursor cursor = contentResolver.query(
                    voicemailUri, HAS_CONTENT_PROJECTION, null, null, null);
            try {
                if (cursor != null && cursor.moveToNext()) {
                    return cursor.getInt(cursor.getColumnIndexOrThrow(
                            VoicemailContract.Voicemails.HAS_CONTENT)) == 1;
                }
            } finally {
                MoreCloseables.closeQuietly(cursor);
            }
            return false;
        }

        private AudioManager getAudioManager() {
            return (AudioManager) mApplicationContext.getSystemService(Context.AUDIO_SERVICE);
        }

        @Override
        public boolean isSpeakerPhoneOn() {
            return getAudioManager().isSpeakerphoneOn();
        }

        @Override
        public void setSpeakerPhoneOn(boolean on) {
            getAudioManager().setSpeakerphoneOn(on);
            if (on) {
                mPlaybackSpeakerphone.setImageResource(R.drawable.ic_speakerphone_on);
                // Speaker is now on, tapping button will turn it off.
                mPlaybackSpeakerphone.setContentDescription(
                        mApplicationContext.getString(R.string.voicemail_speaker_off));
            } else {
                mPlaybackSpeakerphone.setImageResource(R.drawable.ic_speakerphone_off);
                // Speaker is now off, tapping button will turn it on.
                mPlaybackSpeakerphone.setContentDescription(
                        mApplicationContext.getString(R.string.voicemail_speaker_on));
            }
        }

        @Override
        public void setVolumeControlStream(int streamType) {
            Activity activity = mActivityReference.get();
            if (activity != null) {
                activity.setVolumeControlStream(streamType);
            }
        }
    }
}
