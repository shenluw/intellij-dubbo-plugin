package top.shenluw.plugin.dubbo.ui

import top.shenluw.plugin.dubbo.utils.KLogger

/**
 * @author Shenluw
 * created: 2020/2/9 22:31
 */
object ItemChangedTransaction : KLogger {
    private val record = hashSetOf<Int>()

    fun begin(obj: Any) {
        record.add(obj.hashCode())
    }

    fun commit(obj: Any) {
        val hashCode = obj.hashCode()
        if (hashCode in record) {
            record.remove(hashCode)
        } else {
            val msg = "must begin when commit"
            log.debug(msg, obj)
            throw RuntimeException(msg)
        }
    }

    fun isBegin(obj: Any): Boolean {
        return obj.hashCode() in record
    }
}



