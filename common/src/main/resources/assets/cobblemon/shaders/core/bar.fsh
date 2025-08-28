#version 150

in vec2 texCoord0;
in vec4 vertexColor;

uniform sampler2D Sampler0; // base texture
uniform sampler2D Sampler3; // overlay texture

out vec4 fragColor;

void main() {
    vec4 baseColor = texture(Sampler0, texCoord0);

    fragColor = baseColor;
    return;

    vec4 overlayColor = texture(Sampler3, texCoord0);

    // Apply overlay at 50% opacity, masked by base alpha
    float overlayAlpha = 0.5; // overlay contributes 50%
    vec3 blendedRGB = mix(baseColor.rgb, overlayColor.rgb, overlayAlpha);

    // Keep alpha same as base
    fragColor = vec4(blendedRGB, baseColor.a) * vertexColor;
}