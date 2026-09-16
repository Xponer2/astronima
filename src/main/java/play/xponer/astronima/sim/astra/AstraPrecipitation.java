package play.xponer.astronima.sim.astra;

/**
 * When a held charge of astra is concentrated enough to precipitate, and how much matter comes
 * out of it — design/astra-precipitation.md §1.1/§1.2: the tier's first material, manufactured
 * from a field rather than mined (design/astra-core.md §6). Minecraft-free (rule 1): takes a
 * plain held amount (already leak-adjusted by {@link AstraVessel#afterLeak}), returns whole items
 * and the fractional remainder that stays held.
 *
 * <h2>The numbers, first-pass per rule 41</h2>
 * {@link #THRESHOLD} and the 1:1 conversion below are chosen to state a real player experience,
 * not measured: reaching threshold should take several real sweeps ({@code AstraField.swept} on a
 * working-depth site yields well under 1.0 per sweep), so precipitating for the first time is a
 * real "I have been patiently gathering this" moment, not an instant reflex on the first sweep —
 * and the race against {@link AstraVessel}'s own leak (design/astra-precipitation.md §1.2) only
 * exists at all if reaching threshold takes long enough for the leak to matter.
 */
public final class AstraPrecipitation {

    /** Held astra needed before any of it can precipitate. A first-pass number, tuned by
     *  playtest, not measured. */
    private static final double THRESHOLD = 3.0;

    /** How much richer a ritual's own conversion is than a vessel's raw 1:1 — design/
     *  astra-ritual-grammar.md §2's own headline claim: a completed ritual "precipitates a large
     *  batch... at a scale a vessel could not attempt." A real ritual's own {@code totalDrawn} is
     *  bounded by whatever one {@code AstraFieldStorage} grid cell can actually give up in sixty
     *  real seconds (order of one baseline's worth - the ground's real physics, not negotiable),
     *  which alone would never clear {@link #THRESHOLD}; this is the honest, named place a
     *  ritual's structure and sustained draw pay off as a real multiplier on what came out of the
     *  ground, not a second copy of the conversion itself (rule 46 - {@link
     *  #itemsYieldedFromRitual} still calls {@link #itemsYielded} underneath). A first-pass
     *  number, not measured. */
    private static final double RITUAL_CONCENTRATION_FACTOR = 20.0;

    private AstraPrecipitation() {}

    /** Whether a held amount is concentrated enough to precipitate at all. */
    public static boolean canPrecipitate(double heldAmount) {
        return heldAmount >= THRESHOLD;
    }

    /** Whole items of raw asterium a held amount yields — zero below {@link #THRESHOLD}. 1:1
     *  conversion, a first-pass number: every whole unit of held astra becomes one grain, and a
     *  fractional remainder stays held (see {@link #remainingAfterPrecipitation}) rather than
     *  being lost or rounded generously. */
    public static int itemsYielded(double heldAmount) {
        if (!canPrecipitate(heldAmount)) {
            return 0;
        }
        return (int) Math.floor(heldAmount);
    }

    /** What stays held after precipitating — the fractional remainder below one whole item's
     *  worth, which keeps leaking and accumulating exactly like any other held charge. Unchanged
     *  (the whole input) below {@link #THRESHOLD}, since nothing precipitated. */
    public static double remainingAfterPrecipitation(double heldAmount) {
        if (!canPrecipitate(heldAmount)) {
            return heldAmount;
        }
        return heldAmount - itemsYielded(heldAmount);
    }

    /** Whole items of raw asterium a completed ritual's total draw yields — {@link
     *  #RITUAL_CONCENTRATION_FACTOR} applied before the same whole-item floor {@link
     *  #itemsYielded} already uses, so a ritual's "large batch" and a vessel's patient 1:1 can
     *  never quietly compute their common floor two different ways. A ritual keeps no remainder
     *  (design/astra-ritual-grammar.md §2: "no stockpile") - whatever the concentration factor
     *  does not round into a whole grain is simply gone, unlike a vessel's held fraction. */
    public static int itemsYieldedFromRitual(double totalDrawn) {
        return itemsYielded(totalDrawn * RITUAL_CONCENTRATION_FACTOR);
    }
}
