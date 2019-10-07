package top.shenluw.plugin.dubbo.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.AnimatedIcon.FS
import com.intellij.ui.ClickListener
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent


/**
 * @author Shenluw
 * created：2019/9/28 17:38
 */
class DubboWindowPanel : DubboWindowForm() {
    private val REFRESH_ICON = FS()

    private val EMPTY_ADDRESS = ""

    private val connectState = hashMapOf<String, ConnectState>()

    private val registries = hashSetOf<String>()

    private val listenerMap = hashMapOf<JComponent, EventListener>()
    private val clickListenerMap = hashMapOf<JComponent, ClickListener>()

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
        for (i in 0 until registryComboBox.itemCount) {
            registries.add(registryComboBox.getItemAt(i) as String)
        }
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

    fun setOnRegistryChangedListener(handler: (registry: String) -> Unit) {
        registryComboBox.setSelectChangedListener(handler)
    }

    fun setOnApplicationChangedListener(handler: (app: String) -> Unit) {
        applicationComboBox.setSelectChangedListener(handler)
    }

    fun setOnServiceChangedListener(handler: (app: String) -> Unit) {
        serviceComboBox.setSelectChangedListener(handler)
    }

    fun setOnMethodChangedListener(handler: (app: String) -> Unit) {
        methodComboBox.setSelectChangedListener(handler)
    }

    private fun JButton.setOnClickListener(handler: () -> Unit) {
        var listener = clickListenerMap.remove(this)
        listener?.uninstall(this)
        listener = object : ClickListener() {
            override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
                handler.invoke()
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
        paramsEditor.text = txt
    }

    fun updateResultAreaText(txt: String?) {
        responseTextPane.text = txt
    }

    fun updateApplicationList(apps: List<String>) {
        updateComboBox(applicationComboBox, apps)
    }

    fun updateInterfaceList(interfaces: List<String>) {
        updateComboBox(serviceComboBox, interfaces)
    }

    fun updateMethodList(methods: List<String>) {
        updateComboBox(methodComboBox, methods)
    }

    fun updateVersionList(versions: List<String>) {
        updateComboBox(versionComboBox, versions)
    }

    fun updateGroupList(groups: List<String>) {
        updateComboBox(groupComboBox, groups)
    }

    fun updateServerList(servers: List<String>) {
        updateComboBox(serverComboBox, servers)
    }

    private fun updateComboBox(comboBox: JComboBox<String>, values: Collection<String>) {
        val current = comboBox.selectedItem as String?
        comboBox.removeAllItems()
        if (values.isEmpty()) return
        values.forEach {
            comboBox.addItem(it)
        }
        if (current in values) {
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