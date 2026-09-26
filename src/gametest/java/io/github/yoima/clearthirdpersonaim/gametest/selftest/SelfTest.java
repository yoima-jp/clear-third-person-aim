package io.github.yoima.clearthirdpersonaim.gametest.selftest;

import io.github.yoima.clearthirdpersonaim.ClearThirdPersonAim;
import io.github.yoima.clearthirdpersonaim.gametest.AimScenario;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

import java.nio.file.Path;
import java.util.function.Function;

/**
 * Fabric のクライアントゲームテスト API が使えない環境 (NeoForge 全バージョン、Fabric 1.21.1) 向けの自作テストランナー。
 *
 * <p>MOD 本体は {@code -Dcta.selftest=true} のときだけ、このクラスをリフレクションで起動する
 * (テストコードは開発用の実行構成にだけ含まれ、配布 jar には入らない)。
 * 流れ: タイトル画面を待つ → スーパーフラットのワールドを作る → {@link AimScenario} を実行 → 結果を書いてゲームを終了する。
 * 終了コードは成功 0 / 失敗 1 で、Gradle の実行タスクの成否になる。
 */
public final class SelfTest {
    private SelfTest() {
    }

    /**
     * @param loader              ローダー名 (結果の見出し用)
     * @param configDir           設定ディレクトリ
     * @param configScreenFactory ローダーの MOD 一覧から開く設定画面のファクトリ (連携の確認用、無ければ null)
     */
    public static void start(String loader, Path configDir, Function<Screen, Screen> configScreenFactory) {
        Thread thread = new Thread(() -> run(loader, configDir, configScreenFactory), "Clear Third Person Aim self test");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run(String loader, Path configDir, Function<Screen, Screen> configScreenFactory) {
        // NeoForge では MOD の初期化が Minecraft インスタンスの生成より前に走るので、生成を待つ。
        Minecraft mc;
        while ((mc = Minecraft.getInstance()) == null) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                return;
            }
        }
        SelfTestDriver driver = new SelfTestDriver(mc);
        AimScenario scenario = null;
        int exitCode = 1;
        try {
            // 初回起動ではタイトル画面の前にアクセシビリティ案内などが出ることがあるので、読み込みが終わって
            // 何かの画面が出た時点で進む (ワールド作成は現在の画面に関係なく行える)。
            driver.waitFor("main menu", m -> SelfTestDriver.currentScreen(m) != null && SelfTestDriver.hasNoOverlay(m), 20 * 60 * 5);
            String startScreen = driver.computeOnClient(m -> SelfTestDriver.currentScreen(m).getClass().getName());
            ClearThirdPersonAim.LOGGER.info("[client-test] starting from screen {}", startScreen);
            driver.runOnClient(m -> m.options.pauseOnLostFocus = false);
            driver.createFlatWorld();
            driver.waitFor("world", m -> m.level != null && m.player != null && SelfTestDriver.currentScreen(m) == null, 20 * 60 * 3);
            driver.waitTicks(60);
            scenario = new AimScenario(driver, loader, configDir);
            scenario.run();
            if (configScreenFactory != null) {
                String screenClass = driver.computeOnClient(m -> configScreenFactory.apply(null).getClass().getName());
                scenario.results().add("[" + loader + "] mod list config screen factory creates: " + screenClass);
                scenario.check(screenClass.endsWith(".ConfigScreen"), "the mod list should open ConfigScreen, got " + screenClass);
            }
            exitCode = scenario.failures().isEmpty() ? 0 : 1;
        } catch (Throwable t) {
            ClearThirdPersonAim.LOGGER.error("[client-test] self test aborted", t);
            if (scenario != null) scenario.check(false, "aborted: " + t);
        } finally {
            if (scenario != null) scenario.writeResults();
        }
        ClearThirdPersonAim.LOGGER.info("[client-test] finished with exit code {}", exitCode);
        System.exit(exitCode);
    }
}
