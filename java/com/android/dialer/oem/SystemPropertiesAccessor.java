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
 * limitations under the License.
 */

package com.android.dialer.oem;

import android.support.annotation.Nullable;
import com.android.dialer.common.LogUtil;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Hacky way to call the hidden SystemProperties class API. Needed to get the real value of
 * ro.carrier and some other values.
 */
class SystemPropertiesAccessor {
  private Method systemPropertiesGetMethod;

  @SuppressWarnings("PrivateApi")
  public String get(String name) {
    Method systemPropertiesGetMethod = getSystemPropertiesGetMethod();
    if (systemPropertiesGetMethod == null) {
      return null;
    }

    try {
      return (String) systemPropertiesGetMethod.invoke(null, name);
    } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
      LogUtil.e("SystemPropertiesAccessor.get", "unable to invoke system method", e);
      return null;
    }
  }

  @SuppressWarnings("PrivateApi")
  private @Nullable Method getSystemPropertiesGetMethod() {
    if (systemPropertiesGetMethod != null) {
      return systemPropertiesGetMethod;
    }

    try {
      Class<?> systemPropertiesClass = Class.forName("android.os.SystemProperties");
      if (systemPropertiesClass == null) {
        return null;
      }
      systemPropertiesGetMethod = systemPropertiesClass.getMethod("get", String.class);
      return systemPropertiesGetMethod;
    } catch (ClassNotFoundException | NoSuchMethodException e) {
      LogUtil.e("SystemPropertiesAccessor.get", "unable to access system class", e);
      return null;
    }
  }
}
