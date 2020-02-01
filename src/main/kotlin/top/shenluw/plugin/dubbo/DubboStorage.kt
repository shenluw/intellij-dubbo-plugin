package top.shenluw.plugin.dubbo

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Transient
import com.intellij.util.xmlb.annotations.XCollection
import com.jetbrains.rd.util.concurrentMapOf
import org.apache.commons.codec.digest.DigestUtils
import org.apache.dubbo.common.URL
import org.apache.dubbo.common.constants.CommonConstants
import top.shenluw.plugin.dubbo.client.DubboParameter
import top.shenluw.plugin.dubbo.client.DubboRequest
import top.shenluw.plugin.dubbo.client.DubboResponse
import top.shenluw.plugin.dubbo.utils.DubboUtils
import top.shenluw.plugin.dubbo.utils.KLogger


/**
 * 保存dubbo信息，包括缓存，调用历史参数，注册地址
 * @author Shenluw
 * created：2019/10/6 21:50
 */
@State(name = "DubboStorage", storages = [Storage(Constants.DUBBO_STORAGE_FILE)])
class DubboStorage : PersistentStateComponent<DubboStorage>, KLogger {

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

    @XCollection(propertyElementName = "invokeHistories")
    val invokeHistories = mutableListOf<InvokeHistory>()

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
    fun getLastInvoke(service: ServiceInfo, method: MethodInfo): UnWrapperHistory? {
        val key = getKey(service, method)
        if (invokeHistories.size > 0) {
            for (i in invokeHistories.size - 1 downTo 0) {
                if (invokeHistories[i].key == key) {
                    val history = invokeHistories[i]
                    var params: Array<DubboParameter>? = null
                    if (!history.request.isNullOrBlank()) {
                        try {
                            params = Gson.fromJson(history.request, Array<DubboParameter>::class.java)
                        } catch (e: Exception) {
                            log.debug("saved value invalid")
                        }
                    }
                    var response: DubboResponse? = null
                    if (!history.response.isNullOrBlank()) {
                        try {
                            response = Gson.fromJson(history.response, DubboResponse::class.java)
                        } catch (e: Exception) {
                            log.debug("saved value invalid")
                        }
                    }
                    return UnWrapperHistory(params, response)
                }
            }
        }
        return null
    }

    fun addInvokeHistory(request: DubboRequest, response: DubboResponse? = null) {
        val key = getKey(request)
        invokeHistories.add(InvokeHistory(key, Gson.toJson(request.params), Gson.toJson(response)))
        if (invokeHistories.size > MAX_HISTORY) {
            invokeHistories.removeAt(0)
        }
    }

    fun clear() {
        services.clear()
        invokeHistories.clear()
    }

    private fun getKey(service: ServiceInfo, method: MethodInfo): String {
        return DigestUtils.md2Hex("${service.appName}${service.interfaceName}${method.key}${service.version}")
    }

    private fun getKey(request: DubboRequest): String {
        val methodKey = DubboUtils.genMethodKey(
            request.method,
            request.params.map { it.type }.toTypedArray()
        )
        return DigestUtils.md2Hex("${request.appName}${request.interfaceName}$methodKey${request.version}")
    }

    override fun getState() = this

    override fun loadState(state: DubboStorage) {
        XmlSerializerUtil.copyBean(state, this)
    }

    data class UnWrapperHistory(val params: Array<DubboParameter>?, val response: DubboResponse?) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as UnWrapperHistory

            if (params != null) {
                if (other.params == null) return false
                if (!params.contentEquals(other.params)) return false
            } else if (other.params != null) return false
            if (response != other.response) return false

            return true
        }

        override fun hashCode(): Int {
            var result = params?.contentHashCode() ?: 0
            result = 31 * result + (response?.hashCode() ?: 0)
            return result
        }
    }
}
