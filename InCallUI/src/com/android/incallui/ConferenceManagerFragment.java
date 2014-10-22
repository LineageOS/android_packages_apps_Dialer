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
 * limitations under the License
 */

package com.android.incallui;

import android.app.ActionBar;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;

/**
 * Fragment for call control buttons
 */
public class ConferenceManagerFragment
        extends BaseFragment<ConferenceManagerPresenter,
                ConferenceManagerPresenter.ConferenceManagerUi>
        implements ConferenceManagerPresenter.ConferenceManagerUi {

    private ViewGroup[] mConferenceCallList;
    private int mActionBarElevation;
    private ContactPhotoManager mContactPhotoManager;

    @Override
    ConferenceManagerPresenter createPresenter() {
        // having a singleton instance.
        return new ConferenceManagerPresenter();
    }

    @Override
    ConferenceManagerPresenter.ConferenceManagerUi getUi() {
        return this;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View parent = inflater.inflate(R.layout.conference_manager_fragment, container,
                false);

        // Create list of conference call widgets
        mConferenceCallList = new ViewGroup[getPresenter().getMaxCallersInConference()];
        final int[] viewGroupIdList = { R.id.caller0, R.id.caller1, R.id.caller2,
                                        R.id.caller3, R.id.caller4 };
        for (int i = 0; i < getPresenter().getMaxCallersInConference(); i++) {
            mConferenceCallList[i] = (ViewGroup) parent.findViewById(viewGroupIdList[i]);
        }

        mContactPhotoManager =
                ContactPhotoManager.getInstance(getActivity().getApplicationContext());

        mActionBarElevation =
                (int) getResources().getDimension(R.dimen.incall_action_bar_elevation);

        return parent;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void setVisible(boolean on) {
        ActionBar actionBar = getActivity().getActionBar();

        if (on) {
            actionBar.setTitle(R.string.manageConferenceLabel);
            actionBar.setElevation(mActionBarElevation);
            actionBar.setHideOffset(0);
            actionBar.show();

            final CallList calls = CallList.getInstance();
            getPresenter().init(getActivity(), calls);
            getView().setVisibility(View.VISIBLE);
        } else {
            getView().setVisibility(View.GONE);

            actionBar.setElevation(0);
            actionBar.setHideOffset(actionBar.getHeight());
        }
    }

    @Override
    public boolean isFragmentVisible() {
        return isVisible();
    }

    @Override
    public void setRowVisible(int rowId, boolean on) {
        if (on) {
            mConferenceCallList[rowId].setVisibility(View.VISIBLE);
        } else {
            mConferenceCallList[rowId].setVisibility(View.GONE);
        }
    }

    /**
     * Helper function to fill out the Conference Call(er) information
     * for each item in the "Manage Conference Call" list.
     */
    @Override
    public final void displayCallerInfoForConferenceRow(int rowId, String callerName,
            String callerNumber, String callerNumberType, String lookupKey, Uri photoUri) {

        final ImageView photoView = (ImageView) mConferenceCallList[rowId].findViewById(
                R.id.callerPhoto);
        final TextView nameTextView = (TextView) mConferenceCallList[rowId].findViewById(
                R.id.conferenceCallerName);
        final TextView numberTextView = (TextView) mConferenceCallList[rowId].findViewById(
                R.id.conferenceCallerNumber);
        final TextView numberTypeTextView = (TextView) mConferenceCallList[rowId].findViewById(
                R.id.conferenceCallerNumberType);

        DefaultImageRequest imageRequest = (photoUri != null) ? null :
                new DefaultImageRequest(callerName, lookupKey, true /* isCircularPhoto */);
        mContactPhotoManager.loadDirectoryPhoto(photoView, photoUri, false, true, imageRequest);

        // set the caller name
        nameTextView.setText(callerName);

        // set the caller number in subscript, or make the field disappear.
        if (TextUtils.isEmpty(callerNumber)) {
            numberTextView.setVisibility(View.GONE);
            numberTypeTextView.setVisibility(View.GONE);
        } else {
            numberTextView.setVisibility(View.VISIBLE);
            numberTextView.setText(callerNumber);
            numberTypeTextView.setVisibility(View.VISIBLE);
            numberTypeTextView.setText(callerNumberType);
        }
    }

    @Override
    public final void setupEndButtonForRow(final int rowId, boolean canDisconnect) {
        View endButton = mConferenceCallList[rowId].findViewById(R.id.conferenceCallerDisconnect);

        // Comment
        if (canDisconnect) {
            endButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    getPresenter().endConferenceConnection(rowId);
                }
            });
            endButton.setVisibility(View.VISIBLE);
        } else {
            endButton.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public final void setupSeparateButtonForRow(final int rowId, boolean canSeparate) {
        final View separateButton =
                mConferenceCallList[rowId].findViewById(R.id.conferenceCallerSeparate);

        if (canSeparate) {
            separateButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    getPresenter().separateConferenceConnection(rowId);
                }
            });
            separateButton.setVisibility(View.VISIBLE);
        } else {
            separateButton.setVisibility(View.INVISIBLE);
        }
    }
}