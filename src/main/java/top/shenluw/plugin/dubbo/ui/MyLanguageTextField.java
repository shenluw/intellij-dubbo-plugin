package top.shenluw.plugin.dubbo.ui;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.LanguageTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 处理tab键会跳转到下一个输入框问题
 * 原因： 设置了 EditorEx.setEmbeddedIntoDialogWrapper(true) 之后导致问题
 *
 * @author Shenluw
 * created: 2019/11/2 23:55
 */
public class MyLanguageTextField extends EditorTextField {
    private Language myLanguage;
    private final Project myProject;

    public MyLanguageTextField(Language language, @Nullable Project project, @NotNull String value) {
        this(language, project, value, true);
    }

    public MyLanguageTextField(Language language, @Nullable Project project, @NotNull String value, boolean oneLineMode) {
        this(language, project, value, new LanguageTextField.SimpleDocumentCreator(), oneLineMode);
    }

    public MyLanguageTextField(@Nullable Language language,
                               @Nullable Project project,
                               @NotNull String value,
                               @NotNull LanguageTextField.DocumentCreator documentCreator) {
        this(language, project, value, documentCreator, true);
    }

    public MyLanguageTextField(@Nullable Language language,
                               @Nullable Project project,
                               @NotNull String value,
                               @NotNull LanguageTextField.DocumentCreator documentCreator,
                               boolean oneLineMode) {
        super(documentCreator.createDocument(value, language, project), project,
                language != null ? language.getAssociatedFileType() : StdFileTypes.PLAIN_TEXT, language == null, oneLineMode);

        myLanguage = language;
        myProject = project;

        setEnabled(language != null);
    }

    @Override
    protected EditorEx createEditor() {
        EditorEx ex = super.createEditor();
        if (myLanguage != null) {
            final FileType fileType = myLanguage.getAssociatedFileType();
            if (fileType != null) {
                ex.setHighlighter(HighlighterFactory.createHighlighter(myProject, fileType));
            }
        }
        return ex;
    }

    @Override
    public void setNewDocumentAndFileType(@NotNull FileType fileType, Document document) {
        super.setNewDocumentAndFileType(fileType, document);
        Project project = getProject();
        if (project != null && document != null) {
            PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
            if (psiFile != null) {
                myLanguage = psiFile.getLanguage();
            }
        }
    }
}
