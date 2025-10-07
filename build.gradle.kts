plugins {
    id("java-library")
    kotlin("jvm") version "1.9.22"
    kotlin("kapt") version "1.9.22"
}
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
kotlin {
    jvmToolchain(21)
}
dependencies {
    compileOnly("com.github.Moriafly:spw-workshop-api:0.1.0-dev14")
    kapt("com.github.Moriafly:spw-workshop-api:0.1.0-dev14")
    implementation("org.eclipse.jetty:jetty-server:11.0.15")
    implementation("org.eclipse.jetty:jetty-servlet:11.0.15")
    compileOnly("net.java.dev.jna:jna:5.10.0")
    compileOnly("net.java.dev.jna:jna-platform:5.10.0")
    implementation("org.json:json:20210307")
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.google.code.gson:gson:2.10.1")
}
val pluginClass = "com.zmxl.taskbarlyrics.TaskbarLyricsPlugin"
val pluginId = "TaskbarLyricsPlugin"
val pluginVersion = "1.0.0"
val pluginProvider = "zmxl"
val PluginHasConfig = "false"
val PluginOpenSourceUrl = "https://github.com/zmxlsss666/TaskbarLyricsPlugin"
val PluginDescription = "任务栏歌词，必须安装.NET8.0运行时方可使用"
sourceSets {
    main {
        resources {
            srcDirs("src/main/resources")
            include("exe/**")
        }
    }
}
tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
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
    from(sourceSets.main.get().resources) {
        include("exe/**")
        into("resources")
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
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
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}