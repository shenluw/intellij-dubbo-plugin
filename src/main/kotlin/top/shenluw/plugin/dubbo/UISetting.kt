package top.shenluw.plugin.dubbo

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * @author Shenluw
 * created：2019/10/19 23:45
 */
@State(name = "DubboUISetting", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class UISetting : PersistentStateComponent<UISetting> {
    var responseEditorLanguage: String = Constants.TEXT_LANGUAGE
    var parameterEditorLanguage: String = Constants.YAML_LANGUAGE

    override fun getState() = this

    override fun loadState(state: UISetting) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(project: Project): UISetting {
            return ServiceManager.getService(project, UISetting::class.java)
        }
    }

}
