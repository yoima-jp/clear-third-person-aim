#version 120
// Clear Third Person Aim の互換性テスト専用の最小シェーダーパック。最終画面をそのまま出力するだけ。
varying vec2 texcoord;

void main() {
    gl_Position = ftransform();
    texcoord = gl_MultiTexCoord0.st;
}
