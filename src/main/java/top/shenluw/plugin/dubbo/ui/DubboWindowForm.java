package top.shenluw.plugin.dubbo.ui;

import com.intellij.openapi.ui.SimpleToolWindowPanel;

import javax.swing.*;

/**
 * @author Shenluw
 * created：2019/9/28 12:53
 */
public class DubboWindowForm extends SimpleToolWindowPanel {

    protected JPanel root;
    protected JComboBox<String> registryComboBox;
    protected JButton connectBtn;
    protected JComboBox<String> applicationComboBox;
    protected JComboBox<String> serviceComboBox;
    protected JComboBox<String> methodComboBox;
    protected JButton execBtn;
    protected JButton concurrentExecBtn;
    protected JComboBox paramsEditorTypeSelect;
    protected JComboBox responseTypeSelect;
    protected JButton paramsEditorOpenBtn;
    protected JButton responseOpenBtn;
    protected MyLanguageTextField parameterEditor;
    protected MyLanguageTextField responseEditor;
    protected JComboBox<String> threadGroupCountComboBox;
    protected JComboBox<String> concurrentCountComboBox;
    protected JButton refreshBtn;
    protected JComboBox<String> versionComboBox;
    protected JCheckBox specialCheckBox;
    protected JComboBox<String> serverComboBox;
    protected JComboBox<String> groupComboBox;
    protected MyJTextField usernameField;
    protected MyJTextField passwordField;
    protected JSplitPane contentRootPane;

    public DubboWindowForm() {
        super(true, true);
        setContent(root);
    }

}
