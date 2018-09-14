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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.method.DialerKeyListener;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.contacts.common.compat.PhoneNumberUtilsCompat;
import com.android.dialer.R;
import com.android.phone.common.dialpad.DialpadKeyButton;
import com.android.phone.common.dialpad.DialpadView;

import java.util.HashMap;

/**
 * Fragment for call control buttons
 */
public class DialpadFragment extends BaseFragment<DialpadPresenter, DialpadPresenter.DialpadUi>
        implements DialpadPresenter.DialpadUi, View.OnTouchListener, View.OnKeyListener,
        View.OnHoverListener, View.OnClickListener {

    private static final int ACCESSIBILITY_DTMF_STOP_DELAY_MILLIS = 50;

    private final int[] mButtonIds = new int[] {R.id.zero, R.id.one, R.id.two, R.id.three,
            R.id.four, R.id.five, R.id.six, R.id.seven, R.id.eight, R.id.nine, R.id.star,
            R.id.pound};

    /**
     * LinearLayout with getter and setter methods for the translationY property using floats,
     * for animation purposes.
     */
    public static class DialpadSlidingLinearLayout extends LinearLayout {

        public DialpadSlidingLinearLayout(Context context) {
            super(context);
        }

        public DialpadSlidingLinearLayout(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public DialpadSlidingLinearLayout(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        public float getYFraction() {
            final int height = getHeight();
            if (height == 0) return 0;
            return getTranslationY() / height;
        }

        public void setYFraction(float yFraction) {
            setTranslationY(yFraction * getHeight());
        }
    }

    private EditText mDtmfDialerField;

    /** Hash Map to map a view id to a character*/
    private static final HashMap<Integer, Character> mDisplayMap =
        new HashMap<Integer, Character>();

    private static final Handler sHandler = new Handler(Looper.getMainLooper());


    /** Set up the static maps*/
    static {
        // Map the buttons to the display characters
        mDisplayMap.put(R.id.one, '1');
        mDisplayMap.put(R.id.two, '2');
        mDisplayMap.put(R.id.three, '3');
        mDisplayMap.put(R.id.four, '4');
        mDisplayMap.put(R.id.five, '5');
        mDisplayMap.put(R.id.six, '6');
        mDisplayMap.put(R.id.seven, '7');
        mDisplayMap.put(R.id.eight, '8');
        mDisplayMap.put(R.id.nine, '9');
        mDisplayMap.put(R.id.zero, '0');
        mDisplayMap.put(R.id.pound, '#');
        mDisplayMap.put(R.id.star, '*');
    }

    // KeyListener used with the "dialpad digits" EditText widget.
    private DTMFKeyListener mDialerKeyListener;

    private DialpadView mDialpadView;

    private int mCurrentTextColor;

    /**
     * Our own key listener, specialized for dealing with DTMF codes.
     *   1. Ignore the backspace since it is irrelevant.
     *   2. Allow ONLY valid DTMF characters to generate a tone and be
     *      sent as a DTMF code.
     *   3. All other remaining characters are handled by the superclass.
     *
     * This code is purely here to handle events from the hardware keyboard
     * while the DTMF dialpad is up.
     */
    private class DTMFKeyListener extends DialerKeyListener {

        private DTMFKeyListener() {
            super();
        }

        /**
         * Overriden to return correct DTMF-dialable characters.
         */
        @Override
        protected char[] getAcceptedChars(){
            return DTMF_CHARACTERS;
        }

        /** special key listener ignores backspace. */
        @Override
        public boolean backspace(View view, Editable content, int keyCode,
                KeyEvent event) {
            return false;
        }

        /**
         * Return true if the keyCode is an accepted modifier key for the
         * dialer (ALT or SHIFT).
         */
        private boolean isAcceptableModifierKey(int keyCode) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_ALT_LEFT:
                case KeyEvent.KEYCODE_ALT_RIGHT:
                case KeyEvent.KEYCODE_SHIFT_LEFT:
                case KeyEvent.KEYCODE_SHIFT_RIGHT:
                    return true;
                default:
                    return false;
            }
        }

        /**
         * Overriden so that with each valid button press, we start sending
         * a dtmf code and play a local dtmf tone.
         */
        @Override
        public boolean onKeyDown(View view, Editable content,
                                 int keyCode, KeyEvent event) {
            // if (DBG) log("DTMFKeyListener.onKeyDown, keyCode " + keyCode + ", view " + view);

            // find the character
            char c = (char) lookup(event, content);

            // if not a long press, and parent onKeyDown accepts the input
            if (event.getRepeatCount() == 0 && super.onKeyDown(view, content, keyCode, event)) {

                boolean keyOK = ok(getAcceptedChars(), c);

                // if the character is a valid dtmf code, start playing the tone and send the
                // code.
                if (keyOK) {
                    Log.d(this, "DTMFKeyListener reading '" + c + "' from input.");
                    getPresenter().processDtmf(c);
                } else {
                    Log.d(this, "DTMFKeyListener rejecting '" + c + "' from input.");
                }
                return true;
            }
            return false;
        }

        /**
         * Overriden so that with each valid button up, we stop sending
         * a dtmf code and the dtmf tone.
         */
        @Override
        public boolean onKeyUp(View view, Editable content,
                                 int keyCode, KeyEvent event) {
            // if (DBG) log("DTMFKeyListener.onKeyUp, keyCode " + keyCode + ", view " + view);

            super.onKeyUp(view, content, keyCode, event);

            // find the character
            char c = (char) lookup(event, content);

            boolean keyOK = ok(getAcceptedChars(), c);

            if (keyOK) {
                Log.d(this, "Stopping the tone for '" + c + "'");
                getPresenter().stopDtmf();
                return true;
            }

            return false;
        }

        /**
         * Handle individual keydown events when we DO NOT have an Editable handy.
         */
        public boolean onKeyDown(KeyEvent event) {
            char c = lookup(event);
            Log.d(this, "DTMFKeyListener.onKeyDown: event '" + c + "'");

            // if not a long press, and parent onKeyDown accepts the input
            if (event.getRepeatCount() == 0 && c != 0) {
                // if the character is a valid dtmf code, start playing the tone and send the
                // code.
                if (ok(getAcceptedChars(), c)) {
                    Log.d(this, "DTMFKeyListener reading '" + c + "' from input.");
                    getPresenter().processDtmf(c);
                    return true;
                } else {
                    Log.d(this, "DTMFKeyListener rejecting '" + c + "' from input.");
                }
            }
            return false;
        }

        /**
         * Handle individual keyup events.
         *
         * @param event is the event we are trying to stop.  If this is null,
         * then we just force-stop the last tone without checking if the event
         * is an acceptable dialer event.
         */
        public boolean onKeyUp(KeyEvent event) {
            if (event == null) {
                //the below piece of code sends stopDTMF event unnecessarily even when a null event
                //is received, hence commenting it.
                /*if (DBG) log("Stopping the last played tone.");
                stopTone();*/
                return true;
            }

            char c = lookup(event);
            Log.d(this, "DTMFKeyListener.onKeyUp: event '" + c + "'");

            // TODO: stopTone does not take in character input, we may want to
            // consider checking for this ourselves.
            if (ok(getAcceptedChars(), c)) {
                Log.d(this, "Stopping the tone for '" + c + "'");
                getPresenter().stopDtmf();
                return true;
            }

            return false;
        }

        /**
         * Find the Dialer Key mapped to this event.
         *
         * @return The char value of the input event, otherwise
         * 0 if no matching character was found.
         */
        private char lookup(KeyEvent event) {
            // This code is similar to {@link DialerKeyListener#lookup(KeyEvent, Spannable) lookup}
            int meta = event.getMetaState();
            int number = event.getNumber();

            if (!((meta & (KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON)) == 0) || (number == 0)) {
                int match = event.getMatch(getAcceptedChars(), meta);
                number = (match != 0) ? match : number;
            }

            return (char) number;
        }

        /**
         * Check to see if the keyEvent is dialable.
         */
        boolean isKeyEventAcceptable (KeyEvent event) {
            return (ok(getAcceptedChars(), lookup(event)));
        }

        /**
         * Overrides the characters used in {@link DialerKeyListener#CHARACTERS}
         * These are the valid dtmf characters.
         */
        public final char[] DTMF_CHARACTERS = new char[] {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '#', '*'
        };
    }

    @Override
    public void onClick(View v) {
        final AccessibilityManager accessibilityManager = (AccessibilityManager)
            v.getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        // When accessibility is on, simulate press and release to preserve the
        // semantic meaning of performClick(). Required for Braille support.
        if (accessibilityManager.isEnabled()) {
            final int id = v.getId();
            // Checking the press state prevents double activation.
            if (!v.isPressed() && mDisplayMap.containsKey(id)) {
                getPresenter().processDtmf(mDisplayMap.get(id));
                sHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        getPresenter().stopDtmf();
                    }
                }, ACCESSIBILITY_DTMF_STOP_DELAY_MILLIS);
            }
        }
        if (v.getId() == R.id.dialpad_back) {
            getActivity().onBackPressed();
        }
    }

    @Override
    public boolean onHover(View v, MotionEvent event) {
        // When touch exploration is turned on, lifting a finger while inside
        // the button's hover target bounds should perform a click action.
        final AccessibilityManager accessibilityManager = (AccessibilityManager)
            v.getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);

        if (accessibilityManager.isEnabled()
                && accessibilityManager.isTouchExplorationEnabled()) {
            final int left = v.getPaddingLeft();
            final int right = (v.getWidth() - v.getPaddingRight());
            final int top = v.getPaddingTop();
            final int bottom = (v.getHeight() - v.getPaddingBottom());

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    // Lift-to-type temporarily disables double-tap activation.
                    v.setClickable(false);
                    break;
                case MotionEvent.ACTION_HOVER_EXIT:
                    final int x = (int) event.getX();
                    final int y = (int) event.getY();
                    if ((x > left) && (x < right) && (y > top) && (y < bottom)) {
                        v.performClick();
                    }
                    v.setClickable(true);
                    break;
            }
        }

        return false;
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        Log.d(this, "onKey:  keyCode " + keyCode + ", view " + v);

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            int viewId = v.getId();
            if (mDisplayMap.containsKey(viewId)) {
                switch (event.getAction()) {
                case KeyEvent.ACTION_DOWN:
                    if (event.getRepeatCount() == 0) {
                        getPresenter().processDtmf(mDisplayMap.get(viewId));
                    }
                    break;
                case KeyEvent.ACTION_UP:
                    getPresenter().stopDtmf();
                    break;
                }
                // do not return true [handled] here, since we want the
                // press / click animation to be handled by the framework.
            }
        }
        return false;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        Log.d(this, "onTouch");
        int viewId = v.getId();

        // if the button is recognized
        if (mDisplayMap.containsKey(viewId)) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // Append the character mapped to this button, to the display.
                    // start the tone
                    getPresenter().processDtmf(mDisplayMap.get(viewId));
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // stop the tone on ANY other event, except for MOVE.
                    getPresenter().stopDtmf();
                    break;
            }
            // do not return true [handled] here, since we want the
            // press / click animation to be handled by the framework.
        }
        return false;
    }

    // TODO(klp) Adds hardware keyboard listener

    @Override
    public DialpadPresenter createPresenter() {
        return new DialpadPresenter();
    }

    @Override
    public DialpadPresenter.DialpadUi getUi() {
        return this;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View parent = inflater.inflate(
                R.layout.incall_dialpad_fragment, container, false);
        mDialpadView = (DialpadView) parent.findViewById(R.id.dialpad_view);
        mDialpadView.setCanDigitsBeEdited(false);
        mDialpadView.setBackgroundResource(R.color.incall_dialpad_background);
        mDtmfDialerField = (EditText) parent.findViewById(R.id.digits);
        if (mDtmfDialerField != null) {
            mDialerKeyListener = new DTMFKeyListener();
            mDtmfDialerField.setKeyListener(mDialerKeyListener);
            // remove the long-press context menus that support
            // the edit (copy / paste / select) functions.
            mDtmfDialerField.setLongClickable(false);
            mDtmfDialerField.setElegantTextHeight(false);
            configureKeypadListeners();
        }
        View backButton = mDialpadView.findViewById(R.id.dialpad_back);
        backButton.setVisibility(View.VISIBLE);
        backButton.setOnClickListener(this);

        return parent;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateColors();
    }

    public void updateColors() {
        int textColor = InCallPresenter.getInstance().getThemeColors().mPrimaryColor;
        // Disable dynamic digits color, for better theme compatibility
        if (getContext().getResources().getBoolean(R.bool.config_dialpadDigitsStaticColor)) {
            textColor = getContext().getResources().getColor(R.color.dialpad_digits_color);
        }

        if (mCurrentTextColor == textColor) {
            return;
        }

        DialpadKeyButton dialpadKey;
        for (int i = 0; i < mButtonIds.length; i++) {
            dialpadKey = (DialpadKeyButton) mDialpadView.findViewById(mButtonIds[i]);
            ((TextView) dialpadKey.findViewById(R.id.dialpad_key_number)).setTextColor(textColor);
        }

        mCurrentTextColor = textColor;
    }

    @Override
    public void onDestroyView() {
        mDialerKeyListener = null;
        super.onDestroyView();
    }

    /**
     * Getter for Dialpad text.
     *
     * @return String containing current Dialpad EditText text.
     */
    public String getDtmfText() {
        return mDtmfDialerField.getText().toString();
    }

    /**
     * Sets the Dialpad text field with some text.
     *
     * @param text Text to set Dialpad EditText to.
     */
    public void setDtmfText(String text) {
        mDtmfDialerField.setText(PhoneNumberUtilsCompat.createTtsSpannable(text));
    }

    @Override
    public void setVisible(boolean on) {
        if (on) {
            getView().setVisibility(View.VISIBLE);
        } else {
            getView().setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Starts the slide up animation for the Dialpad keys when the Dialpad is revealed.
     */
    public void animateShowDialpad() {
        final DialpadView dialpadView = (DialpadView) getView().findViewById(R.id.dialpad_view);
        dialpadView.animateShow();
    }

    @Override
    public void appendDigitsToField(char digit) {
        if (mDtmfDialerField != null) {
            // TODO: maybe *don't* manually append this digit if
            // mDialpadDigits is focused and this key came from the HW
            // keyboard, since in that case the EditText field will
            // get the key event directly and automatically appends
            // whetever the user types.
            // (Or, a cleaner fix would be to just make mDialpadDigits
            // *not* handle HW key presses.  That seems to be more
            // complicated than just setting focusable="false" on it,
            // though.)
            mDtmfDialerField.getText().append(digit);
        }
    }

    /**
     * Called externally (from InCallScreen) to play a DTMF Tone.
     */
    /* package */ boolean onDialerKeyDown(KeyEvent event) {
        Log.d(this, "Notifying dtmf key down.");
        if (mDialerKeyListener != null) {
            return mDialerKeyListener.onKeyDown(event);
        } else {
            return false;
        }
    }

    /**
     * Called externally (from InCallScreen) to cancel the last DTMF Tone played.
     */
    public boolean onDialerKeyUp(KeyEvent event) {
        Log.d(this, "Notifying dtmf key up.");
        if (mDialerKeyListener != null) {
            return mDialerKeyListener.onKeyUp(event);
        } else {
            return false;
        }
    }

    private void configureKeypadListeners() {
        DialpadKeyButton dialpadKey;
        for (int i = 0; i < mButtonIds.length; i++) {
            dialpadKey = (DialpadKeyButton) mDialpadView.findViewById(mButtonIds[i]);
            dialpadKey.setOnTouchListener(this);
            dialpadKey.setOnKeyListener(this);
            dialpadKey.setOnHoverListener(this);
            dialpadKey.setOnClickListener(this);
        }
    }
}
