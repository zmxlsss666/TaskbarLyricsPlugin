package com.zmxl.taskbarlyrics

/**
 * 插件日志开关：所有日志统一走这里。默认关闭；由配置项 `debug_logs` 控制。
 * 日志开启时直接打印到 stdout（Salt Player 的控制台），关闭时静默。
 */
object Log {
    @Volatile
    var enabled: Boolean = false

    /** 普通信息日志。 */
    fun i(msg: Any?) {
        if (enabled) println(msg)
    }

    /** 带异常的日志。 */
    fun e(msg: String, t: Throwable?) {
        if (enabled) {
            println(msg)
            t?.printStackTrace()
        }
    }

    /** 仅异常堆栈。 */
    fun e(t: Throwable?) {
        if (enabled) t?.printStackTrace()
    }
}