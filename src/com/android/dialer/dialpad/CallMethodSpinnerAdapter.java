/*
 * Copyright (C) 2015 The CyanogenMod Project
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

import android.content.ComponentName;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.UserHandle;
import android.telecom.PhoneAccount;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.SpinnerAdapter;

import android.util.Log;

import android.widget.TextView;
import com.android.dialer.R;

import com.android.dialer.dialpad.VolteUtils.CallMethodInfo;

import java.util.List;

/**
 * Based on CallMethodSpinnerAdapter from DialerNext; this class provides
 * enough functionality to support the call_method_volte UI.
 */
public class CallMethodSpinnerAdapter extends ArrayAdapter<CallMethodInfo>
        implements SpinnerAdapter {
    private static final String TAG = CallMethodSpinnerAdapter.class.getSimpleName();

    private final Context mContext;
    private boolean mVolteInUse = true;   // Affects how the spinner is drawn

    public CallMethodSpinnerAdapter(Context context, List<CallMethodInfo> objects) {
        super(context, 0, objects);
        mContext = context.getApplicationContext();
    }

    /**
     * Return a View that displays the data at the specified position in the data set.
     *
     * @param position    The position of the item within the adapter's data set of the item whose
     *                    view we want.
     * @param convertView The old view to reuse, if possible.
     * @param parent      The parent that this view will eventually be attached to
     * @return A View corresponding to the data at the specified position.
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        CallMethodInfo callMethodInfo = getItem(position);
        ViewHolder holder = null;
        // Note: if mVolteInUse changes, it invalidates cached views
        if (convertView == null ||
            (holder = (ViewHolder)convertView.getTag()) == null ||
            holder.volteInUse != mVolteInUse)
        {
            int resId = mVolteInUse ? R.layout.call_method_spinner_item2 :
                                      R.layout.call_method_spinner_item;
            convertView = LayoutInflater.from(mContext).inflate(resId, parent, false);
            if (holder == null) holder = new ViewHolder();
            convertView.setTag(holder);
            holder.volteInUse = mVolteInUse;
            holder.icon = (ImageView) convertView.findViewById(R.id.call_method_spinner_item_image);
        }

        setIcon(holder.icon, callMethodInfo);

        return convertView;
    }

    /**
     * Return a View that displays in the drop down popup
     * the data at the specified position in the data set.</p>
     *
     * @param position     index of the item whose view we want.
     * @param convertView  the old view to reuse, if possible.
     * @param parent       the parent that this view will eventually be attached to
     * @return View corresponding to the data at the specified position.
     */
    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        CallMethodInfo callMethodInfo = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext)
                    .inflate(R.layout.call_method_spinner_dropdown_item, parent, false);
        }

        ImageView icon = (ImageView) convertView.findViewById(R.id.call_method_spinner_item_image);
        setIcon(icon, callMethodInfo);

        TextView text=(TextView) convertView.findViewById(R.id.call_method_spinner_item_text);
        text.setText(callMethodInfo.mName);

        return convertView;
    }

    public void setVolteInUse(boolean volteInUse) {
        if (volteInUse != mVolteInUse) {
            mVolteInUse = volteInUse;
            notifyDataSetChanged();
        }
    }

    private void setIcon(ImageView icon, CallMethodInfo callMethodInfo) {
        if (callMethodInfo.mIcon != null) {
            icon.setImageDrawable(callMethodInfo.mIcon);
            icon.getDrawable().setTintList(null);
            icon.setBackground(null);
        } else {
            int drawableId = getIconForSlot(callMethodInfo.mSlotId);
            Drawable foreground = mContext.getDrawable(drawableId);
            Drawable background = mContext.getDrawable(R.drawable.ic_sim_backing);

            if (callMethodInfo.mColor != PhoneAccount.NO_ICON_TINT) {
                foreground.setTint(callMethodInfo.mColor);
            } else {
                foreground.setTint(mContext.getResources().getColor(R.color.sim_icon_color));
            }

            Drawable[] layers = {background, foreground};
            LayerDrawable layerDrawable = new LayerDrawable(layers);
            icon.setImageDrawable(layerDrawable);
        }
    }

    private static int getIconForSlot(int slotId) {
        // Note: only supports 4 slots. If someone builds a phone with more
        // than that, we may have to revisit this.
        final int[] drawables = {
                R.drawable.ic_sim_1,
                R.drawable.ic_sim_2,
                R.drawable.ic_sim_3,
                R.drawable.ic_sim_4,
        };
        if (slotId < 0) return R.drawable.ic_sim_1;
        return drawables[slotId % 4];
    }

    private static class ViewHolder {
        ImageView icon;
        boolean volteInUse;
    }
}
