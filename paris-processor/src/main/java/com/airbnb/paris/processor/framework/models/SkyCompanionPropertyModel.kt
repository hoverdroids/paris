package com.airbnb.paris.processor.framework.models

import com.airbnb.paris.processor.abstractions.XElement
import com.airbnb.paris.processor.abstractions.XFieldElement
import com.airbnb.paris.processor.abstractions.XTypeElement
import com.airbnb.paris.processor.abstractions.isFieldElement
import com.airbnb.paris.processor.abstractions.isMethod
import com.airbnb.paris.processor.abstractions.javac.JavacFieldElement
import com.airbnb.paris.processor.framework.JavaCodeBlock
import com.airbnb.paris.processor.framework.KotlinCodeBlock
import com.airbnb.paris.processor.framework.JavaSkyProcessor
import com.airbnb.paris.processor.framework.isJava
import com.airbnb.paris.processor.framework.siblings
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror

/**
 * Applies to Java fields and Kotlin properties
 */
abstract class SkyCompanionPropertyModel(val element: XFieldElement) : SkyModel {

    val enclosingElement: XTypeElement = element.enclosingTypeElement
    val name: String = element.name
    val getterElement: Element
    val javaGetter: JavaCodeBlock
    val kotlinGetter: KotlinCodeBlock

    init {
        if (element !is JavacFieldElement) error("unsupported $element")
        val variableElement = element.element

        if (variableElement.isJava()) {
            getterElement = variableElement
            javaGetter = JavaCodeBlock.of("\$N", variableElement.simpleName)
        } else {
            // In Kotlin the annotated element is a private static field which is accompanied by a Companion method

            val getterName = "get${name.capitalize()}"
            val companionFunctions = variableElement.siblings()
                .single {
                    it is TypeElement && it.simpleName.toString() == "Companion"
                }
                .enclosedElements
                .filterIsInstance<ExecutableElement>()

            // If the property is public the name of the getter function will be prepended with "get". If it's internal, it will also
            // be appended with "$" and an arbitrary string for obfuscation purposes.
            // Kotlin 1.4.x contains BOTH at once, but only the none synthetic one can be used, so we check for the real one first.
            getterElement = companionFunctions.firstOrNull {
                val elementSimpleName = it.simpleName.toString()
                elementSimpleName == getterName
            } ?: companionFunctions.firstOrNull {
                val elementSimpleName = it.simpleName.toString()
                elementSimpleName.startsWith("$getterName$")
            } ?: error("$variableElement - could not get companion property")

            javaGetter = JavaCodeBlock.of("Companion.\$N()", getterElement.simpleName)
        }

        kotlinGetter = KotlinCodeBlock.of("%N()", getterElement.simpleName)
    }
}

abstract class SkyCompanionPropertyModelFactory<T : SkyCompanionPropertyModel>(
    override val processor: JavaSkyProcessor,
    annotationClass: Class<out Annotation>
) : JavaSkyModelFactory<T, XFieldElement>(processor, annotationClass) {

    override fun filter(element: XElement): Boolean = element.isFieldElement()
}
