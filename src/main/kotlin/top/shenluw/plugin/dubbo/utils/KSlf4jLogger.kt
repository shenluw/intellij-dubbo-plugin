package top.shenluw.luss.common.log

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Shenluw
 * 创建日期：2019/4/22 16:01
 */
interface KSlf4jLogger {
    val log: Logger
        get() = LoggerFactory.getLogger(javaClass)
}