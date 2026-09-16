package play.xponer.astronima.gametest;


import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.registries.DeferredRegister;
import play.xponer.astronima.network.ClaimStageCompletePayload;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.magic.Claims;
import play.xponer.astronima.sim.magic.Research;
import play.xponer.astronima.sim.magic.ResearchState;
import play.xponer.astronima.sim.magic.SpectralLine;

/**
 * The research stage loop, end to end on a server (design/astra-atlas-s3c-completion.md §2,
 * §4; design/astra-atlas-s3d-unlocks.md §6). Every scenario drives
 * {@link ClaimStageCompletePayload#handle} — the same handler the atlas packet runs, so the
 * test enters through the player's own door (rule 13) — and asserts on what a player would
 * see: the stage record in their attachment, the grant arriving, the plates gone.
 *
 * <p>Also carries the research save fixture (rule 5/60, rule 14): the stage_progress codec
 * field post-dates every existing save, and the plain JUnit runner cannot load the codec's
 * class ({@code NoClassDefFoundError: MappedRegistry}, hit for real), so the round-trip
 * assertion lives here where the real serialization runs.
 */
public final class ResearchStageScenarios {
    /** Classloading anchor: registrations run here, per the WireScenarios pattern. */
    public static void init() {}

    @SuppressWarnings("unused")
    private static final Object STAGE2_NEEDS_THE_FILTER =
            PlayerScenarios.SCENARIOS.register("scenario_claim_stage2_needs_the_filter",
                    () -> ResearchStageScenarios::claimStage2NeedsTheFilter);

    @SuppressWarnings("unused")
    private static final Object STAGE3_CONSUMES_THE_PLATES =
            PlayerScenarios.SCENARIOS.register("scenario_claim_stage3_consumes_the_plates",
                    () -> ResearchStageScenarios::claimStage3ConsumesThePlates);

    @SuppressWarnings("unused")
    private static final Object STALE_STAGE_INDEX_GAINS_NOTHING =
            PlayerScenarios.SCENARIOS.register("scenario_claim_stale_stage_index_gains_nothing",
                    () -> ResearchStageScenarios::staleStageIndexGainsNothing);

    @SuppressWarnings("unused")
    private static final Object SAVE_FIXTURE_STAGE_PROGRESS =
            PlayerScenarios.SCENARIOS.register("scenario_research_stage_save_fixture",
                    () -> ResearchStageScenarios::saveFixture);

    /** Identifies the claim's two evidence objects through the state's own mutator. */
    private static ResearchState identifyEvidence(Research.Claim claim, ResearchState state) {
        ResearchState next = state;
        for (String objectId : claim.requiredEvidence()) {
            next = next.withIdentified(objectId);
        }
        return next;
    }

    private static net.minecraft.world.entity.player.Player playerOf(GameTestHelper helper) {
        return helper.makeMockPlayer(GameType.SURVIVAL);
    }

    /** Stage 2 (own the branch's filters) refuses while the player carries nothing, then completes. */
    private static void claimStage2NeedsTheFilter(GameTestHelper helper) {
        var player = playerOf(helper);
        Research.Claim claim = Claims.SAME_ELEMENTS;
        player.setData(ModAttachments.RESEARCH.get(),
                identifyEvidence(claim, ResearchState.NONE)
                        .withStageComplete(claim.id(), 0, claim.totalStages()));

        helper.runAfterDelay(2, () -> {
            // Press Complete with an empty inventory: the filter stage must refuse.
            ClaimStageCompletePayload.handle(player, claim.id(), 1);
            ResearchState after = player.getData(ModAttachments.RESEARCH.get());
            if (after.stageOf(claim.id()) != 1) {
                helper.fail("completing stage 2 without the filters advanced the claim — a"
                        + " refused hand-in changed state, which is the duplication bug");
                return;
            }

            // Carry the filter and press again: it completes.
            player.getInventory().add(new ItemStack(ModItems.filterToken(SpectralLine.HELIUM_I)));
            player.getInventory().add(new ItemStack(ModItems.filterToken(SpectralLine.FORBIDDEN_OIII)));
            ClaimStageCompletePayload.handle(player, claim.id(), 1);
            ResearchState done = player.getData(ModAttachments.RESEARCH.get());
            if (done.stageOf(claim.id()) != 2) {
                helper.fail("carrying the filter still did not complete stage 2 — the"
                        + " inventory walk does not see the item it asked for");
                return;
            }
            helper.succeed();
        });
    }

    /** Stage 3 hands in two plates: they leave the inventory, the claim holds, the grant lands. */
    private static void claimStage3ConsumesThePlates(GameTestHelper helper) {
        var player = playerOf(helper);
        Research.Claim claim = Claims.METAL_ASSAY;
        ResearchState staged = identifyEvidence(claim, ResearchState.NONE)
                .withStageComplete(claim.id(), 0, claim.totalStages())
                .withStageComplete(claim.id(), 1, claim.totalStages());
        player.setData(ModAttachments.RESEARCH.get(), staged);
        player.getInventory().add(new ItemStack(ModItems.SPECTRAL_PLATE.get(), 2));

        helper.runAfterDelay(2, () -> {
            int before = count(player, ModItems.SPECTRAL_PLATE.get());
            if (before != 2) {
                helper.fail("setup failed: the plates are not in the inventory the walk reads");
                return;
            }
            ClaimStageCompletePayload.handle(player, claim.id(), 2);

            ResearchState after = player.getData(ModAttachments.RESEARCH.get());
            if (!after.holds(claim.id())) {
                helper.fail("the final stage completed without the claim holding - held"
                        + " and stageProgress disagreed, the two-reader bug");
                return;
            }
            if (count(player, ModItems.SPECTRAL_PLATE.get()) != 0) {
                helper.fail("the plates survived a hand-in that completed - nothing was"
                        + " consumed, so the cost was a label");
                return;
            }
            if (!player.getData(ModAttachments.UNLOCKS.get())
                    .has("research:" + claim.id())) {
                helper.fail("holding " + claim.id() + " granted nothing - the claim's"
                        + " unlock did not land, so demand 2's answer is still empty");
                return;
            }
            helper.succeed();
        });
    }

    /** A stale or future stage index must gain nothing — the server derives the index itself.
     *  The player carries everything both the current and the requested stage want, so the
     *  index check is the ONLY thing standing between this press and a skipped stage — which is
     *  what makes this scenario able to fail (rule 12: a test saved by a second check proves
     *  nothing about the first one). */
    private static void staleStageIndexGainsNothing(GameTestHelper helper) {
        var player = playerOf(helper);
        Research.Claim claim = Claims.SAME_ELEMENTS;
        ResearchState staged = identifyEvidence(claim, ResearchState.NONE)
                .withStageComplete(claim.id(), 0, claim.totalStages());
        player.setData(ModAttachments.RESEARCH.get(), staged);
        player.getInventory().add(new ItemStack(ModItems.SPECTRAL_PLATE.get(), 8));
        player.getInventory().add(new ItemStack(ModItems.filterToken(SpectralLine.HELIUM_I)));
        player.getInventory().add(new ItemStack(ModItems.filterToken(SpectralLine.FORBIDDEN_OIII)));

        helper.runAfterDelay(2, () -> {
            // The client asks for stage 3 (index 2) while the claim is on stage 2 (index 1).
            ClaimStageCompletePayload.handle(player, claim.id(), 2);
            ResearchState after = player.getData(ModAttachments.RESEARCH.get());
            if (after.stageOf(claim.id()) != 1) {
                helper.fail("a future stage index advanced the claim - the server trusted"
                        + " the client's index, which is the duplication door");
                return;
            }
            if (count(player, ModItems.SPECTRAL_PLATE.get()) != 8) {
                helper.fail("a refused completion consumed plates anyway");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * The save fixture, moved here by rule 14: a pre-stage save must load at stage 0 with
     * everything else intact, and a post-stage state must round-trip the codec unchanged.
     * Runs against the real attachment codec on a real (game) thread, where
     * {@code com.mojang.serialization} and {@code net.minecraft} both exist.
     */
    private static void saveFixture(GameTestHelper helper) {
        var player = playerOf(helper);
        helper.runAfterDelay(2, () -> {
            var codec = ModAttachments.RESEARCH_CODEC;

            // The pre-stage save: the shape every existing player file carries.
            String preStage = """
                    {
                      "captured_targets": ["orion_nebula", "helix_nebula"],
                      "held_claims": ["same_elements"],
                      "identified_objects": ["orion_nebula", "helix_nebula"],
                      "refuted_combinations": ["same_elements|helix_nebula"]
                    }""";
            ResearchState decoded = codec.parse(
                    com.mojang.serialization.JsonOps.INSTANCE,
                    com.google.gson.JsonParser.parseString(preStage)).getOrThrow();
            if (!decoded.holds("same_elements") || !decoded.identified("orion_nebula")
                    || decoded.stageOf("same_elements") != 0) {
                helper.fail("a pre-stage save did not load: held=" + decoded.holds("same_elements")
                        + " stage=" + decoded.stageOf("same_elements"));
                return;
            }

            // A post-stage state round-trips byte-honest through the same codec.
            ResearchState advanced = decoded
                    .withStageComplete("same_elements", 1, Claims.SAME_ELEMENTS.totalStages());
            String written = codec.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,
                    advanced).getOrThrow().toString();
            ResearchState reloaded = codec.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    com.google.gson.JsonParser.parseString(written)).getOrThrow();
            if (!reloaded.equals(advanced) || reloaded.stageOf("same_elements") != 2) {
                helper.fail("stage progress did not survive a save round-trip: wrote "
                        + written);
                return;
            }
            helper.succeed();
        });
    }

    private static int count(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.Item item) {
        int total = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                total += inventory.getItem(slot).getCount();
            }
        }
        return total;
    }

    private ResearchStageScenarios() {}
}
