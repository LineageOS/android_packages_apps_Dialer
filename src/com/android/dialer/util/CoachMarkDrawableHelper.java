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

package com.android.dialer.util;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewOverlay;
import android.view.ViewTreeObserver;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.widget.CoachMarkDrawable;

import com.android.phone.common.incall.CallMethodUtils;
import com.android.phone.common.incall.CallMethodInfo;
import com.android.phone.common.incall.CallMethodHelper;

import com.cyanogen.ambient.plugin.PluginStatus;

public class CoachMarkDrawableHelper {


    public static void assignViewTreeObserver(final View v, final Activity act, final View main,
                                              final boolean ignoreScreenSize, final View touch,
                                              final String unformatted) {
        final SharedPreferences pref = act
                .getSharedPreferences(DialtactsActivity.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        final CallMethodInfo cmi = shouldShowCoachMark(pref);
        if (cmi != null) {
            ViewTreeObserver vto = v.getViewTreeObserver();
            vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    v.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    buildOverlayCoachMark(act, main, v.getHeight(), ignoreScreenSize,
                            unformatted, touch, pref, cmi, 1.0f);
                }
            });
        } else {
            main.getOverlay().clear();
        }
    }

    public static void assignViewTreeObserverWithHeight(final View v, final Activity act,
                                                        final View main, final int height,
                                                        final boolean ignoreScreenSize,
                                                        final View touch,
                                                        final String unformatted,
                                                        final float fontWidthScale) {
        final SharedPreferences pref = act
                .getSharedPreferences(DialtactsActivity.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        final CallMethodInfo cmi = shouldShowCoachMark(pref);
        if (cmi != null) {
            touch.clearFocus();
            ViewTreeObserver vto = v.getViewTreeObserver();
            vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    v.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    buildOverlayCoachMark(act, main, height, ignoreScreenSize, unformatted,
                            touch, pref, cmi, fontWidthScale);
                }
            });
        } else {
            main.getOverlay().clear();
        }
    }

    private static CallMethodInfo shouldShowCoachMark(SharedPreferences pref) {

        String lastProvider = pref.getString(CallMethodUtils.PREF_LAST_ENABLED_PROVIDER, null);
        boolean showCoachmark = pref.getBoolean(CallMethodUtils.PREF_SPINNER_COACHMARK_SHOW, false);

        if (TextUtils.isEmpty(lastProvider)) {
            return null;
        }
        ComponentName cn = ComponentName.unflattenFromString(lastProvider);
        if (cn == null) {
            return null;
        }
        final CallMethodInfo cmi = CallMethodHelper.getCallMethod(cn);
        if (showCoachmark && !CallMethodHelper.getAllCallMethods().isEmpty() && cmi != null) {
            if (cmi.mStatus == PluginStatus.ENABLED) {
                return cmi;
            }
        }
        return null;
    }

    private static void buildOverlayCoachMark(Activity act, final View main, int parentHeight,
                                              final boolean ignoreScreenSize, String text,
                                              final View touch, final SharedPreferences sp,
                                              CallMethodInfo cmi, float fontWidthScale) {

        Display display = act.getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        Resources res = act.getResources();
        int width = res.getDimensionPixelSize(R.dimen.call_method_spinner_icon_size);
        int defaultPadding = res.getDimensionPixelSize(R.dimen
                .call_method_spinner_item_default_padding);
        int startPadding = res.getDimensionPixelSize(R.dimen.call_method_spinner_padding_start);
        int textHeight = res.getDimensionPixelSize(R.dimen.coachmark_hint_text);

        // Format text to show
        text = String.format(text, cmi.mName);

        // Pick placement for center of circle
        int y;
        int x = width;
        int circlePosX = 0;
        if (ignoreScreenSize) {
            y = parentHeight / 2;
        } else {
            y = (size.y - parentHeight) + defaultPadding/2;

            if (display.getRotation() == Surface.ROTATION_90 ||
                    display.getRotation() == Surface.ROTATION_270) {
                // when rotated, we want to take half the screen width into account only if not
                // ignoring the screen size.
                circlePosX = width + (size.x/2) + defaultPadding/2 + startPadding;
            } else {
                x += startPadding;
            }

        }

        // build our coachmark drawable
        Drawable d = new CoachMarkDrawable(res, text, y, x, width, size.x, size.y, textHeight,
                ignoreScreenSize, fontWidthScale, circlePosX, cmi.mBrandIcon);

        // Get and add view overlay
        final ViewOverlay overlay = main.getOverlay();
        overlay.clear();
        overlay.add(d);

        // Find the right X to prepare our touch zones.
        if (circlePosX != 0) {
            x = circlePosX;
        }

        // we need some padding so close touches to the circle work
        final int TOUCH_PADDING = res.getDimensionPixelSize(R.dimen.coachmark_touch_padding);

        final int circleTouchStart = (x - TOUCH_PADDING) - width/2;
        final int circleTouchEnd = (x + TOUCH_PADDING) + width/2;
        final int circleTouchTop = (y + TOUCH_PADDING) + width/2;
        final int circleTouchBottom = (y - TOUCH_PADDING) - width/2;

        final int buttonTouchStart;
        final int buttonTouchEnd;

        if (circlePosX == 0) {
            buttonTouchStart = size.x/2 - CoachMarkDrawable.BUTTON_WIDTH;
            buttonTouchEnd = size.x/2 + CoachMarkDrawable.BUTTON_WIDTH;
        } else {
            int touchWidth = (int)(size.x / CoachMarkDrawable.LANDSCAPE_BUTTON_X_SCALE);
            buttonTouchStart = touchWidth - CoachMarkDrawable.BUTTON_WIDTH;
            buttonTouchEnd = touchWidth + CoachMarkDrawable.BUTTON_WIDTH;
        }
        final int buttonTouchTop = size.y - CoachMarkDrawable.BUTTON_BOTTOM_PADDING - (textHeight*2 + textHeight/2);
        final int buttonTouchBottom = size.y + CoachMarkDrawable.BUTTON_BOTTOM_PADDING;

        touch.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                int x = (int)motionEvent.getX();
                int y = (int)motionEvent.getY();

                 boolean userTouchedCircle = (x <= circleTouchEnd && x >= circleTouchStart)
                         && (y <= circleTouchTop && y >= circleTouchBottom);

                boolean userTouchedButton = (x <= buttonTouchEnd && x >= buttonTouchStart)
                        && (y >= buttonTouchTop && y <= buttonTouchBottom);

                if (userTouchedButton || userTouchedCircle || ignoreScreenSize)  {
                    overlay.clear();
                    touch.setOnTouchListener(null);
                    // set coachmark status to hide
                    sp.edit().putBoolean(CallMethodUtils.PREF_SPINNER_COACHMARK_SHOW, false).apply();
                    if (!userTouchedButton) {
                        return false;
                    }
                }

                return true;
            }
        });
    }
}
