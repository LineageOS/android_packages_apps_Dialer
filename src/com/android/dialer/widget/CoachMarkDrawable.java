/*
 * Copyright (C) 2016 The CyanogenMod Project
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

package com.android.dialer.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.util.Log;
import com.android.dialer.R;


public class CoachMarkDrawable extends Drawable {

    Paint mPaint;
    Paint eraser;
    String[] mMarkText;
    int mX;
    int mY;
    int mRadius;
    Canvas cv;
    Bitmap bm;
    int mScreenWidth;
    int mScreenHeight;
    int mFontSize;
    int mLineColor;
    boolean mHideGreyOverlay;
    Resources mRes;
    float mFontWidthScale;
    int mCirclePosX;


    private static final int PAINT_STROKE = 3;

    public CoachMarkDrawable(Resources res, String markText, int y, int x, int radius,
                             int screenWidth, int screenHeight, int fontSize,
                             boolean hideGreyOverlay, float fontWidthScale, int circlePosX) {

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(Color.BLACK);
        mMarkText = markText.split("\n");

        mX = x;
        mY = y;
        mRadius = radius;
        mScreenWidth = screenWidth;
        mScreenHeight = screenHeight;
        mCirclePosX = circlePosX;

        eraser = new Paint();
        eraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        eraser.setAntiAlias(true);
        mFontSize = fontSize;
        mFontWidthScale = fontWidthScale;
        mLineColor = res.getColor(R.color.coach_mark_line_color);
        mHideGreyOverlay = hideGreyOverlay;
        mRes = res;
    }

    @Override
    public void draw(Canvas canvas) {
        bm = Bitmap.createBitmap(mScreenWidth, mScreenHeight, Bitmap.Config.ARGB_8888);
        cv = new Canvas(bm);
        bm.eraseColor(Color.TRANSPARENT);

        if (!mHideGreyOverlay) {
            cv.drawARGB(75, 0, 0, 0);
        }

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(PAINT_STROKE);
        mPaint.setColor(mLineColor);

        // Draw the main circle around the object
        if (mCirclePosX != 0) {
            cv.drawCircle(mCirclePosX, mY, mRadius, eraser);
            cv.drawCircle(mCirclePosX, mY, mRadius, mPaint);
        } else {
            cv.drawCircle(mX, mY, mRadius, eraser);
            cv.drawCircle(mX, mY, mRadius, mPaint);
        }

        canvas.drawBitmap(bm, 0, 0, null);

        if (!mHideGreyOverlay) {
            mPaint.setColor(mRes.getColor(R.color.coach_mark_text_with_overlay));
        } else {
            mPaint.setColor(mRes.getColor(R.color.coach_mark_text));
        }
        mPaint.setTextSize(mFontSize);
        mPaint.setStyle(Paint.Style.FILL);

        // mX + mRadius represents the edge of the coach circle. Add a second mRadius size to
        // allow at least minimal space between the main circle and the other coach items.
        float calculatedX = mX + mRadius + mRadius;
        float stringWidth = 0;

        for (int i = 0; i < mMarkText.length; i++) {
            // Get the Y position based on the size of the text, and the line we are at.
            // if this is line zero, then this is just mY
            int yPos = (mY + (i * mFontSize));

            // If this is the first line in the coach text, calculate its width for placement of
            // all future items and text.
            if (stringWidth == 0) {

                // If we use the entire string width, there will be no padding.
                // Multiply by a scale factor
                stringWidth = (mPaint.measureText(mMarkText[i]) * mFontWidthScale);

                calculatedX += stringWidth;
            }

            // Draw line i of the text.
            canvas.drawText(mMarkText[i], mScreenWidth - calculatedX, yPos, mPaint);
        }

        mPaint.setColor(mLineColor);

        // We want to shrink the calculated end X position of the line pointing to the text
        // This way we don't draw right ontop of the text and instead draw to the left of it
        // For this, use half of the radius as padding.
        // Note: Increasing this number actually goes left instead of right.
        calculatedX += mRadius/2;

        if (mCirclePosX != 0) {
            // Draw the line from the main circle to the point
            canvas.drawLine(mCirclePosX + mRadius, mY, mScreenWidth - calculatedX, mY,
                    mPaint);
        } else {
            // Draw the line from the main circle to the point
            canvas.drawLine(mX + mRadius, mY, mScreenWidth - calculatedX, mY,
                    mPaint);
        }

        // Draw the point at the end of the line. This dot is 1/6th the size of the main coach
        // circle.
        canvas.drawCircle(mScreenWidth - calculatedX, mY, mRadius/6, mPaint);
    }

    @Override
    public void setAlpha(int i) {}

    @Override
    public void setColorFilter(ColorFilter colorFilter) {}

    @Override
    public int getOpacity() {
        return 0;
    }
}
