@file:OptIn(UnstableSpwWorkshopApi::class)

package com.zmxl.taskbarlyrics.config

import com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi
import com.xuncorp.spw.workshop.api.WorkshopApi
import com.xuncorp.spw.workshop.api.config.ConfigHelper
import com.xuncorp.spw.workshop.api.config.ConfigManager as SpwConfigManager
import com.zmxl.taskbarlyrics.Log
import java.util.function.Consumer
import kotlin.math.roundToInt

/**
 * 配置管理：完全由官方 ConfigHelper 驱动，不再读写任何自定义配置文件。
 *
 * SPW 的 preference_config.json 是唯一设置界面，用户在界面中修改任意一项后，
 * 官方系统会自动保存到 config.json 并触发 [SpwConfigManager.addConfigChangeListener]。
 * 本类收到变更后通过 [ConfigManager.listeners] 通知 HTTP 服务把新配置实时推给
 * 任务栏歌词（Rust 客户端经 SSE 接收并应用），实现真正的设置热同步。
 */
object ConfigManager {
    private const val PLUGIN_ID = "TaskbarLyricsPlugin"

    private var spwConfigManager: SpwConfigManager? = null
    private var helper: ConfigHelper? = null

    /** 依赖配置变更的业务监听器（由 HttpServer.StreamServlet 注册）。 */
    private val listeners = mutableListOf<() -> Unit>()

    // ---- 配置项键名（与 preference_config.json / Rust 端 LyricsConfig 保持一致） ----
    const val KEY_FONT_FAMILY = "font_family"
    const val KEY_FONT_SIZE = "font_size"
    const val KEY_FONT_COLOR = "font_color"
    const val KEY_BACKGROUND_COLOR = "background_color"
    const val KEY_ALIGNMENT = "alignment"
    const val KEY_SHOW_TRANSLATION = "show_translation"
    const val KEY_TRANSLATION_FONT_SIZE = "translation_font_size"
    const val KEY_TRANSLATION_FONT_COLOR = "translation_font_color"
    const val KEY_LINE_SPACING = "line_spacing"
    const val KEY_HIGHLIGHT_COLOR = "highlight_color"
    const val KEY_HIGHLIGHT_GRADIENT = "highlight_gradient"
    const val KEY_HIGHLIGHT_ANIMATION = "highlight_animation"
    const val KEY_AUTO_HIDE_FULLSCREEN = "auto_hide_fullscreen"
    const val KEY_POSITION_OFFSET_X = "position_offset_x"
    const val KEY_POSITION_OFFSET_Y = "position_offset_y"
    const val KEY_LYRIC_FILTER_REGEX = "lyric_filter_regex"
    const val KEY_DEBUG_LOGS = "debug_logs"

    const val DEFAULT_FONT_FAMILY = "default"
    const val DEFAULT_FONT_FONT_SIZE = 16
    const val DEFAULT_FONT_COLOR = "#FFFFFF"
    const val DEFAULT_BACKGROUND_COLOR = "#00000000"
    const val DEFAULT_ALIGNMENT = "center"
    const val DEFAULT_SHOW_TRANSLATION = true
    const val DEFAULT_TRANSLATION_FONT_SIZE = 14
    const val DEFAULT_TRANSLATION_FONT_COLOR = "#CCCCCC"
    const val DEFAULT_LINE_SPACING = 2
    const val DEFAULT_HIGHLIGHT_COLOR = "#00FFFF"
    const val DEFAULT_HIGHLIGHT_GRADIENT = false
    const val DEFAULT_HIGHLIGHT_ANIMATION = true
    const val DEFAULT_AUTO_HIDE_FULLSCREEN = true
    const val DEFAULT_POSITION_OFFSET_X = 0
    const val DEFAULT_POSITION_OFFSET_Y = 0
    const val DEFAULT_LYRIC_FILTER_REGEX = ""
    const val DEFAULT_DEBUG_LOGS = false

    @UnstableSpwWorkshopApi
    fun initialize() {
        if (spwConfigManager != null) return
        try {
            val manager = WorkshopApi.manager.createConfigManager(PLUGIN_ID)
            spwConfigManager = manager
            helper = manager.getConfig()

            Log.enabled = debugLogs()

            // 官方配置保存后回调；此时 helper 已在磁盘持久化，get() 可读到最新值。
            manager.addConfigChangeListener(Consumer<ConfigHelper> { h ->
                try {
                    h.reload()
                } catch (_: Exception) {}
                Log.enabled = debugLogs()
                notifyListeners()
            })

            Log.i("配置管理器已初始化, 配置文件: ${helper?.getConfigPath()}")
        } catch (e: Exception) {
            Log.i("配置管理器初始化失败: ${e.message}")
            Log.e(e)
        }
    }

    private fun configHelper(): ConfigHelper {
        if (helper == null) initialize()
        return helper ?: throw IllegalStateException("配置未初始化")
    }

    /** 插件数据目录（config.json 所在目录）：用于存放并运行随插件打包的任务栏歌词客户端 exe。 */
    fun configDir(): java.io.File = configHelper().getConfigPath().toFile().parentFile

    /** 官方配置文件本身（用于高频 lastModified 轮询以快速感知磁盘变更）。 */
    fun configFilePath(): java.io.File = configHelper().getConfigPath().toFile()

    // ---- getters：直接读取官方配置（seekbar 存 Float，转 Int 输出） ----

    fun getFontFamily(): String = configHelper().get(KEY_FONT_FAMILY, DEFAULT_FONT_FAMILY)

    fun getFontSize(): Int = configHelper().get(KEY_FONT_SIZE, DEFAULT_FONT_FONT_SIZE.toFloat()).roundToInt()

    fun getFontColor(): String = configHelper().get(KEY_FONT_COLOR, DEFAULT_FONT_COLOR)

    fun getBackgroundColor(): String = configHelper().get(KEY_BACKGROUND_COLOR, DEFAULT_BACKGROUND_COLOR)

    fun getAlignment(): String = configHelper().get(KEY_ALIGNMENT, DEFAULT_ALIGNMENT)

    fun showTranslation(): Boolean = configHelper().get(KEY_SHOW_TRANSLATION, DEFAULT_SHOW_TRANSLATION)

    fun getTranslationFontSize(): Int = configHelper().get(KEY_TRANSLATION_FONT_SIZE, DEFAULT_TRANSLATION_FONT_SIZE.toFloat()).roundToInt()

    fun getTranslationFontColor(): String = configHelper().get(KEY_TRANSLATION_FONT_COLOR, DEFAULT_TRANSLATION_FONT_COLOR)

    fun getLineSpacing(): Int = configHelper().get(KEY_LINE_SPACING, DEFAULT_LINE_SPACING.toFloat()).roundToInt()

    fun getHighlightColor(): String = configHelper().get(KEY_HIGHLIGHT_COLOR, DEFAULT_HIGHLIGHT_COLOR)

    fun highlightGradient(): Boolean = configHelper().get(KEY_HIGHLIGHT_GRADIENT, DEFAULT_HIGHLIGHT_GRADIENT)

    fun highlightAnimation(): Boolean = configHelper().get(KEY_HIGHLIGHT_ANIMATION, DEFAULT_HIGHLIGHT_ANIMATION)

    fun autoHideFullscreen(): Boolean = configHelper().get(KEY_AUTO_HIDE_FULLSCREEN, DEFAULT_AUTO_HIDE_FULLSCREEN)

    fun getPositionOffsetX(): Int = configHelper().get(KEY_POSITION_OFFSET_X, DEFAULT_POSITION_OFFSET_X.toFloat()).roundToInt()

    fun getPositionOffsetY(): Int = configHelper().get(KEY_POSITION_OFFSET_Y, DEFAULT_POSITION_OFFSET_Y.toFloat()).roundToInt()

    fun getLyricFilterRegex(): String = configHelper().get(KEY_LYRIC_FILTER_REGEX, DEFAULT_LYRIC_FILTER_REGEX)

    fun debugLogs(): Boolean = configHelper().get(KEY_DEBUG_LOGS, DEFAULT_DEBUG_LOGS)

    /** 供 HTTP `/api/config` 与 SSE 推送使用的完整配置对象。 */
    fun getAllConfig(): Map<String, Any> {
        initialize()
        return linkedMapOf(
            KEY_FONT_FAMILY to getFontFamily(),
            KEY_FONT_SIZE to getFontSize(),
            KEY_FONT_COLOR to getFontColor(),
            KEY_BACKGROUND_COLOR to getBackgroundColor(),
            KEY_ALIGNMENT to getAlignment(),
            KEY_SHOW_TRANSLATION to showTranslation(),
            KEY_TRANSLATION_FONT_SIZE to getTranslationFontSize(),
            KEY_TRANSLATION_FONT_COLOR to getTranslationFontColor(),
            KEY_LINE_SPACING to getLineSpacing(),
            KEY_HIGHLIGHT_COLOR to getHighlightColor(),
            KEY_HIGHLIGHT_GRADIENT to highlightGradient(),
            KEY_HIGHLIGHT_ANIMATION to highlightAnimation(),
            KEY_AUTO_HIDE_FULLSCREEN to autoHideFullscreen(),
            KEY_POSITION_OFFSET_X to getPositionOffsetX(),
            KEY_POSITION_OFFSET_Y to getPositionOffsetY(),
            KEY_LYRIC_FILTER_REGEX to getLyricFilterRegex(),
            KEY_DEBUG_LOGS to debugLogs()
        )
    }

    fun addConfigChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeConfigChangeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    /** 从磁盘重新读取官方配置并通知监听器（供 HTTP `/api/config?forceRefresh=true` 使用）。 */
    fun refreshConfig() {
        try {
            helper?.reload()
            notifyListeners()
            Log.i("强制刷新配置完成")
        } catch (e: Exception) {
            Log.i("强制刷新配置失败: ${e.message}")
        }
    }

    /** 仅从磁盘静默重载配置，不通知监听器（供服务器周期性对账、补推丢失的配置变更）。 */
    fun reloadSilently() {
        try {
            helper?.reload()
        } catch (_: Exception) {}
    }

    private fun notifyListeners() {
        listeners.toList().forEach { it.invoke() }
    }

    /** 设置单个配置项的值并保存（供任务栏歌词在拖拽定位后写入 position_offset_x/y 使用）。 */
    @JvmStatic
    @JvmName("setValue")
    fun setValue(key: String, value: Int): Boolean {
        return try {
            val h = configHelper()
            h.set(key, value.toFloat())
            val ok = h.save()
            Log.i("设置配置项: $key = $value, 保存=${if (ok) "成功" else "失败"}")
            ok
        } catch (e: Exception) {
            Log.i("设置配置项失败: $key = $value, ${e.message}")
            Log.e(e)
            false
        }
    }

    /** 强制把内存中的配置写入官方配置文件并保存（供“应用配置”按钮调用）。 */
    @JvmStatic
    @JvmName("applyConfig")
    fun applyConfig() {
        try {
            val h = configHelper()
            setAllToHelper(h)
            if (h.save()) {
                Log.i("配置已保存到 SPW 官方配置系统")
                WorkshopApi.ui.toast("配置已成功应用", WorkshopApi.Ui.ToastType.Success)
            } else {
                Log.i("保存配置到 SPW 系统失败")
                WorkshopApi.ui.toast("保存配置失败", WorkshopApi.Ui.ToastType.Error)
            }
        } catch (e: Exception) {
            Log.i("应用配置时出错: ${e.message}")
            Log.e(e)
            WorkshopApi.ui.toast("应用配置时出错: ${e.message}", WorkshopApi.Ui.ToastType.Error)
        }
    }

    /** 重置为默认值：删除配置文件（config.json），下次访问时由 SPW 按默认值重建。 */
    @JvmStatic
    @JvmName("resetToDefault")
    fun resetToDefault() {
        try {
            val file = configHelper().getConfigPath().toFile()
            val existed = file.exists()
            val deleted = !existed || file.delete()
            if (!deleted) {
                Log.i("重置配置失败: 无法删除配置文件 ${file.absolutePath}")
                WorkshopApi.ui.toast("重置失败：无法删除配置文件", WorkshopApi.Ui.ToastType.Error)
                return
            }
            // 文件已删除 → 从磁盘重载（缺失即回落到默认值）。
            try { helper?.reload() } catch (_: Exception) {}
            // 关键：SPW 的 ConfigHelper 在文件缺失+reload 后内存值可能仍是旧值，必须显式把内存
            // 配置写成默认值，否则 getAllConfig() 广播给任务栏的还是旧位置偏移，Rust 端判定"无变化"
            // 而不重绘，导致重置后位置无法即时还原。这里不保存文件，保留"删除 config.json"语义。
            try { setDefaultsToHelper(configHelper()) } catch (_: Exception) {}
            notifyListeners()
            Log.i(if (existed) "已删除配置文件并恢复默认: ${file.absolutePath}" else "配置文件不存在，无需重置")
            WorkshopApi.ui.toast(
                if (existed) "已删除配置文件，恢复默认" else "配置文件不存在，已是默认",
                WorkshopApi.Ui.ToastType.Success
            )
        } catch (e: Exception) {
            Log.i("重置配置时出错: ${e.message}")
            Log.e(e)
            WorkshopApi.ui.toast("重置配置时出错: ${e.message}", WorkshopApi.Ui.ToastType.Error)
        }
    }

    private fun setAllToHelper(h: ConfigHelper) {
        h.set(KEY_FONT_FAMILY, getFontFamily())
        h.set(KEY_FONT_SIZE, getFontSize())
        h.set(KEY_FONT_COLOR, getFontColor())
        h.set(KEY_BACKGROUND_COLOR, getBackgroundColor())
        h.set(KEY_ALIGNMENT, getAlignment())
        h.set(KEY_SHOW_TRANSLATION, showTranslation())
        h.set(KEY_TRANSLATION_FONT_SIZE, getTranslationFontSize())
        h.set(KEY_TRANSLATION_FONT_COLOR, getTranslationFontColor())
        h.set(KEY_LINE_SPACING, getLineSpacing())
        h.set(KEY_HIGHLIGHT_COLOR, getHighlightColor())
        h.set(KEY_HIGHLIGHT_GRADIENT, highlightGradient())
        h.set(KEY_HIGHLIGHT_ANIMATION, highlightAnimation())
        h.set(KEY_AUTO_HIDE_FULLSCREEN, autoHideFullscreen())
        h.set(KEY_POSITION_OFFSET_X, getPositionOffsetX())
        h.set(KEY_POSITION_OFFSET_Y, getPositionOffsetY())
        h.set(KEY_LYRIC_FILTER_REGEX, getLyricFilterRegex())
    }

    /** 把所有配置项的内存值写成默认（不保存，供重置后广播正确默认值给任务栏歌词）。 */
    private fun setDefaultsToHelper(h: ConfigHelper) {
        h.set(KEY_FONT_FAMILY, DEFAULT_FONT_FAMILY)
        h.set(KEY_FONT_SIZE, DEFAULT_FONT_FONT_SIZE)
        h.set(KEY_FONT_COLOR, DEFAULT_FONT_COLOR)
        h.set(KEY_BACKGROUND_COLOR, DEFAULT_BACKGROUND_COLOR)
        h.set(KEY_ALIGNMENT, DEFAULT_ALIGNMENT)
        h.set(KEY_SHOW_TRANSLATION, DEFAULT_SHOW_TRANSLATION)
        h.set(KEY_TRANSLATION_FONT_SIZE, DEFAULT_TRANSLATION_FONT_SIZE)
        h.set(KEY_TRANSLATION_FONT_COLOR, DEFAULT_TRANSLATION_FONT_COLOR)
        h.set(KEY_LINE_SPACING, DEFAULT_LINE_SPACING)
        h.set(KEY_HIGHLIGHT_COLOR, DEFAULT_HIGHLIGHT_COLOR)
        h.set(KEY_HIGHLIGHT_GRADIENT, DEFAULT_HIGHLIGHT_GRADIENT)
        h.set(KEY_HIGHLIGHT_ANIMATION, DEFAULT_HIGHLIGHT_ANIMATION)
        h.set(KEY_AUTO_HIDE_FULLSCREEN, DEFAULT_AUTO_HIDE_FULLSCREEN)
        h.set(KEY_POSITION_OFFSET_X, DEFAULT_POSITION_OFFSET_X)
        h.set(KEY_POSITION_OFFSET_Y, DEFAULT_POSITION_OFFSET_Y)
        h.set(KEY_LYRIC_FILTER_REGEX, DEFAULT_LYRIC_FILTER_REGEX)
    }
}