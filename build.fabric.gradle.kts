plugins {
    // MC バージョンに応じて Loom の remap 版 (1.21.x) / non-remap 版 (26.x) を切り替えて適用する。
    id("dev.kikugie.loom-back-compat")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "${property("mod.id") as String}-fabric"

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

repositories {
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

// MC 非依存のコアロジックはソースとして取り込み、1 つの jar に同梱する(jar-in-jar を避けて依存を単純にする)。
sourceSets.main {
    java.srcDir(rootProject.file("common/src/main/java"))
    // NeoForge 専用パッケージは Fabric の classpath では解決できないのでコンパイル対象から外す。
    java.exclude("**/neoforge/**")
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    // 1.21.x では Mojang 公式マッピングを使い、全バージョンのソースを同じ名前で書けるようにする。
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")

    // Mod Menu は任意依存。compileOnly 相当で参照し、実行時に無ければ連携クラスは読み込まれない。
    sc.properties.getOrNull<String>("deps.modmenu")?.let {
        modCompileOnly("maven.modrinth:modmenu:$it")
        if (providers.gradleProperty("cta.withModMenu").isPresent) modLocalRuntime("maven.modrinth:modmenu:$it")
    }
    // 互換性検証用 (Phase 8)。-Pcta.withSodium / -Pcta.withIris を付けたときだけ開発クライアントに入れる。
    sc.properties.getOrNull<String>("deps.sodium")?.let {
        if (providers.gradleProperty("cta.withSodium").isPresent || providers.gradleProperty("cta.withIris").isPresent)
            modLocalRuntime("maven.modrinth:sodium:$it")
    }
    sc.properties.getOrNull<String>("deps.iris")?.let {
        if (providers.gradleProperty("cta.withIris").isPresent) modLocalRuntime("maven.modrinth:iris:$it")
    }
}

// Fabric のクライアントゲームテスト API は 1.21.4 以降にしか無い。1.21.1 では作らない (KNOWN_ISSUES.md 参照)。
val hasClientGameTest = sc.current.parsed >= "1.21.4"
if (hasClientGameTest) {
    fabricApi {
        configureTests {
            createSourceSet = true
            modId = "clear_third_person_aim_gametest"
            enableGameTests = false
            enableClientGameTests = true
            eula = true
        }
    }
}

loom {
    if (hasClientGameTest) {
        runs.named("clientGameTest") {
            // スクリーンショットと数値結果をルートの build/test-screenshots/<version>-fabric/ に集める。
            vmArg("-Dcta.screenshotDir=" + rootProject.layout.buildDirectory.dir("test-screenshots/${sc.current.version}-fabric").get().asFile.absolutePath)
        }
    }

    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")

    decompilerOptions.named("vineflower") {
        options.put("mark-corresponding-synthetics", "1")
    }

    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
    }
    // 手動起動用の実行ディレクトリはバージョンごとに分ける。ゲームテストは毎回削除される専用ディレクトリ
    // (Loom 既定の build/run/clientGameTest) を使い、手動用のワールドや設定を消さない。
    runConfigs.matching { it.name != "clientGameTest" }.configureEach {
        runDirectory = rootProject.file("run/fabric-${sc.current.version}")
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
            register("loader", Regex("""\d+\.\d+""").find(sc.properties.get<String>("deps.fabric_loader"))!!.value)
        }

        filesMatching("fabric.mod.json") { expand(props) }

        val mixinJava = "JAVA_${requiredJava.majorVersion}"
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }

        exclude("META-INF/neoforge.mods.toml")
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
        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/${project.property("mod.version")}"))
        dependsOn("build")
    }
}
