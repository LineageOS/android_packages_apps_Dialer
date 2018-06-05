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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates the place with this annotation when a RootComponent is needed.
 *
 * <p>Usually users put this annotation on application class that is root of dependencies (the last
 * thing to compile). The annotation processor will figure out what it needs to generate a variant
 * root through dependencies.
 *
 * <p>Example:
 *
 * <pre>
 * <code>
 * @DialerRootComponent(variant = DialerVariant.DIALER_AOSP)
 * public class RootDialerAosp {}
 * </code>
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface DialerRootComponent {
  DialerVariant variant();

  Class<?> injectClass() default Object.class;
}
