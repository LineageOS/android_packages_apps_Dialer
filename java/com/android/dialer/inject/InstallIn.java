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

package com.android.dialer.inject;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Annotation for {@link dagger.Module dagger.Modules} which causes them to be installed in the
 * specified variants.
 *
 * <p>It has a parameter for users to enter on which variants annotated module will be installed and
 * also must be non-empty. Example:
 *
 * <pre>
 * <code>
 * @InstallIn(variants = {DialerVariant.DIALER_AOSP, DialerVariant.DIALER_TEST})
 * public class Module1 {}
 *
 * </code>
 * </pre>
 */
@Target(ElementType.TYPE)
public @interface InstallIn {
  DialerVariant[] variants();
}
