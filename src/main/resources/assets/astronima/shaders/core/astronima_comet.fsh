#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 localPos;

out vec4 fragColor;

// Rotates this quad's own local coordinates into a tail frame where +x runs straight down the
// given tail and +y is its width axis.
vec2 tailFrame(vec2 p, float angle) {
    float c = cos(-angle);
    float s = sin(-angle);
    return vec2(p.x * c - p.y * s, p.x * s + p.y * c);
}

// A comet (design/sky.md §3's own "comet" row), drawn as two tails of genuinely different
// character:
//
//   * The MAIN tail: straight, narrow, blue from CO+ fluorescence.
//   * The DUST tail: broader, warmer (sunlight scattered off grains, so roughly solar-coloured),
//     and visibly curved, fanning off the main tail to one side.
//
// REAL vs INVENTED, stated plainly because this mod's own standard requires it. The colours are
// real (CO+ blue, C2 Swan-band green in the coma, reflected sunlight off dust), the two-tail
// structure is real, and the tails lengthening toward peak brightness and shrinking again is real.
// WHERE THE TAILS POINT IS NOT. A real comet's tail is not a wake: the solar wind sweeps it
// radially away from the Sun whatever direction the comet travels, so a real tail can and does run
// ahead of the nucleus on the outbound leg. This mod draws the main tail trailing behind the
// direction of motion instead - the owner's explicit call after seeing the real version, which
// read as though the comet were flying backwards. See AsteroidSkyRenderer#TAIL_ANCHOR_MOTION: one
// constant restores the real geometry.
//
// The Sun does still set the dust tail's own fan - which side it leans and how far - so where the
// Sun is stays legible in the picture.
//
// Both angles arrive already converted into this quad's own local space: ModelOffset.y is the main
// tail's, TextureMat[0].x is the dust tail's.
//
// Also invented: the exact width/length/curvature curves and the ray animation rate, tuned for a
// readable silhouette rather than measured from a real coma density profile.
void main() {
    float d = length(localPos);
    if (d > 1.0) {
        discard;
    }

    float time = ModelOffset.x;
    float ionAngle = ModelOffset.y;
    float growth = clamp(ModelOffset.z, 0.0, 1.0);
    float dustAngle = TextureMat[0].x;

    // --- The ion tail: straight, narrow, the longer of the two.
    vec2 ionSpace = tailFrame(localPos, ionAngle);
    float ionAlong = max(ionSpace.x, 0.0);
    float ionLength = mix(0.16, 0.42, growth);
    float ionWidth = 0.030 + ionAlong * 0.055;
    float ionMask = smoothstep(-0.03, 0.03, ionSpace.x);
    float ion = ionMask
            * exp(-ionAlong / ionLength)
            * exp(-(ionSpace.y * ionSpace.y) / (ionWidth * ionWidth));
    // Real ion tails show knots and rays travelling outward as the solar wind gusts.
    ion *= 0.72 + 0.28 * sin(ionAlong * 24.0 - time * 3.0);

    // --- The dust tail: broader, curved, shorter, warmer.
    vec2 dustSpace = tailFrame(localPos, dustAngle);
    float dustAlong = max(dustSpace.x, 0.0);
    // The curve: grains released earlier lag further behind, so the offset grows with distance.
    float dustY = dustSpace.y - 0.40 * dustAlong * dustAlong;
    float dustLength = mix(0.12, 0.30, growth);
    float dustWidth = 0.055 + dustAlong * 0.26;
    float dustMask = smoothstep(-0.03, 0.03, dustSpace.x);
    float dust = dustMask
            * exp(-dustAlong / dustLength)
            * exp(-(dustY * dustY) / (dustWidth * dustWidth))
            * 0.75;

    // --- The coma: small and bright, so the tails read as genuinely long next to it.
    float coma = smoothstep(0.075, 0.0, d) * 1.3;

    // A long, gentle taper to nothing well before the discard boundary above. The first cut of
    // this shader let the tail reach the hard d>1 cut still most of the way to full brightness,
    // reported back as "хвост отрезается маской" (the tail gets cut off by a mask) - this fade,
    // running across the whole outer 45% of the disc, is what makes the taper read as the tail
    // genuinely thinning out rather than as a circular clip line.
    float edgeFade = smoothstep(1.0, 0.55, d);

    vec3 comaColor = vec3(0.62, 1.00, 0.72);   // C2 Swan-band green, the real coma colour
    vec3 ionColor = vec3(0.45, 0.68, 1.00);    // CO+ blue
    vec3 dustColor = vec3(1.00, 0.90, 0.70);   // reflected sunlight off grains

    float total = coma + ion + dust;
    vec3 tint = (comaColor * coma + ionColor * ion + dustColor * dust) / max(total, 1.0e-4);

    float glow = total * edgeFade * growth;
    fragColor = vec4(ColorModulator.rgb * tint * glow, clamp(glow, 0.0, 1.0));
}
