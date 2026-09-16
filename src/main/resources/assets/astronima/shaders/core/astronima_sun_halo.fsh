#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <astronima:noise.glsl>

in vec2 localPos;

out vec4 fragColor;

float fbm(vec2 p) {
    float value = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        value += amp * noise(p);
        p *= 2.07;
        amp *= 0.5;
    }
    return value;
}

// The photosphere's own edge, in this shader's local coordinates - not d=1 (that is the halo
// quad's own much larger physical edge, ~75 world units against the disc's own ~34). A loop's
// real footpoints sit ON the surface, so anchoring them here rather than at d=1 is what makes
// them read as attached to the Sun instead of floating in the corona disconnected from it.
#define PHOTOSPHERE_EDGE 0.4

// Real content, cited: coronal loops are magnetic flux tubes threading the lower corona and
// transition region, anchored where the underlying magnetic field breaks through the photosphere
// - real, textbook solar physics. The first version scattered four independent loops evenly
// around the whole rim and spun them at the granulation's own fast rate - reported back plainly
// as "выглядят глупо ещё и крутятся не как в реальной жизни" (look stupid, and rotate nothing
// like real life), and both halves of that were real mistakes, not taste: real loops cluster in
// bundles over a single active region rather than appearing evenly spaced around the entire disc,
// and real solar rotation is dramatically slower than the granulation's own visible churn - a
// region that visibly sweeps around the rim at the granulation's own pace reads as a toy, not a
// star. Fixed on both counts below: one active region (not four independent points), several
// loops of different sizes clustered close together inside it, and the region's own drift rate
// cut to under a ninth of the granulation's.
#define ACTIVE_REGION_DRIFT_RATE 0.004
#define LOOPS_PER_REGION 4

float coronalLoopGlow(float angle, float d, float time) {
    // One region's own position - nearly stationary at the compressed pace this sky already
    // runs everything else at, which is the entire point: a viewer should not be able to watch
    // it visibly orbit the disc the way the granulation underneath it does.
    float regionAngle = hash(vec2(3.0, 11.0)) * 6.28318530718 - time * ACTIVE_REGION_DRIFT_RATE;

    float totalGlow = 0.0;
    for (int i = 0; i < LOOPS_PER_REGION; i++) {
        float seed2 = float(i) * 7.0;
        vec2 seed = vec2(seed2, seed2 * 1.7);
        // Clustered close around the region's own centre, not spread across the whole rim - a
        // real active region's own loops share one small patch of sky, not four random points.
        float angleOffset = (hash(seed + vec2(5.0, 9.0)) - 0.5) * 0.55;
        float baseAngle = regionAngle + angleOffset;
        float width = 0.16 + hash(seed + vec2(9.0, 2.0)) * 0.20;
        float height = 0.08 + hash(seed + vec2(4.0, 13.0)) * 0.32;

        float delta = angle - baseAngle;
        delta = mod(delta + 3.14159265, 6.28318530718) - 3.14159265;
        float t = delta / width;
        float mask = smoothstep(1.0, 0.8, abs(t));

        // A real family of circular arcs, not a decorative bump: sin() traces exactly the shape a
        // magnetic loop's own silhouette makes - zero at both footpoints (t = -1, 1), peaking at
        // the apex (t = 0) - so the arc's own base sits flush against the photosphere both ends.
        float archProfile = sin(clamp(t * 0.5 + 0.5, 0.0, 1.0) * 3.14159265);
        float loopRadius = PHOTOSPHERE_EDGE + height * archProfile;

        // A real core line plus a soft glow shoulder around it - the first version's 0.018-0.028
        // thickness was thin enough to read as barely-there hairlines lost against the streamer
        // noise already filling this shader; this is deliberately bolder.
        float distFromLoop = abs(d - loopRadius);
        float core = smoothstep(0.04, 0.0, distFromLoop);
        float shoulder = smoothstep(0.1, 0.0, distFromLoop) * 0.4;
        float onLoop = max(core, shoulder);

        // Plasma genuinely flows along a real coronal loop's own field line - a slow travelling
        // brightness ripple along `t` stands in for that (invented in its exact rate, real in
        // kind), independent of the region's own near-stationary drift above.
        float flow = 0.7 + 0.3 * sin(t * 14.0 - time * 1.0 + seed2);

        totalGlow += onLoop * mask * flow;
    }
    return clamp(totalGlow, 0.0, 1.0);
}

void main() {
    float d = length(localPos);
    if (d > 1.0) {
        discard;
    }
    float angle = atan(localPos.y, localPos.x);
    // Same time smuggled through ModelOffset.x as the core shader (see its own comment) - kept
    // at the identical rate so the corona turns together with the surface beneath it rather
    // than as two independently-animated layers.
    float time = ModelOffset.x;
    // Same seam fix as the core: sample from cos/sin's closed loop, never the raw angle. Rate
    // slowed to match the core's own (reported back as "too fast" together) - kept identical so
    // the corona still turns together with the surface beneath it, just at the corrected pace.
    vec2 angularPos = vec2(cos(angle - time * 0.035), sin(angle - time * 0.035));

    // Two streamer scales instead of one - a single noise pass gives evenly-spaced rays, real
    // corona streamers clump into a few dominant plumes with finer filaments between them. Each
    // also drifts through the noise field (not just rotating with `angularPos`), so the plumes
    // themselves lengthen, split and fade over time instead of orbiting as one frozen shape -
    // same reasoning as the core's own drift term, scaled down by the same factor.
    float coarseStreamer = fbm(angularPos * 4.0 + vec2(d * 1.5 - time * 0.09, time * 0.05));
    float fineStreamer = fbm(angularPos * 13.0 + vec2(d * 4.0 + time * 0.15, 20.0 - time * 0.12));
    float streamer = 0.35 + 0.45 * coarseStreamer + 0.35 * fineStreamer;

    // Streamers now reach further out (softer power curve) and are individually brighter where
    // they overlap a bright coarse lobe, so a few plumes read as genuinely longer than the rest
    // rather than every direction fading at the same uniform rate.
    float reach = mix(1.6, 2.6, smoothstep(0.5, 0.85, coarseStreamer));
    float falloff = pow(max(0.0, 1.0 - d), reach) * streamer;

    vec3 color = mix(ColorModulator.rgb, vec3(1.0, 0.85, 0.55), smoothstep(0.55, 0.9, coarseStreamer) * 0.5);
    vec3 result = color * falloff;
    float totalGlow = falloff;

    // The active region's own loop bundle - deliberately using the raw, un-rotated angle rather
    // than the granulation's own fast-spinning frame (see coronalLoopGlow's own comment for why
    // sharing that rotation was the actual bug): real solar rotation is its own, much slower
    // process, handled entirely inside coronalLoopGlow at ACTIVE_REGION_DRIFT_RATE instead.
    // Additive on top of the streamer glow already computed, since a loop is real plasma adding
    // its own light, not a mask subtracting from anything.
    float loopGlow = coronalLoopGlow(angle, d, time) * 0.9;
    result += vec3(1.0, 0.92, 0.75) * loopGlow;
    totalGlow += loopGlow;

    fragColor = vec4(result, clamp(totalGlow, 0.0, 1.0));
}
