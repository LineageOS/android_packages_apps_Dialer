/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.test.suitebuilder.annotation.Suppress;

import com.android.dialer.calllog.CallLogActivity;

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
}