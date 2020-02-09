package top.shenluw.plugin.dubbo.client

import org.apache.dubbo.common.URL
import top.shenluw.plugin.dubbo.DubboException
import top.shenluw.plugin.dubbo.MethodInfo
import java.io.Serializable

/**
 * @author Shenluw
 * created：2019/9/28 11:13
 */
interface DubboClient {

    var connectState: ConnectState

    var listener: DubboListener?

    val address: String

    var username: String?

    var password: String?

    fun connect(): Boolean

    fun disconnect()

    fun getUrls(): List<URL>

    fun getType(): RegistryType?

    fun invoke(request: DubboRequest): DubboResponse

    /**
     * @throws DubboClientException 接口不存在或者掉线时抛出
     */
    fun getServiceMethods(url: URL): List<MethodInfo>

    fun isConnected(): Boolean {
        return connectState == ConnectState.Connected
    }

    fun isConnecting(): Boolean {
        return connectState == ConnectState.Connecting
    }

}

enum class ConnectState {
    Idle, Connected, Connecting, Error
}

/**
 * 支持并发调用的客户端
 */
interface DubboConcurrentClient : DubboClient {
    fun beforeInvoke(request: DubboRequest)
    fun afterInvoke(request: DubboRequest)
}

enum class RegistryType(val protocol: String) {
    Dubbo("dubbo"),
    Consul("consul"),
    Zookeeper("zookeeper"),
    Redis("redis"),
    Multicast("multicast"),
    Nacos("nacos");

    companion object {
        fun get(protocol: String): RegistryType? {
            return values().find { it.protocol == protocol }
        }
    }
}

class DubboClientException : DubboException {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)
}

class CancelException : DubboException {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
}

interface DubboListener {
    fun onConnect(address: String, username: String?, password: String?) {}

    fun onConnectError(address: String, exception: Exception?) {}

    fun onDisconnect(address: String) {}
    /**
     * @param urls 如果为空，表示当前没有能提供的服务
     * @param state url 变化状态
     */
    fun onUrlChanged(address: String, urls: List<URL>, state: URLState) {}
}

enum class URLState {
    /* 新加入的接口 */
    ADD,
    /* 接口数量或者信息发生变化 */
    UPDATE,
    /* 接口被移除 */
    REMOVE
}

data class DubboRequest(
    val appName: String,
    val interfaceName: String,
    val method: String,
    val params: Array<DubboParameter>,
    val version: String,
    /* 指定调用机器的接口地址 */
    val url: String? = null,
    val group: String? = null
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DubboRequest

        if (appName != other.appName) return false
        if (interfaceName != other.interfaceName) return false
        if (method != other.method) return false
        if (!params.contentEquals(other.params)) return false
        if (version != other.version) return false
        if (url != other.url) return false
        if (group != other.group) return false

        return true
    }

    override fun hashCode(): Int {
        var result = appName.hashCode()
        result = 31 * result + interfaceName.hashCode()
        result = 31 * result + method.hashCode()
        result = 31 * result + params.contentHashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + (url?.hashCode() ?: 0)
        result = 31 * result + (group?.hashCode() ?: 0)
        return result
    }
}

data class DubboParameter(val type: String, val value: Any?) : Serializable

data class DubboResponse(
    val data: Any?,
    val attachments: Map<String, String>,
    val exception: Exception? = null
)