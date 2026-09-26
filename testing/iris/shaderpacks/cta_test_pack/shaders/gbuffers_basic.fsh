#version 120
uniform sampler2D lightmap;
varying vec2 lmcoord;
varying vec4 glcolor;

void main() {
/* DRAWBUFFERS:0 */
    gl_FragData[0] = glcolor * texture2D(lightmap, lmcoord);
}
