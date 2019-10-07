package top.shenluw.plugin.dubbo

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.concurrentMapOf
import org.apache.dubbo.common.URL
import org.apache.dubbo.common.constants.CommonConstants.*
import top.shenluw.plugin.dubbo.client.DubboClient
import top.shenluw.plugin.dubbo.client.DubboClientException
import top.shenluw.plugin.dubbo.client.DubboListener
import top.shenluw.plugin.dubbo.client.impl.DubboClientImpl
import top.shenluw.plugin.dubbo.utils.DubboUtils.getAppKey

/**
 * @author Shenluw
 * created：2019/10/5 23:00
 */
class DubboService(val project: Project) : Disposable {
    /* key = registry */
    private val clients = concurrentMapOf<String, DubboClient>()

    fun getClient(registry: String): DubboClient? {
        return clients[registry]
    }

    fun isConnected(registry: String): Boolean {
        val client = clients[registry] ?: return false
        return client.connected
    }

    fun connect(registry: String, username: String?, password: String?, listener: DubboListener) {
        var client = clients[registry]
        if (client != null) {
            client.listener = DubboListenerWrapper(listener)
            if (client.connected) {
                listener.onConnect(registry)
            } else {
                // 不允许同一个地址在连接时反复调用
                listener.onConnectError(registry, DubboClientException("连接失败"))
            }
        } else {
            object : Task.Backgroundable(project, "dubbo connect") {
                override fun run(indicator: ProgressIndicator) {
                    indicator.text = "connect $registry"

                    client = DubboClientImpl(DubboListenerWrapper(listener))
                    clients[registry] = client!!
                    client?.connect(registry)
                    indicator.text = "connect success"
                }

                override fun onThrowable(error: Throwable) {
                    listener.onConnectError(registry, DubboClientException("连接失败", error))
                }
            }.queue()
        }
    }

    fun disconnect(registry: String) {
        val client = clients[registry]
        client?.run {
            object : Task.Backgroundable(project, "dubbo disconnect") {
                override fun run(indicator: ProgressIndicator) {
                    clients[registry]?.disconnect()
                }
            }.queue()
        }
    }

    /**
     * 调用此方法只返回url对应的数据，具体处理交给调用方
     */
    fun getAppInfo(registry: String, urls: List<URL>, callback: TaskCallback<List<AppInfo>>) {
        val client = clients[registry]
        if (client == null) {
            callback.onError("Dubbo 客户端不存在")
        } else if (!client.connected) {
            callback.onError("Dubbo 客户端未连接")
        } else {
            object : Task.Backgroundable(project, "dubbo 详情拉取") {
                private var values: List<AppInfo>? = null
                override fun run(indicator: ProgressIndicator) {
                    val client = clients[registry] ?: throw RuntimeException("Dubbo 客户端不存在")
                    val pullInfos = mutableMapOf<String, AppInfo>()
                    urls.forEach {
                        val methodInfos = client.getServiceMethods(it)
                        val key = it.getAppKey()

                        val appName = it.getParameter(APPLICATION_KEY, "")
                        val version = it.getParameter(VERSION_KEY, "")
                        val serviceInterface = it.serviceInterface
                        val group = it.getParameter(GROUP_KEY, "")
                        val appInfo = pullInfos.getOrPut(
                            key,
                            { AppInfo(appName, registry, "${it.protocol}://${it.address}", null) })

                        var services = appInfo.services
                        if (services == null) {
                            services =
                                arrayListOf(
                                    ServiceInfo(
                                        serviceInterface,
                                        version,
                                        group,
                                        "${it.protocol}://${it.address}",
                                        methodInfos.toMutableList()
                                    )
                                )
                            appInfo.services = services
                        } else {
                            var containService = false
                            services.forEach { info ->
                                if (info.group == group && info.interfaceName == serviceInterface) {
                                    info.methods?.addAll(methodInfos)
                                    containService = true
                                }
                            }
                            if (!containService) {
                                services.add(
                                    ServiceInfo(
                                        serviceInterface,
                                        version,
                                        group,
                                        "${it.protocol}://${it.address}",
                                        methodInfos.toMutableList()
                                    )
                                )
                            }
                        }
                    }
                    values = pullInfos.values.toList()
                }

                override fun onSuccess() {
                    callback.onSuccess(values)
                }

                override fun onThrowable(error: Throwable) {
                    callback.onError("信息获取失败", error)
                }
            }.queue()
        }
    }

    override fun dispose() {
        clients.values.forEach {
            it.disconnect()
        }
        clients.clear()
    }

    inner class DubboListenerWrapper(private val listener: DubboListener) : DubboListener {
        override fun onConnect(address: String) {
            listener.onConnect(address)
        }

        override fun onConnectError(address: String, exception: Exception?) {
            clients.remove(address)
            listener.onConnectError(address, exception)
        }

        override fun onDisconnect(address: String) {
            clients.remove(address)
            listener.onDisconnect(address)
        }

        override fun onUrlChanged(address: String, urls: List<URL>) {
            listener.onUrlChanged(address, urls)
        }
    }
}

/**
 * 回调方法必须在ui线程
 */
interface TaskCallback<T> {
    fun onSuccess(any: T? = null) {}

    fun onError(msg: String, e: Throwable? = null) {}
}