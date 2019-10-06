package top.shenluw.plugin.dubbo.ui;

import org.apache.commons.lang.StringUtils;

import javax.swing.*;
import java.awt.*;

/**
 * @author Shenluw
 * created：2019/9/28 22:52
 */
public class MyJComboBox<E> extends JComboBox<E> implements PlaceholderRenderer {

    private String placeholder;
    private int placeholderOffset = 12;


    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (!hasEditObj() && g instanceof Graphics2D) {
            paintPlaceholder((Graphics2D) g, getFont());
        }
    }

    private boolean hasEditObj() {
        ComboBoxEditor editor = getEditor();
        if (editor != null) {
            Object item = editor.getItem();
            if (item != null) {
                return !(item instanceof String) || StringUtils.isNotBlank((String) item);
            }
        }
        return false;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    @Override
    public int getPlaceholderOffset() {
        return placeholderOffset;
    }

    @Override
    public void setPlaceholderOffset(int offset) {
        placeholderOffset = offset;
    }
}
