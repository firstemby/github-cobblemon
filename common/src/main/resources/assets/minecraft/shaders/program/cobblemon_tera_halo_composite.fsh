#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D MaskSampler;
uniform float Strength;

in vec2 texCoord;

out vec4 fragColor;

// How much the glow is dimmed over the mon itself, so the crystal overlay stays readable
const float INNER_DIM = 0.9;

void main() {
    vec3 glow = texture(DiffuseSampler, texCoord).rgb;
    float inside = texture(MaskSampler, texCoord).a;
    fragColor = vec4(min(glow * Strength, vec3(1.0)) * (1.0 - INNER_DIM * inside), 1.0);
}
