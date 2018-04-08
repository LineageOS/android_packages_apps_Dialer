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
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.util.ElementFilter.typesIn;

import com.android.dialer.inject.IncludeInDialerRoot;
import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.auto.common.MoreElements;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.Subcomponent;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 * Generates component for a type annotated with {@link IncludeInDialerRoot}.
 *
 * <p>Our components have boilerplate code like:
 *
 * <p>
 *
 * <pre>
 * <code>
 *
 * {@literal @}dagger.Subcomponent
 * public abstract class GenXXXXComponent {
 *   public static SimulatorComponent get(Context context) {
 *      return ((HasComponent)((HasRootComponent) context.getApplicationContext()).component())
 *         .simulatorComponent();
 *   }
 *   {@literal @}IncludeInDialerRoot
 *   public interface HasComponent {
 *      SimulatorComponent simulatorComponent();
 *  }
 * }
 * </code>
 * </pre>
 */
final class ComponentGeneratingStep implements ProcessingStep {

  private static final String DIALER_INJECT_PACKAGE = "com.android.dialer.inject";
  private static final String DIALER_HASROOTCOMPONENT_INTERFACE = "HasRootComponent";
  private static final ClassName ANDROID_CONTEXT_CLASS_NAME =
      ClassName.get("android.content", "Context");
  private final ProcessingEnvironment processingEnv;

  public ComponentGeneratingStep(ProcessingEnvironment processingEnv) {
    this.processingEnv = processingEnv;
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(IncludeInDialerRoot.class);
  }

  @Override
  public Set<? extends Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    for (TypeElement type : typesIn(elementsByAnnotation.get(IncludeInDialerRoot.class))) {
      generateComponent(type);
    }
    return Collections.emptySet();
  }

  /**
   * Generates component file for a componentElement.
   *
   * <p>The annotation processor will generate a new type file with some prefix, which contains
   * public static XXX get(Context context) method and HasComponent interface.
   *
   * @param dialerComponentElement a component used by the annotation processor.
   */
  private void generateComponent(TypeElement dialerComponentElement) {
    TypeSpec.Builder componentClass =
        dialerComponentElement.getKind().isClass()
            ? cloneClass(dialerComponentElement, RootComponentUtils.GENERATED_COMPONENT_PREFIX)
            : cloneInterface(dialerComponentElement, RootComponentUtils.GENERATED_COMPONENT_PREFIX);
    componentClass.addAnnotation(makeDaggerSubcomponentAnnotation(dialerComponentElement));
    RootComponentUtils.writeJavaFile(
        processingEnv,
        ClassName.get(dialerComponentElement).packageName(),
        dialerBoilerplateCode(componentClass, dialerComponentElement));
  }

  @SuppressWarnings("unchecked")
  private AnnotationSpec makeDaggerSubcomponentAnnotation(TypeElement dialerComponentElement) {

    Optional<AnnotationMirror> componentMirror =
        getAnnotationMirror(dialerComponentElement, IncludeInDialerRoot.class);

    AnnotationSpec.Builder subcomponentBuilder = AnnotationSpec.builder(Subcomponent.class);
    for (AnnotationValue annotationValue :
        (List<? extends AnnotationValue>)
            getAnnotationValue(componentMirror.get(), "modules").getValue()) {
      subcomponentBuilder.addMember(
          "modules", "$T.class", ClassName.get((TypeMirror) annotationValue.getValue()));
    }
    return subcomponentBuilder.build();
  }

  private TypeSpec dialerBoilerplateCode(
      TypeSpec.Builder typeBuilder, TypeElement dialerComponentElement) {
    return typeBuilder
        .addType(hasComponentInterface(typeBuilder, dialerComponentElement))
        .addMethod(addGetComponentMethod(typeBuilder, dialerComponentElement))
        .build();
  }

  private TypeSpec hasComponentInterface(
      TypeSpec.Builder typeBuilder, TypeElement dialerComponentElement) {
    return TypeSpec.interfaceBuilder("HasComponent")
        .addModifiers(PUBLIC)
        .addMethod(
            MethodSpec.methodBuilder("make" + dialerComponentElement.getSimpleName())
                .addModifiers(PUBLIC, ABSTRACT)
                .returns(getComponentClass(typeBuilder, dialerComponentElement))
                .build())
        .build();
  }

  private MethodSpec addGetComponentMethod(
      TypeSpec.Builder typeBuilder, TypeElement dialerComponentElement) {
    ClassName hasComponenetInterface =
        ClassName.get(
                getPackageName(dialerComponentElement),
                RootComponentUtils.GENERATED_COMPONENT_PREFIX
                    + dialerComponentElement.getSimpleName())
            .nestedClass("HasComponent");
    ClassName hasRootComponentInterface =
        ClassName.get(DIALER_INJECT_PACKAGE, DIALER_HASROOTCOMPONENT_INTERFACE);
    return MethodSpec.methodBuilder("get")
        .addModifiers(PUBLIC, STATIC)
        .addParameter(ParameterSpec.builder(ANDROID_CONTEXT_CLASS_NAME, "context").build())
        .addStatement(
            "$1T hasRootComponent = ($1T) context.getApplicationContext()",
            hasRootComponentInterface)
        .addStatement(
            "return (($T) (hasRootComponent.component())).make$T()",
            hasComponenetInterface,
            dialerComponentElement)
        .returns(getComponentClass(typeBuilder, dialerComponentElement))
        .build();
  }

  private void addElement(TypeSpec.Builder builder, Element element) {
    switch (element.getKind()) {
      case INTERFACE:
        builder.addType(cloneInterface(MoreElements.asType(element), "").build());
        break;
      case CLASS:
        builder.addType(cloneClass(MoreElements.asType(element), "").build());
        break;
      case FIELD:
        builder.addField(cloneField(MoreElements.asVariable(element)).build());
        break;
      case METHOD:
        builder.addMethod(cloneMethod(MoreElements.asExecutable(element)));
        break;
      case CONSTRUCTOR:
        builder.addMethod(cloneConstructor(MoreElements.asExecutable(element)));
        break;
      default:
        throw new RuntimeException(
            String.format("Unexpected element %s met during class cloning phase!", element));
    }
  }

  private MethodSpec cloneMethod(ExecutableElement element) {
    return MethodSpec.methodBuilder(element.getSimpleName().toString())
        .addModifiers(element.getModifiers())
        .returns(TypeName.get(element.getReturnType()))
        .addParameters(cloneParameters(element.getParameters()))
        .build();
  }

  private MethodSpec cloneConstructor(ExecutableElement element) {
    return MethodSpec.constructorBuilder()
        .addModifiers(element.getModifiers())
        .addParameters(cloneParameters(element.getParameters()))
        .build();
  }

  private List<ParameterSpec> cloneParameters(
      List<? extends VariableElement> variableElementsList) {
    List<ParameterSpec> list = new ArrayList<>();
    for (VariableElement variableElement : variableElementsList) {
      ParameterSpec.Builder builder =
          ParameterSpec.builder(
                  TypeName.get(variableElement.asType()),
                  variableElement.getSimpleName().toString())
              .addModifiers(variableElement.getModifiers());
      list.add(builder.build());
    }
    return list;
  }

  private TypeSpec.Builder cloneInterface(TypeElement element, String prefix) {
    return cloneType(TypeSpec.interfaceBuilder(prefix + element.getSimpleName()), element);
  }

  private TypeSpec.Builder cloneClass(TypeElement element, String prefix) {
    return cloneType(TypeSpec.classBuilder(prefix + element.getSimpleName()), element);
  }

  private FieldSpec.Builder cloneField(VariableElement element) {
    FieldSpec.Builder builder =
        FieldSpec.builder(TypeName.get(element.asType()), element.getSimpleName().toString());
    element.getModifiers().forEach(builder::addModifiers);
    return builder;
  }

  private TypeSpec.Builder cloneType(TypeSpec.Builder builder, TypeElement element) {
    element.getModifiers().forEach(builder::addModifiers);
    for (Element enclosedElement : element.getEnclosedElements()) {
      addElement(builder, enclosedElement);
    }
    return builder;
  }

  private ClassName getComponentClass(
      TypeSpec.Builder typeBuilder, TypeElement dialerComponentElement) {
    return ClassName.get(getPackageName(dialerComponentElement), typeBuilder.build().name);
  }

  private String getPackageName(TypeElement element) {
    return ClassName.get(element).packageName();
  }
}
