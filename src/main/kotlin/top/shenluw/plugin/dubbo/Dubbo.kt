package top.shenluw.plugin.dubbo

import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import org.apache.dubbo.common.URL
import org.apache.dubbo.common.constants.CommonConstants
import top.shenluw.plugin.dubbo.utils.DubboUtils
import java.io.Serializable

/**
 * @author Shenluw
 * created：2019/10/6 22:01
 */

data class ServiceInfo(
    /* 注册中心地址 */
    val registry: String?,
    val appName: String,
    val interfaceName: String,
    val version: String,
    val group: String?,
    /* 具体主机ip */
    val address: String,
    val protocol: String,
    var methods: List<MethodInfo>?,
    val url: URL?
) : Serializable {
    constructor(registry: String?, url: URL, methods: List<MethodInfo>? = null) : this(
        registry, url.getParameter(CommonConstants.APPLICATION_KEY, ""), url.serviceInterface,
        url.getParameter(CommonConstants.VERSION_KEY, ""),
        url.getParameter(CommonConstants.GROUP_KEY, ""),
        url.address, url.protocol, methods, url
    )
}

data class MethodInfo(val method: String, val argumentTypes: Array<String>, val returnType: String) : Serializable {

    val key: String by lazy {
        DubboUtils.genMethodKey(method, argumentTypes)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MethodInfo

        if (method != other.method) return false
        if (!argumentTypes.contentEquals(other.argumentTypes)) return false
        if (returnType != other.returnType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = method.hashCode()
        result = 31 * result + argumentTypes.contentHashCode()
        result = 31 * result + returnType.hashCode()
        return result
    }
}

@Tag
data class InvokeHistory(
    @Attribute val key: String? = null,
    @Attribute val request: String? = null,
    @Attribute val response: String? = null
) :
    Serializable

data class RegistryInfo(var address: String? = null, var username: String? = null, var password: String? = null) :
    Serializable

data class ConcurrentInfo(val count: Int, val group: Int)

val NoConcurrentInfo = ConcurrentInfo(1, 1)