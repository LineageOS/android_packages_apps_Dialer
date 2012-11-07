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

import com.android.contacts.R;
import com.google.common.collect.Lists;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

public class SmartDialAdapter extends BaseAdapter {
    public static final String LOG_TAG = "SmartDial";
    private final LayoutInflater mInflater;

    private List<SmartDialEntry> mEntries;
    private static Drawable mHighConfidenceHint;

    private final int mHighlightedTextColor;

    public SmartDialAdapter(Context context) {
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final Resources res = context.getResources();
        mHighConfidenceHint = SmartDialTextView.getHighConfidenceHintDrawable(
                res, res.getDimension(R.dimen.smartdial_confidence_hint_text_size),
                res.getColor(R.color.smartdial_confidence_drawable_color));
        mHighlightedTextColor = res.getColor(R.color.smartdial_highlighted_text_color);
        clear();
    }

    /** Remove all entries. */
    public void clear() {
        mEntries = Lists.newArrayList();
        notifyDataSetChanged();
    }

    /** Set entries. */
    public void setEntries(List<SmartDialEntry> entries) {
        if (entries == null) throw new IllegalArgumentException();
        mEntries = entries;

        if (mEntries.size() <= 1) {
            // add a null entry to push the single entry into the middle
            mEntries.add(0, null);
        } else if (mEntries.size() >= 2){
            // swap the 1st and 2nd entries so that the highest confidence match goes into the
            // middle
            final SmartDialEntry temp = mEntries.get(0);
            mEntries.set(0, mEntries.get(1));
            mEntries.set(1, temp);
        }

        notifyDataSetChanged();
    }

    @Override
    public boolean isEnabled(int position) {
        return !(mEntries.get(position) == null);
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public int getCount() {
        return mEntries.size();
    }

    @Override
    public Object getItem(int position) {
        return mEntries.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position; // Just use the position as the ID, so it's not stable.
    }

    @Override
    public boolean hasStableIds() {
        return false; // Not stable because we just use the position as the ID.
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final SmartDialTextView view;
        if (convertView == null) {
            view = (SmartDialTextView) mInflater.inflate(
                    R.layout.dialpad_smartdial_item, parent, false);
        } else {
            view = (SmartDialTextView) convertView;
        }
        // Set the display name with highlight.

        final SmartDialEntry item = mEntries.get(position);

        if (item == null) {
            // Clear the text in case the view was reused.
            view.setText("");
            // Empty view. We use this to force a single entry to be in the middle
            return view;
        }
        final SpannableString displayName = new SpannableString(item.displayName);
        for (final SmartDialMatchPosition p : item.matchPositions) {
            final int matchStart = p.start;
            final int matchEnd = p.end;
            if (matchStart < matchEnd) {
                // Create a new ForegroundColorSpan for each section of the name to highlight,
                // otherwise multiple highlights won't work.
                try {
                    displayName.setSpan(
                            new ForegroundColorSpan(mHighlightedTextColor), matchStart, matchEnd,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                } catch (final IndexOutOfBoundsException e) {
                    Log.wtf(LOG_TAG,
                            "Invalid match positions provided - [" + matchStart + ","
                            + matchEnd + "] for display name: " + item.displayName);
                }
            }
        }

        if (position == 1) {
            view.setCompoundDrawablesWithIntrinsicBounds(
                    null, null, null, mHighConfidenceHint);
            // Hack to align text in this view with text in other views without the
            // overflow drawable
            view.setCompoundDrawablePadding(-mHighConfidenceHint.getIntrinsicHeight());
        } else {
            view.setCompoundDrawablesWithIntrinsicBounds(
                    null, null, null, null);
        }


        view.setText(displayName);
        view.setTag(item);

        return view;
    }
}
