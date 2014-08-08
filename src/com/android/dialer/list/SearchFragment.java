/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.dialer.list;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ListView;

import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.common.list.PhoneNumberPickerFragment;
import com.android.contacts.common.util.ViewUtil;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.util.DialerUtils;
import com.android.phone.common.animation.AnimUtils;

public class SearchFragment extends PhoneNumberPickerFragment {

    private OnListFragmentScrolledListener mActivityScrollListener;

    /*
     * Stores the untouched user-entered string that is used to populate the add to contacts
     * intent.
     */
    private String mAddToContactNumber;
    private int mActionBarHeight;
    private int mShadowHeight;
    private int mPaddingTop;
    private int mShowDialpadDuration;
    private int mHideDialpadDuration;

    private HostInterface mActivity;

    public interface HostInterface {
        public boolean isActionBarShowing();
        public boolean isDialpadShown();
        public int getActionBarHideOffset();
        public int getActionBarHeight();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        setQuickContactEnabled(true);
        setAdjustSelectionBoundsEnabled(false);
        setDarkTheme(false);
        setPhotoPosition(ContactListItemView.getDefaultPhotoPosition(false /* opposite */));
        setUseCallableUri(true);
        sendScreenView();

        try {
            mActivityScrollListener = (OnListFragmentScrolledListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnListFragmentScrolledListener");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (isSearchMode()) {
            getAdapter().setHasHeader(0, false);
        }

        mActivity = (HostInterface) getActivity();

        final Resources res = getResources();
        mActionBarHeight = mActivity.getActionBarHeight();
        mShadowHeight  = res.getDrawable(R.drawable.search_shadow).getIntrinsicHeight();
        mPaddingTop = res.getDimensionPixelSize(R.dimen.search_list_padding_top);
        mShowDialpadDuration = res.getInteger(R.integer.dialpad_slide_in_duration);
        mHideDialpadDuration = res.getInteger(R.integer.dialpad_slide_out_duration);

        final View parentView = getView();

        final ListView listView = getListView();

        listView.setBackgroundColor(res.getColor(R.color.background_dialer_results));
        listView.setClipToPadding(false);
        setVisibleScrollbarEnabled(false);
        listView.setOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                mActivityScrollListener.onListFragmentScrollStateChange(scrollState);
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                    int totalItemCount) {
            }
        });

        updatePosition(false /* animate */);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewUtil.addBottomPaddingToListViewForFab(getListView(), getResources());
    }

    @Override
    protected void setSearchMode(boolean flag) {
        super.setSearchMode(flag);
        // This hides the "All contacts with phone numbers" header in the search fragment
        final ContactEntryListAdapter adapter = getAdapter();
        if (adapter != null) {
            adapter.setHasHeader(0, false);
        }
    }

    public void setAddToContactNumber(String addToContactNumber) {
        mAddToContactNumber = addToContactNumber;
    }

    @Override
    protected ContactEntryListAdapter createListAdapter() {
        DialerPhoneNumberListAdapter adapter = new DialerPhoneNumberListAdapter(getActivity());
        adapter.setDisplayPhotos(true);
        adapter.setUseCallableUri(super.usesCallableUri());
        return adapter;
    }

    @Override
    protected void onItemClick(int position, long id) {
        final DialerPhoneNumberListAdapter adapter = (DialerPhoneNumberListAdapter) getAdapter();
        final int shortcutType = adapter.getShortcutTypeFromPosition(position);
        final OnPhoneNumberPickerActionListener listener;

        switch (shortcutType) {
            case DialerPhoneNumberListAdapter.SHORTCUT_INVALID:
                super.onItemClick(position, id);
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CALL:
                listener = getOnPhoneNumberPickerListener();
                if (listener != null) {
                    listener.onCallNumberDirectly(getQueryString());
                }
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_ADD_NUMBER_TO_CONTACTS:
                final String number = TextUtils.isEmpty(mAddToContactNumber) ?
                        adapter.getFormattedQueryString() : mAddToContactNumber;
                final Intent intent = DialtactsActivity.getAddNumberToContactIntent(number);
                DialerUtils.startActivityWithErrorToast(getActivity(), intent,
                        R.string.add_contact_not_available);
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_MAKE_VIDEO_CALL:
                listener = getOnPhoneNumberPickerListener();
                if (listener != null) {
                    listener.onCallNumberDirectly(getQueryString(), true /* isVideoCall */);
                }
                break;
        }
    }

    /**
     * Updates the position and padding of the search fragment, depending on whether the dialpad is
     * shown. This can be optionally animated.
     * @param animate
     */
    public void updatePosition(boolean animate) {
        // Use negative shadow height instead of 0 to account for the 9-patch's shadow.
        int startTranslationValue =
                mActivity.isDialpadShown() ? mActionBarHeight - mShadowHeight: -mShadowHeight;
        int endTranslationValue = 0;
        // Prevents ListView from being translated down after a rotation when the ActionBar is up.
        if (animate || mActivity.isActionBarShowing()) {
            endTranslationValue =
                    mActivity.isDialpadShown() ? 0 : mActionBarHeight -mShadowHeight;
        }
        if (animate) {
            Interpolator interpolator =
                    mActivity.isDialpadShown() ? AnimUtils.EASE_IN : AnimUtils.EASE_OUT ;
            int duration =
                    mActivity.isDialpadShown() ? mShowDialpadDuration : mHideDialpadDuration;
            getView().setTranslationY(startTranslationValue);
            getView().animate()
                    .translationY(endTranslationValue)
                    .setInterpolator(interpolator)
                    .setDuration(duration);
        } else {
            getView().setTranslationY(endTranslationValue);
        }

        // There is padding which should only be applied when the dialpad is not shown.
        int paddingTop = mActivity.isDialpadShown() ? 0 : mPaddingTop;
        final ListView listView = getListView();
        listView.setPaddingRelative(
                listView.getPaddingStart(),
                paddingTop,
                listView.getPaddingEnd(),
                listView.getPaddingBottom());
    }
}
