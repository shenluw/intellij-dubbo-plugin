package top.shenluw.plugin.dubbo.ui;

import com.intellij.ui.JBColor;
import org.apache.commons.lang.StringUtils;

import java.awt.*;

/**
 * @author Shenluw
 * created：2019/10/6 21:28
 */
public interface PlaceholderRenderer {

    String getPlaceholder();

    void setPlaceholder(String placeholder);

    int getPlaceholderOffset();

    void setPlaceholderOffset(int offset);

    default void paintPlaceholder(Graphics2D g2d, Font font) {
        String placeholder = getPlaceholder();
        if (StringUtils.isNotBlank(placeholder) && font != null) {
            Font oldFont = g2d.getFont();
            g2d.setFont(font);
            Object oldHint = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color oldColor = g2d.getColor();
            g2d.setColor(JBColor.GRAY);

            FontMetrics fm = g2d.getFontMetrics(font);
            int ascent = fm.getAscent();
            int baseLine = (g2d.getClipBounds().height - (ascent + fm.getDescent())) / 2 + ascent;
            g2d.drawString(placeholder, getPlaceholderOffset(), baseLine);

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldHint);
            g2d.setFont(oldFont);
            g2d.setColor(oldColor);
        }
    }
}
