package top.shenluw.plugin.dubbo

import com.google.gson.GsonBuilder
import com.intellij.openapi.application.ApplicationManager

/**
 * @author Shenluw
 * created：2019/9/28 17:34
 */

internal val Application = ApplicationManager.getApplication()

internal val Gson = GsonBuilder().create()