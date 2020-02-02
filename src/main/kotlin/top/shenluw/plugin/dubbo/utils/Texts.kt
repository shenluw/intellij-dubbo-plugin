package top.shenluw.plugin.dubbo.utils

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import top.shenluw.plugin.dubbo.Constants
import top.shenluw.plugin.dubbo.PrettyGson
import top.shenluw.plugin.dubbo.client.DubboParameter

/**
 * @author Shenluw
 * created：2019/10/4 21:40
 */
object Texts {
    /**
     * 对telnet字符串中的特殊字符转义
     */
    fun escape(src: String): String {
        if (src.isEmpty()) {
            return src
        }
        val sb = StringBuilder()
        src.forEach {
            when (it) {
                '"' -> sb.append('\\').append(it)
                '\\' -> sb.append('\\').append(it)
                else -> sb.append(it)
            }
        }
        return sb.toString()
    }

    private val DefaultDumperOptions = DumperOptions()

    init {
        DefaultDumperOptions.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
    }

    fun convertToLanguage(language: String, params: Array<DubboParameter>?): String {
        if (params.isNullOrEmpty()) {
            return ""
        }
        if (Constants.YAML_LANGUAGE.equals(language, true)) {
            return Yaml(DefaultDumperOptions).dump(params.map { it.value })
        }
        return ""
    }

    fun convertToLanguage(language: String, data: Any?): String {
        // 字符串不处理，因为不知道类型
        if (data is String) {
            return data
        }
        if (Constants.JSON_LANGUAGE.equals(language, true)) {
            return PrettyGson.toJson(data)
        } else if (Constants.YAML_LANGUAGE.equals(language, true)) {
            return Yaml(DefaultDumperOptions).dump(data)
        }
        return data?.toString() ?: "null"
    }

}