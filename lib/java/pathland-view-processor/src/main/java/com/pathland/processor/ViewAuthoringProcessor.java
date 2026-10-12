package com.pathland.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Set;

/**
 * Enforces the **configurator convention** (spec DSL.md §2) for the two
 * authoring surfaces that take a value config:
 *
 * <ul>
 *   <li>a custom {@code com.pathland.view.View}, and</li>
 *   <li>a custom style ({@code com.pathland.view.Style} — {@code ButtonStyle},
 *       {@code LabelStyle}, {@code AudioStyle}, {@code VideoStyle} implementations).</li>
 * </ul>
 *
 * <p>Both are configured through a static {@code make(Consumer<Config>)} factory
 * (views) / {@code with(Consumer<Config>)} factory (styles),
 * never a parameterized constructor. Any non-abstract class assignable to
 * {@code View} or {@code Style} that exposes a {@code public}/{@code protected}
 * constructor **with ≥ 1 parameter** is a compile error — creation must go
 * through the configurator that a {@code Config} + private constructor exist for
 * (a no-arg constructor is legal: config-less roots like {@code new MyView()}
 * may still be mounted directly).
 *
 * <p>Types are detected by assignability (no annotation), only for classes part
 * of this compilation round (application views/styles), never library/jar types.
 * Abstract classes are exempt (they cannot be instantiated), and enums/records/
 * interfaces are skipped (they cannot expose a parameterized public constructor
 * in the flagged form).
 *
 * <p>Opt-in per module: add this jar (pathland-view-processor) to the module's
 * {@code annotationProcessorPaths} (the same wiring that activates
 * {@link PersistedProcessor} for {@code State} binders).
 */
@SupportedAnnotationTypes("*")
public final class ViewAuthoringProcessor extends AbstractProcessor {

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        Elements elements = processingEnv.getElementUtils();
        Types types = processingEnv.getTypeUtils();
        TypeElement viewType = elements.getTypeElement("com.pathland.view.View");
        if (viewType == null) {
            return false; // pathland-view not on the processing classpath
        }
        TypeMirror viewErasure = types.erasure(viewType.asType());
        TypeElement styleType = elements.getTypeElement("com.pathland.view.Style");
        TypeMirror styleErasure = styleType == null ? null : types.erasure(styleType.asType());

        for (Element root : roundEnv.getRootElements()) {
            if (!(root instanceof TypeElement type) || type.getKind() != ElementKind.CLASS) {
                continue;
            }
            TypeMirror erasure = types.erasure(type.asType());
            boolean isView = types.isAssignable(erasure, viewErasure);
            boolean isStyle = styleErasure != null && types.isAssignable(erasure, styleErasure);
            if (!isView && !isStyle) {
                continue;
            }
            if (type.getModifiers().contains(Modifier.ABSTRACT)) {
                continue; // abstract types can't be instantiated — configurator may not apply
            }
            String surface = isStyle ? "Style" : "View";
            for (ExecutableElement constructor : constructorsOf(type)) {
                if (isAccessible(constructor) && !constructor.getParameters().isEmpty()) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            type.getSimpleName() + " exposes a " + accessibilityOf(constructor)
                                    + " constructor with parameters: " + constructor
                                    + " — a custom " + surface + " must be configured via its static "
                                    + type.getSimpleName() + (isStyle ? ".with" : ".make")
                                    + "(Consumer<Config>) factory (spec DSL.md §2);"
                                    + " keep the constructor private and expose the values on the nested Config.",
                            constructor);
                }
            }
        }
        return false;
    }

    private static List<ExecutableElement> constructorsOf(TypeElement type) {
        return type.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
                .map(e -> (ExecutableElement) e)
                .toList();
    }

    private static boolean isAccessible(ExecutableElement constructor) {
        return constructor.getModifiers().contains(Modifier.PUBLIC)
                || constructor.getModifiers().contains(Modifier.PROTECTED);
    }

    private static String accessibilityOf(ExecutableElement constructor) {
        return constructor.getModifiers().contains(Modifier.PUBLIC) ? "public" : "protected";
    }
}