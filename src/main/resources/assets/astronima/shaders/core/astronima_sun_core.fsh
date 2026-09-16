#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <astronima:noise.glsl>

in vec2 localPos;

out vec4 fragColor;

float fbm(vec2 p) {
    float value = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        value += amp * noise(p);
        p *= 2.03;
        amp *= 0.5;
    }
    return value;
}

// The quad Java hands this shader is deliberately oversized by this same factor (see
// CORE_BULGE_MARGIN in AsteroidSkyRenderer.java): the true disc (pos = 1 below) needs to sit
// well *inside* the quad's own flat edges, not right at them, or the bulge noise has nowhere to
// go and gets sliced by the quad's own straight boundary - which is what "квадрат" turned out to
// be (the previous version of this fix divided here instead of multiplying, which did the exact
// opposite: it shrank the reachable range until almost nothing ever discarded, painting nearly
// the whole enlarged quad solid). Multiplying localPos - not dividing - is what pushes pos = 1
// inward, away from the quad's edge. Must match Java's multiplier on the quad's own size.
#define CORE_BULGE_MARGIN 1.6

void main() {
    vec2 pos = localPos * CORE_BULGE_MARGIN;
    float d = length(pos);
    float angle = atan(pos.y, pos.x);
    // Seconds since a wrapped tick counter, smuggled in through ModelOffset.x (a sky billboard
    // has nothing else to offset) - see the comment at its write site in
    // AsteroidSkyRenderer#drawRadialQuad for why that slot rather than a new uniform.
    float time = ModelOffset.x;
    // atan has a hard discontinuity at +-pi - feeding the raw angle into noise put a visible
    // seam exactly there (reported back as "rays break at a join"). cos/sin trace a closed
    // loop as angle sweeps through that same point, so sampling noise from a position moving
    // around that loop has no seam - the fix is in the coordinate, not the noise function.
    vec2 angularPos = vec2(cos(angle), sin(angle));

    // Invented, not measured: the disc's own silhouette is no longer a perfect circle. A low-
    // frequency noise around the rim bulges the edge out or pulls it in by up to ~18%, reading
    // as prominence-like arcs breaking away from the limb instead of a razor-straight boundary -
    // the user's own request ("даже если научно обьяснить не получится, не проблема").
    float edgeNoise = fbm(angularPos * 5.0 + vec2(7.0, 3.0));
    float edgeThreshold = 1.0 + (edgeNoise - 0.5) * 0.36;
    if (d > edgeThreshold) {
        discard;
    }

    // The linear limb-darkening law is real (I(mu)/I(0) = 1 - u + u*mu, mu = sqrt(1-d^2) seen
    // face-on) and stays the base; u eased back to 0.55 now that the texture below carries
    // more of the read than brightness alone. Past the disc's own d=1 radius mu clamps to 0,
    // which is what makes the prominence bulges above read dim and wispy rather than solid -
    // a plausible side effect of the same clamp, not a separately authored rule. Everything
    // past this line is deliberately invented rather than measured - a real quiescent Sun has
    // no rays or visible swirl in ordinary light. Said plainly so the boundary stays honest.
    float mu = sqrt(max(0.0, 1.0 - d * d));
    float LIMB_U = 0.55;
    float limbIntensity = 1.0 - LIMB_U + LIMB_U * mu;

    // Convective cells: sampled in a spiralling, radius-scaled embedding so cells stretch
    // outward like real granulation rather than sitting as a flat static grid. A second,
    // finer-scale pass layered on top so the surface reads as textured at more than one size
    // of detail, not one grid of uniform blobs.
    //
    // Rotation alone was the "dumb spinner" complaint: a pure `angle -= time*k` rotation resamples
    // the exact same frozen noise pattern from a turning viewpoint, so nothing about the pattern
    // itself ever changes shape - it just reads as a rigid pinwheel, indistinguishable from a
    // loading icon. `drift` instead translates the sample position through the noise field over
    // time, so the actual cells being sampled change - new ones enter, old ones leave, existing
    // ones stretch and merge - which is what makes it read as a turbulent flow rather than a
    // spinning decal. Both are kept together: rotation gives it the vortex's overall shape and
    // direction, drift gives it something genuinely new to look at as it turns.
    // Slowed to roughly a third of the first pass's rate - reported back as "still too fast" even
    // after the drift fix solved the "frozen pinwheel" complaint; the drift terms are scaled down
    // by the same factor so the churn and the rotation stay in the same proportion to each other.
    vec2 drift = vec2(time * 0.11, time * 0.07);
    float spiralAngle = angle + d * 3.0 - time * 0.035;
    vec2 spiralPos = vec2(cos(spiralAngle), sin(spiralAngle)) * (2.0 + d * 5.0) + drift;
    float cells = fbm(spiralPos);
    float fineCells = fbm(spiralPos * 3.3 + vec2(40.0, 12.0) - drift * 1.7);

    // Rays sample from the same slowly-rotating angular frame as the spiral (a shared rotation,
    // not an independent one) so the rays and the granulation churn together as one turning
    // body rather than two unrelated animations layered by coincidence, with their own drift so
    // the ray pattern itself also reshapes rather than just orbiting.
    vec2 spinningAngularPos = vec2(cos(angle - time * 0.035), sin(angle - time * 0.035));
    float rayNoise = fbm(spinningAngularPos * 5.0 + vec2(d * 3.0 - time * 0.12, time * 0.045));
    float rays = smoothstep(0.4, 0.75, rayNoise) * smoothstep(0.1, 0.7, d);

    // The Sun's own signature, distinct from the Moon's real opposition surge and the nebula's
    // own pulsing core: a slow overall brightness "breathing", standing in for real stellar
    // micro-variability (every star's output flickers slightly; the Sun's own is smaller than
    // this but the same phenomenon) - invented in magnitude, real in kind.
    float breathe = 0.93 + 0.07 * sin(time * 0.15);

    // Sunspots: real content, not invented. A sunspot is where magnetic flux pushes up through
    // the photosphere and exposes the cooler plasma beneath it - "dark" only by contrast against
    // the far hotter surface around it, the same reason the coronal loops this same magnetic
    // activity produces are anchored here too (see astronima_sun_halo.fsh's own comment). Sparse,
    // sharp-edged patches where a separate low-frequency field dips below a high threshold - the
    // real correlation with the loops above is thematic (both are magnetic-activity features,
    // both use this session's sparse-hash-threshold technique) rather than one shared coordinate,
    // since the core and the halo are two different quads at two different scales.
    float spotField = fbm(spiralPos * 0.6 + vec2(90.0, 30.0));
    float spot = 1.0 - smoothstep(0.62, 0.7, spotField);

    // The actual fix for "still just a white circle": cells and rays now drive real colour
    // variation directly, mixing all the way from a dim ember tone to blown-white, rather than
    // nudging a brightness multiplier that was already saturated near 1.0 across most of the
    // disc - which is what made the previous version's texture invisible under clamping.
    float surface = clamp(limbIntensity * 0.5 + cells * 0.55 + fineCells * 0.25 + rays * 0.4, 0.0, 1.0);
    surface *= mix(1.0, 0.35, spot);

    vec3 ember = ColorModulator.rgb * 0.4;
    vec3 hot = mix(ColorModulator.rgb, vec3(1.0), 0.6);
    vec3 color = mix(ember, hot, surface);
    // Blown-out core - a real photosphere saturates any sensor (the same reason the
    // incandescent retort's own core blows out, design/vfx-craft.md §2.2) - modulated by the
    // cell pattern so even the blowout is not a perfectly smooth circle, and pulled back where
    // a sunspot sits so spots stay visible even at the disc's brightest point.
    color = mix(color, vec3(1.0), pow(mu, 5.0) * 0.5 * (0.6 + 0.4 * cells) * (1.0 - spot * 0.8));
    // Sunspot core itself, deep umbra-red rather than flat black - a spot is still emitting,
    // just far cooler than the surrounding photosphere.
    color = mix(color, vec3(0.35, 0.08, 0.02), spot * 0.55);
    color *= breathe;

    // design/sky.md's occultation event: shading and blur, not a separately drawn masked object.
    // The first version drew a whole extra quad clipped to a hard circle, which needed its own
    // boundary to line up pixel-for-pixel with this disc's own irregular, bulging edge above -
    // reported back exactly as that mismatch reads: "появляется прям из середины солнца слева"
    // (appears right out of the middle of the Sun) and "вылазит не по размерам солнца" (comes out
    // not sized to the Sun at all), the second one made worse by a debug preview's own looping
    // motion snapping hard back to its start every cycle. Doing the dimming here instead, in this
    // shader's own coordinate space, means there is no second boundary left to ever disagree with
    // this one - the shadow is simply darker paint on the same disc, softened by a wide
    // smoothstep band rather than a hard edge, exactly the "затенение и размытость" (shading and
    // blur) asked for directly in place of a mask.
    float occultationOffset = ModelOffset.y;
    float occultationRadius = ModelOffset.z;
    if (occultationRadius > 0.0) {
        float distFromShadow = length(pos - vec2(occultationOffset, 0.0));
        float shadow = 1.0 - smoothstep(occultationRadius * 0.5, occultationRadius * 1.4, distFromShadow);
        color *= mix(1.0, 0.12, shadow);
    }

    fragColor = vec4(color, clamp((edgeThreshold - d) * 8.0, 0.0, 1.0));
}
