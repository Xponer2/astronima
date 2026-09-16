#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <astronima:noise.glsl>

in vec2 localPos;

out vec4 fragColor;

// --- Named tuning constants (S1a: the atlas's baked sheets read these by name, same block
// shape as the emission nebula's own). Every value is byte-identical to the inline literal
// it replaces.
const float RING_ROTATION_RATE = 0.25;
const float RING_NOISE_SCALE = 4.0;
const float FINE_RING_NOISE_SCALE = 11.0;
const float MICRO_RING_NOISE_SCALE = 23.0;
const vec3 OXYGEN_III = vec3(0.16, 0.88, 0.80);
const vec3 H_ALPHA_RIM = vec3(0.90, 0.36, 0.22);
const float FILAMENT_SCALE = 7.0;
const float INNER_GLOW_SCALE = 9.0;

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

// The Helix Nebula (NGC 7293): real content is a ring, not a cloud. A planetary nebula is a
// dying star's own outer envelope, expelled and expanding outward - seen from outside, that is a
// shell, which is why real photographs show a ring ("the Eye of God"), unlike Orion's amorphous
// HII cloud or the Pleiades' diffuse haze. The tiny, superheated white dwarf that ionises the
// whole shell is visible as a single pinpoint straight through the ring's own hole.
void main() {
    float d = length(localPos);
    if (d > 1.0) {
        discard;
    }
    float time = ModelOffset.x;

    // The ring itself turns - this object's own signature, distinct from Orion's pulsing core
    // and the Pleiades' independently-twinkling stars: real planetary nebulae do show knots and
    // asymmetries in their shell, and an apparent rotation stands in for that structure being
    // seen from a shifting angle. Rate raised from 0.05 (≈126 s/revolution - too slow to read as
    // motion rather than a still image) to 0.25 (≈25 s/revolution), design/sky.md §5.7's S7c.
    float ringAngle = atan(localPos.y, localPos.x) - time * RING_ROTATION_RATE;
    vec2 angularPos = vec2(cos(ringAngle), sin(ringAngle));

    // Three noise scales across the ring band, not one - the actual fix for "внутри чёрное":
    // the ring's own body needs real, layered texture across it, the same fbm-stacking fix used
    // everywhere else in this sky rather than a single flat noise sample.
    float ringNoise = fbm(angularPos * RING_NOISE_SCALE + vec2(d * 3.0, time * 0.03));
    float fineRingNoise = fbm(angularPos * FINE_RING_NOISE_SCALE + vec2(d * 6.0, 40.0));
    float microRingNoise = fbm(angularPos * MICRO_RING_NOISE_SCALE + vec2(d * 12.0, 80.0));

    // The annulus: density rises from the centre, peaks around the shell, falls off again into
    // the void - two smoothsteps back to back describe that real radial shape directly.
    float ringShape = smoothstep(0.22, 0.52, d) * smoothstep(1.0, 0.60, d);
    float density = ringShape * clamp(
            0.30 + 0.28 * ringNoise + 0.24 * fineRingNoise + 0.18 * microRingNoise, 0.0, 1.0);

    vec3 oxygenIII = OXYGEN_III;
    vec3 hAlphaRim = H_ALPHA_RIM;
    // Real Helix photographs show a teal [O III] interior and a warmer H-alpha outer rim - driven
    // by radius, because that is a real radial structure (H-alpha survives further out into the
    // thinner, cooler outer shell than the doubly-ionised oxygen core does).
    vec3 base = mix(oxygenIII, hAlphaRim, smoothstep(0.55, 0.95, d));
    vec3 color = base * density;
    // The ring's own brightest knots blow toward white - real planetary-nebula shells do show
    // individual bright cometary knots, not a uniformly-lit band.
    color = mix(color, vec3(1.0, 0.95, 0.85), pow(clamp(microRingNoise, 0.0, 1.0), 4.0) * density * 0.8);

    // Radial filaments ("cometary knots"), real Helix structure streaking inward from the shell
    // toward the centre - what actually fills the cavity with content instead of leaving it flat
    // black. Fades out before the very centre, so the white dwarf pinpoint still reads as the
    // single brightest thing in the frame.
    float filamentNoise = fbm(angularPos * FILAMENT_SCALE + vec2(d * 1.6, 0.0));
    float filaments = smoothstep(0.52, 0.85, filamentNoise)
            * smoothstep(0.0, 0.42, d) * smoothstep(0.62, 0.20, d);
    color += oxygenIII * filaments * 0.7;

    // A faint inner glow across the whole cavity, on top of the filaments - real Helix photographs
    // show diffuse structure even well inside the main ring, never a flat empty black disc.
    float innerGlow = smoothstep(0.30, 0.0, d)
            * (0.10 + 0.10 * fbm(angularPos * INNER_GLOW_SCALE + vec2(d * 5.0, 12.0)));
    color += oxygenIII * innerGlow;

    // The white dwarf itself, dead centre, visible straight through the ring's own hole - the
    // real ionising source this entire shell is lit by.
    float corePinpoint = smoothstep(0.065, 0.0, d);
    color = mix(color, vec3(1.0, 0.98, 0.95), corePinpoint);

    float alpha = clamp(
            density * 0.9 + filaments * 0.4 + innerGlow * 1.8 + corePinpoint * 0.95, 0.0, 1.0);
    fragColor = vec4(color, alpha);
}
