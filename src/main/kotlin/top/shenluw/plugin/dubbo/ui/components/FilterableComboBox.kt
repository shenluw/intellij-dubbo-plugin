package top.shenluw.plugin.dubbo.ui.components

import com.intellij.openapi.util.Condition
import com.intellij.util.castSafelyTo
import top.shenluw.plugin.dubbo.ui.PlaceholderRenderer
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
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
    KeyEvent.VK_WINDOWS,
    KeyEvent.VK_SHIFT,
    KeyEvent.VK_CONTROL,
    KeyEvent.VK_ALT,
    KeyEvent.VK_TAB
)

private val CompositeKeys = arrayOf(KeyEvent.VK_CONTROL, KeyEvent.VK_ALT)


class FilterableComboBox<E> : JComboBox<E>(), PlaceholderRenderer {
    private var keyword: String? = null

    init {
        val tc = editor.editorComponent as JTextComponent
        tc.addKeyListener(object : KeyAdapter() {

            private var pressedKeyCode: Int? = null

            fun restoreCaretPosition(position: Int) {
                val doc = tc.document
                if (doc != null && position <= doc.length && position >= 0) {
                    tc.caretPosition = position
                }
            }

            fun pass(e: KeyEvent, pressedKeyCode: Int?): Boolean {
                val code = e.keyCode
                if (code >= KeyEvent.VK_F1 && code <= KeyEvent.VK_F12) {
                    return true
                }
                if (code >= KeyEvent.VK_F13 && code <= KeyEvent.VK_F24) {
                    return true
                }
                if (code in IgnoreKeyCodes) {
                    return true
                }
                return isCompositeAction(e, pressedKeyCode)
            }

            /**
             * 判断是否是ctrl + c 这些组合键
             */
            fun isCompositeAction(e: KeyEvent, pressedKeyCode: Int?): Boolean {
                return pressedKeyCode != null && e.keyCode != pressedKeyCode
            }

            override fun keyPressed(e: KeyEvent) {
                if (!isEditable) {
                    return
                }
                if (pressedKeyCode == null && e.keyCode in CompositeKeys) {
                    pressedKeyCode = e.keyCode
                }
            }

            override fun keyReleased(e: KeyEvent) {
                if (!isEditable) {
                    return
                }
                val code = e.keyCode
                val tmpCode = pressedKeyCode
                if (code in CompositeKeys) {
                    pressedKeyCode = null
                }

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
                if (pass(e, tmpCode)) {
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

    private var myPlaceholder: String? = null
    private var myPlaceholderOffset: Int = 12
    override fun getPlaceholderOffset(): Int {
        return myPlaceholderOffset
    }

    override fun setPlaceholderOffset(offset: Int) {
        myPlaceholderOffset = offset
    }

    override fun getPlaceholder(): String? {
        return myPlaceholder
    }

    override fun setPlaceholder(placeholder: String?) {
        myPlaceholder = placeholder
    }

    override fun paintComponent(g: Graphics?) {
        super.paintComponent(g)
        if (!hasEditObj() && g is Graphics2D && selectedIndex == -1) {
            paintPlaceholder(g as Graphics2D?, font)
        }
    }

    private fun hasEditObj(): Boolean {
        val tc = editor.editorComponent as JTextComponent
        return !tc.text.isNullOrEmpty()
    }

    override fun removeAllItems() {
        val model = dataModel as FilteringComboBoxModel<E>
        model.removeAllElements()
        selectedItemReminder = null
        if (isEditable()) {
            editor.item = null
        }
    }

}


class FilteringComboBoxModel<T>(private val originalModel: MutableComboBoxModel<T>) : AbstractListModel<T>(),
    MutableComboBoxModel<T> {
    private var selectedObject: Any? = null

    private val myData: MutableList<T> = ArrayList()
    private var myCondition: Condition<T>? = null

    private val myListDataListener: ListDataListener = object : ListDataListener {
        override fun contentsChanged(e: ListDataEvent) {
            refilter()
        }

        override fun intervalAdded(e: ListDataEvent) {
            refilter()
        }

        override fun intervalRemoved(e: ListDataEvent) {
            refilter()
        }
    }

    init {
        originalModel.addListDataListener(myListDataListener)
    }

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
        println("ins $index $item")
        originalModel.insertElementAt(item, index)
        fireIntervalAdded(this, index, index)
    }

    override fun removeElement(anObject: Any?) {
        val index = getElementIndex(anObject as T)
        if (index != -1) {
            removeElementAt(index)
        }
    }

    fun setFilter(condition: Condition<T>) {
        myCondition = condition
        refilter()
    }

    fun removeAllElements() {
        val index1 = size - 1
        val size = originalModel.size
        if (size > 0) {
            for (i in 0 until originalModel.size) {
                originalModel.removeElementAt(0)
            }
            if (originalModel is DefaultComboBoxModel<*>) {
                (originalModel as DefaultComboBoxModel<*>).removeAllElements()
            } else {
                for (i in 0 until originalModel.size) {
                    originalModel.removeElementAt(0)
                }
            }
            myData.clear()
            selectedItem = null
            if (index1 >= 0) {
                fireIntervalRemoved(this, 0, index1)
            }
        }
    }

    private fun removeAllElementsWithMy() {
        val index1 = myData.size - 1
        if (index1 >= 0) {
            myData.clear()
            fireIntervalRemoved(this, 0, index1)
        }
    }

    fun refilter() {
        removeAllElementsWithMy()
        var count = 0
        for (i in 0 until originalModel.size) {
            val elt = originalModel.getElementAt(i)
            if (passElement(elt)) {
                myData.add(elt)
                count++
            }
        }
        if (count > 0) {
            fireIntervalAdded(this, 0, count - 1)
        }
    }

    override fun getSize(): Int {
        return myData.size
    }

    override fun getElementAt(index: Int): T {
        return myData[index]
    }

    fun getElementIndex(element: T): Int {
        return myData.indexOf(element)
    }

    private fun passElement(element: T): Boolean {
        return myCondition == null || myCondition?.value(element) == true
    }

    fun contains(value: T): Boolean {
        return myData.contains(value)
    }

}