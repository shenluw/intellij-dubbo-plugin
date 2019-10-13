package top.shenluw.plugin.dubbo.client.impl

import com.jetbrains.rd.util.concurrentMapOf
import org.apache.dubbo.common.URL
import org.apache.dubbo.common.URLBuilder
import org.apache.dubbo.common.constants.CommonConstants
import org.apache.dubbo.common.constants.CommonConstants.*
import org.apache.dubbo.common.constants.RegistryConstants
import org.apache.dubbo.common.constants.RegistryConstants.EMPTY_PROTOCOL
import org.apache.dubbo.common.extension.ExtensionLoader
import org.apache.dubbo.common.utils.NetUtils
import org.apache.dubbo.config.ApplicationConfig
import org.apache.dubbo.config.ReferenceConfig
import org.apache.dubbo.config.RegistryConfig
import org.apache.dubbo.config.builders.ApplicationBuilder
import org.apache.dubbo.config.builders.ReferenceBuilder
import org.apache.dubbo.config.builders.RegistryBuilder
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
import top.shenluw.plugin.dubbo.utils.Collections
import top.shenluw.plugin.dubbo.utils.KLogger
import java.util.Collections.unmodifiableList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


class DubboClientImpl(override var listener: DubboListener? = null) : AbstractDubboClient(listener), KLogger,
    DubboConcurrentClient, NotifyListener {
    private val DUBBO_NAME = "DubboPlugin"

    private val SUBSCRIBE = URL(
        DUBBO_NAME, NetUtils.getLocalHost(),
        0, "",
        CommonConstants.INTERFACE_KEY, ANY_VALUE,
        CommonConstants.GROUP_KEY, ANY_VALUE,
        CommonConstants.VERSION_KEY, ANY_VALUE,
        CommonConstants.CLASSIFIER_KEY, ANY_VALUE,
//        RegistryConstants.CATEGORY_KEY, RegistryConstants.PROVIDERS_CATEGORY + ","
//                + RegistryConstants.CONSUMERS_CATEGORY + ","
//                + RegistryConstants.ROUTERS_CATEGORY + ","
//                + RegistryConstants.CONFIGURATORS_CATEGORY,
        RegistryConstants.CATEGORY_KEY, RegistryConstants.PROVIDERS_CATEGORY,
        CommonConstants.ENABLED_KEY, ANY_VALUE,
        org.apache.dubbo.remoting.Constants.CHECK_KEY, false.toString()
    )

    private var applicationConfig: ApplicationConfig? = null
    private var registryConfig: RegistryConfig? = null

    private var registryURL: URL? = null
    private var registry: Registry? = null

    private var dubboTelnetClients = concurrentMapOf<String, DubboClient>()

    private var methodInfoCache = concurrentMapOf<String, List<MethodInfo>>()

    /**
     * key = url.interfaceName
     */
    private var registryCache: MutableMap<String, MutableList<URL>> = hashMapOf()

    /* 保证url变化通知必需在连接状态成功=true之后 */
    private var delayNotifyUrls = arrayListOf<List<URL>>()

    override fun prepareConnect(address: String, username: String?, password: String?) {
        registryURL = URL.valueOf(address)
            .setUsername(username)
            .setPassword(password)

        registryConfig = RegistryBuilder()
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
        val registryFactory = ExtensionLoader.getExtensionLoader(RegistryFactory::class.java).adaptiveExtension
        registry = registryFactory.getRegistry(registryURL)
        registry?.subscribe(SUBSCRIBE, this)
    }

    override fun connect(address: String, username: String?, password: String?) {
        super.connect(address, username, password)
        if (delayNotifyUrls.isNotEmpty()) {
            delayNotifyUrls.forEach {
                notify(it)
            }
            delayNotifyUrls.clear()
        }
    }

    override fun doDisconnect() {
        this.registry?.run {
            if (this.isAvailable) {
                this.destroy()

                // 通过反射删除factory中的缓存
                // 非常无语，调用destroy不会删除缓存，导致重新建立的出现异常

                val field = AbstractRegistryFactory::class.java.getDeclaredField("REGISTRIES")
                field.isAccessible = true
                val cache = field.get(null) as MutableMap<String, Registry>

                val key = URLBuilder.from(registryURL)
                    .setPath(RegistryService::class.java.name)
                    .addParameter(INTERFACE_KEY, RegistryService::class.java.name)
                    .removeParameters(EXPORT_KEY, REFER_KEY)
                    .build()
                    .toServiceStringWithoutResolving()
                cache.remove(key)

            }
        }
        this.registry = null
        registryCache.clear()
        delayNotifyUrls.clear()
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

    override fun getServiceMethods(url: URL): List<MethodInfo> {
        val key = url.toFullString()
        val methods = methodInfoCache[key]
        if (methods != null) {
            return methods
        }

        val telnetKey = "${url.host}:${url.port}"
        var client = dubboTelnetClients[telnetKey]
        if (client == null) {
            client = DubboTelnetClientImpl()
            client.listener = object : DubboListener {
                override fun onConnectError(address: String, exception: Exception?) {
                    dubboTelnetClients.remove(telnetKey)
                }

                override fun onDisconnect(address: String) {
                    dubboTelnetClients.remove(telnetKey)
                }
            }
            client.connect(telnetKey)
            dubboTelnetClients[telnetKey] = client
        }
        if (!client.connected) {
            // 如果没有连接，阻塞3秒 再次判断状态
            CountDownLatch(1).await(3, TimeUnit.SECONDS)
            if (!client.connected) {
                throw DubboClientException("telnet can not connect")
            }
        }
        return client.getServiceMethods(url)
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
        if (!connected) {
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

    @Synchronized
    override fun notify(urls: List<URL>?) {
        if (urls.isNullOrEmpty()) {
            return
        }
        if (!connected) {
            delayNotifyUrls.add(urls)
            return
        }
        // 建立一个当前缓存的副本 修改操作在当前副本上执行  最后复制到缓存中去
        val tmpCache: MutableMap<String, MutableList<URL>> = hashMapOf()
        registryCache.forEach { (t, u) ->
            tmpCache[t] = u.toMutableList()
        }

        val flags = arrayListOf<String>()
        urls.forEach {
            val url = it
            if (EMPTY_PROTOCOL.equals(url.protocol, true)) {
                // 收到empty便认为没有可以的接口，直接移除
                if (tmpCache.isEmpty()) {
                    return
                }
                val interfaceName = url.path
                if (!interfaceName.isNullOrBlank()) {
                    tmpCache.remove(interfaceName)
                }
            } else {
                val interfaceName = url.serviceInterface
                if (interfaceName.isNullOrBlank()) {
                    return
                }
                // 清除缓存
                if (!flags.contains(interfaceName)) {
                    tmpCache.remove(interfaceName)
                }
                var list = tmpCache[interfaceName]
                if (list == null) {
                    list = arrayListOf()
                    tmpCache[interfaceName] = list
                }
                list.add(url)
            }
        }

        // 判断接口是否存在变化
        if (!Collections.isEqualMap(registryCache, tmpCache)) {
            // 移除无效的telnet数据
            val allUrls = tmpCache.values.flatten()

            val iterator = methodInfoCache.iterator()
            while (iterator.hasNext()) {
                // key = url.serviceKey
                val key = iterator.next().key
                if (allUrls.find { it.serviceKey == key } == null) {
                    iterator.remove()
                }
            }

            // 更新缓存
            registryCache = tmpCache
            this.listener?.onUrlChanged(address!!, allUrls)
        }
    }

}