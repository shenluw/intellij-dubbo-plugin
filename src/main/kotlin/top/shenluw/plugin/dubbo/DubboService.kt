package top.shenluw.plugin.dubbo

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.concurrentMapOf
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.apache.dubbo.common.URL
import top.shenluw.plugin.dubbo.client.*
import top.shenluw.plugin.dubbo.client.impl.DubboClientImpl
import top.shenluw.plugin.dubbo.utils.DubboUtils
import top.shenluw.plugin.dubbo.utils.KLogger
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * @author Shenluw
 * created：2019/10/5 23:00
 */
class DubboService(val project: Project) : Disposable, KLogger {
    /* key = registry */
    private var clients: MutableMap<String, DubboClient>? = concurrentMapOf()
    private var threadPoolCache: ThreadPoolCache? = ThreadPoolCache()
    private var responseListenExecutor: ExecutorService? = Executors.newSingleThreadExecutor()

    var clientListener: DubboListener? = null

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
        return getConnectState(registry) == ConnectState.Connected
    }

    fun getConnectState(registry: String): ConnectState? {
        checkDisposed()
        val client = clients!![registry] ?: return null
        return client.connectState
    }

    private fun getOrThrowClient(registry: String): DubboClient {
        val client = clients!![registry]
        if (client == null) {
            throw DubboClientException("Dubbo 客户端不存在")
        } else if (!client.isConnected()) {
            throw DubboClientException("Dubbo 客户端未连接")
        }
        return client
    }

    suspend fun connect(registry: String, username: String?, password: String?): DubboClient? {
        checkDisposed()
        var client = clients!![registry]
        if (client != null) {
            return if (client.isConnected()) {
                log.debug("already connect ", registry)
                client
            } else {
                log.debug("connecting: ", registry)
                null
            }
        }
        return suspendCancellableCoroutine { ctx ->
            checkDisposed()
            object : MyBackgroundable(project, "dubbo connect") {
                override fun doRun(indicator: ProgressIndicator) {
                    checkDisposed()
                    indicator.text = "connect $registry"
                    client = DubboClientImpl(registry, username, password, DubboListenerWrapper(object : DubboListener {
                        override fun onConnect(address: String, username: String?, password: String?) {
                            checkDisposed()
                            clients?.put(address, client!!)
                            ctx.resume(client)
                        }

                        override fun onConnectError(address: String, exception: Exception?) {
                            checkDisposed()
                            ctx.resumeWithException(DubboClientException("连接失败: ${exception?.message}", exception))
                        }
                    }))
                    clients?.put(registry, client!!)
                    client!!.connect()
                    indicator.text = "连接成功"
                }

                override fun onThrowable(error: Throwable) {
                    clients?.remove(registry)
                    ctx.resumeWithException(DubboClientException("连接失败: ${error.message}", error))
                }

                override fun onCancel() {
                    clients?.remove(registry)
                    ctx.cancel()
                }
            }.queue()
        }
    }

    suspend fun disconnect(registry: String) {
        checkDisposed()
        return suspendCoroutine { cont ->
            checkDisposed()
            val client = clients!![registry]
            client?.run {
                object : Backgroundable(project, "dubbo disconnect") {
                    override fun run(indicator: ProgressIndicator) {
                        checkDisposed()
                        clients!![registry]?.disconnect()
                        cont.resume(Unit)
                    }

                    override fun onThrowable(error: Throwable) {
                        cont.resumeWithException(error)
                    }
                }.queue()
            }
        }
    }

    /**
     * 调用此方法只返回url对应的数据，具体处理交给调用方
     */
    suspend fun getServiceInfo(registry: String, urls: List<URL>): Collection<ServiceInfo> {
        checkDisposed()
        getOrThrowClient(registry)
        return suspendCancellableCoroutine { ctx ->
            val client = getOrThrowClient(registry)
            val values = urls.map {
                val m = client.getServiceMethods(it)
                ServiceInfo(registry, it, m)
            }
            ctx.resume(values)
        }
    }

    suspend fun updateServiceInfo(registry: String, services: Collection<ServiceInfo>) {
        checkDisposed()
        getOrThrowClient(registry)
        return suspendCancellableCoroutine { ctx ->
            val client = getOrThrowClient(registry)
            services.forEach {
                it.methods = client.getServiceMethods(it.url!!)
            }
            ctx.resume(Unit)
        }
    }

    suspend fun execute(registry: String, request: DubboRequest): DubboResponse {
        checkDisposed()
        getOrThrowClient(registry)

        return suspendCancellableCoroutine { ctx ->
            object : MyBackgroundable(project, "dubbo execute") {
                override fun doRun(indicator: ProgressIndicator) {
                    checkDisposed()
                    indicator.text = "开始测试"
                    val client = getOrThrowClient(registry)
                    ctx.resume(client.invoke(request))
                }

                override fun onThrowable(error: Throwable) {
                    ctx.resumeWithException(error)
                }
            }.queue()
        }
    }

    suspend fun execute(
        registry: String,
        request: DubboRequest,
        concurrentInfo: ConcurrentInfo
    ): ReceiveChannel<DubboResponse> {
        checkDisposed()
        getOrThrowClient(registry)

        return suspendCancellableCoroutine { ctx ->
            object : MyBackgroundable(project, "开始测试") {
                private var canceled = false
                override fun doRun(indicator: ProgressIndicator) {
                    checkDisposed()
                    val client = getOrThrowClient(registry)
                    if (client !is DubboConcurrentClient) {
                        throw DubboClientException("当前连接不允许并发测试")
                    }
                    client.beforeInvoke(request)
                    val executor = threadPoolCache?.get(concurrentInfo.group)

                    val tasks = arrayListOf<Callable<DubboResponse>>()
                    for (i in 1..concurrentInfo.count) {
                        tasks.add(Callable { client.invoke(request) })
                    }
                    val futures = executor!!.invokeAll(tasks)
                    val latch = CountDownLatch(tasks.size)

                    responseListenExecutor!!.submit {
                        ctx.resume(GlobalScope.produce {
                            while (latch.count > 0) {
                                if (canceled) {
                                    cancelInvokes(futures, latch, this.channel)
                                }

                                var i = 0
                                while (i < futures.size && !canceled) {
                                    val future = futures[i]
                                    if (future.isDone || future.isCancelled) {
                                        if (future.isDone) {
                                            send(future.get())
                                        } else if (future.isCancelled) {
                                            send(DubboResponse(null, emptyMap(), CancelException("task is cancel")))
                                        }
                                        latch.countDown()
                                        futures.removeAt(i)
                                    } else {
                                        i++
                                    }
                                }
                                delay(10)
                            }
                            close()
                        })
                    }
                    latch.await(5, TimeUnit.MINUTES)
                    client.afterInvoke(request)
                }

                override fun onCancel() {
                    canceled = true
                }

                override fun onThrowable(e: Throwable) {
                    ctx.resumeWithException(e)
                }
            }.queue()
        }
    }

    private suspend fun cancelInvokes(
        futures: MutableList<Future<DubboResponse>>,
        latch: CountDownLatch,
        channel: SendChannel<DubboResponse>
    ) {
        for (i in 0 until latch.count) {
            latch.countDown()
        }
        futures.forEach {
            when {
                it.isDone -> {
                    channel.send(it.get())
                }
                it.isCancelled -> {
                    channel.send(DubboResponse(null, emptyMap(), CancelException("task is cancel")))
                }
                else -> {
                    it.cancel(true)
                }
            }
        }
        futures.clear()
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

        responseListenExecutor?.shutdownNow()
        responseListenExecutor = null
    }

    inner class DubboListenerWrapper(private val listener: DubboListener) : DubboListener {
        override fun onConnect(address: String, username: String?, password: String?) {
            listener.onConnect(address, username, password)
            clientListener?.onConnect(address, username, password)
        }

        override fun onConnectError(address: String, exception: Exception?) {
            clients?.remove(address)
            listener.onConnectError(address, exception)
            clientListener?.onConnectError(address, exception)
        }

        override fun onDisconnect(address: String) {
            clients?.remove(address)
            listener.onDisconnect(address)
            clientListener?.onDisconnect(address)
        }

        override fun onUrlChanged(address: String, urls: List<URL>, state: URLState) {
            listener.onUrlChanged(address, urls, state)
            clientListener?.onUrlChanged(address, urls, state)
        }
    }
}

private abstract class MyBackgroundable(project: Project?, title: String, canBeCancelled: Boolean = true) :
    Backgroundable(project, title, canBeCancelled) {
    override fun run(indicator: ProgressIndicator) {
        try {
            DubboUtils.replaceClassLoader()
            doRun(indicator)
        } finally {
            DubboUtils.restoreClassLoader()
        }
    }

    abstract fun doRun(indicator: ProgressIndicator)
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
        }, 5 * 60 + 1, 5 * 60 + 1, TimeUnit.SECONDS)
    }

    fun get(count: Int): ExecutorService {

        var executor = cache?.getIfPresent(count)
        if (executor == null) {
            val threadNumber = AtomicInteger(1)
            executor = Executors.newFixedThreadPool(count, ThreadFactory {
                val t = Thread(
                    null, it,
                    "dubbo-invoke-" + threadNumber.getAndIncrement(),
                    0
                )
                t.isDaemon = false
                t.priority = Thread.NORM_PRIORITY
                t
            })
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
