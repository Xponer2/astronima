package play.xponer.astronima.compat.jei;

import mezz.jei.api.recipe.RecipeType;
import net.minecraft.world.level.block.Block;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.registry.ModBlocks;

import java.util.List;
import java.util.function.Supplier;

/**
 * Every machine that has a page in JEI, published once.
 *
 * <p><strong>This exists because the hand-written version already lost two machines.</strong>
 * Registering a machine took three separate edits — a category, a catalyst and its recipes —
 * in three separate methods, and the solar retort and the winnowing table were added to the
 * game with none of the three. Every gate stayed green: they build, they run, they have
 * screens, they have recipes. They simply were not in the manual, and the only way to find
 * that out was to open JEI and look.
 *
 * <p>That is rule 20 exactly — <em>where several things must all be registered the same way,
 * publish the set once and have the registration iterate it, then a test can assert the set is
 * complete.</em> {@link #all()} is that set; {@code JeiCoverageTest} is that test, and it checks
 * this list against {@link ProcessingMenu.Kind} so a machine cannot be given a screen without
 * also being given a page.
 *
 * @param type    JEI's handle for the category
 * @param title   what the tab is called
 * @param machine the block that opens the page when clicked
 * @param pages   the pages themselves, computed from the same models the machine runs
 */
public record MachinePages(RecipeType<ProcessingRecipe> type, String title, Supplier<Block> machine,
                           Supplier<List<ProcessingRecipe>> pages) {

    private static RecipeType<ProcessingRecipe> type(String name) {
        return RecipeType.create("astronima", name, ProcessingRecipe.class);
    }

    public static final RecipeType<ProcessingRecipe> CRUSHING = type("crushing");
    public static final RecipeType<ProcessingRecipe> SEPARATION = type("separation");
    public static final RecipeType<ProcessingRecipe> FORGING = type("forging");
    public static final RecipeType<ProcessingRecipe> RETORT = type("retort");
    public static final RecipeType<ProcessingRecipe> WINNOWING = type("winnowing");
    public static final RecipeType<ProcessingRecipe> REFINER = type("refining");
    public static final RecipeType<ProcessingRecipe> FLUIDBED = type("reduction");
    public static final RecipeType<ProcessingRecipe> ELECTROLYSIS = type("electrolysis");
    public static final RecipeType<ProcessingRecipe> SLS = type("sls");
    public static final RecipeType<ProcessingRecipe> CRACKING_TOWER = type("cracking_tower");
    public static final RecipeType<ProcessingRecipe> POLYMERIZER = type("polymerizer");
    public static final RecipeType<ProcessingRecipe> WATER_ELECTROLYZER = type("water_electrolyzer");
    public static final RecipeType<ProcessingRecipe> SABATIER_REACTOR = type("sabatier_reactor");
    public static final RecipeType<ProcessingRecipe> BOSCH_REACTOR = type("bosch_reactor");
    public static final RecipeType<ProcessingRecipe> TROILITE_ROASTER = type("troilite_roaster");
    public static final RecipeType<ProcessingRecipe> SULFURIC_ACID_PLANT =
            type("sulfuric_acid_plant");
    public static final RecipeType<ProcessingRecipe> HEAVY_WATER_CELL = type("heavy_water_cell");
    public static final RecipeType<ProcessingRecipe> TITANIUM_CELL = type("titanium_cell");
    public static final RecipeType<ProcessingRecipe> INDUCTION_FURNACE = type("induction_furnace");
    public static final RecipeType<ProcessingRecipe> IRON_SMELTER = type("iron_smelter");
    public static final RecipeType<ProcessingRecipe> FREEZE_DRYER = type("freeze_dryer");
    public static final RecipeType<ProcessingRecipe> DOWNS_CELL = type("downs_cell");
    public static final RecipeType<ProcessingRecipe> ZONE_REFINER = type("zone_refiner");
    public static final RecipeType<ProcessingRecipe> HF_DIGESTER = type("hf_digester");
    public static final RecipeType<ProcessingRecipe> ETCH_STATION = type("etch_station");
    public static final RecipeType<ProcessingRecipe> GRAPHITIZER = type("graphitizer");
    public static final RecipeType<ProcessingRecipe> ALGAE_BIOREACTOR = type("algae_bioreactor");
    public static final RecipeType<ProcessingRecipe> ANAEROBIC_DIGESTER = type("anaerobic_digester");

    /**
     * The machines that make power, air and water rather than items.
     *
     * <p>Six of these had no page at all: JEI covered the seven that turn an item into another
     * item, and a player looking up the generator, the fuel cell, the candle, the scrubber, the
     * condenser or the array got a blank. Every one of them converts something into something else,
     * which is the definition of a machine.
     */
    public static final RecipeType<ProcessingRecipe> SOLAR = type("solar");
    public static final RecipeType<ProcessingRecipe> COMBUSTION = type("combustion");
    public static final RecipeType<ProcessingRecipe> FUEL_CELL = type("fuel_cell");
    public static final RecipeType<ProcessingRecipe> CANDLE = type("candle");
    public static final RecipeType<ProcessingRecipe> SCRUBBING = type("scrubbing");
    public static final RecipeType<ProcessingRecipe> CONDENSING = type("condensing");

    /**
     * The complete set, in the order a player meets the machines.
     *
     * <p>The blocks are supplied lazily rather than held: this class is loaded when JEI is,
     * and the deferred registry may not have resolved yet.
     */
    public static List<MachinePages> all() {
        return List.of(
                new MachinePages(CRUSHING, "Crushing",
                        ModBlocks.ORE_CRUSHER::get, ProcessingRecipe::crushing),
                new MachinePages(SEPARATION, "Magnetic Separation",
                        ModBlocks.MAGNETIC_SEPARATOR::get, ProcessingRecipe::separation),
                new MachinePages(WINNOWING, "Winnowing",
                        ModBlocks.WINNOWING_TABLE::get, ProcessingRecipe::winnowing),
                new MachinePages(FORGING, "Cold Forging",
                        ModBlocks.COLD_FORGE::get, ProcessingRecipe::forging),
                new MachinePages(RETORT, "Solar Retort",
                        ModBlocks.SOLAR_RETORT::get, ProcessingRecipe::retort),
                new MachinePages(REFINER, "Carbonyl Refining",
                        ModBlocks.CARBONYL_REFINER::get, ProcessingRecipe::refining),
                new MachinePages(FLUIDBED, "Fluidized-Bed Reduction",
                        ModBlocks.FLUIDIZED_BED::get, ProcessingRecipe::reduction),
                new MachinePages(ELECTROLYSIS, "Molten-Oxide Electrolysis",
                        ModBlocks.ELECTROLYSIS_CELL::get, ProcessingRecipe::electrolysis),
                new MachinePages(SLS, "Selective Laser Sintering",
                        ModBlocks.SLS_PRINTER::get, ProcessingRecipe::sls),
                new MachinePages(CRACKING_TOWER, "Thermal Cracking",
                        ModBlocks.CRACKING_TOWER::get, ProcessingRecipe::crackingTower),
                new MachinePages(POLYMERIZER, "Addition Polymerization",
                        ModBlocks.POLYMERIZER::get, ProcessingRecipe::polymerizer),
                new MachinePages(WATER_ELECTROLYZER, "Water Electrolysis",
                        ModBlocks.WATER_ELECTROLYZER::get, ProcessingRecipe::waterElectrolyzer),
                new MachinePages(SABATIER_REACTOR, "The Sabatier Reaction",
                        ModBlocks.SABATIER_REACTOR::get, ProcessingRecipe::sabatierReactor),
                new MachinePages(BOSCH_REACTOR, "The Bosch Reaction",
                        ModBlocks.BOSCH_REACTOR::get, ProcessingRecipe::boschReactor),
                new MachinePages(TROILITE_ROASTER, "Troilite Roasting",
                        ModBlocks.TROILITE_ROASTER::get, ProcessingRecipe::troiliteRoaster),
                new MachinePages(SULFURIC_ACID_PLANT, "The Contact Process",
                        ModBlocks.SULFURIC_ACID_PLANT::get, ProcessingRecipe::sulfuricAcidPlant),
                new MachinePages(HEAVY_WATER_CELL, "Heavy Water Cell",
                        ModBlocks.HEAVY_WATER_CELL::get, ProcessingRecipe::heavyWaterCell),
                new MachinePages(TITANIUM_CELL, "Titanium Cell",
                        ModBlocks.TITANIUM_CELL::get, ProcessingRecipe::titaniumCell),
                new MachinePages(INDUCTION_FURNACE, "Induction Furnace",
                        ModBlocks.INDUCTION_FURNACE::get, ProcessingRecipe::inductionFurnace),
                new MachinePages(IRON_SMELTER, "Iron Smelter",
                        ModBlocks.IRON_SMELTER::get, ProcessingRecipe::ironSmelter),
                new MachinePages(FREEZE_DRYER, "Freeze-Drying",
                        ModBlocks.FREEZE_DRYER::get, ProcessingRecipe::freezeDryer),
                new MachinePages(DOWNS_CELL, "The Downs Process",
                        ModBlocks.DOWNS_CELL::get, ProcessingRecipe::downsCell),
                new MachinePages(ZONE_REFINER, "Zone Refiner",
                        ModBlocks.ZONE_REFINER::get, ProcessingRecipe::zoneRefiner),
                new MachinePages(HF_DIGESTER, "HF Digester",
                        ModBlocks.HF_DIGESTER::get, ProcessingRecipe::hfDigester),
                new MachinePages(ETCH_STATION, "Wafer Etching",
                        ModBlocks.ETCH_STATION::get, ProcessingRecipe::etchStation),
                new MachinePages(GRAPHITIZER, "Graphitizing",
                        ModBlocks.GRAPHITIZER::get, ProcessingRecipe::graphitizer),
                new MachinePages(ALGAE_BIOREACTOR, "Algae Bioreactor",
                        ModBlocks.ALGAE_BIOREACTOR::get, ProcessingRecipe::algaeBioreactor),
                new MachinePages(ANAEROBIC_DIGESTER, "Anaerobic Digester",
                        ModBlocks.ANAEROBIC_DIGESTER::get, ProcessingRecipe::anaerobicDigester),
                new MachinePages(SOLAR, "Solar Array",
                        ModBlocks.SOLAR_ARRAY::get, PowerPages::solarArray),
                new MachinePages(COMBUSTION, "Combustion Generator",
                        ModBlocks.COMBUSTION_GENERATOR::get, PowerPages::generator),
                new MachinePages(FUEL_CELL, "Fuel Cell",
                        ModBlocks.FUEL_CELL::get, PowerPages::fuelCell),
                new MachinePages(CANDLE, "Oxygen Candle",
                        ModBlocks.OXYGEN_CANDLE::get, PowerPages::oxygenCandle),
                new MachinePages(SCRUBBING, "Scrubbing",
                        ModBlocks.SCRUBBER::get, PowerPages::scrubber),
                new MachinePages(CONDENSING, "Condensing",
                        ModBlocks.DEHUMIDIFIER::get, PowerPages::condenser));
    }
}
