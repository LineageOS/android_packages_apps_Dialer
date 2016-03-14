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
 * limitations under the License
 */

package com.android.dialer.voicemail;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.CallLog;
import android.provider.VoicemailContract;
import android.test.InstrumentationTestCase;
import android.test.mock.MockContentResolver;

import com.android.contacts.common.test.mocks.MockContentProvider;
import com.android.contacts.common.test.mocks.MockContentProvider.Query;
import com.android.dialer.calllog.CallLogQuery;
import com.android.dialer.database.VoicemailArchiveContract;
import com.android.dialer.util.AsyncTaskExecutors;
import com.android.dialer.util.FakeAsyncTaskExecutor;
import com.android.dialer.voicemail.VoicemailAsyncTaskUtil.OnGetArchivedVoicemailFilePathListener;
import com.android.dialer.voicemail.VoicemailAsyncTaskUtil.OnArchiveVoicemailListener;
import com.android.dialer.voicemail.VoicemailAsyncTaskUtil.OnSetVoicemailArchiveStatusListener;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.mockito.Mockito.*;

import java.io.FileNotFoundException;
import java.io.IOException;

public class VoicemailAsyncTaskUtilTest extends InstrumentationTestCase {
    private static final String TEST_MIME_TYPE = "audio/mp3";
    private static final String TEST_NUMBER = "+1412555555";
    private static final String TEST_FILE_PATH = "TEST_PATH";
    private static final String TEST_TRANSCRIPTION = "TEST_TRANSCRIPTION";
    private static final long TEST_DATE = 0;
    private static final long TEST_DURATION = 0;
    private static final int TEST_SERVER_ID = 1;
    private static final int TEST_ID = 2;
    private static final Uri ARCHIVED_VOICEMAIL_URI =
            VoicemailArchiveContract.VoicemailArchive.buildWithId(TEST_ID);
    private static final Uri VOICEMAIL_URI = VoicemailContract.Voicemails.CONTENT_URI
            .buildUpon().appendPath(Integer.toString(TEST_SERVER_ID)).build();
    private static final String[] CALLLOG_QUERY_PROJECTION = new String[] {
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE,
            CallLog.Calls.COUNTRY_ISO,
            CallLog.Calls.VOICEMAIL_URI,
            CallLog.Calls.GEOCODED_LOCATION,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.CACHED_NUMBER_TYPE,
            CallLog.Calls.CACHED_NUMBER_LABEL,
            CallLog.Calls.CACHED_LOOKUP_URI,
            CallLog.Calls.CACHED_MATCHED_NUMBER,
            CallLog.Calls.CACHED_NORMALIZED_NUMBER,
            CallLog.Calls.CACHED_PHOTO_ID,
            CallLog.Calls.CACHED_FORMATTED_NUMBER,
            CallLog.Calls.IS_READ,
            CallLog.Calls.NUMBER_PRESENTATION,
            CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME,
            CallLog.Calls.PHONE_ACCOUNT_ID,
            CallLog.Calls.FEATURES,
            CallLog.Calls.DATA_USAGE,
            CallLog.Calls.TRANSCRIPTION,
            CallLog.Calls.CACHED_PHOTO_URI
    };
    private static final String[] VOICEMAIL_PROJECTION = new String[] {
            VoicemailContract.Voicemails._ID,
            VoicemailContract.Voicemails.NUMBER,
            VoicemailContract.Voicemails.DATE,
            VoicemailContract.Voicemails.DURATION,
            VoicemailContract.Voicemails.MIME_TYPE,
            VoicemailContract.Voicemails.TRANSCRIPTION,
    };

    private final MockContentResolver mContentResolver = new MockContentResolver();
    private final MockContentProvider mArchiveContentProvider = new TestVoicemailContentProvider();
    private final MockContentProvider mVoicemailContentProvider =
            new TestVoicemailContentProvider();
    private final MockContentProvider mCallLogContentProvider = new MockContentProvider();
    @Rule
    private final TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    private VoicemailAsyncTaskUtil mVoicemailAsyncTaskUtil;
    private FakeAsyncTaskExecutor mFakeAsyncTaskExecutor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mFakeAsyncTaskExecutor = new FakeAsyncTaskExecutor(getInstrumentation());
        AsyncTaskExecutors.setFactoryForTest(mFakeAsyncTaskExecutor.getFactory());
        mContentResolver.addProvider(VoicemailArchiveContract.AUTHORITY, mArchiveContentProvider);
        mContentResolver.addProvider(VoicemailContract.AUTHORITY, mVoicemailContentProvider);
        mContentResolver.addProvider(CallLog.AUTHORITY, mCallLogContentProvider);
        mVoicemailAsyncTaskUtil = new VoicemailAsyncTaskUtil(mContentResolver);
    }

    @Override
    protected void tearDown() throws Exception {
        AsyncTaskExecutors.setFactoryForTest(null);
        super.tearDown();
    }

    public void testGetVoicemailFilePath_VoicemailExists() throws Throwable {
        newVoicemailArchiveQuery().withAnyProjection()
                .returnRow(getCombinedContentValuesWithData());
        final OnGetArchivedVoicemailFilePathListener listener =
                mock(OnGetArchivedVoicemailFilePathListener.class);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mVoicemailAsyncTaskUtil.getVoicemailFilePath(listener, ARCHIVED_VOICEMAIL_URI);
            }
        });
        mFakeAsyncTaskExecutor.runTask(VoicemailAsyncTaskUtil.Tasks.GET_VOICEMAIL_FILE_PATH);
        verify(listener).onGetArchivedVoicemailFilePath(TEST_FILE_PATH);
        verifyQueries();
    }

    public void testGetVoicemailFilePath_VoicemailNotExists() throws Throwable {
        newVoicemailArchiveFakeQuery().withAnyProjection().returnEmptyCursor();
        final OnGetArchivedVoicemailFilePathListener listener =
                mock(OnGetArchivedVoicemailFilePathListener.class);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mVoicemailAsyncTaskUtil.getVoicemailFilePath(listener, getFakeVoicemailUri());
            }
        });
        mFakeAsyncTaskExecutor.runTask(VoicemailAsyncTaskUtil.Tasks.GET_VOICEMAIL_FILE_PATH);
        verify(listener).onGetArchivedVoicemailFilePath(null);
        verifyQueries();
    }

    public void testSetVoicemailArchiveStatus_VoicemailNotExists() throws Throwable {
        newVoicemailArchiveNotExistsUpdateQuery(true);
        final OnSetVoicemailArchiveStatusListener listener =
                mock(OnSetVoicemailArchiveStatusListener.class);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mVoicemailAsyncTaskUtil.setVoicemailArchiveStatus(listener,
                        getFakeVoicemailUri(), true);
            }
        });
        mFakeAsyncTaskExecutor.runTask(VoicemailAsyncTaskUtil.Tasks.SET_VOICEMAIL_ARCHIVE_STATUS);
        verify(listener).onSetVoicemailArchiveStatus(false);
        verifyQueries();
    }

    public void testSetVoicemailArchiveStatus_VoicemailExists() throws Throwable {
        newVoicemailArchiveExistsUpdateQuery(true);
        final OnSetVoicemailArchiveStatusListener listener =
                mock(OnSetVoicemailArchiveStatusListener.class);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mVoicemailAsyncTaskUtil.setVoicemailArchiveStatus(listener,
                        ARCHIVED_VOICEMAIL_URI, true);
            }
        });
        mFakeAsyncTaskExecutor.runTask(VoicemailAsyncTaskUtil.Tasks.SET_VOICEMAIL_ARCHIVE_STATUS);
        verify(listener).onSetVoicemailArchiveStatus(true);
        verifyQueries();
    }

    public void testArchiveVoicemailContent_ArchiveNotExists() throws Throwable {
        newVoicemailArchiveExistsQuery().returnEmptyCursor();
        newQueryCallLogInfo().returnRow(getCallLogContentValues());
        newVoicemailQuery().returnRow(getVoicemailContentValues());
        newVoicemailArchiveInsert();
        final OnArchiveVoicemailListener listener = mock(OnArchiveVoicemailListener.class);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mVoicemailAsyncTaskUtil.archiveVoicemailContent(listener, VOICEMAIL_URI);
            }
        });
        mFakeAsyncTaskExecutor.runTask(VoicemailAsyncTaskUtil.Tasks.ARCHIVE_VOICEMAIL_CONTENT);
        verify(listener).onArchiveVoicemail(ARCHIVED_VOICEMAIL_URI);
        verifyQueries();
    }

    public void testArchiveVoicemailContent_ArchiveExists() throws Throwable {
        newVoicemailArchiveExistsQuery().returnRow(getCombinedValuesWithId());
        final OnArchiveVoicemailListener listener = mock(OnArchiveVoicemailListener.class);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mVoicemailAsyncTaskUtil.archiveVoicemailContent(listener, VOICEMAIL_URI);
            }
        });
        mFakeAsyncTaskExecutor.runTask(VoicemailAsyncTaskUtil.Tasks.ARCHIVE_VOICEMAIL_CONTENT);
        verify(listener).onArchiveVoicemail(ARCHIVED_VOICEMAIL_URI);
        verifyQueries();
    }

    public void testArchiveVoicemailContent_CallLogInfoNotExists() throws Throwable {
        newVoicemailArchiveExistsQuery().returnEmptyCursor();
        newQueryCallLogInfo().returnEmptyCursor();
        newVoicemailQuery().returnEmptyCursor();
        final OnArchiveVoicemailListener listener = mock(OnArchiveVoicemailListener.class);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mVoicemailAsyncTaskUtil.archiveVoicemailContent(listener, VOICEMAIL_URI);
            }
        });
        mFakeAsyncTaskExecutor.runTask(VoicemailAsyncTaskUtil.Tasks.ARCHIVE_VOICEMAIL_CONTENT);
        verify(listener).onArchiveVoicemail(null);
        verifyQueries();
    }

    private Query newVoicemailArchiveQuery() {
        return mArchiveContentProvider.expectQuery(ARCHIVED_VOICEMAIL_URI);
    }

    private Query newVoicemailArchiveFakeQuery() {
        return mArchiveContentProvider.expectQuery(getFakeVoicemailUri());
    }

    private Query newQueryCallLogInfo() {
        return mCallLogContentProvider.expectQuery(ContentUris.withAppendedId(
                CallLog.Calls.CONTENT_URI_WITH_VOICEMAIL, ContentUris.parseId(VOICEMAIL_URI)))
                .withProjection(CALLLOG_QUERY_PROJECTION);
    }

    private Query newVoicemailQuery() {
        return mVoicemailContentProvider.expectQuery(VOICEMAIL_URI).withAnySelection()
                .withProjection(VOICEMAIL_PROJECTION);
    }

    private Query newVoicemailArchiveExistsQuery() {
        return mArchiveContentProvider.expectQuery(
                VoicemailArchiveContract.VoicemailArchive.CONTENT_URI)
                .withSelection(VoicemailArchiveContract.VoicemailArchive.SERVER_ID + "="
                        + ContentUris.parseId(VOICEMAIL_URI), (String[]) null)
                .withProjection(VoicemailArchiveContract.VoicemailArchive._ID);
    }

    private void newVoicemailArchiveInsert() {
        mArchiveContentProvider.expectInsert(
                VoicemailArchiveContract.VoicemailArchive.CONTENT_URI, getCombinedContentValues(),
                ARCHIVED_VOICEMAIL_URI);
    }

    private void newVoicemailArchiveNotExistsUpdateQuery(boolean status) {
        mArchiveContentProvider.expectUpdate(getFakeVoicemailUri(),
                getArchiveStatusUpdateValues(status), null, null).returnRowsAffected(0);
    }

    private void newVoicemailArchiveExistsUpdateQuery(boolean status) {
        mArchiveContentProvider.expectUpdate(ARCHIVED_VOICEMAIL_URI,
                getArchiveStatusUpdateValues(status), null, null).returnRowsAffected(1);
    }

    private static ContentValues getCallLogContentValues() {
        ContentValues values = new ContentValues();
        values.put(CALLLOG_QUERY_PROJECTION[CallLogQuery.GEOCODED_LOCATION], "");
        values.put(CALLLOG_QUERY_PROJECTION[CallLogQuery.CACHED_NAME], "");
        values.put(CALLLOG_QUERY_PROJECTION[CallLogQuery.COUNTRY_ISO], "");
        values.put(CALLLOG_QUERY_PROJECTION[CallLogQuery.CACHED_NUMBER_TYPE], 0);
        values.put(CALLLOG_QUERY_PROJECTION[CallLogQuery.CACHED_NUMBER_LABEL], "");
        values.put(CALLLOG_QUERY_PROJECTION[CallLogQuery.CACHED_LOOKUP_URI], "");
        values.put(CALLLOG_QUERY_PROJECTION[CallLogQuery.CACHED_MATCHED_NUMBER], "");
        values.put(CALLLOG_QUERY_PROJECTION[CallLogQuery.CACHED_NORMALIZED_NUMBER], "");
        values.put(CALLLOG_QUERY_PROJECTION[CallLogQuery.CACHED_FORMATTED_NUMBER], "");
        values.put(CALLLOG_QUERY_PROJECTION[CallLogQuery.NUMBER_PRESENTATION], 0);
        values.put(CALLLOG_QUERY_PROJECTION[CallLogQuery.ACCOUNT_COMPONENT_NAME], "");
        values.put(CALLLOG_QUERY_PROJECTION[CallLogQuery.ACCOUNT_ID], "");
        values.put(CALLLOG_QUERY_PROJECTION[CallLogQuery.FEATURES], 0);
        values.put(CALLLOG_QUERY_PROJECTION[23], "");
        return values;
    }

    private static ContentValues getVoicemailContentValues() {
        ContentValues values = new ContentValues();
        values.put(VoicemailContract.Voicemails.NUMBER, TEST_NUMBER);
        values.put(VoicemailContract.Voicemails.DATE, TEST_DATE);
        values.put(VoicemailContract.Voicemails.DURATION, TEST_DURATION);
        values.put(VoicemailContract.Voicemails.MIME_TYPE, TEST_MIME_TYPE);
        values.put(VoicemailContract.Voicemails._ID, TEST_SERVER_ID);
        values.put(VoicemailContract.Voicemails.TRANSCRIPTION, TEST_TRANSCRIPTION);
        values.put("_data", TEST_FILE_PATH);    // VoicemailContract.Voicemails._DATA
        return values;
    }

    private static ContentValues getCombinedContentValues() {
        ContentValues values = new ContentValues();
        // Call log info
        values.put(VoicemailArchiveContract.VoicemailArchive.GEOCODED_LOCATION, "");
        values.put(VoicemailArchiveContract.VoicemailArchive.CACHED_NAME, "");
        values.put(VoicemailArchiveContract.VoicemailArchive.CACHED_NUMBER_TYPE, 0);
        values.put(VoicemailArchiveContract.VoicemailArchive.CACHED_NUMBER_LABEL, "");
        values.put(VoicemailArchiveContract.VoicemailArchive.CACHED_LOOKUP_URI, "");
        values.put(VoicemailArchiveContract.VoicemailArchive.CACHED_MATCHED_NUMBER, "");
        values.put(VoicemailArchiveContract.VoicemailArchive.CACHED_NORMALIZED_NUMBER, "");
        values.put(VoicemailArchiveContract.VoicemailArchive.CACHED_FORMATTED_NUMBER, "");
        values.put(VoicemailArchiveContract.VoicemailArchive.NUMBER_PRESENTATION, 0);
        values.put(VoicemailArchiveContract.VoicemailArchive.ACCOUNT_COMPONENT_NAME, "");
        values.put(VoicemailArchiveContract.VoicemailArchive.ACCOUNT_ID, "");
        values.put(VoicemailArchiveContract.VoicemailArchive.FEATURES, 0);
        values.put(VoicemailArchiveContract.VoicemailArchive.CACHED_PHOTO_URI, "");
        values.put(VoicemailArchiveContract.VoicemailArchive.COUNTRY_ISO, "");

        // Voicemail content info
        values.put(VoicemailArchiveContract.VoicemailArchive.NUMBER, TEST_NUMBER);
        values.put(VoicemailArchiveContract.VoicemailArchive.DATE, TEST_DATE);
        values.put(VoicemailArchiveContract.VoicemailArchive.DURATION, TEST_DURATION);
        values.put(VoicemailArchiveContract.VoicemailArchive.MIME_TYPE, TEST_MIME_TYPE);
        values.put(VoicemailArchiveContract.VoicemailArchive.SERVER_ID, TEST_SERVER_ID);
        values.put(VoicemailArchiveContract.VoicemailArchive.TRANSCRIPTION, TEST_TRANSCRIPTION);

        // Custom fields
        values.put(VoicemailArchiveContract.VoicemailArchive.ARCHIVED, false);
        return values;
    }

    private static ContentValues getCombinedContentValuesWithData() {
        ContentValues values = getCombinedContentValues();
        values.put(VoicemailArchiveContract.VoicemailArchive._DATA, TEST_FILE_PATH);
        return values;
    }

    private static ContentValues getCombinedValuesWithId() {
        ContentValues values = getCombinedContentValuesWithData();
        values.put(VoicemailArchiveContract.VoicemailArchive._ID, TEST_ID);
        return values;
    }

    private static ContentValues getArchiveStatusUpdateValues(boolean status) {
        ContentValues values = new ContentValues();
        values.put(VoicemailArchiveContract.VoicemailArchive.ARCHIVED, status);
        return values;
    }

    private static Uri getFakeVoicemailUri() {
        return VoicemailArchiveContract.VoicemailArchive.buildWithId(0);
    }

    private void verifyQueries() {
        mArchiveContentProvider.verify();
        mCallLogContentProvider.verify();
        mVoicemailContentProvider.verify();
    }

    private class TestVoicemailContentProvider extends MockContentProvider {
        @Override
        public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
            int modeBits = ParcelFileDescriptor.parseMode(mode);
            try {
                return ParcelFileDescriptor.open(mTemporaryFolder.newFile(), modeBits);
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts) {
            try {
                return new AssetFileDescriptor(openFile(uri, "r"), 0,
                        AssetFileDescriptor.UNKNOWN_LENGTH);
            } catch (FileNotFoundException e) {
                return null;
            }
        }
    }
}

