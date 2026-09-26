plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.3-fabric"

stonecutter parameters {
    val (version, loader) = current.project.split('-', limit = 2)

    properties {
        tags(version, loader)
    }

    // `//? if fabric {` / `//? if neoforge {` を使えるようにする。
    constants {
        match(loader, "fabric", "neoforge")
    }

    swaps["mod_version"] = "\"${properties.get<String>("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"

    replacements {
        // 1.21.11 で ResourceLocation が Identifier に改名された。Stonecutter の置換は双方向なので、古い版へ切り替えると元に戻る。
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }
    }
}

// 全バージョン × ローダーの配布用 jar を build/libs/{mod version}/ に集める。
tasks.register("buildAndCollectAll") {
    group = "build"
    dependsOn(stonecutter.tasks.named("buildAndCollect"))
}
