package play.xponer.astronima.sim.circuit;

/**
 * One run of wire, as a physical object: a material, a thickness and a length.
 *
 * <p>Everything electrical about a cable comes out of those three and nothing else.
 * {@code R = ρ·L/A} is the whole of it, and it is why this replaces {@code Transmission}'s
 * {@code LOSS_PER_BLOCK = 0.01} — a constant that document itself flags as
 * <em>"a game decision instead of a law"</em>. A percentage per block cannot tell a thin wire
 * from a thick one, iron from aluminium, or a hot run from a cold one. Ohm can.
 *
 * <h2>The 1-pixel wire is a 1-pixel jacket</h2>
 * A wire drawn 1/16 of a block across would be 6.25 cm of solid metal — a bus bar 150 times the
 * section of a real 100 A cable, with a resistance so low that no mechanic could exist. So the
 * sheath is what you see and <strong>the conductor inside it is a stated gauge</strong>, exactly
 * as in life: a 4 mm² conductor inside a 6 cm conduit is an ordinary installation. Cross-section
 * is a property of the wire type, and it is the second axis of the ladder — thicker costs more
 * metal and loses less.
 *
 * <p>Minecraft-free (rule 1). Lengths are metres, and one block is one metre everywhere in this
 * mod, so a run's length in blocks is its length in metres.
 *
 * @param material      what it is made of
 * @param crossSectionM2 the conductor's area, m² — not the jacket's
 * @param lengthM       how far it goes, m
 */
public record Conductor(ConductorMaterial material, double crossSectionM2, double lengthM) {

    /** Control wiring: enough for a relay coil or a sensor, useless for a machine. */
    public static final double SIGNAL_MM2 = 0.5;
    /** The default power conductor, and the one every worked example in the design uses. */
    public static final double STANDARD_MM2 = 4.0;
    /** For a run that has to carry a whole workshop. */
    public static final double HEAVY_MM2 = 16.0;
    /** A bus bar: short, fat, and what a generator bolts onto. */
    public static final double BUSBAR_MM2 = 50.0;

    /** Square millimetres as the square metres the arithmetic wants. */
    public static double mm2(double squareMillimetres) {
        return squareMillimetres * 1e-6;
    }

    public Conductor {
        if (crossSectionM2 <= 0) {
            throw new IllegalArgumentException("a conductor with no cross-section is not a wire");
        }
        if (lengthM < 0) {
            throw new IllegalArgumentException("negative length");
        }
    }

    /**
     * Resistance at a temperature, Ω — {@code R = ρ(T)·L/A}.
     *
     * <p>Takes the temperature rather than assuming room temperature, because a wire at its
     * limit is genuinely half again as resistive as a cold one, and that difference is the
     * runaway {@link ConductorMaterial} describes rather than a rounding error.
     */
    public double resistance(double temperatureK) {
        return material.resistivity(temperatureK) * lengthM / crossSectionM2;
    }

    /** Resistance of one metre, Ω/m — what an ampacity calculation is charged against. */
    public double resistancePerMetre(double temperatureK) {
        return material.resistivity(temperatureK) / crossSectionM2;
    }

    /**
     * The conductor's diameter, m, taking it as round.
     *
     * <p>Round because that is what wire is, and because the diameter is not cosmetic here: it
     * sets the surface area the run has to shed its heat through, which in vacuum is the only
     * thing standing between a current and a puddle.
     */
    public double diameterM() {
        return Math.sqrt(4.0 * crossSectionM2 / Math.PI);
    }

    /** Outside area per metre of run, m²/m — the radiating and convecting surface. */
    public double surfacePerMetreM2() {
        return Math.PI * diameterM();
    }

    /**
     * What this run turns into heat at a given current, W — {@code P = I²R}.
     *
     * <p>The square is the entire reason voltage is the decision this tier turns on: halving the
     * current quarters the loss, and raising the bus voltage is how you halve the current
     * without touching the wire.
     */
    public double lossWatts(double amps, double temperatureK) {
        return amps * amps * resistance(temperatureK);
    }

    /** How much voltage the run itself eats, V — {@code V = I·R}. */
    public double voltageDrop(double amps, double temperatureK) {
        return amps * resistance(temperatureK);
    }

    /** The same run, a different length. */
    public Conductor withLength(double metres) {
        return new Conductor(material, crossSectionM2, metres);
    }
}
