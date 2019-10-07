package top.shenluw.plugin.dubbo

import java.io.Serializable

/**
 * @author Shenluw
 * created：2019/10/6 22:01
 */
/* 一台机器下的服务信息 */
data class AppInfo(
    val name: String,
    val registry: String?,
    val address: String,
    var services: MutableList<ServiceInfo>?
) : Serializable

data class ServiceInfo(
    val interfaceName: String,
    val version: String,
    val group: String?,
    /* 冗余字段 同 app address */
    val address: String,
    var methods: MutableList<MethodInfo>?
) : Serializable

data class MethodInfo(val method: String, val argumentTypes: Array<String>, val returnType: String) {
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

data class InvokeHistory(val key: String, val value: String)