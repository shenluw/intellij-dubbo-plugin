package top.shenluw.plugin.dubbo.client.impl

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
    private var connecting = false
    @Volatile
    override var connected = false

    override fun connect() {
        if (connecting || connected) {
            return
        }
        connecting = true
        prepareConnect()

        try {
            doConnect()
            connected = true
            listener?.onConnect(address, username, password)
        } catch (e: Exception) {
            onConnectError(null, e)
        } finally {
            connecting = false
        }
    }

    protected open fun prepareConnect() {}

    protected open fun onConnectError(msg: String?, e: Exception) {
        log.warn("connect fail", e)
        listener?.onConnectError(address!!, e)
    }

    protected abstract fun doConnect()

    protected abstract fun doDisconnect()

    override fun disconnect() {
        if (!connected || connecting) return
        doDisconnect()
        this.connecting = false
        this.connected = false
        listener?.onDisconnect(address)
    }

    override fun refresh() {
        if (this.connecting) {
            return
        }
        if (!connected) {
            return
        }
        disconnect()

        try {
            connecting = true
            doConnect()
            connected = true
            listener?.onConnect(address, username, password)
        } catch (e: Exception) {
            onConnectError(null, e)
        } finally {
            connecting = false
        }
    }
}