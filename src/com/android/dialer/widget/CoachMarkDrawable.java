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
import android.graphics.RectF;
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
    Drawable mProviderIcon;


    private static final int PAINT_STROKE = 3;
    public static final int BUTTON_WIDTH = 300;
    public static final int BUTTON_BOTTOM_PADDING = 400;
    public static final double LANDSCAPE_BUTTON_X_SCALE = 1.35;
    private static final float buttonRadius = 6;
    private static final int PROVIDER_ICON_PADDING = 25;

    public CoachMarkDrawable(Resources res, String markText, int y, int x, int radius,
                             int screenWidth, int screenHeight, int fontSize,
                             boolean hideGreyOverlay, float fontWidthScale, int circlePosX, Drawable providerIcon) {

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(Color.BLACK);
        mMarkText = markText.split("\n");

        mX = x;
        mY = y;
        mRadius = radius;
        mScreenWidth = screenWidth;
        mScreenHeight = screenHeight;
        mCirclePosX = circlePosX;
        mProviderIcon = providerIcon;

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

        if (mCirclePosX != 0) {
            mProviderIcon.setBounds(
                    mCirclePosX - mRadius + PROVIDER_ICON_PADDING,
                    mY - mRadius + PROVIDER_ICON_PADDING,
                    mCirclePosX + mRadius - PROVIDER_ICON_PADDING,
                    mY + mRadius - PROVIDER_ICON_PADDING
            );
        } else {
            mProviderIcon.setBounds(
                    mX - mRadius + PROVIDER_ICON_PADDING,
                    mY - mRadius + PROVIDER_ICON_PADDING,
                    mX + mRadius - PROVIDER_ICON_PADDING,
                    mY + mRadius - PROVIDER_ICON_PADDING
            );
        }
        mProviderIcon.draw(canvas);

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

        // If this is not a fullscreen coachmark, quit here.
        if (mHideGreyOverlay) {
            return;
        }

        // Calculate the button position based on rotation.
        RectF r;
        if (mCirclePosX == 0) {
            r = new RectF(
                    mScreenWidth / 2 - BUTTON_WIDTH / 2,
                    mScreenHeight - BUTTON_BOTTOM_PADDING - (mFontSize * 2 + mFontSize / 2),
                    mScreenWidth / 2 + BUTTON_WIDTH / 2,
                    mScreenHeight - BUTTON_BOTTOM_PADDING
            );
        } else {
            float width = (float)(mScreenWidth / LANDSCAPE_BUTTON_X_SCALE);
            r = new RectF(
                    width - BUTTON_WIDTH / 2,
                    mScreenHeight - BUTTON_BOTTOM_PADDING - (mFontSize * 2 + mFontSize / 2),
                    width + BUTTON_WIDTH / 2,
                    mScreenHeight - BUTTON_BOTTOM_PADDING
            );
        }

        String buttonText = mRes.getString(R.string.provider_button_help);

        // find the rectangles center
        float rectCenter = r.height()/2;

        // find the left most coord where we should start our button text
        float buttonTextLeftMostX =  (r.left + BUTTON_WIDTH/2)
                - mPaint.measureText(buttonText.toUpperCase())/2;

        // find the baseline where we should draw our button text, based on fontsize and rectangle center
        float buttonTextBaseline = r.bottom - rectCenter/2 - mFontSize/4;

        // Draw the button with text
        canvas.drawRoundRect(r, buttonRadius, buttonRadius, mPaint);
        mPaint.setColor(mRes.getColor(R.color.coach_mark_text_with_overlay));
        canvas.drawText(buttonText.toUpperCase(), buttonTextLeftMostX, buttonTextBaseline, mPaint);

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
