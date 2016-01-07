/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader.TileMode;
import android.util.AttributeSet;
import android.view.View;

import com.android.dialer.R;
import com.google.common.collect.Lists;

import java.util.ArrayList;

/**
 * Pie chart with multiple items.
 */
public class PieChartView extends View {
    public static final String TAG = "PieChartView";
    public static final boolean LOGD = false;

    private ArrayList<Slice> mSlices = Lists.newArrayList();

    private int mOriginAngle;
    private Matrix mMatrix = new Matrix();

    public class Slice {
        public long value;
        public Path path = new Path();
        public Paint paint;

        public Slice(long value, int color) {
            this.value = value;
            this.paint = buildFillPaint(color, getResources());
        }
    }

    public PieChartView(Context context) {
        this(context, null);
    }

    public PieChartView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PieChartView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setWillNotDraw(false);
    }

    private static Paint buildFillPaint(int color, Resources res) {
        final Paint paint = new Paint();

        paint.setColor(color);
        paint.setStyle(Style.FILL_AND_STROKE);
        paint.setAntiAlias(true);

        return paint;
    }

    public void setOriginAngle(int originAngle) {
        mOriginAngle = originAngle;
    }

    public void addSlice(long value, int color) {
        mSlices.add(new Slice(value, color));
    }

    public void removeAllSlices() {
        mSlices.clear();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final float centerX = getWidth() / 2;
        final float centerY = getHeight() / 2;

        mMatrix.reset();
        mMatrix.postScale(0.95f, 0.95f, centerX, centerY);

        generatePath();
    }

    public void generatePath() {
        long total = 0;
        for (Slice slice : mSlices) {
            slice.path.reset();
            total += slice.value;
        }

        // bail when not enough stats to render
        if (total == 0) {
            invalidate();
            return;
        }

        final int width = getWidth();
        final int height = getHeight();

        final RectF rect = new RectF(0, 0, width, height);

        float startAngle = mOriginAngle;
        for (Slice slice : mSlices) {
            final float sweepAngle = 360f * slice.value / total;

            // draw slice
            slice.path.moveTo(rect.centerX(), rect.centerY());
            slice.path.arcTo(rect, startAngle, sweepAngle);
            slice.path.lineTo(rect.centerX(), rect.centerY());

            startAngle += sweepAngle;
        }

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.concat(mMatrix);

        for (Slice slice : mSlices) {
            canvas.drawPath(slice.path, slice.paint);
        }
    }
}
