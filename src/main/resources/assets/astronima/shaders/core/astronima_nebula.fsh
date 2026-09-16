#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <astronima:noise.glsl>

in vec2 localPos;

out vec4 fragColor;

// --- Named tuning constants (S1a: the atlas's baked sheets read these by name, so a tuned
// sky re-bakes the atlas - design/astra-atlas-s1-depiction.md section 3.2). Each one was an
// inline literal; the values are byte-identical.
const float DRIFT_X = 0.14;
const float DRIFT_Y = 0.09;
const float EDGE_WOBBLE_SCALE = 1.3;
const float EDGE_WOBBLE_AMPLITUDE = 0.18;
const float TURBULENCE_SCALE = 3.0;
const float WISPY_SCALE = 8.0;
const float HUE_SCALE = 4.0;
const vec2 HUE_OFFSET = vec2(70.0, 15.0);
const float CORE_PULSE_BASE = 0.8;
const float CORE_PULSE_AMPLITUDE = 0.2;
const float CORE_PULSE_RATE = 0.4;
const float CORE_PULSE_PHASE = 3.0;
const vec3 H_ALPHA = vec3(0.92, 0.30, 0.38);
const vec3 OXYGEN_III = vec3(0.26, 0.78, 0.72);

float fbm(vec2 p) {
    float value = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        value += amp * noise(p);
        p *= 2.09;
        amp *= 0.5;
    }
    return value;
}

// This mod's own L4 first cut (design/sky.md): a real emission nebula, not a flat point. The
// quad's own square boundary is never reached (no bulge-margin trick, unlike the sun) - a
// nebula's own irregular, wispy silhouette comes entirely from noise-modulated density inside
// the ordinary unit disc, the same technique the galaxy band already proved at dome scale,
// reapplied here at marker scale.
void main() {
    float d = length(localPos);
    if (d > 1.0) {
        discard;
    }
    // Time, smuggled through ModelOffset.x exactly as the sun's own shaders do (see that
    // comment at the write site). Invented drift, purely for "make it beautiful" (design/astra-
    // atlas-redesign.md §7's own rule: real-science rigour governs the sim, not this VFX layer).
    // Sped up once already for this exact complaint ("было-бы круто если бы у дымки была
    // анимация" - it already was animating, just imperceptibly) and reported static again
    // regardless (design/sky.md §5.7): raising this rate alone cannot fix it, because the
    // silhouette below never moved - see that comment.
    float time = ModelOffset.x;
    vec2 drift = vec2(time * DRIFT_X, time * DRIFT_Y);

    // §5.7.2's structural fix, not a tuning one: `radialFalloff` used to be a function of `d`
    // alone, so no drift rate could ever move the actual edge - noise churned under a perfectly
    // fixed circle, which reads as a still image no matter how fast. A slow, coarse (low-
    // frequency in angle, so a handful of lobes rather than jagged noise) perturbation of the
    // radius itself makes the silhouette genuinely billow.
    float angle = atan(localPos.y, localPos.x);
    float edgeWobble = fbm(vec2(cos(angle), sin(angle)) * EDGE_WOBBLE_SCALE + drift * 0.5) - 0.5;
    float wobblyD = d + edgeWobble * EDGE_WOBBLE_AMPLITUDE;

    float radialFalloff = smoothstep(1.0, 0.15, wobblyD);
    float turbulence = fbm(localPos * TURBULENCE_SCALE + drift);
    float wispy = fbm(localPos * WISPY_SCALE - drift * 1.6);
    float density = radialFalloff * clamp(0.35 + 0.4 * turbulence + 0.3 * wispy, 0.0, 1.0);

    // Real diagnostic colours for M42, the same two families the spectrograph itself would
    // report on a successful capture: Balmer-alpha red/pink and the [O III] forbidden line's
    // teal, picked per-point by an independent noise field rather than blended smoothly, so the
    // two read as separate regions the way a real astrophotograph shows them (the galaxy band's
    // own reasoning, reapplied at marker scale).
    vec3 hAlpha = H_ALPHA;
    vec3 oxygenIII = OXYGEN_III;
    float hueField = fbm(localPos * HUE_SCALE + HUE_OFFSET);
    vec3 base = mix(hAlpha, oxygenIII, smoothstep(0.4, 0.6, hueField));

    // The Trapezium's own hottest member peeking through, near the centre - invented placement
    // (§0's own admission this dimension's sky has no real RA/Dec to honour), real reason it is
    // there: an emission nebula like this is lit by a real exciting star, not glowing on its own.
    // This mod's own signature for this object, distinct from the Sun's whole-disc breathing and
    // the Moon's real opposition surge: the exciting star's own light pulses gently, standing in
    // for a young, still-variable star rather than a settled main-sequence one - invented in
    // exact rhythm, real in that young stars genuinely do vary while they finish forming.
    float pulse = CORE_PULSE_BASE + CORE_PULSE_AMPLITUDE * sin(time * CORE_PULSE_RATE + CORE_PULSE_PHASE);
    float coreGlow = smoothstep(0.55, 0.0, d) * pulse;
    vec3 color = mix(base * density, vec3(1.0, 0.95, 0.88), clamp(coreGlow * 1.4, 0.0, 1.0));

    float alpha = clamp(density * 0.85 + coreGlow * 0.55, 0.0, 1.0);
    fragColor = vec4(color, alpha);
}
