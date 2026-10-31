#version 150

// Project Hero v0.17.2: Wildmutt's blind sense. He has no eyes -- smell, hearing / echolocation and the heat-sensing gills on
// his neck. The world is near-black; an echolocation pulse sweeps out from him every so often and lights the shapes it passes
// (depth edges and block faces) in pale sonar blue, fading behind it; up close he "feels" the dim grey shapes of things.
// Creatures are not drawn here: the scent outlines (WildmuttSense) are rendered after this pass, in colour.

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;

uniform float Pulse; // the echo front's distance from him, in blocks (negative = none)
uniform float Reach; // how far an echo carries
uniform float Far;   // the camera's far plane
uniform float Sniff; // 0..1 while he sniffs: he senses further and clearer

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

const float NEAR = 0.05;

float raw(vec2 uv) {
    return texture(DiffuseDepthSampler, uv).r;
}

float lin(vec2 uv) {
    float z = raw(uv) * 2.0 - 1.0;
    return 2.0 * NEAR * Far / (Far + NEAR - z * (Far - NEAR));
}

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec3 col = texture(DiffuseSampler, texCoord).rgb;
    float depth = raw(texCoord);
    if (depth >= 0.99999) {
        // the sky: nothing to echo off
        fragColor = vec4(0.008, 0.01, 0.02, 1.0);
        return;
    }
    float d = lin(texCoord);

    // shape edges: depth creases (corners, silhouettes) and block-face / texture edges
    vec2 ox = vec2(oneTexel.x, 0.0), oy = vec2(0.0, oneTexel.y);
    float dl = lin(texCoord - ox), dr = lin(texCoord + ox), du = lin(texCoord - oy), dd = lin(texCoord + oy);
    float crease = (abs(dl + dr - 2.0 * d) + abs(du + dd - 2.0 * d)) / max(d, 0.6);
    float gx = luma(texture(DiffuseSampler, texCoord + ox).rgb) - luma(texture(DiffuseSampler, texCoord - ox).rgb);
    float gy = luma(texture(DiffuseSampler, texCoord + oy).rgb) - luma(texture(DiffuseSampler, texCoord - oy).rgb);
    // (texture edges fade with distance, so far grass is not a field of speckle)
    float edge = clamp(crease * 8.0 + length(vec2(gx, gy)) * 1.2 * (1.0 - smoothstep(4.0, 16.0, d)), 0.0, 1.0);

    float g = luma(col);
    float reach = Reach * (1.0 + Sniff * 0.6);

    // the echo: a bright front and a fading wake behind it
    float front = 0.0, wake = 0.0;
    if (Pulse >= 0.0) {
        float x = Pulse - d;
        front = exp(-x * x * 0.7);
        wake = x > 0.0 ? exp(-x / (5.0 + Sniff * 6.0)) : 0.0;
        float carry = 1.0 - smoothstep(reach * 0.6, reach, d);
        front *= carry;
        wake *= carry;
    }

    // close by he feels the shapes of things: a dim grey world within a few blocks
    float feel = 1.0 - smoothstep(2.5, 7.0 + Sniff * 8.0, d);
    // far away, only faint edges stay in memory
    float memory = 0.10 * (1.0 - smoothstep(8.0, reach, d)) + 0.06;

    vec3 base = vec3(0.012, 0.016, 0.032) + vec3(0.42, 0.46, 0.55) * g * feel * 0.55;
    vec3 sonar = vec3(0.50, 0.82, 1.0);
    // the front is a visible ring sweeping over the ground and walls, brightest on edges
    vec3 lit = sonar * (edge * (memory + feel * 0.25 + wake * 0.9 + front * 1.8) + front * (0.35 + g * 0.5) + wake * g * 0.28);
    fragColor = vec4(base + lit, 1.0);
}
