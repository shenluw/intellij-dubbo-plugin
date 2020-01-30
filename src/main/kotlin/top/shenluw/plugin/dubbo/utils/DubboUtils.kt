package top.shenluw.plugin.dubbo.utils

import com.intellij.openapi.project.Project
import org.apache.dubbo.common.URL
import org.apache.dubbo.common.constants.CommonConstants
import top.shenluw.plugin.dubbo.Constants
import top.shenluw.plugin.dubbo.DubboStorage
import top.shenluw.plugin.dubbo.MethodInfo
import top.shenluw.plugin.dubbo.ServiceInfo
import top.shenluw.plugin.dubbo.client.DubboClientException
import java.io.StringReader

/**
 * @author Shenluw
 * created：2019/10/3 20:21
 */
object DubboUtils {

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

    fun URL.getAppKey(): String {
        return "${protocol}${address}${getParameter(
            CommonConstants.APPLICATION_KEY,
            ""
        )}"
    }

    private fun containsService(interfaceName: String, services: Collection<ServiceInfo>): Boolean {
        return services.find { it.interfaceName == interfaceName } != null
    }

    fun getMethodKey(appName: String, interfaceName: String, infos: Collection<ServiceInfo>): Set<String> {
        return infos.asSequence().filter { it.appName == appName && it.interfaceName == interfaceName }
            .mapNotNull { it.methods }
            .flatMap { it.asSequence() }
            .mapNotNull { it.key }
            .toSortedSet()
    }

    fun getVersions(appName: String, interfaceName: String, infos: List<ServiceInfo>): Set<String> {
        return infos.asSequence().filter { it.appName == appName && it.interfaceName == interfaceName }
            .map { it.version }
            .toSortedSet()
    }

    fun getGroups(appName: String, interfaceName: String, infos: List<ServiceInfo>): Set<String> {
        return infos.asSequence().filter { it.appName == appName && it.interfaceName == interfaceName }
            .mapNotNull { it.group }
            .filter { it.isNotBlank() }
            .toSortedSet()
    }

    fun getMethodInfo(
        project: Project,
        registry: String,
        appName: String,
        interfaceName: String,
        methodKey: String,
        version: String
    ): List<MethodInfo> {
        val infos = DubboStorage.getInstance(project).getServices(registry)
        if (infos.isNullOrEmpty()) return emptyList()
        return infos.asSequence()
            .filter { it.appName == appName && it.interfaceName == interfaceName && it.version == version }
            .mapNotNull { it.methods }
            .flatMap { it.asSequence() }
            .filter { it.key == methodKey }
            .toList()
    }

    fun getMethodName(key: String): String {
        val index = key.indexOf('(')
        return if (index == -1) {
            key
        } else {
            key.substring(0, index)
        }
    }

    fun Collection<URL>.containsByCondition(app: String, interfaceName: String? = null): Boolean {
        forEach {
            val appName = it.getParameter(CommonConstants.APPLICATION_KEY, "")
            val serviceInterface = it.serviceInterface
            if (interfaceName.isNullOrBlank()) {
                if (appName == app) {
                    return true
                }
            } else {
                if (appName == app && interfaceName == serviceInterface) {
                    return true
                }
            }
        }
        return false
    }

    fun getExtension(type: String?): String {
        if (type == null) {
            return Constants.TXT_EXTENSION
        }
        if (type.equals(Constants.XML_LANGUAGE, true)) {
            return Constants.XML_EXTENSION
        }
        if (type.equals(Constants.JSON_LANGUAGE, true)) {
            return Constants.JSON_EXTENSION
        }
        if (type.equals(Constants.HTML_LANGUAGE, true)) {
            return Constants.HTML_EXTENSION
        }
        if (type.equals(Constants.YAML_LANGUAGE, true)) {
            return Constants.YAML_EXTENSION
        }
        return Constants.TXT_EXTENSION
    }

    private val threadLocal = object : ThreadLocal<ClassLoader>() {
        override fun initialValue(): ClassLoader {
            return Thread.currentThread().contextClassLoader
        }
    }

    //插件启动ClassLoader读取不到Spi处理
    fun replaceClassLoader() {
        val loader = Thread.currentThread().contextClassLoader
        threadLocal.set(loader)
        Thread.currentThread().contextClassLoader = DubboUtils.javaClass.classLoader
    }

    fun restoreClassLoader() {
        threadLocal.get()?.run {
            Thread.currentThread().contextClassLoader = this
        }
        threadLocal.remove()
    }


    fun URL.getVersion(): String? {
        var version = getParameter(CommonConstants.RELEASE_KEY)
        if (version.isNullOrEmpty()) {
            // 未移交apache时的版本
            version = getParameter("dubbo")
        }
        return version
    }

}