package play.xponer.astronima.client.render;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.fx.BlockEffectExecutor;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.gameobject.emitter.data.MaterialSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.BlendMode;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.color.Gradient;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Cone;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.gui.editor.FXProject;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.ARGB;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueOutput;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.xponer.astronima.block.entity.SolarRetortBlockEntity;
import play.xponer.astronima.client.model.PlumbingModels;
import play.xponer.astronima.sim.ore.RetortGlow;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * The retort's sparks (design/vfx-craft.md §3, S2) — built on the Photon library per explicit
 * user direction, superseding the vanilla-{@code RenderPipeline} plan that document originally
 * sketched for this stage. See §1.1 there for what was verified about Photon's API before writing
 * this class.
 *
 * <p>One looping {@code ParticleEmitter} per retort position, started once and never re-triggered:
 * its {@link com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig} is a plain,
 * live-mutable object shared with the already-running Photon runtime (a shallow copy shares the
 * same config reference — verified in §1.1), so every frame simply overwrites
 * {@code emission.emissionRate} and {@code startColor} from the block entity's own published
 * {@link SolarRetortBlockEntity.Glow} — the exact reading {@link PlumbingIndicatorRenderers.Retort}
 * already draws the mesh glow from (rule 46: no second copy of the physics). No packets, no
 * server-side spawning: {@code onServerTick()}'s old continuous particle stream is gone.
 */
final class RetortPhotonSparks {
    private static final Logger LOG = LoggerFactory.getLogger(RetortPhotonSparks.class);
    /** Log only the first build and the first noticeably-nonzero rate per position — enough to
     *  tell "never got hot enough to emit" apart from "emitted and still nothing rendered" from
     *  the log alone, without spamming every frame. Temporary until S2 has been looked at once. */
    private static final Map<BlockPos, Boolean> LOGGED_FIRST_RATE = new HashMap<>();

    /** Blocks/tick sparks fall — small and gentle; this is an ember thrown a few centimetres, not
     *  a thrown rock. Ballistic, not drifting, per design/vfx-craft.md §2.1's vacuum-side rule. */
    private static final float GRAVITY = 0.25F;
    /** Short and disposable — an ember thrown a few centimetres, not a thing that lingers. Now
     *  fades out over this span (see {@link #build()}'s colorOverLifetime) rather than popping,
     *  closing the §1.1 named simplification once the exported test effect showed the real,
     *  0..1-float {@code GradientColor} point format to build one against. */
    private static final int LIFETIME_TICKS = 16;
    private static final float SIZE_BLOCKS = 0.06F;
    private static final float CONE_ANGLE_DEGREES = 18F;
    private static final float CONE_RADIUS_BLOCKS = 0.12F;
    private static final int MAX_PARTICLES = 64;

    /** One emitter per retort block position; rebuilt lazily once {@code isAlive()} says the old
     *  one died (chunk unload, block change — see §1.1). Never proactively evicted: a broken and
     *  never-revisited retort leaks one small POJO, not any particles or GL state. */
    private static final Map<BlockPos, ParticleEmitter> EMITTERS = new HashMap<>();

    /** Called once per frame per visible retort, from {@link PlumbingIndicatorRenderers.Retort}. */
    static void update(Level level, BlockPos pos, SolarRetortBlockEntity.@Nullable Glow glow) {
        ParticleEmitter emitter = EMITTERS.get(pos);
        if (emitter == null || !emitter.isAlive()) {
            emitter = build();
            new BlockEffectExecutor(wrap(emitter), level, pos.immutable()).start();
            EMITTERS.put(pos.immutable(), emitter);
            LOG.info("Photon spark emitter (re)started at {}", pos.immutable());
        }
        double rate = glow == null ? 0.0 : rateFromIntensity(glow.intensity01());
        emitter.config.emission.setEmissionRate(NumberFunction.constant((float) rate));
        if (glow != null) {
            emitter.config.setStartColor(NumberFunction.color(ARGB.color(1.0F, glow.rgb())));
        }
        if (rate > 0.05 && LOGGED_FIRST_RATE.putIfAbsent(pos.immutable(), Boolean.TRUE) == null) {
            LOG.info("Photon spark emitter at {} reached rate {}/tick, colour 0x{}", pos.immutable(),
                    rate, glow == null ? "?" : Integer.toHexString(glow.rgb()));
        }
    }

    /**
     * {@link RetortGlow#particlesPerTick} takes a temperature, but the published {@link
     * SolarRetortBlockEntity.Glow} only carries the colour {@code BlackBody} already resolved plus
     * {@code intensity01} — recomputing a temperature from intensity would run {@code BlackBody}'s
     * curve a second time, backwards (rule 46). {@code intensity01} is exactly the factor
     * {@code particlesPerTick} scales by, so this reapplies that same public rate constant to the
     * already-published value instead.
     */
    private static double rateFromIntensity(float intensity01) {
        return RetortGlow.MAX_PARTICLES_PER_SECOND * intensity01 / 20.0;
    }

    private static FX wrap(ParticleEmitter emitter) {
        FX fx = new FX();
        fx.getFxData().objects().add(emitter);
        return fx;
    }

    /**
     * The default place {@code /photon_editor} itself would offer to save a new project — verified
     * against a real {@code new.fxproj} the user saved from the editor, which landed directly here,
     * not under a per-mod subfolder the way exported {@code .fx} runtime files do.
     */
    static File defaultProjectFile(String name) {
        return new File(LDLib2.getAssetsDir(), name + FXProject.TYPE.getSuffix());
    }

    /**
     * Writes the same emitter {@link #build()} makes as an editable {@code .fxproj}, so a person
     * can open it in {@code /photon_editor}, see exactly what this class configures, and change it
     * visually instead of asking for a code change every time something should look different.
     *
     * <p>Goes through the real {@link FXProject}/{@code IProject#serialize} path rather than a
     * hand-built {@code CompoundTag} — {@code IProject.serialize} is public, plain, and exactly
     * what the editor's own save button calls, so there is nothing here to get subtly wrong about
     * the project wrapper's shape.
     *
     * <p>The write call was this method's own — and wrong the first time. {@code .fx} runtime
     * exports are gzip ({@code FXProject.showExportFxDialog} calls {@link NbtIo#writeCompressed}),
     * but {@code .fxproj} project files are plain, uncompressed NBT: {@code ProjectType}'s own
     * {@code saveProjectToFile}/{@code loadProjectFromFile} both call {@link NbtIo#write}/{@link
     * NbtIo#read} (not the {@code *Compressed} pair). Writing this one compressed produced a file
     * whose first two bytes ({@code 0x1f 0x8b}, the gzip magic) {@code DataInputStream.readUTF}
     * then misread as a huge string-length prefix — confirmed against the exact
     * {@code EOFException} the editor threw trying to open it, and against a real
     * {@code new.fxproj}'s raw bytes starting with the plain NBT compound tag (0x0A), not gzip.
     */
    static void exportProject(Level level, File file) throws Exception {
        FXProject project = new FXProject();
        project.getFx().getFxData().objects().add(build());
        try (var reporter = new ProblemReporter.ScopedCollector(Photon.LOGGER)) {
            var output = TagValueOutput.createWithContext(reporter, level.registryAccess());
            project.serialize(output);
            NbtIo.write(output.buildResult(), file.toPath());
        }
    }

    private static ParticleEmitter build() {
        ParticleEmitter emitter = new ParticleEmitter();
        var config = emitter.config;
        config.setLooping(true);
        config.setMaxParticles(MAX_PARTICLES);
        config.setStartLifetime(NumberFunction.constant(LIFETIME_TICKS));
        config.setStartSize(new NumberFunction3(SIZE_BLOCKS, SIZE_BLOCKS, SIZE_BLOCKS));
        config.setStartColor(NumberFunction.color(0xFFFFFFFF));
        config.emission.setEmissionRate(NumberFunction.constant(0F));

        if (config.shape.getShape() instanceof Cone cone) {
            cone.setAngle(CONE_ANGLE_DEGREES);
            cone.setRadius(CONE_RADIUS_BLOCKS);
            cone.setRadiusThickness(1F);
        }

        config.physics.setEnable(true);
        config.physics.setHasCollision(false);
        config.physics.setGravity(NumberFunction.constant(GRAVITY));

        // Alpha fade 1->0 over the particle's life; RGB left at the GradientColor default (white),
        // so this only fades the live startColor rather than re-deriving a colour (rule 46).
        // Point values are 0..1 floats — confirmed against a real exported .fxproj's GradientColor
        // (design/vfx-craft.md §1.1), not the 0-255 range the source alone suggested.
        GradientColor fade = new GradientColor();
        fade.getAP().clear();
        fade.getAP().add(new Vector2f(0F, 1F));
        fade.getAP().add(new Vector2f(1F, 0F));
        config.colorOverLifetime.setColor(new Gradient(fade));
        config.colorOverLifetime.setEnable(true);

        MaterialSetting material = config.renderer.getMaterials().get(0);
        material.setMaterial(new TextureMaterial(PlumbingModels.GLOW_TEXTURE));
        material.setDepthMask(false);
        BlendMode blend = material.getBlendMode();
        blend.setEnableBlend(true);
        blend.setSrcColorFactor(SourceFactor.ONE);
        blend.setDstColorFactor(DestFactor.ONE);
        blend.setSrcAlphaFactor(SourceFactor.ONE);
        blend.setDstAlphaFactor(DestFactor.ONE);
        blend.setBlendFunc(BlendMode.BlendFuc.ADD);

        return emitter;
    }

    private RetortPhotonSparks() {}
}
