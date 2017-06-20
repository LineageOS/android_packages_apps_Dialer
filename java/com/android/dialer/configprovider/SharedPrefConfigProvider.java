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
package com.android.dialer.configprovider;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.StrictMode;
import android.support.annotation.Nullable;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.util.DialerUtils;
import javax.inject.Inject;

/**
 * {@link ConfigProvider} which uses a shared preferences file.
 *
 * <p>Config flags can be written using adb (with root access), for example:
 *
 * <pre>
 *   adb root
 *   adb shell am startservice -n \
 *     'com.android.dialer/.configprovider.SharedPrefConfigProvider\$Service' \
 *     --ez boolean_flag_name flag_value
 * </pre>
 *
 * <p>(For longs use --el and for strings use --es.)
 *
 * <p>Flags can be viewed with:
 *
 * <pre>
 *   adb shell cat \
 *     /data/user_de/0/com.android.dialer/shared_prefs/com.android.dialer_preferences.xml
 * </pre>
 */
class SharedPrefConfigProvider implements ConfigProvider {
  private static final String PREF_PREFIX = "config_provider_prefs_";

  private final Context appContext;

  @Inject
  SharedPrefConfigProvider(@ApplicationContext Context appContext) {
    this.appContext = appContext;
  }

  /** Service to write values into {@link SharedPrefConfigProvider} using adb. */
  public static class Service extends IntentService {

    public Service() {
      super("SharedPrefConfigProvider.Service");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
      if (intent == null || intent.getExtras() == null || intent.getExtras().size() != 1) {
        LogUtil.w("SharedPrefConfigProvider.Service.onHandleIntent", "must set exactly one extra");
        return;
      }
      String key = intent.getExtras().keySet().iterator().next();
      Object value = intent.getExtras().get(key);
      put(key, value);
    }

    private void put(String key, Object value) {
      Editor editor = getSharedPrefs(getApplicationContext()).edit();
      String prefixedKey = PREF_PREFIX + key;
      if (value instanceof Boolean) {
        editor.putBoolean(prefixedKey, (Boolean) value);
      } else if (value instanceof Long) {
        editor.putLong(prefixedKey, (Long) value);
      } else if (value instanceof String) {
        editor.putString(prefixedKey, (String) value);
      } else {
        throw Assert.createAssertionFailException("unsupported extra type: " + value.getClass());
      }
      editor.apply();
    }
  }

  @Override
  public String getString(String key, String defaultValue) {
    return bypassStrictMode(
        () -> getSharedPrefs(appContext).getString(PREF_PREFIX + key, defaultValue));
  }

  @Override
  public long getLong(String key, long defaultValue) {
    return bypassStrictMode(
        () -> getSharedPrefs(appContext).getLong(PREF_PREFIX + key, defaultValue));
  }

  @Override
  public boolean getBoolean(String key, boolean defaultValue) {
    return bypassStrictMode(
        () -> getSharedPrefs(appContext).getBoolean(PREF_PREFIX + key, defaultValue));
  }

  private static SharedPreferences getSharedPrefs(Context appContext) {
    return DialerUtils.getDefaultSharedPreferenceForDeviceProtectedStorageContext(appContext);
  }

  private interface Provider<T> {
    T get();
  }

  // Reading shared prefs on the main thread is generally safe since a single instance is cached.
  private static <T> T bypassStrictMode(Provider<T> provider) {
    StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
    try {
      return provider.get();
    } finally {
      StrictMode.setThreadPolicy(oldPolicy);
    }
  }
}
