/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.dialer.dialpad;

import android.content.Context;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.dialer.R;

public class SmartDialTextView extends TextView {

    private final float mPadding;
    private final float mExtraPadding;

    public SmartDialTextView(Context context) {
        this(context, null);
    }

    public SmartDialTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPadding = getResources().getDimension(R.dimen.smartdial_suggestions_padding);
        mExtraPadding = getResources().getDimension(R.dimen.smartdial_suggestions_extra_padding);
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        rescaleText(getWidth());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rescaleText(w);
    }

    private void rescaleText(int w) {
        if (w == 0) {
            return;
        }
        setTextScaleX(1);
        final Paint paint = getPaint();
        float width = w - 2 * mPadding - 2 * mExtraPadding;

        float ratio = width / paint.measureText(getText().toString());
        TextUtils.TruncateAt ellipsizeAt = null;
        if (ratio < 1.0f) {
            if (ratio < 0.8f) {
                // If the text is too big to fit even after scaling to 80%, just ellipsize it
                // instead.
                ellipsizeAt = TextUtils.TruncateAt.END;
                setTextScaleX(0.8f);
            } else {
                setTextScaleX(ratio);
            }
        }
        setEllipsize(ellipsizeAt);
    }
}
