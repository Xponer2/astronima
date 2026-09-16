package play.xponer.astronima.sim.airlock;

/**
 * The airlock cycle, as a machine that knows nothing about blocks.
 *
 * <p>An airlock exists to move a person between a pressurised habitat and vacuum without
 * losing the habitat's air — air that was carried up at enormous cost, so venting it
 * overboard is throwing away a consumable. Going out is therefore slow and cheap on air:
 * a pump recovers the chamber's atmosphere into storage, and only the residue it cannot
 * reach is vented. Coming back is quick and free: stored gas at high pressure flows into
 * the empty chamber on its own. That asymmetry is not a rule written here — it falls out
 * of the physics the mod already has, and this machine only sequences it.
 *
 * <p>The one property the whole block exists to guarantee lives in the door table below:
 * <strong>the inner door is unlocked only in {@link Phase#SEALED} and the outer only in
 * {@link Phase#VACUUM}, so the two are never open across a pressure difference at once.</strong>
 * Not usually — by construction, and {@code AirlockCycleTest} asserts it across every phase.
 *
 * <p>Minecraft-free on purpose (rule 1): the sequence and its safety property are the part
 * that must be provable, so they are separated from the block that drives them. The block
 * feeds it a {@link Sense} of physical quantities each step and reads back the next phase,
 * a {@link Fault} to show, and which doors and pump to hold.
 */
public final class AirlockCycle {

    /** Chamber pressure at or below which the pump has done its useful work; vent the rest. */
    public static final double RECOVERY_THRESHOLD_KPA = 5.0;

    /**
     * Backstop on the pump-down. Reaching it means the chamber is too large for this pump
     * to evacuate in reasonable time — the honest "pump too small for this chamber" the
     * design promises instead of a magic block-count cap.
     */
    public static final int MAX_PUMP_TICKS = 1200;

    /** How long the residual vent takes — a brief hiss, not instantaneous, so it reads. */
    public static final int VENT_TICKS = 20;

    /**
     * Fraction of habitat pressure the chamber must regain before the inner door may open.
     *
     * <p>Not a full equalisation, and the number is forced by arithmetic rather than taste:
     * the vent throws away up to {@code RECOVERY_THRESHOLD/habitat} ≈ 5 % of the chamber's
     * air, so a target of 95 % is exactly on the edge of what the tank can ever give back —
     * a chamber that started at habitat pressure would asymptote forever just short of it.
     * 90 % leaves the margin that makes completion certain, and a 10 kPa step through a
     * briefly-open door is felt in the ears and harmless.
     */
    public static final double REPRESSURIZE_FRACTION = 0.90;

    /** Where in the cycle we are. */
    public enum Phase { SEALED, PUMPING, VENTING, VACUUM, REPRESSURIZING }

    /**
     * Why the cycle is not advancing, for the indicator. {@link #NONE} is progressing
     * normally. Every other value is reachable by some real arrangement — a fault nothing
     * can trigger is dead code, so the test proves each one out.
     */
    public enum Fault {
        NONE,
        /** Asked to cycle with a door open where it must be shut first. */
        DOORS_OPEN,
        /** The pump stalled with the chamber still pressurised — nowhere to put the air. */
        TANK_FULL,
        /** The pump is still working but the chamber is too large to evacuate in time. */
        PUMP_TOO_SLOW
    }

    /**
     * What the world reports this step. Physical quantities only — L3 owns every decision.
     *
     * <p>{@code moverCanHelp} is about whichever gas mover serves the current phase: while
     * PUMPING it means the pump can still make progress (its outlet below rating); while
     * REPRESSURIZING it means the tank still holds gas to give back. Both phases end the
     * same way — when their mover has done all it can — which is why it is one field.
     */
    public record Sense(boolean commanded, boolean innerDoorShut, boolean outerDoorShut,
                        double chamberPressureKPa, double habitatPressureKPa,
                        boolean moverCanHelp, int ticksInPhase) {}

    /** The next phase and the reason, if any, it is being held. */
    public record Result(Phase phase, Fault fault) {}

    /** The inner door (habitat side) is safe to open only when the chamber holds pressure. */
    public static boolean innerLocked(Phase phase) {
        return phase != Phase.SEALED;
    }

    /** The outer door (space side) is safe to open only when the chamber is evacuated. */
    public static boolean outerLocked(Phase phase) {
        return phase != Phase.VACUUM;
    }

    /** The chamber's pump runs only while pumping it down; otherwise it would never hold air. */
    public static boolean pumpEnabled(Phase phase) {
        return phase == Phase.PUMPING;
    }

    /**
     * One step of the cycle.
     *
     * <p>{@code commanded} is read only in the two resting phases; the three working phases
     * respond to the physics alone, which is what makes pressing the control again mid-cycle
     * do nothing rather than skip ahead.
     */
    public static Result step(Phase phase, Sense sense) {
        return switch (phase) {
            case SEALED -> {
                if (sense.commanded()) {
                    if (sense.innerDoorShut() && sense.outerDoorShut()) {
                        yield new Result(Phase.PUMPING, Fault.NONE);
                    }
                    yield new Result(Phase.SEALED, Fault.DOORS_OPEN);
                }
                yield new Result(Phase.SEALED, Fault.NONE);
            }
            case PUMPING -> {
                if (sense.chamberPressureKPa() <= RECOVERY_THRESHOLD_KPA) {
                    yield new Result(Phase.VENTING, Fault.NONE);
                }
                if (!sense.moverCanHelp()) {
                    // Still pressurised but the pump can move nothing: its outlet is full.
                    yield new Result(Phase.PUMPING, Fault.TANK_FULL);
                }
                if (sense.ticksInPhase() >= MAX_PUMP_TICKS) {
                    yield new Result(Phase.PUMPING, Fault.PUMP_TOO_SLOW);
                }
                yield new Result(Phase.PUMPING, Fault.NONE);
            }
            case VENTING -> sense.ticksInPhase() >= VENT_TICKS
                    ? new Result(Phase.VACUUM, Fault.NONE)
                    : new Result(Phase.VENTING, Fault.NONE);
            case VACUUM -> {
                if (sense.commanded()) {
                    if (sense.outerDoorShut()) {
                        yield new Result(Phase.REPRESSURIZING, Fault.NONE);
                    }
                    yield new Result(Phase.VACUUM, Fault.DOORS_OPEN);
                }
                yield new Result(Phase.VACUUM, Fault.NONE);
            }
            // Complete at the target — or when the tank has no more to give, because a
            // cycle that waits forever on air that does not exist seals nobody home. The
            // chamber then holds whatever the store could provide, exactly as a hand-built
            // tambour would, and the shortfall is the player's stored-air problem to fix.
            case REPRESSURIZING -> sense.chamberPressureKPa()
                    >= sense.habitatPressureKPa() * REPRESSURIZE_FRACTION
                    || !sense.moverCanHelp()
                    ? new Result(Phase.SEALED, Fault.NONE)
                    : new Result(Phase.REPRESSURIZING, Fault.NONE);
        };
    }

    private AirlockCycle() {}
}
