package top.shenluw.plugin.dubbo.ui;

import com.intellij.openapi.ui.SimpleToolWindowPanel;

import javax.swing.*;

/**
 * @author Shenluw
 * created：2019/9/28 12:53
 */
public class DubboWindowForm extends SimpleToolWindowPanel {

    protected JPanel root;
    protected JComboBox registryComboBox;
    protected JButton connectBtn;
    protected JComboBox applicationComboBox;
    protected JComboBox serviceComboBox;
    protected JComboBox methodComboBox;
    protected JButton execBtn;
    protected JButton concurrentExecBtn;
    protected JComboBox paramsEditorTypeSelect;
    protected JComboBox responseTypeSelect;
    protected JButton paramsEditorOpenBtn;
    protected JButton responseOpenBtn;
    protected JEditorPane paramsEditor;
    protected JTextPane responseTextPane;
    protected JComboBox threadGroupCountComboBox;
    protected JComboBox concurrentCountComboBox;
    protected JButton refreshBtn;
    protected MyJComboBox versionComboBox;
    protected JCheckBox specialCheckBox;
    protected MyJComboBox serverComboBox;
    protected MyJComboBox groupComboBox;
    protected MyJTextField usernameField;
    protected MyJTextField passwordField;

    public DubboWindowForm() {
        super(true, true);
        setContent(root);
    }

}
