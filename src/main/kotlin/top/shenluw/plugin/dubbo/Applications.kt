package top.shenluw.plugin.dubbo

import com.google.gson.GsonBuilder
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.notification.*
import com.intellij.openapi.application.ApplicationManager
import org.apache.dubbo.common.logger.Level
import org.apache.dubbo.common.logger.LoggerFactory

/**
 * @author Shenluw
 * created：2019/9/28 17:34
 */
const val PLUGIN_ID = "DubboPlugin"

inline val Application get() = ApplicationManager.getApplication()

inline val Gson get() = GsonBuilder().create()
inline val PrettyGson get() = GsonBuilder().setPrettyPrinting().create()

inline fun notifyMsg(
    title: String, msg: String,
    type: NotificationType = NotificationType.INFORMATION,
    listener: NotificationListener? = null
) {
    Notifications.Bus.notify(
        Notification(PLUGIN_ID, title, msg, type, listener)
    )
}

inline fun invokeLater(crossinline block: () -> Unit) {
    if (Application.isDispatchThread) {
        block.invoke()
    } else {
        Application.invokeLater { block.invoke() }
    }
}

inline fun invokeLater(runnable: Runnable) {
    if (Application.isDispatchThread) {
        runnable.run()
    } else {
        Application.invokeLater(runnable)
    }
}


class MyApplicationInitializedListener : ApplicationInitializedListener {
    override fun componentsInitialized() {
        NotificationsConfiguration.getNotificationsConfiguration()
            .changeSettings(PLUGIN_ID, NotificationDisplayType.NONE, true, false)

        LoggerFactory.setLevel(Level.WARN)
    }
}