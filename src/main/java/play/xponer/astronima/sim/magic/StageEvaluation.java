package play.xponer.astronima.sim.magic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One row of a stage's evaluation — the fact the atlas's requirement row and its tick draw
 * (design/astra-atlas-s2-entry.md §3.3's generated stage rows read exactly this).
 *
 * <p>MC-free: a pure result record, the S3a model's output shape.
 */
public record StageEvaluation(ResearchStage stage, int nextStageIndex,
                              List<Row> rows, boolean allMet) {

    /** One requirement's answer: what it wants, how much of it is already there. */
    public record Row(Requirement requirement, boolean met, int have, int wanted) {}

    private static final StageEvaluation EMPTY =
            new StageEvaluation(null, -1, List.of(), true);

    /** Every requirement of this stage, evaluated against the player's own state. */
    public static StageEvaluation of(ResearchStage stage, ResearchState research,
                                     InventoryView inventory) {
        if (stage == null) {
            return EMPTY;
        }
        List<Row> rows = new ArrayList<>(stage.requirements().size());
        boolean allMet = true;
        for (Requirement requirement : stage.requirements()) {
            Row row = evaluate(requirement, research, inventory);
            rows.add(row);
            allMet &= row.met();
        }
        return new StageEvaluation(stage, stage.index() + 1, rows, allMet);
    }

    private static Row evaluate(Requirement requirement, ResearchState research,
                                InventoryView inventory) {
        if (requirement instanceof Requirement.Identified id) {
            return new Row(requirement, research.identified(id.objectId()), 
                    research.identified(id.objectId()) ? 1 : 0, 1);
        }
        if (requirement instanceof Requirement.Holds holds) {
            return new Row(requirement, research.holds(holds.claimId()),
                    research.holds(holds.claimId()) ? 1 : 0, 1);
        }
        if (requirement instanceof Requirement.Owns owns) {
            int have = inventory.countOf(owns.itemId());
            return new Row(requirement, have >= owns.count(), have, owns.count());
        }
        if (requirement instanceof Requirement.HandsIn handsIn) {
            int have = inventory.countOf(handsIn.itemId());
            return new Row(requirement, have >= handsIn.count(), have, handsIn.count());
        }
        // A sealed interface with these four kinds cannot reach here, but rule 90 prefers the
        // statement shape that cannot lie about exhaustiveness over an unreachable throw.
        throw new IllegalStateException("unhandled requirement kind: " + requirement.getClass());
    }

    /** The stage's grants, consumable only when every requirement is met. */
    public List<String> grantsIfMet() {
        return allMet ? stage.grants().stream().sorted().toList() : List.of();
    }

    /** Per-claim highest completed stage index — the map shape {@code ResearchState.stageProgress}
     *  carries. Utility for the completion verb's advance step. */
    public static Map<String, Integer> advanced(Map<String, Integer> stageProgress,
                                                String claimId, int completedStage) {
        Map<String, Integer> next = new HashMap<>(stageProgress);
        next.merge(claimId, completedStage, Math::max);
        return next;
    }
}
