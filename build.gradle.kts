plugins {
    id("java-library")
    kotlin("jvm") version "2.3.0"
    kotlin("kapt") version "2.3.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly("com.github.Moriafly:spw-workshop-api:0.1.0-dev20")
    kapt("com.github.Moriafly:spw-workshop-api:0.1.0-dev20")
    implementation("org.json:json:20210307")
    implementation(kotlin("stdlib-jdk8"))
    compileOnly("net.jthink:jaudiotagger:3.0.1")
}

val pluginClass = "com.zmxl.taskbarlyrics.TaskbarLyricsPlugin"
val pluginId = "TaskbarLyricsPlugin"
val pluginVersion = "3.0.0"
val pluginProvider = "zmxl"
val PluginHasConfig = "true"
val PluginOpenSourceUrl = "https://github.com/zmxlsss666/TaskbarLyricsPlugin"
val PluginDescription = "一个适用于 Salt Player For Windows 的任务栏歌词插件"

tasks.named<Jar>("jar") {
    manifest {
        attributes["Plugin-Class"] = pluginClass
        attributes["Plugin-Id"] = pluginId
        attributes["Plugin-Version"] = pluginVersion
        attributes["Plugin-Provider"] = pluginProvider
        attributes["Plugin-Has-Config"] = PluginHasConfig
        attributes["Plugin-Open-Source-Url"] = PluginOpenSourceUrl
        attributes["Plugin-Description"] = PluginDescription
    }
}

tasks.register<Jar>("plugin") {
    archiveBaseName.set("plugin-$pluginId-$pluginVersion")
    into("classes") {
        with(tasks.named<Jar>("jar").get())
    }
    dependsOn(configurations.runtimeClasspath)
    into("lib") {
        from(configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") })
    }
    archiveExtension.set("zip")
}