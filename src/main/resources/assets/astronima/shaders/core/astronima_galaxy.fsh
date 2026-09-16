#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <astronima:noise.glsl>

in vec3 direction;

out vec4 fragColor;

// Four octaves, each doubling frequency and halving amplitude (fractal Brownian motion) -
// the actual fix for "not enough detail": two flat noise samples give a handful of large
// blobs, whatever their threshold; stacking octaves is what puts fine wisps and filaments
// inside the coarse structure instead of just softening the same few blobs' edges.
float fbm(vec3 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        value += amplitude * noise(p);
        p *= 2.11;
        amplitude *= 0.5;
    }
    return value;
}

void main() {
    vec3 dir = normalize(direction);

    // Time, smuggled through ModelOffset.x exactly as every other body in this sky already does
    // (design/sky.md §5.7's S7b) - this band previously took no time input at all, so the
    // largest single object on screen was a genuinely still image, not merely a slow one.
    // Deliberately the slowest drift anywhere in this sky: it is the backdrop, and it must not
    // compete with anything drawn over it.
    float time = ModelOffset.x;
    vec3 bandDrift = vec3(time * 0.006, time * 0.004, 0.0);

    // The galactic plane as a band: how close this direction sits to the great circle at
    // dir.y == 0. Real bands are only a few degrees wide against the whole sky, so the falloff
    // is sharp rather than a gentle gradient.
    float bandDistance = abs(dir.y);

    // Invented, not measured: real dust bands do not visibly balloon along their own length the
    // way this does, but the user asked for exactly this - a hose-pinch look, thick in most
    // places and swelling or narrowing at points along the band rather than a constant ribbon
    // width. `bandAzimuth` is "how far along the band", independent of `bandDistance` ("how far
    // across it"), so this only ever changes the band's thickness at a given point along its own
    // length, never its overall shape or position.
    float bandAzimuth = atan(dir.z, dir.x);
    vec2 azimuthPos = vec2(cos(bandAzimuth), sin(bandAzimuth));
    float widthWave = fbm(vec3(azimuthPos * 2.2, 0.3) + vec3(11.0, 5.0, 60.0));
    float fineWidthWave = fbm(vec3(azimuthPos * 6.0, 0.7) + vec3(80.0, 2.0, 14.0));
    // Baseline pushed fatter throughout (2.4 -> 1.8), then the wave pushes thickness further
    // still in places - "жырнее" as a floor, the swelling/pinching as the variation on top.
    float widthSharpness = 1.8 / mix(0.55, 1.9, widthWave * 0.75 + fineWidthWave * 0.25);
    float bandDensity = pow(clamp(1.0 - bandDistance * widthSharpness, 0.0, 1.0), 1.7);

    // Frequencies pushed well past the first two passes: on a unit sphere, `dir * 3` spans
    // only a few noise cells across the whole visible band, which is the actual reason
    // "more fbm octaves" alone still read as under-detailed - the lattice itself was too
    // coarse for anything fine to exist at. Reported back as "critically under-detailed"
    // twice; this is the fix that changes the amount of structure, not just its blend.
    float turbulence = fbm(dir * 9.0 + bandDrift);
    float fineDetail = fbm(dir * 30.0 + vec3(50.0, 20.0, 10.0) + bandDrift * 2.0);
    float microDetail = fbm(dir * 70.0 + vec3(5.0, 90.0, 33.0) + bandDrift * 3.0);

    // Two independent dust-lane fields, not one, crossing at their own angles - a single lane
    // field reads as one wipe across the band; two reads as an actual dust structure. Cut hard
    // now (down to 0.2x/0.45x, not a gentle dim) - real dust lanes read as near-silhouette,
    // not a softer patch of the same glow.
    float dustCut = smoothstep(0.32, 0.68, fbm(dir * 14.0 + vec3(91.0, 3.0, 61.0)));
    float dustCut2 = smoothstep(0.35, 0.72, fbm(dir * 9.0 + vec3(15.0, 66.0, 4.0)));
    float density = bandDensity * mix(1.0, 0.2, dustCut) * mix(1.0, 0.45, dustCut2)
            * (0.4 + 0.28 * turbulence + 0.2 * fineDetail + 0.12 * microDetail);

    vec3 indigo = vec3(0.10, 0.05, 0.26);
    vec3 magenta = vec3(0.46, 0.10, 0.50);
    vec3 teal = vec3(0.08, 0.36, 0.42);
    vec3 warmCore = vec3(0.75, 0.48, 0.32);

    // Three colour families, not two: a second, independent noise field picks between the
    // magenta and teal regions rather than blending them smoothly, because real emission
    // nebulae show genuinely separate colour regions side by side (H-alpha red/pink against
    // OIII teal) - one hue fading continuously into another is not what that looks like.
    float hueField = fbm(dir * 11.0 + vec3(77.0, 12.0, 40.0));
    vec3 accent = mix(magenta, teal, smoothstep(0.35, 0.65, hueField));
    vec3 color = mix(indigo, accent, clamp(turbulence * 1.3 + fineDetail * 0.4, 0.0, 1.0));

    // Warmth only fires deep in the band's own densest core and gently, gated by turbulence
    // so it follows the same fine structure the dust does instead of painting flat patches -
    // the "eye-searing" fix from two passes ago, kept.
    float warmth = clamp((bandDensity - 0.78) * 1.4, 0.0, 1.0) * turbulence;
    color = mix(color, warmCore, warmth);

    // Two bright-knot layers, different scales - sparse, sharp points where fine noise spikes
    // past a high threshold. Invented, not measured (real star-forming regions do not look
    // like this up close), standing in for the clustered bright points a real band photograph
    // shows that smooth density and colour alone cannot produce. Additive, so it only ever
    // adds accent, never replaces the underlying structure.
    // A faint shimmer, independent of the structural drift above and per-point rather than
    // uniform (each knot's own phase comes from its position, so they do not all pulse in
    // lockstep) - the nebulosity's own idle motion, deliberately much lower amplitude than any
    // named L4 object's so the backdrop still reads as calm next to them.
    float sparkle = smoothstep(0.82, 0.96, fbm(dir * 55.0 + vec3(200.0, 5.0, 80.0)));
    float shimmer1 = 0.85 + 0.15 * sin(time * 0.6 + dot(dir, vec3(41.0, 7.0, 19.0)));
    color += vec3(1.0, 0.92, 0.78) * sparkle * bandDensity * 1.8 * shimmer1;
    float sparkle2 = smoothstep(0.9, 0.99, fbm(dir * 95.0 + vec3(11.0, 44.0, 130.0)));
    float shimmer2 = 0.85 + 0.15 * sin(time * 0.8 + dot(dir, vec3(13.0, 53.0, 29.0)) + 1.7);
    color += vec3(0.7, 0.82, 1.0) * sparkle2 * bandDensity * 1.3 * shimmer2;

    fragColor = vec4(color * density, density * 0.78);
}
