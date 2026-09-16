package play.xponer.astronima.network;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.magic.Claims;
import play.xponer.astronima.sim.magic.Research;
import play.xponer.astronima.sim.magic.ResearchState;
import play.xponer.astronima.sim.magic.ResearchStage;
import play.xponer.astronima.sim.magic.StageCompletion;
import play.xponer.astronima.sim.magic.StageCompletion.Outcome.AlreadyDone;
import play.xponer.astronima.sim.magic.StageCompletion.Outcome.Advanced;
import play.xponer.astronima.sim.magic.StageCompletion.Outcome.Held;
import play.xponer.astronima.sim.magic.StageCompletion.Outcome.Refused;
import play.xponer.astronima.sim.magic.StageEvaluation;

/**
 * "Try to resolve this claim from whatever I've actually identified" — the atlas's click on a
 * claim stroke (design/astra-research-m4a.md). Reads {@code ResearchState.identifiedObjects()}
 * (design/astra-research-m4b.md §3 — a bare capture is not evidence on its own; {@code
 * SpectrumDecodeAttemptPayload} is the only door that turns a capture into an identification).
 * The server decides, never the client: a client that could send its own held/refuted verdict
 * could send one it never actually earned.
 *
 * <p><strong>Stages (design/astra-atlas-s3-progression.md §2.3):</strong> a stroke click is now
 * a <em>stage attempt</em>, not a whole-claim verdict — a three-stage claim's resolve completes
 * stage 1 and says so, rather than announcing "Held" for a claim that still has stages open.
 * The truth lives in {@link StageCompletion}, the same pure core the Complete button's {@link
 * ClaimStageCompletePayload} runs; this payload's remaining job is deriving the offered
 * evidence from identified objects, which is what made stroke-click "resolve from what you
 * decoded" rather than a separate verb. The stage index the server trusts is the one it derives
 * itself from the attachment — never the packet's.
 */
public record ClaimResolvePayload(String claimId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClaimResolvePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("astronima", "claim_resolve"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimResolvePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeUtf(payload.claimId),
                    buffer -> new ClaimResolvePayload(buffer.readUtf()));

    public static void apply(ClaimResolvePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Research.Claim claim = findClaim(payload.claimId());
        if (claim == null) {
            return;
        }
        ResearchState before = player.getData(ModAttachments.RESEARCH.get());
        int current = before.stageOf(claim.id());
        ResearchStage stage = claim.stage(current);
        if (stage == null) {
            return;
        }
        var outcome = StageCompletion.attempt(claim, current, before,
                ClaimStageCompletePayload.inventoryOfServer(player));
        switch (outcome) {
            case AlreadyDone already -> announceAlready(player, claim);
            case Refused refused ->
                    refuse(player, claim, Claims.evidenceFor(claim, before.identifiedObjects()),
                            refused.evaluation());
            // Both doors run the one completion path: consumption, stage record and grants are
            // ClaimStageCompletePayload.completeStage's job, so the stroke click pays exactly
            // what the Complete button pays (s3c-completion section 2.2's one-path rule).
            case Held held -> ClaimStageCompletePayload.completeStage(player, claim, held.grants());
            case Advanced advanced -> {
                ClaimStageCompletePayload.completeStage(player, claim, advanced.grants());
                player.sendSystemMessage(Component.literal(
                                "Stage " + advanced.nextStageIndex() + " of "
                                        + claim.id().replace('_', ' ')
                                        + " — read what it asks for next")
                        .withStyle(ChatFormatting.AQUA));
            }
        }
    }

    private static void announceAlready(ServerPlayer player, Research.Claim claim) {
        player.sendSystemMessage(Component.literal(claim.id() + " is already held")
                .withStyle(ChatFormatting.GRAY));
    }

    /** Rule 18, kept from the pre-stage payload: the refusal names exactly what is short —
     *  evidence rows first (the stroke's own promise), then the stage's other rows. */
    private static void refuse(ServerPlayer player, Research.Claim claim,
                               java.util.Set<String> offered, StageEvaluation evaluation) {
        boolean evidenceShort = !offered.containsAll(claim.requiredEvidence());
        String message;
        if (evidenceShort) {
            String missing = String.join(", ", claim.requiredEvidence().stream()
                    .filter(id -> !offered.contains(id)).sorted().toList());
            message = "Doesn't hold — still haven't identified: "
                    + (missing.isEmpty() ? "?" : missing);
        } else {
            StringBuilder builder = new StringBuilder("Stage ")
                    .append(player.getData(ModAttachments.RESEARCH.get()).stageOf(claim.id()) + 1)
                    .append(" of ").append(claim.id()).append(" is not met yet:");
            for (StageEvaluation.Row row : evaluation.rows()) {
                builder.append("\n  - ").append(ClaimStageCompletePayload.describe(row));
            }
            message = builder.toString();
        }
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, 1.0f);
        }
    }

    private static Research.Claim findClaim(String claimId) {
        for (Research.Claim claim : Claims.ALL) {
            if (claim.id().equals(claimId)) {
                return claim;
            }
        }
        return null;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
