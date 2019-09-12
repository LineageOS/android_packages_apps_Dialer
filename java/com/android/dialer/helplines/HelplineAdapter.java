/*
 * Copyright (C) 2019 The LineageOS Project
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
package com.android.dialer.helplines;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.dialer.app.calllog.IntentProvider;
import com.android.dialer.helplines.utils.HelplineUtils;
import com.android.dialer.R;
import com.android.server.telecom.SensitivePhoneNumberInfo;

import java.util.ArrayList;
import java.util.List;

class HelplineAdapter extends RecyclerView.Adapter<HelplineAdapter.ViewHolder> {
    private Context mContext;
    private Resources mResources;
    private List<SensitivePhoneNumberInfo> mList = new ArrayList<>();

    HelplineAdapter(Context context) {
        mContext = context;
        mResources = mContext.getResources();
    }

    public void update(List<SensitivePhoneNumberInfo> list) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new Callback(mList, list));
        mList = list;
        result.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_helpline, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
        viewHolder.bind(mList.get(i));
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private TextView mLabelView;
        private TextView mLanguageView;
        private ImageView mCallIcon;

        ViewHolder(@NonNull View itemView) {
            super(itemView);

            mLabelView = itemView.findViewById(R.id.item_helpline_title);
            mLanguageView = itemView.findViewById(R.id.item_helpline_languages);
            mCallIcon = itemView.findViewById(R.id.item_helpline_call_icon);
        }

        void bind(SensitivePhoneNumberInfo helpline) {
            String title = HelplineUtils.getTitle(mResources, helpline);
            mLabelView.setText(title);

            String number = helpline.get("number");
            if (TextUtils.isEmpty(number)) {
                mCallIcon.setVisibility(View.GONE);
            }

            /*String languages = HelplineUtils.getLanguages(mResources, helpline);
            if (TextUtils.isEmpty(languages)) {
                mLanguageView.setVisibility(View.VISIBLE);
                mLanguageView.setText(languages);
            }*/

            mCallIcon.setOnClickListener(v -> {
                IntentProvider provider = IntentProvider.getReturnCallIntentProvider(number);
                Intent intent = provider.getClickIntent(mContext);
                mContext.startActivity(intent);
            });
        }
    }

    private static class Callback extends DiffUtil.Callback {
        List<SensitivePhoneNumberInfo> mOldList;
        List<SensitivePhoneNumberInfo> mNewList;

        public Callback(List<SensitivePhoneNumberInfo> oldList,
                        List<SensitivePhoneNumberInfo> newList) {
            mOldList = oldList;
            mNewList = newList;
        }


        @Override
        public int getOldListSize() {
            return mOldList.size();
        }

        @Override
        public int getNewListSize() {
            return mNewList.size();
        }

        @Override
        public boolean areItemsTheSame(int iOld, int iNew) {
            /*String oldPkg = mOldList.get(iOld).getPackageName();
            String newPkg = mNewList.get(iNew).getPackageName();
            return oldPkg.equals(newPkg);*/
            return false;
        }

        @Override
        public boolean areContentsTheSame(int iOld, int iNew) {
            return false;
            //return mOldList.get(iOld).equals(mNewList.get(iNew));
        }
    }
}
