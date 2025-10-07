package com.zmxl.taskbarlyrics.config
import com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi
import com.xuncorp.spw.workshop.api.WorkshopApi
import com.xuncorp.spw.workshop.api.config.ConfigHelper
import com.xuncorp.spw.workshop.api.config.ConfigManager as SpwConfigManager
import java.io.File
@OptIn(UnstableSpwWorkshopApi::class)
object ConfigManager {
    private var configHelper: ConfigHelper? = null
    private var spwConfigManager: SpwConfigManager? = null
    const val KEY_FONT_FAMILY = "font_family"
    const val KEY_FONT_SIZE = "font_size"
    const val KEY_FONT_COLOR = "font_color"
    const val KEY_BACKGROUND_COLOR = "background_color"
    const val KEY_ALIGNMENT = "alignment"
    const val KEY_SHOW_TRANSLATION = "show_translation"
    const val DEFAULT_FONT_FAMILY = "default"
    const val DEFAULT_FONT_SIZE = 16.0f
    const val DEFAULT_FONT_COLOR = "#FFFFFF"
    const val DEFAULT_BACKGROUND_COLOR = "#000000"
    const val DEFAULT_ALIGNMENT = "center"
    const val DEFAULT_SHOW_TRANSLATION = true
    private val configCache = mutableMapOf<String, Any>()
    private const val PLUGIN_ID = "TaskbarLyricsPlugin"
    private fun getHardcodedConfigPath(): String {
        val appData = System.getenv("APPDATA")
        return "$appData/Salt Player for Windows/workshop/data/$PLUGIN_ID/config.json"
    }
    @UnstableSpwWorkshopApi
    fun initialize() {
        try {
            spwConfigManager = WorkshopApi.manager.createConfigManager()
            configHelper = spwConfigManager?.getConfig()
            println("配置管理器已初始化，插件ID: $PLUGIN_ID")
            reloadFromDisk()
        } catch (e: Exception) {
            println("配置管理器初始化失败: ${e.message}")
            e.printStackTrace()
        }
    }
    private fun reloadFromDisk() {
        try {
            loadFromHardcodedPath()
        } catch (e: Exception) {
            println("从硬编码路径重新加载配置失败: ${e.message}")
            loadFromConfigHelper()
        }
    }
    private fun loadFromHardcodedPath() {
        try {
            val configFile = File(getHardcodedConfigPath())
            if (configFile.exists()) {
                val configContent = configFile.readText()
                val configMap = parseJsonConfig(configContent)
                resetToDefaultValues()
                configMap.forEach { (key, value) ->
                    when (key) {
                        KEY_FONT_FAMILY -> configCache[key] = value.toString()
                        KEY_FONT_SIZE -> configCache[key] = (value as? Number)?.toFloat() ?: DEFAULT_FONT_SIZE
                        KEY_FONT_COLOR -> configCache[key] = value.toString()
                        KEY_BACKGROUND_COLOR -> configCache[key] = value.toString()
                        KEY_ALIGNMENT -> configCache[key] = value.toString()
                        KEY_SHOW_TRANSLATION -> configCache[key] = value.toString().toBooleanStrictOrNull() ?: DEFAULT_SHOW_TRANSLATION
                    }
                }
                println("配置已从文件加载: $configCache")
            } else {
                println("配置文件不存在，使用默认配置")
                resetToDefaultValues()
            }
        } catch (e: Exception) {
            println("从硬编码路径加载配置失败: ${e.message}")
            throw e
        }
    }
    private fun loadFromConfigHelper() {
        configHelper?.let { helper ->
            try {
                helper.reload()
                configCache[KEY_FONT_FAMILY] = helper.get(KEY_FONT_FAMILY, DEFAULT_FONT_FAMILY)
                configCache[KEY_FONT_SIZE] = helper.get(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
                configCache[KEY_FONT_COLOR] = helper.get(KEY_FONT_COLOR, DEFAULT_FONT_COLOR)
                configCache[KEY_BACKGROUND_COLOR] = helper.get(KEY_BACKGROUND_COLOR, DEFAULT_BACKGROUND_COLOR)
                configCache[KEY_ALIGNMENT] = helper.get(KEY_ALIGNMENT, DEFAULT_ALIGNMENT)
                configCache[KEY_SHOW_TRANSLATION] = helper.get(KEY_SHOW_TRANSLATION, DEFAULT_SHOW_TRANSLATION)
                println("从ConfigHelper重新加载配置: $configCache")
            } catch (e: Exception) {
                println("从ConfigHelper重新加载配置失败: ${e.message}")
                resetToDefaultValues()
            }
        } ?: run {
            println("ConfigHelper未初始化，使用默认配置")
            resetToDefaultValues()
        }
    }
    private fun parseJsonConfig(jsonContent: String): Map<String, Any> {
        return try {
            val map = mutableMapOf<String, Any>()
            var cleanJson = jsonContent.trim()
            if (cleanJson.startsWith("{") && cleanJson.endsWith("}")) {
                cleanJson = cleanJson.substring(1, cleanJson.length - 1).trim()
            }
            if (cleanJson.isEmpty()) {
                return emptyMap()
            }
            val pairs = cleanJson.split(",")
            for (pair in pairs) {
                val keyValue = pair.split(":", limit = 2)
                if (keyValue.size == 2) {
                    var key = keyValue[0].trim().removeSurrounding("\"")
                    var value = keyValue[1].trim()
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.removeSurrounding("\"")
                        map[key] = value
                    }
                    else if (value == "true" || value == "false") {
                        map[key] = value == "true"
                    }
                    else if (value.contains('.') || value.toFloatOrNull() != null) {
                        map[key] = value.toFloatOrNull() ?: value
                    } else {
                        map[key] = value.toIntOrNull() ?: value
                    }
                }
            }
            map
        } catch (e: Exception) {
            println("解析JSON配置失败: ${e.message}")
            emptyMap()
        }
    }
    private fun resetToDefaultValues() {
        configCache[KEY_FONT_FAMILY] = DEFAULT_FONT_FAMILY
        configCache[KEY_FONT_SIZE] = DEFAULT_FONT_SIZE
        configCache[KEY_FONT_COLOR] = DEFAULT_FONT_COLOR
        configCache[KEY_BACKGROUND_COLOR] = DEFAULT_BACKGROUND_COLOR
        configCache[KEY_ALIGNMENT] = DEFAULT_ALIGNMENT
        configCache[KEY_SHOW_TRANSLATION] = DEFAULT_SHOW_TRANSLATION
    }
    private fun ensureInitialized() {
        if (configCache.isEmpty()) {
            initialize()
        }
    }
    fun getFontFamily(): String {
        ensureInitialized()
        return configCache[KEY_FONT_FAMILY] as? String ?: DEFAULT_FONT_FAMILY
    }
    fun getFontSize(): Float {
        ensureInitialized()
        return configCache[KEY_FONT_SIZE] as? Float ?: DEFAULT_FONT_SIZE
    }
    fun getFontColor(): String {
        ensureInitialized()
        return configCache[KEY_FONT_COLOR] as? String ?: DEFAULT_FONT_COLOR
    }
    fun getBackgroundColor(): String {
        ensureInitialized()
        return configCache[KEY_BACKGROUND_COLOR] as? String ?: DEFAULT_BACKGROUND_COLOR
    }
    fun getAlignment(): String {
        ensureInitialized()
        return configCache[KEY_ALIGNMENT] as? String ?: DEFAULT_ALIGNMENT
    }
    fun showTranslation(): Boolean {
        ensureInitialized()
        return configCache[KEY_SHOW_TRANSLATION] as? Boolean ?: DEFAULT_SHOW_TRANSLATION
    }
    fun getAllConfig(): Map<String, Any> {
        ensureInitialized()
        return mapOf(
            "font_family" to getFontFamily(),
            "font_size" to getFontSize(),
            "font_color" to getFontColor(),
            "background_color" to getBackgroundColor(),
            "alignment" to getAlignment(),
            "show_translation" to showTranslation()
        )
    }
    fun refreshConfig() {
        println("强制刷新配置请求")
        reloadFromDisk()
    }
    @JvmStatic
    @JvmName("applyConfig")
    fun applyConfig() {
        ensureInitialized()
        configHelper?.let { helper ->
            try {
                if (helper.save()) {
                    println("配置已成功保存到文件")
                    reloadFromDisk()
                    try {
                        WorkshopApi.ui.toast("配置已成功应用", WorkshopApi.Ui.ToastType.Success)
                    } catch (e: Exception) {
                        println("发送通知失败: ${e.message}")
                    }
                } else {
                    println("保存配置失败")
                    try {
                        WorkshopApi.ui.toast("保存配置失败", WorkshopApi.Ui.ToastType.Error)
                    } catch (e: Exception) {
                        println("发送通知失败: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                println("应用配置时出错: ${e.message}")
                e.printStackTrace()
                try {
                    WorkshopApi.ui.toast("应用配置时出错: ${e.message}", WorkshopApi.Ui.ToastType.Error)
                } catch (ex: Exception) {
                    println("发送通知失败: ${ex.message}")
                }
            }
        }
    }
    @JvmStatic
    @JvmName("resetToDefault")
    fun resetToDefault() {
        ensureInitialized()
        configHelper?.let { helper ->
            try {
                helper.set(KEY_FONT_FAMILY, DEFAULT_FONT_FAMILY)
                helper.set(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
                helper.set(KEY_FONT_COLOR, DEFAULT_FONT_COLOR)
                helper.set(KEY_BACKGROUND_COLOR, DEFAULT_BACKGROUND_COLOR)
                helper.set(KEY_ALIGNMENT, DEFAULT_ALIGNMENT)
                helper.set(KEY_SHOW_TRANSLATION, DEFAULT_SHOW_TRANSLATION)
                if (helper.save()) {
                    println("配置已重置为默认值")
                    reloadFromDisk()
                    try {
                        WorkshopApi.ui.toast("配置已重置为默认值", WorkshopApi.Ui.ToastType.Success)
                    } catch (e: Exception) {
                        println("发送通知失败: ${e.message}")
                    }
                } else {
                    println("重置配置失败")
                    try {
                        WorkshopApi.ui.toast("重置配置失败", WorkshopApi.Ui.ToastType.Error)
                    } catch (e: Exception) {
                        println("发送通知失败: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                println("重置配置时出错: ${e.message}")
                try {
                    WorkshopApi.ui.toast("重置配置时出错: ${e.message}", WorkshopApi.Ui.ToastType.Error)
                } catch (ex: Exception) {
                    println("发送通知失败: ${ex.message}")
                }
            }
        }
    }
}