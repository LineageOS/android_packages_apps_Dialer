/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.voicemail;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.res.AssetManager;

import com.android.dialer.database.VoicemailArchiveContract.VoicemailArchive;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Unit tests for {@link VoicemailArchiveActivity} and {@link VoicemailArchivePlaybackPresenter}.
 */
public class VoicemailArchiveTest
        extends VoicemailActivityInstrumentationTestCase2<VoicemailArchiveActivity> {

    public VoicemailArchiveTest() {
        super(VoicemailArchiveActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mPresenter = VoicemailArchivePlaybackPresenter.getInstance(getActivity(), null);
    }

    @Override
    public void testFetchingVoicemail() throws Throwable {
        setUriForRealFileVoicemailEntry();
        setPlaybackViewForPresenter();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mPresenter.checkForContent(
                        new VoicemailPlaybackPresenter.OnContentCheckedListener() {
                            @Override
                            public void onContentChecked(boolean hasContent) {
                                mPresenter.resumePlayback();
                                assertEquals(true, mPresenter.isPlaying());
                            }
                        });
            }
        });
    }

    @Override
    public void testInvalidVoicemailShowsErrorMessage() throws Throwable {
        setUriForInvalidVoicemailEntry();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mPresenter.checkForContent(
                        new VoicemailPlaybackPresenter.OnContentCheckedListener() {
                            @Override
                            public void onContentChecked(boolean hasContent) {
                                assertStateTextContains("Couldn't play voicemail");
                            }
                        });
            }
        });
    }

    @Override
    protected void setUriForInvalidVoicemailEntry() {
        assertNull(mVoicemailUri);
        ContentValues values = new ContentValues();
        values.put(VoicemailArchive.NUMBER, CONTACT_NUMBER);
        values.put(VoicemailArchive.DATE, String.valueOf(System.currentTimeMillis()));
        values.put(VoicemailArchive.MIME_TYPE, MIME_TYPE);
        values.put(VoicemailArchive._DATA, VOICEMAIL_FILE_LOCATION);
        mVoicemailUri = getContentResolver().insert(VoicemailArchive.CONTENT_URI, values);
    }

    @Override
    protected void setUriForRealFileVoicemailEntry() throws IOException {
        assertNull(mVoicemailUri);
        ContentValues values = new ContentValues();
        values.put(VoicemailArchive.DATE, String.valueOf(System.currentTimeMillis()));
        values.put(VoicemailArchive.NUMBER, CONTACT_NUMBER);
        values.put(VoicemailArchive.MIME_TYPE, MIME_TYPE);
        values.put(VoicemailArchive.DURATION, 0);
        mVoicemailUri = getContentResolver().insert(VoicemailArchive.CONTENT_URI, values);
        AssetManager assets = getAssets();
        try (InputStream inputStream = assets.open(TEST_ASSET_NAME);
             OutputStream outputStream = getContentResolver().openOutputStream(mVoicemailUri)) {
            copyBetweenStreams(inputStream, outputStream);
        }
    }

    @Override
    protected void cleanUpVoicemailUri() {
        if (mVoicemailUri != null) {
            getContentResolver().delete(VoicemailArchive.CONTENT_URI,
                    "_ID = ?", new String[] { String.valueOf(ContentUris.parseId(mVoicemailUri)) });
            mVoicemailUri = null;
        }
    }
}
