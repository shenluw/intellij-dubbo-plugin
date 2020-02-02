package top.shenluw.plugin.dubbo

import com.intellij.ide.startup.StartupManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

/**
 * @author Shenluw
 * created：2019/9/28 13:07
 */
class DubboToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val manager = StartupManagerEx.getInstanceEx(project)

        val block = {
            manager.runWhenProjectIsInitialized {
                DubboWindowView.getInstance(project).install(project, toolWindow)
            }
        }
        if (manager.postStartupActivityPassed()) {
            block.invoke()
        } else {
            manager.registerPostStartupActivity(block)
        }
    }
}

