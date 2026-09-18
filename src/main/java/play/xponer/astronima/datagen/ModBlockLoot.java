package play.xponer.astronima.datagen;

import net.minecraft.advancements.criterion.StatePropertiesPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import play.xponer.astronima.block.OxygenCandleBlock;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.registry.ModItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ModBlockLoot extends BlockLootSubProvider {
    public ModBlockLoot(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        // The bulk rock also yields the occasional combustible organic clump —
        // the asteroid's only furnace fuel (PLAN §1.0.1).
        add(ModBlocks.ASTEROID_ROCK.get(), block -> LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0f))
                        .add(LootItem.lootTableItem(block)))
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0f))
                        .add(LootItem.lootTableItem(ModItems.THOLIN_CLUMP.get()))
                        .when(LootItemRandomChanceCondition.randomChance(0.08f))));
        dropSelf(ModBlocks.REGOLITH.get());
        dropSelf(ModBlocks.WATER_ICE.get());
        dropSelf(ModBlocks.HEMATITE_ORE.get());
        // Wreck debris fused into the seam on impact — a rare, salvaged find rather than a
        // mined mineral (design/radiation.md §7): the mod has no isotope-refining chemistry,
        // so the RTG's fuel is something you find whole, not something you make.
        add(ModBlocks.METAL_RICH_ORE.get(), block -> LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0f))
                        .add(LootItem.lootTableItem(block)))
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0f))
                        .add(LootItem.lootTableItem(ModItems.RTG_CORE.get()))
                        .when(LootItemRandomChanceCondition.randomChance(0.03f))));
        dropSelf(ModBlocks.ASTERIUM_BLOCK.get());
        dropSelf(ModBlocks.ASTRA_ALTAR.get());
        dropSelf(ModBlocks.ORE_CRUSHER.get());
        dropSelf(ModBlocks.MAGNETIC_SEPARATOR.get());
        dropSelf(ModBlocks.WINNOWING_TABLE.get());
        dropSelf(ModBlocks.CARGO_CRATE.get());
        dropSelf(ModBlocks.SOLAR_RETORT.get());
        dropSelf(ModBlocks.TELESCOPE.get());
        dropSelf(ModBlocks.COLD_FORGE.get());
        dropSelf(ModBlocks.PACKED_TAILINGS.get());
        dropSelf(ModBlocks.HULL_PLATE.get());
        dropSelf(ModBlocks.INSULATED_HULL_PLATE.get());
        dropSelf(ModBlocks.PAINTED_HULL_PLATE.get());
        dropSelf(ModBlocks.PAINTED_INSULATED_HULL_PLATE.get());
        dropSelf(ModBlocks.GRAB_RAIL.get());
        dropSelf(ModBlocks.PURGE_VALVE.get());
        dropSelf(ModBlocks.GAS_PIPE.get());
        dropSelf(ModBlocks.GAS_PORT.get());
        dropSelf(ModBlocks.GAS_PUMP.get());
        dropSelf(ModBlocks.GAS_TANK.get());
        dropSelf(ModBlocks.GAS_VALVE.get());
        // GAS_POCKET_CORE is deliberately absent here, not a gap: its own registration in
        // ModBlocks.java already declares .noLootTable() (vanilla's real "this block has no
        // loot table at all" mechanism, distinct from noDrop()'s "has an empty one"). It is an
        // invisible worldgen marker consumed automatically the first time the room system
        // materializes its cavity (GasPocketCoreBlock's own doc) - Block#getLootTable() returns
        // empty for it, so calling add(...)/dropSelf(...) here throws "does not have loot
        // table" rather than generating anything. LootCoverageTest's own exemption set names it.
        dropSelf(ModBlocks.AIRLOCK_CONTROLLER.get());
        dropSelf(ModBlocks.SOLAR_ARRAY.get());
        dropSelf(ModBlocks.POWER_CELL.get());
        // A retired cable hands back its metal rather than itself: it can no longer be made,
        // so dropping one would be handing back something the player cannot obtain any other
        // way — and the point of retiring it is that the iron becomes routed wire instead.
        dropOther(ModBlocks.POWER_CABLE.get(),
                play.xponer.astronima.registry.ModItems.IRON_ROD.get());
        dropSelf(ModBlocks.COMBUSTION_GENERATOR.get());
        dropSelf(ModBlocks.FUEL_CELL.get());
        dropSelf(ModBlocks.CARBONYL_REFINER.get());
        dropSelf(ModBlocks.FLUIDIZED_BED.get());
        dropSelf(ModBlocks.ILMENITE_ORE.get());
        dropSelf(ModBlocks.ELECTROLYSIS_CELL.get());
        dropSelf(ModBlocks.SLS_PRINTER.get());
        dropSelf(ModBlocks.CRACKING_TOWER.get());
        dropSelf(ModBlocks.POLYMERIZER.get());
        dropSelf(ModBlocks.ACOUSTIC_FOAM.get());
        dropSelf(ModBlocks.SLEEPING_BAG.get());
        dropSelf(ModBlocks.WATER_ELECTROLYZER.get());
        dropSelf(ModBlocks.SABATIER_REACTOR.get());
        dropSelf(ModBlocks.BOSCH_REACTOR.get());
        dropSelf(ModBlocks.TROILITE_ROASTER.get());
        dropSelf(ModBlocks.HALITE_ORE.get());
        dropSelf(ModBlocks.FLUORITE_ORE.get());
        dropSelf(ModBlocks.DOWNS_CELL.get());
        dropSelf(ModBlocks.HF_DIGESTER.get());
        dropSelf(ModBlocks.CLEANROOM_CONTROLLER.get());
        dropSelf(ModBlocks.ETCH_STATION.get());
        dropSelf(ModBlocks.GRAPHITIZER.get());
        dropSelf(ModBlocks.ALGAE_BIOREACTOR.get());
        dropSelf(ModBlocks.ANAEROBIC_DIGESTER.get());
        dropSelf(ModBlocks.STORAGE_FRAME.get());
        dropSelf(ModBlocks.STORAGE_DRIVE.get());
        dropSelf(ModBlocks.STORAGE_TERMINAL.get());
        dropSelf(ModBlocks.SULFURIC_ACID_PLANT.get());
        dropSelf(ModBlocks.HEAVY_WATER_CELL.get());
        dropSelf(ModBlocks.TITANIUM_CELL.get());
        dropSelf(ModBlocks.ZONE_REFINER.get());
        dropSelf(ModBlocks.INDUCTION_FURNACE.get());
        dropSelf(ModBlocks.IRON_SMELTER.get());
        // v0.65 shipped without these three (found while wiring the RTG's own loot entry) —
        // breaking any of them dropped nothing at all. Fixed here; the mod has other blocks
        // with the same gap, flagged separately rather than fixed as a side effect of this phase.
        dropSelf(ModBlocks.CRYO_TANK.get());
        dropSelf(ModBlocks.CRYO_COOLER.get());
        dropSelf(ModBlocks.FREEZE_DRYER.get());
        dropSelf(ModBlocks.RTG.get());
        dropSelf(ModBlocks.VR_SIMULATION_POD.get());
        dropSelf(ModBlocks.AMMONIA_HEAT_PIPE.get());
        dropSelf(ModBlocks.SCRUBBER.get());
        dropSelf(ModBlocks.ALARM.get());
        dropSelf(ModBlocks.PRESENCE_SENSOR.get());
        dropSelf(ModBlocks.VACUUM_SENSOR.get());
        dropSelf(ModBlocks.STARVING_SENSOR.get());
        dropSelf(ModBlocks.FREEZING_SENSOR.get());


        dropSelf(ModBlocks.DECON_STATION.get());
        dropSelf(ModBlocks.INCUBATOR.get());
        dropSelf(ModBlocks.MICROSCOPE.get());
        dropSelf(ModBlocks.SYNTHESISER.get());
        dropSelf(ModBlocks.PRE_BREATHE_STATION.get());
        dropSelf(ModBlocks.DEHUMIDIFIER.get());
        dropSelf(ModBlocks.GLOW_STICK.get());
        dropSelf(ModBlocks.UNLIT_TORCH.get());
        dropSelf(ModBlocks.PARAFFIN_THERMAL_MASS.get());
        // The wall variant drops the upright item, as vanilla torches do.
        add(ModBlocks.UNLIT_WALL_TORCH.get(), createSingleItemTable(ModBlocks.UNLIT_TORCH.get()));
        // Scraped mold is destroyed, not harvested.
        add(ModBlocks.MOLD.get(), noDrop());
        // Breaking a growing plant destroys it - the real harvest is a right-click at maturity
        // (design/hydroponics.md §4.2), not breaking the block.
        add(ModBlocks.HYDROPONIC_CROP.get(), noDrop());
        // A candle is a one-shot chemical charge: once lit (or spent) there is nothing
        // left to recover — breaking it mid-burn must not refund a fresh candle.
        add(ModBlocks.OXYGEN_CANDLE.get(), block -> LootTable.lootTable().withPool(
                LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0f))
                        .add(LootItem.lootTableItem(block))
                        .when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                                .setProperties(StatePropertiesPredicate.Builder.properties()
                                        .hasProperty(OxygenCandleBlock.LIT, false)
                                        .hasProperty(OxygenCandleBlock.SPENT, false)))));
        add(ModBlocks.BULKHEAD_DOOR.get(), this::createDoorTable);
        // Crystal veins shatter into powder unless mined with silk touch.
        add(ModBlocks.CHLORATE_ORE.get(), block -> createSingleItemTableWithSilkTouch(
                block, ModItems.CHLORATE_POWDER.get(), UniformGenerator.between(2.0f, 4.0f)));
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return covered;
    }

    /**
     * Every block this generator actually wrote a table for, recorded as it writes them.
     *
     * <p><strong>It used to be a second hand-written list, and the inevitable happened.</strong>
     * A grab rail was given a {@code dropSelf} and not an entry here, and datagen failed with
     * <em>"created block loot tables for non-blocks"</em> — a message about the bookkeeping
     * rather than about the mistake, which is the worst kind to debug.
     *
     * <p>That is rule 20 exactly: two lists that must agree, and the miss lands in whichever
     * one you were not looking at. {@code add(Block, LootTable.Builder)} is the single funnel
     * every helper here goes through, so overriding it is enough to keep the set honest —
     * and it stays honest whatever anyone adds next.
     */
    private final List<Block> covered = new ArrayList<>();

    @Override
    protected void add(Block block, LootTable.Builder builder) {
        covered.add(block);
        super.add(block, builder);
    }
}
