package play.xponer.astronima.sim.magic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A player's own research: which objects they have ever successfully observed through a real
 * instrument, which claims they hold, which objects a held claim has confirmed as identified, and
 * which (claim, evidence) combinations they already tried and got wrong
 * (design/astra-research.md §8).
 *
 * <p>{@code capturedTargets} replaces the old {@code SpectralPlateItem}'s job of carrying evidence
 * between an observation and a claim attempt (design/astra-telescope.md §5): a successful telescope
 * lock-on writes here directly, and {@code ClaimResolvePayload} reads this set instead of scanning
 * a player's inventory for plates that no longer exist.
 *
 * <p>Same shape as {@code progression.Unlocks} on purpose, including the reason: no {@code Codec}
 * field here, so this stays a plain JUnit-testable class the way every {@code sim/}-style record
 * in this project is. The codec lives in {@code ModAttachments}, which already needs {@code
 * com.mojang.serialization} for every attachment it declares.
 */
public record ResearchState(Set<String> capturedTargets, Set<String> heldClaims,
                             Set<String> identifiedObjects, Set<String> refutedCombinations,
                             Set<String> openedBranches,
                             Map<String, Integer> stageProgress) {

    public static final ResearchState NONE =
            new ResearchState(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Map.of());

    public ResearchState {
        capturedTargets = Set.copyOf(capturedTargets);
        heldClaims = Set.copyOf(heldClaims);
        identifiedObjects = Set.copyOf(identifiedObjects);
        refutedCombinations = Set.copyOf(refutedCombinations);
        openedBranches = Set.copyOf(openedBranches);
        stageProgress = Map.copyOf(stageProgress);
    }

    /** The five-field shape every pre-stage save carries; stage progress starts empty. */
    public ResearchState(Set<String> capturedTargets, Set<String> heldClaims,
                         Set<String> identifiedObjects, Set<String> refutedCombinations,
                         Set<String> openedBranches) {
        this(capturedTargets, heldClaims, identifiedObjects, refutedCombinations,
                openedBranches, Map.of());
    }

    /** Highest stage index this player has completed of {@code claimId}; 0 if none. */
    public int stageOf(String claimId) {
        return stageProgress.getOrDefault(claimId, 0);
    }

    public boolean captured(String objectId) {
        return capturedTargets.contains(objectId);
    }

    public boolean holds(String claimId) {
        return heldClaims.contains(claimId);
    }

    public boolean identified(String objectId) {
        return identifiedObjects.contains(objectId);
    }

    public boolean wasRefuted(String combinationKey) {
        return refutedCombinations.contains(combinationKey);
    }

    /** Whether a branch's cartography has been charted yet (design/astra-research.md §2a) — a
     * branch can be open with zero claims held in it, the same "known to exist, not yet resolved"
     * shape {@link #capturedTargets} already has for a single object. */
    public boolean opened(String branchId) {
        return openedBranches.contains(branchId);
    }

    /** Records a real observation, unchanged if already captured — locking onto the same object
     * twice must not be observable, the same idempotence every other mutator here already keeps. */
    public ResearchState withCaptured(String objectId) {
        if (capturedTargets.contains(objectId)) {
            return this;
        }
        Set<String> next = new HashSet<>(capturedTargets);
        next.add(objectId);
        return new ResearchState(next, heldClaims, identifiedObjects, refutedCombinations, openedBranches);
    }

    /** Grants a claim, unchanged if already held — granting twice must not be observable. */
    public ResearchState withHeld(String claimId) {
        if (heldClaims.contains(claimId)) {
            return this;
        }
        Set<String> next = new HashSet<>(heldClaims);
        next.add(claimId);
        return new ResearchState(capturedTargets, next, identifiedObjects, refutedCombinations, openedBranches);
    }

    /** Revokes a claim — the debug command's undo. Unchanged if it was not held. */
    public ResearchState withoutHeld(String claimId) {
        if (!heldClaims.contains(claimId)) {
            return this;
        }
        Set<String> next = new HashSet<>(heldClaims);
        next.remove(claimId);
        return new ResearchState(capturedTargets, next, identifiedObjects, refutedCombinations, openedBranches);
    }

    public ResearchState withIdentified(String objectId) {
        if (identifiedObjects.contains(objectId)) {
            return this;
        }
        Set<String> next = new HashSet<>(identifiedObjects);
        next.add(objectId);
        return new ResearchState(capturedTargets, heldClaims, next, refutedCombinations, openedBranches);
    }

    /** Records a failed resolve attempt — never removed; a struck-out mark stays visible (§3.2). */
    public ResearchState withRefuted(String combinationKey) {
        if (refutedCombinations.contains(combinationKey)) {
            return this;
        }
        Set<String> next = new HashSet<>(refutedCombinations);
        next.add(combinationKey);
        return new ResearchState(capturedTargets, heldClaims, identifiedObjects, next, openedBranches);
    }

    /** Charts a branch's cartography, unchanged if already open — design/astra-research.md §2a's
     *  once-per-branch event. Opening twice must not be observable, the same idempotence every
     *  other mutator here already keeps. */
    public ResearchState withOpened(String branchId) {
        if (openedBranches.contains(branchId)) {
            return this;
        }
        Set<String> next = new HashSet<>(openedBranches);
        next.add(branchId);
        return new ResearchState(capturedTargets, heldClaims, identifiedObjects, refutedCombinations, next, stageProgress);
    }

    /**
     * Marks a stage complete — design/astra-atlas-s3c-completion.md §2.1's advance step.
     *
     * <p>The one mutator with a compound rule, stated once here: <strong>held iff final</strong>.
     * Completing the claim's last stage is what holding means, so {@code heldClaims} is written
     * in the same breath as the stage record — never separately, never derivable differently by
     * two readers. For a one-stage claim this is exactly the old {@link #withHeld}: stage 0 done
     * and held, which is why no migration exists (astra-atlas-s3-progression.md §2.3).
     */
    public ResearchState withStageComplete(String claimId, int stageIndex, int totalStages) {
        if (stageOf(claimId) >= stageIndex + 1) {
            return this;
        }
        Map<String, Integer> nextStages = new HashMap<>(stageProgress);
        nextStages.put(claimId, stageIndex + 1);
        boolean finalStage = stageIndex + 1 >= totalStages;
        return new ResearchState(capturedTargets,
                finalStage ? plus(heldClaims, claimId) : heldClaims,
                identifiedObjects, refutedCombinations, openedBranches,
                nextStages);
    }

    /** Same as {@link #withStageComplete} for the resolve path: mark stage 0, hold iff one-stage. */
    public ResearchState withHeldIfFinalStage(String claimId, int totalStages, int stageIndex) {
        return withStageComplete(claimId, stageIndex, totalStages);
    }

    private static Set<String> plus(Set<String> set, String value) {
        Set<String> next = new HashSet<>(set);
        next.add(value);
        return next;
    }
}
