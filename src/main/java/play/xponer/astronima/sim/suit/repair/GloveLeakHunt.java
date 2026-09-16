package play.xponer.astronima.sim.suit.repair;

import play.xponer.astronima.sim.suit.SuitSubsystem;

/**
 * Sealed gloves — <strong>find the hole before you patch it</strong>.
 *
 * <p>Every other repair in this suit starts with a part that is visibly wrong. A pinhole in a
 * glove is not: it is invisible, it is somewhere, and the whole job is finding it. So this is the
 * one procedure where the work happens <em>before</em> the fix, and the verb is search rather than
 * assembly.
 *
 * <p>The method is the real one. Inflate the glove, hold it under a bead of soap solution, and
 * watch for the stream of bubbles. Sweep along the seam and the bubbling rises as you approach the
 * leak and falls as you pass it — you are following a gradient with your hands, exactly as a
 * technician does. Then squeeze the patch on where the bubbling is loudest.
 *
 * <p>The trap it allows, and it is a real one: <strong>patch it where you first hear something and
 * you will patch the wrong place.</strong> A near miss seals over the hole's edge, holds pressure
 * on the bench, and splits the first time you grip something. That failure is deliberately
 * survivable — you get the glove back and you get to look again — because
 * {@link RepairTask} forbids stranding anybody. It just costs you the search twice.
 *
 * <p>Minecraft-free (rule 1); the screen renders this and decides nothing (rule 25).
 */
public final class GloveLeakHunt implements RepairTask {

    /** How wide the bubbling can be heard around the leak, in sweep units. */
    public static final double AUDIBLE_WIDTH = 0.22;

    /** Within this of the leak, a patch actually seals it. */
    public static final double PATCH_TOLERANCE = 0.05;

    /** How fast the hands sweep along the seam, sweep units per second. */
    private static final double SWEEP_SPEED = 0.35;

    /** Pressure lost per second while inflated, so searching forever is not free. */
    private static final double BLEED_PER_SECOND = 0.06;

    private final double leakAt;
    private double position;
    private double pressure = 1.0;
    private boolean sweepingForward = true;
    private boolean sealed;
    private int missedPatches;

    public GloveLeakHunt() {
        this(0.63); // deterministic by default: a test that cannot predict the leak tests nothing
    }

    public GloveLeakHunt(double leakAt) {
        this.leakAt = Math.clamp(leakAt, 0.0, 1.0);
    }

    @Override
    public SuitSubsystem subsystem() {
        return SuitSubsystem.SEALED_GLOVES;
    }

    @Override
    public void tick(double dtSeconds) {
        if (sealed || dtSeconds <= 0) {
            return;
        }
        pressure = Math.max(0, pressure - BLEED_PER_SECOND * dtSeconds);
        position += (sweepingForward ? 1 : -1) * SWEEP_SPEED * dtSeconds;
        if (position >= 1.0) {
            position = 1.0;
            sweepingForward = false;
        } else if (position <= 0.0) {
            position = 0.0;
            sweepingForward = true;
        }
    }

    /** Turn the sweep around, which is how you close in on a gradient rather than pace past it. */
    public void reverse() {
        sweepingForward = !sweepingForward;
    }

    /**
     * How hard it is bubbling right now, 0..1 — the only information the player gets.
     *
     * <p>Falls off with distance from the leak <em>and</em> with the pressure left in the glove,
     * so a long hunt gets quieter. That is the clock on this job: nothing stops you searching, but
     * the signal you are searching by fades while you do it.
     */
    public double bubbling() {
        double distance = Math.abs(position - leakAt);
        if (distance >= AUDIBLE_WIDTH) {
            return 0;
        }
        return (1.0 - distance / AUDIBLE_WIDTH) * pressure;
    }

    /** Re-inflate. Costs nothing but the time, and the time is the cost. */
    public void repressurise() {
        if (!sealed) {
            pressure = 1.0;
        }
    }

    /** Press the patch on here. */
    public void patch() {
        if (sealed) {
            return;
        }
        if (Math.abs(position - leakAt) <= PATCH_TOLERANCE) {
            sealed = true;
        } else {
            // A near miss seals over the edge of the hole: it holds on the bench and splits under
            // load. You get the glove back, and you get to look again.
            missedPatches++;
            pressure = 1.0;
        }
    }

    public double position() {
        return position;
    }

    public double pressure() {
        return pressure;
    }

    public int missedPatches() {
        return missedPatches;
    }

    @Override
    public boolean isComplete() {
        return sealed;
    }

    @Override
    public float progress() {
        return sealed ? 1f : (float) Math.clamp(bubbling(), 0.0, 0.95);
    }

    /**
     * A patch laid right on the hole is as good as new; one laid at the edge of tolerance has
     * already taken a set, and every missed attempt is another scar on the same glove.
     */
    @Override
    public float quality() {
        if (!sealed) {
            return 0.4f;
        }
        float placement = (float) (1.0 - Math.abs(position - leakAt) / PATCH_TOLERANCE * 0.25);
        return Math.max(0.4f, placement - missedPatches * 0.12f);
    }

    @Override
    public String hintKey() {
        if (sealed) {
            return "done";
        }
        if (pressure < 0.25) {
            return "reinflate_the_glove";
        }
        return bubbling() > 0 ? "patch_where_it_bubbles_loudest" : "sweep_the_seam_for_bubbles";
    }
}
