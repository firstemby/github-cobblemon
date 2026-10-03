#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float GameTime;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;

out vec4 fragColor;

vec2 hash2(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return fract(sin(p) * 43758.5453);
}

vec4 voronoi(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float f1 = 8.0, f2 = 8.0;
    vec2 id = vec2(0.0);
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(x, y);
            vec2 o = hash2(i + g);
            float d = length(g + o - f);
            if (d < f1) { f2 = f1; f1 = d; id = i + g; }
            else if (d < f2) { f2 = d; }
        }
    }
    return vec4(f1, f2 - f1, id);
}

vec3 rainbow(float t) {
    return 0.5 + 0.5 * cos(6.28318 * (t + vec3(0.0, 0.33, 0.67)));
}

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) discard;

    float seconds = GameTime * 1200.0;
    vec3 tint = vertexColor.rgb;
    bool stellar = min(tint.r, min(tint.g, tint.b)) > 0.95;

    vec2 texel = texCoord0 * vec2(textureSize(Sampler0, 0)) / 3.0;
    vec4 v = voronoi(texel);
    vec2 cellRand = hash2(v.zw) * 2.0 - 1.0;
    vec3 n = normalize(viewNormal + vec3(cellRand * 0.6, 0.0));
    vec3 viewDir = normalize(-viewPos);

    float ndv = clamp(abs(dot(n, viewDir)), 0.0, 1.0);
    float fresnel = pow(1.0 - ndv, 3.0);
    vec3 lightDir = normalize(vec3(0.4, 0.8, 0.6));
    float spec = pow(max(dot(reflect(-viewDir, n), lightDir), 0.0), 32.0);
    float edge = 1.0 - smoothstep(0.0, 0.08, v.y);

    float sweep = smoothstep(0.92, 1.0, sin(dot(texCoord0, vec2(6.0, 3.0)) * 3.0 - seconds * 2.0) * 0.5 + 0.5);
    float twinkle = step(0.97, fract(hash2(v.zw).x + seconds * 0.35)) * 0.8;

    vec3 crystal = stellar ? rainbow(ndv * 1.5 + cellRand.x * 0.15 + seconds * 0.1) : tint;
    float luma = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    vec3 body = crystal * (0.45 + 0.55 * luma);
    vec3 shine = mix(crystal, vec3(1.0), 0.6) * (spec + edge * 0.5 + sweep * 0.6 + twinkle + fresnel * 0.8);
    vec3 color = body + shine;

    color *= mix(lightMapColor.rgb, vec3(1.0), 0.5);

    float alpha = clamp(0.55 + fresnel * 0.4 + edge * 0.3 + spec, 0.0, 1.0);
    fragColor = linear_fog(vec4(color, alpha) * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
}
