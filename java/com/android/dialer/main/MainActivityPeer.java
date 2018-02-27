/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.main;

import android.content.Intent;
import android.os.Bundle;

/** Interface for peers of MainActivity. */
public interface MainActivityPeer {

  void onActivityCreate(Bundle saveInstanceState);

  void onActivityResume();

  void onUserLeaveHint();

  void onActivityPause();

  void onActivityStop();

  void onActivityDestroyed();

  void onNewIntent(Intent intent);

  void onActivityResult(int requestCode, int resultCode, Intent data);

  void onSaveInstanceState(Bundle bundle);

  boolean onBackPressed();

  /** Supplies the MainActivityPeer */
  interface PeerSupplier {

    MainActivityPeer getPeer();
  }
}
