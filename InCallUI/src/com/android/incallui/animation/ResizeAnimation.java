/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.incallui.animation;

import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;

public class ResizeAnimation extends Animation {
    private final int mStartWidth;
    private final int mStartHeight;
    private final int mEndWidth;
    private final int mEndHeight;
    private final View mView;

    public ResizeAnimation(View view, int startWidth, int endWidth, int startHeight,
            int endHeight) {
        this.mView = view;
        this.mStartWidth = startWidth;
        this.mStartHeight = startHeight;
        this.mEndWidth = endWidth;
        this.mEndHeight = endHeight;
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        int width = (int) (mStartWidth + (mEndWidth - mStartWidth) * interpolatedTime);
        int height = (int) (mStartHeight + (mEndHeight - mStartHeight) * interpolatedTime);
        mView.getLayoutParams().width = width;
        mView.getLayoutParams().height = height;
        mView.requestLayout();
    }

    @Override
    public boolean willChangeBounds() {
        return true;
    }
}