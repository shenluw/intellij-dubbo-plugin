package top.shenluw.plugin.dubbo.client.impl

import com.google.common.base.Stopwatch
import com.jetbrains.rd.util.concurrentMapOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.apache.dubbo.common.URL
import org.apache.dubbo.common.URLBuilder
import org.apache.dubbo.common.constants.CommonConstants.*
import org.apache.dubbo.common.constants.RegistryConstants
import org.apache.dubbo.common.constants.RegistryConstants.EMPTY_PROTOCOL
import org.apache.dubbo.common.extension.ExtensionLoader
import org.apache.dubbo.common.utils.NetUtils
import org.apache.dubbo.config.ApplicationConfig
import org.apache.dubbo.config.ReferenceConfig
import org.apache.dubbo.config.bootstrap.builders.ApplicationBuilder
import org.apache.dubbo.config.bootstrap.builders.ReferenceBuilder
import org.apache.dubbo.config.bootstrap.builders.RegistryBuilder
import org.apache.dubbo.registry.NotifyListener
import org.apache.dubbo.registry.Registry
import org.apache.dubbo.registry.RegistryFactory
import org.apache.dubbo.registry.RegistryService
import org.apache.dubbo.registry.support.AbstractRegistryFactory
import org.apache.dubbo.rpc.RpcContext
import org.apache.dubbo.rpc.cluster.Constants.EXPORT_KEY
import org.apache.dubbo.rpc.cluster.Constants.REFER_KEY
import org.apache.dubbo.rpc.service.GenericService
import top.shenluw.plugin.dubbo.MethodInfo
import top.shenluw.plugin.dubbo.client.*
import top.shenluw.plugin.dubbo.utils.KLogger
import java.util.Collections.unmodifiableList
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DubboClientImpl(
    address: String,
    username: String? = null,
    password: String? = null,
    listener: DubboListener? = null
) : AbstractDubboClient(address, username, password, listener), KLogger,
    DubboConcurrentClient, NotifyListener {

    private val DUBBO_NAME = "DubboPlugin"
    private val DEFAULT_TIMEOUT_MS = 30 * 1000L

    private val SUBSCRIBE = URL(
        DUBBO_NAME, NetUtils.getLocalHost(),
        0, "",
        INTERFACE_KEY, ANY_VALUE,
        GROUP_KEY, ANY_VALUE,
        VERSION_KEY, ANY_VALUE,
        CLASSIFIER_KEY, ANY_VALUE,
//        RegistryConstants.CATEGORY_KEY, RegistryConstants.PROVIDERS_CATEGORY + ","
//                + RegistryConstants.CONSUMERS_CATEGORY + ","
//                + RegistryConstants.ROUTERS_CATEGORY + ","
//                + RegistryConstants.CONFIGURATORS_CATEGORY,
        RegistryConstants.CATEGORY_KEY, RegistryConstants.PROVIDERS_CATEGORY,
        ENABLED_KEY, ANY_VALUE,
        org.apache.dubbo.remoting.Constants.CHECK_KEY, false.toString()
    )

    private var applicationConfig: ApplicationConfig? = null

    private var registryURL: URL? = null
    private var registry: Registry? = null

    private var dubboTelnetClients = concurrentMapOf<String, DubboClient>()

    /**
     * key = application + interfaceName
     */
    private var registryCache: MutableMap<String, MutableList<URL>> = hashMapOf()

    private fun prepareConnect() {
        registryURL = URL.valueOf(address)
            .setUsername(username)
            .setPassword(password)

        if (!registryURL!!.hasParameter(TIMEOUT_KEY)) {
            registryURL = registryURL?.addParameter(TIMEOUT_KEY, DEFAULT_TIMEOUT_MS)
        }
        val registryConfig = RegistryBuilder()
            .address(registryURL?.address)
            .protocol(registryURL?.protocol)
            .port(registryURL?.port)
            .register(false)
            .username(username)
            .password(password)
            .build()

        applicationConfig = ApplicationBuilder()
            .name(DUBBO_NAME)
            .addRegistry(registryConfig)
            .build()
    }

    override fun doConnect() {
        prepareConnect()

        val registryFactory = ExtensionLoader.getExtensionLoader(RegistryFactory::class.java).adaptiveExtension
        registry = registryFactory.getRegistry(registryURL)
        registry?.takeUnless { it.isAvailable }?.run {
            destroyRegistry()
            throw DubboClientException("connect error")
        }

        thread(true) {
            val stopwatch = Stopwatch.createStarted()
            try {
                log.debug("subscribe start")
                registry?.subscribe(SUBSCRIBE, this)
            } catch (e: Exception) {
                log.warn("subscribe error", e)
            } finally {
                log.debug("subscribe end ", stopwatch.stop().elapsed(TimeUnit.SECONDS))
            }
        }
    }

    private fun destroyRegistry() {
        this.registry?.takeIf { it.isAvailable }?.run {
            try {
                this.destroy()
            } catch (e: Exception) {
                log.warn("destroy error", e)
            }

            cleanDubboCache()
        }
        this.registry = null
    }

    override fun doDisconnect() {
        destroyRegistry()
        registryCache.clear()
    }

    override fun onConnectError(msg: String?, e: Exception) {
        doDisconnect()
        super.onConnectError(msg, e)
    }

    /**
     * 通过反射删除factory中的缓存
     * 非常无语，调用destroy不会删除缓存，导致重新建立的出现异常
     */
    private fun cleanDubboCache() {
        val field = AbstractRegistryFactory::class.java.getDeclaredField("REGISTRIES")
        field.isAccessible = true
        val cache = field.get(null) as MutableMap<*, *>

        val key = URLBuilder.from(registryURL)
            .setPath(RegistryService::class.java.name)
            .addParameter(INTERFACE_KEY, RegistryService::class.java.name)
            .removeParameters(EXPORT_KEY, REFER_KEY)
            .build()
            .toServiceStringWithoutResolving()
        cache.remove(key)
    }

    override fun getUrls(): List<URL> {
        return unmodifiableList(registryCache.values.flatten())
    }

    override fun getType(): RegistryType? {
        val protocol = this.registryURL?.protocol
        if (protocol != null) {
            return RegistryType.get(protocol)
        }
        return null
    }

    private suspend fun getTelnetClient(telnetAddress: String): DubboClient? {
        var client = dubboTelnetClients[telnetAddress]
        if (client != null) {
            return client
        }
        client = DubboTelnetClientImpl(telnetAddress)
        dubboTelnetClients[telnetAddress] = client
        return suspendCancellableCoroutine { ctx ->
            client.listener = object : DubboListener {
                override fun onConnectError(address: String, exception: Exception?) {
                    dubboTelnetClients.remove(telnetAddress)
                    ctx.resumeWithException(exception!!)
                }

                override fun onDisconnect(address: String) {
                    dubboTelnetClients.remove(telnetAddress)
                }

                override fun onConnect(address: String, username: String?, password: String?) {
                    ctx.resume(client)
                }
            }
            log.debug("connect telnet $telnetAddress")
            client.connect()
        }
    }

    @Synchronized
    override fun getServiceMethods(url: URL): List<MethodInfo> {
        val key = url.toFullString()

        if (registryCache.values.flatten().find { it.toFullString() == key } == null) {
            throw DubboClientException("url already removed")
        }

        return runBlocking {
            val client = getTelnetClient("${url.host}:${url.port}")
                ?: throw DubboClientException("telnet can not connect")
            if (!client.isConnected()) {
                var index = 0
                // 如果没有连接，阻塞3秒 再次判断状态
                while (index++ < 3) {
                    delay(1000)
                    if (client.isConnected()) {
                        break
                    }
                }
                if (!client.isConnected()) {
                    throw DubboClientException("telnet can not connect")
                }
            }
            client.getServiceMethods(url)
        }
    }

    private fun createReferenceConfig(request: DubboRequest): ReferenceConfig<GenericService> {
        val builder = ReferenceBuilder<GenericService>()
            .generic(true)
            .application(applicationConfig)
            .interfaceName(request.interfaceName)
            .version(request.version)
            .group(request.group)
        request.url?.run { builder.url(URL.valueOf(this).addParameter(VERSION_KEY, request.version).toFullString()) }
        return builder.build()
    }

    override fun invoke(request: DubboRequest): DubboResponse {
        if (!isConnected()) {
            return DubboResponse(null, emptyMap(), DubboClientException("client not connect"))
        }
        var result: Any? = null
        var ex: Exception? = null
        val referenceConfig: ReferenceConfig<GenericService> = if (refConfigCache == null) {
            createReferenceConfig(request)
        } else {
            refConfigCache!!
        }
        try {
            val service = referenceConfig.get()
            val params = request.params

            val types = params.map { it.type }.toTypedArray()
            val values = params.map { it.value }.toTypedArray()
            result = service.`$invoke`(request.method, types, values)
        } catch (e: Exception) {
            ex = e
        } finally {
            if (refConfigCache == null) {
                referenceConfig.destroy()
            }
        }
        return DubboResponse(result, RpcContext.getContext().attachments, ex)
    }


    @Volatile
    private var refConfigCache: ReferenceConfig<GenericService>? = null

    override fun beforeInvoke(request: DubboRequest) {
        refConfigCache = createReferenceConfig(request)
        refConfigCache?.get()
    }

    override fun afterInvoke(request: DubboRequest) {
        refConfigCache?.destroy()
        refConfigCache = null
    }

    /**
     * 根据application + interface 分组
     */
    private fun group(urls: List<URL>): Map<String, List<URL>> {
        return urls.groupBy {
            val app = it.getParameter(APPLICATION_KEY, "")
            "$app-${it.serviceInterface}"
        }
    }

    @Synchronized
    override fun notify(urls: List<URL>?) {
        // Note: 2020/1/28 dubbo 通知变化的时候会同一个接口会全量通知
        log.debug("dubbo notify: ", urls?.size)
        if (urls.isNullOrEmpty()) {
            return
        }

        group(urls).forEach { (key, us) ->
            var cached = registryCache.getOrDefault(key, null)
            val isAdd = cached == null

            us.forEach { url ->
                if (EMPTY_PROTOCOL.equals(url.protocol, true)) {
                    // 收到empty便认为没有可用的接口，直接移除
                    val interfaceName = url.path

                    val iter = registryCache.iterator()

                    while (iter.hasNext()) {
                        val entry = iter.next()
                        val tmp = entry.value
                        val n = tmp.filter { it.serviceInterface != interfaceName }
                        if (n.isEmpty()) {
                            iter.remove()
                            listener?.onUrlChanged(address, tmp, URLState.REMOVE)
                        } else {
                            entry.setValue(n.toMutableList())
                            listener?.onUrlChanged(address, n, URLState.UPDATE)
                        }
                        if (key == entry.key) {
                            cached = registryCache.getOrDefault(key, null)
                        }
                    }
                } else {
                    if (cached == null) {
                        cached = arrayListOf()
                        registryCache[key] = cached!!
                    }
                    cached?.add(url)
                }
            }

            if (cached != null && us.isNotEmpty()) {
                listener?.onUrlChanged(address, cached!!, if (isAdd) URLState.ADD else URLState.UPDATE)
            }
        }
    }

}