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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.dialer.R;

public class SmartDialTextView extends TextView {

    private final float mPadding;
    private final float mExtraPadding;
    private static final String HIGH_CONFIDENCE_HINT = "\u2026";

    public SmartDialTextView(Context context) {
        this(context, null);
    }

    public SmartDialTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPadding = getResources().getDimension(R.dimen.smartdial_suggestions_padding);
        mExtraPadding = getResources().getDimension(R.dimen.smartdial_suggestions_extra_padding);
    }

    /**
     * Returns a drawable that resembles a sideways overflow icon. Used to indicate the presence
     * of a high confidence match.
     *
     * @param res Resources that we will use to create our BitmapDrawable with
     * @param textSize Size of drawable to create
     * @param color Color of drawable to create
     * @return The drawable drawn according to the given parameters
     */
    public static Drawable getHighConfidenceHintDrawable(final Resources res, final float textSize,
            final int color) {
        final Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextAlign(Align.CENTER);
        paint.setTextSize(textSize);
        paint.setColor(color);
        final Rect bounds = new Rect();
        paint.getTextBounds(HIGH_CONFIDENCE_HINT, 0, HIGH_CONFIDENCE_HINT.length(), bounds);
        final int width = bounds.width();
        final int height = bounds.height();
        final Bitmap buffer = Bitmap.createBitmap(
                width, (height * 3 / 2), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(buffer);
        canvas.drawText(HIGH_CONFIDENCE_HINT, width / 2, height, paint);
        return new BitmapDrawable(res, buffer);
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
        if (ratio < 1.0f) {
            setTextScaleX(ratio);
        }
    }
}
