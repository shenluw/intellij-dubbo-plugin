package top.shenluw.plugin.dubbo.parameter

import org.yaml.snakeyaml.Yaml
import top.shenluw.plugin.dubbo.DubboException
import top.shenluw.plugin.dubbo.client.DubboParameter
import java.util.*

/**
 * @author Shenluw
 * created：2019/10/13 18:45
 */
interface ParameterParser {

    fun parse(txt: String, argumentTypes: Array<String>): Array<DubboParameter>

}


object YamlParameterParser : ParameterParser {

    private fun checkType(type: String, value: Any): Boolean {
        return when (type) {
            "java.lang.String" -> value is String
            "java.lang.Integer", "int" -> value is Int
            "java.lang.Character", "char" -> value is Char
            "java.lang.Boolean", "boolean" -> value is Boolean
            "java.lang.Long", "long" -> value is Long
            "java.lang.Double", "double" -> value is Double
            "java.lang.Float", "float" -> value is Float
            "java.util.List" -> value is List<*>
            "java.util.Queue" -> value is Queue<*>
            "java.util.Collection" -> value is Collection<*>
            "java.util.Map" -> value is Map<*, *>
            "java.util.Set" -> value is Set<*>
            "java.lang.Iterable" -> value is Iterable<*>
            else -> value is Map<*, *>
        }
    }

    private fun getDefaultValue(type: String): Any? {
        return when (type) {
            "int" -> 0
            "char" -> 0
            "boolean" -> false
            "float" -> 0.0f
            "double" -> 0.0
            else -> null
        }
    }

    override fun parse(txt: String, argumentTypes: Array<String>): Array<DubboParameter> {
        val values = Yaml().load<List<Any?>>(txt)

        if (values.size != argumentTypes.size) {
            throw DubboException("解析参数错误与类型不匹配, 目标长度: ${argumentTypes.size}， 解析结果长度: ${values.size}")
        }

        return Array<DubboParameter>(argumentTypes.size) {
            val type = argumentTypes[it]
            val value = values[it]
            if (value == null) {
                DubboParameter(type, getDefaultValue(type))
            } else {
                if (!checkType(type, value)) {
                    throw DubboException("解析参数错误与类型不匹配, 目标类型: $type, 解析结果: ${value.javaClass.simpleName}")
                }
                DubboParameter(type, value)
            }
        }
    }

}