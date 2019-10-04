package top.shenluw.plugin.dubbo.utils

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
}