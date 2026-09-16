package play.xponer.astronima.datagen;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.criterion.InventoryChangeTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.advancements.AdvancementSubProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.advancement.AirlockTrigger;
import play.xponer.astronima.advancement.HabitatTrigger;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.logic.PartType;

import java.util.function.Consumer;

/**
 * The tree that tells a player what there is to do.
 *
 * <p>Written because the honest report on the mod was "there is nothing to do", and
 * that was a discoverability problem rather than a content problem: a gas simulation,
 * a six-part suit, three machines and an ore chain existed, and the game mentioned none
 * of them. A game with hidden content is indistinguishable from a game with none.
 *
 * <p>Branches from one root, deliberately not sequential — the suit, the habitat, the
 * rock (which folds in the metal it becomes), the halogens, the archive, the gas loop,
 * power, cryogenics, and radiation are what this mod actually is, and a player can push
 * any of them. Nothing here gates anything; the tree describes, it does not permit.
 * Every branch past the original three was added once its tier existed —
 * {@code design/progression.md} §6/§7 name each gap directly rather than letting a
 * newer tier silently stay invisible the same way the whole tree was written to fix in
 * the first place.
 *
 * <p>Descriptions say why a step matters rather than restating what was done. "You
 * crafted a hammer stone" is a notification; "tools from rock, because there is no fire
 * here to make anything else" is the reason the step exists.
 */
public class ModAdvancements implements AdvancementSubProvider {
    @Override
    public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> output) {
        AdvancementHolder root = Advancement.Builder.advancement()
                .display(ModItems.EVA_SUIT.get(),
                        title("Sole Survivor"),
                        describe("You woke up. The rock did not care either way."),
                        Identifier.fromNamespaceAndPath(Astronima.MODID, "textures/block/asteroid_rock.png"),
                        AdvancementType.TASK, false, false, false)
                .addCriterion("has_suit", hasItem(ModItems.EVA_SUIT.get()))
                .save(output, id("root"));

        suitBranch(output, root);
        habitatBranch(output, root);
        rockBranch(output, root);
        halogensBranch(output, root);
        archiveBranch(output, root);
        gasLoopBranch(output, root);
        powerBranch(output, root);
        cryogenicsBranch(output, root);
        radiationBranch(output, root);
    }

    /** Staying alive outside. */
    private void suitBranch(Consumer<AdvancementHolder> output, AdvancementHolder root) {
        AdvancementHolder repaired = child(output, root, "field_repair",
                ModItems.SEALANT_PATCH.get(), "Field Repair",
                "The suit came out of the wreck broken in six ways. Each one is fixed "
                        + "separately, and each gives something back.",
                AdvancementType.TASK, hasItem(ModItems.SEALANT_PATCH.get()));

        child(output, repaired, "pressure_tested",
                ModItems.OXYGEN_TANK.get(), "Pressure Tested",
                "A sealed helmet and a working tank mount. Either one alone is still a "
                        + "suit you die in.",
                AdvancementType.GOAL, hasItem(ModItems.OXYGEN_TANK.get()));
    }

    /**
     * Staying alive inside — the branch that is checked against the world rather than
     * the inventory, because building a room that holds air is the accomplishment.
     */
    private void habitatBranch(Consumer<AdvancementHolder> output, AdvancementHolder root) {
        AdvancementHolder sealed = Advancement.Builder.advancement()
                .parent(root)
                .display(ModBlocks.HULL_PLATE.get(),
                        title("Airtight"),
                        describe("A room that actually holds. The analyzer will tell you "
                                + "whether it does; guessing will not."),
                        null, AdvancementType.TASK, true, true, false)
                .addCriterion("sealed_room",
                        HabitatTrigger.TriggerInstance.reached(HabitatTrigger.Standard.SHELTER))
                .save(output, id("airtight"));

        AdvancementHolder breathing = Advancement.Builder.advancement()
                .parent(sealed)
                .display(ModBlocks.OXYGEN_CANDLE.get(),
                        title("First Breath"),
                        describe("Air you made, at a pressure that keeps your blood liquid. "
                                + "You can take the helmet off in here."),
                        null, AdvancementType.GOAL, true, true, false)
                .addCriterion("breathable_room",
                        HabitatTrigger.TriggerInstance.reached(HabitatTrigger.Standard.BREATHABLE))
                .save(output, id("first_breath"));

        child(output, breathing, "closed_loop",
                ModBlocks.SCRUBBER.get(), "Closed Loop",
                "Oxygen is only half of it. You exhale carbon dioxide into a sealed room "
                        + "and it stays there until something removes it.",
                AdvancementType.GOAL, hasItem(ModBlocks.SCRUBBER.get()));

        // The airlock leg of the habitat branch: from holding air to leaving without
        // losing it. Both steps are checked against a machine that actually ran, not an
        // item held, because building the airlock is the accomplishment.
        // Commissioning is its own step because it is its own job — and the one a player
        // is most likely to stop halfway through, since a panel with three of four
        // terminals filled does nothing whatsoever.
        AdvancementHolder commissioned = Advancement.Builder.advancement()
                .parent(sealed)
                .display(ModItems.WRENCH.get(),
                        title("Commissioned"),
                        describe("Four terminals, four devices, all of them checked out. "
                                + "You told the panel which door is the outer one instead "
                                + "of hoping it would work that out — which is what "
                                + "commissioning a control panel actually is."),
                        null, AdvancementType.TASK, true, true, false)
                .addCriterion("commissioned", AirlockTrigger.TriggerInstance.completed(
                        AirlockTrigger.Moment.COMMISSIONED))
                .save(output, id("commissioned"));

        AdvancementHolder cycled = Advancement.Builder.advancement()
                .parent(commissioned)
                .display(ModBlocks.AIRLOCK_CONTROLLER.get(),
                        title("Depressurise"),
                        describe("The chamber pumped down into your tank and the outer door "
                                + "released. You left without venting the habitat — the air "
                                + "is in the bottle, not in space."),
                        null, AdvancementType.GOAL, true, true, false)
                .addCriterion("cycled",
                        AirlockTrigger.TriggerInstance.completed(AirlockTrigger.Moment.CYCLED))
                .save(output, id("depressurise"));

        Advancement.Builder.advancement()
                .parent(cycled)
                .display(ModItems.OXYGEN_TANK.get(),
                        title("Come Home on Stored Air"),
                        describe("The chamber refilled from the tank and the inner door "
                                + "opened. This is the moment the bottle you filled pays for "
                                + "itself — coming in is quick and free."),
                        null, AdvancementType.CHALLENGE, true, true, true)
                .addCriterion("came_home",
                        AirlockTrigger.TriggerInstance.completed(AirlockTrigger.Moment.CAME_HOME))
                .save(output, id("come_home"));
    }

    /** Finding what is worth having, and making something permanent out of it. */
    private void rockBranch(Consumer<AdvancementHolder> output, AdvancementHolder root) {
        AdvancementHolder hammer = child(output, root, "percussive_geology",
                ModItems.HAMMER_STONE.get(), "Percussive Geology",
                "A tool made of rock, because there is no fire here to make anything else.",
                AdvancementType.TASK, hasItem(ModItems.HAMMER_STONE.get()));

        AdvancementHolder crushed = child(output, hammer, "ore_dressing",
                ModItems.CRUSHED_ORE.get(), "Ore Dressing",
                "Ore is not smelted here, it is dressed. How finely you grind it decides "
                        + "everything the magnet can do next.",
                AdvancementType.TASK, hasItem(ModItems.CRUSHED_ORE.get()));

        AdvancementHolder grains = child(output, crushed, "beneficiation",
                ModItems.IRON_NICKEL_GRAINS.get(), "Beneficiation",
                "Native metal, sorted out by a magnet. It was already metal when you dug "
                        + "it up — meteoric iron was worked long before anyone could smelt.",
                AdvancementType.GOAL, hasItem(ModItems.IRON_NICKEL_GRAINS.get()));

        // A sibling of beneficiation rather than a step after it: the two machines are two
        // axes on the same feed, not two rungs. Off the crushed ore, so the tree shows the
        // fork the player actually faces — magnet or gas, and the grind that suits one may
        // not suit the other.
        child(output, crushed, "density_sorting",
                ModBlocks.WINNOWING_TABLE.get(), "The Other Axis",
                "A gas stream sorts by weight where a magnet sorts by magnetism, so it "
                        + "catches the nickel sulfide a magnet never will. Concentrating it "
                        + "is not the same as getting the nickel out - that needs chemistry "
                        + "you do not have yet.",
                AdvancementType.GOAL, hasItem(ModBlocks.WINNOWING_TABLE.get()));

        child(output, crushed, "prospector",
                ModBlocks.METAL_RICH_ORE.get(), "Prospector",
                "A metal-rich seam. Ordinary rock carries a few percent; this carries "
                        + "enough to be worth the walk.",
                AdvancementType.TASK, hasItem(ModBlocks.METAL_RICH_ORE.get()));

        AdvancementHolder billet = child(output, grains, "cold_welded",
                ModItems.METAL_BILLET.get(), "Cold Welded",
                "Pressed solid in vacuum. With no air there is no oxide film, so clean "
                        + "metal simply bonds to itself — no heat involved anywhere.",
                AdvancementType.GOAL, hasItem(ModItems.METAL_BILLET.get()));

        child(output, billet, "meteoric",
                ModItems.METEORIC_PICKAXE.get(), "Meteoric",
                "A pickaxe from a rock that has never seen a flame. Work it too hard and "
                        + "it cracks; there is no annealing out here.",
                AdvancementType.CHALLENGE, hasItem(ModItems.METEORIC_PICKAXE.get()));
    }

    /**
     * Real electrolysis and real digestion, split off the rock/metal branch because neither
     * chain depends on the other or on cold-forged metal — two parallel real payoffs off the
     * same salvage-tier feedstocks (halite, fluorite), the fork the player actually faces once
     * both ores are in reach ({@code design/halogens.md} Parts A/B/C1).
     */
    private void halogensBranch(Consumer<AdvancementHolder> output, AdvancementHolder root) {
        AdvancementHolder downsCell = child(output, root, "downs_process",
                ModBlocks.DOWNS_CELL.get(), "The Downs Process",
                "Molten rock salt, split by current into real sodium metal and real "
                        + "chlorine gas — the same electrolysis that has made both "
                        + "industrially since 1924.",
                AdvancementType.TASK, hasItem(ModBlocks.DOWNS_CELL.get()));

        child(output, downsCell, "wafer_grade",
                ModItems.WAFER_SILICON.get(), "Wafer Grade",
                "A molten zone dragged the length of the rod pushes every trace of "
                        + "metal ahead of it. What is left behind is clean enough for a "
                        + "real chip.",
                AdvancementType.GOAL, hasItem(ModItems.WAFER_SILICON.get()));

        child(output, downsCell, "salt_cake",
                ModItems.HYDROFLUORIC_ACID.get(), "Salt Cake",
                "Fluorite and sulfuric acid, reacted the way most of the world's real "
                        + "hydrofluoric acid still is made — the reaction takes real "
                        + "heat, not free heat.",
                AdvancementType.GOAL, hasItem(ModItems.HYDROFLUORIC_ACID.get()));
    }

    /**
     * Data cells, the structure that grows their real capacity, and the terminal that
     * actually browses them — a real sequential chain, unlike the halogens' own two parallel
     * forks, since each step is what makes the next one worth building
     * ({@code design/data-cells.md}).
     */
    private void archiveBranch(Consumer<AdvancementHolder> output, AdvancementHolder root) {
        AdvancementHolder cell = child(output, root, "the_archive",
                ModItems.DATA_CELL.get(), "The Archive",
                "A digital manifest, not a bag of holding: log a container's real "
                        + "contents into one index instead of hauling every stack in "
                        + "your own hands.",
                AdvancementType.TASK, hasItem(ModItems.DATA_CELL.get()));

        AdvancementHolder drive = child(output, cell, "built_to_grow",
                ModBlocks.STORAGE_DRIVE.get(), "Built to Grow",
                "Fifty-four real slots, but only as many unlocked as the frame you "
                        + "actually built around it. The structure is the upgrade, not "
                        + "the block alone.",
                AdvancementType.TASK, hasItem(ModBlocks.STORAGE_DRIVE.get()));

        AdvancementHolder terminal = child(output, drive, "one_screen_every_cell",
                ModBlocks.STORAGE_TERMINAL.get(), "One Screen, Every Cell",
                "Every item logged in every connected drive, one sorted list, live. "
                        + "You no longer open cells one at a time to remember what is "
                        + "in them.",
                AdvancementType.GOAL, hasItem(ModBlocks.STORAGE_TERMINAL.get()));

        child(output, terminal, "denser_by_design",
                ModItems.CELL_COMPRESSOR.get(), "Denser by Design",
                "The same cell, the same real contents, a quarter of the slots. "
                        + "Compression is a real upgrade applied in place, not a "
                        + "bigger box.",
                AdvancementType.CHALLENGE, hasItem(ModItems.CELL_COMPRESSOR.get()));
    }

    /**
     * The room-gas loop: water split for its own oxygen, two real alternate ways to fix the CO2
     * it also produces, and a separate real sulfur chain that only the roaster actually starts —
     * see {@code design/chemistry-loop.md}. `sulfuric_acid_plant` is gated on the roaster rather
     * than on all three real reagents it needs (Minecraft's own tree has one parent per node),
     * because SO2 is the one input nothing else in this branch already supplies.
     */
    private void gasLoopBranch(Consumer<AdvancementHolder> output, AdvancementHolder root) {
        AdvancementHolder electrolyzer = child(output, root, "water_split",
                ModBlocks.WATER_ELECTROLYZER.get(), "Water Split",
                "2 H2O -> 2 H2 + O2, by current alone. Real hydrogen and real oxygen "
                        + "out of a water bottle, vented into the sealed room around it.",
                AdvancementType.TASK, hasItem(ModBlocks.WATER_ELECTROLYZER.get()));

        child(output, electrolyzer, "closing_the_loop",
                ModBlocks.SABATIER_REACTOR.get(), "Closing the Loop",
                "CO2 + 4 H2 -> CH4 + 2 H2O over a nickel catalyst — the real reaction "
                        + "the ISS itself uses to turn exhaled carbon back into water.",
                AdvancementType.GOAL, hasItem(ModBlocks.SABATIER_REACTOR.get()));

        child(output, electrolyzer, "carbon_kept_solid",
                ModBlocks.BOSCH_REACTOR.get(), "Carbon Kept Solid",
                "The same CO2 problem, solved the other real way: an iron catalyst turns "
                        + "it into water and solid carbon instead of spending it as fuel gas.",
                AdvancementType.GOAL, hasItem(ModBlocks.BOSCH_REACTOR.get()));

        AdvancementHolder roaster = child(output, root, "roasting_troilite",
                ModBlocks.TROILITE_ROASTER.get(), "Roasting Troilite",
                "4 FeS + 7 O2 -> 2 Fe2O3 + 4 SO2 — real oxygen spent on purpose, "
                        + "for the one gas nothing else in this mod produces yet.",
                AdvancementType.TASK, hasItem(ModBlocks.TROILITE_ROASTER.get()));

        child(output, roaster, "bottled_acid",
                ModItems.SULFURIC_ACID.get(), "Bottled Acid",
                "The Contact Process, closed: SO2, real room oxygen, and the water "
                        + "vapor the loop next door already vents, all three or nothing.",
                AdvancementType.CHALLENGE, hasItem(ModItems.SULFURIC_ACID.get()));
    }

    /** Power, stored, then spent on real logic — a straight chain, each step the reason the
     *  next one is worth building, the same shape the archive branch already uses. */
    private void powerBranch(Consumer<AdvancementHolder> output, AdvancementHolder root) {
        AdvancementHolder array = child(output, root, "first_watt",
                ModBlocks.SOLAR_ARRAY.get(), "First Watt",
                "A square metre of photovoltaic: real watts in real sun, and nothing "
                        + "at all once it sets.",
                AdvancementType.TASK, hasItem(ModBlocks.SOLAR_ARRAY.get()));

        AdvancementHolder cell = child(output, array, "kept_for_the_dark",
                ModBlocks.POWER_CELL.get(), "Kept for the Dark",
                "Joules banked for the half of every day the array makes none — power "
                        + "you generated hours ago, spent now.",
                AdvancementType.TASK, hasItem(ModBlocks.POWER_CELL.get()));

        AdvancementHolder gate = child(output, cell, "surface_mount_logic",
                ModItems.part(PartType.GATE_AND), "Surface-Mount Logic",
                "Six kinds of gate, five pixels square, wired into the same plane as "
                        + "the cable itself — real schematic logic, not a block per gate.",
                AdvancementType.GOAL, hasItem(ModItems.part(PartType.GATE_AND)));

        child(output, gate, "a_whole_microcontroller",
                ModItems.part(PartType.PROCESSOR), "A Whole Microcontroller",
                "An accumulator, a program counter, real flags — programmed by writing "
                        + "actual assembly, not stacking one more gate by hand.",
                AdvancementType.CHALLENGE, hasItem(ModItems.part(PartType.PROCESSOR)));
    }

    /** Liquefying gas on purpose, then using the cold for something real. */
    private void cryogenicsBranch(Consumer<AdvancementHolder> output, AdvancementHolder root) {
        AdvancementHolder tank = child(output, root, "gone_liquid",
                ModBlocks.CRYO_TANK.get(), "Gone Liquid",
                "A dewar, not a pressure vessel — real cryogenic storage, holding "
                        + "far more of a gas than compressing it ever could.",
                AdvancementType.TASK, hasItem(ModBlocks.CRYO_TANK.get()));

        AdvancementHolder cooler = child(output, tank, "stirling_cold",
                ModBlocks.CRYO_COOLER.get(), "Stirling Cold",
                "A real Stirling cryocooler, cold enough to keep a dewar's own "
                        + "contents liquid indefinitely rather than boiling off.",
                AdvancementType.GOAL, hasItem(ModBlocks.CRYO_COOLER.get()));

        AdvancementHolder dryer = child(output, cooler, "freeze_dried",
                ModBlocks.FREEZE_DRYER.get(), "Freeze-Dried",
                "Ice sublimated straight to vapor under vacuum, skipping the liquid "
                        + "phase entirely — the real process, not just cold air.",
                AdvancementType.GOAL, hasItem(ModBlocks.FREEZE_DRYER.get()));

        child(output, dryer, "lightest_real_solid",
                ModItems.SILICA_AEROGEL.get(), "The Lightest Real Solid",
                "Silica aerogel: 99% air by volume and still a solid you can hold — "
                        + "the real insulator this mod's own cryogenics needed to exist.",
                AdvancementType.CHALLENGE, hasItem(ModItems.SILICA_AEROGEL.get()));
    }

    /** Measure first, then harness — the smallest branch, matching how little of the radiation
     *  tier is actually built yet (gamma only; neutron waits on fission, PLAN.md v0.9). */
    private void radiationBranch(Consumer<AdvancementHolder> output, AdvancementHolder root) {
        AdvancementHolder counter = child(output, root, "clicks_per_second",
                ModItems.GEIGER_COUNTER.get(), "Clicks per Second",
                "A real detector. You cannot manage a hazard you cannot measure, and "
                        + "this is the first honest number for this one.",
                AdvancementType.TASK, hasItem(ModItems.GEIGER_COUNTER.get()));

        child(output, counter, "decay_into_watts",
                ModBlocks.RTG.get(), "Decay Into Watts",
                "A radioisotope thermoelectric generator: real decay heat, straight "
                        + "to real watts, for years without a single moving part.",
                AdvancementType.CHALLENGE, hasItem(ModBlocks.RTG.get()));
    }

    // -------------------------------------------------------------------- helpers

    private AdvancementHolder child(Consumer<AdvancementHolder> output, AdvancementHolder parent,
                                    String name, ItemLike iconItem, String title,
                                    String description, AdvancementType type,
                                    net.minecraft.advancements.Criterion<?> criterion) {
        return Advancement.Builder.advancement()
                .parent(parent)
                .display(iconItem, title(title), describe(description),
                        null, type, true, true, false)
                .addCriterion(name, criterion)
                .save(output, id(name));
    }

    private static net.minecraft.advancements.Criterion<?> hasItem(ItemLike item) {
        return InventoryChangeTrigger.TriggerInstance.hasItems(item);
    }

    private static Component title(String text) {
        return Component.literal(text);
    }

    private static Component describe(String text) {
        return Component.literal(text);
    }

    private static String id(String name) {
        return Astronima.MODID + ":" + name;
    }
}
