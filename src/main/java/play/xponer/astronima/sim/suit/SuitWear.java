package play.xponer.astronima.sim.suit;

/**
 * How long a repair lasts, packed as one nibble of remaining life per subsystem.
 *
 * <p>This is what stops the suit becoming a solved problem after the first hour. A
 * repair does not restore a subsystem to "fixed" forever — it restores it to a
 * <em>condition</em>, set by how well the job was done, and that condition is then
 * spent by use. A rushed field fix genuinely is a decision with a tail: it works now
 * and it fails sooner, which is the honest version of the trade a person actually
 * makes when something is going wrong around them.
 *
 * <p>Subsystems wear from the thing that physically tires them, not from a clock:
 * seals from pressure cycling, the regulator from flow, the thermal layer from
 * temperature swings. A suit hanging on a hook does not degrade.
 *
 * <p>Four bits each is coarse — sixteen steps of life — and that is deliberate. It
 * keeps the whole suit in a single integer that rides on the existing data component,
 * and a player cannot perceive finer resolution than "most of it left" anyway.
 */
public final class SuitWear {
    /** Life is stored in a nibble, so this is a full-condition subsystem. */
    public static final int FULL = 15;

    private static final int BITS = 4;
    private static final int MASK = 0xF;

    /** A suit that has never been repaired: nothing has any life in it. */
    public static final int NONE = 0;

    /** Everything at full life, for a factory-fresh suit. */
    public static int intact() {
        int packed = 0;
        for (SuitSubsystem subsystem : SuitSubsystem.values()) {
            packed = withLife(packed, subsystem, FULL);
        }
        return packed;
    }

    public static int lifeOf(int packed, SuitSubsystem subsystem) {
        return (packed >> (subsystem.ordinal() * BITS)) & MASK;
    }

    public static int withLife(int packed, SuitSubsystem subsystem, int life) {
        int shift = subsystem.ordinal() * BITS;
        return (packed & ~(MASK << shift)) | (Math.clamp(life, 0, FULL) << shift);
    }

    /**
     * The life a finished repair starts with. A perfect job seats properly and lasts;
     * a fumbled one is at roughly a third of that from the moment you close the panel.
     */
    public static int startingLife(float quality) {
        return Math.clamp(Math.round(quality * FULL), 1, FULL);
    }

    /** 0..1 remaining, for a gauge. */
    public static float fraction(int packed, SuitSubsystem subsystem) {
        return (float) lifeOf(packed, subsystem) / FULL;
    }

    // ------------------------------------------------ sub-nibble stress accumulation

    /**
     * Stress accumulates as <em>whole seconds</em> toward the next nibble of life,
     * ten bits per subsystem in a single long.
     *
     * <p>Counting seconds rather than fractions-of-a-nibble is the point. A nibble is
     * worth minutes of stress, so anything that converted per-tick time straight into
     * nibble units would truncate to zero every tick and the suit would never wear at
     * all — the same rounding trap the consumables hit. Seconds are the coarsest unit
     * that still divides evenly into a tick budget, so the caller accumulates
     * fractional seconds and hands over whole ones.
     */
    public static final int STRESS_BITS = 10;

    /** Largest stress count the encoding can hold, and so the longest nibble. */
    public static final int MAX_SECONDS_PER_NIBBLE = (1 << STRESS_BITS) - 1;

    private static final long STRESS_MASK = MAX_SECONDS_PER_NIBBLE;

    /** No stress accumulated anywhere. */
    public static final long NO_STRESS = 0L;

    /** Seconds of stress accumulated toward this subsystem's next lost nibble. */
    public static int stressOf(long packedStress, SuitSubsystem subsystem) {
        return (int) ((packedStress >> (subsystem.ordinal() * STRESS_BITS)) & STRESS_MASK);
    }

    private static long withStress(long packedStress, SuitSubsystem subsystem, int seconds) {
        int shift = subsystem.ordinal() * STRESS_BITS;
        return (packedStress & ~(STRESS_MASK << shift))
                | ((seconds & STRESS_MASK) << shift);
    }

    /** Seconds of stress that cost one nibble of life. */
    public static int secondsPerNibble(SuitSubsystem subsystem) {
        return (int) Math.round(lifetimeSeconds(subsystem) / (double) FULL);
    }

    /**
     * The result of applying stress: the subsystem's remaining life and its leftover
     * progress toward the next nibble, both of which the caller stores on the suit.
     */
    public record Stressed(int life, long stress) {}

    /**
     * Applies a stretch of stress to one subsystem.
     *
     * @param seconds whole seconds of the stress that actually wears this part —
     *                sealed time for a seal, gas flow for a regulator — not wall-clock
     */
    public static Stressed applyStress(int packedLife, long packedStress,
                                       SuitSubsystem subsystem, int seconds) {
        if (seconds <= 0 || isWornOut(packedLife, subsystem)) {
            return new Stressed(packedLife, packedStress);
        }
        int threshold = secondsPerNibble(subsystem);
        int accumulated = stressOf(packedStress, subsystem) + seconds;
        int nibblesLost = accumulated / threshold;

        return new Stressed(wear(packedLife, subsystem, nibblesLost),
                withStress(packedStress, subsystem, accumulated % threshold));
    }

    /**
     * Spends life on a subsystem.
     *
     * @param wearUnits how much to spend, in nibble units; fractional callers should
     *                  accumulate and pass whole units
     * @return the new packed word
     */
    public static int wear(int packed, SuitSubsystem subsystem, int wearUnits) {
        if (wearUnits <= 0) {
            return packed;
        }
        return withLife(packed, subsystem, lifeOf(packed, subsystem) - wearUnits);
    }

    /** True once a subsystem has worn out and should read as broken again. */
    public static boolean isWornOut(int packed, SuitSubsystem subsystem) {
        return lifeOf(packed, subsystem) <= 0;
    }

    /**
     * Which subsystem is closest to worn out right now — game-design audit #2, finding D: the
     * one number a proactive instrument actually needs, since a full seven-row breakdown would
     * bury it under six that do not matter yet. Never null: {@link SuitSubsystem#values()} is
     * never empty.
     */
    public static SuitSubsystem worstSubsystem(int packed) {
        SuitSubsystem worst = null;
        int worstLife = FULL + 1;
        for (SuitSubsystem subsystem : SuitSubsystem.values()) {
            int life = lifeOf(packed, subsystem);
            if (worst == null || life < worstLife) {
                worst = subsystem;
                worstLife = life;
            }
        }
        return worst;
    }

    /**
     * Seconds of the relevant stress a subsystem survives at full life.
     *
     * <p>These are lifetimes for the <em>wearing activity only</em>, not wall-clock:
     * the seal counts sealed time, the regulator counts gas flow, the thermal layer
     * counts time spent somewhere that is trying to kill you thermally. Sized so a
     * good repair comfortably outlasts several EVAs and a bad one does not.
     */
    public static double lifetimeSeconds(SuitSubsystem subsystem) {
        return switch (subsystem) {
            // Pressure cycling is the seal's enemy; it holds for many sealed hours.
            case HELMET_SEAL -> 14_400;
            // The mount is loaded whenever a tank is on it.
            case TANK_MOUNT -> 15_000;
            // The regulator wears whenever gas moves through it.
            case REGULATOR -> 10_800;
            // The bay is worked every time a cartridge is swapped, not continuously.
            case SCRUBBER_BAY -> 15_000;
            // Insulation degrades under thermal stress, which is rarer but harsher.
            case THERMAL_LAYER -> 7_200;
            // Electronics just age; the display is the longest-lived part of the suit.
            case STATUS_DISPLAY -> 15_000;
            // Gloves are the part that is actually handled, so they go first - but not so fast
            // that maintaining them is busywork. The guard on this list draws that line at two
            // hours and it drew it in the right place: the first number here was ninety minutes.
            case SEALED_GLOVES -> 7_200;
        };
    }

    /** Nibble-units of life spent per second of the stress that wears this part. */
    public static double wearPerSecond(SuitSubsystem subsystem) {
        return FULL / lifetimeSeconds(subsystem);
    }

    private SuitWear() {}
}
