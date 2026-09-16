package play.xponer.astronima.sim.magic;

import java.util.List;
import java.util.Set;

/**
 * One stage of a claim (design/astra-atlas-s3-progression.md §2.2): an ordered position in the
 * claim's sequence, what completing it requires, what it grants, and its own prose key —
 * Azanor's own schema makes per-stage text required, and so does this (a claim whose entry says
 * the same thing at stage 1 and stage 3 has failed demand 3's point).
 *
 * <p>{@code textKey} is the lang key S2c-2's per-stage prose hangs from;
 * {@code grants} are the unlock ids handed to {@code progression.Unlocks} when this stage
 * completes (S3d-1's currency — the same set {@code CodexMarkup.Block.Locked} reads, so a grant
 * needs no new presentation path).
 *
 * <p>MC-free and immutable like every {@code sim/magic} record.
 */
public record ResearchStage(int index, List<Requirement> requirements, String textKey,
                            Set<String> grants) {

    public ResearchStage {
        requirements = List.copyOf(requirements);
        grants = Set.copyOf(grants);
    }

    public static ResearchStage of(int index, List<Requirement> requirements, String textKey) {
        return new ResearchStage(index, requirements, textKey, Set.of());
    }
}
