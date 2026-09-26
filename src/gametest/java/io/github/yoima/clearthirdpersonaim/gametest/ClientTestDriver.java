package io.github.yoima.clearthirdpersonaim.gametest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * テストシナリオ ({@link AimScenario}) がクライアントを操作するための最小限の窓口。
 *
 * <p>実装は 2 つ:
 * <ul>
 *   <li>Fabric のクライアントゲームテスト API に委譲するもの (1.21.4 以降の Fabric)</li>
 *   <li>自作のドライバ (NeoForge 全バージョンと、ゲームテスト API の無い Fabric 1.21.1)。
 *       テスト用スレッドからクライアントスレッドへ処理を投げ、クライアント tick を数えて待つ。</li>
 * </ul>
 * どのメソッドもテスト用スレッドから呼ぶ (クライアントスレッドから呼ぶとデッドロックする)。
 */
public interface ClientTestDriver {
    /** クライアント tick を n 回進める (描画も行われる)。 */
    void waitTicks(int ticks);

    <T> T computeOnClient(Function<Minecraft, T> function);

    default void runOnClient(Consumer<Minecraft> action) {
        computeOnClient(mc -> {
            action.accept(mc);
            return null;
        });
    }

    /** 統合サーバーでコマンドを実行する (権限レベル最大)。 */
    void runCommand(String command);

    /** 現在の画面を撮影し、指定ディレクトリに {@code name.png} で保存してパスを返す。 */
    Path screenshot(String name, Path directory);

    /** 使用キー (右クリック) を押しっぱなし / 離す。 */
    void holdUse(boolean down);

    void setScreen(Supplier<Screen> screen);

    /** GUI 座標 (GUI スケール適用前) を左クリックする。 */
    void clickGui(int guiX, int guiY);

    /** メッセージが指定の翻訳キーのボタンを押す。 */
    void clickButton(String translationKey);
}
