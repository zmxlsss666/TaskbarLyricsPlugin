@file:OptIn(UnstableSpwWorkshopApi::class)
package com.zmxl.taskbarlyrics

import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import com.zmxl.taskbarlyrics.server.HttpServer
import com.zmxl.taskbarlyrics.config.ConfigManager
import com.xuncorp.spw.workshop.api.WorkshopApi
import com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi

class TaskbarLyricsPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {
    private lateinit var httpServer: HttpServer

    override fun start() {
        super.start()
        Log.i("Taskbar Lyrics Plugin 开始启动...")

        try {
            ConfigManager.initialize()
            Log.i("ConfigManager 初始化成功")
        } catch (e: Exception) {
            Log.i("ConfigManager 初始化失败: ${e.message}")
        }

        httpServer = HttpServer(35374)
        httpServer.start()
        Log.i("HTTP服务器启动成功，端口: 35374")

        ClientLauncher.start(ConfigManager.configDir())
        Log.i("任务栏歌词客户端已启动")

        Log.i("Taskbar Lyrics Plugin 启动完成")
    }

    override fun stop() {
        super.stop()
        Log.i("Taskbar Lyrics Plugin 开始停止...")

        ClientLauncher.stop()
        Log.i("任务栏歌词客户端已结束")

        httpServer.stop()
        Log.i("HTTP服务器已停止")

        Log.i("Taskbar Lyrics Plugin 已完全停止")
    }
}