package play.xponer.astronima.sim.pathogen;

/**
 * A quantity of one strain sitting on something: a surface, a glove, a person's skin.
 *
 * <h2>It is a substance, not a flag</h2>
 * A flag would give three states — clean, dirty, sick — and every question worth asking would be
 * unanswerable. A load answers them. Touching a heavily contaminated block with a clean glove
 * moves a <em>share</em>, so the block gets cleaner and the glove does not. Ten light touches add
 * up to one heavy one, which is what makes routine sloppiness a hazard rather than requiring one
 * dramatic mistake. And there is a threshold with a margin under it, and the margin is the thing
 * the player is actually managing.
 *
 * <p>Minecraft-free (rule 1): a load in, a load out.
 *
 * @param source which strain this is. Mixing two is not modelled — the one you have more of wins,
 *               because a person carrying two infections is a different design problem.
 * @param load   0..1. Zero is clean; {@link #INFECTIOUS_DOSE} is where a body starts losing.
 */
public record Contamination(Strain.Source source, double load) {

    /**
     * The load at which contact with the body starts an infection.
     *
     * <p>Not 1.0, and not near zero. It sits where a single careless touch does not infect but
     * three do, so the player has a margin they can feel and spend rather than a coin toss.
     */
    public static final double INFECTIOUS_DOSE = 0.35;

    /** Below this, a load is gone rather than merely small. Stops loads from ghosting forever. */
    public static final double NEGLIGIBLE = 0.001;

    /** Share of a surface's load that comes away on a hand that touches it once. */
    public static final double TOUCH_FRACTION = 0.4;

    /** Share of what is on a glove that reaches skin when a dirty glove touches a face. */
    public static final double FACE_FRACTION = 0.5;

    /** Half-life of a load in vacuum, seconds — no water, hard radiation, and it goes. */
    public static final double VACUUM_HALF_LIFE = 90.0;

    /**
     * Half-life on a surface in a warm, damp habitat, seconds.
     *
     * <p>Two orders of magnitude longer than vacuum, and that gap is the point: the habitat you
     * built decides how forgiving your mistake is. Outside, a spill cleans itself while you walk
     * back. Inside, it is still there tomorrow.
     */
    public static final double HABITAT_HALF_LIFE = 9000.0;

    public Contamination {
        load = Math.clamp(load, 0.0, 1.0);
    }

    public static Contamination clean(Strain.Source source) {
        return new Contamination(source, 0);
    }

    public boolean isClean() {
        return load < NEGLIGIBLE;
    }

    /** Whether touching a body with this much of it would start an infection. */
    public boolean isInfectious() {
        return load >= INFECTIOUS_DOSE;
    }

    /**
     * Moves a share of this load onto something else, and returns both sides.
     *
     * <p>Conserved: what leaves here arrives there. That is what makes wiping a surface with your
     * glove do the two things it really does — the surface gets cleaner <em>and</em> you get
     * dirtier — instead of the one thing a flag could express.
     *
     * @param onto     what is already on the receiving thing
     * @param fraction share of this load that comes away
     */
    public Handover transferTo(Contamination onto, double fraction) {
        double moved = load * Math.clamp(fraction, 0.0, 1.0);
        return new Handover(new Contamination(source, load - moved), onto.add(source, moved));
    }

    /**
     * Adds a load of a strain.
     *
     * <p>If the receiving thing already carries a different strain, the larger load keeps its
     * identity. Carrying two infections at once is a different design problem and pretending to
     * model it with a sum would be a lie about what the number means.
     */
    public Contamination add(Strain.Source added, double amount) {
        if (amount <= 0) {
            return this;
        }
        if (isClean() || source == added) {
            return new Contamination(added, (source == added ? load : 0) + amount);
        }
        return amount > load ? new Contamination(added, amount) : this;
    }

    /**
     * What is left after time passes somewhere.
     *
     * @param seconds   elapsed
     * @param halfLife  {@link #VACUUM_HALF_LIFE} outside, {@link #HABITAT_HALF_LIFE} in a room
     */
    public Contamination decay(double seconds, double halfLife) {
        if (seconds <= 0 || halfLife <= 0) {
            return this;
        }
        double left = load * Math.pow(0.5, seconds / halfLife);
        return new Contamination(source, left < NEGLIGIBLE ? 0 : left);
    }

    /** Everything gone — what a decontamination step does. */
    public Contamination cleaned() {
        return new Contamination(source, 0);
    }

    /** Both sides of a transfer: what stayed behind, and what arrived. */
    public record Handover(Contamination from, Contamination to) {}
}
