package play.xponer.astronima.sim.metal;

/**
 * The evacuated chamber a cold weld actually happens in.
 *
 * <p>Written after a design error worth recording. Cold welding needs no oxide film,
 * so the first version required the <em>room</em> to be free of oxygen — which meant
 * the player had to stand in an unbreathable space in a suit to do any metalwork, and
 * a suit needs parts, which need metal. It was a knot, and it was also not how any of
 * this is done in reality.
 *
 * <p><strong>Real vacuum processes evacuate a chamber, not a building.</strong> A bell
 * jar, a glove box, a vacuum press: the operator stands in shirtsleeves and the hard
 * vacuum is inside a box the size of the work. That is what this models, and it
 * dissolves the knot without softening the physics — the weld still needs vacuum, it
 * simply needs it in the right place.
 *
 * <p>What is preserved is the reason to care about where you build. Pumping a chamber
 * down from habitat pressure is real work; pumping it down when it is already sitting
 * in a vacuum tunnel is almost none. So a cold shop out in the airless part of the rock
 * is a genuine optimisation, rather than the only way through.
 */
public final class VacuumChamber {
    /** Chamber pressure below which fresh metal surfaces stay clean enough to weld. */
    public static final double WELD_PRESSURE_KPA = 0.5;

    /** Sea-level pressure, the worst case a hand pump will ever face. */
    public static final double HABITAT_PRESSURE_KPA = 101.0;

    /**
     * Fraction of the remaining gas one stroke of the pump removes.
     *
     * <p>A displacement pump takes a fixed <em>volume</em> per stroke, so it removes a
     * fixed fraction of what is left rather than a fixed amount. That is why pumping
     * down is exponential and why the last decade of pressure takes as many strokes as
     * the first — every vacuum technician's least favourite fact.
     */
    public static final double STROKE_EFFICIENCY = 0.22;

    /** Leak rate back into a sealed chamber, per second, as a fraction of the gap. */
    private static final double LEAK_RATE = 0.02;

    /**
     * Pressure after one pump stroke.
     *
     * <p>Exponential decay, never reaching zero — which is correct: no pump reaches a
     * perfect vacuum, and the weld threshold is a real pressure rather than an
     * absence.
     */
    public static double pump(double chamberKPa) {
        return Math.max(0, chamberKPa * (1.0 - STROKE_EFFICIENCY));
    }

    /**
     * Strokes needed to bring a chamber from ambient down to welding pressure.
     *
     * <p>The whole point of the model in one number: about 24 strokes from a
     * pressurised habitat, and none at all in a vacuum tunnel. Building a workshop
     * where the air already is not saves real effort without being mandatory.
     */
    public static int strokesToWeld(double ambientKPa) {
        if (ambientKPa <= WELD_PRESSURE_KPA) {
            return 0;
        }
        int strokes = 0;
        double pressure = ambientKPa;
        while (pressure > WELD_PRESSURE_KPA && strokes < 500) {
            pressure = pump(pressure);
            strokes++;
        }
        return strokes;
    }

    /**
     * Gas creeping back into a chamber that is not being pumped.
     *
     * <p>Every seal leaks. It means a chamber left alone slowly comes back up to the
     * room around it, so the work has to be finished rather than started and
     * abandoned — and it is why a forge in a vacuum tunnel never has to be pumped at
     * all, since there is nothing outside to leak in.
     */
    public static double leak(double chamberKPa, double ambientKPa, double dtSeconds) {
        if (chamberKPa >= ambientKPa) {
            return ambientKPa;
        }
        double gap = ambientKPa - chamberKPa;
        return Math.min(ambientKPa, chamberKPa + gap * LEAK_RATE * dtSeconds);
    }

    /** True when the chamber is empty enough for clean metal to bond. */
    public static boolean canWeld(double chamberKPa) {
        return chamberKPa <= WELD_PRESSURE_KPA;
    }

    /** 0..1 for a gauge: 1 is hard vacuum, 0 is full habitat pressure. */
    public static float evacuation(double chamberKPa) {
        return (float) Math.clamp(1.0 - chamberKPa / HABITAT_PRESSURE_KPA, 0.0, 1.0);
    }

    private VacuumChamber() {}
}
