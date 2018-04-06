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

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import java.io.IOException;
import javax.annotation.processing.ProcessingEnvironment;

/**
 * Contains a basic method writing java file to a curtain package. ProcessingEnvironment is needed.
 */
public abstract class RootComponentUtils {
  /**
   * The place where the generator puts metadata files storing reference for {@link
   * RootComponentGeneratingStep}.
   */
  static final String METADATA_PACKAGE_NAME = "com.android.dialer.rootcomponentgenerator.metadata";

  static final String GENERATED_COMPONENT_PREFIX = "Gen";

  static void writeJavaFile(
      ProcessingEnvironment processingEnv, String packageName, TypeSpec typeSpec) {
    try {
      JavaFile.builder(packageName, typeSpec)
          .skipJavaLangImports(true)
          .build()
          .writeTo(processingEnv.getFiler());
    } catch (IOException e) {
      System.out.println(e);
      throw new RuntimeException(e);
    }
  }
}
