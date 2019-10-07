package top.shenluw.plugin.dubbo

import com.intellij.notification.NotificationType.WARNING
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import org.apache.dubbo.common.URL
import top.shenluw.plugin.dubbo.client.DubboListener
import top.shenluw.plugin.dubbo.ui.ConnectState
import top.shenluw.plugin.dubbo.ui.DubboWindowPanel
import top.shenluw.plugin.dubbo.utils.DubboUtils
import top.shenluw.plugin.dubbo.utils.KLogger

/**
 * @author Shenluw
 * created：2019/10/1 15:04
 */
class DubboWindowView : KLogger, DubboListener {

    private var project: Project? = null

    private var dubboWindowPanel: DubboWindowPanel? = null

    private var dubboService: DubboService? = null

    fun install(project: Project, toolWindow: ToolWindow) {
        Application.assertIsDispatchThread()
        this.project = project
        this.dubboService = DubboService(project)
        val contentManager = toolWindow.contentManager
        val panel = DubboWindowPanel().apply {
            setOnConnectClickListener {
                connectOrDisconnect()
            }
            setOnRegistryChangedListener { onRegistryChanged(it) }
            setOnRefreshClickListener { refreshRegistry() }
            setOnApplicationChangedListener { onApplicationChanged(it) }
            setOnServiceChangedListener { onInterfaceChanged(it) }
            setOnMethodChangedListener { onMethodChanged(it) }
        }

        val content = contentManager.factory.createContent(panel, null, false)
        contentManager.addContent(content)
        contentManager.setSelectedContent(content)

        initUI()

        panel.isVisible = true
        this.dubboWindowPanel = panel

        Disposer.register(project, dubboService!!)
    }

    private fun initUI() {
        val storage = DubboStorage.getInstance(project!!)
        val registries = storage.registries
        if (registries.isNotEmpty()) {
            dubboWindowPanel?.setRegistries(registries)
        }
        dubboWindowPanel?.updateRegistrySelected(storage.lastConnectRegistry)
    }

    private fun connectOrDisconnect() {
        val ui = dubboWindowPanel ?: return
        val dubboService = this.dubboService ?: return
        val registry = ui.getSelectedRegistry()
        if (!registry.isNullOrBlank()) {
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

    private fun refreshRegistry() {
        if (project == null) return
        val ui = dubboWindowPanel ?: return

        ui.getSelectedRegistry()?.run {
            dubboService?.disconnect(this)
            dubboService?.connect(this, ui.getUsername(), ui.getPassword(), this@DubboWindowView)
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
                    ui.updateMethodList(DubboUtils.getMethodNames(appName, interfaceName, appInfos))
                }
                if (updateInterface) {
                    ui.updateServerList(DubboUtils.getServers(appName, interfaceName, appInfos))
                }
            }
        }
    }

    override fun onConnect(registry: String) {
        invokeLater {
            val now = dubboWindowPanel?.getSelectedRegistry()
            if (registry == now) {
                dubboWindowPanel?.updateConnectState(registry, ConnectState.Connected)
                dubboWindowPanel?.addRegistry(registry)
            }
            project?.run {
                DubboStorage.getInstance(this).lastConnectRegistry = registry
            }
        }
    }

    override fun onConnectError(registry: String, exception: Exception?) {
        invokeLater {
            val now = dubboWindowPanel?.getSelectedRegistry()
            if (registry == now) {
                dubboWindowPanel?.updateConnectState(registry, ConnectState.Disconnect)
            }
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