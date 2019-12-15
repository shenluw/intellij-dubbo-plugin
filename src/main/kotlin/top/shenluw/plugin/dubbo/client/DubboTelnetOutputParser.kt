package top.shenluw.plugin.dubbo.client

import org.apache.dubbo.common.Version
import top.shenluw.plugin.dubbo.MethodInfo
import java.io.StringReader
import java.util.regex.Pattern

/**
 * @author Shenluw
 * created：2019/10/27 1:08
 */
interface DubboTelnetOutputParser {

    fun support(version: String): Boolean

    /**
     * 解析 ls -l xxx 结果
     */
    fun parseMethod(txt: String): List<MethodInfo>

}

object DubboTelnetOutputParserComposite {
    private val parsers = arrayOf(DubboTelnetOutputParser1, DubboTelnetOutputParser2)

    fun support(version: String): Boolean {
        return getSupport(version) != null
    }

    fun getSupport(version: String): DubboTelnetOutputParser? {
        for (parser in parsers) {
            if (parser.support(version)) {
                return parser
            }
        }
        return null
    }

}

private fun parseMethodWithLine(line: String, pattern: Pattern): MethodInfo? {
    val matcher = pattern.matcher(line)
    if (matcher.find()) {
        val returnType = matcher.group("rtype").trim()
        val methodName = matcher.group("name").trim()
        val args = matcher.group("args").trim()
        return if (args.isEmpty()) {
            MethodInfo(methodName, emptyArray(), returnType)
        } else {
            MethodInfo(methodName, args.split(",").toTypedArray(), returnType)
        }
    }
    return null
}

/**
 * 解析 2.6.0 及以上版本的telnet输出
 */
private object DubboTelnetOutputParser1 : DubboTelnetOutputParser {

    private val MIN_VERSION = Version.getIntVersion("2.6.0")
    private const val lsRegex = "\\s(?<rtype>.*)\\s(?<name>.*)\\((?<args>.*)\\)"

    override fun support(version: String): Boolean {
        val v = try {
            Version.getIntVersion(version)
        } catch (e: Exception) {
            return false
        }
        return v >= MIN_VERSION
    }

    override fun parseMethod(txt: String): List<MethodInfo> {
        if (txt.contains("No such service")) {
            throw DubboClientException(txt)
        }
        val reader = StringReader(txt)
        val compile = Pattern.compile(lsRegex)
        val result = arrayListOf<MethodInfo>()
        var first = true
        reader.forEachLine {
            if (first) {
                first = false
                return@forEachLine
            }
            parseMethodWithLine(it, compile)?.run {
                result.add(this)
            }
        }
        return result
    }
}

/**
 * 解析 2.6.0 以下版本的telnet输出
 */
private object DubboTelnetOutputParser2 : DubboTelnetOutputParser {

    private val MAX_VERSION = Version.getIntVersion("2.6.0")

    private const val lsRegex = "(?<rtype>.*)\\s(?<name>.*)\\((?<args>.*)\\)"

    override fun support(version: String): Boolean {
        val v = try {
            Version.getIntVersion(version)
        } catch (e: Exception) {
            return false
        }
        return v < MAX_VERSION
    }

    override fun parseMethod(txt: String): List<MethodInfo> {
        val reader = StringReader(txt)
        val compile = Pattern.compile(lsRegex)
        val result = arrayListOf<MethodInfo>()
        reader.forEachLine {
            parseMethodWithLine(it, compile)?.run { result.add(this) }
        }
        return result
    }
}
