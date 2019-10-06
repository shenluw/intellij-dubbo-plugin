package top.shenluw.plugin.dubbo.utils

import top.shenluw.plugin.dubbo.client.DubboClientException
import top.shenluw.plugin.dubbo.client.DubboMethodInfo
import java.io.StringReader
import java.util.regex.Pattern

/**
 * @author Shenluw
 * created：2019/10/3 20:21
 */
object Dubbo {

    private const val lsRegex = "\\s(?<rtype>.*)\\s(?<name>.*)\\((?<args>.*)\\)"
    /**
     * 解析 ls -l xxx 结果
     */
    fun parseTelnetMethods(txt: String): List<DubboMethodInfo> {
        if (txt.contains("No such service")) {
            throw DubboClientException(txt)
        }
        val reader = StringReader(txt)
        val compile = Pattern.compile(lsRegex)
        val result = arrayListOf<DubboMethodInfo>()
        var first = true
        reader.forEachLine {
            if (first) {
                first = false
                return@forEachLine
            }
            val matcher = compile.matcher(it)
            if (matcher.find()) {
                val returnType = matcher.group("rtype").trim()
                val methodName = matcher.group("name").trim()
                val args = matcher.group("args").trim()
                result.add(DubboMethodInfo(methodName, args.split(",").toTypedArray(), returnType))
            }
        }
        return result
    }


    private const val RESPONSE_PREFIX = "result: "

    /**
     * 解析telnet调用结果数据
     */
    fun parseTelnetInvokeResponse(txt: String): String {
        val reader = StringReader(txt)
        val lines = reader.readLines()
        if (lines.size == 3) {
            val line = lines[1]
            return line.substring(RESPONSE_PREFIX.length)
        } else {
            if (txt.contains("No such method") || txt.contains("No such service")) {
                throw DubboClientException(lines[1])
            }
            if (txt.contains("Failed to invoke method")) {
                throw DubboClientException(lines[0])
            }
            throw DubboClientException("返回数据错误")
        }
    }
}