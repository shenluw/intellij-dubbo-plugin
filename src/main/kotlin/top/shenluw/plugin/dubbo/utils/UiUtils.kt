package top.shenluw.plugin.dubbo.utils

import com.intellij.ui.NumberDocument
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.text.JTextComponent

/**
 * @author Shenluw
 * created：2019/10/7 20:16
 */
object UiUtils {

    inline fun <reified T> setEditorNumericType(comboBox: JComboBox<T>) {
        val items = comboBox.getItems()
        val select = comboBox.selectedItem
        val comp = comboBox.editor.editorComponent
        if (comp is JTextComponent) {
            comp.document = NumberDocument()
        }
        comboBox.removeAllItems()
        items.forEach {
            it?.run {
                comboBox.addItem(this)
                if (select == this) {
                    comboBox.selectedItem = select
                }
            }
        }
    }

    fun JComponent.getTextWithWidth(text: String?, width: Int, ellipsis: String?): String? {
        if (!text.isNullOrBlank() && width > 0) {
            val fm = getFontMetrics(font)
            if (fm.stringWidth(text) >= width) {
                val charW = fm.charWidth(text[0])
                val count = width / charW
                if (count < text.length) {
                    return if (ellipsis.isNullOrBlank()) {
                        text.substring(0, count)
                    } else {
                        if (count > ellipsis.length) {
                            text.substring(0, count - 3) + ellipsis
                        } else {
                            text.substring(0, count)
                        }
                    }
                }
            }
        }
        return text
    }

    fun JTextComponent.adapterTextComponentWidth(width: Int, ellipsis: String?) {
        val n = getTextWithWidth(text, width, ellipsis)
        if (n != text) {
            toolTipText = text
            text = n
        }
    }

    fun JLabel.adapterLabelComponentWidth(width: Int, ellipsis: String?) {
        val n = getTextWithWidth(text, width, ellipsis)
        if (n != text) {
            toolTipText = text
            text = n
        }
    }

    fun <E> JComboBox<E>.getItems(): MutableList<E> {
        val itemCount = this.itemCount
        val list = arrayListOf<E>()
        for (i in 0 until itemCount) {
            list.add(getItemAt(i))
        }
        return list
    }
}