package play.xponer.astronima.sim.physio;

/**
 * A body losing heat faster than it makes it, and what that costs.
 *
 * <p>The habitat has a heat balance ({@code sim/thermal/HeatBalance}); this is the same
 * arithmetic applied to the person standing in it. Metabolism in, conduction out through
 * whatever they are wearing, against the air of the room — and what comes out is a
 * <strong>core temperature</strong>.
 *
 * <h2>Why the core and not the room</h2>
 * The obvious model classifies the room and harms anyone in it below some threshold. That
 * makes a cold room a <em>wall</em>: you can be in there or you cannot, there is nothing to
 * watch, and nothing to do in the meantime. A core temperature makes it a <em>clock</em> — the
 * same room is survivable for a while and lethal eventually, the reading falls where the
 * player can see it, and every remedy stays reachable right up to the end.
 *
 * <p>And the arithmetic is unexpectedly friendly to a game. A human is about
 * {@value #BODY_CAPACITY_J_PER_K} J/K, so a hundred watts of net loss costs a degree in forty
 * minutes and a kilowatt costs one in four. Mild hypothermia is two degrees down. So a merely
 * cool room gives you an hour to notice and a bitterly cold one gives you minutes — which is
 * the shape you would want if you were tuning it by hand, and it comes out of the physics.
 *
 * <p>Minecraft-free (rule 1): temperatures and a conductance in, a core temperature out.
 */
public final class Hypothermia {

    /**
     * The body's thermal mass, J/K.
     *
     * <p>About 70 kg of mostly water at ~3500 J/kg/K. This single number sets the entire
     * timescale of the mechanic, which is why it is derived rather than chosen: a figure
     * picked to feel right would be the first thing to drift when anything else was retuned.
     */
    public static final double BODY_CAPACITY_J_PER_K = 245_000.0;

    /** Core temperature of someone who is fine, K — 37 °C. */
    public static final double NORMAL_CORE_K = 310.15;

    /**
     * Skin temperature, K — 33 °C.
     *
     * <p>Held fixed rather than modelled. The body defends its core by throttling blood to
     * the skin, so skin temperature is roughly constant across a wide range of conditions and
     * is what actually faces the room. Modelling the vasoconstriction that achieves this would
     * add a variable nobody can act on.
     */
    public static final double SKIN_K = 306.15;

    /** Surface area of an adult facing the room, m². */
    public static final double SKIN_AREA_M2 = 1.8;

    /**
     * Heat transfer from an unsuited person to habitat air, W/m²/K.
     *
     * <p>Radiation plus still-air convection through ordinary work clothing, lumped.
     * <strong>Clothing, not naked skin</strong>, and the difference decides whether the
     * mechanic is playable: bare skin is about 8 W/m²/K, which puts break-even at 26 °C and
     * would leave a player slowly chilling in their own shirtsleeve habitat forever. That is
     * physically true of a nude human and completely wrong as a description of someone in
     * coveralls, which is what the crew of a mining vessel is wearing.
     *
     * <p>At this figure break-even resting is 18 °C — just under a comfortable cabin, so a
     * warm habitat holds you and a cooling one starts a clock.
     */
    public static final double BARE_CONDUCTANCE = 3.7;

    /**
     * The same, through an intact suit thermal layer, W/m²/K.
     *
     * <p>Nearly a fourfold reduction, and the important word is <em>reduction</em>. The path this
     * replaces treated a working layer as immunity, which made the suit a certificate rather
     * than equipment. A real thermal garment is very good and is not perfect, so an intact
     * suit buys hours where bare skin has minutes — and a stripped one is dangerous without
     * being instantly fatal.
     */
    public static final double SUITED_CONDUCTANCE = 1.0;

    /** Heat an adult produces at rest, W. Matches the room's side of the same balance. */
    public static final double RESTING_METABOLIC_W = 100.0;

    /** How graded a chill is, by core temperature. */
    public enum Severity {
        /** Core is holding. Nothing to report. */
        NONE,
        /** Below 35 °C: shivering, clumsiness. Reversible by walking somewhere warm. */
        MILD,
        /** Below 32 °C: shivering stops, judgement goes. This is where it gets dangerous. */
        MODERATE,
        /** Below 28 °C: the heart is at risk. */
        SEVERE;

        public boolean isHarmful() {
            return this != NONE;
        }
    }

    /** Core below this is mild hypothermia, K — 35 °C. */
    public static final double MILD_K = 308.15;
    /** Core below this is moderate, K — 32 °C. */
    public static final double MODERATE_K = 305.15;
    /** Core below this is severe, K — 28 °C. */
    public static final double SEVERE_K = 301.15;

    public static Severity classify(double coreK) {
        if (coreK < SEVERE_K) {
            return Severity.SEVERE;
        }
        if (coreK < MODERATE_K) {
            return Severity.MODERATE;
        }
        return coreK < MILD_K ? Severity.MILD : Severity.NONE;
    }

    /** Skin-to-room conductance for someone dressed like this, W/m²/K. */
    public static double conductance(boolean thermalLayerIntact) {
        return thermalLayerIntact ? SUITED_CONDUCTANCE : BARE_CONDUCTANCE;
    }

    /**
     * Heat leaving the body into the room, W.
     *
     * <p>Negative when the room is warmer than skin, which is correct and is why hot rooms
     * are the suit's problem rather than this model's: it will happily say a body is gaining
     * heat, and nothing here punishes that.
     */
    public static double lostWatts(double ambientK, boolean thermalLayerIntact) {
        return conductance(thermalLayerIntact) * SKIN_AREA_M2 * (SKIN_K - ambientK);
    }

    /**
     * The core temperature one step later.
     *
     * <p><strong>Exertion is the third consequence of one number.</strong> Working already
     * costs oxygen and already warms the room; it warms the person too, from the same
     * {@code exertionOf} the other two use. Three effects, one metabolic rate, and no way for
     * them to disagree about how hard someone is working.
     *
     * <p>Clamped above at normal: a body does not run hot because you jogged, it sweats. The
     * heat side of that is the suit's existing thermal-stress path and is deliberately not
     * modelled here.
     *
     * @param coreK              core temperature now
     * @param ambientK           the air the person is standing in
     * @param thermalLayerIntact whether the suit's thermal layer is doing its job
     * @param exertion           multiplier over resting metabolism, 1 at rest
     */
    public static double step(double coreK, double ambientK, boolean thermalLayerIntact,
                              double exertion, double dtSeconds) {
        double produced = RESTING_METABOLIC_W * Math.max(exertion, 1.0);
        double net = produced - lostWatts(ambientK, thermalLayerIntact);
        return Math.min(NORMAL_CORE_K, coreK + net * dtSeconds / BODY_CAPACITY_J_PER_K);
    }

    /**
     * The ambient temperature at which this person exactly breaks even, K.
     *
     * <p>Worth being able to ask directly, because it is the honest statement of what a
     * loadout is good for: below this you are on a clock, above it you are not. It is also
     * what an instrument should be scaled against.
     */
    public static double sustainableAmbientK(boolean thermalLayerIntact, double exertion) {
        double produced = RESTING_METABOLIC_W * Math.max(exertion, 1.0);
        return SKIN_K - produced / (conductance(thermalLayerIntact) * SKIN_AREA_M2);
    }

    private Hypothermia() {}
}
