package play.xponer.astronima.sim.machine;

/**
 * A setting you calibrate by hand, and that drifts while you use the machine.
 *
 * <h2>Why the slider had to go</h2>
 * Reported as <em>"I still don't get the point of the sliders — you can leave them on one value and
 * everything is fine"</em>, and that was right about six of the seven machines. A control that is
 * correct to ignore is worse than no control: it takes panel space, it takes a player's attention
 * once, and it teaches them panels are decoration.
 *
 * <p>So the setting stops being a handle in a window and becomes a <strong>calibration</strong>:
 * set on the machine, with a tool, taking time — and then moving away from where you put it as the
 * machine is used. "Set and forget" becomes "set, and come back", which is maintenance, and
 * maintenance is what an engineer's day is actually made of.
 *
 * <p>It is also true. Jaw crusher liners wear, the closed side setting opens as they do, and plants
 * measure it on a schedule by crushing a lead ball between the jaws. Somebody re-shims it. That is
 * a real job and a better one than dragging a handle.
 *
 * <h2>Drift is legible, which is what stops it being a chore tax</h2>
 * A chore is a timer that asks for a click. This is not one: the jaws open, the grind coarsens,
 * liberation falls, and the panel says all three. A player who ignores it is not punished by a
 * hidden number — they watch the yield fall and decide whether the walk is worth it.
 *
 * <p>Minecraft-free (rule 1). See {@code design/calibration.md}.
 */
public final class Calibration {

    /**
     * How a machine's setting is chosen.
     *
     * <p>The distinction that decides whether a machine keeps a handle in its window, and it is a
     * question about the <em>machine</em>, not about the interface: does the operator move this
     * during a run?
     *
     * <p>Getting this wrong in either direction is a bug the player feels. A dial on a setting
     * nobody ever moves is the decoration this whole file exists to delete. A wrench on a
     * setting the operation <em>requires</em> moving would forbid using the machine correctly,
     * which is much worse than a useless slider — though every machine that once needed that
     * live dial (the refiner's journey from forming to decomposing, the bed's trim against the
     * grind it was fed) has since turned out not to need a <em>player</em> moving it either; see
     * {@link Drift#NONE}.
     */
    public enum SetBy {
        /** Set once on the machine, with a tool, and then left. No handle in the window. */
        WRENCH,
        /** Moved while the machine runs, because moving it <em>is</em> running the machine — or,
         * now, a don't-care for a machine with no handle in its window at all; see
         * {@link Drift#NONE}. */
        DIAL
    }

    /**
     * Which way a machine's setting slides when nobody is looking, how it is set, and how long it
     * takes to need a service.
     *
     * <p><strong>The rate is stated as work-to-service, not as a slope.</strong> The first version
     * of this file carried a per-work figure like {@code 0.00055}, and nobody could tell from it
     * whether a crusher wanted a wrench twice an hour or twice a week — the number was only ever
     * checked against itself. It was wrong: a cranked machine does four work a tick, so a crusher
     * went a whole range out of true in under a minute of grinding, which is not maintenance but a
     * machine that eats itself. Saying "about this much work, then service it" is a claim somebody
     * can read, argue with, and test.
     */
    public enum Drift {
        /**
         * Liner wear: the jaws open up, so the grind gets coarser.
         *
         * <p>Roughly six batches of ore. The most-used machine in the game and the one whose
         * setting matters most, so it is also the one that asks most often.
         */
        CRUSHER(900, +1, SetBy.WRENCH, "jaws worn open"),
        /** The ram's spring takes a set, so the blow softens. About thirty hammer blows. */
        FORGE(800, -1, SetBy.WRENCH, "ram spring gone soft"),
        /** The magnet warms with use, so the field weakens. About ten drums of feed. */
        SEPARATOR(1300, -1, SetBy.WRENCH, "magnet warm, field down"),
        /**
         * No drift, because there is nothing left for a player to leave mis-set.
         *
         * <p>Three machines share this now, and each found its own right answer moves on its
         * own: the retort's mirror computes its own aim every tick from the sun and the loaded
         * process's window ({@code SolarRetortBlockEntity#focus()}); the refiner's vessel heats
         * straight to the one point where forming and decomposing both run at once
         * ({@code CarbonylRefinerBlockEntity#setpointK()}); the bed's drum brackets the boiling
         * band for whatever it was fed and sits in the middle of it
         * ({@code FluidizedBedBlockEntity#setting()}). {@code setBy()} here is a don't-care —
         * {@code MachineBody} excludes each of their kinds from mouse interaction directly,
         * the same way it already does for the winnowing table, rather than repurposing
         * {@link SetBy#WRENCH}'s recalibration framing for machines that have neither a wrench
         * job nor an error to recalibrate.
         */
        NONE(0, 0, SetBy.DIAL, "");

        private final int workToService;
        private final int direction;
        private final SetBy setBy;
        private final String fault;

        Drift(int workToService, int direction, SetBy setBy, String fault) {
            this.workToService = workToService;
            this.direction = direction;
            this.setBy = setBy;
            this.fault = fault;
        }

        /**
         * Work this machine gets through before the panel says it is worth the walk.
         *
         * <p>Compare against {@code workRequired()} to read it in batches: a crusher batch is
         * about 150, so 900 is six of them.
         */
        public int workToService() {
            return workToService;
        }

        /** How far the setting moves per unit of work, which is the shape the maths wants. */
        public double perWork() {
            return workToService <= 0 ? 0 : WORTH_RESETTING / workToService;
        }

        public int direction() {
            return direction;
        }

        public SetBy setBy() {
            return setBy;
        }

        /**
         * What has gone wrong, in the machine's own words, for the panel to say.
         *
         * <p>Named after the part rather than the number, because "jaws worn open" tells a player
         * what to picture and 0.14 does not (rule 25).
         */
        public String fault() {
            return fault;
        }

        public boolean drifts() {
            return workToService > 0;
        }

        /** Whether this machine has no handle in its window, because a wrench sets it. */
        public boolean isSetByWrench() {
            return setBy == SetBy.WRENCH;
        }
    }

    /**
     * Where the setting has got to after this much work.
     *
     * <p><strong>Work, not time.</strong> A machine standing idle stays where it was — the same
     * distinction the suit's wear model already draws, and the right one: a player who leaves for
     * the night should not come back to a crusher that has ruined itself doing nothing.
     *
     */
    public static double after(double set, Drift drift, double work) {
        if (!drift.drifts() || work <= 0) {
            return Math.clamp(set, 0.0, 1.0);
        }
        double moved = set + drift.direction() * drift.perWork() * work;
        return Math.clamp(moved, 0.0, 1.0);
    }

    /**
     * How far out of true a powered servo lets a machine get.
     *
     * <p>A closed-loop positioner does not hold a point, it holds a <strong>band</strong>: it
     * corrects when the error grows past what it can see, so the setting wanders inside its own
     * resolution and never leaves it. Comfortably under {@link #WORTH_RESETTING}, so a servoed
     * machine never asks for a wrench — that is the whole reward — but not zero either, because a
     * servo that cancelled drift outright would turn this mechanic <em>off</em> for everybody past
     * the electrical tier and the machines would stop being machines again.
     */
    public static final double SERVO_DEADBAND = 0.03;

    /**
     * Work charged against a machine's calibration this tick.
     *
     * <p>This is the whole of what a servo does, and it is deliberately expressed as
     * <em>charging less wear</em> rather than as reading the setting differently. A servo holds
     * where it finds things: putting a machine right is a person with a tool. An implementation
     * that cancelled the drift at display time would hand the player a free instant recalibration
     * for the price of one wire — a far bigger reward than this tier is selling (design §6.3).
     *
     * @param already how much work the machine has already accumulated since it was calibrated
     * @param rate    what this tick would ordinarily add
     * @param held    whether a wired, powered servo is holding it
     * @return the work to add, which is zero once a held machine has reached its deadband
     */
    public static double workCharged(double already, double rate, Drift drift, boolean held) {
        if (!held || !drift.drifts()) {
            return rate;
        }
        double ceiling = SERVO_DEADBAND / drift.perWork();
        return Math.clamp(ceiling - already, 0, rate);
    }

    /**
     * How far out of true it has got, 0..1.
     *
     * <p>The number the panel draws as a distance. A percentage of what it should be would flatter
     * a machine that has drifted to one end and stopped, which is the state a player most needs to
     * notice.
     */
    public static double error(double set, double now) {
        return Math.abs(set - now);
    }

    /**
     * Beyond this the machine is worth walking back to.
     *
     * <p>Not "is it perfect" — nothing stays perfect and a panel that said so would be nagging.
     * This is the point where the yield loss is worth the minutes, and it is what the instrument
     * reports rather than a raw number the player has to judge for themselves (rule 25).
     */
    public static final double WORTH_RESETTING = 0.12;

    public static boolean isWorthResetting(double set, double now) {
        return error(set, now) >= WORTH_RESETTING;
    }

    /**
     * How long a calibration takes to perform, in seconds.
     *
     * <p>Long enough to be a job and short enough not to be a punishment. It is the whole cost of
     * the mechanic: calibrating never consumes a resource, only minutes, so a player can always
     * put a machine right however far it has gone.
     */
    public static final double SECONDS_TO_CALIBRATE = 3.0;

    private Calibration() {}
}
