package play.xponer.astronima.sim.suit;

/**
 * How long the suit's two consumables last, and the rhythm that creates.
 *
 * <p>These are the numbers <em>design/suit.md §3.4</em> commits to, kept here rather
 * than inside the Minecraft classes so the commitment can be tested. The section is
 * worth restating because the choice is deliberate and slightly dishonest about
 * physics: tank duration runs on the compressed gameplay clock while helmet CO2 runs
 * on the real one, so no mole-accurate cartridge figure is correct in both. The
 * cartridge is therefore sized in <em>sealed-suit minutes</em>.
 *
 * <p>The intended rhythm: you swap tanks routinely, and the cartridge every second
 * tank — often enough to be a chore you plan around, rarely enough that it is the
 * thing you forget about until it bites.
 */
public final class SuitEndurance {
    /** Seconds of sealed operation from a full tank — about 11 minutes. */
    public static final double TANK_SECONDS = 660.0;

    /** Seconds of scrubbing from a fresh cartridge — about 20 minutes. */
    public static final double CARTRIDGE_SECONDS = 1200.0;

    /** How many tanks a single cartridge outlives. */
    public static double tanksPerCartridge() {
        return CARTRIDGE_SECONDS / TANK_SECONDS;
    }

    /**
     * Wear per second for a consumable whose durability spans its whole life.
     *
     * @param maxDamage      the item's durability
     * @param lifetimeSeconds seconds of use it should represent
     */
    public static double wearPerSecond(int maxDamage, double lifetimeSeconds) {
        return maxDamage / lifetimeSeconds;
    }

    private SuitEndurance() {}
}
