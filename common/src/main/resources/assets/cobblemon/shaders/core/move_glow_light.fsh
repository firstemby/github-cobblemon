#version 150

// Screen-space colored "dynamic light" for glowing Cobblemon moves. Runs as a fullscreen additive
// pass AFTER the world (and shaderpack) has composited, reading the scene depth to find each surface's
// position and adding the move's color with distance falloff. This tints the actual blocks/entities
// near the move in its color (no visible shape), complementing LambDynamicLights' monochrome level.

uniform sampler2D DepthSampler;   // main framebuffer depth (full world depth at this stage)
uniform mat4 InverseProjMat;      // inverse of the world projection, to rebuild view-space position
uniform vec3 LightViewPos;        // the move emitter, in view space
uniform vec3 LightColor;          // the move's color (0..1)
uniform float LightRadius;        // falloff radius in blocks
uniform float Intensity;          // overall strength (0..1)

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float depth = texture(DepthSampler, texCoord).r;

    // Don't light the sky / far plane.
    if (depth >= 1.0) {
        fragColor = vec4(0.0);
        return;
    }

    // Reconstruct the surface position in view space from depth.
    vec4 ndc = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPos = InverseProjMat * ndc;
    viewPos.xyz /= viewPos.w;

    float dist = distance(viewPos.xyz, LightViewPos);
    float atten = clamp(1.0 - dist / LightRadius, 0.0, 1.0);
    atten *= atten; // quadratic falloff reads more like real light

    fragColor = vec4(LightColor * atten * Intensity, 1.0);
}
