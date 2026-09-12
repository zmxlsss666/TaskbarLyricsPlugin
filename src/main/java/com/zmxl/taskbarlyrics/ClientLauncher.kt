@file:OptIn(UnstableSpwWorkshopApi::class)
package com.zmxl.taskbarlyrics

import com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi
import java.io.File

object ClientLauncher {
    private const val RES_EXE = "/taskbar-lyrics.exe"
    private const val EXE_NAME = "taskbar-lyrics.exe"

    private var process: Process? = null

    private fun log(baseDir: File, msg: String) {
        Log.i(msg)
        // 仅当调试日志开启时才落盘 launcher.log，否则不生成任何日志文件。
        if (Log.enabled) {
            try {
                File(baseDir, "launcher.log").appendText(msg + "\n")
            } catch (_: Exception) {}
        }
    }

    /** 释放 exe 并启动客户端（带瞬态文件锁重试）。 */
    fun start(baseDir: File) {
        stop() // 确保没有残留实例再启动
        try {
            val exe = File(baseDir, EXE_NAME)
            val bytes = javaClass.getResourceAsStream(RES_EXE)?.use { it.readBytes() }
            if (bytes == null) {
                log(baseDir, "资源缺失: $RES_EXE")
                return
            }
            // 幂等写入：大小一致则跳过，避免每次启动都触发防御软件扫描、制造锁窗口。
            if (exe.exists() && exe.length() == bytes.size.toLong()) {
                log(baseDir, "exe 已存在且大小一致，跳过写入")
            } else {
                exe.outputStream().use { it.write(bytes) }
                log(baseDir, "已释放 exe -> ${exe.absolutePath}, ${exe.length()} bytes")
                // 刚写完整新 exe 时防御软件可能短暂加锁，稍候再启动，降低 error=32。
                Thread.sleep(300)
            }

            val outLog = File(baseDir, "client.log")
            val pb = ProcessBuilder(exe.absolutePath)
            pb.directory(baseDir)
            pb.redirectErrorStream(true)
            // 仅调试开启时才把客户端输出写入 client.log；否则丢弃，确保不生成日志文件。
            if (Log.enabled) {
                pb.redirectOutput(outLog)
                pb.redirectError(outLog)
            } else {
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
                pb.redirectError(ProcessBuilder.Redirect.DISCARD)
            }
            // 告诉 exe 自己的父进程（Salt Player）PID：父进程退出时 exe 自动自杀，
            // 不依赖插件的 stop() 是否被调用。
            pb.environment()["TASKBAR_LYRICS_PARENT_PID"] = ProcessHandle.current().pid().toString()

            // 启动失败多为瞬态文件锁（CreateProcess error=32），重试几次。
            var proc: Process? = null
            var last: Exception? = null
            for (attempt in 1..8) {
                try {
                    proc = pb.start()
                    break
                } catch (e: Exception) {
                    last = e
                    Thread.sleep(400)
                }
            }
            val p = proc ?: run {
                log(baseDir, "启动 exe 失败(重试后仍失败): ${last?.message}")
                return
            }
            process = p
            log(baseDir, "exe 已启动, pid=${p.pid()}, path=${exe.absolutePath}")

            // 后台稍候判断是否立即退出，并记录退出码，便于定位"启动即崩"。
            Thread {
                try {
                    Thread.sleep(1500)
                    if (!p.isAlive) {
                        log(baseDir, "警告: exe 在启动后 1.5s 内已退出, exitCode=${p.exitValue()}")
                    } else {
                        log(baseDir, "exe 启动后保持运行")
                    }
                } catch (_: Exception) {}
            }.start()
        } catch (e: Exception) {
            log(baseDir, "启动 exe 失败: ${e.message}")
            Log.e(e)
        }
    }

    /** 结束客户端进程（并清理可能残留的同名孤儿进程）。 */
    fun stop() {
        try {
            process?.destroyForcibly()
        } catch (_: Exception) {}
        process = null
        try {
            ProcessBuilder("taskkill", "/f", "/im", EXE_NAME, "/t").start().waitFor()
        } catch (_: Exception) {}
    }
}