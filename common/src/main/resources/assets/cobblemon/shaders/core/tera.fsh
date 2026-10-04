#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform float GameTime;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in float vanillaLight;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;

out vec4 fragColor;

const float CELL_SIZE_TEXELS = 16.0 / 3.0;
const float FACET_TILT = 0.7;
const bool SNAP_TO_TEXELS = true;
const int DISTANCE_METRIC = 0;

const float AMBIENT = 0.5;
const float SPEC_SHININESS = 5.0;
const float SPEC_STRENGTH = 0.4;
const float SPEC_WHITENESS = 0.25;
const float SELF_LIGHT = 0.85;
const vec3 LIGHT_DIR = vec3(0.5, 0.8, 0.6);

const float SHADE_SPREAD = 2.0;
const float SHEEN_STRENGTH = 0.25;
const float FILM_IOR = 1.45;
const float FILM_THICKNESS_MIN = 250.0;
const float FILM_THICKNESS_MAX = 550.0;
const float FILM_WAVELENGTH = 550.0;

const float SHIMMER_NM = 40.0;
const float SHIMMER_SPEED = 1.5;

const float GLIMMER_PERIOD = 3.0;
const float GLIMMER_SHARPNESS = 60.0;
const float GLIMMER_STRENGTH = 0.3;

const float SECONDS_PER_DAY = 1200.0;
const float PI = 3.14159265;
const float TAU = 6.28318531;

vec2 random2(vec2 p) {
    return fract(sin(vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)))) * 43758.5453);
}

float cellDistance(vec2 d) {
    if (DISTANCE_METRIC == 1) return abs(d.x) + abs(d.y);
    if (DISTANCE_METRIC == 2) return max(abs(d.x), abs(d.y));
    return length(d);
}

vec2 cellId(vec2 st) {
    vec2 i_st = floor(st);
    vec2 f_st = fract(st);

    vec2 nearestCell = vec2(0.0);
    float m_dist = 8.0;

    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 neighbor = vec2(float(x), float(y));
            vec2 point = random2(i_st + neighbor);
            float dist = cellDistance(neighbor + point - f_st);

            if (dist < m_dist) {
                m_dist = dist;
                nearestCell = neighbor;
            }
        }
    }

    return i_st + nearestCell;
}

void faceAxes(vec3 normal, out vec3 tangent, out vec3 bitangent) {
    vec3 dp1 = dFdx(viewPos);
    vec3 dp2 = dFdy(viewPos);
    vec2 duv1 = dFdx(texCoord0);
    vec2 duv2 = dFdy(texCoord0);

    vec3 dp2perp = cross(dp2, normal);
    vec3 dp1perp = cross(normal, dp1);
    tangent = normalize(dp2perp * duv1.x + dp1perp * duv2.x);
    bitangent = normalize(dp2perp * duv1.y + dp1perp * duv2.y);
}

vec3 facetNormal(vec3 normal, vec3 tangent, vec3 bitangent, vec2 id) {
    float angle = random2(id).x * TAU;
    vec2 lean = vec2(cos(angle), sin(angle)) * FACET_TILT;
    return normalize(normal + lean.x * tangent + lean.y * bitangent);
}

float thinFilm(vec3 normal, vec3 viewDir, float thicknessNm) {
    float cosI = clamp(dot(normal, viewDir), 0.0, 1.0);
    float sinT2 = (1.0 - cosI * cosI) / (FILM_IOR * FILM_IOR);
    float cosT = sqrt(1.0 - sinT2);
    float extraPath = 2.0 * FILM_IOR * thicknessNm * cosT;
    float phase = TAU * extraPath / FILM_WAVELENGTH + PI;
    return 0.5 + 0.5 * cos(phase);
}

vec3 teraSheen(vec3 teraColor, float film) {
    vec3 teraDeep = pow(teraColor, vec3(SHADE_SPREAD));
    return mix(teraDeep, teraColor, film);
}

vec2 shade(vec3 normal, vec3 lightDir, vec3 viewDir) {
    float diffuse = AMBIENT + (1.0 - AMBIENT) * max(dot(normal, lightDir), 0.0);
    vec3 h = normalize(lightDir + viewDir);
    float spec = pow(max(dot(normal, h), 0.0), SPEC_SHININESS) * SPEC_STRENGTH;
    return vec2(diffuse, spec);
}

void main() {
    vec4 albedo = texture(Sampler0, texCoord0);
    if (albedo.a < 0.1) discard;

    float seconds = GameTime * SECONDS_PER_DAY;
    vec3 normal = normalize(viewNormal);
    vec3 viewDir = normalize(-viewPos);
    vec3 lightDir = normalize(LIGHT_DIR);

    vec2 facetUv = texCoord0 * vec2(textureSize(Sampler0, 0));
    if (SNAP_TO_TEXELS) {
        facetUv = floor(facetUv) + 0.5;
    }
    vec2 id = cellId(facetUv / CELL_SIZE_TEXELS);

    vec3 tangent, bitangent;
    faceAxes(normal, tangent, bitangent);
    vec3 n = facetNormal(normal, tangent, bitangent, id);

    vec2 rnd = random2(id + 17.0);
    float thickness = mix(FILM_THICKNESS_MIN, FILM_THICKNESS_MAX, rnd.x);
    thickness += sin(seconds * SHIMMER_SPEED + TAU * rnd.y) * SHIMMER_NM;
    vec3 film = teraSheen(vertexColor.rgb, thinFilm(n, viewDir, thickness));

    float glimmerPhase = seconds / GLIMMER_PERIOD + random2(id + 53.0).x;
    float glimmer = pow(0.5 + 0.5 * cos(TAU * glimmerPhase), GLIMMER_SHARPNESS) * GLIMMER_STRENGTH;

    vec2 light = shade(n, lightDir, viewDir);
    float facetShading = light.x / shade(normal, lightDir, viewDir).x;

    vec3 selfLit = mix(lightMapColor.rgb, vec3(1.0), SELF_LIGHT);
    float fade = linear_fog_fade(vertexDistance, FogStart, FogEnd);
    float keep = mix(1.0, min(facetShading, 1.0), fade);

    vec3 base = albedo.rgb * vanillaLight * lightMapColor.rgb;
    vec3 target = albedo.rgb * vanillaLight * selfLit * facetShading;
    vec3 brighten = max(target - base * min(facetShading, 1.0), 0.0);
    vec3 sparkle = mix(film, vec3(1.0), SPEC_WHITENESS) * (light.y + glimmer);
    vec3 reflection = (sparkle + film * SHEEN_STRENGTH) * selfLit;

    vec3 added = (brighten + reflection) * fade * ColorModulator.rgb;

    fragColor = vec4(added, keep);
}
