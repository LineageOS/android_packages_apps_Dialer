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

import static javax.tools.Diagnostic.Kind.ERROR;

import com.android.dialer.inject.IncludeInDialerRoot;
import com.android.dialer.inject.InstallIn;
import com.android.dialer.inject.RootComponentGeneratorMetadata;
import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.TypeSpec;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * Genereates metadata for every type annotated by {@link InstallIn} and {@link
 * IncludeInDialerRoot}.
 *
 * <p>The metadata has the information where the annotated types are and it is used by annotation
 * processor when the processor tries to generate root component.
 */
final class MetadataGeneratingStep implements ProcessingStep {

  private final ProcessingEnvironment processingEnv;

  MetadataGeneratingStep(ProcessingEnvironment processingEnv) {
    this.processingEnv = processingEnv;
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(IncludeInDialerRoot.class, InstallIn.class);
  }

  @Override
  public Set<? extends Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {

    for (Element element : elementsByAnnotation.get(IncludeInDialerRoot.class)) {
      generateMetadataFor(IncludeInDialerRoot.class, MoreElements.asType(element));
    }
    for (Element element : elementsByAnnotation.get(InstallIn.class)) {
      if (element.getAnnotation(InstallIn.class).variants().length == 0) {
        processingEnv
            .getMessager()
            .printMessage(
                ERROR, String.format("@InstallIn %s must have at least one variant", element));
        continue;
      }
      generateMetadataFor(InstallIn.class, MoreElements.asType(element));
    }

    return Collections.emptySet();
  }

  private void generateMetadataFor(
      Class<? extends Annotation> annotation, TypeElement annotatedElement) {
    TypeSpec.Builder metadataClassBuilder =
        TypeSpec.classBuilder(
            annotatedElement.getQualifiedName().toString().replace('.', '_') + "Metadata");
    metadataClassBuilder.addAnnotation(
        AnnotationSpec.builder(RootComponentGeneratorMetadata.class)
            .addMember("tag", "$S", annotation.getSimpleName())
            .addMember("annotatedClass", "$T.class", annotatedElement.asType())
            .build());
    TypeSpec metadataClass = metadataClassBuilder.build();
    RootComponentUtils.writeJavaFile(
        processingEnv, RootComponentUtils.METADATA_PACKAGE_NAME, metadataClass);
  }
}
