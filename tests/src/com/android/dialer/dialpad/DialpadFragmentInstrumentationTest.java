package com.android.dialer.dialpad;

import android.test.ActivityInstrumentationTestCase2;
import android.view.View;

import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;

/**
 * Tests that rely on instrumenting an actual instance of a {@link DialpadFragment}.
 */
public class DialpadFragmentInstrumentationTest extends
        ActivityInstrumentationTestCase2<DialtactsActivity> {
    private DialtactsActivity mActivity;

    public DialpadFragmentInstrumentationTest() {
        super(DialtactsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
    }

    /**
     * Simulates a long click event on the zero key with a prior onPressed callback.
     *
     */
    public void testManualLongClickZero_DeletesPreviousCharacter() {
        final DialpadFragment fragment = showDialpad();
        pressAndReleaseKey(9, fragment);

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                final View zeroKey = findViewByDigit(0, fragment);
                fragment.onPressed(zeroKey, true);
                fragment.onLongClick(zeroKey);
            }
        });

        assertEquals("9+", fragment.getDigitsWidget().getText().toString());
    }

    /**
     * Simulates a long click event on the zero key without a prior onPressed
     * callback.
     */
    public void testSystemLongClickZero_PreservesPreviousCharacter() {
        final DialpadFragment fragment = showDialpad();
        pressAndReleaseKey(9, fragment);

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                final View zeroKey = findViewByDigit(0, fragment);
                fragment.onLongClick(zeroKey);
            }
        });

        assertEquals("9+", fragment.getDigitsWidget().getText().toString());
    }

    private DialpadFragment showDialpad() {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mActivity.showDialpad();
            }
        });
        getInstrumentation().waitForIdleSync();
        return (DialpadFragment) mActivity.getFragmentManager().findFragmentByTag(
                DialtactsActivity.TAG_DIALPAD_FRAGMENT);
    }

    private void pressAndReleaseKey(int digit, final DialpadFragment fragment) {
        final View dialpadKey = findViewByDigit(digit, fragment);
        final String digitsBefore = fragment.getDigitsWidget().getText().toString();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                fragment.onPressed(dialpadKey, true);
                fragment.onPressed(dialpadKey, false);
            }
        });
        assertEquals(digitsBefore + String.valueOf(digit),
                fragment.getDigitsWidget().getText().toString());
    }

    private View findViewByDigit(int digit, DialpadFragment fragment) {
        return fragment.getView().findViewById(getViewIdByDigit(digit));
    }

    private int getViewIdByDigit(int digit) {
        switch (digit) {
            case 0:
                return R.id.zero;
            case 1:
                return R.id.one;
            case 2:
                return R.id.two;
            case 3:
                return R.id.three;
            case 4:
                return R.id.four;
            case 5:
                return R.id.five;
            case 6:
                return R.id.six;
            case 7:
                return R.id.seven;
            case 8:
                return R.id.eight;
            case 9:
                return R.id.nine;
            default:
                return 0;
        }
    }
}
