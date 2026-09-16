package play.xponer.astronima.client.render;

import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.fx.EntityEffectExecutor;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.client.model.PlumbingModels;
import play.xponer.astronima.network.AstraFieldReadingPayload;
import play.xponer.astronima.sim.magic.WavelengthColor;

import java.util.HashMap;
import java.util.Map;

/**
 * The astra field's first visible consequence (design/astra-field-live.md §2): a faint personal
 * glow near the player, brightness tied to the real baseline density {@link
 * AstraFieldReadingPayload} publishes — never re-derived on the client (rule 26).
 *
 * <p>Anchored to the player via {@link EntityEffectExecutor}, which already follows an entity's
 * eye position every frame on its own — unlike {@code RetortPhotonGlow}'s block anchor, this needs
 * no per-frame repositioning. The "one relay particle standing in for one continuously-existing
 * object" recipe is otherwise identical to that class's own, proven shape: a burst of exactly one
 * particle, {@code duration == startLifetime} so each relay's death lines up with the next one's
 * birth.
 *
 * <h2>Simplified, named per rule 8 — see design/astra-field-live.md §2.3</h2>
 * Colour is one fixed reference hue standing in for "astra's own signal before the crust does
 * anything to it," via {@link WavelengthColor} — the same real, already-guarded port every other
 * coloured thing in this tier uses. The real per-position colour (what mineral column is under the
 * player) is crust sounding's payoff, not built yet; only brightness (density) is real here.
 *
 * <p><strong>Needs a runClient check before this is trusted (rule 4/15) — nothing in this
 * codebase's automated gates draws a frame.</strong>
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class AstraGlowFx {

    /** design/astra-field-live.md §2.3: a fixed placeholder hue, distinct from every real
     *  spectral line this tier already colours things by (H-α 656.3, [O III] 500.7, Na D
     *  589.0/589.6, Ca II 393.4/396.8) so nothing reads as a real, identified line by accident. */
    private static final double REFERENCE_WAVELENGTH_NM = 460.0;

    private static final int RELAY_TICKS = 20;
    private static final float MIN_DIAMETER_BLOCKS = 0.0F;
    private static final float MAX_DIAMETER_BLOCKS = 0.9F;

    private static final Map<Player, ParticleEmitter> EMITTERS = new HashMap<>();

    private static volatile float latestDensity;

    /** Called from {@code ModNetworking}'s payload handler — thread-safety matches {@code
     *  CoherenceHud.accept}'s own shape exactly: a bare volatile write, with every touch of a
     *  Minecraft/Photon object deferred to the main-thread tick below. */
    public static void accept(AstraFieldReadingPayload payload) {
        latestDensity = payload.density();
    }

    @SubscribeEvent
    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        update(player, latestDensity);
    }

    private static void update(Player player, float density) {
        ParticleEmitter emitter = EMITTERS.get(player);
        if (emitter == null || !emitter.isAlive()) {
            emitter = build();
            var executor = new EntityEffectExecutor(wrap(emitter), player.level(), player,
                    EntityEffectExecutor.AutoRotate.NONE);
            // Near the feet, not the eyes — "the ground here is different" (design/
            // astra-field-live.md §2.1), not a floating head-lamp.
            executor.setOffset(new org.joml.Vector3f(0F, -1.4F, 0F));
            executor.start();
            EMITTERS.put(player, emitter);
        }

        float clampedForSize = Math.min(1.0F, Math.max(0.0F, density));
        float diameter = MIN_DIAMETER_BLOCKS + (MAX_DIAMETER_BLOCKS - MIN_DIAMETER_BLOCKS) * clampedForSize;
        emitter.config.setStartSize(new NumberFunction3(diameter, diameter, diameter));
        emitter.config.setStartColor(NumberFunction.color(ARGB.color(1.0F, WavelengthColor.rgb(REFERENCE_WAVELENGTH_NM))));
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

    private AstraGlowFx() {}
}
