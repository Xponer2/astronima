package play.xponer.astronima.sim.magic;

/**
 * A claim's own state relative to one player, the fact design/astra-atlas-redesign-v2.md §4.1's
 * left rail actually draws — adapted from Thaumcraft's own teal/amber/violet/dark reading (§1 of
 * that document): colour and shape there encode the player's *relationship* to a node, not the
 * node's own category, and this is that same relationship for a claim instead of an object.
 *
 * <p>Minecraft-free (rule 1): a pure function of a claim's required evidence against one
 * {@link ResearchState}, nothing else.
 */
public enum ClaimProgress {
    /** Nothing this claim needs has been captured yet — Thaumcraft's "dark, unknown". */
    UNSTARTED,
    /** Some evidence captured, not all of it identified yet — Thaumcraft's "violet, blocked". */
    PARTIAL,
    /** Every requirement of the claim's current stage is met — Thaumcraft's "amber, ready". */
    READY,
    /** Already fully completed — Thaumcraft's "teal, done". */
    HELD;

    public static ClaimProgress of(Research.Claim claim, ResearchState research) {
        if (research.holds(claim.id())) {
            return HELD;
        }
        // Stages (design/astra-atlas-s3-progression.md section 2.3): advancing-now is a
        // statement about the claim's CURRENT stage, not about the whole evidence set - a
        // claim mid-stages whose next stage wants a filter the player lacks is violet
        // (blocked on the next gate), not an amber promise the verb cannot keep. READY
        // means the current stage's requirements are met; the pane's stage rows name which
        // stage that is. Inventory answers "carries nothing" here: the atlas's rows
        // evaluate again with the real inventory before any Complete press.
        ResearchStage stage = claim.stage(research.stageOf(claim.id()));
        if (stage != null && StageEvaluation.of(stage, research, s -> 0).allMet()) {
            return READY;
        }
        boolean anyCaptured = false;
        for (String objectId : claim.requiredEvidence()) {
            if (research.captured(objectId)) {
                anyCaptured = true;
            }
        }
        return anyCaptured ? PARTIAL : UNSTARTED;
    }
}
