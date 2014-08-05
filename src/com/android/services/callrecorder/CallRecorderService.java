package com.android.services.callrecorder;

import android.app.Service;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.util.Log;
import com.android.services.callrecorder.common.CallRecording;
import com.android.services.callrecorder.common.ICallRecorderService;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CallRecorderService extends Service {

    private static final String TAG = "CallRecorderService";
    private static final boolean DBG = false;

    private static enum RecorderState {NONE, RECORDING};

    private MediaRecorder mMediaRecorder = null;
    private RecorderState mState = RecorderState.NONE;
    private CallRecording mCurrentRecording = null;

    private static final String AUDIO_SOURCE_PROPERTY = "persist.call_recording.src";

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmssSSS");

    private int getAudioSource() {
        return SystemProperties.getInt(AUDIO_SOURCE_PROPERTY, MediaRecorder.AudioSource.MIC);
    }

    private final ICallRecorderService.Stub mBinder = new ICallRecorderService.Stub() {
        @Override
        public CallRecording stopRecording() {
            if (getState() == RecorderState.RECORDING) {
                stopRecordingInternal();
                return mCurrentRecording;
            }
            else {
                return null;
            }
        }

        @Override
        public boolean startRecording(String phoneNumber, long creationTime) throws RemoteException {
            String fileName = generateFilename();
            mCurrentRecording = new CallRecording(phoneNumber, creationTime, fileName, new Date().getTime());
            return startRecordingInternal(fileName);

        }

        @Override
        public boolean isRecording() throws RemoteException {
            return getState() == RecorderState.RECORDING;
        }

        @Override
        public CallRecording getActiveRecording() throws RemoteException {
            return mCurrentRecording;
        }
    };

    @Override
    public void onCreate() {
        if (DBG) Log.d(TAG, "Creating CallRecorderService");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private synchronized boolean startRecordingInternal(String fileName) {

        if (mMediaRecorder != null) {
            if (DBG) Log.d(TAG, "Start called with recording in progress, stopping  current recording");
            stopRecordingInternal();
        }

        if (DBG) Log.d(TAG, "Starting recording");

        mMediaRecorder = new MediaRecorder();
        try {
            int audioSource = getAudioSource();
            if (DBG) Log.d(TAG, "Creating media recorder with audio source " + audioSource);
            mMediaRecorder.setAudioSource(audioSource);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        }
        catch (Exception e) {
            Log.w(TAG, "Error initializing media recorder", e);
            return false;
        }

        File storageDir = Environment.getExternalStoragePublicDirectory(CallRecording.PUBLIC_DIRECTORY_NAME);
        storageDir.mkdirs();
        File outputFile = new File(storageDir, fileName);

        String outputPath = outputFile.getAbsolutePath();
        if (DBG) Log.d(TAG, "Writing output to file " + outputPath);

        try {
            mMediaRecorder.setOutputFile(outputPath);
            mMediaRecorder.prepare();
            mMediaRecorder.start();
            setState(RecorderState.RECORDING);
        }
        catch (Exception e) {
            Log.w(TAG, "Exception starting recording for file " + outputPath, e);
            mMediaRecorder.reset();
            mMediaRecorder.release();
            return false;
        }

        return true;
    }

    private synchronized void stopRecordingInternal() {
        if (DBG) Log.d(TAG, "Stopping current recording");
        if (mMediaRecorder != null) {
            try {
                if (getState() == RecorderState.RECORDING) {
                    mMediaRecorder.stop();
                    mMediaRecorder.reset();
                    mMediaRecorder.release();
                }
            }
            catch (RuntimeException e) {
                Log.e(TAG, "Exception closing media recorder", e);
            }
            mMediaRecorder = null;
            setState(RecorderState.NONE);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (DBG) Log.d(TAG, "Destroying CallRecorderService");
    }

    private synchronized void setState(RecorderState state) {
        mState = state;
    }

    private synchronized RecorderState getState() {
        return mState;
    }

    private String generateFilename() {
        String timestamp = dateFormat.format(new Date());
        return "callrecorder_"+timestamp+".amr";
    }
}
