package top.shenluw.plugin.dubbo.parameter

import top.shenluw.plugin.dubbo.utils.ClassUtils

/**
 * @author Shenluw
 * created: 2019/12/15 21:18
 */
object SimplifyParameter {

    fun transform(name: String): String {
        return ClassUtils.getSimpleName(name)
    }

}