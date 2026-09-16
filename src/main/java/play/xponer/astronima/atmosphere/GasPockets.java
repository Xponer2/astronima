package play.xponer.astronima.atmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.GasMixture;
import play.xponer.astronima.sim.RoomState;

/**
 * Contents of trapped-volatile pockets, rolled deterministically from world seed and
 * marker position. The species distribution follows the depth zoning of DESIGN.md §3:
 * oxidized/irritant gases near the old thermally processed surface, reduced gases
 * (methane, CO, sulfide) in the primordial depths.
 *
 * <p>Pockets are pressurized (60–180 kPa at rock temperature) — breaching one pushes
 * its contents out at the miner rather than waiting to diffuse.
 */
final class GasPockets {
    /** Reference gas volume per marker, m³ — a core charges "one small cave" worth. */
    private static final double REFERENCE_VOLUME_M3 = 30.0;

    static void inject(long worldSeed, BlockPos pos, RoomState room) {
        RandomSource random = RandomSource.create(worldSeed ^ (pos.asLong() * 0x9E3779B97F4A7C15L));
        Gas primary = pickPrimary(pos.getY(), random);
        double pressureKPa = 60.0 + random.nextDouble() * 120.0;
        double moles = GasMixture.totalMolesFor(pressureKPa, REFERENCE_VOLUME_M3, Atmosphere.AMBIENT_ROCK_TEMP_K);
        room.addGasAt(primary, moles * 0.9, Atmosphere.AMBIENT_ROCK_TEMP_K);
        room.addGasAt(Gas.CARBON_DIOXIDE, moles * 0.1, Atmosphere.AMBIENT_ROCK_TEMP_K);
    }

    private static Gas pickPrimary(int y, RandomSource random) {
        float roll = random.nextFloat();
        if (y >= 0) {
            // Shallow: outgassed/oxidized species.
            if (roll < 0.45f) return Gas.CARBON_DIOXIDE;
            if (roll < 0.65f) return Gas.NITROGEN;
            if (roll < 0.80f) return Gas.METHANE;
            if (roll < 0.90f) return Gas.AMMONIA;
            return Gas.SULFUR_DIOXIDE;
        }
        // Deep: primordial reduced volatiles.
        if (roll < 0.30f) return Gas.METHANE;
        if (roll < 0.50f) return Gas.CARBON_MONOXIDE;
        if (roll < 0.65f) return Gas.HYDROGEN_SULFIDE;
        if (roll < 0.80f) return Gas.CARBON_DIOXIDE;
        if (roll < 0.92f) return Gas.HYDROGEN;
        return Gas.NITROGEN;
    }

    private GasPockets() {}
}
