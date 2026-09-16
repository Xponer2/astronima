#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <astronima:noise.glsl>

in vec2 localPos;

out vec4 fragColor;

// --- Named tuning constants (S1a: same reasoning as the emission nebula's own block).
const float DRIFT_X = 0.10;
const float DRIFT_Y = 0.07;
const float EDGE_WOBBLE_SCALE = 1.1;
const float EDGE_WOBBLE_AMPLITUDE = 0.22;
const float HAZE_SCALE = 2.2;
const float WISPY_SCALE = 6.5;
const float FINE_WISPY_SCALE = 16.0;
const vec3 DUST_BLUE = vec3(0.22, 0.34, 0.62);
const vec3 MID_BLUE = vec3(0.50, 0.62, 0.88);
const vec3 BRIGHT_BLUE = vec3(0.86, 0.92, 1.0);

float fbm(vec2 p) {
    float value = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        value += amp * noise(p);
        p *= 2.09;
        amp *= 0.5;
    }
    return value;
}

// A jittered-point star field, real placement (not a smooth-noise threshold, which reads as a
// faint smudge rather than an actual point - the round-11 complaint). `scale` sets how many
// cells fit across the disc; `salt` decorrelates each of the three octaves this is called with
// from each other so they do not all place a star in the same relative spot.
//
// The first version of this only ever compared against the single cell a fragment's own scaled
// position fell into - the classic single-cell Worley-noise mistake. A star sitting near its own
// cell's edge has a glow radius that extends past that edge, and with no neighbouring cell's star
// considered, the glow was hard-cut exactly at the cell boundary rather than fading naturally -
// reported back as the marker "clipping" into visible squares and triangles, which is exactly
// what a hard cut along a grid line looks like. Searching the full 3x3 neighbourhood and keeping
// the closest real star found anywhere in it is the standard, correct technique - there is no
// cell boundary left for anything to clip against.
float starLayer(vec2 pos, float scale, float salt, float time) {
    vec2 scaledPos = pos * scale + vec2(salt * 13.0, salt * 29.0);
    vec2 baseCell = floor(scaledPos);
    float best = 0.0;
    for (int dy = -1; dy <= 1; dy++) {
        for (int dx = -1; dx <= 1; dx++) {
            vec2 cell = baseCell + vec2(float(dx), float(dy));
            vec2 seed = cell + salt * 7.0;
            float hasStar = step(0.7, hash(seed + vec2(5.0, 9.0)));
            vec2 jitter = vec2(hash(seed), hash(seed + vec2(17.0, 31.0)));
            float distToStar = length(scaledPos - (cell + jitter));
            float twinklePhase = hash(seed + vec2(90.0, 3.0)) * 30.0;
            float twinkleRate = 0.5 + hash(seed + vec2(44.0, 61.0)) * 0.6;
            float twinkle = 0.4 + 0.6 * sin(time * twinkleRate + twinklePhase);
            float glow = hasStar * smoothstep(0.32, 0.0, distToStar) * max(twinkle, 0.0);
            best = max(best, glow);
        }
    }
    return best;
}

// The Pleiades (M45): real content, a genuinely different kind of object from the emission
// nebulae this pipeline's siblings draw. There is no ionised gas here - the nebulosity is
// starlight scattered off cold dust, the same physics that makes Earth's own sky blue, which is
// exactly why real Pleiades photographs are blue/cyan rather than red. No dual-hue split, on
// purpose - a reflection nebula is one continuous colour family, not two ionisation processes
// side by side.
void main() {
    float d = length(localPos);
    if (d > 1.0) {
        discard;
    }
    float time = ModelOffset.x;
    vec2 drift = vec2(time * DRIFT_X, time * DRIFT_Y);

    // Three-octave haze rather than two - the actual fix for "мыльное": one or two broad noise
    // samples give a soft blur whatever their weighting, stacking a third, much finer layer is
    // what puts real filament structure inside the haze instead of just softening its edges.
    //
    // The silhouette itself also billows now (design/sky.md §5.7.2, the same fix as this
    // pipeline's emission-nebula sibling): `radialFalloff` used to be a function of `d` alone, so
    // the internal haze could drift arbitrarily fast and the edge would still read as a fixed,
    // static circle. A slow, coarse (low-frequency in angle) perturbation of the radius makes the
    // cloud's own edge move, invented for legibility, same as the emission nebula's own note.
    float angle = atan(localPos.y, localPos.x);
    float edgeWobble = fbm(vec2(cos(angle), sin(angle)) * EDGE_WOBBLE_SCALE + drift * 0.4) - 0.5;
    float wobblyD = d + edgeWobble * EDGE_WOBBLE_AMPLITUDE;
    float radialFalloff = smoothstep(1.0, 0.05, wobblyD);
    float haze = fbm(localPos * HAZE_SCALE + drift);
    float wispy = fbm(localPos * WISPY_SCALE - drift * 1.4);
    float fineWispy = fbm(localPos * FINE_WISPY_SCALE + drift * 2.3);
    float density = radialFalloff
            * clamp(0.24 + 0.30 * haze + 0.26 * wispy + 0.20 * fineWispy, 0.0, 1.0);

    vec3 dustBlue = DUST_BLUE;
    vec3 midBlue = MID_BLUE;
    vec3 brightBlue = BRIGHT_BLUE;
    vec3 base = mix(dustBlue, midBlue, density);
    base = mix(base, brightBlue, pow(density, 2.0));

    // Real content: several embedded hot B-type stars (Alcyone and its neighbours), not one
    // exciting star, placed as three independent jittered-point layers at different scales so the
    // field reads as genuinely populated rather than one or two lonely dots - each twinkling at
    // its own rate and phase (real, desynchronised, not a shared clock), this object's own
    // signature distinct from Orion's single pulsing core and the Moon's real opposition surge.
    float stars = starLayer(localPos, 3.0, 1.0, time)
            + starLayer(localPos, 4.6, 2.0, time) * 0.85
            + starLayer(localPos, 6.5, 3.0, time) * 0.7;
    stars = clamp(stars, 0.0, 1.0);

    // Real content: hot B-type stars burn blue-white, hotter than the Sun - tinted rather than
    // flat white so they read as distinctly this cluster's own stars, not a coincidence of the
    // ordinary background starfield sitting nearby.
    vec3 color = mix(base * density, vec3(0.92, 0.96, 1.0), stars);
    // A halo around each star, additive, so they read as genuinely bright and blown-out rather
    // than a flat disc dropped on top of the haze.
    color += vec3(0.6, 0.75, 1.0) * stars * 0.6;

    float alpha = clamp(density * 0.8 + stars, 0.0, 1.0);
    fragColor = vec4(color, alpha);
}
