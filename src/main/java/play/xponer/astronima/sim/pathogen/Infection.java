package play.xponer.astronima.sim.pathogen;

import play.xponer.astronima.sim.physio.Ailment;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One infection, running on one person.
 *
 * <h2>Four things that make it a disease rather than a timer</h2>
 * <ul>
 *   <li><strong>Incubation.</strong> Silent for a while, so the cause is not obviously the thing
 *       you just did. A timer starts when you can see it start.</li>
 *   <li><strong>Progression.</strong> Untreated it worsens through stages, each <em>adding</em>
 *       symptoms. You are not "sick"; you are at a point on a curve, and how far you let it go is
 *       your decision.</li>
 *   <li><strong>Immune response.</strong> A rested, well-fed body pushes back, so the correct
 *       answer is sometimes <em>nothing</em> — and knowing that is knowledge.</li>
 *   <li><strong>Resistance.</strong> Treat with the wrong thing, or stop early, and you have bred
 *       a strain that treatment will never touch again.</li>
 * </ul>
 *
 * <p>Resistance is the one that changes how people play. A wrong guess does not cost a dose; it
 * costs that treatment <strong>for ever, against this organism</strong>. That is what makes the
 * assay bench worth building instead of guessing twice.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class Infection {

    private final Strain strain;
    private final Set<Treatment> shrugsOff = new LinkedHashSet<>();

    private double elapsed;
    private Strain.Stage stage = Strain.Stage.HIDDEN;
    private double pressure;
    private Treatment course;
    private double courseTaken;
    private boolean cleared;

    public Infection(Strain strain) {
        this.strain = strain;
    }

    public Strain strain() {
        return strain;
    }

    public Strain.Stage stage() {
        return stage;
    }

    public boolean isCleared() {
        return cleared;
    }

    /** True while it is present and showing nothing — the window the whole design turns on. */
    public boolean isIncubating() {
        return !cleared && elapsed < strain.incubationSeconds();
    }

    /** What the player can feel. Nothing at all until incubation is over. */
    public List<Ailment> symptoms() {
        return cleared || isIncubating() ? List.of() : strain.showing(stage);
    }

    /** Whether this strain has already learned to shrug that treatment off. */
    public boolean isResistantTo(Treatment treatment) {
        return shrugsOff.contains(treatment);
    }

    /** Every treatment it has beaten, in the order it beat them — for the medical readout. */
    public List<Treatment> resistances() {
        return List.copyOf(shrugsOff);
    }

    /**
     * Advances the illness.
     *
     * <p><strong>Immunity is a rate, not a shield.</strong> A shield would make the outcome binary:
     * either you are healthy enough to be untouchable or you are not. As a rate it gives the three
     * outcomes the tier wants out of one number — a healthy body shrugs off a mild strain, the same
     * body loses to an aggressive one, and an exhausted body loses to both.
     *
     * @param immunity 0..1, from rest and nutrition
     */
    public void step(double seconds, double immunity) {
        if (cleared || seconds <= 0) {
            return;
        }
        elapsed += seconds;
        if (elapsed < strain.incubationSeconds()) {
            return;                 // silent, and not yet doing anything either
        }
        double help = course != null && !isResistantTo(course)
                && course.worksOn(strain) ? course.strength() : 0;
        if (course != null) {
            courseTaken += seconds;
        }
        // Positive means the organism is gaining. Virulence against immunity plus whatever the
        // treatment is adding — the treatment moves the line rather than ending the fight, which
        // is why a course has to be finished.
        double swing = (strain.virulence() - immunity - help) * 2;
        pressure += swing * seconds / Math.max(1e-6, strain.stageSeconds());

        while (pressure >= 1) {
            pressure -= 1;
            stage = stage.worse();
        }
        while (pressure <= -1) {
            pressure += 1;
            if (stage == Strain.Stage.HIDDEN) {
                cleared = true;     // pushed all the way back out
                return;
            }
            stage = stage.better();
        }
    }

    /**
     * Starts a course of treatment.
     *
     * <p>Starting a second one abandons the first, which is a decision the player makes and pays
     * for below.
     */
    public void begin(Treatment treatment) {
        if (course != null && course != treatment) {
            stop();
        }
        course = treatment;
        courseTaken = 0;
    }

    /**
     * Stops the current course.
     *
     * <p><strong>This is where resistance is bred</strong>, and it is bred by two different
     * mistakes that deserve the same punishment. Stopping a working course <em>early</em> leaves
     * the organisms that were hardest to kill; and a course that was never going to work — the
     * wrong countermeasure — is a full course of selection pressure with no kill at all.
     *
     * <p>Recorded against the <strong>pair</strong>, not against the strain. A strain that shrugged
     * off one antimicrobial is still answerable to another, which is the entire reason for having
     * more than one and the reason the assay bench is worth building.
     */
    public void stop() {
        if (course == null) {
            return;
        }
        boolean wrong = !course.worksOn(strain);
        boolean earlyExit = courseTaken < course.courseSeconds();
        if (!cleared && (wrong || earlyExit)) {
            shrugsOff.add(course);
        }
        course = null;
        courseTaken = 0;
    }

    public double elapsed() {
        return elapsed;
    }

    /** Part-way progress toward the next stage, so a relog does not restart the clock. */
    public double pressure() {
        return pressure;
    }

    /**
     * Puts a saved infection back where it was.
     *
     * <p>Separate from the constructor on purpose: a fresh infection is a fresh infection, and an
     * illness that could be constructed mid-stage would let any caller invent one at whatever
     * point suited it. Restoring is what a save does, and it says so.
     */
    public void restore(double sinceStart, Strain.Stage at, double partWay) {
        elapsed = Math.max(0, sinceStart);
        stage = at;
        pressure = Math.clamp(partWay, -1, 1);
    }

    /** How far into the current course it is, so a relog does not restart the clock. */
    public double taken() {
        return courseTaken;
    }

    /** Puts a saved course back where it was. */
    public void carry(double alreadyTaken) {
        courseTaken = Math.max(0, alreadyTaken);
    }

    /** Records a resistance the organism already had when it was saved. */
    public void remember(Treatment treatment) {
        shrugsOff.add(treatment);
    }

    /** The treatment currently being taken, or null. */
    public Treatment course() {
        return course;
    }

    /** True once the current course has been taken for long enough to count as finished. */
    public boolean courseFinished() {
        return course != null && courseTaken >= course.courseSeconds();
    }
}
