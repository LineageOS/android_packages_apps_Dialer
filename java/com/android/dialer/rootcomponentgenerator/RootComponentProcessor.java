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

package com.android.dialer.rootcomponentgenerator;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;

/** Processes annotations defined by RootComponentGenerator. */
@AutoService(Processor.class)
public class RootComponentProcessor extends BasicAnnotationProcessor {

  @Override
  protected Iterable<? extends ProcessingStep> initSteps() {
    return ImmutableList.of(
        new MetadataGeneratingStep(processingEnv), new RootComponentGeneratingStep(processingEnv));
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }
}
