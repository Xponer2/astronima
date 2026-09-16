package play.xponer.astronima.client.sky;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.sim.optics.BlackBody;
import play.xponer.astronima.sim.optics.MoonPhaseLighting;
import play.xponer.astronima.sim.optics.NamedSkyObjects;
import play.xponer.astronima.sim.optics.SkyRotation;
import play.xponer.astronima.sim.optics.StarField;
import play.xponer.astronima.sim.magic.SupernovaSpectrum;
import play.xponer.astronima.sim.sky.CometSchedule;
import play.xponer.astronima.sim.sky.OccultationSchedule;
import play.xponer.astronima.sim.sky.PassingBodySchedule;
import play.xponer.astronima.sim.sky.SkyEventOverride;
import play.xponer.astronima.sim.sky.SupernovaSchedule;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * S1 of design/sky.md: a starfield with real magnitude and colour, replacing vanilla's flat
 * grey default (suppressed via {@code star_brightness: 0} in {@code dimension_type/asteroid.json}
 * — one field to draw, one field to switch off, rather than two starfields overlapping).
 *
 * <p><strong>No new shader for this stage.</strong> {@link StarField} gives each star a real
 * {@link BlackBody} temperature, but {@code RenderPipelines.STARS} is position-only — the same
 * pipeline vanilla's own stars use, colour applied as a single uniform per draw call, not per
 * vertex. Rather than write a custom vertex-colour shader (real, first-time GPU risk this early
 * in the sky effort), stars are bucketed by temperature into a handful of colour groups, each
 * its own small buffer and its own draw call with that bucket's colour as the uniform — still a
 * genuinely varied, physically real sky, built entirely from pipelines already proven to work.
 * The galactic band (next) is where a real custom shader actually earns its risk, because noise
 * across a dome has no equivalent trick.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class AsteroidSkyRenderer {
    /** Fixed on purpose: the same star in the same place for every player and every session,
     * per design/sky.md §7 ("two players seeing the same sky at the same world time"). */
    private static final long STAR_FIELD_SEED = 20260809L;

    private static final int STAR_COUNT = 3200;
    private static final int COLOR_BUCKETS = 6;
    private static final float STAR_DISTANCE = 100.0F;
    /** Smaller than the first draft on purpose: a big flat quad reads as an actual square,
     * reported back exactly that way; a small one reads as a point the way vanilla's own
     * (also flat, also unsoftened) stars do. */
    private static final float MIN_STAR_SIZE = 0.08F;
    private static final float MAX_STAR_SIZE_BONUS = 0.14F;

    private static @Nullable GpuBuffer[] bucketBuffers;
    private static int[] bucketIndexCounts = new int[0];
    private static int[] bucketColors = new int[0];

    /** The actual reason it was reported back as "genuinely cannot find it": 0.7 against a
     * {@code MOON_SIZE} of 22 and a {@code SUN_CORE_SIZE} of 34 is 30-50x smaller — a couple of
     * pixels on screen, comparable to a star despite the doc comment's claim otherwise. A real
     * nebula's own apparent size is comparable to the full moon's; this is not that large (the
     * sun and moon still need to read as the dominant sky objects), but it is now unmistakably
     * a hazy patch of sky rather than a point, findable without a screenshot-and-zoom. */
    private static final float NEBULA_MARKER_SIZE = 11.0F;

    private static final int DOME_LAT_SEGMENTS = 16;
    private static final int DOME_LON_SEGMENTS = 24;
    private static final float DOME_RADIUS = 300.0F;

    private static @Nullable GpuBuffer galaxyDomeBuffer;
    private static int galaxyDomeIndexCount;

    /** One full turn every this many ticks — a small tumbling body, not a 24000-tick Earth
     * day; fast enough to actually notice the sky wheel within a play session. */
    private static final double ROTATION_PERIOD_TICKS = 12000.0;

    /** The spin axis is not vertical — real small bodies do not obligingly rotate about
     * whatever axis a player calls "up"; this is what makes the sky read as this asteroid's
     * own rather than a re-skinned Earth sky (design/sky.md §2, row L6). */
    private static final float POLE_TILT_DEGREES = 35.0F;

    /**
     * Everything one radial-quad draw needs, gathered before any GPU call is made — the whole
     * point of collecting these first rather than drawing each one as it is computed (the old
     * shape of this file) is that every quad this frame can then share <em>one</em> combined
     * vertex buffer and <em>one</em> render pass instead of each getting a brand-new tiny buffer
     * and a brand-new render pass of its own.
     */
    private record RadialQuad(com.mojang.blaze3d.pipeline.RenderPipeline pipeline, Matrix4f modelView,
                              Vector3f direction, float size, int colorRgb, Vector3f modelOffset,
                              Matrix4f textureMatrix) {}

    @SubscribeEvent
    private static void onAfterSky(RenderLevelStageEvent.AfterSky event) {
        Minecraft minecraft = Minecraft.getInstance();
        // The one line every other line in this file exists to be gated by: a vanilla world,
        // or any other dimension of this mod's own, must render byte-identically to before.
        if (minecraft.level == null || minecraft.level.dimension() != ModDimensions.ASTEROID_LEVEL) {
            return;
        }
        long gameTimeTicks = minecraft.level.getGameTime();
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(skyRotation(gameTimeTicks));
        Matrix4f cameraModelView = RenderSystem.getModelViewMatrix();

        if (bucketBuffers == null) {
            bucketBuffers = buildBucketBuffers();
        }
        if (galaxyDomeBuffer == null) {
            galaxyDomeBuffer = buildGalaxyDome();
        }

        // Gathered, not yet drawn: the sun, the moon, every named nebula marker, and whichever of
        // the passing-body/comet/supernova events are actually active right now. Order here is
        // draw order — nebula markers first so the occluders below still paint over them last for
        // the same reason they always did (see the render pass below).
        List<RadialQuad> quads = new ArrayList<>();
        collectNebulaMarkers(quads, modelView, gameTimeTicks);
        collectSun(quads, cameraModelView, gameTimeTicks);
        collectMoon(quads, cameraModelView);
        collectPassingBody(quads, cameraModelView, gameTimeTicks);
        collectComet(quads, cameraModelView, gameTimeTicks);
        collectSupernova(quads, cameraModelView, gameTimeTicks);

        // Every dynamic-uniform write for the whole frame happens here, before the render pass
        // below ever opens.
        //
        // The first cut of this consolidation wrote each object's own transform lazily, inside
        // the shared pass, right before drawing it - matching how this file always did it when
        // each object had its own pass. That crashed instantly on the real GPU path:
        // `DynamicUniforms#writeTransform` calls `CommandEncoder#mapBuffer`, and mapping a buffer
        // while a render pass is open is refused outright ("Close the existing render pass before
        // performing additional commands"), a constraint `runGameTestServer` cannot see at all
        // (no GPU on that classpath) and only `runClient` could ever have caught. Every slice this
        // frame needs is written up front instead, and the pass below only ever reads them back.
        // Time, smuggled through ModelOffset.x exactly as every other body here already does
        // (design/sky.md §5.7's S7b) — the band previously always wrote a zero offset, which is
        // why its own shader read a constant and never moved at all.
        float bandTimeSeconds = (gameTimeTicks % 1_000_000L) / 20.0F;
        GpuBufferSlice bandTransform = RenderSystem.getDynamicUniforms()
                .writeTransform(modelView, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
                        new Vector3f(bandTimeSeconds, 0.0F, 0.0F), new Matrix4f());
        GpuBufferSlice[] bucketTransforms = new GpuBufferSlice[bucketBuffers.length];
        for (int bucket = 0; bucket < bucketBuffers.length; bucket++) {
            if (bucketIndexCounts[bucket] > 0) {
                Vector4f color = ARGB.vector4fFromARGB32(ARGB.opaque(bucketColors[bucket]));
                bucketTransforms[bucket] = RenderSystem.getDynamicUniforms()
                        .writeTransform(modelView, color, new Vector3f(), new Matrix4f());
            }
        }
        List<GpuBufferSlice> quadTransforms = new ArrayList<>(quads.size());
        for (RadialQuad quad : quads) {
            Vector4f color = ARGB.vector4fFromARGB32(ARGB.opaque(quad.colorRgb()));
            quadTransforms.add(RenderSystem.getDynamicUniforms()
                    .writeTransform(quad.modelView(), color, quad.modelOffset(), quad.textureMatrix()));
        }

        // One shared render pass for the whole sky, not one per object.
        //
        // This file used to open a brand-new RenderPass — and allocate a brand-new tiny GpuBuffer
        // to go with it — for the galactic band, each of six star colour buckets, every named
        // nebula marker, the sun's two layers, the moon's two layers, and up to three live events:
        // fifteen to twenty separate render passes every single frame for the sky alone. Opening a
        // render pass is real driver work (a framebuffer bind and, on modern backends, real
        // synchronisation), and paying that cost that many times a frame is exactly what "небо
        // сильно нагружает видеокарту" (the sky heavily loads the GPU) describes. None of these
        // passes ever clears anything — every one of them only ever accumulates draws onto
        // whatever is already there — so merging them into one shared pass changes nothing about
        // what ends up on screen, only how many times the GPU is asked to begin one.
        GpuTextureView colorTexture = minecraft.getMainRenderTarget().getColorTextureView();
        GpuTextureView depthTexture = minecraft.getMainRenderTarget().getDepthTextureView();
        try (GpuBuffer quadBuffer = buildQuadBuffer(quads);
                RenderPass renderPass = RenderSystem.getDevice()
                        .createCommandEncoder()
                        .createRenderPass(() -> "Astronima sky", colorTexture, OptionalInt.empty(),
                                depthTexture, OptionalDouble.empty())) {
            // Band first, stars after: the band's alpha then only ever tints the black void
            // behind it, never a star that is meant to be drawn sharp on top of it.
            drawGalaxyBand(renderPass, bandTransform);
            for (int bucket = 0; bucket < bucketBuffers.length; bucket++) {
                if (bucketIndexCounts[bucket] > 0) {
                    drawBucket(renderPass, bucketBuffers[bucket], bucketIndexCounts[bucket], bucketTransforms[bucket]);
                }
            }
            // Last, deliberately: none of these pipelines depth-test against each other (neither
            // STARS nor CELESTIAL declares a DepthStencilState), so vanilla's own sun - already
            // drawn earlier in the same sky pass - has nothing stopping a star from painting over
            // it, which is exactly what was reported back with a screenshot. Redrawing an opaque
            // disc at the sun's own real position, last, is what actually guarantees it wins. The
            // moon gets the identical treatment for the identical reason - both are collected
            // after the nebula markers above, and issueRadialQuads draws them in that same order.
            issueRadialQuads(renderPass, quadBuffer, quads, quadTransforms);
        }
    }

    /** Same warm colour a Sun-like star's own black-body temperature gives — reusing
     * {@link BlackBody} here too rather than an arbitrary "sun yellow" (rule 46's spirit: one
     * colour law for every hot thing in this mod, not a bespoke one for this single case). */
    private static final int SUN_COLOR = BlackBody.rgb(5800.0);

    /** Comfortably larger than vanilla's own sun sprite (30 units wide at 100 units out), so
     * the opaque core fully covers the sprite's footprint and the stars immediately around it. */
    private static final float SUN_CORE_SIZE = 34.0F;

    /** The bloom: real light spilling past the disc's own edge, not a bigger flat shape. */
    private static final float SUN_HALO_SIZE = 75.0F;

    /** The core's quad is a square; the disc's own bulging edge noise (see
     * {@code astronima_sun_core.fsh}) needs room to reach past the shader's own d=1 circle
     * without hitting the quad's flat physical boundary first — otherwise the organic bulge
     * gets sliced by a dead straight line exactly at the quad's edge, which is what "резко
     * обрывается картинка" turned out to be: not a shading bug, a geometry-too-small bug. The
     * quad is grown by this margin (and the shader multiplies its own local coordinate by the
     * same constant, pushing the true disc radius inward so it lands back at its original
     * on-screen size) — the first version of this fix divided in the shader instead of
     * multiplying, which shrank the reachable range instead of growing it and painted almost
     * the entire oversized quad solid, a flat square filling most of the screen. Must match
     * {@code CORE_BULGE_MARGIN} in astronima_sun_core.fsh. */
    private static final float CORE_BULGE_MARGIN = 1.6F;

    /** Own seed, not reused from {@link #STAR_FIELD_SEED}: distinct concepts, no reason for one
     * to accidentally correlate with the other. */
    private static final long FLARE_SEED = 20260810L;

    /** Must match {@code SkyExposure.OCCULTATION_SEED} exactly — one schedule read from two
     * places, so what a player sees darkening the Sun and what the retort's own gauge does can
     * never disagree (design/sky.md §S5.1). */
    private static final long OCCULTATION_SEED = 20260811L;

    /** How far the occultation's own shadow centre sweeps to either side of the Sun's own centre,
     * in units of the Sun's own true radius — the shadow now lives directly inside the Sun's own
     * shader (see astronima_sun_core.fsh's own comment for why), so there is no second quad's
     * own world-space size to convert this through any more. */
    private static final float OCCULTATION_SWEEP_RADII = 1.3F;

    private static final float OCCULTATION_MIN_RADIUS = 0.2F;
    private static final float OCCULTATION_MAX_RADIUS_BONUS = 0.65F;

    /** How long a debug-forced preview takes for one full sweep - slower than a real transit's
     * own 400-tick duration so it is comfortable to actually watch. */
    private static final long DEBUG_PREVIEW_SWEEP_PERIOD_TICKS = OccultationSchedule.DURATION_TICKS * 3L;

    /**
     * The sun, drawn last in the pass as two layers — an opaque, radially-gradiented core
     * (the shape and the actual guarantee nothing shows through it) and a larger additive
     * halo (the bloom). Both replace an earlier flat single-colour quad that was reported
     * back looking exactly like what it was: a tinted square, not a sun.
     *
     * <p>design/sky.md §S5.1's first cut of the flare event: real, scheduled, visible — the sun's
     * own colour mixes toward white and its halo grows, both driven by {@link
     * SkyEventOverride#resolveFlareIntensity} (the real schedule, unless {@code /astronima sky
     * flare} is forcing a test value). Deliberately not yet the asymmetric CME arc the design
     * also calls for (needs new shader geometry) and not yet tied to any hazard (no radiation/dose
     * mechanic exists in this mod at all — named and deferred to the design's own v0.79 tier,
     * not silently skipped).
     */
    private static void collectSun(List<RadialQuad> quads, Matrix4f cameraModelView, long gameTimeTicks) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float sunAngleDegrees = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, 0.0F);
        Vector3f sunDirection = celestialDirection(sunAngleDegrees);
        // Seconds, not ticks: the noise frequencies in the sun shaders were tuned by eye against
        // a plain float, and ticks/20 keeps that same scale while still growing without bound
        // slowly enough that float precision never becomes visible at any realistic play length.
        float timeSeconds = (gameTimeTicks % 1_000_000L) / 20.0F;

        double flareIntensity = SkyEventOverride.resolveFlareIntensity(gameTimeTicks, FLARE_SEED);
        int flareColor = mixTowardWhite(SUN_COLOR, flareIntensity);
        float haloSize = SUN_HALO_SIZE * (1.0F + (float) flareIntensity * 0.4F);

        // Only the core carries the occultation's own shadow - the corona sits behind the disc
        // conceptually, and the y/z components are otherwise unused for the halo (kept at 0).
        Vector3f coreModelOffset = new Vector3f(timeSeconds, 0.0F, 0.0F);
        applyOccultationShadow(coreModelOffset, gameTimeTicks);

        quads.add(new RadialQuad(AstronimaSkyPipelines.sunCore(), cameraModelView, sunDirection,
                SUN_CORE_SIZE * CORE_BULGE_MARGIN, flareColor, coreModelOffset, new Matrix4f()));
        quads.add(new RadialQuad(AstronimaSkyPipelines.sunHalo(), cameraModelView, sunDirection, haloSize,
                flareColor, new Vector3f(timeSeconds, 0.0F, 0.0F), new Matrix4f()));
    }

    /**
     * Fills {@code modelOffset}'s otherwise-unused y/z with the occultation's own shadow centre
     * and radius, both in units of the Sun's own true radius, for {@code astronima_sun_core.fsh}
     * to read directly — {@code z <= 0} means no occultation at all, which the shader treats as
     * "skip the whole block."
     */
    private static void applyOccultationShadow(Vector3f modelOffset, long gameTimeTicks) {
        long activeStart = OccultationSchedule.activeTransitStart(gameTimeTicks, OCCULTATION_SEED);
        double depth = SkyEventOverride.resolveOccultationDepth(gameTimeTicks, OCCULTATION_SEED);
        if (activeStart < 0 && depth <= 0.0) {
            return;
        }
        float sweepFraction;
        if (activeStart >= 0) {
            sweepFraction = (gameTimeTicks - activeStart) / (float) OccultationSchedule.DURATION_TICKS;
        } else {
            // A forced debug override has no real elapsed time to sweep against - a smooth
            // back-and-forth preview instead of a hard modulo wrap, which snapped instantly back
            // to its start every cycle and read exactly like the bug it was: "появляется прям из
            // середины солнца слева" (appears right out of the middle of the Sun, on the left).
            long period = DEBUG_PREVIEW_SWEEP_PERIOD_TICKS;
            float raw = (gameTimeTicks % period) / (float) period * 2.0F;
            sweepFraction = raw <= 1.0F ? raw : 2.0F - raw;
        }
        modelOffset.y = (sweepFraction * 2.0F - 1.0F) * OCCULTATION_SWEEP_RADII;
        modelOffset.z = OCCULTATION_MIN_RADIUS
                + OCCULTATION_MAX_RADIUS_BONUS * (float) (depth / OccultationSchedule.MAX_DEPTH);
    }

    /** {@code factor} of the way from {@code colorRgb} toward white — the sun's own real,
     * scheduled brightening during a flare (design/sky.md §S5.1), not a texture swap. */
    private static int mixTowardWhite(int colorRgb, double factor) {
        if (factor <= 0.0) {
            return colorRgb;
        }
        int r = (colorRgb >> 16) & 0xFF;
        int g = (colorRgb >> 8) & 0xFF;
        int b = colorRgb & 0xFF;
        r += Math.round((255 - r) * factor);
        g += Math.round((255 - g) * factor);
        b += Math.round((255 - b) * factor);
        return (r << 16) | (g << 8) | b;
    }

    /** Same warm-white a Sun-like star's own black-body temperature gives, dimmed and left to the
     * shader's own regolith texture to do the rest — the Moon has no light of its own, it is
     * this same colour, reflected. */
    private static final int MOON_COLOR = 0xC8C4BE;
    private static final float MOON_SIZE = 22.0F;
    private static final float MOON_GLOW_SIZE = 32.0F;

    /**
     * The moon, drawn last for the same occlusion reason as the sun.
     *
     * <p><strong>The first version shaded from the Sun's own real direction, projected into the
     * Moon's local basis — wrong.</strong> Reported back as "just white no matter the time of
     * day"; {@code /astronima sky} (added to chase this down) printed {@code sun_angle}/
     * {@code moon_angle} side by side and showed why: {@code asteroid_day.json}'s own
     * {@code moon_angle} track is the sun's track with a constant +180° added to both keyframes,
     * so the Moon sits in exact opposition to the Sun at literally every tick of every day.
     *
     * <p><strong>The second version read the real {@code MOON_PHASE} attribute — also wrong.</strong>
     * The user re-ran {@code /astronima sky} across several {@code /time set} jumps spanning far
     * more than one 8-phase cycle, and {@code moon_phase} never once left {@code full_moon}.
     * Reading {@code net.minecraft.world.timeline.Timeline} (rule 2) showed why: a timeline only
     * ever writes the attributes explicitly listed in its own {@code tracks} map, and this
     * dimension's timeline carries no {@code minecraft:visual/moon_phase} track at all — nothing
     * anywhere ever assigns it, so it sits at its registered default forever.
     *
     * <p><strong>The third version paced the phase against real elapsed game ticks — also
     * wrong, for a usability reason.</strong> {@code Level#getGameTime()} deliberately does not
     * move with {@code /time set} (verified against {@code TimeCommand}), which is exactly why
     * the asteroid's own axial rotation is tied to it. Reusing that reasoning for the Moon's
     * phase was a mistake in practice: reported back a second time as "тупо белая... даже когда
     * время проматываю" — from the tester's chair, indistinguishable from the first two bugs,
     * because scrubbing time with {@code /time set} never touches {@code gameTime} at all.
     *
     * <p>Fixed by pacing against {@code SUN_ANGLE} itself instead ({@link
     * MoonPhaseLighting#phaseIndexForSunAngle}) — the one value every round of this bug has
     * already shown responds correctly and immediately to {@code /time set}. {@link
     * MoonPhaseLighting#direction} turns the resulting index into a light direction exactly as
     * before; the shader itself ({@code astronima_moon.fsh}) has needed no change through any of
     * the three rounds.
     */
    private static void collectMoon(List<RadialQuad> quads, Matrix4f cameraModelView) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float moonAngleDegrees = camera.attributeProbe().getValue(EnvironmentAttributes.MOON_ANGLE, 0.0F);
        float sunAngleDegrees = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, 0.0F);
        Vector3f moonDirection = celestialDirection(moonAngleDegrees);

        int phaseIndex = MoonPhaseLighting.phaseIndexForSunAngle(sunAngleDegrees);
        SkyRotation.Vec3 phaseDirection = MoonPhaseLighting.direction(phaseIndex);
        Vector3f phaseLightDirection =
                new Vector3f((float) phaseDirection.x(), (float) phaseDirection.y(), (float) phaseDirection.z());

        quads.add(new RadialQuad(AstronimaSkyPipelines.moon(), cameraModelView, moonDirection, MOON_SIZE, MOON_COLOR,
                phaseLightDirection, new Matrix4f()));
        quads.add(new RadialQuad(AstronimaSkyPipelines.moonGlow(), cameraModelView, moonDirection, MOON_GLOW_SIZE,
                MOON_COLOR, phaseLightDirection, new Matrix4f()));
    }

    /** Own seed, matching {@code PassingBodyScheduleTest}'s own {@code SEED} — distinct from
     * every other schedule's seed for the same reason as {@link #FLARE_SEED}. */
    private static final long PASSING_BODY_SEED = 20260813L;

    /** A companion body reads as small and dark in real reflected sunlight (typical asteroid
     * albedo is low, nothing like the Moon's own brighter regolith) — smaller than
     * {@link #MOON_SIZE} and noticeably darker than {@link #MOON_COLOR}. The first fix bumped this
     * from 8 to 10 to compensate for a shorter pass; reported back as "слишком больше и некрасиво"
     * (too big and ugly) — a close flyby is genuinely a small, fast point of reflected light, not
     * a disc anyone gets a good look at, so this is now a fifth of that, well under even the first
     * draft's own original size. */
    private static final float PASSING_BODY_SIZE = 2.0F;

    private static final int PASSING_BODY_COLOR = 0x8C8880;

    /**
     * design/sky.md §3's "passing body" row: a companion asteroid drifts across a real, different
     * path each pass (see {@link PassingBodySchedule#startDirection}/{@code endDirection}),
     * visibly tumbling ({@code astronima_passing_body.fsh}'s own independent rotation).
     *
     * <p>Direction is a straight per-frame lerp between the pass's fixed start and end points,
     * renormalised to the unit sphere — real in kind (an object on a straight-line trajectory
     * relative to a distant, near-stationary observer does trace a great-circle-like arc across
     * the sky over a span this short), not an orbit integration, which would be real effort spent
     * on a difference invisible across a pass this brief.
     *
     * <p><strong>The first cut of this method was reported back exactly as bad as it was:</strong>
     * "тупо крутящийся шарик который стоит на одном месте... он даже не двигается" (just a
     * spinning ball standing in one place, it doesn't even move). True, for two compounding
     * reasons fixed in {@link PassingBodySchedule} itself rather than here — a 600-tick (30 second)
     * crossing moved only a couple of degrees per second, and nothing guaranteed the start and end
     * points weren't already close together — so the only thing changing fast enough to notice was
     * the shader's own surface tumble, exactly matching "spinning in place." {@link
     * PassingBodySchedule#endDirection} now guarantees a real 70-140 degree sweep every pass; this
     * method itself needed no change for that round, since it was already correctly reading
     * whatever the schedule gave it.
     *
     * <p><strong>Second round, same complaint from a different angle:</strong> "слишком больше и
     * некрасивое, нужно уменьшить размер раза в 5, и сделать ещё быстреё" (too big and ugly, make
     * it about five times smaller, and even faster). {@link PassingBodySchedule#DURATION_TICKS}
     * dropped from 100 to 40 (two seconds, not five) and {@link #PASSING_BODY_SIZE} from 10 down to
     * 2 — a genuinely close flyby is a small, fast point of reflected light crossing the sky in a
     * couple of seconds, not a disc anyone gets a good long look at.
     *
     * <p><strong>Third round, a genuine bug in the debug trigger, plus a real layout complaint:
     * </strong> "несколько раз ввёл команду двигался и спавнился всегда на одном и том-же месте, и
     * лучше сделать что-бы спавнился за горизонтом" (triggered the command several times, it moved
     * but always spawned in the same place; better to have it spawn beyond the horizon). The first
     * half was a real bug in {@link PassingBodySchedule#cycleForEventStart} — its own fallback
     * bucketed every forced trigger within the same ~50000-tick window onto one identical cycle
     * number, fixed there (not here) by hashing the raw trigger tick directly instead. The second
     * half is why {@link PassingBodySchedule#startDirection}/{@code endDirection} now sample from a
     * low band around the horizon rather than the full sphere: a real close flyby is seen rising
     * and setting near the horizon, not popping into existence overhead, and staying in that band
     * makes a pass far more likely to cross an ordinary player's actual eye-line. Not coupled to
     * the live camera direction specifically — see that constant's own comment for why doing so
     * would break design/sky.md §7's "two players, same sky, same world time" guarantee.
     */
    private static void collectPassingBody(List<RadialQuad> quads, Matrix4f cameraModelView, long gameTimeTicks) {
        long activeStart = SkyEventOverride.resolvePassingBodyActiveStart(gameTimeTicks, PASSING_BODY_SEED);
        if (activeStart < 0) {
            return;
        }
        long cycleIndex = PassingBodySchedule.cycleForEventStart(activeStart, PASSING_BODY_SEED);
        float progress = Mth.clamp(
                (gameTimeTicks - activeStart) / (float) PassingBodySchedule.DURATION_TICKS, 0.0F, 1.0F);

        SkyRotation.Vec3 start = PassingBodySchedule.startDirection(cycleIndex, PASSING_BODY_SEED);
        SkyRotation.Vec3 end = PassingBodySchedule.endDirection(cycleIndex, PASSING_BODY_SEED);
        Vector3f direction = new Vector3f(
                (float) (start.x() + (end.x() - start.x()) * progress),
                (float) (start.y() + (end.y() - start.y()) * progress),
                (float) (start.z() + (end.z() - start.z()) * progress)).normalize();

        float timeSeconds = (gameTimeTicks % 1_000_000L) / 20.0F;
        quads.add(new RadialQuad(AstronimaSkyPipelines.passingBody(), cameraModelView, direction, PASSING_BODY_SIZE,
                PASSING_BODY_COLOR, new Vector3f(timeSeconds, 0.0F, 0.0F), new Matrix4f()));
    }

    /** Own seed, matching {@code CometScheduleTest}'s own {@code SEED}. */
    private static final long COMET_SEED = 20260814L;

    /** Big enough to read as a real, calendar-worthy spectacle at peak growth — comparable to
     * {@link #MOON_SIZE}, since a bright comet is genuinely one of the more dramatic naked-eye
     * sights a sky offers, and the quad also has to be large enough for the tail (drawn inside
     * the same quad by {@code astronima_comet.fsh}, not as separate geometry) to read as a real
     * streak rather than a stub. */
    private static final float COMET_SIZE = 20.0F;

    /** White: the shader computes its own real coma/tail colours (Swan-band coma, bluer ion
     * tail) and multiplies them by {@code ColorModulator}, the same reason every named-object
     * marker also passes white rather than tinting the shader's own physics. */
    private static final int COMET_COLOR = 0xFFFFFF;

    /**
     * Where the comet's tails are anchored: {@code 0.0} is the real physics (both tails swept
     * directly anti-sunward by the solar wind, regardless of which way the comet travels),
     * {@code 1.0} trails them behind the direction of motion instead.
     *
     * <p><strong>Set to 1.0 deliberately, and this is an invented behaviour, not a real one.</strong>
     * A real comet's tail is not a wake — nothing is left behind it; the solar wind blows the tail
     * radially away from the Sun, so on the outbound leg of a real orbit the tail genuinely runs
     * <em>ahead</em> of the nucleus. That was built first and verified correct end to end (an
     * independent bilinear check against {@code addFacingTexQuad}'s own real vertex/UV table
     * confirmed the drawn tail fragments really do land farther from the Sun than the head). It was
     * reported back as reading wrong all the same — "кажется буд-то комета летит задом наперёд"
     * (it looks like the comet is flying backwards), because the eye reads any trail as a wake —
     * and, told plainly that the anti-sunward version was the physically real one, the call was
     * made anyway: "нужно что-бы трейл смотрел от стороны движения а не солнца."
     *
     * <p>That is the owner's call on a VFX-tier object and it is taken as given here, but it is
     * recorded rather than buried: this mod's own standard is that invented physics is labelled as
     * invented (see design/sky.md's own note on this event). Dropping this constant back to
     * {@code 0.0} restores the real behaviour with no other change anywhere.
     */
    private static final float TAIL_ANCHOR_MOTION = 1.0F;

    /** How far the dust tail fans off the main tail, in radians at its widest. The Sun still sets
     * this fan's own direction and size (see {@link #drawComet}), so where the Sun is remains
     * visible in the picture even with the main tail anchored to motion. */
    private static final float DUST_TAIL_FAN_RADIANS = 0.45F;

    /**
     * design/sky.md §3's "comet" row, the design's own "Calendar" event — grows over real days,
     * holds, fades over real weeks ({@link CometSchedule}), travelling a real arc against the fixed
     * stars as it goes, with two tails computed from the comet's own real velocity along that arc
     * and the Sun's real current position. <strong>Which of those two anchors the tails is a
     * deliberate departure from real physics — see {@link #TAIL_ANCHOR_MOTION}.</strong>
     *
     * <p><strong>The first cut left the comet stationary and said so as a named simplification;
     * that was reported back as exactly what it was</strong> — "комета постоянно на месте стоит не
     * двигается" (the comet just stands there and doesn't move). Removed rather than defended: a
     * comet's proper motion against the background stars is real (it is how comets were found and
     * tracked before photography). The comet visibly travels now, and the two tails visibly
     * separate as it goes.
     *
     * <p>{@link SkyRotation#currentDirection} converts the comet's own fixed sky-space direction
     * into the same "current, un-rotated-by-our-own-asteroid-spin" frame {@link #celestialDirection}
     * already returns the Sun in — the identical maths the renderer's own JOML skyRotation already
     * performs on the GPU for the starfield, proven equivalent by {@code SkyRotationTest}, reused
     * here on the CPU so the tail vector math has every direction in one consistent frame.
     */
    private static void collectComet(List<RadialQuad> quads, Matrix4f cameraModelView, long gameTimeTicks) {
        long activeStart = SkyEventOverride.resolveCometActiveStart(gameTimeTicks, COMET_SEED);
        if (activeStart < 0) {
            return;
        }
        long elapsed = gameTimeTicks - activeStart;
        boolean preview = SkyEventOverride.isCometPreviewActive(gameTimeTicks);
        double growth = preview
                ? CometSchedule.previewGrowthForElapsed(elapsed)
                : CometSchedule.growthForElapsedTicks(elapsed);
        if (growth <= 0.0) {
            return;
        }
        // The same fraction-of-its-own-apparition in both cases, so a debug preview shows the whole
        // arc compressed into its twenty seconds while a real apparition drifts across it over its
        // real two weeks - one path, two playback speeds, rather than two different behaviours.
        long duration = preview ? CometSchedule.PREVIEW_DURATION_TICKS : CometSchedule.DURATION_TICKS;
        double progress = Mth.clamp(elapsed / (double) duration, 0.0, 1.0);

        long cycleIndex = CometSchedule.cycleForEventStart(activeStart, COMET_SEED);
        SkyRotation.Vec3 fixedNow = CometSchedule.directionAtProgress(cycleIndex, COMET_SEED, progress);
        SkyRotation.Vec3 current = SkyRotation.currentDirection(fixedNow, gameTimeTicks);
        Vector3f cometDirection = new Vector3f((float) current.x(), (float) current.y(), (float) current.z());

        // The comet's own velocity on the sky, as a real forward difference along its own path -
        // sampled in the same rotated frame as the position itself so the two are directly
        // comparable, rather than one in fixed sky space and one in current space.
        double aheadProgress = Math.min(1.0, progress + 1.0e-3);
        SkyRotation.Vec3 fixedAhead = CometSchedule.directionAtProgress(cycleIndex, COMET_SEED, aheadProgress);
        SkyRotation.Vec3 ahead = SkyRotation.currentDirection(fixedAhead, gameTimeTicks);
        Vector3f velocity = new Vector3f((float) (ahead.x() - current.x()),
                (float) (ahead.y() - current.y()), (float) (ahead.z() - current.z()));

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float sunAngleDegrees = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, 0.0F);
        Vector3f sunDirection = celestialDirection(sunAngleDegrees);

        Vector3f awayFromSun = tangentAwayFrom(cometDirection, sunDirection);
        Vector3f trailing = tangentAwayFrom(cometDirection, velocity);
        // See TAIL_ANCHOR_MOTION: 0 is the real anti-sunward sweep, 1 trails behind the motion.
        Vector3f mainTail = new Vector3f(awayFromSun).mul(1.0F - TAIL_ANCHOR_MOTION)
                .add(new Vector3f(trailing).mul(TAIL_ANCHOR_MOTION));
        if (mainTail.lengthSquared() < 1.0e-8F) {
            mainTail.set(awayFromSun);
        }
        mainTail.normalize();

        // The dust tail fans off the main one, and the Sun is what decides which side and how far:
        // the fan follows the Sun's own offset from the tail axis, smoothly reversing (through
        // zero, so it never snaps) as the Sun crosses it. Keeps the Sun's real position legible in
        // the picture even though the main tail no longer follows it.
        Vector3f inPlane = new Vector3f(cometDirection).normalize().cross(mainTail, new Vector3f());
        Vector3f dust = new Vector3f(mainTail);
        if (inPlane.lengthSquared() > 1.0e-8F) {
            inPlane.normalize();
            float fan = DUST_TAIL_FAN_RADIANS * Mth.clamp(awayFromSun.dot(inPlane), -1.0F, 1.0F);
            dust.set(new Vector3f(mainTail).mul(Mth.cos(fan)).add(new Vector3f(inPlane).mul(Mth.sin(fan))));
            dust.normalize();
        }

        float ionAngle = quadSpaceAngle(cometDirection, mainTail);
        float dustAngle = quadSpaceAngle(cometDirection, dust);

        float timeSeconds = (gameTimeTicks % 1_000_000L) / 20.0F;
        Vector3f modelOffset = new Vector3f(timeSeconds, ionAngle, (float) growth);
        quads.add(new RadialQuad(AstronimaSkyPipelines.comet(), cameraModelView, cometDirection, COMET_SIZE,
                COMET_COLOR, modelOffset, new Matrix4f().m00(dustAngle)));
    }

    /**
     * The unit direction, in the plane tangent to the sky at {@code objectDirection}, pointing
     * directly <em>away</em> from {@code source} — the real construction behind both of a comet's
     * tails: project {@code source} into the tangent plane (Gram-Schmidt) and negate.
     */
    private static Vector3f tangentAwayFrom(Vector3f objectDirection, Vector3f source) {
        Vector3f object = new Vector3f(objectDirection).normalize();
        if (source.lengthSquared() < 1.0e-12F) {
            return new Vector3f(1.0F, 0.0F, 0.0F);
        }
        Vector3f unitSource = new Vector3f(source).normalize();
        float along = unitSource.dot(object);
        Vector3f tangent = new Vector3f(unitSource).sub(new Vector3f(object).mul(along));
        Vector3f away = tangent.negate();
        if (away.lengthSquared() < 1.0e-6F) {
            // Degenerate only when the source sits exactly at the object's own place in the sky -
            // any direction is as good as any other, and for the Sun's case a comet that deep in
            // real solar glare is at its least observable anyway.
            away.set(1.0F, 0.0F, 0.0F);
        }
        return away.normalize();
    }

    /**
     * A direction already lying in the sky-tangent plane at {@code objectDirection}, converted into
     * the exact local axes {@code astronima_comet.fsh}'s own {@code localPos} uses.
     *
     * <p>{@code addFacingTexQuad} assigns UVs so that, worked out by hand against its own vertex
     * table: the fragment shader's {@code localPos.x} axis is this quad's own "up" direction, and
     * {@code localPos.y} is its own "right" direction negated — not a guess, the one mapping
     * consistent with every one of that method's four vertex/UV pairs at once.
     */
    private static float quadSpaceAngle(Vector3f objectDirection, Vector3f tangentDirection) {
        Vector3f object = new Vector3f(objectDirection).normalize();
        Matrix3f basis = new Matrix3f().rotateTowards(new Vector3f(object).negate(), upReferenceFor(object));
        Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).mul(basis);
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).mul(basis);
        float fragX = tangentDirection.dot(up);
        float fragY = -tangentDirection.dot(right);
        return (float) Math.atan2(fragY, fragX);
    }

    /** Own seed, matching {@code SupernovaScheduleTest}'s own {@code SEED}. */
    private static final long SUPERNOVA_SEED = 20260815L;

    /** Bigger than an ordinary star's own {@link #MAX_STAR_SIZE_BONUS}-topped size, smaller than a
     * named nebula marker — reads as a genuinely new, unmissable point rather than a patch. */
    private static final float SUPERNOVA_SIZE = 7.0F;

    /**
     * design/sky.md §3's "supernova" row: a new star appears where there was none, real content
     * driven entirely by {@link SupernovaSchedule} (when/how bright) and {@link SupernovaSpectrum}
     * (what colour, via the exact same temperature curve {@code /astronima magic spectrum
     * supernova} reads — computed once, in one place, not tuned twice). {@link BlackBody#rgb} is
     * the one real colour law this whole file already uses for every hot thing in it (the Sun, the
     * star buckets); reusing it here means this object's on-screen colour actually shifts blue to
     * orange as it cools over its own real evolution, not an invented palette.
     */
    private static void collectSupernova(List<RadialQuad> quads, Matrix4f cameraModelView, long gameTimeTicks) {
        long activeStart = SkyEventOverride.resolveSupernovaActiveStart(gameTimeTicks, SUPERNOVA_SEED);
        if (activeStart < 0) {
            return;
        }
        long elapsed = gameTimeTicks - activeStart;
        double growth = SkyEventOverride.isSupernovaPreviewActive(gameTimeTicks)
                ? SupernovaSchedule.previewGrowthForElapsed(elapsed)
                : SupernovaSchedule.growthForElapsedTicks(elapsed);
        if (growth <= 0.0) {
            return;
        }
        long cycleIndex = SupernovaSchedule.cycleForEventStart(activeStart, SUPERNOVA_SEED);
        SkyRotation.Vec3 fixed = SupernovaSchedule.direction(cycleIndex, SUPERNOVA_SEED);
        SkyRotation.Vec3 current = SkyRotation.currentDirection(fixed, gameTimeTicks);
        Vector3f direction = new Vector3f((float) current.x(), (float) current.y(), (float) current.z());

        double temperatureK = SupernovaSpectrum.temperatureKAt(elapsed);
        int color = BlackBody.rgb(temperatureK);

        float timeSeconds = (gameTimeTicks % 1_000_000L) / 20.0F;
        Vector3f modelOffset = new Vector3f(timeSeconds, (float) growth, 0.0F);
        quads.add(new RadialQuad(AstronimaSkyPipelines.supernova(), cameraModelView, direction, SUPERNOVA_SIZE,
                color, modelOffset, new Matrix4f()));
    }

    /**
     * The shared direction formula behind both the sun and the moon, replicated from
     * {@code SkyRenderer.renderSunMoonAndStars} exactly rather than approximated: rotate -90°
     * about Y, then by the body's own angle about X — verified against the real source (rule 2),
     * because a guessed direction here would put an occluder in the wrong place and make the
     * problem worse, not better. Vanilla calls this with {@code sunAngle} and {@code moonAngle}
     * in turn from the same two lines of its own code; this is that same one formula, not two.
     */
    private static Vector3f celestialDirection(float angleDegrees) {
        Matrix3f frame = new Matrix3f()
                .rotateY((float) Math.toRadians(-90.0))
                .rotateX((float) Math.toRadians(angleDegrees));
        return frame.transform(new Vector3f(0.0F, 1.0F, 0.0F));
    }

    /** One quad per {@link NamedSkyObjects#ALL} placement, each drawn through whichever of the
     * three nebula pipelines matches its real {@link NamedSkyObjects.Kind} — an emission nebula,
     * a reflection nebula and a planetary nebula are physically different classes of object, so
     * each gets its own shader rather than one shape with a colour swapped in (the user's own
     * standing request: every body its own animation, its own signature). Built from the exact
     * same fixed direction {@link NamedSkyObjects#lookedAt} tests the spectrograph's aim against,
     * so the render side and the targeting side stay one shared source of truth. */
    private static void collectNebulaMarkers(List<RadialQuad> quads, Matrix4f modelView, long gameTimeTicks) {
        float timeSeconds = (gameTimeTicks % 1_000_000L) / 20.0F;
        // Shared, not copied, across every marker below - safe because nothing here ever mutates
        // it again, only reads it later when the buffer is built.
        Vector3f time = new Vector3f(timeSeconds, 0.0F, 0.0F);
        for (NamedSkyObjects.Placement placement : NamedSkyObjects.ALL) {
            SkyRotation.Vec3 fixed = placement.fixedDirection();
            Vector3f direction = new Vector3f((float) fixed.x(), (float) fixed.y(), (float) fixed.z());
            var pipeline = switch (placement.kind()) {
                case EMISSION -> AstronimaSkyPipelines.nebula();
                case REFLECTION -> AstronimaSkyPipelines.reflectionNebula();
                case PLANETARY -> AstronimaSkyPipelines.planetaryNebula();
                case GALAXY -> AstronimaSkyPipelines.galaxyDisk();
            };
            quads.add(new RadialQuad(pipeline, modelView, direction, NEBULA_MARKER_SIZE, 0xFFFFFF, time,
                    new Matrix4f()));
        }
    }

    /** One combined {@code POSITION_TEX} vertex buffer for every {@link RadialQuad} this frame,
     * each object's own four vertices at a fixed offset ({@code quads.get(i)} lives at vertex
     * {@code i*4}) — replaces a fresh tiny {@code GpuBuffer} allocated and destroyed per object
     * every frame with exactly one allocation sized for however many objects are actually on
     * screen. Every one of these pipelines shares the identical {@code POSITION_TEX}/
     * {@code TRIANGLE_FAN} vertex layout (confirmed in {@code AstronimaSkyPipelines}, not
     * assumed), which is what makes batching them into one buffer safe at all. */
    private static GpuBuffer buildQuadBuffer(List<RadialQuad> quads) {
        try (ByteBufferBuilder byteBuffer = ByteBufferBuilder
                .exactlySized(DefaultVertexFormat.POSITION_TEX.getVertexSize() * 4 * quads.size())) {
            BufferBuilder bufferBuilder =
                    new BufferBuilder(byteBuffer, VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_TEX);
            for (RadialQuad quad : quads) {
                addFacingTexQuad(bufferBuilder, quad.direction(), STAR_DISTANCE, quad.size());
            }
            try (MeshData mesh = bufferBuilder.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(() -> "Astronima sky quads", 32, mesh.vertexBuffer());
            }
        }
    }

    /** One {@code setPipeline}/{@code setUniform}/{@code draw} per quad, each against its own
     * four-vertex slice of the one shared buffer {@link #buildQuadBuffer} built, all inside the
     * single render pass {@link #onAfterSky} already opened — this loop, not the buffer above, is
     * where the real saving over the old one-render-pass-per-object shape actually is.
     *
     * <p>{@code transforms} is precomputed and passed in rather than written here: {@code
     * DynamicUniforms#writeTransform} calls {@code CommandEncoder#mapBuffer}, which refuses to run
     * at all while a render pass is open ("Close the existing render pass before performing
     * additional commands") — a real crash the first cut of this method hit immediately on
     * `runClient`, invisible to `runGameTestServer` since that harness has no GPU on its
     * classpath at all. Every uniform this frame needs is written before the pass opens; this
     * loop only ever reads one back. */
    private static void issueRadialQuads(RenderPass renderPass, GpuBuffer quadBuffer, List<RadialQuad> quads,
                                         List<GpuBufferSlice> transforms) {
        for (int i = 0; i < quads.size(); i++) {
            RadialQuad quad = quads.get(i);
            renderPass.setPipeline(quad.pipeline());
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", transforms.get(i));
            renderPass.setVertexBuffer(0, quadBuffer);
            renderPass.draw(i * 4, 4);
        }
    }

    /** Same construction as {@link #addFacingQuad}, with UV corners added so the fragment
     * shader can compute a true radial distance from the quad's own centre. */
    private static void addFacingTexQuad(BufferBuilder builder, Vector3f direction, float distance, float size) {
        Vector3f center = new Vector3f(direction).normalize(distance);
        Matrix3f rotation = new Matrix3f()
                .rotateTowards(new Vector3f(center).negate(), upReferenceFor(direction));
        builder.addVertex(new Vector3f(size, -size, 0.0F).mul(rotation).add(center)).setUv(0.0F, 0.0F);
        builder.addVertex(new Vector3f(size, size, 0.0F).mul(rotation).add(center)).setUv(1.0F, 0.0F);
        builder.addVertex(new Vector3f(-size, size, 0.0F).mul(rotation).add(center)).setUv(1.0F, 1.0F);
        builder.addVertex(new Vector3f(-size, -size, 0.0F).mul(rotation).add(center)).setUv(0.0F, 1.0F);
    }

    /**
     * The reference axis {@code rotateTowards} needs to disambiguate roll — (0,1,0) almost
     * always, except when the target direction is itself nearly parallel to (0,1,0), which is
     * exactly what "the sun at local noon" is (straight overhead). A look-at rotation with its
     * target and its up reference pointing the same way is a textbook gimbal lock: the cross
     * product between them degenerates and the rotation comes out undefined, which is why the
     * sun disc was reported missing at noon specifically and nowhere else along its arc.
     * Switching to (0,0,1) only in that narrow case keeps every other quad — including every
     * star, whose near-pole ones give the mod its (kept, liked) twinkle — on the path that
     * already works.
     */
    private static Vector3f upReferenceFor(Vector3f direction) {
        Vector3f unit = new Vector3f(direction).normalize();
        return Math.abs(unit.y) > 0.999F
                ? new Vector3f(0.0F, 0.0F, 1.0F)
                : new Vector3f(0.0F, 1.0F, 0.0F);
    }

    /** A single rotation about a fixed tilted axis, so the whole assembly spins as one rigid
     * body rather than wobbling — the asteroid's own spin, not a wandering camera trick. */
    private static Matrix4f skyRotation(long gameTimeTicks) {
        float spinAngle = (float) (gameTimeTicks / ROTATION_PERIOD_TICKS * Math.PI * 2.0);
        float tilt = (float) Math.toRadians(POLE_TILT_DEGREES);
        Vector3f poleAxis = new Vector3f(Mth.sin(tilt), Mth.cos(tilt), 0.0F);
        return new Matrix4f().rotate(spinAngle, poleAxis);
    }

    /**
     * The galactic band and its nebulosity — an actual custom fragment shader
     * ({@link AstronimaSkyPipelines#galaxyBand()}), because unlike the starfield's colour this
     * has no equivalent trick with an existing vanilla pipeline: noise across a dome needs a
     * shader to compute it, there is no bucket-and-reuse shortcut for it.
     */
    private static void drawGalaxyBand(RenderPass renderPass, GpuBufferSlice dynamicTransforms) {
        var quadIndices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = quadIndices.getBuffer(galaxyDomeIndexCount);

        renderPass.setPipeline(AstronimaSkyPipelines.galaxyBand());
        RenderSystem.bindDefaultUniforms(renderPass);
        renderPass.setUniform("DynamicTransforms", dynamicTransforms);
        renderPass.setVertexBuffer(0, galaxyDomeBuffer);
        renderPass.setIndexBuffer(indexBuffer, quadIndices.type());
        renderPass.drawIndexed(0, 0, galaxyDomeIndexCount, 1);
    }

    /** A UV-sphere of quads; the fragment shader reads each fragment's own direction, so the
     * only job of this geometry is to cover every direction at a resolution fine enough that
     * its own facets do not show against the shader's smooth noise. */
    private static GpuBuffer buildGalaxyDome() {
        int quadCount = DOME_LAT_SEGMENTS * DOME_LON_SEGMENTS;
        try (ByteBufferBuilder byteBuffer = ByteBufferBuilder
                .exactlySized(DefaultVertexFormat.POSITION.getVertexSize() * quadCount * 4)) {
            BufferBuilder bufferBuilder =
                    new BufferBuilder(byteBuffer, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            for (int lat = 0; lat < DOME_LAT_SEGMENTS; lat++) {
                float theta0 = (float) lat / DOME_LAT_SEGMENTS * (float) Math.PI;
                float theta1 = (float) (lat + 1) / DOME_LAT_SEGMENTS * (float) Math.PI;
                for (int lon = 0; lon < DOME_LON_SEGMENTS; lon++) {
                    float phi0 = (float) lon / DOME_LON_SEGMENTS * (float) (Math.PI * 2.0);
                    float phi1 = (float) (lon + 1) / DOME_LON_SEGMENTS * (float) (Math.PI * 2.0);
                    bufferBuilder.addVertex(spherePoint(theta0, phi0));
                    bufferBuilder.addVertex(spherePoint(theta1, phi0));
                    bufferBuilder.addVertex(spherePoint(theta1, phi1));
                    bufferBuilder.addVertex(spherePoint(theta0, phi1));
                }
            }
            try (MeshData mesh = bufferBuilder.buildOrThrow()) {
                galaxyDomeIndexCount = mesh.drawState().indexCount();
                return RenderSystem.getDevice()
                        .createBuffer(() -> "Astronima galaxy dome", 32, mesh.vertexBuffer());
            }
        }
    }

    private static Vector3f spherePoint(float theta, float phi) {
        float sinTheta = Mth.sin(theta);
        return new Vector3f(sinTheta * Mth.cos(phi), Mth.cos(theta), sinTheta * Mth.sin(phi))
                .mul(DOME_RADIUS);
    }

    private static void drawBucket(RenderPass renderPass, GpuBuffer vertexBuffer, int indexCount,
                                   GpuBufferSlice dynamicTransforms) {
        var quadIndices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = quadIndices.getBuffer(indexCount);

        renderPass.setPipeline(RenderPipelines.STARS);
        RenderSystem.bindDefaultUniforms(renderPass);
        renderPass.setUniform("DynamicTransforms", dynamicTransforms);
        renderPass.setVertexBuffer(0, vertexBuffer);
        renderPass.setIndexBuffer(indexBuffer, quadIndices.type());
        renderPass.drawIndexed(0, 0, indexCount, 1);
    }

    /** Groups {@link StarField}'s population by temperature and builds one buffer per group. */
    private static GpuBuffer[] buildBucketBuffers() {
        List<List<StarField.Star>> buckets = new ArrayList<>(COLOR_BUCKETS);
        for (int i = 0; i < COLOR_BUCKETS; i++) {
            buckets.add(new ArrayList<>());
        }
        for (StarField.Star star : StarField.generate(STAR_COUNT, STAR_FIELD_SEED)) {
            buckets.get(bucketIndexFor(star.temperatureK())).add(star);
        }

        GpuBuffer[] buffers = new GpuBuffer[COLOR_BUCKETS];
        bucketIndexCounts = new int[COLOR_BUCKETS];
        bucketColors = new int[COLOR_BUCKETS];
        RandomSource random = RandomSource.create(STAR_FIELD_SEED);

        for (int bucket = 0; bucket < COLOR_BUCKETS; bucket++) {
            bucketColors[bucket] = BlackBody.rgb(bucketMidpointK(bucket));
            List<StarField.Star> group = buckets.get(bucket);
            if (group.isEmpty()) {
                continue;
            }
            try (ByteBufferBuilder byteBuffer = ByteBufferBuilder
                    .exactlySized(DefaultVertexFormat.POSITION.getVertexSize() * group.size() * 4)) {
                BufferBuilder bufferBuilder =
                        new BufferBuilder(byteBuffer, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
                for (StarField.Star star : group) {
                    addStarQuad(bufferBuilder, star, random);
                }
                try (MeshData mesh = bufferBuilder.buildOrThrow()) {
                    bucketIndexCounts[bucket] = mesh.drawState().indexCount();
                    int finalBucket = bucket;
                    buffers[bucket] = RenderSystem.getDevice()
                            .createBuffer(() -> "Astronima stars bucket " + finalBucket, 32, mesh.vertexBuffer());
                }
            }
        }
        return buffers;
    }

    /** One billboarded quad, facing outward from the sky's centre — the same construction
     * {@code SkyRenderer.buildStars} uses, sized from the star's own brightness rather than a
     * flat random range, so the brightest stars are visibly bigger, not just a different colour. */
    private static void addStarQuad(BufferBuilder builder, StarField.Star star, RandomSource random) {
        float size = MIN_STAR_SIZE + (float) star.brightness01() * MAX_STAR_SIZE_BONUS;
        Vector3f direction = new Vector3f((float) star.x(), (float) star.y(), (float) star.z());
        float roll = random.nextFloat() * (float) Math.PI * 2.0F;
        addFacingQuad(builder, direction, STAR_DISTANCE, size, roll);
    }

    /** A flat quad at {@code distance} out along {@code direction}, oriented to face back
     * toward the centre — vanilla's own {@code SkyRenderer.buildStars} construction, shared
     * here by both the starfield and the sun occluder rather than written twice (rule 46's
     * concern about a second copy applied to geometry, not just a formula). The vertex order
     * is a plain perimeter loop, so the same call works whether the caller's buffer is in
     * {@code QUADS} mode (stars, drawn with an index buffer) or {@code TRIANGLE_FAN} mode (the
     * sun occluder, drawn without one) — both triangulate a convex quad's own 4 corners
     * identically. */
    private static void addFacingQuad(BufferBuilder builder, Vector3f direction, float distance,
                                      float size, float roll) {
        Vector3f center = new Vector3f(direction).normalize(distance);
        Matrix3f rotation = new Matrix3f()
                .rotateTowards(new Vector3f(center).negate(), upReferenceFor(direction))
                .rotateZ(-roll);
        builder.addVertex(new Vector3f(size, -size, 0.0F).mul(rotation).add(center));
        builder.addVertex(new Vector3f(size, size, 0.0F).mul(rotation).add(center));
        builder.addVertex(new Vector3f(-size, size, 0.0F).mul(rotation).add(center));
        builder.addVertex(new Vector3f(-size, -size, 0.0F).mul(rotation).add(center));
    }

    private static int bucketIndexFor(double temperatureK) {
        double fraction = (temperatureK - StarField.MIN_TEMPERATURE_K)
                / (StarField.MAX_TEMPERATURE_K - StarField.MIN_TEMPERATURE_K);
        int bucket = (int) (fraction * COLOR_BUCKETS);
        return Math.max(0, Math.min(COLOR_BUCKETS - 1, bucket));
    }

    private static double bucketMidpointK(int bucket) {
        double span = (StarField.MAX_TEMPERATURE_K - StarField.MIN_TEMPERATURE_K) / COLOR_BUCKETS;
        return StarField.MIN_TEMPERATURE_K + span * (bucket + 0.5);
    }

    private AsteroidSkyRenderer() {}
}
