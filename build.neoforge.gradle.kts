plugins {
    id("net.neoforged.moddev") version "2.0.147"
    id("neoforge-mutex")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "${property("mod.id") as String}-neoforge"

val requiredJava = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

sourceSets.main {
    java.srcDir(rootProject.file("common/src/main/java"))
    // Fabric 専用パッケージ (エントリポイント・Mod Menu・ゲームテスト) は NeoForge ではコンパイルしない。
    java.exclude("**/fabric/**")
}

/** 互換性検証の構成 (-Pcta.compat=sodium / iris / iris-shader)。 */
val compat: String? = providers.gradleProperty("cta.compat").orNull
val screenshotSuffix = if (compat == null) "" else "-$compat"

// 自作のクライアントテスト (Fabric のゲームテスト API の代わり)。開発用の実行構成 runSelfTest でだけ MOD に含める。
val gametest: SourceSet = sourceSets.create("gametest") {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
    java.exclude("**/fabric/**")
    resources.exclude("fabric.mod.json")
}

repositories {
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
}

dependencies {
    if (compat != null) sc.properties.getOrNull<String>("deps.sodium")?.let { "runtimeOnly"("maven.modrinth:sodium:$it") }
    if (compat?.startsWith("iris") == true) sc.properties.getOrNull<String>("deps.iris")?.let { "runtimeOnly"("maven.modrinth:iris:$it") }
}

neoForge {
    version = property("deps.neo_loader") as String

    mods {
        register(property("mod.id") as String) {
            sourceSet(sourceSets.main.get())
            sourceSet(gametest)
        }
    }

    runs {
        register("client") {
            gameDirectory = rootProject.file("run/neoforge-${sc.current.version}")
            client()
        }
        // 自作クライアントテスト。毎回空の実行ディレクトリで起動し、結果とスクリーンショットをルートの build/test-screenshots に出す。
        register("selfTest") {
            client()
            gameDirectory = layout.buildDirectory.dir("run/selfTest").get().asFile
            systemProperty("cta.selftest", "true")
            systemProperty("cta.compat", compat ?: "none")
            systemProperty("cta.screenshotDir", rootProject.layout.buildDirectory.dir("test-screenshots/${sc.current.version}-neoforge$screenshotSuffix").get().asFile.absolutePath)
            sourceSet = gametest
        }
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
    toolchain {
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    processResources {
        fun MutableMap<String, String>.register(key: String, value: String) {
            inputs.property(key, value)
            set(key, value)
        }

        val props = buildMap {
            register("id", sc.properties["mod.id"])
            register("name", sc.properties["mod.name"])
            register("version", sc.properties["mod.version"])
            register("minecraft", sc.properties["mod.mc_compat"])
            // 26.3 の NeoForge では logoFile が非推奨になり、正方形アイコンは iconFile で指定する (古い版は iconFile を知らない)。
            register("icon_key", if (sc.current.parsed >= "26.3") "iconFile" else "logoFile")
        }

        filesMatching("META-INF/neoforge.mods.toml") { expand(props) }

        val mixinJava = "JAVA_${requiredJava.majorVersion}"
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }

        exclude("fabric.mod.json", "*.ct", "*.classtweaker", "**/*.accesswidener")
    }

    val cleanSelfTestRun = register<Delete>("cleanSelfTestRun") {
        delete(layout.buildDirectory.dir("run/selfTest"))
    }
    if (compat == "iris-shader") {
        val prepare = register<Copy>("prepareIrisShaderPack") {
            from(rootProject.file("testing/iris"))
            into(layout.buildDirectory.dir("run/selfTest"))
            dependsOn(cleanSelfTestRun)
        }
        named("runSelfTest") { dependsOn(prepare) }
    } else {
        named("runSelfTest") { dependsOn(cleanSelfTestRun) }
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    withType<Jar> {
        val name = project.property("mod.id")
        inputs.property("mod_id", name)
        from("../../LICENSE") { rename { "${it}_$name" } }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies results to `build/libs/{mod version}/`"
        inputs.property("version", project.property("mod.version"))
        from(jar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/${project.property("mod.version")}"))
        dependsOn("build")
    }
}
