import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java-library")
    kotlin("jvm") version "2.4.20"
    kotlin("kapt") version "2.4.20"
    id("com.xuncorp.spw.workshop") version "0.1.0-dev23"
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
    jvmToolchain(25)

    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
    }
}

dependencies {
    compileOnly("com.github.Moriafly:spw-workshop-api:0.1.0-dev23")
    kapt("com.github.Moriafly:spw-workshop-api:0.1.0-dev23")
    compileOnly(kotlin("stdlib"))
    implementation("org.json:json:20210307")
    compileOnly("net.jthink:jaudiotagger:3.0.1")
}

spmod {
    PluginClass = "com.zmxl.taskbarlyrics.TaskbarLyricsPlugin"
    PluginId = "TaskbarLyricsPlugin"
    PluginVersion = "3.0.0"
    PluginProvider = "zmxl"
    PluginDescription = "一个适用于 Salt Player For Windows 的任务栏歌词插件"
    PluginHasConfig = true
    PluginOpenSourceUrl = "https://github.com/zmxlsss666/TaskbarLyricsPlugin"
}
