#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <astronima:noise.glsl>

in vec2 localPos;

out vec4 fragColor;

// --- Named tuning constants (S1a: same reasoning as the emission nebula's own block).
const float SHEAR_RATE = 0.06;
const float SHEAR_CORE_RADIUS = 0.3;
const float STRETCH_Y = 2.4;
const float DRIFT_X = 0.05;
const float DRIFT_Y = 0.035;
const float COARSE_SCALE = 2.4;
const float DUST_SCALE = 4.0;
const float FINE_GRAIN_SCALE = 11.0;
const float DUST_ASYM_MIN = -0.15;
const float DUST_ASYM_MAX = 0.55;
const float DUST_ASYM_MIX = 0.3;
const float DUST_CUT_MIN = 0.35;
const float DUST_CUT_MAX = 0.55;
const vec3 WARM_CORE = vec3(0.95, 0.80, 0.56);
const vec3 MID_DISK = vec3(0.58, 0.48, 0.46);
const vec3 OUTER_DISK = vec3(0.26, 0.24, 0.30);
const float KNOT_COARSE_SCALE = 6.0;
const vec2 KNOT_COARSE_OFFSET = vec2(21.0, 44.0);
const float KNOT_FINE_SCALE = 13.0;
const vec2 KNOT_FINE_OFFSET = vec2(3.0, 61.0);

float fbm(vec2 p) {
    float value = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        value += amp * noise(p);
        p *= 2.13;
        amp *= 0.5;
    }
    return value;
}

// M31, the Andromeda Galaxy: real content is a whole galaxy, not a nebula - the fourth and last
// of this session's L4 objects, and the one furthest in kind from the other three. Dominated by
// the integrated light of its old bulge population (cool G-K giants - the same real absorption
// forest the Sun's own spectrum carries, which is why ObservationTarget.ANDROMEDA_GALAXY reuses
// the Sun's own line set almost unchanged), fading outward through a fainter, dustier disc with
// its own real star-forming regions.
void main() {
    float time = ModelOffset.x;

    // Differential rotation (design/sky.md §5.7's S7d) - the one real motion a disc galaxy
    // actually has: inner material orbits faster than outer. Applied as a radius-dependent shear
    // on the angle before the ellipse stretch below, so the bulge turns relative to the outer
    // disc and the shape itself moves, not only the noise drifting under it (§5.7.2's own
    // argument, applied here too - a fixed silhouette reads as static regardless of internal
    // drift speed). The +0.3 keeps the rate finite at the centre rather than spinning without
    // bound as r -> 0.
    float preShearRadius = length(localPos);
    float shearAngle = time * SHEAR_RATE / (SHEAR_CORE_RADIUS + preShearRadius);
    float cosShear = cos(shearAngle);
    float sinShear = sin(shearAngle);
    vec2 sheared = vec2(localPos.x * cosShear - localPos.y * sinShear,
                         localPos.x * sinShear + localPos.y * cosShear);

    // Stretched, not circular - the classic elongated shape every real photograph of this object
    // shows (we see it steeply inclined, not face-on), built into the distance check itself
    // rather than drawn as decoration on top of a circle.
    vec2 stretched = sheared * vec2(1.0, STRETCH_Y);
    float d = length(stretched);
    if (d > 1.0) {
        discard;
    }

    // Raised from (0.015, 0.01) - reported static in play even after the sixth round's own drift
    // increase to the sibling nebula shaders; unlike those, this shape's motion is now real
    // (the shear above), so the internal texture drift only has to support that, not carry the
    // whole effect alone the way it would have to on a fixed silhouette.
    vec2 drift = vec2(time * DRIFT_X, time * DRIFT_Y);

    // A sharp, compact bulge, not a smooth gradient across the whole ellipse - real galactic
    // bulges are a distinctly brighter, tighter core sitting inside a much larger, fainter disc.
    // Tightened again after the last pass still read as "just yellow fog": 0.34 with a cubed,
    // 0.6-capped blend was too gentle a hot-spot to separate itself from the disc it sat inside -
    // this is now a genuinely small, genuinely blown-out point, not a softly brighter patch.
    float bulgeCore = smoothstep(0.20, 0.0, d);
    float diskFalloff = smoothstep(1.0, 0.12, d);

    // Three-scale disc texture - coarse structure, a dust lane, and fine grain - the same "stack
    // enough octaves that the lattice itself has fine detail to show" fix used everywhere else in
    // this sky, not attempted here on the first pass.
    float coarseTexture = fbm(stretched * COARSE_SCALE + drift);
    float dustLane = fbm(stretched * DUST_SCALE + drift * 1.3);
    float fineGrain = fbm(stretched * FINE_GRAIN_SCALE - drift * 1.8);

    // The dust lane is asymmetric and hard-edged, on purpose: real M31 photographs show a single
    // dominant lane of extinction cutting across one side of the disc, not a lane running
    // symmetrically through the centre. Cut much harder than the last pass (0.18 -> 0.04): a real
    // dust lane reads as near-silhouette against the bulge behind it, not a slightly dimmer patch
    // of the same warm glow - the same lesson the galaxy band's own dust lanes already learned.
    float asymmetry = smoothstep(DUST_ASYM_MIN, DUST_ASYM_MAX, stretched.x + DUST_ASYM_MIX * coarseTexture);
    float dustCut = smoothstep(DUST_CUT_MIN, DUST_CUT_MAX, dustLane) * asymmetry;

    float density = diskFalloff
            * mix(1.0, 0.04, dustCut)
            * clamp(0.45 + 0.25 * coarseTexture + 0.2 * fineGrain, 0.0, 1.0);

    vec3 warmCore = WARM_CORE;
    vec3 midDisk = MID_DISK;
    vec3 outerDisk = OUTER_DISK;
    vec3 base = mix(outerDisk, midDisk, diskFalloff);
    base = mix(base, warmCore, bulgeCore);

    vec3 color = base * density;
    // The bulge itself blows toward white at its very centre - the same real photosphere-style
    // saturation the sun and the retort's own core already use (design/vfx-craft.md §2.2). Pushed
    // much harder than the last pass (was cubed and capped at 0.6, reading as barely brighter
    // than its surroundings): a real galactic nucleus saturates hard, the same way the Sun's own
    // core does, not a gentle highlight.
    color = mix(color, vec3(1.0, 0.97, 0.90), bulgeCore * 0.95);

    // Real disc HII regions and OB associations, two independent scales so the field reads as
    // genuinely populated rather than a handful of lonely specks - still far fainter and sparser
    // than the Pleiades' own close-up stars, because this object is vastly further away.
    float knotFieldCoarse = fbm(stretched * KNOT_COARSE_SCALE + KNOT_COARSE_OFFSET);
    float knotFieldFine = fbm(stretched * KNOT_FINE_SCALE + KNOT_FINE_OFFSET);
    float knots = smoothstep(0.84, 0.96, knotFieldCoarse) * 0.7
            + smoothstep(0.88, 0.97, knotFieldFine) * 0.45;
    color += vec3(1.0, 0.45, 0.45) * knots * diskFalloff;

    float alpha = clamp(density * 0.9 + bulgeCore * 0.9 + knots * 0.8, 0.0, 1.0);
    fragColor = vec4(color, alpha);
}
