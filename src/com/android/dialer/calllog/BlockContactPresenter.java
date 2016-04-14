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

package com.android.dialer.calllog;

import android.app.Activity;
import android.app.Fragment;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.common.util.BlockContactHelper;
import com.android.dialer.R;
import com.android.internal.telephony.util.BlacklistUtils;
import com.cyanogen.lookup.phonenumber.provider.LookupProviderImpl;

/**
 * Coordinator for Block contact UI and Blacklist state
 */
public class BlockContactPresenter implements View.OnClickListener,
        BlockContactHelper.StatusCallbacks {

    private Activity mActivity;
    private Fragment mTargetFragment;
    private BlockContactHelper mBlockContactHelper;

    private BlockContactViewHolder mViewHolder;

    public BlockContactPresenter(Activity activity, Fragment targetFragment) {
        mActivity = activity;
        mTargetFragment = targetFragment;
    }

    public void setControlView(View view, String number) {
        view.setOnClickListener(this);
        mViewHolder = BlockContactViewHolder.create(view, mViewHolder);
        if (mBlockContactHelper == null) {
            mBlockContactHelper = new BlockContactHelper(mActivity, new LookupProviderImpl(mActivity));
            mBlockContactHelper.setStatusListener(this);
        }
        mBlockContactHelper.setContactInfo(number);
    }

    @Override
    public void onClick(View v) {
        mBlockContactHelper.getBlockContactDialog(mBlockContactHelper.isContactBlacklisted() ?
                BlockContactHelper.BlockOperation.UNBLOCK : BlockContactHelper.BlockOperation.BLOCK,
                mTargetFragment)
                .show(mActivity.getFragmentManager(), "block_contact_dialog");
    }

    @Override
    public void onInfoAvailable() {
        updateView();
    }

    @Override
    public void onBlockCompleted() {
        updateView();
    }

    @Override
    public void onUnblockCompleted() {
        updateView();
    }

    private void updateView() {
        Resources res = mActivity.getResources();
        if (mBlockContactHelper.isContactBlacklisted()) {
            mViewHolder.mDescription.setText(R.string.call_log_action_unblock);
            mViewHolder.mDescription.setTextColor(res.getColor(
                    R.color.call_log_action_block_red));
            mViewHolder.mIcon.setColorFilter(res.getColor(
                    R.color.call_log_action_block_red), PorterDuff.Mode.SRC_ATOP);
        } else {
            mViewHolder.mDescription.setText(R.string.call_log_action_block);
            mViewHolder.mDescription.setTextColor(res.getColor(
                    R.color.call_log_action_block_gray));
            mViewHolder.mIcon.setColorFilter(res.getColor(
                    R.color.call_log_action_block_gray), PorterDuff.Mode.SRC_ATOP);
        }
    }

    public boolean canBlock() {
        return BlacklistUtils.isBlacklistEnabled(mActivity);
    }

    public void onBlockSelected(boolean notifyProvider) {
        mBlockContactHelper.blockContactAsync(notifyProvider);
    }

    public void onUnblockSelected(boolean notifyProvider) {
        mBlockContactHelper.unblockContactAsync(notifyProvider);
    }

    public void disable() {
        if (mBlockContactHelper != null) {
            mBlockContactHelper.setContactInfo((String) null);
        }
    }

    public void onDestroy() {
        if (mBlockContactHelper != null) {
            mBlockContactHelper.destroy();
        }
    }

    private static class BlockContactViewHolder {
        private TextView mDescription;
        private ImageView mIcon;

        public static BlockContactViewHolder create(View rootView, BlockContactViewHolder template) {
            if (template == null) {
                template = new BlockContactViewHolder();
            }
            template.mDescription = (TextView) rootView.findViewById(R.id.block_caller_text);
            template.mIcon = (ImageView) rootView.findViewById(R.id.block_caller_image);

            return template;
        }
    }
}
