// Shared value-noise primitives for this mod's sky shaders — one real copy instead of eight
// identical ones (found while investigating dependencies.md's GLSL-noise-library suggestions:
// astronima_nebula/moon/sun_core/sun_halo/galaxy_disk/planetary_nebula/reflection_nebula/
// passing_body.fsh all carried byte-for-byte identical hash(vec2)/noise(vec2) blocks, exactly
// the "same model, quietly able to drift" risk rule 46 already governs everywhere else in this
// project). `#moj_import <astronima:noise.glsl>` resolves to this file the same way vanilla's
// own `#moj_import <minecraft:dynamictransforms.glsl>` resolves to
// assets/minecraft/shaders/include/dynamictransforms.glsl — `Identifier`-based, not
// namespace-restricted to vanilla (verified directly in ShaderManager#createPreprocessor, rule 2).
//
// Deliberately does NOT include `fbm` — every caller's own octave count (4 or 5) and lacunarity
// (2.09/2.11/2.13) are real per-shader tuning, not accidental drift, and stay local to each file.

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// The galactic band's own 3D variant (astronima_galaxy.fsh) — a cube-lattice hash/noise for a
// direction on the sky dome rather than a 2D marker's local position. Kept in the same shared
// file since it is the same technique at one more dimension, not a different one.
float hash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yzx + 19.19);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = fract(p);
    vec3 s = f * f * (3.0 - 2.0 * f);

    float n000 = hash(cell + vec3(0.0, 0.0, 0.0));
    float n100 = hash(cell + vec3(1.0, 0.0, 0.0));
    float n010 = hash(cell + vec3(0.0, 1.0, 0.0));
    float n110 = hash(cell + vec3(1.0, 1.0, 0.0));
    float n001 = hash(cell + vec3(0.0, 0.0, 1.0));
    float n101 = hash(cell + vec3(1.0, 0.0, 1.0));
    float n011 = hash(cell + vec3(0.0, 1.0, 1.0));
    float n111 = hash(cell + vec3(1.0, 1.0, 1.0));

    float nx00 = mix(n000, n100, s.x);
    float nx10 = mix(n010, n110, s.x);
    float nx01 = mix(n001, n101, s.x);
    float nx11 = mix(n011, n111, s.x);
    float nxy0 = mix(nx00, nx10, s.y);
    float nxy1 = mix(nx01, nx11, s.y);
    return mix(nxy0, nxy1, s.z);
}
