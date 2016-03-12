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

package com.android.dialer.database;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.test.ProviderTestCase2;
import android.test.mock.MockContentResolver;
import com.android.dialer.database.VoicemailArchiveContract.VoicemailArchive;

import java.io.File;
import java.io.OutputStream;

/**
 * Tests for {@link VoicemailArchiveProvider}.
 */
public class VoicemailArchiveProviderTest extends
        ProviderTestCase2<VoicemailArchiveProviderTest.TestVoicemailArchiveProvider> {
    private static final String TEST_MIME_TYPE = "audio/mp3";
    private static final String TEST_NUMBER = "+1412555555";
    private static final int TEST_ARCHIVED = 1;
    private static final String TEST_STRING = "TEST";

    private MockContentResolver mResolver;
    private Cursor mCursor;
    private Uri mVoicemailUri;

    public VoicemailArchiveProviderTest() {
        super(TestVoicemailArchiveProvider.class, VoicemailArchiveContract.AUTHORITY);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResolver = getMockContentResolver();
    }

    @Override
    protected void tearDown() throws Exception {
        if (mCursor != null) {
            mCursor.close();
        }
        // Need to delete test.cache folder created by {@link android.test.ProviderTestCase2}
        deleteRecursive(getContext().getCacheDir());
        getProvider().closeDb();
        super.tearDown();
    }

    public void testInsert() {
        insertVoicemail();
        assertNotNull(mVoicemailUri);
        assertTrue(doesRowExist());
        long id = ContentUris.parseId(mVoicemailUri);
        assertTrue(id > 0);
    }

    public void testQuery_createFile() throws Exception {
        insertVoicemail();
        assertTrue(doesRowExist());
        assertFalse(doesFileExist());
        createFile();
        assertTrue(doesFileExist());
    }

    public void testQuery_createFileMimeTypeIsAMR() throws Exception {
        insertVoicemailWithMimeType("audio/amr");
        assertTrue(doesRowExist());
        assertFalse(doesFileExist());
        createFile();
        assertTrue(doesFileExist());
        assertEquals("amr", getFileExtension(getFilePath()));
    }

    public void testQuery_createFileMimeTypeIsMP3() throws Exception {
        insertVoicemailWithMimeType("audio/mpeg");
        assertTrue(doesRowExist());
        assertFalse(doesFileExist());
        createFile();
        assertTrue(doesFileExist());
        assertEquals("mp3", getFileExtension(getFilePath()));
    }

    public void testQuery_createFileMimeTypeNotExists() throws Exception {
        insertVoicemailWithMimeType(TEST_STRING);
        assertTrue(doesRowExist());
        assertFalse(doesFileExist());
        createFile();
        assertTrue(doesFileExist());
        assertEquals("", getFileExtension(getFilePath()));
    }

    public void testQuery() {
        insertVoicemail();
        updateCursor();
        assertCursorCount(1);
        assertContentValues();
    }

    public void testQuery_correctValuesSelection() {
        insertVoicemail();
        updateCursorWithIdQuery();
        assertCursorCount(1);
        assertContentValues();
    }

    public void testQuery_illegalUri() {
        try {
            mResolver.query(Uri.withAppendedPath(VoicemailArchive.CONTENT_URI, TEST_STRING),
                    null, null, null, null);
            fail("Expecting exception but none was thrown.");
        } catch (IllegalArgumentException e) {}
    }

    public void testUpdate() throws Exception {
        insertVoicemail();
        assertTrue(doesRowExist());
        ContentValues values = new ContentValues();
        values.put(VoicemailArchive.MIME_TYPE, TEST_STRING);
        values.put(VoicemailArchive.NUMBER, TEST_STRING);
        values.put(VoicemailArchive.ARCHIVED, false);
        mResolver.update(mVoicemailUri, values, null, null);
        updateCursor();
        assertEquals(TEST_STRING, mCursor.getString(mCursor.getColumnIndex(VoicemailArchive.MIME_TYPE)));
        assertEquals(TEST_STRING, mCursor.getString(mCursor.getColumnIndex(VoicemailArchive.NUMBER)));
        assertEquals(false, mCursor.getInt(
                mCursor.getColumnIndex(VoicemailArchive.ARCHIVED)) == 1);
    }

    public void testUpdate_selection() throws Exception {
        insertVoicemail();
        assertTrue(doesRowExist());
        ContentValues values = new ContentValues();
        values.put(VoicemailArchive.MIME_TYPE, TEST_STRING);
        values.put(VoicemailArchive.NUMBER, TEST_STRING);
        values.put(VoicemailArchive.ARCHIVED, false);
        mResolver.update(VoicemailArchive.CONTENT_URI, values, getIdQuery(), null);
        updateCursor();
        assertEquals(TEST_STRING, mCursor.getString(mCursor.getColumnIndex(VoicemailArchive.MIME_TYPE)));
        assertEquals(TEST_STRING, mCursor.getString(mCursor.getColumnIndex(VoicemailArchive.NUMBER)));
        assertEquals(false, mCursor.getInt(
                mCursor.getColumnIndex(VoicemailArchive.ARCHIVED)) == 1);
    }

    public void testUpdate_illegalUri() {
        try {
            mResolver.update(Uri.withAppendedPath(VoicemailArchive.CONTENT_URI, TEST_STRING),
                    null, null, null);
            fail("Expecting exception but none was thrown.");
        } catch (IllegalArgumentException e) {}
    }

    public void testDelete() throws Exception {
        insertVoicemail();
        createFile();
        assertTrue(doesRowExist());
        assertTrue(doesFileExist());
        mResolver.delete(mVoicemailUri, null, null);
        assertFalse(doesRowExist());
        assertFalse(doesFileExist());
    }

    public void testDelete_selection() throws Exception{
        insertVoicemail();
        createFile();
        assertTrue(doesRowExist());
        assertTrue(doesFileExist());
        mResolver.delete(VoicemailArchive.CONTENT_URI, getIdQuery(), null);
        assertFalse(doesRowExist());
        assertFalse(doesFileExist());
    }

    public void testDelete_illegalUri() {
        try {
            mResolver.delete(Uri.withAppendedPath(VoicemailArchive.CONTENT_URI, TEST_STRING),
                    null, null);
            fail("Expecting exception but none was thrown.");
        } catch (IllegalArgumentException e) {}
    }

    private boolean doesRowExist() {
        Cursor tempCursor = mResolver.query(mVoicemailUri, null, null, null, null);
        boolean rowExists = tempCursor != null && tempCursor.getCount() > 0;
        tempCursor.close();
        return rowExists;
    }

    private boolean doesFileExist() {
        File voicemailFile = new File(getFilePath());
        return voicemailFile.exists();
    }

    private static String getFileExtension(String filePath) {
        File file = new File(filePath);
        String fileName = file.getName();
        int index = fileName.lastIndexOf(".");
        return index > 0 ? fileName.substring(index + 1) : "";
    }

    private void assertCursorCount(int count) {
        assertEquals(count, mCursor.getCount());
    }

    private void assertContentValues() {
        assertEquals(TEST_MIME_TYPE, mCursor
                .getString(mCursor.getColumnIndex(VoicemailArchive.MIME_TYPE)));
        assertEquals(TEST_NUMBER, mCursor
                .getString(mCursor.getColumnIndex(VoicemailArchive.NUMBER)));
        assertEquals(TEST_ARCHIVED, mCursor
                .getInt(mCursor.getColumnIndex(VoicemailArchive.ARCHIVED)));
    }

    private void insertVoicemail() {
        mVoicemailUri = mResolver.insert(VoicemailArchive.CONTENT_URI, getTestValues());
    }

    private void insertVoicemailWithMimeType(String mimeType) {
        ContentValues values = getTestValues();
        values.put(VoicemailArchive.MIME_TYPE, mimeType);
        mVoicemailUri = mResolver.insert(VoicemailArchive.CONTENT_URI, values);
    }

    private void updateCursor() {
        mCursor = mResolver.query(mVoicemailUri, null, null, null, null);
        assertEquals(true, mCursor.getCount() > 0);
        mCursor.moveToFirst();
    }

    private void updateCursorWithIdQuery() {
        mCursor = mResolver.query(mVoicemailUri, null, getIdQuery(), null, null);
        assertEquals(true, mCursor.getCount() > 0);
        mCursor.moveToFirst();
    }

    private void createFile() throws Exception {
        assertFalse(doesFileExist());
        // Opening output stream and closing it should create the file
        OutputStream outputStream = mResolver.openOutputStream(mVoicemailUri);
        outputStream.close();
    }

    private String getIdQuery() {
        return VoicemailArchive._ID + "=" + ContentUris.parseId(mVoicemailUri);
    }

    private String getFilePath() {
        if (mCursor == null) {
            updateCursor();
        }
        return mCursor.getString(mCursor.getColumnIndex(VoicemailArchive._DATA));
    }

    private static ContentValues getTestValues() {
        ContentValues values = new ContentValues();
        values.put(VoicemailArchive.NUMBER, TEST_NUMBER);
        values.put(VoicemailArchive.MIME_TYPE, TEST_MIME_TYPE);
        values.put(VoicemailArchive.ARCHIVED, TEST_ARCHIVED);
        return values;
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }
        fileOrDirectory.delete();
    }

    public static class TestVoicemailArchiveProvider extends VoicemailArchiveProvider {
        private DialerDatabaseHelper mDialerDatabaseHelper;

        @Override
        protected File getFilesDir() {
            return getContext().getCacheDir();
        }

        @Override
        protected DialerDatabaseHelper getDatabaseHelper(Context context) {
            if (mDialerDatabaseHelper == null) {
                mDialerDatabaseHelper = DialerDatabaseHelper.getNewInstanceForTest(context);
            }
            return mDialerDatabaseHelper;
        }

        protected void closeDb() {
            mDialerDatabaseHelper.close();
        }
    }
}
