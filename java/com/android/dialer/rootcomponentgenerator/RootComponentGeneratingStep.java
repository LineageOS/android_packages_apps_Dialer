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

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.android.dialer.inject.DialerRootComponent;
import com.android.dialer.inject.DialerVariant;
import com.android.dialer.inject.IncludeInDialerRoot;
import com.android.dialer.inject.InstallIn;
import com.android.dialer.inject.RootComponentGeneratorMetadata;
import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.auto.common.MoreElements;
import com.google.common.base.Optional;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.SetMultimap;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.Component;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Singleton;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * Generates root component for a java type annotated with {@link DialerRootComponent}.
 *
 * <p>If users use {@link GenerateTestDaggerApp} along with RootComponentGenerator, there's an
 * optional method that they can use to inject instance in the test.
 *
 * <p>Example:
 *
 * <p>
 *
 * <pre>
 * <code>
 * @Inject SomeThing someThing;
 * @Before
 * public void setUp() {
 * ...
 * TestApplication application = (TestApplication) RuntimeEnvironment.application;
 * TestComponent component = (TestComponent) application.component();
 * component.inject(this);
 * ...
 * }
 * </code>
 * </pre>
 */
final class RootComponentGeneratingStep implements ProcessingStep {

  private final ProcessingEnvironment processingEnv;

  public RootComponentGeneratingStep(ProcessingEnvironment processingEnv) {
    this.processingEnv = processingEnv;
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(DialerRootComponent.class, InstallIn.class, IncludeInDialerRoot.class);
  }

  @Override
  public Set<? extends Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    for (Element element : elementsByAnnotation.get(DialerRootComponent.class)) {
      // defer root components to the next round in case where the current build target contains
      // elements annotated with @InstallIn. Annotation processor cannot detect metadata files
      // generated in the same round and the metadata is accessible in the next round.
      if (shouldDeferRootComponent(elementsByAnnotation)) {
        return elementsByAnnotation.get(DialerRootComponent.class);
      } else {
        generateRootComponent(MoreElements.asType(element));
      }
    }
    return Collections.emptySet();
  }

  private boolean shouldDeferRootComponent(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    return elementsByAnnotation.containsKey(InstallIn.class)
        || elementsByAnnotation.containsKey(IncludeInDialerRoot.class);
  }

  /**
   * Generates a root component.
   *
   * @param rootElement the annotated type with {@link DialerRootComponent} used in annotation
   *     processor.
   */
  private void generateRootComponent(TypeElement rootElement) {
    DialerRootComponent dialerRootComponent = rootElement.getAnnotation(DialerRootComponent.class);
    ListMultimap<DialerVariant, TypeElement> componentModuleMap = generateComponentModuleMap();
    List<TypeElement> componentList = generateComponentList();
    DialerVariant dialerVariant = dialerRootComponent.variant();
    TypeSpec.Builder rootComponentClassBuilder =
        TypeSpec.interfaceBuilder(dialerVariant.toString())
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Singleton.class);
    for (TypeElement componentWithSuperInterface : componentList) {
      rootComponentClassBuilder.addSuperinterface(ClassName.get(componentWithSuperInterface));
    }
    AnnotationSpec.Builder componentAnnotation = AnnotationSpec.builder(Component.class);
    for (TypeElement annotatedElement : componentModuleMap.get(dialerVariant)) {
      componentAnnotation.addMember("modules", "$T.class", annotatedElement.asType());
    }
    rootComponentClassBuilder.addAnnotation(componentAnnotation.build());

    AnnotationMirror dialerRootComponentMirror =
        getAnnotationMirror(rootElement, DialerRootComponent.class).get();

    TypeMirror annotatedTestClass =
        (TypeMirror) getAnnotationValue(dialerRootComponentMirror, "injectClass").getValue();

    rootComponentClassBuilder.addMethod(generateInjectMethod(annotatedTestClass));

    TypeSpec rootComponentClass = rootComponentClassBuilder.build();
    RootComponentUtils.writeJavaFile(
        processingEnv, ClassName.get(rootElement).packageName(), rootComponentClass);
  }

  private List<TypeElement> generateComponentList() {
    List<TypeElement> list = new ArrayList<>();
    extractInfoFromMetadata(IncludeInDialerRoot.class, list::add);
    return list;
  }

  private ListMultimap<DialerVariant, TypeElement> generateComponentModuleMap() {
    ListMultimap<DialerVariant, TypeElement> map = ArrayListMultimap.create();
    extractInfoFromMetadata(
        InstallIn.class,
        (annotatedElement) -> {
          for (DialerVariant rootComponentName :
              annotatedElement.getAnnotation(InstallIn.class).variants()) {
            map.put(rootComponentName, annotatedElement);
          }
        });
    return map;
  }

  private void extractInfoFromMetadata(
      Class<? extends Annotation> annotation, MetadataProcessor metadataProcessor) {
    PackageElement cachePackage =
        processingEnv.getElementUtils().getPackageElement(RootComponentUtils.METADATA_PACKAGE_NAME);
    if (cachePackage == null) {
      processingEnv
          .getMessager()
          .printMessage(
              ERROR,
              "Metadata haven't been generated! do you forget to add modules "
                  + "or components in dependency of dialer root?");
      return;
    }
    for (Element element : cachePackage.getEnclosedElements()) {
      Optional<AnnotationMirror> metadataAnnotation =
          getAnnotationMirror(element, RootComponentGeneratorMetadata.class);
      if (isAnnotationPresent(element, RootComponentGeneratorMetadata.class)
          && getAnnotationValue(metadataAnnotation.get(), "tag")
              .getValue()
              .equals(annotation.getSimpleName())) {
        TypeMirror annotatedClass =
            (TypeMirror) getAnnotationValue(metadataAnnotation.get(), "annotatedClass").getValue();
        TypeElement annotatedElement =
            processingEnv.getElementUtils().getTypeElement(annotatedClass.toString());
        metadataProcessor.process(annotatedElement);
      }
    }
  }

  private MethodSpec generateInjectMethod(TypeMirror testClassTypeMirror) {
    return MethodSpec.methodBuilder("inject")
        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
        .returns(void.class)
        .addParameter(ClassName.get(testClassTypeMirror), "clazz")
        .build();
  }

  private interface MetadataProcessor {
    void process(TypeElement typeElement);
  }
}
