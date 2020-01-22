package top.shenluw.plugin.dubbo.ui.components

import com.intellij.openapi.util.Condition
import com.intellij.ui.speedSearch.FilteringListModel
import com.intellij.util.castSafelyTo
import top.shenluw.plugin.dubbo.utils.KLogger
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.ComboBoxModel
import javax.swing.JComboBox
import javax.swing.MutableComboBoxModel
import javax.swing.plaf.basic.ComboPopup
import javax.swing.text.JTextComponent

/**
 * @author Shenluw
 * created: 2020/1/21 16:56
 */
private val IgnoreKeyCodes = arrayOf(
    KeyEvent.VK_ESCAPE,
    KeyEvent.VK_KP_UP,
    KeyEvent.VK_KP_DOWN,
    KeyEvent.VK_UP,
    KeyEvent.VK_DOWN,
    KeyEvent.VK_LEFT,
    KeyEvent.VK_RIGHT,
    KeyEvent.VK_KP_LEFT,
    KeyEvent.VK_KP_RIGHT,
    KeyEvent.VK_PAGE_DOWN,
    KeyEvent.VK_PAGE_UP,
    KeyEvent.VK_CAPS_LOCK,
    KeyEvent.VK_HOME,
    KeyEvent.VK_END,
    KeyEvent.VK_WINDOWS
)

class FilterableComboBox<E> : JComboBox<E>(), KLogger {
    private var keyword: String? = null

    init {
        val tc = editor.editorComponent as JTextComponent
        tc.addKeyListener(object : KeyAdapter() {

            fun restoreCaretPosition(position: Int) {
                val doc = tc.document
                if (doc != null && position <= doc.length && position >= 0) {
                    tc.caretPosition = position
                }
            }

            fun pass(e: KeyEvent): Boolean {
                val code = e.keyCode
                if (code >= KeyEvent.VK_F1 && code <= KeyEvent.VK_F12) {
                    return true
                }
                if (code >= KeyEvent.VK_F13 && code <= KeyEvent.VK_F24) {
                    return true
                }
                return code in IgnoreKeyCodes
            }

            override fun keyReleased(e: KeyEvent) {
                if (!isEditable) {
                    return
                }
                val code = e.keyCode
                if (code == KeyEvent.VK_ENTER) {
                    if (keyword.isNullOrEmpty()) {
                        return
                    }
                    keyword = null
                    refilter()
                    if (isPopupVisible) {
                        hidePopup()
                    }
                    return
                }
                if (pass(e)) {
                    return
                }
                val p = tc.caretPosition
                val text = tc.text
                keyword = text
                refilter()
                tc.text = text
                restoreCaretPosition(p)
                if (isPopupVisible) {
                    hidePopup()
                }
                showPopup()
            }
        })

        getUI().getAccessibleChild(this, 0)?.castSafelyTo<ComboPopup>()
            ?.list?.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent?) {
                if (!isEditable) {
                    return
                }
                if (!isPopupVisible) {
                    keyword = null
                    refilter()
                }
            }
        })
    }

    fun refilter() {
        model.castSafelyTo<FilteringComboBoxModel<*>>()?.refilter()
    }

    override fun setModel(model: ComboBoxModel<E>?) {
        super.setModel(wrapperModel(model))
    }

    private fun wrapperModel(model: ComboBoxModel<E>?): ComboBoxModel<E> {
        if (model is FilterableComboBox<*>) {
            return model
        }
        val wrapper = FilteringComboBoxModel(model as MutableComboBoxModel<E>)

        wrapper.setFilter(Condition {
            val keyword = keyword
            if (keyword.isNullOrEmpty()) {
                return@Condition true
            }
            it.toString().contains(keyword)
        })
        return wrapper
    }

}


class FilteringComboBoxModel<T>(private val originalModel: MutableComboBoxModel<T>) :
    FilteringListModel<T>(originalModel),
    MutableComboBoxModel<T> {
    private var selectedObject: Any? = null

    override fun setSelectedItem(anObject: Any?) {
        if (selectedObject != null && selectedObject != anObject ||
            selectedObject == null && anObject != null
        ) {
            selectedObject = anObject
            fireContentsChanged(this, -1, -1)
        }
    }

    override fun getSelectedItem(): Any? {
        return selectedObject
    }

    override fun addElement(item: T) {
        val old = size
        originalModel.addElement(item)
        if (size > old) {
            fireIntervalAdded(this, size - 1, size - 1)
        }
        if (size == 1 && selectedObject == null && item != null) {
            selectedItem = if (contains(item)) {
                item
            } else {
                null
            }
        }
    }

    override fun removeElementAt(index: Int) {
        val removeObj = getElementAt(index)
        if (removeObj === selectedObject) {
            if (index == 0) {
                setSelectedItem(if (size == 1) null else getElementAt(index + 1))
            } else {
                setSelectedItem(getElementAt(index - 1))
            }
        }
        originalModel.removeElement(removeObj)

        fireIntervalRemoved(this, index, index)
    }

    override fun insertElementAt(item: T, index: Int) {
        originalModel.insertElementAt(item, index)
        fireIntervalAdded(this, index, index)
    }

    override fun removeElement(anObject: Any?) {
        val index = getElementIndex(anObject as T)
        if (index != -1) {
            removeElementAt(index)
        }
    }
}