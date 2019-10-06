package top.shenluw.plugin.dubbo.client.impl

import top.shenluw.plugin.dubbo.client.DubboClient
import top.shenluw.plugin.dubbo.client.DubboListener

/**
 * @author Shenluw
 * created：2019/10/3 23:27
 */
abstract class AbstractDubboClient(override var listener: DubboListener? = null) : DubboClient {
    @Volatile
    private var connecting = false
    @Volatile
    override var connected = false

    override var address: String? = null

    override fun connect(address: String, username: String?, password: String?) {
        if (connecting || connected) {
            return
        }
        connecting = true
        this.address = address
        prepareConnect(address, username, password)

        try {
            doConnect()
            connected = true
            listener?.onConnect(address)
        } catch (e: Exception) {
            onConnectError(null, e)
        } finally {
            connecting = false
        }
    }

    protected open fun prepareConnect(address: String, username: String?, password: String?) {}

    protected open fun onConnectError(msg: String?, e: Exception) {
        listener?.onConnectError(address!!, e)
    }

    protected abstract fun doConnect()

    protected abstract fun doDisconnect()

    override fun disconnect() {
        if (!connected || connecting) return
        doDisconnect()
        this.connecting = false
        this.connected = false
        listener?.onDisconnect(address!!)
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
            listener?.onConnect(address!!)
        } catch (e: Exception) {
            onConnectError(null, e)
        } finally {
            connecting = false
        }
    }
}