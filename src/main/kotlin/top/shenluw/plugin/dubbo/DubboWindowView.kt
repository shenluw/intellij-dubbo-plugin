package top.shenluw.plugin.dubbo

import com.intellij.notification.NotificationType.WARNING
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindow
import kotlinx.coroutines.runBlocking
import org.apache.dubbo.common.URL
import top.shenluw.plugin.dubbo.Constants.DUBBO_TEMP_RESPONSE_PREFIX
import top.shenluw.plugin.dubbo.client.DubboListener
import top.shenluw.plugin.dubbo.client.DubboParameter
import top.shenluw.plugin.dubbo.client.DubboRequest
import top.shenluw.plugin.dubbo.client.DubboResponse
import top.shenluw.plugin.dubbo.parameter.YamlParameterParser
import top.shenluw.plugin.dubbo.ui.ConnectState
import top.shenluw.plugin.dubbo.ui.DubboWindowPanel
import top.shenluw.plugin.dubbo.utils.DubboUtils
import top.shenluw.plugin.dubbo.utils.KLogger
import top.shenluw.plugin.dubbo.utils.TempFileManager
import java.text.SimpleDateFormat

/**
 * @author Shenluw
 * created：2019/10/1 15:04
 */
class DubboWindowView : KLogger, DubboListener, Disposable {

    private var project: Project? = null

    private var dubboWindowPanel: DubboWindowPanel? = null

    private var dubboService: DubboService? = null
    private var disposed = false

    fun install(project: Project, toolWindow: ToolWindow) {
        Application.assertIsDispatchThread()
        this.project = project
        this.dubboService = DubboService(project)
        val contentManager = toolWindow.contentManager
        val panel = DubboWindowPanel().apply {
            setOnConnectClickListener { connectOrDisconnect() }
            setOnRegistryChangedListener { onRegistryChanged(it) }
            setOnRefreshClickListener { refreshRegistry() }
            setOnApplicationChangedListener { onApplicationChanged(it) }
            setOnServiceChangedListener { onInterfaceChanged(it) }
            setOnMethodChangedListener { onMethodChanged(it) }
            setOnExecClickListener { onExec(NoConcurrentInfo) }
            setOnConcurrentExecClickListener {
                onExec(ConcurrentInfo(getConcurrentCount(), getConcurrentGroup()))
            }
            setOnOpenResponseEditorListener { openResponseEditor() }
        }

        val content = contentManager.factory.createContent(panel, null, false)
        contentManager.addContent(content)
        contentManager.setSelectedContent(content)

        this.dubboWindowPanel = panel
        initUI()

        panel.isVisible = true

        Disposer.register(project, this)
    }

    override fun dispose() {
        dubboService?.dispose()
        dubboService = null
        dubboWindowPanel = null
        project = null
        TempFileManager.dispose()
        disposed = true
    }

    private fun initUI() {
        dubboWindowPanel?.initUI(project!!, UISetting.getInstance(project!!))
        val storage = DubboStorage.getInstance(project!!)
        val registries = storage.registries
        if (registries.isNotEmpty()) {
            dubboWindowPanel?.setRegistries(registries.map { it.key })
        }
        dubboWindowPanel?.updateRegistrySelected(storage.lastConnectRegistry)
    }

    private fun openResponseEditor() {
        val ui = dubboWindowPanel ?: return

        val fileEditorManagerEx = FileEditorManagerEx.getInstance(project!!)

        val tmpFile = TempFileManager.createTempFileIfNotExists(
            DUBBO_TEMP_RESPONSE_PREFIX,
            DubboUtils.getExtension(ui.getOpenResponseType())
        ) ?: return
        val resp = ui.getResponseText()
        if (resp.isNullOrBlank()) {
            return
        }
        val previewVf = LocalFileSystem.getInstance().findFileByIoFile(tmpFile)
        previewVf?.charset = Constants.DEFAULT_CHARSET
        if (previewVf != null) {
            Application.runWriteAction {
                VfsUtil.saveText(previewVf, resp)
                fileEditorManagerEx.openFile(previewVf, false)
            }
        }
    }

    private fun onExec(concurrentInfo: ConcurrentInfo) {
        val dubboService = this.dubboService ?: return
        val ui = dubboWindowPanel ?: return
        val registry = ui.getSelectedRegistry() ?: return

        if (!dubboService.isConnected(registry)) {
            notifyMsg("dubbo", "dubbo 客户端未连接")
            return
        }

        val app = ui.getSelectedApplication()
        val interfaceName = ui.getSelectedService()
        val methodKey = ui.getSelectedMethod()
        val version = ui.getSelectVersion()

        if (app.isNullOrBlank() || interfaceName.isNullOrBlank() || methodKey.isNullOrBlank() || version.isNullOrBlank()) {
            notifyMsg("dubbo", "接口必须参数不全", WARNING)
            return
        }

        val group = ui.getSelectedGroup()
        val server = ui.getSelectedServer()
        val paramsText = ui.getParamsText()

        val methodInfos = DubboUtils.getMethodInfo(project!!, registry, app, interfaceName, methodKey, version)

        if (methodInfos.isEmpty()) {
            notifyMsg("dubbo", "接口信息出现异常", WARNING)
            return
        }
        val methodName = DubboUtils.getMethodName(methodKey)

        val methodInfo = methodInfos[0]
        val argumentTypes = methodInfo.argumentTypes
        var params: Array<DubboParameter> = if (argumentTypes.isNullOrEmpty()) {
            emptyArray()
        } else {
            if (paramsText.isNullOrBlank()) {
                notifyMsg("dubbo", "接口调用参数缺失", WARNING)
                return
            }
            YamlParameterParser().parse(paramsText, argumentTypes)
        }
        ui.setPanelEnableState(false)

        val callback = if (concurrentInfo === NoConcurrentInfo) {
            OnceExecCallback()
        } else {
            ConcurrentExecCallback()
        }
        val request = DubboRequest(app, interfaceName, methodName, params, version, server, group)
        dubboService.execute(registry, request, concurrentInfo, callback)
    }


    private inner class ConcurrentExecCallback : TaskCallback<DubboResponse> {
        private val sb = StringBuilder()
        override fun onSuccess(respone: DubboResponse?) {
            if (!disposed) {
                sb.append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss sss").format(System.currentTimeMillis()))
                sb.append('\n')
                if (respone?.exception != null) {
                    sb.append(respone.exception.message)
                } else {
                    respone?.data?.run {
                        if (this is String) {
                            sb.append(this)
                        } else {
                            sb.append(PrettyGson.toJson(this))
                        }
                    }
                }
                dubboWindowPanel?.updateResultAreaText(sb.toString())
                sb.append('\n')
            }
        }

        override fun onError(msg: String, e: Throwable?) {
            dubboWindowPanel?.updateResultAreaText("$msg ${e?.message}")
        }

        override fun finish() {
            dubboWindowPanel?.setPanelEnableState(true)
        }
    }

    private inner class OnceExecCallback : TaskCallback<DubboResponse> {
        override fun onSuccess(respone: DubboResponse?) {
            if (respone?.exception != null) {
                onError("接口调用异常", respone.exception)
            } else {
                respone?.data?.run {
                    if (this is String) {
                        dubboWindowPanel?.updateResultAreaText(this)
                    } else {
                        dubboWindowPanel?.updateResultAreaText(PrettyGson.toJson(this))
                    }
                }
            }
        }

        override fun onError(msg: String, e: Throwable?) {
            notifyMsg("dubbo", "$msg ${e?.message}", WARNING)
        }

        override fun finish() {
            dubboWindowPanel?.setPanelEnableState(true)
        }
    }

    private fun connectOrDisconnect() = runBlocking {
        val ui = dubboWindowPanel ?: return@runBlocking
        val dubboService = this@DubboWindowView.dubboService ?: return@runBlocking
        val registry = ui.getSelectedRegistry()
        if (!registry.isNullOrBlank()) {
            ui.setPanelEnableState(false)
            if (dubboService.isConnected(registry)) {
                dubboService.disconnect(registry)
            } else {
                ui.updateConnectState(registry, ConnectState.Connecting)
                dubboService.connect(
                    registry,
                    ui.getUsername(),
                    ui.getPassword(),
                    this@DubboWindowView
                )
            }
        }
    }

    private fun onRegistryChanged(registry: String) {
        val dubboService = this.dubboService ?: return
        val ui = dubboWindowPanel ?: return

        if (dubboService.isConnected(registry)) {
            ui.updateConnectState(registry, ConnectState.Connected)
        } else {
            ui.updateConnectState(registry, ConnectState.Disconnect)
        }
        updatePanel()
    }

    private fun onApplicationChanged(appName: String) {
        updatePanel(false, true, true, true)
    }

    private fun onInterfaceChanged(interfaceName: String) {
        updatePanel(false, false, true, true)
    }

    private fun onMethodChanged(method: String) {
        updatePanel(false, false, false, true)
    }

    private fun refreshRegistry() = runBlocking {
        if (project == null) return@runBlocking
        val ui = dubboWindowPanel ?: return@runBlocking

        ui.getSelectedRegistry()?.run {
            dubboService?.disconnect(this)
            dubboService?.connect(this, ui.getUsername(), ui.getPassword(), this@DubboWindowView)
            ui.updateConnectState(this, ConnectState.Connecting)
        }
    }


    private fun updatePanel(
        isApplication: Boolean = true,
        isInterface: Boolean = true,
        isMethod: Boolean = true,
        isMisc: Boolean = true
    ) {
        if (project == null) return
        val ui = dubboWindowPanel ?: return
        val registry = ui.getSelectedRegistry()
        if (registry == null) {
            ui.resetToEmpty()
        } else {
            val appInfos = DubboStorage.getInstance(project!!).getAppInfos(registry)
            if (appInfos.isNullOrEmpty()) {
                ui.resetToEmpty()
                return
            }
            if (isApplication) {
                ui.updateApplicationList(appInfos.map { it.name })
            }

            val updateInterface = isApplication || isInterface
            val updateMethod = isApplication || isInterface || isMethod
            val updateMisc = isApplication || isInterface || isMethod || isMisc

            val appName = ui.getSelectedApplication()
            if (updateInterface) {
                if (appName.isNullOrBlank()) {
                    ui.updateInterfaceList(emptyList())
                } else {
                    ui.updateInterfaceList(DubboUtils.getInterfaceNames(appName, appInfos))
                }
            }
            val interfaceName = ui.getSelectedService()
            if (interfaceName.isNullOrBlank() || appName.isNullOrBlank()) {
                if (updateMisc) {
                    ui.updateVersionList(emptyList())
                    ui.updateGroupList(emptyList())
                }
                if (updateMethod) {
                    ui.updateMethodList(emptyList())
                }
                if (updateInterface) {
                    ui.updateServerList(emptyList())
                }
            } else {
                if (updateMisc) {
                    ui.updateVersionList(DubboUtils.getVersions(appName, interfaceName, appInfos))
                    ui.updateGroupList(DubboUtils.getGroups(appName, interfaceName, appInfos))
                }
                if (updateMethod) {
                    ui.updateMethodList(DubboUtils.getMethodKey(appName, interfaceName, appInfos))
                }
                if (updateInterface) {
                    ui.updateServerList(DubboUtils.getServers(appName, interfaceName, appInfos))
                }
            }
        }
    }

    override fun onConnect(registry: String, username: String?, password: String?) {
        project?.run {
            val storage = DubboStorage.getInstance(this)
            storage.lastConnectRegistry = registry
            storage.addRegistry(RegistryInfo(registry, username, password))
        }
        invokeLater {
            val now = dubboWindowPanel?.getSelectedRegistry()
            if (registry == now) {
                dubboWindowPanel?.updateConnectState(registry, ConnectState.Connected)
                dubboWindowPanel?.addRegistry(registry)
            }
            dubboWindowPanel?.setPanelEnableState(true)
        }
    }

    override fun onConnectError(registry: String, exception: Exception?) {
        invokeLater {
            val now = dubboWindowPanel?.getSelectedRegistry()
            if (registry == now) {
                dubboWindowPanel?.updateConnectState(registry, ConnectState.Disconnect)
            }
            dubboWindowPanel?.setPanelEnableState(true)
        }
    }

    override fun onDisconnect(registry: String) {
        project?.run {
            DubboStorage.getInstance(this).removeRegistry(registry)
        }
        invokeLater {
            val now = dubboWindowPanel?.getSelectedRegistry()
            if (registry == now) {
                dubboWindowPanel?.updateConnectState(registry, ConnectState.Disconnect)
            }
            dubboWindowPanel?.setPanelEnableState(true)
        }
    }

    override fun onUrlChanged(registry: String, urls: List<URL>) {
        if (urls.isEmpty()) {
            project?.run {
                DubboStorage.getInstance(this).removeRegistry(registry)
                invokeLater { updatePanel() }
            }
            return
        }

        dubboService?.getAppInfo(registry, urls, object : TaskCallback<List<AppInfo>> {
            override fun onSuccess(inc: List<AppInfo>?) {
                project?.run {
                    val storage = DubboStorage.getInstance(this)
                    val origin = storage.getAppInfos(registry)

                    val infos = DubboUtils.mergeAppInfos(origin, inc)
                    storage.setAppInfos(registry, infos)
                    updatePanel()
                }
            }

            override fun onError(msg: String, e: Throwable?) {
                notifyMsg("dubbo", "$msg ${e?.message}", WARNING)
            }
        })

    }

    companion object {
        fun getInstance(project: Project): DubboWindowView {
            return ServiceManager.getService(project, DubboWindowView::class.java)
        }
    }

}