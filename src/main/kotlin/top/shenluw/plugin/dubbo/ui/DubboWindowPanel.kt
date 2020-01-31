package top.shenluw.plugin.dubbo.ui

import com.intellij.icons.AllIcons
import com.intellij.lang.Language
import com.intellij.lang.Language.findLanguageByID
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.ui.AnimatedIcon.FS
import com.intellij.ui.ClickListener
import top.shenluw.plugin.dubbo.UISetting
import top.shenluw.plugin.dubbo.utils.KLogger
import top.shenluw.plugin.dubbo.utils.UiUtils
import top.shenluw.plugin.dubbo.utils.UiUtils.getItems
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import kotlin.math.max


/**
 * @author Shenluw
 * created：2019/9/28 17:38
 */
class DubboWindowPanel : DubboWindowForm(), KLogger {
    private val REFRESH_ICON = FS()

    private val EMPTY_ADDRESS = ""

    private val connectState = hashMapOf<String, ConnectState>()

    private val registries = hashSetOf<String>()

    private val listenerMap = hashMapOf<JComponent, EventListener>()
    private val clickListenerMap = hashMapOf<JComponent, ClickListener>()

    private lateinit var project: Project
    private lateinit var uiSetting: UISetting

    init {
        registryComboBox.addItemListener {
            if (it.stateChange == ItemEvent.SELECTED) {
                val address = getSelectedRegistry()
                if (address.isNullOrBlank()) {
                    updateConnectState(EMPTY_ADDRESS, ConnectState.Disconnect)
                } else {
                    val state = connectState.getOrDefault(address, ConnectState.Disconnect)
                    updateConnectState(address, state)
                }
            }
        }
        specialCheckBox.addChangeListener {
            updateSpecialOption(specialCheckBox.isSelected)
        }
        registries.addAll(registryComboBox.getItems())

        UiUtils.setEditorNumericType(threadGroupCountComboBox)
        UiUtils.setEditorNumericType(concurrentCountComboBox)
    }

    fun initUI(project: Project, setting: UISetting) {
        this.project = project
        this.uiSetting = setting
        initEditorUI()
        initEditorTypeSelectUI()
    }

    private fun initEditorUI() {
        parameterEditor = createEditorUI(findLanguageByID(uiSetting.parameterEditorLanguage)!!, "")
        parameterEditor.setPlaceholder("参数使用YAML语法")
        contentRootPane.leftComponent = parameterEditor

        responseEditor = createEditorUI(findLanguageByID(uiSetting.responseEditorLanguage)!!, "", true)
        contentRootPane.rightComponent = responseEditor
    }

    private fun initEditorTypeSelectUI() {
        responseTypeSelect.addItemListener {
            if (it.stateChange == ItemEvent.SELECTED) {
                val language = findLanguageByID(it.item.toString())
                if (language != null) {
                    updateResponseEditor(language, responseEditor.text)
                    uiSetting.responseEditorLanguage = it.item.toString()
                }
            }
        }
        responseTypeSelect.selectedItem = uiSetting.responseEditorLanguage
    }


    private fun updateParameterEditor(language: Language, text: String) {
        contentRootPane.leftComponent = null
        parameterEditor = createEditorUI(language, text, false)
        contentRootPane.leftComponent = parameterEditor
    }

    private fun updateResponseEditor(language: Language, text: String) {
        val dividerLocation = contentRootPane.dividerLocation
        contentRootPane.rightComponent = null
        responseEditor = createEditorUI(language, text, true)
        contentRootPane.rightComponent = responseEditor
        contentRootPane.dividerLocation = dividerLocation
    }

    private fun createEditorUI(language: Language, text: String, readOnly: Boolean = false): MyLanguageTextField {
        val field = MyLanguageTextField(language, project, text, false)
        field.addSettingsProvider {
            it.setHorizontalScrollbarVisible(true)
            it.setVerticalScrollbarVisible(true)
        }
        field.document.setReadOnly(readOnly)
        return field
    }

    /**
     * 添加一个新的注册中心
     */
    fun addRegistry(address: String) {
        if (!registries.contains(address)) {
            registries.add(address)
            registryComboBox.addItem(address)
        }
    }

    fun setRegistries(registries: Collection<String>) {
        registryComboBox.removeAllItems()
        this.registries.clear()
        this.registries.addAll(registries)
        updateComboBox(registryComboBox, registries)
    }

    fun setOnConnectClickListener(handler: () -> Unit) {
        connectBtn.setOnClickListener(handler)
    }

    fun setOnRefreshClickListener(handler: () -> Unit) {
        refreshBtn.setOnClickListener(handler)
    }

    fun setOnExecClickListener(handler: () -> Unit) {
        execBtn.setOnClickListener(handler)
    }

    fun setOnConcurrentExecClickListener(handler: () -> Unit) {
        concurrentExecBtn.setOnClickListener(handler)
    }

    fun setOnRegistryChangedListener(handler: (registry: String) -> Unit) {
        registryComboBox.setSelectChangedListener(handler)
    }

    fun setOnApplicationChangedListener(handler: (v: String) -> Unit) {
        applicationComboBox.setSelectChangedListener(handler)
    }

    fun setOnServiceChangedListener(handler: (v: String) -> Unit) {
        serviceComboBox.setSelectChangedListener(handler)
    }

    fun setOnMethodChangedListener(handler: (v: String) -> Unit) {
        methodComboBox.setSelectChangedListener(handler)
    }

    fun setOnVersionChangedListener(handler: (v: String) -> Unit) {
        versionComboBox.setSelectChangedListener(handler)
    }

    fun setOnOpenResponseEditorListener(handler: () -> Unit) {
        responseOpenBtn.setOnClickListener(handler)
    }

    fun setOnOpenRequestEditorListener(handler: () -> Unit) {
        paramsEditorOpenBtn.setOnClickListener(handler)
    }

    private fun JButton.setOnClickListener(handler: () -> Unit) {
        var listener = clickListenerMap.remove(this)
        listener?.uninstall(this)
        listener = object : ClickListener() {
            override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
                if (this@setOnClickListener.isEnabled) {
                    handler.invoke()
                }
                return true
            }
        }
        listener.installOn(this)
        clickListenerMap[this] = listener
    }

    private fun JComboBox<String>.setSelectChangedListener(
        handler: (registry: String) -> Unit
    ) {
        val listener = listenerMap.remove(this)
        if (listener != null && listener is ItemListener) {
            this.removeItemListener(listener)
        }
        val itemListener = ItemListener { e ->
            if (e?.stateChange == ItemEvent.SELECTED) {
                handler.invoke(e.item as String)
            }
        }
        listenerMap[this] = itemListener
        this.addItemListener(itemListener)
    }

    fun getSelectedRegistry(): String? {
        return registryComboBox.selectedItem as String?
    }

    fun getSelectedApplication(): String? {
        return applicationComboBox.selectedItem as String?
    }

    fun getSelectedService(): String? {
        return serviceComboBox.selectedItem as String?
    }

    fun getSelectedMethod(): String? {
        return methodComboBox.selectedItem as String?
    }

    fun getSelectedGroup(): String? {
        if (specialCheckBox.isSelected) {
            return groupComboBox.selectedItem as String?
        }
        return null
    }

    fun getSelectedServer(): String? {
        if (specialCheckBox.isSelected) {
            return serverComboBox.selectedItem as String?
        }
        return null
    }

    fun getSelectVersion(): String? {
        return versionComboBox.selectedItem as String?
    }

    fun getUsername(): String? {
        if (specialCheckBox.isSelected) {
            return usernameField.text
        }
        return null
    }

    fun getPassword(): String? {
        if (specialCheckBox.isSelected) {
            return passwordField.text
        }
        return null
    }

    fun getConcurrentCount(): Int {
        return (concurrentCountComboBox.selectedItem as String?)?.toInt() ?: 1
    }

    fun getConcurrentGroup(): Int {
        return (threadGroupCountComboBox.selectedItem as String?)?.toInt() ?: 1
    }

    fun getParamsText(): String? {
        return parameterEditor.text
    }

    fun getResponseText(): String? {
        return responseEditor.text
    }

    fun getOpenResponseType(): String? {
        return responseTypeSelect.selectedItem?.toString()
    }

    /**
     * 更新连接的状态
     */
    fun updateConnectState(registry: String, state: ConnectState) {
        connectState[registry] = state
        when (state) {
            ConnectState.Disconnect -> {
                connectBtn.icon = AllIcons.Actions.Execute
                refreshBtn.isEnabled = false
                connectBtn.isEnabled = true
            }
            ConnectState.Connected -> {
                connectBtn.icon = AllIcons.Actions.Suspend
                refreshBtn.isEnabled = true
                connectBtn.isEnabled = true
            }
            else -> {
                connectBtn.icon = REFRESH_ICON
                refreshBtn.isEnabled = false
                connectBtn.isEnabled = false
            }
        }
    }

    /**
     * 用于在连接，调用时避免一些操作引起一些问题，禁用ui操作
     */
    fun setPanelEnableState(enable: Boolean) {

        val registry = getSelectedRegistry()
        if (registry.isNullOrBlank()) {
            refreshBtn.isEnabled = false
            connectBtn.isEnabled = enable
        } else {
            if (enable) {
                val state = connectState.getOrDefault(registry, ConnectState.Disconnect)
                updateConnectState(registry, state)
            } else {
                refreshBtn.isEnabled = false
                connectBtn.isEnabled = false
            }
        }

        registryComboBox.isEnabled = enable
        applicationComboBox.isEnabled = enable
        serviceComboBox.isEnabled = enable
        methodComboBox.isEnabled = enable
        specialCheckBox.isEnabled = enable
        serverComboBox.isEnabled = enable
        groupComboBox.isEnabled = enable
        versionComboBox.isEnabled = enable

        execBtn.isEnabled = enable
        concurrentExecBtn.isEnabled = enable
    }

    fun resetToEmpty() {
        updateParamAreaText(null)
        updateResultAreaText(null)
        updateApplicationList(emptyList())
        updateInterfaceList(emptyList())
        updateMethodList(emptyList())
        updateVersionList(emptyList())
        updateGroupList(emptyList())
        updateServerList(emptyList())
    }

    fun updateParamAreaText(txt: String?) {
        parameterEditor.text = txt ?: ""
    }

    fun updateResultAreaText(txt: String?) {
        val writable = responseEditor.document.isWritable
        responseEditor.document.setReadOnly(false)
        responseEditor.text = txt ?: ""
        responseEditor.document.setReadOnly(!writable)
    }

    fun appendResultAreaText(txt: String) {
        val document = responseEditor.document
        val writable = document.isWritable
        WriteCommandAction.runWriteCommandAction(project) {
            document.setReadOnly(false)
            document.insertString(document.getLineEndOffset(max(0, document.lineCount - 1)), txt)
            document.setReadOnly(!writable)
        }
    }

    fun updateApplicationList(apps: Collection<String>) {
        updateComboBox(applicationComboBox, apps)
    }

    fun updateInterfaceList(interfaces: Collection<String>) {
        updateComboBox(serviceComboBox, interfaces)
    }

    fun updateMethodList(methods: Collection<String>) {
        updateComboBox(methodComboBox, methods)
    }

    fun updateVersionList(versions: Collection<String>) {
        updateComboBox(versionComboBox, versions)
    }

    fun updateGroupList(groups: Collection<String>) {
        updateComboBox(groupComboBox, groups)
    }

    fun updateServerList(servers: Collection<String>) {
        updateComboBox(serverComboBox, servers)
    }

    private fun updateComboBox(comboBox: JComboBox<String>, values: Collection<String>) {
        val current = comboBox.selectedItem as String?
        if (comboBox.itemCount > 0) {
            comboBox.removeAllItems()
        }
        if (values.isEmpty()) return
        values.forEach {
            comboBox.addItem(it)
        }
        if (current != null && current in values) {
            comboBox.selectedItem = current
        } else {
            comboBox.selectedIndex = 0
        }
    }

    fun updateRegistrySelected(registry: String?) {
        updateComboBoxSelected(registryComboBox, registry)
    }

    fun updateApplicationSelected(app: String?) {
        updateComboBoxSelected(applicationComboBox, app)
    }

    fun updateServiceSelected(service: String?) {
        updateComboBoxSelected(serviceComboBox, service)
    }

    fun updateMethodSelected(method: String?) {
        updateComboBoxSelected(methodComboBox, method)
    }

    fun updateVersionSelected(version: String?) {
        updateComboBoxSelected(versionComboBox, version)
    }

    fun updateServerSelected(server: String?) {
        updateComboBoxSelected(serverComboBox, server)
    }

    fun updateGroupSelected(group: String?) {
        updateComboBoxSelected(groupComboBox, group)
    }

    private fun updateComboBoxSelected(comboBox: JComboBox<String>, value: String?) {
        if (value.isNullOrEmpty()) {
            comboBox.selectedIndex = -1
        } else {
            for (i in 0 until comboBox.itemCount) {
                val txt = comboBox.getItemAt(i) as String
                if (txt == value) {
                    comboBox.selectedIndex = i
                    return
                }
            }
        }
    }

    private fun updateSpecialOption(visible: Boolean) {
        usernameField.isVisible = visible
        passwordField.isVisible = visible
        groupComboBox.isVisible = visible
        serverComboBox.isVisible = visible
    }
}

enum class ConnectState {
    Disconnect, Connecting, Connected
}