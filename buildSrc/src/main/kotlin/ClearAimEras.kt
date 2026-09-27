/**
 * Minecraft の描画 API の世代ごとに、コンパイルするソースと適用する Mixin を決める。
 * Fabric / NeoForge の両方のビルドスクリプトから使う。
 *
 * - E4 (26.3〜): Renderpearl。レンダーパスを明示的に渡す。
 * - E3 (26.2): blaze3d + 準備済みフレーム (PreparedFrame)。出力先はテクスチャの上書きで切り替える。
 * - E2 (1.21.9〜26.1.x): blaze3d + FeatureRenderDispatcher が BufferSource へ即時描画する。
 * - E1 (〜1.21.8, 実際の対象は 1.21.1): レンダーステートも submit も無い古い描画系。
 *
 * 世代の境目を変えるときは、src/main/java の Stonecutter 条件 (//? if >=26.2 など) も合わせて確認すること。
 */
object ClearAimEras {
    fun era(atLeast: (String) -> Boolean): String = when {
        atLeast("26.3") -> "e4"
        atLeast("26.2") -> "e3"
        atLeast("1.21.9") -> "e2"
        else -> "e1"
    }

    private const val MIXIN = "io/github/yoima/clearthirdpersonaim/mixin"
    private const val RENDER = "io/github/yoima/clearthirdpersonaim/render"

    /** その世代でコンパイルしないソース (ソースセットの除外パターン)。 */
    fun excludedSources(era: String): List<String> = buildList {
        if (era == "e1") {
            add("$MIXIN/modern/**")
            add("$RENDER/modern/**")
        } else {
            add("$MIXIN/legacy/**")
            add("$RENDER/legacy/**")
            if (era != "e3") add("$MIXIN/modern/PreparedFrameMixin.java")
            if (era != "e2") {
                add("$MIXIN/modern/FeatureRenderDispatcherMixin.java")
                add("$RENDER/modern/BufferSourceCompositor.java")
            }
            if (era == "e2") add("$RENDER/modern/PassCompositor.java")
        }
    }

    /** mixins.json の "client" 配列に入れる Mixin クラス (パッケージ io.github.yoima.clearthirdpersonaim.mixin からの相対名)。 */
    fun mixins(era: String, needsGameRendererFovAccessor: Boolean): List<String> = when (era) {
        // 1.21.1 は LevelRenderer#renderLevel の引数に実際の射影行列が渡るので、GameRenderer への注入は無い。
        "e1" -> listOf(
            "GameRendererFovAccessor",
            "GuiCrosshairMixin",
            "legacy.EntityRenderDispatcherMixin",
            "legacy.ItemInHandLayerMixin",
            "legacy.LevelRendererMixin",
            "legacy.LivingEntityRendererMixin",
        )
        else -> buildList {
            if (needsGameRendererFovAccessor) add("GameRendererFovAccessor")
            add("GuiCrosshairMixin")
            add("modern.EntityRenderDispatcherMixin")
            add("modern.GameRendererMixin")
            add("modern.ItemInHandLayerMixin")
            add("modern.LevelRendererMixin")
            add("modern.LivingEntityRendererMixin")
            if (era == "e3") add("modern.PreparedFrameMixin")
            if (era == "e2") add("modern.FeatureRenderDispatcherMixin")
        }
    }

    /** processResources の expand 用 (JSON 配列の中身)。 */
    fun mixinsJson(era: String, needsGameRendererFovAccessor: Boolean): String =
        mixins(era, needsGameRendererFovAccessor).joinToString(", ") { "\"$it\"" }
}
