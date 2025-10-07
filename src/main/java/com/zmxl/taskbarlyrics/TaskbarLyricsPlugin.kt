package com.zmxl.taskbarlyrics
import com.xuncorp.spw.workshop.api.SpwPlugin
import com.xuncorp.spw.workshop.api.PluginContext
import com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi
import com.zmxl.taskbarlyrics.server.HttpServer
import com.zmxl.taskbarlyrics.config.ConfigManager
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
@OptIn(UnstableSpwWorkshopApi::class)
class TaskbarLyricsPlugin(pluginContext: PluginContext) : SpwPlugin(pluginContext) {
    private lateinit var httpServer: HttpServer
    private var exeProcess: Process? = null
    private var exeDirectory: File? = null
    override fun start() {
        super.start()
        println("Taskbar Lyrics Plugin 开始启动...")
        ConfigManager.initialize()
        println("配置管理器初始化完成")
        httpServer = HttpServer(35374)
        httpServer.start()
        println("HTTP服务器启动成功，端口: 35374")
        startTaskbarLyricsExe()
        println("Taskbar Lyrics Plugin 启动完成")
    }
    override fun stop() {
        super.stop()
        println("Taskbar Lyrics Plugin 开始停止...")
        stopTaskbarLyricsExe()
        httpServer.stop()
        println("HTTP服务器已停止")
        println("Taskbar Lyrics Plugin 已完全停止")
    }
    private fun startTaskbarLyricsExe() {
        try {
            val tempDir = Files.createTempDirectory("taskbarlyrics").toFile()
            exeDirectory = tempDir
            println("创建临时目录: ${tempDir.absolutePath}")
            extractExeResources(tempDir)
            val exeFile = File(tempDir, "TaskbarLyrics.exe")
            if (exeFile.exists()) {
                val processBuilder = ProcessBuilder(exeFile.absolutePath)
                processBuilder.directory(tempDir)
                exeProcess = processBuilder.start()
                println("TaskbarLyrics.exe 启动成功")
                Runtime.getRuntime().addShutdownHook(Thread {
                    stopTaskbarLyricsExe()
                })
            } else {
                println("TaskbarLyrics.exe 文件不存在")
            }
        } catch (e: Exception) {
            println("启动 TaskbarLyrics.exe 失败: ${e.message}")
            e.printStackTrace()
        }
    }
    private fun stopTaskbarLyricsExe() {
        try {
            exeProcess?.let { process ->
                if (process.isAlive) {
                    process.destroy()
                    process.waitFor()
                    println("TaskbarLyrics.exe 已停止")
                }
                exeProcess = null
            }
            exeDirectory?.let { dir ->
                if (dir.exists()) {
                    deleteDirectory(dir)
                    println("临时文件已清理")
                }
                exeDirectory = null
            }
        } catch (e: Exception) {
            println("停止 TaskbarLyrics.exe 失败: ${e.message}")
        }
    }
    private fun extractExeResources(targetDir: File) {
        try {
            val resourceFiles = listOf(
                "TaskbarLyrics.exe",
                "TaskbarLyrics.dll",
                "TaskbarLyrics.pdb",
                "TaskbarLyrics.runtimeconfig.json",
                "TaskbarLyrics.deps.json",
                "Newtonsoft.Json.dll"
            )
            for (fileName in resourceFiles) {
                val resourcePath = "exe/$fileName"
                val inputStream: InputStream? = javaClass.classLoader.getResourceAsStream(resourcePath)
                if (inputStream != null) {
                    val targetFile = File(targetDir, fileName)
                    Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    inputStream.close()
                    println("已提取文件: $fileName")
                    if (fileName.endsWith(".exe") || fileName.endsWith(".dll")) {
                        targetFile.setExecutable(true)
                    }
                } else {
                    println("资源文件未找到: $resourcePath")
                }
            }
        } catch (e: Exception) {
            println("提取资源文件失败: ${e.message}")
            throw e
        }
    }
    private fun deleteDirectory(directory: File) {
        if (directory.exists()) {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    deleteDirectory(file)
                } else {
                    file.delete()
                }
            }
            directory.delete()
        }
    }
}