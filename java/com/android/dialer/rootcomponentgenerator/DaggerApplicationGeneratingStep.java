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

import com.android.dialer.inject.DialerRootComponent;
import com.android.dialer.inject.DialerVariant;
import com.android.dialer.inject.GenerateDaggerApp;
import com.android.dialer.inject.HasRootComponent;
import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

/**
 * Generates an application class with dagger component instance for a type annotated with {@link
 * GenerateDaggerApp}.
 *
 * <p>Generated code example:
 *
 * <p><code>
 *  @DialerRootComponent(variant = DialerVariant.DIALER_TEST)
 * class GeneratedApplication extends Application implements HasRootComponent {
 *  private volatile Object rootComponent;
 *
 *  @Override
 *  @NonNull
 *  public final Object component() {
 *   Object result = rootComponent;
 *     if (result == null) {
 *       synchronized (this) {
 *         result = rootComponent;
 *         if (result == null) {
 *           rootComponent =
 *              result = DaggerDialerTest.builder().contextModule(new ContextModule(this)).build();
 *         }
 *       }
 *     }
 *   return result;
 *  }
 * }
 * </code>
 */
public class DaggerApplicationGeneratingStep implements ProcessingStep {

  private static final ClassName ANDROID_APPLICATION_CLASS_NAME =
      ClassName.get("android.app", "Application");

  private final ProcessingEnvironment processingEnv;

  public DaggerApplicationGeneratingStep(ProcessingEnvironment processingEnv) {
    this.processingEnv = processingEnv;
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(GenerateDaggerApp.class);
  }

  @Override
  public Set<? extends Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    for (Element element : elementsByAnnotation.get(GenerateDaggerApp.class)) {
      GenerateDaggerApp generateDaggerApp = element.getAnnotation(GenerateDaggerApp.class);
      RootComponentUtils.writeJavaFile(
          processingEnv,
          ClassName.get(MoreElements.asType(element)).packageName(),
          generateDaggerApplication(generateDaggerApp.name(), generateDaggerApp.variant()));
    }

    return Collections.emptySet();
  }

  private TypeSpec generateDaggerApplication(String name, DialerVariant variant) {
    return TypeSpec.classBuilder(name)
        .addAnnotation(
            AnnotationSpec.builder(DialerRootComponent.class)
                .addMember("variant", "$T.$L", DialerVariant.class, variant.name())
                .build())
        .superclass(ANDROID_APPLICATION_CLASS_NAME)
        .addSuperinterface(HasRootComponent.class)
        .addField(
            FieldSpec.builder(TypeName.OBJECT, "rootComponent", Modifier.PRIVATE, Modifier.VOLATILE)
                .build())
        .addMethod(generateComponentMethod(variant))
        .build();
  }

  private MethodSpec generateComponentMethod(DialerVariant dialerVariant) {
    return MethodSpec.overriding(getComponentMethodFromHasRootComponent())
        .addModifiers(Modifier.FINAL)
        .addAnnotation(ClassName.get("android.support.annotation", "NonNull"))
        .addStatement("$T result = rootComponent", TypeName.OBJECT)
        .beginControlFlow("if (result == null)")
        .beginControlFlow("synchronized (this)")
        .addStatement("result = rootComponent")
        .beginControlFlow("if (result == null)")
        .addStatement(
            "rootComponent = result = Dagger$L.builder().contextModule(new $T(this)).build()",
            dialerVariant,
            ClassName.get("com.android.dialer.inject", "ContextModule"))
        .endControlFlow()
        .endControlFlow()
        .endControlFlow()
        .addStatement("return result")
        .build();
  }

  private ExecutableElement getComponentMethodFromHasRootComponent() {
    TypeElement hasRootComponentInterafce =
        processingEnv.getElementUtils().getTypeElement(HasRootComponent.class.getTypeName());
    for (Element element : hasRootComponentInterafce.getEnclosedElements()) {
      if (element.getSimpleName().contentEquals("component")) {
        return MoreElements.asExecutable(element);
      }
    }
    throw new RuntimeException("No component method inside HasRootComponent!");
  }
}
