package top.shenluw.plugin.dubbo.utils

import com.intellij.openapi.diagnostic.Logger

/**
 * @author Shenluw
 * 创建日期：2019/4/22 16:01
 */
interface KLogger {
    val log: Logger
        get() = Logger.getInstance(javaClass)
}