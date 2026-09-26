pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    // 26.1+ は難読化されていないため Loom の non-remap 版、それ以前は remap 版が必要。
    // loom-back-compat が MC バージョンに応じて正しい Loom プラグインを適用する。
    id("dev.kikugie.loom-back-compat") version "0.4.2"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Minecraft に依存しない純粋な Java ロジック。全バージョン・全ローダーで共有し、単体テストもここで 1 回だけ走らせる。
include(":common")

stonecutter {
    create(rootProject) {
        // versions/{mc}-{loader} というサブプロジェクトを作り、ローダーごとに build.{loader}.gradle.kts を割り当てる。
        fun match(project: String, vararg loaders: String, version: String = project) {
            for (loader in loaders) version("$project-$loader", version).buildscript("build.$loader.gradle.kts")
        }

        match("1.21.1", "fabric", "neoforge")
        match("1.21.11", "fabric", "neoforge")
        match("26.1.2", "fabric", "neoforge")
        match("26.2", "fabric", "neoforge")
        match("26.3", "fabric", "neoforge")
        vcsVersion = "26.3-fabric"
    }
}

rootProject.name = "clear-third-person-aim"
