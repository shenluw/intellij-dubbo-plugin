package top.shenluw.plugin.dubbo.utils

import org.apache.commons.collections.CollectionUtils

/**
 * @author Shenluw
 * created：2019/10/3 22:53
 */
object Collections {


    fun isEqualMap(m1: Map<*, *>, m2: Map<*, *>): Boolean {
        if (m1 == m2) {
            return true
        }
        if (m1.size != m2.size) {
            return false
        }

        var m1Key: Any
        var m1Vs: Any?
        var m2Vs: Any?

        for (entry in m1) {
            m1Key = entry.key!!
            m1Vs = entry.value

            m2Vs = m2[m1Key]

            if (m1Vs == m2Vs) {
                continue
            }

            if (m1Vs is Collection<*> && m2Vs is Collection<*>) {
                if (!CollectionUtils.isEqualCollection(m1Vs, m2Vs)) {
                    return false
                }
            } else if (m1Vs is Map<*, *> && m2Vs is Map<*, *>) {
                if (!isEqualMap(m1Vs, m2Vs)) {
                    return false
                }
            } else {
                if (m1Vs != m2Vs) {
                    return false
                }
            }
        }

        return true
    }

}