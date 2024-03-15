/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright (C) 2024 The LineageOS Project
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

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import com.android.dialer.R;

/** Activity launched by simulator->voicemail, provides debug features. */
@SuppressWarnings("FragmentInjection") // not exported
public class VoicemailSecretCodeActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setTheme(R.style.SettingsStyle);
    getSupportFragmentManager().beginTransaction()
      .replace(android.R.id.content, new VoicemailSecretCodeFragment())
      .commit();
  }
}
