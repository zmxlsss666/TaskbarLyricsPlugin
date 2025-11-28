@file:OptIn(UnstableSpwWorkshopApi::class)
package com.zmxl.taskbarlyrics

import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import com.zmxl.taskbarlyrics.server.HttpServer
import com.zmxl.taskbarlyrics.control.SmtcController
import com.zmxl.taskbarlyrics.config.ConfigManager
import com.xuncorp.spw.workshop.api.WorkshopApi
import com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi

class TaskbarLyricsPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {
    private lateinit var httpServer: HttpServer

    override fun start() {
        super.start()
        println("Taskbar Lyrics Plugin 开始启动...")
        
        try {
            ConfigManager.initialize()
            println("ConfigManager 初始化成功")
        } catch (e: Exception) {
            println("ConfigManager 初始化失败: ${e.message}")
        }
        
        httpServer = HttpServer(35374)
        httpServer.start()
        println("HTTP服务器启动成功，端口: 35374")
        
        SmtcController.init()
        println("SMTC控制器初始化成功")
        
        println("Taskbar Lyrics Plugin 启动完成")
    }

    override fun stop() {
        super.stop()
        println("Taskbar Lyrics Plugin 开始停止...")
        
        httpServer.stop()
        println("HTTP服务器已停止")
        
        SmtcController.shutdown()
        println("SMTC控制器已关闭")
        
        println("Taskbar Lyrics Plugin 已完全停止")
    }
}