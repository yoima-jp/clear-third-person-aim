package io.github.yoima.clearthirdpersonaim.render;

import io.github.yoima.clearthirdpersonaim.ClearThirdPersonAim;
import io.github.yoima.clearthirdpersonaim.client.ClearAimClient;
import io.github.yoima.clearthirdpersonaim.core.AimTransparencyRenderer;


/**
 * オフスクリーン合成とフォールバック (パーツ単位の半透明) のどちらで描くかを決める。
 *
 * <p>判定理由:
 * <ul>
 *   <li>Iris などのシェーダーパック使用中は、エンティティ描画がシェーダーパック側の G バッファに流れるため、
 *       別フレームバッファへのキャプチャと合成が正しく動かない (最悪クラッシュ)。自動でフォールバックする。</li>
 *   <li>合成用パイプラインのコンパイル失敗や描画中の例外が一度でも起きたら、そのセッションではフォールバックに固定する。
 *       毎フレーム例外を出し続けてログとフレームレートを潰さないため。</li>
 *   <li>設定の forceFallbackRenderer はデバッグ・比較用。</li>
 * </ul>
 * 方式が変わったときだけ INFO ログを出す (毎フレーム出すとログが埋まるため)。
 */
public final class RenderMethodSelector implements AimTransparencyRenderer {
    private static final String IRIS_API = "net.irisshaders.iris.api.v0.IrisApi";

    private String compositorFailure;
    private Method lastLoggedMethod;
    private String lastLoggedReason;

    private Object irisApi;
    private java.lang.reflect.Method irisShaderPackInUse;
    private boolean irisLookedUp;

    @Override
    public Method currentMethod() {
        return decide(null);
    }

    @Override
    public String methodReason() {
        StringBuilder reason = new StringBuilder();
        decide(reason);
        return reason.toString();
    }

    /**
     * 設定画面に出す理由の翻訳キー。通常 (オフスクリーン合成) のときは null で、理由は表示しない。
     * フォールバックの理由はユーザーが対処できる情報 (シェーダーパック・設定) なので表示する。
     */
    public String reasonTranslationKey() {
        ClearAimClient client = ClearAimClient.get();
        if (client != null && client.config().forceFallbackRenderer) return "config.clear_third_person_aim.reason.forced";
        if (compositorFailure != null) return "config.clear_third_person_aim.reason.failed";
        if (isShaderPackInUse()) return "config.clear_third_person_aim.reason.shader_pack";
        return null;
    }

    private Method decide(StringBuilder reasonOut) {
        Method method;
        String reason;
        ClearAimClient client = ClearAimClient.get();
        if (client != null && client.config().forceFallbackRenderer) {
            method = Method.FALLBACK_TRANSLUCENT;
            reason = "forced by config";
        } else if (compositorFailure != null) {
            method = Method.FALLBACK_TRANSLUCENT;
            reason = "offscreen compositing failed: " + compositorFailure;
        } else if (isShaderPackInUse()) {
            method = Method.FALLBACK_TRANSLUCENT;
            reason = "shader pack in use";
        } else {
            method = Method.OFFSCREEN_COMPOSITE;
            reason = "default";
        }
        if (reasonOut != null) reasonOut.append(reason);
        if (method != lastLoggedMethod || !reason.equals(lastLoggedReason)) {
            lastLoggedMethod = method;
            lastLoggedReason = reason;
            ClearThirdPersonAim.LOGGER.info("Transparency renderer: {} ({})", method, reason);
        }
        return method;
    }

    /** 合成が使えなくなったことを記録する。以後このセッションではフォールバックを使う。 */
    public void markCompositorFailed(String reason, Throwable error) {
        if (compositorFailure == null) {
            compositorFailure = reason;
            ClearThirdPersonAim.LOGGER.error("Offscreen compositing disabled: {}", reason, error);
        }
    }

    /**
     * Iris の公開 API をリフレクションで呼ぶ。Iris はコンパイル時依存にしない (無い環境でもクラスロードで落ちないように)。
     */
    private boolean isShaderPackInUse() {
        if (!irisLookedUp) {
            irisLookedUp = true;
            try {
                Class<?> api = Class.forName(IRIS_API);
                irisApi = api.getMethod("getInstance").invoke(null);
                irisShaderPackInUse = api.getMethod("isShaderPackInUse");
                ClearThirdPersonAim.LOGGER.info("Iris detected; will switch to the fallback renderer while a shader pack is active");
            } catch (ReflectiveOperationException | LinkageError notInstalled) {
                irisApi = null;
            }
        }
        if (irisApi == null) return false;
        try {
            return (boolean) irisShaderPackInUse.invoke(irisApi);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // 判定できないなら安全側 (フォールバック) に倒す。
            return true;
        }
    }
}
