package top.shenluw.plugin.dubbo

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.CollectionBean
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Transient
import com.jetbrains.rd.util.concurrentMapOf
import org.apache.commons.codec.digest.DigestUtils
import org.apache.dubbo.common.URL
import org.apache.dubbo.common.constants.CommonConstants


/**
 * 保存dubbo信息，包括缓存，调用历史参数，注册地址
 * @author Shenluw
 * created：2019/10/6 21:50
 */
@State(name = "DubboStorage", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class DubboStorage : PersistentStateComponent<DubboStorage> {

    companion object {
        private const val MAX_HISTORY = 20
        fun getInstance(project: Project): DubboStorage {
            return ServiceManager.getService(project, DubboStorage::class.java)
        }

    }

    /* 由于第一次连接dubbo时会收到全量通知，所以不需要存储，这里只作为缓存 */
    @Transient
    private val services = hashSetOf<ServiceInfo>()
    @MapAnnotation
    val registries = concurrentMapOf<String, RegistryInfo>()

    @CollectionBean
    val invokeHistory = mutableListOf<InvokeHistory>()

    var lastConnectRegistry: String? = null

    fun getServices(registry: String, appName: String? = null, interfaceName: String? = null): List<ServiceInfo>? {
        var tmp = services.filter { info -> info.registry == registry }
        if (!appName.isNullOrBlank()) {
            tmp = tmp.filter { info -> info.appName == appName }
        }
        if (!interfaceName.isNullOrBlank()) {
            tmp = tmp.filter { info -> info.interfaceName == interfaceName }
        }
        return tmp
    }

    fun setServices(registry: String, infos: Collection<ServiceInfo>) {
        removeRegistry(registry)
        if (infos.isNotEmpty()) {
            services.addAll(infos)
        }
    }

    fun addServices(infos: Collection<ServiceInfo>) {
        infos.forEach { info ->
            val iter = services.iterator()
            if (iter.hasNext()) {
                val m = iter.next()
                if (m.address == info.address && info.interfaceName == m.interfaceName) {
                    iter.remove()
                }
            }
            services.add(info)
        }
    }

    fun addRegistry(info: RegistryInfo) {
        registries[info.address!!] = info
    }

    fun removeRegistry(registry: String, removeRegistryRecord: Boolean = false) {
        val iterator = services.iterator()
        while (iterator.hasNext()) {
            val a = iterator.next()
            if (a.registry == registry) {
                iterator.remove()
            }
        }
        if (removeRegistryRecord) {
            registries.remove(registry)
        }
    }

    fun removeByURL(registry: String, urls: List<URL>) {
        val iterator = services.iterator()
        while (iterator.hasNext()) {
            val a = iterator.next()
            if (a.registry == registry) {
                if (urls.find {
                        it.getParameter(
                            CommonConstants.APPLICATION_KEY,
                            ""
                        ) == a.appName && a.interfaceName == it.serviceInterface
                    } != null) {
                    iterator.remove()
                }
            }
        }
    }

    /**
     * 获取最后一次调用的参数
     */
    fun getLastInvoke(service: ServiceInfo, method: MethodInfo): InvokeHistory? {
        val key = getKey(service, method)
        if (invokeHistory.size > 0) {
            for (i in invokeHistory.size - 1 downTo 0) {
                if (invokeHistory[i].key == key) {
                    return invokeHistory[i]
                }
            }
        }
        return null
    }

    fun addInvokeHistory(service: ServiceInfo, method: MethodInfo, request: String, response: String? = null) {
        val key = getKey(service, method)
        invokeHistory.add(InvokeHistory(key, request, response))
        if (invokeHistory.size > MAX_HISTORY) {
            invokeHistory.removeAt(0)
        }
    }

    fun clear() {
        services.clear()
        invokeHistory.clear()
    }

    private fun getKey(service: ServiceInfo, method: MethodInfo): String {
        return DigestUtils.md2Hex("${service.appName}${service.interfaceName}${method.key}${service.version}")
    }

    override fun getState() = this

    override fun loadState(state: DubboStorage) {
        XmlSerializerUtil.copyBean(state, this)
    }
}
