package play.xponer.astronima.sim.magic;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The verb — laying evidence on a claim and resolving it (design/astra-research.md §4).
 *
 * <p>A {@link Claim} is passed in, not looked up from a registry: where real claims live as data
 * is astra-research.md §14's open question 5, deliberately left open by
 * design/astra-research-m1.md — M1 ships the mechanism the first real claims (M4) will run on,
 * not the claims themselves.
 */
public final class Research {

    /**
     * A piece of understanding: its id, and an ordered sequence of stages — design/
     * astra-atlas-s3-progression.md §2.2. A stage names requirements, per-stage prose and
     * grants; stage 1 of every claim is the historical single resolve, so a one-stage claim
     * behaves exactly as the pre-stage binary did (§2.3 of that leaf: zero migration, every
     * existing consumer unchanged).
     *
     * <p>Two constructors by design: the two-argument one is the legacy binary shape (kept so
     * the two real claims need no edit while their stage tables land), and {@link #staged}
     * builds the real sequence.
     */
    public record Claim(String id, Set<String> requiredEvidence,
                        List<ResearchStage> stages) implements StageCompletion.ClaimView {
        public Claim {
            requiredEvidence = Set.copyOf(requiredEvidence);
            stages = List.copyOf(stages);
        }

        @Override public int totalStages() {
            return stages.size();
        }

        @Override public ResearchStage stage(int index) {
            return stages.get(index);
        }

        /** The legacy binary shape: one stage wanting exactly this evidence, no grants. */
        public Claim(String id, Set<String> requiredEvidence) {
            this(id, requiredEvidence, List.of(ResearchStage.of(0,
                    requiredEvidence.stream().map(Requirement.Identified::new)
                            .collect(Collectors.toUnmodifiableList()),
                    "astronima.research." + id + ".stage0")));
        }

        public static Claim staged(String id, List<ResearchStage> stages) {
            if (stages.isEmpty()) {
                throw new IllegalArgumentException(
                        "claim " + id + " declares no stages — a malformed claim, not an empty one");
            }
            Set<String> evidence = new java.util.LinkedHashSet<>();
            for (ResearchStage stage : stages) {
                for (Requirement requirement : stage.requirements()) {
                    if (requirement instanceof Requirement.Identified id2) {
                        evidence.add(id2.objectId());
                    }
                }
            }
            return new Claim(id, Set.copyOf(evidence), stages);
        }
    }

    /** {@code held}: did this resolve succeed. {@code next}: the state after it, whichever way. */
    public record Resolution(boolean held, ResearchState next) {
    }

    /**
     * Lays {@code offeredEvidence} on {@code claim}'s figure and presses Resolve.
     *
     * <p>Already held is a no-op that reports held — resolving a claim a second time neither
     * re-refutes it nor re-consumes anything. Otherwise: the offered evidence must equal the
     * claim's required evidence exactly (§4's figure has exactly as many vertices as the claim
     * needs; more evidence than asked for is not "generous", it is the wrong shape) or nothing is
     * consumed and the exact combination is recorded as refuted (§4.1 — no penalty beyond that,
     * and the mark never disappears).
     *
     * <p>Stages (design/astra-atlas-s3-progression.md §2.3): on success, stage 1 is recorded in
     * {@code stageProgress} — and the claim marks held only if one stage was its last, so the
     * meaning of {@code heldClaims} stays "fully completed" for every claim, old or new.
     */
    public static Resolution resolve(ResearchState state, Claim claim, Set<String> offeredEvidence) {
        if (state.holds(claim.id())) {
            return new Resolution(true, state);
        }
        if (!offeredEvidence.equals(claim.requiredEvidence())) {
            return new Resolution(false,
                    state.withRefuted(combinationKey(claim.id(), offeredEvidence)));
        }
        ResearchState advanced = state.withHeldIfFinalStage(claim.id(), claim.stages().size(), 0);
        return new Resolution(true, advanced);
    }

    /** Whether this exact (claim, evidence) pairing was already tried and failed. */
    public static boolean wasRefuted(ResearchState state, String claimId, Set<String> offeredEvidence) {
        return state.wasRefuted(combinationKey(claimId, offeredEvidence));
    }

    private static String combinationKey(String claimId, Set<String> evidence) {
        return claimId + "|" + evidence.stream().sorted().collect(Collectors.joining(","));
    }

    private Research() {}
}
