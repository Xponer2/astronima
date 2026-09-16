package play.xponer.astronima.sim.circuit;

/**
 * What the air around a wire is worth to it — the tier's central asteroid twist, made askable.
 *
 * <p>{@code design/electrical.md} §2.1 states it: <strong>a wire's current rating is not a property
 * of the wire.</strong> It is the current at which heat in equals heat out, and on Earth "heat out"
 * is mostly convection into air. In vacuum a conductor can only radiate, so the same copper at the
 * same current runs far hotter — and a run through your workshop and the identical run across the
 * surface are <em>different components</em>.
 *
 * <p>{@link Ampacity} already does that arithmetic, and has since E2. What was missing is the step
 * before it: turning <em>where the wire actually is</em> into the two numbers Ampacity wants. Until
 * this existed the only place the twist appeared in the game was a debug command, and the meter in
 * the player's hand quoted a vacuum rating at a fixed ambient <strong>everywhere</strong> — which
 * inside a habitat is not a simplification, it is wrong by two thirds.
 *
 * <p>Minecraft-free (rule 1), so the claim the whole section rests on — air roughly doubles what a
 * wire may carry — is checked as arithmetic rather than by standing in a room.
 */
public final class Cooling {

    /** One atmosphere, kPa. The pressure at which convection is doing all it can. */
    public static final double SEA_LEVEL_KPA = 101.325;

    /**
     * What a wire with no room around it sees, K.
     *
     * <p>Not 2.7 K. A trace on an asteroid is stapled to rock and lit by a distant sun, and it
     * exchanges with both far more than with the sky — the same 200 K {@code Ampacity}'s own
     * figures are quoted against, and the same the rock conducts at elsewhere in the mod. Using
     * the sky temperature here would make every outdoor run look better cooled than it is.
     */
    public static final double VACUUM_AMBIENT_K = 200.0;

    /**
     * Above this share of its limit, a wire is worth warning about.
     *
     * <p>Nine tenths: close enough that an extra machine on the run will take it over, far enough
     * that a warning is not the normal state of a healthy circuit. A hazard the player only hears
     * about once it has happened is the thing rule 7 forbids.
     */
    public static final double WARN_FRACTION = 0.9;

    /**
     * How much convection the surroundings can offer, 0 (vacuum) to 1 (a full atmosphere).
     *
     * <p>Linear in pressure, and that is first-order right rather than a fudge: free convection
     * carries heat in proportion to how much gas there is to carry it. A half-pressure habitat
     * cools a wire about half as well as a full one, which is the answer a player would guess and
     * also the answer the physics gives.
     */
    public static double airFraction(double pressureKPa) {
        return Math.clamp(pressureKPa / SEA_LEVEL_KPA, 0.0, 1.0);
    }

    /**
     * What a run is doing where it lies.
     *
     * @param ratingAmps   the most this wire may carry here before its insulation is at its limit
     * @param wireK        the temperature it actually settles at, at this current
     * @param airFraction  how much of the cooling is convection — 0 outside, 1 in a full habitat
     */
    public record Conditions(double ratingAmps, double wireK, double airFraction) {

        /** Near enough the limit that one more load takes it over. */
        public boolean isHot() {
            return wireK >= Ampacity.INSULATION_LIMIT_K * WARN_FRACTION;
        }

        /** Past the point where the insulation survives. */
        public boolean isOverloaded() {
            return wireK >= Ampacity.INSULATION_LIMIT_K;
        }

        /** How much of the rating is being used, 0..1 and beyond. */
        public double load() {
            return ratingAmps <= 0 ? 0 : wireK / Ampacity.INSULATION_LIMIT_K;
        }
    }

    /**
     * What a metre of this wire, carrying this current, does in these surroundings.
     *
     * @param pressureKPa the gas around it — zero outside, about a hundred in a habitat
     * @param ambientK    what the surroundings sit at; use {@link #VACUUM_AMBIENT_K} with no room
     */
    public static Conditions of(Conductor metre, double amps, double pressureKPa,
                                double ambientK) {
        double air = airFraction(pressureKPa);
        return new Conditions(
                Ampacity.limitAmps(metre, ambientK, air),
                Ampacity.steadyTemperatureK(metre, amps, ambientK, air),
                air);
    }

    /** The same wire and current, with nothing around it at all. */
    public static Conditions inVacuum(Conductor metre, double amps) {
        return of(metre, amps, 0, VACUUM_AMBIENT_K);
    }

    private Cooling() {}
}
