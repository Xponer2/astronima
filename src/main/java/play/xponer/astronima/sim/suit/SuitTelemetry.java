package play.xponer.astronima.sim.suit;

/**
 * Everything the suit knows about itself, packed into one integer.
 *
 * <p>Packing rather than a bespoke packet is deliberate: this rides on a synced data
 * attachment, so it reaches the client automatically and cannot drift out of step
 * with the server the way a hand-scheduled packet can.
 *
 * <p>The distinction that matters here is <em>absent</em> versus <em>zero</em>. A suit
 * whose status display is dark reports {@link #NOT_FITTED}, and the panel draws
 * nothing at all — an unrepaired suit does not lie to you, it tells you nothing
 * (design/suit.md §2.3). A fitted-but-empty tank reports 0, which is a very different
 * message and has to look different.
 */
public final class SuitTelemetry {
    /** Sentinel for a slot with nothing in it, distinct from an empty one. */
    public static final int NOT_FITTED = 127;

    private static final int LEVEL_MASK = 0x7F;
    private static final int TANK_SHIFT = 0;
    private static final int CARTRIDGE_SHIFT = 7;
    private static final int SEALED_BIT = 1 << 14;
    private static final int DISPLAY_BIT = 1 << 15;
    private static final int WORN_BIT = 1 << 16;

    /** Nothing worn: the panel draws nothing. */
    public static final int ABSENT = 0;

    /**
     * @param tankPercent      0-100, or {@link #NOT_FITTED}
     * @param cartridgePercent 0-100, or {@link #NOT_FITTED}
     * @param sealed           the visor is closed and the suit is on its own supply
     * @param displayWorks     the status display subsystem is repaired
     */
    public static int pack(int tankPercent, int cartridgePercent, boolean sealed,
                           boolean displayWorks) {
        return WORN_BIT
                | (clampLevel(tankPercent) << TANK_SHIFT)
                | (clampLevel(cartridgePercent) << CARTRIDGE_SHIFT)
                | (sealed ? SEALED_BIT : 0)
                | (displayWorks ? DISPLAY_BIT : 0);
    }

    /** Converts a 0-1 fraction, or a negative for "nothing fitted", to a packed level. */
    public static int levelOf(float fraction) {
        return fraction < 0 ? NOT_FITTED : Math.clamp(Math.round(fraction * 100), 0, 100);
    }

    public static boolean isWorn(int packed) {
        return (packed & WORN_BIT) != 0;
    }

    /** True only when the suit can actually report on itself. */
    public static boolean displayWorks(int packed) {
        return (packed & DISPLAY_BIT) != 0;
    }

    public static boolean isSealed(int packed) {
        return (packed & SEALED_BIT) != 0;
    }

    public static int tankPercent(int packed) {
        return (packed >> TANK_SHIFT) & LEVEL_MASK;
    }

    public static int cartridgePercent(int packed) {
        return (packed >> CARTRIDGE_SHIFT) & LEVEL_MASK;
    }

    public static boolean isFitted(int level) {
        return level != NOT_FITTED;
    }

    private static int clampLevel(int level) {
        return level == NOT_FITTED ? NOT_FITTED : Math.clamp(level, 0, 100);
    }

    private SuitTelemetry() {}
}
