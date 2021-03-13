/*
 * Copyright (C) 2019-2021 The LineageOS Project
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

import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.dialer.R;
import com.android.dialer.helplines.utils.HelplineUtils;

import java.util.ArrayList;
import java.util.List;

class HelplineAdapter extends RecyclerView.Adapter<HelplineAdapter.ViewHolder> {

    private Resources mResources;
    private List<HelplineItem> mList = new ArrayList<>();
    private Listener mListener;

    HelplineAdapter(Resources resources, Listener listener) {
        mResources = resources;
        mListener = listener;
    }

    public void update(List<HelplineItem> list) {
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

    public interface Listener {
        void initiateCall(@NonNull String number);

        void onItemClicked(@NonNull HelplineItem item);
    }

    private static class Callback extends DiffUtil.Callback {
        List<HelplineItem> mOldList;
        List<HelplineItem> mNewList;

        public Callback(List<HelplineItem> oldList,
                        List<HelplineItem> newList) {
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
            String oldNumber = mOldList.get(iOld).getItem().getNumber();
            String newNumber = mOldList.get(iNew).getItem().getNumber();
            return oldNumber.equals(newNumber);
        }

        @Override
        public boolean areContentsTheSame(int iOld, int iNew) {
            return false;
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final View mItemView;
        private final TextView mLabelView;
        private final TextView mCategoriesView;
        private final TextView mLanguageView;
        private final ImageView mCallIcon;

        ViewHolder(@NonNull View itemView) {
            super(itemView);

            mItemView = itemView;
            mLabelView = itemView.findViewById(R.id.item_helpline_title);
            mCategoriesView = itemView.findViewById(R.id.item_helpline_categories);
            mLanguageView = itemView.findViewById(R.id.item_helpline_languages);
            mCallIcon = itemView.findViewById(R.id.item_helpline_call_icon);
        }

        void bind(HelplineItem item) {
            mItemView.setOnClickListener(v -> {
                mListener.onItemClicked(item);
            });

            String name = item.getName();
            if (!TextUtils.isEmpty(name)) {
                mLabelView.setText(name);
            } else {
                mLabelView.setText(R.string.unknown_helpline_name);
            }

            String categories = HelplineUtils.getCategories(mResources, item);
            if (!TextUtils.isEmpty(categories)) {
                mCategoriesView.setText(categories);
                mCategoriesView.setVisibility(View.VISIBLE);
            }

            String languages = HelplineUtils.getLanguages(mResources, item);
            if (!TextUtils.isEmpty(languages)) {
                mLanguageView.setVisibility(View.VISIBLE);
                mLanguageView.setText(languages);
            }

            String number = item.getItem().getNumber();
            if (!TextUtils.isEmpty(number)) {
                mCallIcon.setVisibility(View.VISIBLE);
                mCallIcon.setOnClickListener(v -> {
                    mListener.initiateCall(number);
                });
            }
        }
    }
}
