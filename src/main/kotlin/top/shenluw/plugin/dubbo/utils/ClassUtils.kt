package top.shenluw.plugin.dubbo.utils

import com.intellij.psi.util.ClassUtil

/**
 * @author Shenluw
 * created: 2019/12/15 21:21
 */
object ClassUtils {

    private val classCache = mapOf(
        "java.lang.String" to "String",
        "java.lang.Integer" to "int",
        "java.lang.Character" to "char",
        "java.lang.Boolean" to "boolean",
        "java.lang.Long" to "long",
        "java.lang.Double" to "double",
        "java.lang.Float" to "float",
        "java.util.List" to "List",
        "java.util.Queue" to "Queue",
        "java.util.Collection" to "Collection",
        "java.util.Map" to "Map",
        "java.util.Set" to "Set",
        "java.lang.Iterable" to "Iterable"
    )


    fun getSimpleName(name: String): String {
        return classCache[name] ?: ClassUtil.extractClassName(name)
    }

}