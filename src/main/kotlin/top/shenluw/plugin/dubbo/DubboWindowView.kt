package top.shenluw.plugin.dubbo

import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.notification.NotificationType.WARNING
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindow
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.apache.dubbo.common.URL
import top.shenluw.plugin.dubbo.Constants.DUBBO_TEMP_RESPONSE_PREFIX
import top.shenluw.plugin.dubbo.client.DubboListener
import top.shenluw.plugin.dubbo.client.DubboParameter
import top.shenluw.plugin.dubbo.client.DubboRequest
import top.shenluw.plugin.dubbo.client.URLState
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
        this.dubboService?.clientListener = this
        val contentManager = toolWindow.contentManager
        val panel = DubboWindowPanel().apply {
            setOnConnectClickListener { connectOrDisconnect() }
            setOnRegistryChangedListener { onRegistryChanged(it) }
            setOnRefreshClickListener { refreshRegistry() }
            setOnApplicationChangedListener { onApplicationChanged(it) }
            setOnServiceChangedListener { onInterfaceChanged(it) }
            setOnMethodChangedListener { onMethodChanged(it) }
            setOnVersionChangedListener { onVersionChanged(it) }
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

        if (app.isNullOrBlank() || interfaceName.isNullOrBlank() || methodKey.isNullOrBlank() || version == null) {
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
            YamlParameterParser.parse(paramsText, argumentTypes)
        }
        ui.setPanelEnableState(false)

        val request = DubboRequest(app, interfaceName, methodName, params, version, server, group)
        if (concurrentInfo == NoConcurrentInfo) {
            GlobalScope.async {
                callOnce(registry, request)
            }
        } else {
            GlobalScope.async {
                callMore(registry, request, concurrentInfo)
            }
        }
    }

    private suspend fun callOnce(registry: String, request: DubboRequest) {
        kotlin.runCatching { dubboService!!.execute(registry, request) }
            .onSuccess { response ->
                project?.run {
                    DubboStorage.getInstance(this).addInvokeHistory(request, response)
                }
                if (response.exception != null) {
                    notifyMsg("dubbo", "接口调用异常 ${response.exception.message}", WARNING)
                } else {
                    invokeLater {
                        val data = response.data
                        if (data is String) {
                            dubboWindowPanel?.updateResultAreaText(data)
                        } else {
                            dubboWindowPanel?.updateResultAreaText(PrettyGson.toJson(data))
                        }
                    }
                }
            }.onFailure {
                notifyMsg("dubbo", "接口调用异常 ${it.message}", WARNING)
            }
        dubboWindowPanel?.setPanelEnableState(true)
    }

    private suspend fun callMore(registry: String, request: DubboRequest, concurrentInfo: ConcurrentInfo) {
        kotlin.runCatching {
            dubboService!!.execute(registry, request, concurrentInfo)
        }.onSuccess { f ->
            project?.run {
                DubboStorage.getInstance(this).addInvokeHistory(request, null)
            }
            invokeLater { dubboWindowPanel?.updateResultAreaText("start\n") }
            val format = SimpleDateFormat(Constants.DATE_FORMAT_PATTERN)
            for (response in f) {
                invokeLater {
                    if (response.exception != null) {
                        dubboWindowPanel?.appendResultAreaText("${format.format(System.currentTimeMillis())}:\n${response.exception.message}\n")
                    } else {
                        val txt = response.data?.let {
                            if (it !is String) {
                                return@let PrettyGson.toJson(this)
                            }
                            return@let it
                        }
                        dubboWindowPanel?.appendResultAreaText("${format.format(System.currentTimeMillis())}:\n$txt\n")
                    }
                }
            }
            invokeLater { dubboWindowPanel?.appendResultAreaText("end\n") }
        }.onFailure {
            notifyMsg("dubbo", "接口调用异常 ${it.message}", WARNING)
        }
        dubboWindowPanel?.setPanelEnableState(true)
    }

    private fun connectOrDisconnect() = runBlocking {
        val ui = dubboWindowPanel ?: return@runBlocking
        val dubboService = dubboService ?: return@runBlocking
        val registry = ui.getSelectedRegistry()
        if (!registry.isNullOrBlank()) {
            ui.setPanelEnableState(false)
            if (dubboService.isConnected(registry)) {
                if (dubboService.disconnect(registry)) {
                    ui.updateConnectState(registry, ConnectState.Disconnect)
                    dubboWindowPanel?.setPanelEnableState(true)
                }
            } else {
                ui.updateConnectState(registry, ConnectState.Connecting)
                kotlin.runCatching { dubboService.connect(registry, ui.getUsername(), ui.getPassword()) }
                    .onSuccess { client ->
                        if (client != null) {
                            connectSuccess(registry, client.username, client.password)
                        } else {
                            connectError(registry)
                        }
                    }.onFailure {
                        connectError(registry)
                        notifyMsg("dubbo", "连接失败", INFORMATION)
                    }
            }
        }
    }

    private fun connectSuccess(registry: String, username: String?, password: String?) {
        project?.run {
            val storage = DubboStorage.getInstance(this)
            storage.lastConnectRegistry = registry
            storage.addRegistry(RegistryInfo(registry, username, password))
        }
        val now = dubboWindowPanel?.getSelectedRegistry()
        if (registry == now) {
            dubboWindowPanel?.updateConnectState(registry, ConnectState.Connected)
            dubboWindowPanel?.addRegistry(registry)
        }
        dubboWindowPanel?.setPanelEnableState(true)
    }

    private fun connectError(registry: String) {
        val now = dubboWindowPanel?.getSelectedRegistry()
        if (registry == now) {
            dubboWindowPanel?.updateConnectState(registry, ConnectState.Disconnect)
        }
        dubboWindowPanel?.setPanelEnableState(true)
    }

    private fun onRegistryChanged(registry: String) {
        val dubboService = this.dubboService ?: return
        val ui = dubboWindowPanel ?: return

        if (dubboService.isConnected(registry)) {
            ui.updateConnectState(registry, ConnectState.Connected)
        } else {
            ui.updateConnectState(registry, ConnectState.Disconnect)
        }
        updateAll()
    }

    private fun onApplicationChanged(appName: String) {
        val apps = getOrResetUi()
        if (apps.isNotEmpty()) {
            updateInterface(apps)
            updateMethod(apps)
            updateMisc(apps)
            updateEditorPanel()
        }
    }

    private fun onInterfaceChanged(interfaceName: String) {
        val apps = getOrResetUi()
        if (apps.isNotEmpty()) {
            updateMethod(apps)
            updateMisc(apps)
            updateEditorPanel()
        }
    }

    private fun onMethodChanged(method: String) {
        val apps = getOrResetUi()
        if (apps.isNotEmpty()) {
            updateMisc(apps)
            updateEditorPanel()
        }
    }

    private fun onVersionChanged(group: String) {
        updateEditorPanel()
    }

    private fun getOrResetUi(): List<ServiceInfo> {
        if (project == null) return emptyList()
        val ui = dubboWindowPanel ?: return emptyList()
        val registry = ui.getSelectedRegistry()
        if (registry == null) {
            ui.resetToEmpty()
        } else {
            val services = DubboStorage.getInstance(project!!).getServices(registry)
            if (services.isNullOrEmpty()) {
                ui.resetToEmpty()
            } else {
                return services
            }
        }
        return emptyList()
    }

    private fun refreshRegistry() = runBlocking {
        val project = project ?: return@runBlocking
        val ui = dubboWindowPanel ?: return@runBlocking
        val registry = ui.getSelectedRegistry() ?: return@runBlocking

        val client = dubboService?.getClient(registry)
        if (client == null || !client.connected) {
            return@runBlocking
        }
        val urls = client.getUrls()

        DubboStorage.getInstance(project).removeRegistry(registry)
        DubboStorage.getInstance(project).setServices(registry, DubboUtils.getSimpleServiceInfo(registry, urls))
        updateAll()
    }

    private fun updateApplication(infos: List<ServiceInfo>) {
        val ui = dubboWindowPanel ?: return
        val registry = ui.getSelectedRegistry()
        if (registry == null || infos.isNullOrEmpty()) {
            ui.updateApplicationList(emptyList())
        } else {
            ui.updateApplicationList(infos.map { it.appName }.toSortedSet())
        }
    }

    private fun updateInterface(infos: List<ServiceInfo>) {
        val ui = dubboWindowPanel ?: return
        val appName = ui.getSelectedApplication()
        if (appName.isNullOrBlank()) {
            ui.updateInterfaceList(emptyList())
        } else {
            ui.updateInterfaceList(infos.map { it.interfaceName }.toSortedSet())
        }
    }

    private fun updateMethod(infos: List<ServiceInfo>) {
        val ui = dubboWindowPanel ?: return
        val registry = ui.getSelectedRegistry()
        val appName = ui.getSelectedApplication()
        val interfaceName = ui.getSelectedService()
        if (appName.isNullOrBlank() || interfaceName.isNullOrBlank() || registry.isNullOrBlank()) {
            ui.updateMethodList(emptyList())
        } else {
            val services = infos.filter { it.appName == appName && it.interfaceName == interfaceName }
            if (services.find { it.methods == null } != null) {
                // 需要获取方法信息
                GlobalScope.async {
                    kotlin.runCatching { dubboService?.updateServiceInfo(registry, services) }
                        .onSuccess {
                            invokeLater {
                                ui.updateMethodList(DubboUtils.getMethodKey(appName, interfaceName, services))
                            }
                        }.onFailure {
                            notifyMsg("dubbo", "方法详情获取失败", INFORMATION)
                        }
                }
            } else {
                ui.updateMethodList(DubboUtils.getMethodKey(appName, interfaceName, services))
            }
        }
    }

    private fun updateMisc(infos: List<ServiceInfo>) {
        val ui = dubboWindowPanel ?: return
        val appName = ui.getSelectedApplication()
        val interfaceName = ui.getSelectedService()
        if (appName.isNullOrBlank() || interfaceName.isNullOrBlank()) {
            ui.updateVersionList(emptyList())
            ui.updateGroupList(emptyList())
            ui.updateServerList(emptyList())
        } else {
            ui.updateVersionList(DubboUtils.getVersions(appName, interfaceName, infos))
            ui.updateGroupList(DubboUtils.getGroups(appName, interfaceName, infos))
            ui.updateServerList(DubboUtils.getServers(appName, interfaceName, infos))
        }
    }

    private fun updateEditorPanel() {
        val project = project ?: return

        val ui = dubboWindowPanel ?: return
        val registry = ui.getSelectedRegistry()
        val appName = ui.getSelectedApplication()
        val interfaceName = ui.getSelectedService()
        val methodKey = ui.getSelectedMethod()
        val version = ui.getSelectVersion()

        if (methodKey.isNullOrBlank()
            || registry.isNullOrBlank()
            || appName.isNullOrBlank()
            || interfaceName.isNullOrBlank()
            || version.isNullOrBlank()
        ) {
            ui.updateParamAreaText(null)
        } else {
            val storage = DubboStorage.getInstance(project)
            val services = storage.getServices(registry, appName, interfaceName)
                ?.filter { it.version == version }
            val method = services?.asSequence()
                ?.mapNotNull { it.methods }
                ?.flatMap { it.asSequence() }
                ?.find { it.key == methodKey }

            if (method == null || services.isNullOrEmpty()) {
                ui.updateParamAreaText(null)
                ui.updateResultAreaText(null)
            } else {
                val history = storage.getLastInvoke(services[0], method)
                if (history == null) {
                    ui.updateParamAreaText(null)
                    ui.updateResultAreaText(null)
                } else {
                    ui.updateParamAreaText(history.request)
                    ui.updateResultAreaText(history.response)
                }
            }
        }
    }

    private fun updateAll() {
        val apps = getOrResetUi()
        if (apps.isNotEmpty()) {
            updateApplication(apps)
            updateInterface(apps)
            updateMethod(apps)
            updateMisc(apps)
            updateEditorPanel()
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

    override fun onUrlChanged(registry: String, urls: List<URL>, state: URLState) {
        val project = project ?: return
        val selectedRegistry = dubboWindowPanel?.getSelectedRegistry()

        val storage = DubboStorage.getInstance(project)
        // 只要存在变化就移除缓存
        storage.removeByURL(registry, urls)

        if (state == URLState.ADD || state == URLState.UPDATE) {
            val infos = DubboUtils.getSimpleServiceInfo(registry, urls)
            storage.addServices(infos)
        }

        if (selectedRegistry == registry) {
            invokeLater {
                val services = storage.getServices(registry) ?: emptyList()
                updateApplication(services)
                updateInterface(services)
                updateMisc(services)
                updateMethod(services)
                updateEditorPanel()
            }
        }
    }

    companion object {
        fun getInstance(project: Project): DubboWindowView {
            return ServiceManager.getService(project, DubboWindowView::class.java)
        }
    }

}