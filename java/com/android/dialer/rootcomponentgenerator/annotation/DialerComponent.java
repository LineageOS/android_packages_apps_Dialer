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

package com.android.dialer.rootcomponentgenerator.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Annotates a type equivalent to {@link dagger.Subcomponent}.
 *
 * <p>The annotation processor will generate a new type file with some prefix, which contains public
 * static XXX get(Context context) method and HasComponent interface like:
 *
 * <p>
 *
 * <pre>
 * <code>
 *  public static SimulatorComponent get(Context context) {
 *      HasRootComponent hasRootComponent = (HasRootComponent) context.getApplicationContext();
 *      return ((HasComponent)(hasRootComponent.component()).simulatorComponent();
 *  }
 *  public interface HasComponent {
 *      SimulatorComponent simulatorComponent();
 *  }
 * </code>
 * </pre>
 */
@Target(ElementType.TYPE)
public @interface DialerComponent {
  Class<?>[] modules() default {};
}
