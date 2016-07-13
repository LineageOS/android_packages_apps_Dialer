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
import android.hardware.display.DisplayManager;
import android.view.Display;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

import com.google.common.base.Preconditions;

/**
 * This class listens to below events and notifes whether InCallUI is visible to the user or not.
 * a. InCallActivity's lifecycle events (onStop/onStart)
 * b. Display state change events (DISPLAY_ON/DISPLAY_OFF)
 */
public class InCallUiStateNotifier implements DisplayManager.DisplayListener {

    private List<InCallUiStateNotifierListener> mInCallUiStateNotifierListeners =
            new CopyOnWriteArrayList<>();
    private static InCallUiStateNotifier sInCallUiStateNotifier;
    private DisplayManager mDisplayManager;
    private Context mContext;

    /**
     * Tracks whether the application is in the background. {@code True} if the application is in
     * the background, {@code false} otherwise.
     */
    private boolean mIsInBackground;

    /**
     * Tracks whether display is ON/OFF. {@code True} if display is ON, {@code false} otherwise.
     */
    private boolean mIsDisplayOn;

    /**
     * Handles set up of the {@class InCallUiStateNotifier}. Instantiates the context needed by
     * the class and adds a listener to listen to display state changes.
     */
    public void setUp(Context context) {
        mContext = context;
        mDisplayManager = (DisplayManager) mContext.getSystemService(Context.DISPLAY_SERVICE);
        mDisplayManager.registerDisplayListener(this, null);
        mIsDisplayOn = isDisplayOn(
                mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY).getState());
        Log.d(this, "setUp mIsDisplayOn: " + mIsDisplayOn);
    }

    /**
     * Private constructor. Must use getInstance() to get this singleton.
     */
    private InCallUiStateNotifier() {
    }

    /**
     * This method returns a singleton instance of {@class InCallUiStateNotifier}
     */
    public static synchronized InCallUiStateNotifier getInstance() {
        if (sInCallUiStateNotifier == null) {
            sInCallUiStateNotifier = new InCallUiStateNotifier();
        }
        return sInCallUiStateNotifier;
    }

   /**
     * Adds a new {@link InCallUiStateNotifierListener}.
     *
     * @param listener The listener.
     */
    public void addListener(InCallUiStateNotifierListener listener) {
        Preconditions.checkNotNull(listener);
        mInCallUiStateNotifierListeners.add(listener);
    }

    /**
     * Remove a {@link InCallUiStateNotifierListener}.
     *
     * @param listener The listener.
     */
    public void removeListener(InCallUiStateNotifierListener listener) {
        if (listener != null) {
            mInCallUiStateNotifierListeners.remove(listener);
        } else {
            Log.e(this, "Can't remove null listener");
        }
    }

    /**
     * Notfies when visibility of InCallUI is changed. For eg.
     * when UE moves in/out of the foreground, display either turns ON/OFF
     * @param showing true if InCallUI is visible, false  otherwise.
     */
    private void notifyOnUiShowing(boolean showing) {
        Preconditions.checkNotNull(mInCallUiStateNotifierListeners);
        for (InCallUiStateNotifierListener listener : mInCallUiStateNotifierListeners) {
            listener.onUiShowing(showing);
        }
    }

    /**
     * Handles tear down of the {@class InCallUiStateNotifier}. Sets the context to null and
     * unregisters it's display listener.
     */
    public void tearDown() {
        mDisplayManager.unregisterDisplayListener(this);
        mDisplayManager = null;
        mContext = null;
        mInCallUiStateNotifierListeners.clear();
    }

    /**
      * checks to see whether InCallUI experience is visible to the user or not.
      * returns true if InCallUI experience is visible to the user else false.
      */
    private boolean isUiShowing() {
        /* Not in background and display is ON does mean that InCallUI is visible/showing.
        Return true in such cases else false */
        return  !mIsInBackground && mIsDisplayOn;
    }

    /**
     * Checks whether the display is ON.
     *
     * @param displayState The display's current state.
     */
    public static boolean isDisplayOn(int displayState) {
        return displayState == Display.STATE_ON ||
                displayState == Display.STATE_DOZE ||
                displayState == Display.STATE_DOZE_SUSPEND;
    }

    /**
     * Called when UE goes in/out of the foreground.
     * @param showing true if UE is in the foreground, false otherwise.
     */
    public void onUiShowing(boolean showing) {

        //Check UI's old state before updating corresponding state variable(s)
        final boolean wasShowing = isUiShowing();

        mIsInBackground = !showing;

        //Check UI's new state after updating corresponding state variable(s)
        final boolean isShowing = isUiShowing();

        Log.d(this, "onUiShowing wasShowing: " + wasShowing + " isShowing: " + isShowing);
        //notify if there is a change in UI state
        if (wasShowing != isShowing) {
            notifyOnUiShowing(showing);
        }
    }

    /**
     * This method overrides onDisplayRemoved method of {@interface DisplayManager.DisplayListener}
     * Added for completeness. No implementation yet.
     */
    @Override
    public void onDisplayRemoved(int displayId) {
    }

    /**
     * This method overrides onDisplayAdded method of {@interface DisplayManager.DisplayListener}
     * Added for completeness. No implementation yet.
     */
    @Override
    public void onDisplayAdded(int displayId) {
    }

    /**
     * This method overrides onDisplayAdded method of {@interface DisplayManager.DisplayListener}
     * The method gets invoked whenever the properties of a logical display have changed.
     */
    @Override
    public void onDisplayChanged(int displayId) {
        /* Ignore display changed indications if they are received for displays
         * other than default display
         */
        if (displayId != Display.DEFAULT_DISPLAY) {
            Log.w(this, "onDisplayChanged Ignoring...");
            return;
        }

        final int displayState = mDisplayManager.getDisplay(displayId).getState();
        Log.d(this, "onDisplayChanged displayState: " + displayState +
                " displayId: " + displayId);

        //Check UI's old state before updating corresponding state variable(s)
        final boolean wasShowing = isUiShowing();

        mIsDisplayOn = isDisplayOn(displayState);

        //Check UI's new state after updating corresponding state variable(s)
        final boolean isShowing = isUiShowing();

        Log.d(this, "onDisplayChanged wasShowing: " + wasShowing + " isShowing: " + isShowing);
        //notify if there is a change in UI state
        if (wasShowing != isShowing) {
            notifyOnUiShowing(mIsDisplayOn);
        }
    }
}
