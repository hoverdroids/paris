package com.airbnb.paris.processor

import com.airbnb.paris.processor.abstractions.XElement
import com.airbnb.paris.processor.abstractions.XTypeElement
import com.airbnb.paris.processor.models.BaseStyleableInfo
import com.squareup.javapoet.ClassName
import javax.tools.Diagnostic

internal class StyleablesTree(
    override val processor: ParisProcessor,
    private val styleablesInfo: List<BaseStyleableInfo>
) : WithParisProcessor {

    // This is a map of the View class qualified name to the StyleApplier class details
    // eg. "android.view.View" -> "com.airbnb.paris.ViewStyleApplier".className()
    private val viewQualifiedNameToStyleApplierClassName = mutableMapOf<XTypeElement, StyleApplierDetails>()

    /**
     * Traverses the class hierarchy of the given View type to find and return the first
     * corresponding style applier
     */
    internal fun findStyleApplier(viewTypeElement: XTypeElement): StyleApplierDetails {
        return viewQualifiedNameToStyleApplierClassName.getOrPut(viewTypeElement) {

            val type = viewTypeElement.type
            // Check to see if the view type is handled by a styleable class
            val styleableInfo = styleablesInfo.find { type.isSameType(it.viewElementType) }
            if (styleableInfo != null) {
                StyleApplierDetails(
                    annotatedElement = styleableInfo.annotatedElement,
                    className = styleableInfo.styleApplierClassName
                )
            } else {
                val superType = viewTypeElement.superType?.typeElement
                    ?: error("Could not find style applier for ${type}. Available types are ${styleablesInfo.map { it.viewElementType }}")
                findStyleApplier(superType)
            }
        }
    }
}

data class StyleApplierDetails(
    val annotatedElement: XElement,
    val className: ClassName
)
