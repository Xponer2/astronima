package play.xponer.astronima.registry;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.worldgen.AsteroidChunkGenerator;

/** The asteroid dimension and its chunk generator codec. */
public final class ModDimensions {
    public static final ResourceKey<Level> ASTEROID_LEVEL =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(Astronima.MODID, "asteroid"));

    /** The VR Simulation Pod's own void dimension (design/vr-simulation-pod.md). A real vanilla
     *  flat/void generator, not a custom one — nothing here needs the asteroid's own shape. */
    public static final ResourceKey<Level> VR_LEVEL =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(Astronima.MODID, "vr_pod"));

    /** The void has no floor (design/vr-simulation-pod.md §3) — a player who lands here arrives
     *  flying, and a player who dies here (VrPodSafety) is put back here, not back on a floor. */
    public static final double VR_SPAWN_X = 0.5;
    public static final double VR_SPAWN_Y = 100.0;
    public static final double VR_SPAWN_Z = 0.5;

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, Astronima.MODID);

    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<AsteroidChunkGenerator>>
            ASTEROID_GENERATOR = CHUNK_GENERATORS.register("asteroid", () -> AsteroidChunkGenerator.CODEC);

    private ModDimensions() {}
}
