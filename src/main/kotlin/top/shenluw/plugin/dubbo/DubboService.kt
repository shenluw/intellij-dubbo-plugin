package top.shenluw.plugin.dubbo

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.Date
import com.jetbrains.rd.util.concurrentMapOf
import org.apache.dubbo.common.URL
import org.apache.dubbo.common.constants.CommonConstants.*
import top.shenluw.plugin.dubbo.client.*
import top.shenluw.plugin.dubbo.client.impl.DubboClientImpl
import top.shenluw.plugin.dubbo.utils.DubboUtils
import top.shenluw.plugin.dubbo.utils.DubboUtils.getAppKey
import java.util.concurrent.*

/**
 * @author Shenluw
 * created：2019/10/5 23:00
 */
class DubboService(val project: Project) : Disposable {
    /* key = registry */
    private var clients: MutableMap<String, DubboClient>? = concurrentMapOf<String, DubboClient>()
    private var threadPoolCache: ThreadPoolCache? = ThreadPoolCache()
    private var executeQueueThread: ExecuteQueueThread? = ExecuteQueueThread()
    private var responseListenExecutor: ExecutorService? = Executors.newSingleThreadExecutor()

    @Volatile
    var disposed = false

    private fun checkDisposed() {
        assert(!disposed) { "service already disposed" }
    }

    fun getClient(registry: String): DubboClient? {
        checkDisposed()
        return clients!![registry]
    }

    fun isConnected(registry: String): Boolean {
        checkDisposed()
        val client = clients!![registry] ?: return false
        return client.connected
    }

    fun connect(registry: String, username: String?, password: String?, listener: DubboListener) {
        checkDisposed()
        var client = clients!![registry]
        if (client != null) {
            client.listener = DubboListenerWrapper(listener)
            if (client.connected) {
                listener.onConnect(registry, username, password)
            } else {
                // 不允许同一个地址在连接时反复调用
                listener.onConnectError(registry, DubboClientException("连接失败"))
            }
        } else {
            object : Task.Backgroundable(project, "dubbo connect") {
                override fun run(indicator: ProgressIndicator) {
                    checkDisposed()
                    indicator.text = "connect $registry"
                    try {
                        DubboUtils.replaceClassLoader()
                        client = DubboClientImpl(DubboListenerWrapper(listener))
                        clients!![registry] = client!!
                        client?.connect(registry, username, password)
                    } finally {
                        DubboUtils.replaceClassLoader()
                    }
                    indicator.text = "连接成功"
                }

                override fun onThrowable(error: Throwable) {
                    listener.onConnectError(registry, DubboClientException("连接失败", error))
                }
            }.queue()
        }
    }

    fun disconnect(registry: String) {
        checkDisposed()
        val client = clients!![registry]
        client?.run {
            object : Task.Backgroundable(project, "dubbo disconnect") {
                override fun run(indicator: ProgressIndicator) {
                    clients!![registry]?.disconnect()
                }
            }.queue()
        }
    }

    /**
     * 调用此方法只返回url对应的数据，具体处理交给调用方
     */
    fun getAppInfo(registry: String, urls: List<URL>, callback: TaskCallback<List<AppInfo>>) {
        checkDisposed()
        val client = clients!![registry]
        if (client == null) {
            callback.onError("Dubbo 客户端不存在")
            callback.finish()
        } else if (!client.connected) {
            callback.onError("Dubbo 客户端未连接")
            callback.finish()
        } else {
            object : Task.Backgroundable(project, "dubbo 详情拉取") {
                private var values: List<AppInfo>? = null
                override fun run(indicator: ProgressIndicator) {
                    val client = clients!![registry] ?: throw DubboException("Dubbo 客户端不存在")
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
                                        appName,
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
                                        appName,
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

                override fun onFinished() {
                    callback.finish()
                }
            }.queue()
        }
    }

    fun execute(
        registry: String,
        request: DubboRequest,
        concurrentInfo: ConcurrentInfo,
        callback: TaskCallback<DubboResponse>
    ) {
        checkDisposed()
        val client = clients!![registry]
        if (client == null) {
            callback.onError("Dubbo 客户端不存在")
            callback.finish()
        } else {
            executeQueueThread?.push(Runnable {
                object : Task.Backgroundable(project, "dubbo execute") {
                    var response: DubboResponse? = null
                    override fun run(indicator: ProgressIndicator) {
                        checkDisposed()
                        indicator.text = "开始测试"
                        val client = clients!![registry] ?: throw DubboException("Dubbo 客户端不存在")
                        try {
                            DubboUtils.replaceClassLoader()
                            if (concurrentInfo === NoConcurrentInfo) {
                                response = client.invoke(request)
                            } else {
                                if (client is DubboConcurrentClient) {
                                    runConcurrentExec(client, request, concurrentInfo, callback)
                                } else {
                                    throw DubboException("当前连接不允许并发测试")
                                }
                            }
                        } finally {
                            DubboUtils.restoreClassLoader()
                        }
                    }

                    override fun onSuccess() {
                        if (concurrentInfo === NoConcurrentInfo) {
                            callback.onSuccess(response)
                        }
                    }

                    override fun onThrowable(error: Throwable) {
                        callback.onError("接口调用失败", error)
                    }

                    override fun onFinished() {
                        callback.finish()
                    }
                }.queue()
            })
        }
    }

    private fun runConcurrentExec(
        client: DubboConcurrentClient,
        request: DubboRequest,
        concurrentInfo: ConcurrentInfo,
        callback: TaskCallback<DubboResponse>
    ) {
        client.beforeInvoke(request)

        val executor = threadPoolCache?.get(concurrentInfo.group)
        val tasks = arrayListOf<Callable<DubboResponse>>()
        for (i in 1..concurrentInfo.count) {
            tasks.add(Callable {
                client.invoke(request)
            })
        }

        val futures = executor?.invokeAll(tasks)
        val latch = CountDownLatch(tasks.size)
        responseListenExecutor?.submit {
            while (!futures.isNullOrEmpty() && latch.count > 0) {
                var i = 0
                while (futures.isNotEmpty()) {
                    val it = futures[i++]
                    if (it.isDone || it.isCancelled) {
                        try {
                            val response = it.get()
                            callback.postBackground(response)
                            invokeLater { callback.onSuccess(response) }
                        } catch (ex: Exception) {
                            callback.postErrorBackground("执行失败", ex)
                            invokeLater { callback.onError("执行失败", ex) }
                        } finally {
                            futures.removeAt(--i)
                            latch.countDown()
                        }
                    }
                }
            }
        }

        latch.await()
        client.afterInvoke(request)
        invokeLater { callback.finish() }
    }

    override fun dispose() {
        disposed = true
        clients?.values?.forEach {
            it.disconnect()
        }
        clients?.clear()
        clients = null
        threadPoolCache?.destroy()
        threadPoolCache = null

        executeQueueThread?.close()
        executeQueueThread = null

        responseListenExecutor?.shutdownNow()
        responseListenExecutor = null
    }

    inner class DubboListenerWrapper(private val listener: DubboListener) : DubboListener {
        override fun onConnect(address: String, username: String?, password: String?) {
            listener.onConnect(address, username, password)
        }

        override fun onConnectError(address: String, exception: Exception?) {
            clients?.remove(address)
            listener.onConnectError(address, exception)
        }

        override fun onDisconnect(address: String) {
            clients?.remove(address)
            listener.onDisconnect(address)
        }

        override fun onUrlChanged(address: String, urls: List<URL>) {
            listener.onUrlChanged(address, urls)
        }
    }
}

private class ExecuteQueueThread : Thread("ExecuteQueueThread") {
    private val queue = ArrayBlockingQueue<Runnable>(10)
    private var running = true
    override fun run() {
        while (running) {
            postRunnable(queue.take())
        }
    }

    /**
     * @return true push 成功
     */
    fun push(task: Runnable): Boolean {
        if (queue.isEmpty()) {
            postRunnable(task)
        } else {
            return queue.add(task)
        }
        return true
    }

    /**
     * 执行一个调用任务
     */
    fun postRunnable(task: Runnable) {
        task.run()
    }

    fun close() {
        running = false
        queue.clear()
    }

}


private class ThreadPoolCache {
    private val expire = 5L
    private val cache: Cache<Int, ExecutorService?>? = CacheBuilder.newBuilder()
        // 一次并发测试的线程池有效期设定为5分钟，超时后清除减少资源占用
        .expireAfterAccess(expire, TimeUnit.MINUTES)
        .removalListener<Int, ExecutorService?> {
            val value = it.value
            if (value != null && !value.isShutdown) {
                value.shutdown()
            }
        }.build<Int, ExecutorService?>()

    private var executor: ScheduledExecutorService? = Executors.newScheduledThreadPool(1) {
        Thread(it, "clear-invoke-thread-pool-executor")
    }

    init {
        executor?.scheduleAtFixedRate({
            cache?.cleanUp()
            println("run clean ${Date()}")
        }, 5, 5, TimeUnit.MINUTES)
    }

    fun get(count: Int): ExecutorService {

        var executor = cache?.getIfPresent(count)
        if (executor == null) {
            executor = Executors.newFixedThreadPool(count)
            cache?.put(count, executor)
        }

        return executor!!
    }

    fun destroy() {
        cache?.invalidateAll()
        executor?.isShutdown?.apply {
            if (this) {
                executor?.shutdownNow()
                executor = null
            }
        }
    }

}

/**
 * 回调方法必须在ui线程
 */
interface TaskCallback<T> {
    fun onSuccess(any: T? = null) {}

    fun onError(msg: String, e: Throwable? = null) {}
    /**
     * 结果回调不切换线程
     */
    fun postBackground(data: T? = null) {}

    fun postErrorBackground(msg: String, e: Throwable? = null) {}

    fun finish() {}
}