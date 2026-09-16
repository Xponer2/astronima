package play.xponer.astronima.sim.suit;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Which of a suit's subsystems are working, as a packed bit mask.
 *
 * <p>Stored on the suit item itself, so a suit carries its own repair history: a
 * salvaged spare is as broken as you left it, and a suit you rebuilt stays rebuilt.
 */
public final class SuitCondition {
    /** How a suit comes out of the wreck: everything broken. */
    public static final int WRECKED = 0;

    /** A fully rebuilt suit. */
    public static int intact() {
        int mask = 0;
        for (SuitSubsystem subsystem : SuitSubsystem.values()) {
            mask |= subsystem.bit();
        }
        return mask;
    }

    public static boolean isWorking(int mask, SuitSubsystem subsystem) {
        return (mask & subsystem.bit()) != 0;
    }

    public static int repair(int mask, SuitSubsystem subsystem) {
        return mask | subsystem.bit();
    }

    public static int damage(int mask, SuitSubsystem subsystem) {
        return mask & ~subsystem.bit();
    }

    public static boolean isFullyRepaired(int mask) {
        return mask == intact();
    }

    /** Everything still broken, in declaration order — the repair checklist. */
    public static Set<SuitSubsystem> faults(int mask) {
        Set<SuitSubsystem> faults = EnumSet.noneOf(SuitSubsystem.class);
        for (SuitSubsystem subsystem : SuitSubsystem.values()) {
            if (!isWorking(mask, subsystem)) {
                faults.add(subsystem);
            }
        }
        return faults;
    }

    /** 0..1, for a progress readout on the suit panel. */
    public static float repairFraction(int mask) {
        int working = 0;
        for (SuitSubsystem subsystem : SuitSubsystem.values()) {
            if (isWorking(mask, subsystem)) {
                working++;
            }
        }
        return (float) working / SuitSubsystem.values().length;
    }

    /**
     * A suit only holds pressure once it is sealed <em>and</em> can supply gas: a
     * patched helmet is useless without a tank mount to feed it.
     */
    public static boolean canHoldPressure(int mask) {
        return isWorking(mask, SuitSubsystem.HELMET_SEAL) && isWorking(mask, SuitSubsystem.TANK_MOUNT);
    }

    /** Below this ambient oxygen the wearer closes the visor and goes on suit gas. */
    public static final double SEAL_BELOW_PPO2_KPA = 16.0;

    /**
     * Whether the visor is closed right now — the actual, instantaneous mitigation for
     * barotrauma and the only honest input to the suit HUD's seal lamp.
     *
     * <p>Deliberately not the same question as {@link #canHoldPressure}: that asks only
     * whether the suit is <em>capable</em> of holding pressure (repaired seal, repaired
     * mount), with no regard for whether the wearer has actually closed the visor. A
     * fully repaired, fully tanked suit standing in a breathable room answers
     * {@code canHoldPressure() == true} while this answers {@code false}, because nobody
     * keeps a helmet shut in air they can breathe — and a sudden decompression at that
     * moment has to hurt them exactly as if they owned no suit at all. Conflating the two
     * was a real bug: {@code AtmosphereEvents} used to resolve both the barotrauma
     * mitigation and the HUD seal lamp from {@code SuitLoadout.isSealed} (a capability
     * check), so a capable suit read as protective and lit even with the visor open.
     *
     * <p>{@code SuitLoadout.isSealed} still has a legitimate caller of its own —
     * {@code AirlockControllerBlock} asks it to decide whether it is safe to let a cycle
     * finish venting a room the wearer's visor is not sealed against <em>yet</em>, which
     * is exactly the capability question, not this one.
     */
    public static boolean isCurrentlySealed(double ambientPpO2Kpa, boolean suitCanHoldPressure) {
        return ambientPpO2Kpa < SEAL_BELOW_PPO2_KPA && suitCanHoldPressure;
    }

    /** Tank consumption multiplier: a faulty regulator wastes gas. */
    public static double tankDrainMultiplier(int mask) {
        return isWorking(mask, SuitSubsystem.REGULATOR) ? 1.0 : 2.0;
    }

    /**
     * The first candidate, in the given order, that is not working — what a repair
     * part covering several subsystems (PLAN.md rule 57) should open its bench for.
     * Empty once every candidate already works, which is a refusal, not a choice.
     */
    public static Optional<SuitSubsystem> firstFault(int mask, List<SuitSubsystem> candidates) {
        for (SuitSubsystem candidate : candidates) {
            if (!isWorking(mask, candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private SuitCondition() {}
}
