package play.xponer.astronima.sim.magic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The pure core of the stage-completion verb (design/astra-atlas-s3c-completion.md §2.1):
 * given the claim's current stage, the player's research and what their inventory actually
 * holds, decide whether the stage completes, and if so what it consumes and what it grants.
 *
 * <p>The <strong>server</strong> runs this against the real inventory through a
 * {@link ServerStages} adapter; a client that sends a stage index it is not on gains nothing,
 * because the index the server trusts is the one it derives itself, not the one in the packet
 * (rule 25 — a screen may draw, it must not decide).
 */
public final class StageCompletion {

    /** One outcome per shape the verb can meet — never a boolean, so every caller narrates. */
    public sealed interface Outcome {
        /** Stage already at or past this index — idempotent success, nothing consumed. */
        record AlreadyDone(int completedThrough) implements Outcome {}

        /** This stage's requirements are not met; the evaluation names which row is short. */
        record Refused(StageEvaluation evaluation) implements Outcome {}

        /** Completing the final stage — the claim holds, and this is its grants list. */
        record Held(List<String> grants) implements Outcome {}

        /** Completing a non-final stage — its own grants, and the next stage opens. */
        record Advanced(int nextStageIndex, List<String> grants) implements Outcome {}
    }

    /**
     * @param requestedStageIndex what the client asked to complete. The server checks it against
     *        the stage it derives from {@code research} — a stale or future index is refused with
     *        the current stage named, never silently corrected.
     */
    public static Outcome attempt(ClaimView claim, int requestedStageIndex,
                                  ResearchState research, InventoryView inventory) {
        int current = research.stageOf(claim.id());
        int total = claim.totalStages();
        if (current >= total) {
            return new Outcome.AlreadyDone(current);
        }
        if (requestedStageIndex != current) {
            return new Outcome.Refused(StageEvaluation.of(
                    claim.stage(current), research, inventory));
        }
        StageEvaluation evaluation = StageEvaluation.of(claim.stage(current), research, inventory);
        if (!evaluation.allMet()) {
            return new Outcome.Refused(evaluation);
        }
        // requestedStageIndex == current is already guaranteed by the check above — the server
        // never advances by the client's own index, only by the one it derived itself
        // (ResearchStageScenarios.staleStageIndexGainsNothing is what proves that guard still
        // holds). Found live: this line used to read `current = requestedStageIndex;` under a
        // comment reading "MUTATION: trust the client's index instead of the derived one" — a
        // leftover from proving that gametest could fail, left in place with a comment that read
        // as if it documented sanctioned behaviour. Deleted rather than fixed in place: there is
        // nothing for this line to do once the two are already known equal.
        List<String> grants = new ArrayList<>(evaluation.grantsIfMet());
        ResearchState advanced = research.withStageComplete(claim.id(), current, total);
        if (current + 1 >= total) {
            // heldClaims was written by withStageComplete itself (held iff final); the grants
            // are the caller's to apply — Unlocks is a separate attachment and this class stays
            // MC-free of it by reading the ids as data.
            return new Outcome.Held(grants);
        }
        return new Outcome.Advanced(current + 1, grants);
    }

    /**
     * The claim facts the verb needs, so both {@code Research.Claim} and a gametest's synthetic
     * claim can drive the same code (rule 13 — the test enters through the verb's own door).
     */
    public interface ClaimView {
        String id();

        int totalStages();

        ResearchStage stage(int index);
    }

    /** Which items this stage would consume, and how many — the hand-in rows only. */
    public static Map<String, Integer> consumptionOf(ResearchStage stage) {
        Map<String, Integer> consumption = new HashMap<>();
        for (Requirement requirement : stage.requirements()) {
            if (requirement instanceof Requirement.HandsIn handsIn) {
                consumption.merge(handsIn.itemId(), handsIn.count(), Integer::sum);
            }
        }
        return consumption;
    }

    private StageCompletion() {}
}
