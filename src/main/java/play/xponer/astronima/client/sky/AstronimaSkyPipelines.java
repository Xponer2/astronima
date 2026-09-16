package play.xponer.astronima.client.sky;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.Astronima;

/**
 * The one custom GPU pipeline the sky needs so far — the galactic band's noise cannot be faked
 * with an existing vanilla pipeline the way the starfield's colour was (see
 * {@link AsteroidSkyRenderer}'s own note on why that stayed shader-free).
 *
 * <p>Our own GLSL, under our own namespace
 * ({@code assets/astronima/shaders/core/astronima_galaxy.vsh/.fsh}), registered through
 * NeoForge's documented {@link RegisterRenderPipelinesEvent} — not a shaderpack dependency and
 * not a mixin. Modelled directly on vanilla's own trivial {@code core/sky} shader
 * (twelve lines) rather than written from nothing.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class AstronimaSkyPipelines {
    private static @Nullable RenderPipeline galaxyBand;
    private static @Nullable RenderPipeline sunCore;
    private static @Nullable RenderPipeline sunHalo;
    private static @Nullable RenderPipeline nebula;
    private static @Nullable RenderPipeline reflectionNebula;
    private static @Nullable RenderPipeline planetaryNebula;
    private static @Nullable RenderPipeline galaxyDisk;
    private static @Nullable RenderPipeline moon;
    private static @Nullable RenderPipeline moonGlow;
    private static @Nullable RenderPipeline passingBody;
    private static @Nullable RenderPipeline comet;
    private static @Nullable RenderPipeline supernova;

    /** Null until {@link RegisterRenderPipelinesEvent} has fired, same as every other
     * registry-backed constant in this mod before its registration event runs. */
    public static RenderPipeline galaxyBand() {
        return require(galaxyBand, "galaxyBand");
    }

    /** The sun's opaque disc — a real radial gradient computed per-fragment, and the thing
     * that actually guarantees a star cannot show through it (reported back as a flat tinted
     * square before this existed). */
    public static RenderPipeline sunCore() {
        return require(sunCore, "sunCore");
    }

    /** The sun's additive glow, larger than the core and drawn after it — genuine bloom (light
     * added to what is behind it), not a bigger flat shape. */
    public static RenderPipeline sunHalo() {
        return require(sunHalo, "sunHalo");
    }

    /** A named deep-sky object's own marker (design/sky.md L4) — a soft, noise-shaped glow, not
     * a flat point; translucent like the galaxy band it shares its blend mode and technique
     * with, since a nebula is a translucent thing, not an opaque disc. */
    public static RenderPipeline nebula() {
        return require(nebula, "nebula");
    }

    /** The Pleiades' own marker — a reflection nebula, a genuinely different kind of object from
     * {@link #nebula()}'s emission family: no dual-hue split, a blue-white palette, several
     * independently-twinkling embedded stars rather than one pulsing core. */
    public static RenderPipeline reflectionNebula() {
        return require(reflectionNebula, "reflectionNebula");
    }

    /** The Helix Nebula's own marker — a real ring/annulus, not a cloud, with a teal [O III]
     * interior, a warmer H-alpha outer rim, and a slowly turning shell. */
    public static RenderPipeline planetaryNebula() {
        return require(planetaryNebula, "planetaryNebula");
    }

    /** Andromeda's own marker — a whole galaxy, not a nebula: a real elongated disc shape, a
     * warm bulge core, a dust lane, and the slowest, faintest motion of any object in this sky
     * (real galactic structure does not visibly change on a human timescale). */
    public static RenderPipeline galaxyDisk() {
        return require(galaxyDisk, "galaxyDisk");
    }

    /** The moon's own disc — real phase shading per fragment (see {@code astronima_moon.fsh}),
     * not one of vanilla's 8 baked sprite phases. Translucent blend with the interior driven to
     * near-full alpha, the same occlusion trick the sun's own core uses. */
    public static RenderPipeline moon() {
        return require(moon, "moon");
    }

    /** A small, dim, additive glow around the moon — deliberately far more restrained than the
     * sun's own halo (see that shader's own comment on why: no atmosphere, no real corona). */
    public static RenderPipeline moonGlow() {
        return require(moonGlow, "moonGlow");
    }

    /** A passing companion body's own marker (design/sky.md §3, "belt density") — real crater
     * noise and a tumbling rotation independent of its motion across the sky, translucent like
     * every other real (non-additive) sky disc. */
    public static RenderPipeline passingBody() {
        return require(passingBody, "passingBody");
    }

    /** A comet's own marker (design/sky.md §3, "Calendar") — coma plus an anti-sunward tail,
     * translucent like every other real (non-additive) sky disc here. */
    public static RenderPipeline comet() {
        return require(comet, "comet");
    }

    /** A supernova's own marker (design/sky.md §3, "Spectroscopy content") — a bright new point,
     * additive like the sun's own halo: real light genuinely this bright washes out anything
     * behind it rather than blending with it. */
    public static RenderPipeline supernova() {
        return require(supernova, "supernova");
    }

    private static RenderPipeline require(@Nullable RenderPipeline pipeline, String name) {
        if (pipeline == null) {
            throw new IllegalStateException(name + " asked for before RegisterRenderPipelinesEvent fired");
        }
        return pipeline;
    }

    @SubscribeEvent
    private static void onRegisterPipelines(RegisterRenderPipelinesEvent event) {
        galaxyBand = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(Astronima.MODID, "pipeline/galaxy_band"))
                .withVertexShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_galaxy"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_galaxy"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
                .withCull(false)
                .build();
        event.registerPipeline(galaxyBand);

        sunCore = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(Astronima.MODID, "pipeline/sun_core"))
                .withVertexShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_sun"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_sun_core"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLE_FAN)
                .withCull(false)
                .build();
        event.registerPipeline(sunCore);

        sunHalo = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(Astronima.MODID, "pipeline/sun_halo"))
                .withVertexShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_sun"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_sun_halo"))
                .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLE_FAN)
                .withCull(false)
                .build();
        event.registerPipeline(sunHalo);

        nebula = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(Astronima.MODID, "pipeline/nebula"))
                .withVertexShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_sun"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_nebula"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLE_FAN)
                .withCull(false)
                .build();
        event.registerPipeline(nebula);

        reflectionNebula = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(Astronima.MODID, "pipeline/reflection_nebula"))
                .withVertexShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_sun"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_reflection_nebula"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLE_FAN)
                .withCull(false)
                .build();
        event.registerPipeline(reflectionNebula);

        planetaryNebula = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(Astronima.MODID, "pipeline/planetary_nebula"))
                .withVertexShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_sun"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_planetary_nebula"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLE_FAN)
                .withCull(false)
                .build();
        event.registerPipeline(planetaryNebula);

        galaxyDisk = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(Astronima.MODID, "pipeline/galaxy_disk"))
                .withVertexShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_sun"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_galaxy_disk"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLE_FAN)
                .withCull(false)
                .build();
        event.registerPipeline(galaxyDisk);

        moon = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(Astronima.MODID, "pipeline/moon"))
                .withVertexShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_sun"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_moon"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLE_FAN)
                .withCull(false)
                .build();
        event.registerPipeline(moon);

        moonGlow = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(Astronima.MODID, "pipeline/moon_glow"))
                .withVertexShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_sun"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_moon_glow"))
                .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLE_FAN)
                .withCull(false)
                .build();
        event.registerPipeline(moonGlow);

        passingBody = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(Astronima.MODID, "pipeline/passing_body"))
                .withVertexShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_sun"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_passing_body"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLE_FAN)
                .withCull(false)
                .build();
        event.registerPipeline(passingBody);

        comet = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(Astronima.MODID, "pipeline/comet"))
                .withVertexShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_sun"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_comet"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLE_FAN)
                .withCull(false)
                .build();
        event.registerPipeline(comet);

        supernova = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(Astronima.MODID, "pipeline/supernova"))
                .withVertexShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_sun"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(Astronima.MODID, "core/astronima_supernova"))
                .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLE_FAN)
                .withCull(false)
                .build();
        event.registerPipeline(supernova);
    }

    private AstronimaSkyPipelines() {}
}
