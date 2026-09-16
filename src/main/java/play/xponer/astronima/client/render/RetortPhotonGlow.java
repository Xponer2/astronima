package play.xponer.astronima.client.render;

import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.fx.BlockEffectExecutor;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.MaterialSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.BlendMode;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.ObjModelSource;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.MeshData;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleRendererSetting;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.entity.SolarRetortBlockEntity;
import play.xponer.astronima.client.model.PlumbingModels;
import play.xponer.astronima.sim.optics.GlowPulse;

import java.util.HashMap;
import java.util.Map;

/**
 * The vessel's own light (design/vfx-craft.md §3, S1 — rebuilt) — a genuine 3D glowing sphere on
 * Photon's {@code Model} render mode, replacing the old vanilla billboard mesh
 * ({@code PlumbingIndicatorRenderers.Retort.drawGlow}, removed) that a person playing reported as
 * reading like "a flat square always turned to face the camera."
 *
 * <p>A sphere has no wrong angle to be seen from and needs no billboard trick at all — the actual
 * fix for that complaint, not a better billboard. Built on the exact "single persistent mesh
 * particle" recipe a real, professionally-made effect ({@code blackhole.fxpack}, decoded and
 * studied before writing this) uses for its own glowing core: {@code shape=dot}, a burst of
 * exactly one particle, {@code duration == startLifetime} so each relay particle's death lines up
 * with the next one's birth — the illusion of one continuously-existing sphere is a relay of
 * one-particle-long lives, not one particle living forever. {@code renderMode=Model} with
 * Photon's built-in {@code sphere.obj} (verified against the real asset in Photon's own jar,
 * the same primitive {@code MeshResource.buildBuiltin} wires up as "sphere") stands in for the
 * old flat quad; the same additive {@code GLOW_TEXTURE} paints it, so the vessel's light still
 * reads as the same phenomenon it always was, just honestly round now.
 */
final class RetortPhotonGlow {
    /** Real seconds a relay particle lives; short enough that a temperature change reads within
     *  about a second, long enough not to look like a strobe. */
    private static final int RELAY_TICKS = 20;
    private static final float MIN_DIAMETER_BLOCKS = 0.5F;
    private static final float MAX_DIAMETER_BLOCKS = 1.1F;

    private static final Map<BlockPos, ParticleEmitter> EMITTERS = new HashMap<>();

    /** Called once per frame per visible retort, from {@link PlumbingIndicatorRenderers.Retort}. */
    static void update(Level level, BlockPos pos, SolarRetortBlockEntity.@Nullable Glow glow) {
        ParticleEmitter emitter = EMITTERS.get(pos);
        if (emitter == null || !emitter.isAlive()) {
            emitter = build();
            var executor = new BlockEffectExecutor(wrap(emitter), level, pos.immutable());
            // Same spawn point the mesh used to translate to, and the sparks already spawn from —
            // the glow and the embers thrown off it read as one phenomenon, not two.
            executor.setOffset(0F, 0.1F, 0F);
            executor.start();
            EMITTERS.put(pos.immutable(), emitter);
        }
        if (glow == null) {
            emitter.config.setStartSize(new NumberFunction3(0F, 0F, 0F));
            return;
        }
        double ageSeconds = level.getGameTime() / 20.0;
        float base = MIN_DIAMETER_BLOCKS + (MAX_DIAMETER_BLOCKS - MIN_DIAMETER_BLOCKS) * glow.intensity01();
        // The same breathing pulse the old billboard mesh used (design/vfx-craft.md §S1) — a
        // perfectly static glow reads as a lit texture, not a fire, per GlowPulse's own doc. Sized
        // rather than a second brightness layer: the sphere itself swells and eases, which a real
        // 3D object can do and a flat billboard's brightness alone could not.
        float diameter = (float) (base * GlowPulse.multiplier(ageSeconds, glow.intensity01()));
        emitter.config.setStartSize(new NumberFunction3(diameter, diameter, diameter));
        emitter.config.setStartColor(NumberFunction.color(ARGB.color(1.0F, glow.rgb())));
    }

    private static FX wrap(ParticleEmitter emitter) {
        FX fx = new FX();
        fx.getFxData().objects().add(emitter);
        return fx;
    }

    private static ParticleEmitter build() {
        ParticleEmitter emitter = new ParticleEmitter();
        var config = emitter.config;
        config.setLooping(true);
        config.setDuration(RELAY_TICKS);
        config.setMaxParticles(2);
        config.setStartLifetime(NumberFunction.constant(RELAY_TICKS));
        config.setStartSize(new NumberFunction3(0F, 0F, 0F));
        config.setStartColor(NumberFunction.color(0xFFFFFFFF));
        config.setStartSpeed(NumberFunction.constant(0F));

        // One particle, reborn every RELAY_TICKS — see the class doc for why this reads as one
        // continuously-present sphere rather than a blinking one.
        config.emission.setEmissionRate(NumberFunction.constant(0F));
        var burst = new EmissionSetting.Burst();
        burst.time = 0;
        burst.setCount(NumberFunction.constant(1));
        burst.cycles = 1;
        burst.interval = 1;
        burst.probability = 1F;
        config.emission.setBursts(java.util.List.of(burst));

        config.renderer.setRenderMode(ParticleRendererSetting.Mode.Model);
        config.renderer.setModel(new MeshData(new ObjModelSource(Photon.id("models/sphere.obj"))));

        config.physics.setEnable(false);

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

    private RetortPhotonGlow() {}
}
