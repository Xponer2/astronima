package play.xponer.astronima.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.util.RandomSource;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.sim.ore.ResourceFloor;
import play.xponer.astronima.sim.world.AsteroidBody;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Generates the asteroid as a bounded oblate body — not an infinite plane — with a logical
 * centre, sparse cavity pockets (denser toward the interior), regolith blanketing the surface,
 * and resource veins placed by shell depth as described in DESIGN.md §3, now measured from the
 * body's own centre rather than from an arbitrary Y.
 *
 * <p>The shape itself lives in {@link AsteroidBody} (Minecraft-free, rule 1); this class is the
 * noise fields, the block palette and the fill loop wrapped around that shape. See
 * {@code design/asteroid-body.md} and its leaf plan {@code design/asteroid-body-b1.md} for the
 * shape's derivation and the two bugs a naive port of the old absolute-Y gates would have shipped.
 */
public class AsteroidChunkGenerator extends ChunkGenerator {
    public static final MapCodec<AsteroidChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
            ).apply(instance, AsteroidChunkGenerator::new));

    private static final int SURFACE_RELIEF = 24;
    private static final double SURFACE_SCALE = 1.0 / 64.0;
    private static final double CAVE_SCALE = 1.0 / 24.0;
    private static final double ICE_SCALE = 1.0 / 18.0;
    /** Coarser than the others: metal seams should be pockets, not speckle. */
    private static final double ORE_SCALE = 1.0 / 34.0;

    private static final Identifier SURFACE_NOISE_ID = Identifier.fromNamespaceAndPath(Astronima.MODID, "surface");
    private static final Identifier CAVE_NOISE_ID = Identifier.fromNamespaceAndPath(Astronima.MODID, "caves");
    private static final Identifier ICE_NOISE_ID = Identifier.fromNamespaceAndPath(Astronima.MODID, "ice");
    private static final Identifier ORE_NOISE_ID = Identifier.fromNamespaceAndPath(Astronima.MODID, "ores");

    /**
     * Every ore's per-block chance, defended against ever regressing below a real total supply
     * for the whole (now finite) body — see {@code design/asteroid-body-resources.md}. Checked
     * against the real geometry at today's radius and thresholds, none of these actually raise
     * the probability past its original, hand-picked value: {@code max()} only engages the day
     * someone shrinks the body or narrows a band without re-deriving these by hand.
     *
     * <p>The floors themselves are generous on purpose, per the same "the asteroid is basically
     * made of metal" reasoning that set them: real early-game demand for any one ore is in the
     * tens to low hundreds across a full playthrough ({@code ExpeditionTest}'s own accounting for
     * one recipe chain needs 7 hematite) — these floors are one to two orders of magnitude above
     * that, a genuine safety margin rather than a number picked to look large.
     */
    private static final double CHLORATE_BASE_CHANCE = 0.010;
    private static final double HEMATITE_BASE_CHANCE = 0.012;
    private static final double ILMENITE_BASE_CHANCE = 0.009;
    private static final double METAL_RICH_BASE_CHANCE = 0.22;
    /** Between chlorate's and hematite's own — a common feedstock now that it roots two real
     * products (sodium and chlorine), not a rare find. design/halogens.md §2. */
    private static final double HALITE_BASE_CHANCE = 0.011;
    /** Matches ilmenite's own rarity — a real hydrothermal vein mineral, not a bulk one.
     * design/halogens.md §16. */
    private static final double FLUORITE_BASE_CHANCE = 0.009;

    private static final double CHLORATE_MINIMUM_TOTAL = 50_000;
    private static final double HEMATITE_MINIMUM_TOTAL = 50_000;
    private static final double ILMENITE_MINIMUM_TOTAL = 20_000;
    private static final double HALITE_MINIMUM_TOTAL = 40_000;
    private static final double FLUORITE_MINIMUM_TOTAL = 20_000;
    /**
     * Lower than the others because metal-rich is meant to feel rare - "a prospecting result,
     * not a lottery ticket," the class's own ore-noise comment already says. Still comfortably
     * above real demand.
     */
    private static final double METAL_RICH_MINIMUM_TOTAL = 2_000;

    /**
     * The ore-noise field's real, measured exceedance fraction above its {@code 0.62} pocket
     * threshold - sampled directly from {@link ImprovedNoise} at this generator's own
     * {@link #ORE_SCALE} across four seeds (0.39%-0.68%), since there is no closed-form formula
     * for it the way there is for the geometric bands. This is the <em>worst</em> of those four,
     * used deliberately conservatively: the true fraction is per-world and this floor cannot see
     * it at generation time, so the safety margin assumes the least favourable seed observed
     * rather than the average one.
     */
    private static final double ORE_NOISE_CONSERVATIVE_PASS_FRACTION = 0.0039;

    private static final double CHLORATE_CHANCE = ResourceFloor.probabilityForFloor(
            AsteroidBody.bandVolume(0.72, 0.95), CHLORATE_MINIMUM_TOTAL, CHLORATE_BASE_CHANCE);
    private static final double HEMATITE_CHANCE = ResourceFloor.probabilityForFloor(
            AsteroidBody.bandVolume(0.0, 0.80), HEMATITE_MINIMUM_TOTAL, HEMATITE_BASE_CHANCE);
    private static final double ILMENITE_CHANCE = ResourceFloor.probabilityForFloor(
            AsteroidBody.bandVolume(0.0, 0.55), ILMENITE_MINIMUM_TOTAL, ILMENITE_BASE_CHANCE);
    private static final double HALITE_CHANCE = ResourceFloor.probabilityForFloor(
            AsteroidBody.bandVolume(0.55, 0.90), HALITE_MINIMUM_TOTAL, HALITE_BASE_CHANCE);
    private static final double FLUORITE_CHANCE = ResourceFloor.probabilityForFloor(
            AsteroidBody.bandVolume(0.0, 0.60), FLUORITE_MINIMUM_TOTAL, FLUORITE_BASE_CHANCE);
    private static final double METAL_RICH_CHANCE = ResourceFloor.probabilityForFloor(
            AsteroidBody.bandVolume(0.0, 0.62) * ORE_NOISE_CONSERVATIVE_PASS_FRACTION,
            METAL_RICH_MINIMUM_TOTAL, METAL_RICH_BASE_CHANCE);

    private volatile Noises noises;

    private record Noises(ImprovedNoise surface, ImprovedNoise caves, ImprovedNoise ice,
                          ImprovedNoise ore) {}

    /** A column's terrain extent: solid from {@code floor} to {@code surface}, inclusive. */
    private record BodyShape(int floor, int surface) {
        /** True once past the rim — an empty column has nothing to fill. */
        boolean isEmpty() {
            return surface < floor;
        }
    }

    public AsteroidChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    private Noises noises(RandomState randomState) {
        Noises local = noises;
        if (local == null) {
            synchronized (this) {
                local = noises;
                if (local == null) {
                    local = new Noises(
                            new ImprovedNoise(randomState.getOrCreateRandomFactory(SURFACE_NOISE_ID).at(0, 0, 0)),
                            new ImprovedNoise(randomState.getOrCreateRandomFactory(CAVE_NOISE_ID).at(0, 0, 0)),
                            new ImprovedNoise(randomState.getOrCreateRandomFactory(ICE_NOISE_ID).at(0, 0, 0)),
                            new ImprovedNoise(randomState.getOrCreateRandomFactory(ORE_NOISE_ID).at(0, 0, 0)));
                    noises = local;
                }
            }
        }
        return local;
    }

    private BodyShape shapeAt(Noises noises, int x, int z) {
        double r = Math.hypot(x, z);
        if (!AsteroidBody.isInside(r)) {
            return new BodyShape(AsteroidBody.CENTRE_Y, AsteroidBody.CENTRE_Y - 1); // isEmpty()
        }
        double relief = noises.surface().noise(x * SURFACE_SCALE, 0.0, z * SURFACE_SCALE);
        int floor = AsteroidBody.floorHeight(r);
        int surface = AsteroidBody.surfaceHeight(r, relief, SURFACE_RELIEF);
        return new BodyShape(floor, surface);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        Noises noises = noises(randomState);
        BlockState rock = ModBlocks.ASTEROID_ROCK.get().defaultBlockState();
        BlockState regolith = ModBlocks.REGOLITH.get().defaultBlockState();
        BlockState ice = ModBlocks.WATER_ICE.get().defaultBlockState();
        BlockState chlorate = ModBlocks.CHLORATE_ORE.get().defaultBlockState();
        BlockState hematite = ModBlocks.HEMATITE_ORE.get().defaultBlockState();
        BlockState metalRich = ModBlocks.METAL_RICH_ORE.get().defaultBlockState();
        BlockState ilmenite = ModBlocks.ILMENITE_ORE.get().defaultBlockState();
        BlockState halite = ModBlocks.HALITE_ORE.get().defaultBlockState();
        BlockState fluorite = ModBlocks.FLUORITE_ORE.get().defaultBlockState();

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int startX = chunk.getPos().getMinBlockX();
        int startZ = chunk.getPos().getMinBlockZ();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = startX + dx;
                int z = startZ + dz;
                double r = Math.hypot(x, z);
                BodyShape shape = shapeAt(noises, x, z);
                if (shape.isEmpty()) {
                    continue; // past the rim: no terrain at all
                }

                for (int y = shape.floor(); y <= shape.surface(); y++) {
                    pos.set(x, y, z);
                    double t = AsteroidBody.shellCoordinate(r, y);

                    // Cavities: sparse pockets, denser toward the interior. Gated on the shell
                    // coordinate for the same reason the ore bands are below — an absolute-Y
                    // depth check has the same rim-inversion bug caves would otherwise inherit.
                    double caveThreshold = t < 0.5 ? 0.52 : 0.60;
                    if (y < shape.surface() - 4
                            && noises.caves().noise(x * CAVE_SCALE, y * CAVE_SCALE, z * CAVE_SCALE) > caveThreshold) {
                        // Deep cavities occasionally trap volatiles: a marker cell
                        // charges the room with gas on first scan (GasPockets).
                        if (t < 0.4 && RandomSource.create(BlockPos.asLong(x, y, z) * 0x632BE59BD9B4E019L)
                                .nextFloat() < 0.004f) {
                            pos.set(x, y, z);
                            chunk.setBlockState(pos, ModBlocks.GAS_POCKET_CORE.get().defaultBlockState());
                        }
                        continue; // leave as air (a vacuum pocket until pressurized)
                    }

                    chunk.setBlockState(pos, selectBlock(noises, x, y, z, t,
                            rock, regolith, ice, chlorate, hematite, metalRich, ilmenite, halite,
                            fluorite));
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    private BlockState selectBlock(Noises noises, int x, int y, int z, double t,
                                   BlockState rock, BlockState regolith, BlockState ice,
                                   BlockState chlorate, BlockState hematite,
                                   BlockState metalRich, BlockState ilmenite,
                                   BlockState halite, BlockState fluorite) {
        // Impact-gardened regolith blankets the weathered skin.
        if (t > 0.97) {
            return regolith;
        }
        // Ice lenses: coherent noise blobs in the middle depths, where temperatures
        // stayed low enough to keep water frozen but rock shielded it from sublimating.
        if (t > 0.45 && t < 0.85
                && noises.ice().noise(x * ICE_SCALE, y * ICE_SCALE, z * ICE_SCALE) > 0.58) {
            return ice;
        }
        // Scattered single-block ore: cheap positional hash keeps generation stateless.
        RandomSource random = RandomSource.create(BlockPos.asLong(x, y, z) * 0x9E3779B97F4A7C15L);
        if (t > 0.72 && t < 0.95 && random.nextFloat() < CHLORATE_CHANCE) {
            return chlorate;
        }
        if (t < 0.80 && random.nextFloat() < HEMATITE_CHANCE) {
            return hematite;
        }
        // Ilmenite: a deeper find than hematite, the fluidized-bed reactor's feedstock. Scattered
        // like the other single-block ores, and gated below its own depth so it reads as a
        // reward for digging past the iron rather than as more of the same.
        if (t < 0.55 && random.nextFloat() < ILMENITE_CHANCE) {
            return ilmenite;
        }
        // Fluorite: a real hydrothermal vein mineral, geologically a neighbour of the deep
        // magmatic-process minerals rather than the ice/salt band halite shares — the HF
        // digester's own feedstock (design/halogens.md §16).
        if (t < 0.60 && random.nextFloat() < FLUORITE_CHANCE) {
            return fluorite;
        }
        // Rock salt: overlapping the ice band on purpose — real evaporite salt forms from
        // evaporating water, so it reads as a neighbour of the ice lenses without a new
        // mechanical coupling to them (design/halogens.md §2).
        if (t > 0.55 && t < 0.90 && random.nextFloat() < HALITE_CHANCE) {
            return halite;
        }
        // Native metal seams: rare, deep, and clustered rather than scattered, so
        // finding one is a prospecting result rather than a lottery ticket. The
        // ore-noise field gates them into pockets; the hash then thins those out.
        if (t < 0.62 && noises.ore().noise(x * ORE_SCALE, y * ORE_SCALE, z * ORE_SCALE) > 0.62
                && random.nextFloat() < METAL_RICH_CHANCE) {
            return metalRich;
        }
        return rock;
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager,
                             RandomState randomState, ChunkAccess protoChunk) {
        // Surface material is placed during fillFromNoise; nothing to do.
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState,
                             BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk) {
        // Cavities are part of the base noise pass; vanilla carvers don't apply.
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
        // Nothing lives here. The asteroid is the antagonist.
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return Integer.MIN_VALUE; // no fluid ocean of any kind
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor,
                             RandomState randomState) {
        return shapeAt(noises(randomState), x, z).surface() + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
        BodyShape shape = shapeAt(noises(randomState), x, z);
        BlockState rock = ModBlocks.ASTEROID_ROCK.get().defaultBlockState();
        BlockState[] column = new BlockState[heightAccessor.getHeight()];
        for (int i = 0; i < column.length; i++) {
            int y = heightAccessor.getMinY() + i;
            column[i] = (!shape.isEmpty() && y >= shape.floor() && y <= shape.surface())
                    ? rock : Blocks.AIR.defaultBlockState();
        }
        return new NoiseColumn(heightAccessor.getMinY(), column);
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos) {
        result.add("Astronima asteroid generator");
    }
}
