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

package com.android.voicemail.impl.configui;

import android.content.Intent;
import android.preference.PreferenceActivity;
import android.provider.VoicemailContract;
import java.util.List;

/** Activity launched by simulator->voicemail, provides debug features. */
@SuppressWarnings("FragmentInjection") // not exported
public class VoicemailSecretCodeActivity extends PreferenceActivity {

  private Header syncHeader;

  @Override
  public void onBuildHeaders(List<Header> target) {
    super.onBuildHeaders(target);
    syncHeader = new Header();
    syncHeader.title = "Sync";
    target.add(syncHeader);

    Header configOverride = new Header();
    configOverride.fragment = ConfigOverrideFragment.class.getName();
    configOverride.title = "VVM config override";
    target.add(configOverride);
  }

  @Override
  public void onHeaderClick(Header header, int position) {
    if (header == syncHeader) {
      Intent intent = new Intent(VoicemailContract.ACTION_SYNC_VOICEMAIL);
      intent.setPackage(getPackageName());
      sendBroadcast(intent);
      return;
    }
    super.onHeaderClick(header, position);
  }

  @Override
  protected boolean isValidFragment(String fragmentName) {
    return true;
  }
}
