#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
// How far the blur reaches, in pixels on a 1080p screen
uniform float Radius;

in vec2 texCoord;
in vec2 sampleStep;

out vec4 fragColor;

const float WEIGHTS[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);

void main() {
    vec2 tap = sampleStep * Radius * (InSize.y / 1080.0) / 4.0;
    vec4 color = texture(DiffuseSampler, texCoord) * WEIGHTS[0];
    for (int i = 1; i < 5; i++) {
        color += texture(DiffuseSampler, texCoord + tap * float(i)) * WEIGHTS[i];
        color += texture(DiffuseSampler, texCoord - tap * float(i)) * WEIGHTS[i];
    }
    fragColor = color;
}
