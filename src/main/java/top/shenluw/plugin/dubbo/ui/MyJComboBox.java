package top.shenluw.plugin.dubbo.ui;

import com.intellij.ui.JBColor;
import org.apache.commons.lang.StringUtils;

import javax.swing.*;
import java.awt.*;

/**
 * @author Shenluw
 * created：2019/9/28 22:52
 */
public class MyJComboBox<E> extends JComboBox<E> {

    private String placeholder;
    private int placeholderMarginStart = 12;

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        if (StringUtils.isNotBlank(placeholder)) {
            if (hasEditObj()) {
                return;
            }

            Font font = getFont();
            if (font != null) {
                Graphics2D g2d = (Graphics2D) g;
                Font oldFont = g2d.getFont();
                g2d.setFont(font);
                Object oldHint = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color oldColor = g2d.getColor();
                g2d.setColor(JBColor.GRAY);

                FontMetrics fm = g2d.getFontMetrics(font);
                int ascent = fm.getAscent();
                int baseLine = (g2d.getClipBounds().height - (ascent + fm.getDescent())) / 2 + ascent;
                g.drawString(placeholder, placeholderMarginStart, baseLine);

                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldHint);
                g2d.setFont(oldFont);
                g2d.setColor(oldColor);
            }
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

    public int getPlaceholderMarginStart() {
        return placeholderMarginStart;
    }

    public void setPlaceholderMarginStart(int placeholderMarginStart) {
        this.placeholderMarginStart = placeholderMarginStart;
    }

}
