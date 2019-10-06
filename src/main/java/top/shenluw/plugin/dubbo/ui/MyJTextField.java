package top.shenluw.plugin.dubbo.ui;

import org.apache.commons.lang.StringUtils;

import javax.swing.*;
import javax.swing.text.Document;
import java.awt.*;

/**
 * @author Shenluw
 * created：2019/10/6 21:09
 */
public class MyJTextField extends JTextField implements PlaceholderRenderer {

    private String placeholder;
    private int placeholderOffset = 12;

    public MyJTextField() {
    }

    public MyJTextField(String text) {
        super(text);
    }

    public MyJTextField(int columns) {
        super(columns);
    }

    public MyJTextField(String text, int columns) {
        super(text, columns);
    }

    public MyJTextField(Document doc, String text, int columns) {
        super(doc, text, columns);
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

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        String text = getText();
        if (StringUtils.isEmpty(text) && g instanceof Graphics2D) {
            paintPlaceholder((Graphics2D) g, getFont());
        }
    }
}
