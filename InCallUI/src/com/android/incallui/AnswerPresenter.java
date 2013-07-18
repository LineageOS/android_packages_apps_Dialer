package com.android.incallui;

/**
 *
 */
public class AnswerPresenter {

    private Ui mUi;

    public AnswerPresenter(Ui ui) {
        this.mUi = ui;

        mUi.setPresenter(this);
    }

    public void onAnswer() {

    }

    public void onDecline() {

    }

    public void onText() {

    }

    public interface Ui {
        void setPresenter(AnswerPresenter presenter);
    }
}
