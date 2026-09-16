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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import play.xponer.astronima.progression.Unlocks;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.magic.Claims;
import play.xponer.astronima.sim.magic.Research;
import play.xponer.astronima.sim.magic.ResearchState;
import play.xponer.astronima.sim.magic.ResearchStage;
import play.xponer.astronima.sim.magic.StageCompletion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Complete this claim's current stage" — the atlas's Complete action
 * (design/astra-atlas-s3c-completion.md §2.1). The server re-derives the stage index from the
 * saved attachment and re-checks every requirement against the real inventory; a client that
 * sends a stale or future index is refused with the current stage named, and a refused hand-in
 * consumes nothing (rule 18: the refusal is the loudest thing on screen).
 *
 * <p>Why the index is checked rather than corrected: a future index is a client that completed
 * stages it cannot see, and the honest answer is the refusal, not silent progress. The same
 * reasoning {@code ClaimResolvePayload} already states for why the server decides.
 */
public record ClaimStageCompletePayload(String claimId, int stageIndex)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClaimStageCompletePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("astronima", "claim_stage_complete"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimStageCompletePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeUtf(payload.claimId);
                        buffer.writeVarInt(payload.stageIndex);
                    },
                    buffer -> new ClaimStageCompletePayload(buffer.readUtf(), buffer.readVarInt()));

    public static void apply(ClaimStageCompletePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        handle(player, payload.claimId(), payload.stageIndex());
    }

    /**
     * The verb itself, shared by the packet and the gametest harness (rule 13 — one door). The
     * gametest drives this directly with a real server player and a real inventory, exactly as
     * the packet would.
     */
    public static void handle(net.minecraft.world.entity.player.Player player, String claimId, int requestedStageIndex) {
        Research.Claim claim = findClaim(claimId);
        if (claim == null) {
            return;
        }
        ResearchState before = player.getData(ModAttachments.RESEARCH.get());

        var outcome = StageCompletion.attempt(claim, requestedStageIndex, before,
                inventoryOfServer(player));
        switch (outcome) {
            case StageCompletion.Outcome.AlreadyDone done -> announceAlready(player, claim, done);
            case StageCompletion.Outcome.Refused refused ->
                    refuse(player, claim, refused.evaluation());
            case StageCompletion.Outcome.Held held ->
                    completeStage(player, claim, held.grants());
            case StageCompletion.Outcome.Advanced advanced -> {
                completeStage(player, claim, advanced.grants());
                player.sendSystemMessage(Component.literal(
                                "Stage " + advanced.nextStageIndex() + " of " + claim.id()
                                        .replace('_', ' ') + " — check what it asks for next")
                        .withStyle(ChatFormatting.AQUA));
            }
        }
    }

    /**
     * One completion path for both verbs — the Complete button <em>and</em> the stroke click
     * (design/astra-atlas-s3c-completion.md §2.2): consumption, stage record, grants and
     * narration happen here and nowhere else. A stage whose last row is a hand-in cannot be
     * completed through either door without paying, and cannot be completed twice — {@code
     * StageCompletion.attempt} has already refused the second press before this runs.
     * Consumption happens only after every check passed — never before (rule 18's premise).
     */
    static void completeStage(net.minecraft.world.entity.player.Player player, Research.Claim claim,
                              List<String> grants) {
        ResearchState before = player.getData(ModAttachments.RESEARCH.get());
        int current = before.stageOf(claim.id());
        ResearchStage stage = claim.stages().get(Math.min(current, claim.stages().size() - 1));
        for (var entry : StageCompletion.consumptionOf(stage).entrySet()) {
            removeFromInventory(player, entry.getKey(), entry.getValue());
        }

        // Stage progress + held-if-final is one mutator (design/astra-atlas-s3-progression.md
        // §2.3); grants go to the other attachment in the same breath.
        ResearchState after = before.withStageComplete(claim.id(), current, claim.stages().size());
        player.setData(ModAttachments.RESEARCH.get(), after);

        Unlocks unlocks = player.getData(ModAttachments.UNLOCKS.get());
        Unlocks next = unlocks;
        for (String grant : grants) {
            next = next.with(grant);
        }
        if (next != unlocks) {
            player.setData(ModAttachments.UNLOCKS.get(), next);
        }

        boolean held = after.holds(claim.id());
        player.sendSystemMessage(Component.literal(
                        held ? "Held: " + claim.id() + " — what it unlocks is now readable"
                             : "Stage " + after.stageOf(claim.id()) + " of " + claim.id() + " complete")
                .withStyle(ChatFormatting.GREEN));
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    held ? SoundEvents.PLAYER_LEVELUP : SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.6f, held ? 1.4f : 1.2f);
        }
    }

    /**
     * Walks the player's own inventory and shrinks stacks until {@code count} is removed.
     * {@code Inventory#getItem(int)} covers main + equipment + offhand uniformly (the decode
     * payload's own precedent), so a filter in the offhand counts for {@code Owns} — and is
     * equally visible to this walk, which is why the honest-failure message names what the
     * scan covers rather than claiming "not carrying".
     */
    private static void removeFromInventory(net.minecraft.world.entity.player.Player player, String itemId, int count) {
        Item item = BuiltInRegistries.ITEM.getOptional(
                Identifier.parse(itemId.toLowerCase(Locale.ROOT))).orElse(null);
        if (item == null) {
            // The coverage test owns this never happening; if it does, say so rather than
            // silently consuming nothing.
            player.sendSystemMessage(Component.literal(
                    "Internal: no item registered as " + itemId).withStyle(ChatFormatting.RED));
            return;
        }
        var inventory = player.getInventory();
        int remaining = count;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                int taken = Math.min(stack.getCount(), remaining);
                stack.shrink(taken);
                remaining -= taken;
            }
        }
    }

    private static void refuse(net.minecraft.world.entity.player.Player player, Research.Claim claim,
                               play.xponer.astronima.sim.magic.StageEvaluation evaluation) {
        ResearchState state = player.getData(ModAttachments.RESEARCH.get());
        int current = state.stageOf(claim.id());
        StringBuilder message = new StringBuilder("Stage ")
                .append(current + 1).append(" of ").append(claim.id()).append(" is not met yet:");
        for (var row : evaluation.rows()) {
            message.append("\n  - ").append(describe(row));
        }
        player.sendSystemMessage(Component.literal(message.toString())
                .withStyle(ChatFormatting.RED));
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, 1.0f);
    }

    /** Shared with the stroke-click payload, which narrates the same rows. */
    static String describe(play.xponer.astronima.sim.magic.StageEvaluation.Row row) {
        if (row.requirement() instanceof play.xponer.astronima.sim.magic.Requirement.Identified id) {
            return "identify " + id.objectId().replace('_', ' ');
        }
        if (row.requirement() instanceof play.xponer.astronima.sim.magic.Requirement.Holds holds) {
            return "hold the claim " + holds.claimId();
        }
        if (row.requirement() instanceof play.xponer.astronima.sim.magic.Requirement.Owns owns) {
            return "carry " + shortItem(owns.itemId()) + " x" + owns.count()
                    + " (have " + row.have() + ")";
        }
        if (row.requirement() instanceof play.xponer.astronima.sim.magic.Requirement.HandsIn handsIn) {
            return "hand in " + shortItem(handsIn.itemId()) + " x" + handsIn.count()
                    + " (have " + row.have() + ")";
        }
        return "unmet requirement";
    }

    private static String shortItem(String itemId) {
        String bare = itemId.substring(itemId.indexOf(':') + 1);
        return bare.replace('_', ' ');
    }

    private static void announceAlready(net.minecraft.world.entity.player.Player player, Research.Claim claim,
                                        StageCompletion.Outcome.AlreadyDone done) {
        player.sendSystemMessage(Component.literal(claim.id() + " is already held")
                .withStyle(ChatFormatting.GRAY));
    }

    /** The seam StageCompletion's pure core reads through. Shared with the stroke-click payload. */
    public static play.xponer.astronima.sim.magic.InventoryView inventoryOfServer(
            net.minecraft.world.entity.player.Player player) {
        var inventory = player.getInventory();
        return itemId -> {
            Item item = BuiltInRegistries.ITEM.getOptional(
                    Identifier.parse(itemId.toLowerCase(Locale.ROOT))).orElse(null);
            if (item == null) {
                return 0;
            }
            int count = 0;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty() && stack.getItem() == item) {
                    count += stack.getCount();
                }
            }
            return count;
        };
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
