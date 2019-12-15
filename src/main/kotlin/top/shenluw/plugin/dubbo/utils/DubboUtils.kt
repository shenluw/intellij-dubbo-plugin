package top.shenluw.plugin.dubbo.utils

import com.intellij.openapi.project.Project
import org.apache.dubbo.common.URL
import org.apache.dubbo.common.constants.CommonConstants
import top.shenluw.plugin.dubbo.*
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

    /**
     * 由于dubbo保证对interfaceName全量通知
     * 如果inc中的 interfaceName 和 origin 相同，则移除 origin 中的数据
     */
    fun mergeAppInfos(origin: Collection<AppInfo>?, inc: Collection<AppInfo>?): Collection<AppInfo> {
        if (origin.isNullOrEmpty()) {
            return when {
                inc.isNullOrEmpty() -> emptyList()
                else -> inc
            }
        } else {
            if (inc.isNullOrEmpty()) {
                return origin
            }
        }
        // 2个都不为空
        val list = inc.toMutableList()
        origin.forEach { app ->
            val info = findApp(app, list)
            if (info == null) {
                list.add(app)
            } else {
                val services = info.services
                if (services == null) {
                    info.services = app.services
                } else {
                    app.services?.forEach {
                        if (!containsService(it.interfaceName, services)) {
                            services.add(it)
                        }
                    }
                }
            }
        }
        return list
    }

    private fun findApp(app: AppInfo, apps: Collection<AppInfo>): AppInfo? {
        return apps.find { it.name == app.name && it.registry == app.registry && it.address == app.address }
    }

    private fun containsService(interfaceName: String, services: Collection<ServiceInfo>): Boolean {
        return services.find { it.interfaceName == interfaceName } != null
    }

    fun getInterfaceNames(appName: String, apps: Collection<AppInfo>): List<String> {
        return apps.asSequence().filter { it.name == appName }
            .mapNotNull { it.services }
            .flatMap { it.asSequence() }
            .map { it.interfaceName }
            .sorted().toList()
    }

    fun getMethodKey(appName: String, interfaceName: String, apps: Collection<AppInfo>): List<String> {
        return apps.asSequence().filter { it.name == appName }
            .mapNotNull { it.services }
            .flatMap { it.asSequence() }
            .filter { it.interfaceName == interfaceName }
            .mapNotNull { it.methods }
            .flatMap { it.asSequence() }
            .mapNotNull { it.key }
            .sorted().toList()
    }

    fun getVersions(appName: String, interfaceName: String, apps: List<AppInfo>): List<String> {
        return apps.asSequence().filter { it.name == appName }
            .mapNotNull { it.services }
            .flatMap { it.asSequence() }
            .filter { it.interfaceName == interfaceName }
            .map { it.version }
            .sorted().toList()
    }

    fun getGroups(appName: String, interfaceName: String, apps: List<AppInfo>): List<String> {
        return apps.asSequence().filter { it.name == appName }
            .mapNotNull { it.services }
            .flatMap { it.asSequence() }
            .filter { it.interfaceName == interfaceName }
            .mapNotNull { it.group }
            .filter { it.isNotBlank() }
            .sorted().toList()
    }

    fun getServers(appName: String, interfaceName: String, apps: List<AppInfo>): List<String> {
        return apps.asSequence().filter { it.name == appName }
            .mapNotNull { it.services }
            .flatMap { it.asSequence() }
            .filter { it.interfaceName == interfaceName }
            .mapNotNull { it.address }
            .sorted().toList()
    }

    fun getMethodInfo(
        project: Project,
        registry: String,
        appName: String,
        interfaceName: String,
        methodKey: String,
        version: String
    ): List<MethodInfo> {
        val apps = DubboStorage.getInstance(project).getAppInfos(registry)
        if (apps.isNullOrEmpty()) return emptyList()
        return apps.asSequence().filter { it.name == appName }
            .mapNotNull { it.services }
            .flatMap { it.asSequence() }
            .filter { it.interfaceName == interfaceName && it.version == version }
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