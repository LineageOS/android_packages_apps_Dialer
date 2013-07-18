package com.android.incallui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.android.incallui.widget.multiwaveview.GlowPadView;

/**
 *
 */
public class AnswerUi extends GlowPadView implements AnswerPresenter.Ui,
        GlowPadView.OnTriggerListener {

    private static final String TAG = AnswerUi.class.getSimpleName();

    private AnswerPresenter mPresenter;

    public AnswerUi(Context context) {
        super(context);
    }

    public AnswerUi(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onGrabbed(View v, int handle) {

    }

    @Override
    public void onReleased(View v, int handle) {

    }

    @Override
    public void onTrigger(View v, int target) {
        final int resId = getResourceIdForTarget(target);
        switch (resId) {
            case R.drawable.ic_lockscreen_answer:
                mPresenter.onAnswer();
                break;
            case R.drawable.ic_lockscreen_decline:
                mPresenter.onDecline();
                break;
            case R.drawable.ic_lockscreen_text:
                mPresenter.onText();
                break;
            default:
                // Code should never reach here.
                Log.e(TAG, "Trigger detected on unhandled resource. Skipping.");
        }
    }

    @Override
    public void onGrabbedStateChange(View v, int handle) {

    }

    @Override
    public void onFinishFinalAnimation() {

    }

    @Override
    public void setPresenter(AnswerPresenter listener) {
        mPresenter = listener;
    }

}
