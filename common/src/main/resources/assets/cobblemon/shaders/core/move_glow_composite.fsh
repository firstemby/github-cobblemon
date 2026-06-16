#version 150

// Composites the move-glow accumulation buffer onto the scene. The buffer was filled with a MAX blend
// so overlapping move lights kept the brightest contribution (they don't sum), and this adds that
// result to the scene once. Alpha is forced to 0 so the additive composite doesn't touch scene alpha.

uniform sampler2D GlowSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    fragColor = vec4(texture(GlowSampler, texCoord).rgb, 0.0);
}
