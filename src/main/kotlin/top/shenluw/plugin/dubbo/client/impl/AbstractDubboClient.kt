package top.shenluw.plugin.dubbo.client.impl

import top.shenluw.plugin.dubbo.client.ConnectState
import top.shenluw.plugin.dubbo.client.DubboClient
import top.shenluw.plugin.dubbo.client.DubboListener
import top.shenluw.plugin.dubbo.utils.KLogger

/**
 * @author Shenluw
 * created：2019/10/3 23:27
 */
abstract class AbstractDubboClient(
    override val address: String,
    override var username: String? = null,
    override var password: String? = null,
    override var listener: DubboListener? = null
) :
    DubboClient,
    KLogger {

    @Volatile
    override var connectState = ConnectState.Idle

    override fun connect(): Boolean {
        if (connectState == ConnectState.Connecting) {
            return false
        }
        if (isConnected()) {
            return true
        }
        connectState = ConnectState.Connecting

        return try {
            doConnect()
            connectState = ConnectState.Connected
            listener?.onConnect(address, username, password)
            true
        } catch (e: Exception) {
            onConnectError(null, e)
            connectState = ConnectState.Idle
            false
        }
    }

    protected open fun onConnectError(msg: String?, e: Exception) {
        log.warn("connect fail", e)
        listener?.onConnectError(address, e)
    }

    protected abstract fun doConnect()

    protected abstract fun doDisconnect()

    override fun disconnect() {
        if (!isConnected() || isConnecting()) return
        doDisconnect()
        this.connectState = ConnectState.Idle
        listener?.onDisconnect(address)
    }
}