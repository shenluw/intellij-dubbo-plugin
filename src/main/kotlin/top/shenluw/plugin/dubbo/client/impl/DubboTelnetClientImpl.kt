package top.shenluw.plugin.dubbo.client.impl

import org.apache.commons.net.telnet.TelnetClient
import org.apache.dubbo.common.URL
import top.shenluw.luss.common.log.KSlf4jLogger
import top.shenluw.plugin.dubbo.Gson
import top.shenluw.plugin.dubbo.client.*
import top.shenluw.plugin.dubbo.utils.Dubbo
import top.shenluw.plugin.dubbo.utils.Texts
import java.io.PrintWriter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * @author Shenluw
 * created：2019/10/3 23:18
 */
class DubboTelnetClientImpl : AbstractDubboClient(), KSlf4jLogger {

    private var telnet: TelnetClient? = null

    private var pw: PrintWriter? = null

    private var close = false
    /* 调用阻塞队列 */
    private var cmdQueue: BlockingQueue<String> = ArrayBlockingQueue(1)
    /* 回调结果阻塞队列 */
    private var cmdGetQueue: BlockingQueue<String> = ArrayBlockingQueue(1)

    private var cacheMethods = hashMapOf<String, List<DubboMethodInfo>>()

    override fun doConnect() {
        val telnet = TelnetClient()
        this.telnet = telnet

        val url = URL.valueOf(address)

        telnet.connect(url.host, url.port)
        pw = PrintWriter(telnet.outputStream, true)

        thread(true) {
            read()
        }
    }

    @Synchronized
    override fun disconnect() {
        super.disconnect()
    }

    override fun doDisconnect() {
        try {
            close = true
            pw?.close()
            telnet?.disconnect()
        } catch (e: Exception) {
            log.warn("disconnect telnet error", e)
        } finally {
            this.pw = null
            this.telnet = null
        }
    }

    override fun getUrls(): List<URL> {
        // 信息不足，不反悔数据
        return emptyList()
    }

    override fun getType(): RegistryType? {
        return null
    }

    override fun getServiceMethods(url: URL): List<DubboMethodInfo> {
        if (!connected) {
            throw DubboClientException("telnet not connect")
        }
        val interfaceName = url.serviceInterface
        var methods = cacheMethods[interfaceName]
        if (methods != null) {
            return methods
        }
        val response = sendCommand("ls -l $interfaceName")
            ?: throw DubboClientException("数据获取超时")
        methods = Dubbo.parseTelnetMethods(response)
        cacheMethods[interfaceName] = methods
        return methods
    }


    override fun invoke(request: DubboRequest): DubboRespone {
        if (!connected) {
            return DubboRespone(null, emptyMap(), DubboClientException("client not connect"))
        }

        val interfaceName = request.interfaceName
        val method = request.method
        val params = request.params

        val cmd = StringBuilder("invoke ")
        cmd.append(interfaceName).append('.').append(method)
            .append('(')

        val iterator = params.iterator()
        while (iterator.hasNext()) {

            val p = iterator.next()
            val value = p.value
            if (value is String) {
                cmd.append("\"")
                cmd.append(Texts.escape(value))
                cmd.append("\"")
            } else {
                // 全部转成json 字符串
                cmd.append(Gson.toJson(value))
            }
            if (iterator.hasNext()) {
                cmd.append(',')
            }
        }

        cmd.append(')')

        return try {
            val response = sendCommand(cmd.toString())
            if (response == null) {
                DubboRespone(null, emptyMap(), DubboClientException("telnet 调用超时"))
            } else {
                DubboRespone(Dubbo.parseTelnetInvokeResponse(response), emptyMap())
            }
        } catch (e: Exception) {
            DubboRespone(null, emptyMap(), e)
        }
    }

    private fun sendCommand(cmd: String): String? {
        if (connected) {
            throw DubboClientException("telnet not connect")
        }
        try {
            log.debug("send cmd: {}", cmd)
            cmdQueue.put(cmd)
            pw?.println(cmd)
            return cmdGetQueue.poll(5000, TimeUnit.SECONDS)
        } catch (e: Exception) {
            throw DubboClientException("telnet cmd 执行错误", e)
        }
    }

    /* 读取telnet结果 */
    private fun read() {
        val inputStream = telnet?.inputStream!!

        val queue = cmdQueue

        val buff = ByteArray(1024)
        var len: Int

        val sb = StringBuilder(128)

        var cmd: String? = null
        val endFlag = "dubbo>"
        try {
            do {
                len = inputStream.read(buff)
                if (len != -1) {
                    if (cmd == null) {
                        cmd = queue.peek()
                    }
                    sb.append(String(buff, 0, len))
                    // 表示一次读取结束
                    if (sb.endsWith(endFlag)) {
                        queue.poll()
                        cmdGetQueue.put(sb.substring(0, sb.length - endFlag.length))
                        cmd = null
                        sb.clear()
                    }
                }
            } while (len != -1 && !close)
        } catch (e: Exception) {
            log.error("telnet read error", e)
        }
        disconnect()
    }

}