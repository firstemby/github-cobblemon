#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out float vanillaLight;
out vec2 texCoord0;
out vec3 viewPos;
out vec3 viewNormal;

void main() {
    // Same expression as vanilla so the depth matches the base model exactly, otherwise the overlay z-fights
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vec4 vp = ModelViewMat * vec4(Position, 1.0);
    viewPos = vp.xyz;
    viewNormal = normalize(mat3(ModelViewMat) * Normal);

    vertexDistance = fog_distance(Position, FogShape);
    vertexColor = Color;
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    // Same shading the base model got, so the fragment shader knows what's underneath
    vanillaLight = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, vec4(1.0)).r;
    texCoord0 = UV0;
}
