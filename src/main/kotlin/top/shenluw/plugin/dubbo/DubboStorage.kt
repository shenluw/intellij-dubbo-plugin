package top.shenluw.plugin.dubbo

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.CollectionBean
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Transient
import com.jetbrains.rd.util.concurrentMapOf
import org.apache.commons.codec.digest.DigestUtils


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
    private val apps = hashSetOf<AppInfo>()
    @MapAnnotation
    val registries = concurrentMapOf<String, RegistryInfo>()

    @CollectionBean
    val invokeHistory = mutableListOf<InvokeHistory>()

    var lastConnectRegistry: String? = null

    fun getAppInfos(registry: String): List<AppInfo>? {
        return apps.filter { info -> info.registry == registry }
    }

    fun setAppInfos(registry: String, infos: Collection<AppInfo>) {
        removeRegistry(registry)
        if (infos.isNotEmpty()) {
            apps.addAll(infos)
        }
    }

    fun addRegistry(info: RegistryInfo) {
        registries[info.address!!] = info
    }

    fun removeRegistry(registry: String, removeRegistryRecord: Boolean = false) {
        val iterator = apps.iterator()
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

    /**
     * 获取最后一次调用的参数
     */
    fun getLastInvoke(appName: String, interfaceName: String, method: String, version: String): String? {
        val key = DigestUtils.md2Hex("$appName$interfaceName$method$version")
        for (i in invokeHistory.size - 1..0) {
            if (invokeHistory[i].key == key) {
                return invokeHistory[i].value
            }
        }
        return null
    }

    fun addInvokeHistory(appName: String, interfaceName: String, method: String, version: String, value: String) {
        val key = DigestUtils.md2Hex("$appName$interfaceName$method$version")
        invokeHistory.add(InvokeHistory(key, value))
        if (invokeHistory.size > MAX_HISTORY) {
            invokeHistory.removeAt(0)
        }
    }

    fun clear() {
        apps.clear()
        invokeHistory.clear()
    }

    override fun getState() = this

    override fun loadState(state: DubboStorage) {
        XmlSerializerUtil.copyBean(state, this)
    }
}
