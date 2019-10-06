package top.shenluw.plugin.dubbo

import com.google.gson.GsonBuilder
import com.intellij.openapi.application.ApplicationManager

/**
 * @author Shenluw
 * created：2019/9/28 17:34
 */

inline val Application get() = ApplicationManager.getApplication()

inline val Gson get() = GsonBuilder().create()