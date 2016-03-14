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
import android.database.Cursor;
import android.net.Uri;
import android.test.suitebuilder.annotation.Suppress;

import com.android.dialer.calllog.CallLogActivity;
import com.android.dialer.database.VoicemailArchiveContract;
import static com.android.dialer.voicemail.VoicemailAsyncTaskUtil.Tasks.ARCHIVE_VOICEMAIL_CONTENT;
import static com.android.dialer.voicemail.VoicemailPlaybackPresenter.Tasks.CHECK_FOR_CONTENT;

/**
 * Unit tests for {@link VoicemailPlaybackPresenter} and {@link VoicemailPlaybackLayout}.
 */
public class VoicemailPlaybackTest
        extends VoicemailActivityInstrumentationTestCase2<CallLogActivity> {

    public VoicemailPlaybackTest() {
        super(CallLogActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mPresenter = VoicemailPlaybackPresenter.getInstance(getActivity(), null);
    }

    @Override
    public void tearDown() throws Exception {
        cleanUpArchivedVoicemailUri();
        super.tearDown();
    }

    @Suppress
    public void testWhenCheckForContentCompletes() throws Throwable {
        setUriForRealFileVoicemailEntry();
        setPlaybackViewForPresenter();

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mPresenter.resumePlayback();
            }
        });
        mFakeAsyncTaskExecutor.runTask(CHECK_FOR_CONTENT);
        getInstrumentation().waitForIdleSync();

        assertStateTextContains("Loading voicemail");
    }

    public void testArchiveContent() throws Throwable {
        setUriForRealFileVoicemailEntry();
        setPlaybackViewForPresenter();
        mFakeAsyncTaskExecutor.runTask(CHECK_FOR_CONTENT);

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mPresenter.archiveContent(mVoicemailUri, true);
            }
        });
        mFakeAsyncTaskExecutor.runTask(CHECK_FOR_CONTENT);
        mFakeAsyncTaskExecutor.runTask(ARCHIVE_VOICEMAIL_CONTENT);
        getInstrumentation().waitForIdleSync();
        assertVoicemailArchived();
    }

    public void testShareContent() throws Throwable {
        setUriForRealFileVoicemailEntry();
        setPlaybackViewForPresenter();
        mFakeAsyncTaskExecutor.runTask(CHECK_FOR_CONTENT);

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mPresenter.archiveContent(mVoicemailUri, false);
            }
        });
        mFakeAsyncTaskExecutor.runTask(CHECK_FOR_CONTENT);
        mFakeAsyncTaskExecutor.runTask(ARCHIVE_VOICEMAIL_CONTENT);
        getInstrumentation().waitForIdleSync();
        assertVoicemailArchived();
    }

    private void assertVoicemailArchived() {
        try (Cursor cursor = getArchivedVoicemailCursor()) {
            assertTrue(hasContent(cursor));
            assertEquals(ContentUris.parseId(mVoicemailUri), getRowServerId(cursor));
        } catch (Exception e) {
            fail("Voicemail was not archived: " + e.toString());
        }
    }

    private void cleanUpArchivedVoicemailUri() {
        try (Cursor cursor = getArchivedVoicemailCursor()) {
            if (hasContent(cursor)) {
                getContentResolver().delete(getRowUri(cursor), null, null);
            }
        }
    }

    private Cursor getArchivedVoicemailCursor() {
        return getContentResolver().query(
                VoicemailArchiveContract.VoicemailArchive.CONTENT_URI,
                new String[] {
                        VoicemailArchiveContract.VoicemailArchive._ID,
                        VoicemailArchiveContract.VoicemailArchive.SERVER_ID
                },
                VoicemailArchiveContract.VoicemailArchive.SERVER_ID + "="
                        + ContentUris.parseId(mVoicemailUri),
                null,
                null);
    }

    private int getRowServerId(Cursor cursor) {
        return cursor
                .getInt(cursor.getColumnIndex(VoicemailArchiveContract.VoicemailArchive.SERVER_ID));
    }

    private Uri getRowUri(Cursor cursor) {
        return VoicemailArchiveContract.VoicemailArchive.buildWithId(cursor.getInt(
                cursor.getColumnIndex(VoicemailArchiveContract.VoicemailArchive._ID)));
    }

    private boolean hasContent(Cursor cursor) {
        return cursor != null && cursor.moveToFirst();
    }
}