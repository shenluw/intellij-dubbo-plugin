package top.shenluw.plugin.dubbo.ui

import top.shenluw.plugin.dubbo.utils.UiUtils.adapterLabelComponentWidth
import top.shenluw.plugin.dubbo.utils.UiUtils.adapterTextComponentWidth
import java.awt.Component
import java.awt.event.ItemEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.text.JTextComponent

/**
 * 当item长度过长时，超过长度的部分不显示
 *
 * @author Shenluw
 * created：2019/10/13 19:07
 */
class ComboBoxUIAdapter<T>(private val comboBox: JComboBox<T>, private val ellipsis: String = "...") :
    DefaultListCellRenderer() {

    fun adapter() {
        comboBox.renderer = this
        val editorComponent = comboBox.editor.editorComponent
        if (editorComponent is JTextComponent) {
            comboBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    editorComponent.adapterTextComponentWidth(editorComponent.width, ellipsis)
                }
            }
        }
    }

    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        this.adapterLabelComponentWidth(comboBox.width, ellipsis)
        return this
    }

}
