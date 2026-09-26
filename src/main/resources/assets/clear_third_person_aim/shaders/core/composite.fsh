#version 330
#extension GL_ARB_separate_shader_objects : require

// Clear Third Person Aim: オフスクリーンに描いたローカルプレイヤーを、マスク付きでメイン画面へ合成する。
// マスクの式は common/.../MaskMath.java と同じ。片方を変えたらもう片方と MaskMathTest も更新すること。

uniform sampler2D PlayerColor;
uniform sampler2D PlayerDepth;

layout(std140) uniform ClearAimParams {
    vec4 Params0;     // x: mode (0 FULL_BODY, 1 UPPER_BODY, 2 AIM_ELLIPSE), y: progress, z: remainingOpacity, w: feather
    vec4 Params1;     // x: radiusX, y: radiusY (画面高さ比), z: waistHeight, w: waistFeather (ブロック)
    vec4 Params2;     // xy: 画面サイズ (px)
    vec4 FeetPos;     // xyz: プレイヤー足元 (カメラ相対, ワールド軸)
    mat4 InvViewProj; // クリップ座標 -> カメラ相対ワールド座標
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

// GLSL の smoothstep は edge0 >= edge1 で未定義なので、ぼかし 0 のときは段差関数にする。
float safeSmoothstep(float edge0, float edge1, float x) {
    if (edge1 <= edge0) {
        return x < edge0 ? 0.0 : 1.0;
    }
    return smoothstep(edge0, edge1, x);
}

float transparencyWeight(float depth) {
    int mode = int(Params0.x + 0.5);
    if (mode == 2) {
        // 画面中央 (クロスヘア) からの距離を画面の高さで正規化し、アスペクト比に関係なく楕円の形を保つ。
        vec2 size = Params2.xy;
        vec2 d = (gl_FragCoord.xy - size * 0.5) / size.y;
        float dist = length(d / Params1.xy);
        return 1.0 - safeSmoothstep(1.0 - Params0.w, 1.0 + Params0.w, dist);
    }
    if (mode == 1) {
        // 深度からワールド位置を復元し、足元からの高さで上半身/下半身を分ける。
        // パーツ単位ではなく高さで分けるので、1 枚のバッファの中で重なり (胴体の裏の腕など) が正しく処理される。
#ifdef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
        float ndcZ = depth;
#else
        float ndcZ = depth * 2.0 - 1.0;
#endif
        vec4 world = InvViewProj * vec4(texCoord * 2.0 - 1.0, ndcZ, 1.0);
        float height = world.y / world.w - FeetPos.y;
        return safeSmoothstep(Params1.z - Params1.w, Params1.z + Params1.w, height);
    }
    return 1.0;
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    float depth = texelFetch(PlayerDepth, pixel, 0).r;
    // reversed-Z でクリアした 0 のままならプレイヤーが描かれていない。
    if (depth == 0.0) {
        discard;
    }
    vec4 color = texelFetch(PlayerColor, pixel, 0);
    float opacity = 1.0 - (1.0 - Params0.z) * Params0.y * transparencyWeight(depth);

#ifdef CTPA_OPAQUE_PASS
    if (opacity < 0.999) {
        discard;
    }
    fragColor = color;
#else
    if (opacity >= 0.999) {
        discard;
    }
    fragColor = color * opacity;
#endif
    gl_FragDepth = depth;
}
