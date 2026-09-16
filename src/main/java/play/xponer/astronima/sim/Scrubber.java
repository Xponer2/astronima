package play.xponer.astronima.sim;

/**
 * The lithium-hydroxide absorbent bed, as numbers rather than as a block.
 *
 * <p>LiOH binds carbon dioxide first-order in its concentration — the rate depends on how
 * much CO2 is presented to the bed, exactly as a real packed absorbent canister does — so a
 * scrubber does not simply "remove CO2": it settles a room at the level where absorption
 * matches what the occupants are producing. Put two people in a room sized for one and the
 * equilibrium sits higher, with no rule anywhere saying so.
 *
 * <p>Here rather than in the block entity (rule 1) because two things need to agree about
 * it and they live on opposite sides of the world: the machine that consumes the cartridge,
 * and the gauge that tells the player how much is left. A gauge reading "fine" on a
 * cartridge the machine considers spent is the failure {@code design/indicators.md} names,
 * and the only way to be sure is for both to read the same constant.
 */
public final class Scrubber {

    /**
     * CO2 bound per cartridge, mol.
     *
     * <p>About 2 kg of LiOH. The reaction is 2 LiOH + CO2 → Li2CO3 + H2O, so one kilogram
     * takes roughly 0.9 kg of CO2 — call it 20 mol per kilogram, 40 for the canister.
     */
    public static final double CARTRIDGE_CAPACITY_MOL = 40.0;

    /** Absorption rate constant, mol per second per kPa of ppCO2. */
    public static final double RATE_MOL_PER_S_PER_KPA = 0.05;

    /**
     * A cartridge with this much left or less may be swapped out.
     *
     * <p>Not zero: an absorbent bed goes useless well before it is chemically exhausted —
     * the last of it works so slowly that it cannot keep up with a person breathing — and
     * making the player wait for a mathematical zero would mean scrubbing badly for the
     * last stretch with no way to act on it.
     */
    public static final double SWAP_THRESHOLD_MOL = 2.0;

    /** The swap threshold as a fraction of a full cartridge, for gauges. */
    public static final double SWAP_THRESHOLD_FRACTION =
            SWAP_THRESHOLD_MOL / CARTRIDGE_CAPACITY_MOL;

    /**
     * Moles of CO2 a bed with {@code capacityLeftMol} absorbs in {@code seconds} from air at
     * {@code ppCO2KPa} — before the bed's own remaining capacity is applied by the caller.
     */
    public static double absorbedMol(double ppCO2KPa, double seconds) {
        return RATE_MOL_PER_S_PER_KPA * Math.max(0, ppCO2KPa) * seconds;
    }

    /** Absorbent remaining, 0..1 — what the gauge draws. */
    public static double chargeFraction(double capacityLeftMol) {
        return Math.clamp(capacityLeftMol / CARTRIDGE_CAPACITY_MOL, 0.0, 1.0);
    }

    /** True when the cartridge is spent enough that swapping it is the right move. */
    public static boolean needsSwap(double capacityLeftMol) {
        return capacityLeftMol <= SWAP_THRESHOLD_MOL;
    }

    private Scrubber() {}
}
