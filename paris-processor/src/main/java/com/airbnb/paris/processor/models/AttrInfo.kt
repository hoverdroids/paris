package com.airbnb.paris.processor.models

import androidx.annotation.RequiresApi
import com.airbnb.paris.annotations.Attr
import com.airbnb.paris.processor.Format
import com.airbnb.paris.processor.ParisProcessor
import com.airbnb.paris.processor.WithParisProcessor
import com.airbnb.paris.processor.abstractions.XExecutableElement
import com.airbnb.paris.processor.abstractions.XType
import com.airbnb.paris.processor.abstractions.XTypeElement
import com.airbnb.paris.processor.android_resource_scanner.AndroidResourceId
import com.airbnb.paris.processor.framework.JavaCodeBlock
import com.airbnb.paris.processor.framework.KotlinCodeBlock
import com.airbnb.paris.processor.framework.isPrivate
import com.airbnb.paris.processor.framework.isProtected
import com.airbnb.paris.processor.framework.models.SkyMethodModel
import com.airbnb.paris.processor.framework.models.SkyMethodModelFactory
import com.airbnb.paris.processor.framework.toKPoet
import java.lang.annotation.AnnotationTypeMismatchException
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

internal class AttrInfoExtractor(
    override val processor: ParisProcessor
) : SkyMethodModelFactory<AttrInfo>(processor, Attr::class.java), WithParisProcessor {

    override fun elementToModel(element: XExecutableElement): AttrInfo? {
        if (element.isPrivate() || element.isProtected()) {
            logError(element) {
                "Methods annotated with @Attr can't be private or protected."
            }
            return null
        }

        val attr = element.toAnnotationBox(Attr::class)?.value ?: error("@Attr annotation not found on $element")

        val targetType = element.parameters.firstOrNull()?.type ?: run {
            logError(element) {
                "Method with @Attr must provide a single parameter"
            }
            return null
        }

        val targetFormat = Format.forElement(processor.memoizer, element)

        val styleableResId: AndroidResourceId
        try {
            styleableResId = getResourceId(Attr::class.java, element, attr.value) ?: return null
        } catch (e: Throwable) {
            logError(element) {
                "Incorrectly typed @Attr value parameter. (This usually happens when an R value doesn't exist.)"
            }
            return null
        }

        var defaultValueResId: AndroidResourceId? = null
        try {
            if (attr.defaultValue != -1) {
                defaultValueResId = getResourceId(Attr::class.java, element, attr.defaultValue) ?: return null
            }
        } catch (e: Throwable) {
            logError(element) {
                "Incorrectly typed @Attr defaultValue parameter. (This usually happens when an R value doesn't exist.)"
            }
            return null
        }

        val enclosingElement = element.enclosingTypeElement
        val name = element.name
        val javadoc = JavaCodeBlock.of("@see \$T#\$N(\$T)\n", enclosingElement.className, name, targetType.typeName)
        // internal functions have a '$' in their name which creates a kdoc error. We could escape it but the part after the '$' is meant for
        // obfuscation anyway so not using it should result in clearer documentation.
        val kdocName = name.substringBefore('$')
        val kdoc = KotlinCodeBlock.of("@see %T.%N\n", enclosingElement.className.toKPoet(), kdocName)

        // We rely on the `RequiresApi` Android annotation to disable certain attributes based on the Android SDK version.
        // 1 is the default since that's the minimum version.
        val requiresApi = element.toAnnotationBox(RequiresApi::class)?.value?.let {
            // value is an alias of api, so we give precedence to api.
            if (it.api > 1) it.api else it.value
        } ?: 1

        return AttrInfo(
            element,
            targetType,
            targetFormat,
            styleableResId,
            defaultValueResId,
            javadoc,
            kdoc,
            requiresApi
        )
    }
}

/**
 * Element  The annotated element
 * Target   The method parameter
 */
internal class AttrInfo(
    element: XExecutableElement,
    val targetType: XType,
    val targetFormat: Format,
    val styleableResId: AndroidResourceId,
    val defaultValueResId: AndroidResourceId?,
    val javadoc: JavaCodeBlock,
    val kdoc: KotlinCodeBlock,
    val requiresApi: Int
) : SkyMethodModel(element)
