package play.xponer.astronima.registry;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import play.xponer.astronima.Astronima;

/**
 * Custom particle types — design/presentation.md §3.1's infrastructure, previously nonexistent
 * (every particle in the mod was a vanilla type, drawing whatever vanilla's own sprite is).
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, Astronima.MODID);

    /**
     * A hot surface glowing at its own black-body colour — design/presentation.md §3.2's "wire
     * glow" roster entry, generalised: the retort's vessel is the mod's first emitter, not a
     * wire (see {@code sim.ore.RetortGlow}'s own note on why a wire cannot honestly glow here).
     *
     * <p>Carries the colour rather than a bare trigger, because the server already knows the
     * real temperature and {@link play.xponer.astronima.sim.optics.BlackBody} that computes it
     * from is Minecraft-free — asking the client to re-derive a colour from a number it was
     * never sent would be a second, driftable copy of the same physics (rule 46's concern,
     * applied to a particle instead of a texture).
     */
    public static final DeferredHolder<ParticleType<?>, ParticleType<IncandescenceOptions>> INCANDESCENCE =
            PARTICLE_TYPES.register("incandescence", () -> new ParticleType<>(false) {
                @Override
                public MapCodec<IncandescenceOptions> codec() {
                    return IncandescenceOptions.CODEC;
                }

                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, IncandescenceOptions> streamCodec() {
                    return IncandescenceOptions.STREAM_CODEC;
                }
            });

    /**
     * One glowing mote's colour, how bright it reads (0..1), and which role it is playing.
     *
     * <p>{@code accent} distinguishes the rare, larger flare a batch finishing on fires from
     * the ordinary ember drifting off the vessel the rest of the time — one registered type and
     * one provider for both (design/vfx-craft.md §3: a role is a spawn-time choice, not a
     * second particle type), since both are the same physics at the same real colour, only
     * different in how much of it a single mote is showing at once.
     */
    public record IncandescenceOptions(int color, float intensity01, boolean accent) implements ParticleOptions {
        public static final MapCodec<IncandescenceOptions> CODEC = RecordCodecBuilder.mapCodec(
                i -> i.group(
                                ExtraCodecs.RGB_COLOR_CODEC.fieldOf("color").forGetter(IncandescenceOptions::color),
                                com.mojang.serialization.Codec.floatRange(0.0F, 1.0F)
                                        .fieldOf("intensity").forGetter(IncandescenceOptions::intensity01),
                                com.mojang.serialization.Codec.BOOL.fieldOf("accent")
                                        .forGetter(IncandescenceOptions::accent))
                        .apply(i, IncandescenceOptions::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, IncandescenceOptions> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.INT, IncandescenceOptions::color,
                        ByteBufCodecs.FLOAT, IncandescenceOptions::intensity01,
                        ByteBufCodecs.BOOL, IncandescenceOptions::accent,
                        IncandescenceOptions::new);

        @Override
        public ParticleType<IncandescenceOptions> getType() {
            return INCANDESCENCE.get();
        }
    }

    /**
     * A grain of regolith thrown up by an impact (design/sky.md §3's impact-flash row) — a dark
     * rock chip on a real ballistic arc, not a puff of dust.
     *
     * <p>Carries no data, unlike {@link #INCANDESCENCE}: that one had to be told a colour because
     * only the server knew the real temperature. Here the server knows nothing the client does not
     * — {@link play.xponer.astronima.sim.sky.RegolithBallistics} is Minecraft-free and reads
     * {@link play.xponer.astronima.sim.gravity.Microgravity}'s own constants, so both sides
     * compute the identical arc from the identical model with nothing to send.
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> REGOLITH =
            PARTICLE_TYPES.register("regolith", () -> new SimpleParticleType(false));

    private ModParticles() {}
}
