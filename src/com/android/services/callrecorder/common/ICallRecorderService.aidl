package com.android.services.callrecorder.common;

import com.android.services.callrecorder.common.CallRecording;

/**
 * Service for recording phone calls.  Only one recording may be active at a time
 * (i.e. every call to startRecording should be followed by a call to stopRecording).
 */
interface ICallRecorderService {

    /**
     * Start a recording.
     *
     * @return true if recording started successfully
     */
    boolean startRecording(String phoneNumber, long creationTime);

    /**
     * stops the current recording
     *
     * @return call recording data including the output filename
     */
    CallRecording stopRecording();

    /**
     * Recording status
     *
     * @return true if there is an active recording
     */
    boolean isRecording();

    /**
     * Get recording currently in progress
     *
     * @return call recording object
     */
    CallRecording getActiveRecording();

}
