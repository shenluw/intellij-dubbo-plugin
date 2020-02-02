package top.shenluw.plugin.dubbo.utils

import top.shenluw.plugin.dubbo.Constants
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

    fun convertToLanguage(language: String, params: Array<DubboParameter>?): String {
        // TODO: 2020/2/1 未实现
        if (language == Constants.TEXT_LANGUAGE) {
        }
        return ""
    }

    fun convertToLanguage(language: String, data: Any?): String {
        // TODO: 2020/2/1 未实现
        if (language == Constants.TEXT_LANGUAGE) {
        }
        return ""
    }

}