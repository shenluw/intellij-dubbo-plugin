package top.shenluw.plugin.dubbo

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

/**
 * @author Shenluw
 * created：2019/9/28 13:07
 */
class DubboToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        StartupManager.getInstance(project).runWhenProjectIsInitialized {
            StartupManager.getInstance(project).registerPostStartupActivity {
                DubboWindowView.getInstance(project).install(project, toolWindow)
            }
        }
    }
}

