package io.github.yoima.clearthirdpersonaim.render.legacy;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import io.github.yoima.clearthirdpersonaim.ClearThirdPersonAim;
import io.github.yoima.clearthirdpersonaim.core.FrameParams;
import io.github.yoima.clearthirdpersonaim.core.MaskMath;
import io.github.yoima.clearthirdpersonaim.render.RenderHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

/**
 * 1.21.1 向けのオフスクリーン合成 (OpenGL を直接使う描画系)。
 *
 * <p>ローカルプレイヤーの描画内容を専用の BufferSource に受け、その場で専用の {@link TextureTarget} へ描き切る。
 * 合成は半透明の地形を描く直前に、自前の小さなシェーダープログラムで全画面三角形を描いて行う。
 * バニラのシェーダー登録 (ローダーごとに方法が違う) を使わず直接 GL を呼ぶのは、Fabric / NeoForge で同じコードにするため。
 * 使った GL の状態 (プログラム・VAO・テクスチャ・ブレンド・深度) は、バニラの状態キャッシュと食い違わないように戻す。
 *
 * <p>マスクの式は common の MaskMath と 26.x 用の composite.fsh と同じ。変更するときはすべて合わせること。
 */
public final class LegacyCompositor {
    private static final String VERTEX_SHADER = """
            #version 150
            out vec2 texCoord;
            void main() {
                vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
                texCoord = uv;
            }
            """;
    private static final String FRAGMENT_SHADER = """
            #version 150
            uniform sampler2D PlayerColor;
            uniform sampler2D PlayerDepth;
            uniform vec4 Params0;   // mode, progress, remainingOpacity, feather
            uniform vec4 Params1;   // radiusX, radiusY, waistHeight, waistFeather
            uniform vec4 Params2;   // width, height, depthZeroToOne, clearDepth
            uniform vec4 FeetPos;
            uniform mat4 InvViewProj;
            uniform int OpaquePass;
            in vec2 texCoord;
            out vec4 fragColor;

            float safeSmoothstep(float edge0, float edge1, float x) {
                if (edge1 <= edge0) return x < edge0 ? 0.0 : 1.0;
                return smoothstep(edge0, edge1, x);
            }

            float transparencyWeight(float depth) {
                int mode = int(Params0.x + 0.5);
                if (mode == 2) {
                    vec2 size = Params2.xy;
                    vec2 d = (gl_FragCoord.xy - size * 0.5) / size.y;
                    float dist = length(d / Params1.xy);
                    return 1.0 - safeSmoothstep(1.0 - Params0.w, 1.0 + Params0.w, dist);
                }
                if (mode == 1) {
                    float ndcZ = Params2.z > 0.5 ? depth : depth * 2.0 - 1.0;
                    vec4 world = InvViewProj * vec4(texCoord * 2.0 - 1.0, ndcZ, 1.0);
                    float height = world.y / world.w - FeetPos.y;
                    return safeSmoothstep(Params1.z - Params1.w, Params1.z + Params1.w, height);
                }
                return 1.0;
            }

            void main() {
                ivec2 pixel = ivec2(gl_FragCoord.xy);
                float depth = texelFetch(PlayerDepth, pixel, 0).r;
                if (depth == Params2.w) discard;
                vec4 color = texelFetch(PlayerColor, pixel, 0);
                float opacity = 1.0 - (1.0 - Params0.z) * Params0.y * transparencyWeight(depth);
                if (OpaquePass == 1) {
                    if (opacity < 0.999) discard;
                    fragColor = color;
                } else {
                    if (opacity >= 0.999) discard;
                    fragColor = color * opacity;
                }
                gl_FragDepth = depth;
            }
            """;

    private final MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(new ByteBufferBuilder(786432));
    private TextureTarget target;
    private long clearedFrame = -1;
    private int program;
    private int vao;
    private int locColor, locDepth, locParams0, locParams1, locParams2, locFeet, locInvViewProj, locOpaquePass;
    private final Matrix4f invViewProj = new Matrix4f();

    public MultiBufferSource.BufferSource buffer() {
        return bufferSource;
    }

    /**
     * 振り分けたプレイヤーの描画内容を専用ターゲットへ描き切る。エンティティの描画ループの途中で呼ばれるので、
     * 終わったらメインのターゲットへ書き込み先を戻す (バニラのループはメインが結び付いている前提)。
     */
    public void flushCapture() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        ensureTarget(main.width, main.height);
        if (clearedFrame != RenderHooks.levelFrame()) {
            target.setClearColor(0f, 0f, 0f, 0f);
            target.clear(Minecraft.ON_OSX);
            clearedFrame = RenderHooks.levelFrame();
        }
        target.bindWrite(false);
        bufferSource.endBatch();
        main.bindWrite(false);
    }

    /** 半透明の地形を描く直前に呼ぶ。 */
    public void composite(FrameParams params, Matrix4f projection, Matrix4f viewRotation) {
        ensureProgram();
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        main.bindWrite(false);

        invViewProj.set(projection).mul(viewRotation).invert();
        var feet = RenderHooks.feetRelativeToCamera();

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        GlStateManager._glUseProgram(program);
        GL20.glUniform1i(locColor, 0);
        GL20.glUniform1i(locDepth, 1);
        GL20.glUniform4f(locParams0, params.mode().ordinal(), params.progress(), params.remainingOpacity(), params.feather());
        GL20.glUniform4f(locParams1, params.radiusX(), params.radiusY(), params.waistHeight(), MaskMath.WAIST_FEATHER);
        // 1.21.1 は通常の深度 (-1..1, クリア値 1)。
        GL20.glUniform4f(locParams2, main.width, main.height, 0f, 1f);
        GL20.glUniform4f(locFeet, feet.x, feet.y, feet.z, 0f);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer matrix = stack.mallocFloat(16);
            invViewProj.get(matrix);
            GL20.glUniformMatrix4fv(locInvViewProj, false, matrix);
        }
        RenderSystem.activeTexture(GL13.GL_TEXTURE1);
        RenderSystem.bindTexture(target.getDepthTextureId());
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(target.getColorTextureId());
        GL30.glBindVertexArray(vao);

        // 不透明パス (深度を書く) → 半透明パス (深度を書かない)。理由は PassCompositor のクラスコメントを参照。
        GL20.glUniform1i(locOpaquePass, 1);
        RenderSystem.depthMask(true);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        GL20.glUniform1i(locOpaquePass, 0);
        RenderSystem.depthMask(false);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

        // バニラの状態キャッシュと食い違わないように戻す。
        GL30.glBindVertexArray(0);
        BufferUploader.invalidate();
        GameRenderer.getPositionShader().clear();
        RenderSystem.activeTexture(GL13.GL_TEXTURE1);
        RenderSystem.bindTexture(0);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(0);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private void ensureTarget(int width, int height) {
        if (target == null) {
            target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            ClearThirdPersonAim.LOGGER.debug("Allocated offscreen target {}x{}", width, height);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX);
        }
    }

    private void ensureProgram() {
        if (program != 0) return;
        int vs = compile(GL20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, vs);
        GL20.glAttachShader(p, fs);
        GL30.glBindFragDataLocation(p, 0, "fragColor");
        GL20.glLinkProgram(p);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(p);
            GL20.glDeleteProgram(p);
            throw new IllegalStateException("composite program failed to link: " + log);
        }
        program = p;
        locColor = GL20.glGetUniformLocation(p, "PlayerColor");
        locDepth = GL20.glGetUniformLocation(p, "PlayerDepth");
        locParams0 = GL20.glGetUniformLocation(p, "Params0");
        locParams1 = GL20.glGetUniformLocation(p, "Params1");
        locParams2 = GL20.glGetUniformLocation(p, "Params2");
        locFeet = GL20.glGetUniformLocation(p, "FeetPos");
        locInvViewProj = GL20.glGetUniformLocation(p, "InvViewProj");
        locOpaquePass = GL20.glGetUniformLocation(p, "OpaquePass");
        // コアプロファイルでは頂点属性が無くても VAO の結び付けが必要。
        vao = GL30.glGenVertexArrays();
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("composite shader failed to compile: " + log);
        }
        return shader;
    }
}
