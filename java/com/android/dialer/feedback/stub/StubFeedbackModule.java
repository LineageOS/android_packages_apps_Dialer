/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.dialer.feedback.stub;

import android.content.Context;
import com.android.dialer.common.LogUtil;
import com.android.dialer.feedback.FeedbackSender;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.inject.DialerVariant;
import com.android.dialer.inject.InstallIn;
import com.android.dialer.logging.LoggingBindings;
import com.android.dialer.logging.LoggingBindingsFactory;
import com.android.dialer.logging.LoggingBindingsStub;
import com.android.incallui.call.CallList;
import dagger.Module;
import dagger.Provides;

/** Module which bind {@link com.android.dialer.feedback.stub.CallFeedbackListenerStub}. */
@InstallIn(variants = {DialerVariant.DIALER_TEST})
@Module
public class StubFeedbackModule {

  @Provides
  static LoggingBindings provideLoggingBindings(LoggingBindingsFactory factory) {
    return new LoggingBindingsStub();
  }

  @Provides
  static FeedbackSender provideCallFeedbackSender() {
    LogUtil.i("StubFeedbackModule.provideCallFeedbackSender", "return stub");
    return new FeedbackSenderStub();
  }

  @Provides
  static CallList.Listener provideCallFeedbackListener(@ApplicationContext Context context) {
    LogUtil.i("StubFeedbackModule.provideCallFeedbackListener", "returning stub");
    return new CallFeedbackListenerStub(context);
  }
}
