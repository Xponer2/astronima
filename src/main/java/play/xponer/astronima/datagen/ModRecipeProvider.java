package play.xponer.astronima.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import play.xponer.astronima.crafting.CraftingTree;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.registry.ModItems;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Emits every recipe from {@link CraftingTree} — the same model the reachability test
 * verifies. Add recipes there, never here; an id used in the tree without a mapping
 * below fails datagen loudly.
 */
public class ModRecipeProvider extends RecipeProvider {
    protected ModRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
    }

    private static Map<String, ItemLike> itemLookup() {
        Map<String, ItemLike> map = new HashMap<>();
        map.put("minecraft:iron_ingot", Items.IRON_INGOT);
        map.put("minecraft:crafting_table", Items.CRAFTING_TABLE);
        map.put("minecraft:furnace", Items.FURNACE);
        map.put("minecraft:torch", Items.TORCH);
        map.put("minecraft:iron_pickaxe", Items.IRON_PICKAXE);
        map.put("minecraft:iron_shovel", Items.IRON_SHOVEL);
        map.put("minecraft:iron_axe", Items.IRON_AXE);
        map.put("astronima:asteroid_rock", ModBlocks.ASTEROID_ROCK.get());
        map.put("astronima:regolith", ModBlocks.REGOLITH.get());
        map.put("astronima:water_ice", ModBlocks.WATER_ICE.get());
        map.put("astronima:hematite_ore", ModBlocks.HEMATITE_ORE.get());
        map.put("astronima:hull_plate", ModBlocks.HULL_PLATE.get());
        map.put("astronima:insulated_hull_plate", ModBlocks.INSULATED_HULL_PLATE.get());
        map.put("astronima:painted_hull_plate", ModBlocks.PAINTED_HULL_PLATE.get());
        map.put("astronima:painted_insulated_hull_plate", ModBlocks.PAINTED_INSULATED_HULL_PLATE.get());
        map.put("astronima:grab_rail", ModBlocks.GRAB_RAIL.get());
        map.put("astronima:purge_valve", ModBlocks.PURGE_VALVE.get());
        map.put("astronima:solar_array", ModBlocks.SOLAR_ARRAY.get());
        map.put("astronima:power_cell", ModBlocks.POWER_CELL.get());
        map.put("astronima:combustion_generator", ModBlocks.COMBUSTION_GENERATOR.get());
        map.put("astronima:fuel_cell", ModBlocks.FUEL_CELL.get());
        map.put("astronima:sludge", ModItems.SLUDGE.get());
        map.put("astronima:pure_nickel", ModItems.PURE_NICKEL.get());
        map.put("astronima:carbonyl_refiner", ModBlocks.CARBONYL_REFINER.get());
        map.put("astronima:fluidized_bed", ModBlocks.FLUIDIZED_BED.get());
        map.put("astronima:ilmenite_ore", ModBlocks.ILMENITE_ORE.get());
        map.put("astronima:crushed_ilmenite", ModItems.CRUSHED_ILMENITE.get());
        map.put("astronima:iron_powder", ModItems.IRON_POWDER.get());
        map.put("astronima:titania", ModItems.TITANIA.get());
        map.put("astronima:electrolysis_cell", ModBlocks.ELECTROLYSIS_CELL.get());
        map.put("astronima:sls_printer", ModBlocks.SLS_PRINTER.get());
        map.put("astronima:silicon_electrode", ModItems.SILICON_ELECTRODE.get());
        map.put("astronima:aluminum_electrode", ModItems.ALUMINUM_ELECTRODE.get());
        map.put("astronima:silicon", ModItems.SILICON.get());
        map.put("astronima:aluminum", ModItems.ALUMINUM.get());
        map.put("astronima:sintered_frame", ModItems.SINTERED_FRAME.get());
        map.put("astronima:cracking_tower", ModBlocks.CRACKING_TOWER.get());
        map.put("astronima:polymerizer", ModBlocks.POLYMERIZER.get());
        map.put("astronima:polyethylene", ModItems.POLYETHYLENE.get());
        map.put("astronima:mylar", ModItems.MYLAR.get());
        map.put("astronima:kapton_tape", ModItems.KAPTON_TAPE.get());
        map.put("astronima:acoustic_foam", ModBlocks.ACOUSTIC_FOAM.get());
        map.put("astronima:sleeping_bag", ModBlocks.SLEEPING_BAG.get());
        map.put("astronima:water_electrolyzer", ModBlocks.WATER_ELECTROLYZER.get());
        map.put("astronima:sabatier_reactor", ModBlocks.SABATIER_REACTOR.get());
        map.put("astronima:bosch_reactor", ModBlocks.BOSCH_REACTOR.get());
        map.put("astronima:troilite_roaster", ModBlocks.TROILITE_ROASTER.get());
        map.put("astronima:halite_ore", ModBlocks.HALITE_ORE.get());
        map.put("astronima:downs_cell", ModBlocks.DOWNS_CELL.get());
        map.put("astronima:sodium", ModItems.SODIUM.get());
        map.put("astronima:zone_refiner", ModBlocks.ZONE_REFINER.get());
        map.put("astronima:wafer_silicon", ModItems.WAFER_SILICON.get());
        map.put("astronima:fluorite_ore", ModBlocks.FLUORITE_ORE.get());
        map.put("astronima:hf_digester", ModBlocks.HF_DIGESTER.get());
        map.put("astronima:hydrofluoric_acid", ModItems.HYDROFLUORIC_ACID.get());
        map.put("astronima:gypsum", ModItems.GYPSUM.get());
        map.put("astronima:cleanroom_controller", ModBlocks.CLEANROOM_CONTROLLER.get());
        map.put("astronima:hepa_filter", ModItems.HEPA_FILTER.get());
        map.put("astronima:etch_station", ModBlocks.ETCH_STATION.get());
        map.put("astronima:etched_die", ModItems.ETCHED_DIE.get());
        map.put("astronima:fluorosilicic_acid", ModItems.FLUOROSILICIC_ACID.get());
        map.put("astronima:data_cell", ModItems.DATA_CELL.get());
        map.put("astronima:storage_frame", ModBlocks.STORAGE_FRAME.get());
        map.put("astronima:storage_drive", ModBlocks.STORAGE_DRIVE.get());
        map.put("astronima:storage_terminal", ModBlocks.STORAGE_TERMINAL.get());
        map.put("astronima:cell_compressor", ModItems.CELL_COMPRESSOR.get());
        map.put("astronima:sulfuric_acid_plant", ModBlocks.SULFURIC_ACID_PLANT.get());
        map.put("astronima:heavy_water_cell", ModBlocks.HEAVY_WATER_CELL.get());
        map.put("astronima:titanium_cell", ModBlocks.TITANIUM_CELL.get());
        map.put("astronima:titanium", ModItems.TITANIUM.get());
        map.put("astronima:induction_furnace", ModBlocks.INDUCTION_FURNACE.get());
        map.put("astronima:iron_smelter", ModBlocks.IRON_SMELTER.get());
        map.put("astronima:vr_simulation_pod", ModBlocks.VR_SIMULATION_POD.get());
        map.put("astronima:slag", ModItems.SLAG.get());
        map.put("astronima:magnetic_boots", ModItems.MAGNETIC_BOOTS.get());
        map.put("astronima:bulkhead_door", ModBlocks.BULKHEAD_DOOR.get());
        map.put("astronima:scrubber", ModBlocks.SCRUBBER.get());
        map.put("astronima:oxygen_candle", ModBlocks.OXYGEN_CANDLE.get());
        map.put("astronima:chlorate_powder", ModItems.CHLORATE_POWDER.get());
        map.put("astronima:mineral_salts", ModItems.MINERAL_SALTS.get());
        map.put("astronima:lithium_hydroxide_cartridge", ModItems.LITHIUM_HYDROXIDE_CARTRIDGE.get());
        map.put("astronima:oxygen_tank_empty", ModItems.OXYGEN_TANK_EMPTY.get());
        map.put("astronima:ammonia_canister_empty", ModItems.AMMONIA_CANISTER_EMPTY.get());
        map.put("astronima:ammonia_canister", ModItems.AMMONIA_CANISTER.get());
        map.put("astronima:ammonia_heat_pipe", ModBlocks.AMMONIA_HEAT_PIPE.get());
        map.put("astronima:paraffin_wax", ModItems.PARAFFIN_WAX.get());
        map.put("astronima:paraffin_thermal_mass", ModBlocks.PARAFFIN_THERMAL_MASS.get());
        map.put("astronima:gas_analyzer", ModItems.GAS_ANALYZER.get());
        map.put("astronima:ore_crusher", ModBlocks.ORE_CRUSHER.get());
        map.put("astronima:cold_forge", ModBlocks.COLD_FORGE.get());
        map.put("astronima:packed_tailings", ModBlocks.PACKED_TAILINGS.get());
        map.put("astronima:chlorate_ore", ModBlocks.CHLORATE_ORE.get());
        map.put("astronima:metal_billet", ModItems.METAL_BILLET.get());
        map.put("astronima:tool_head", ModItems.TOOL_HEAD.get());
        map.put("astronima:hammer_stone", ModItems.HAMMER_STONE.get());
        map.put("astronima:meteoric_pickaxe", ModItems.METEORIC_PICKAXE.get());
        map.put("astronima:magnetic_separator", ModBlocks.MAGNETIC_SEPARATOR.get());
        map.put("astronima:winnowing_table", ModBlocks.WINNOWING_TABLE.get());
        map.put("astronima:cargo_crate", ModBlocks.CARGO_CRATE.get());
        map.put("astronima:solar_retort", ModBlocks.SOLAR_RETORT.get());
        map.put("astronima:baked_silicate", ModItems.BAKED_SILICATE.get());
        map.put("astronima:metal_rich_ore", ModBlocks.METAL_RICH_ORE.get());
        map.put("astronima:crushed_ore", ModItems.CRUSHED_ORE.get());
        map.put("astronima:iron_nickel_grains", ModItems.IRON_NICKEL_GRAINS.get());
        map.put("astronima:tailings", ModItems.TAILINGS.get());
        map.put("astronima:iron_rod", ModItems.IRON_ROD.get());
        map.put("astronima:refractory_lining", ModItems.REFRACTORY_LINING.get());
        map.put("astronima:precision_bearing", ModItems.PRECISION_BEARING.get());
        map.put("astronima:control_board", ModItems.CONTROL_BOARD.get());
        map.put("astronima:gas_seal", ModItems.GAS_SEAL.get());
        map.put("astronima:reinforced_frame", ModItems.REINFORCED_FRAME.get());
        map.put("astronima:gas_pipe", ModBlocks.GAS_PIPE.get());
        map.put("astronima:gas_port", ModBlocks.GAS_PORT.get());
        map.put("astronima:gas_pump", ModBlocks.GAS_PUMP.get());
        map.put("astronima:gas_tank", ModBlocks.GAS_TANK.get());
        map.put("astronima:cryo_tank", ModBlocks.CRYO_TANK.get());
        map.put("astronima:cryo_cooler", ModBlocks.CRYO_COOLER.get());
        map.put("astronima:freeze_dryer", ModBlocks.FREEZE_DRYER.get());
        map.put("astronima:rtg", ModBlocks.RTG.get());
        map.put("astronima:rtg_core", ModItems.RTG_CORE.get());
        map.put("astronima:geiger_counter", ModItems.GEIGER_COUNTER.get());
        map.put("astronima:gas_valve", ModBlocks.GAS_VALVE.get());
        map.put("astronima:tholin_clump", ModItems.THOLIN_CLUMP.get());
        map.put("astronima:seismic_probe", ModItems.SEISMIC_PROBE.get());
        map.put("astronima:wrench", ModItems.WRENCH.get());
        map.put("astronima:wire_coil", ModItems.WIRE_COIL.get());
        map.put("astronima:wire_ribbon", ModItems.WIRE_RIBBON.get());
        map.put("astronima:wire_cutters", ModItems.WIRE_CUTTERS.get());
        map.put("astronima:wire_snips", ModItems.WIRE_SNIPS.get());
        map.put("astronima:presence_sensor", ModItems.PRESENCE_SENSOR.get());
        map.put("astronima:vacuum_sensor", ModItems.VACUUM_SENSOR.get());
        map.put("astronima:oxygen_sensor", ModItems.STARVING_SENSOR.get());
        map.put("astronima:frost_sensor", ModItems.FREEZING_SENSOR.get());

        map.put("astronima:diagnostic_goggles", ModItems.DIAGNOSTIC_GOGGLES.get());
        map.put("astronima:basic_biomonitor_chip", ModItems.BASIC_BIOMONITOR_CHIP.get());
        map.put("astronima:pathogen_analyzer_chip", ModItems.PATHOGEN_ANALYZER_CHIP.get());
        map.put("astronima:petri_dish", ModItems.PETRI_DISH.get());
        map.put("astronima:incubator", ModItems.INCUBATOR.get());
        map.put("astronima:decon_station", ModItems.DECON_STATION.get());
        map.put("astronima:microscope", ModItems.MICROSCOPE.get());
        map.put("astronima:synthesiser", ModItems.SYNTHESISER.get());
        map.put("astronima:dose", ModItems.DOSE.get());
        // The wire-layer parts, keyed by the ids they had as blocks — so every recipe, JEI
        // page and advancement that already named one still names the same thing (rule 20).
        for (var entry : ModItems.PARTS.entrySet()) {
            map.put("astronima:" + entry.getKey().id(), entry.getValue().get());
        }

        map.put("astronima:airlock_controller", ModBlocks.AIRLOCK_CONTROLLER.get());
        map.put("astronima:alarm", ModBlocks.ALARM.get());
        map.put("astronima:pre_breathe_station", ModBlocks.PRE_BREATHE_STATION.get());
        map.put("astronima:dehumidifier", ModBlocks.DEHUMIDIFIER.get());
        map.put("astronima:glow_stick", ModBlocks.GLOW_STICK.get());
        map.put("astronima:striker", ModItems.STRIKER.get());
        map.put("astronima:eva_suit", ModItems.EVA_SUIT.get());
        map.put("astronima:sealant_patch", ModItems.SEALANT_PATCH.get());
        map.put("astronima:latch_set", ModItems.LATCH_SET.get());
        map.put("astronima:calibrated_valve", ModItems.CALIBRATED_VALVE.get());
        map.put("astronima:insulation_weave", ModItems.INSULATION_WEAVE.get());
        map.put("astronima:salvaged_circuit", ModItems.SALVAGED_CIRCUIT.get());
        map.put("astronima:unlit_torch", ModBlocks.UNLIT_TORCH.get());
        map.put("astronima:spectrograph", ModItems.SPECTROGRAPH.get());
        map.put("astronima:spectral_plate", ModItems.SPECTRAL_PLATE.get());
        map.put("astronima:coherence_meter", ModItems.COHERENCE_METER.get());
        map.put("astronima:astra_field_meter", ModItems.ASTRA_FIELD_METER.get());
        map.put("astronima:astra_collector", ModItems.ASTRA_COLLECTOR.get());
        map.put("astronima:astra_sounder", ModItems.ASTRA_SOUNDER.get());
        map.put("astronima:asterium_grains", ModItems.ASTERIUM_GRAINS.get());
        map.put("astronima:asterium_block", ModBlocks.ASTERIUM_BLOCK.get());
        map.put("astronima:astra_altar", ModBlocks.ASTRA_ALTAR.get());
        map.put("astronima:wide_aperture_lens", ModItems.WIDE_APERTURE_LENS.get());
        for (play.xponer.astronima.sim.magic.SpectralLine line
                : play.xponer.astronima.sim.magic.SpectralLine.values()) {
            map.put("astronima:" + line.id(), ModItems.filterToken(line));
        }

        return map;
    }

    @Override
    protected void buildRecipes() {
        Map<String, ItemLike> items = itemLookup();
        for (CraftingTree.Source source : CraftingTree.sources()) {
            switch (source) {
                case CraftingTree.WorldSource ignored -> { /* obtained by mining, not crafting */ }
                case CraftingTree.Transformation ignored -> { /* in-world item use, not a recipe */ }
                case CraftingTree.Shaped shaped -> emitShaped(items, shaped);
                case CraftingTree.Shapeless shapeless -> emitShapeless(items, shapeless);
                case CraftingTree.Cooking cooking -> emitCooking(items, cooking);
            }
        }
    }

    /**
     * Results whose recipe has to carry data through the crafting grid.
     *
     * <p>These stay in {@link CraftingTree} so the reachability test still proves they
     * are obtainable, but their JSON is hand-written under {@code src/main/resources}
     * because it uses {@code astronima:forged_tool} rather than
     * {@code minecraft:crafting_shaped}. The vanilla builder can only emit the vanilla
     * type, and a generated file of the wrong type would silently shadow the real one
     * and throw the forged quality away again.
     */
    private static final java.util.Set<String> HAND_WRITTEN =
            java.util.Set.of("astronima:meteoric_pickaxe",
                    "astronima:meteoric_shovel",
                    "astronima:meteoric_axe",
                    "astronima:improvised_pickaxe");

    private void emitShaped(Map<String, ItemLike> items, CraftingTree.Shaped shaped) {
        if (HAND_WRITTEN.contains(shaped.result())) {
            return;
        }
        ShapedRecipeBuilder builder = shaped(RecipeCategory.MISC, resolve(items, shaped.result()), shaped.count());
        shaped.pattern().forEach(builder::pattern);
        for (Character symbol : shaped.orderedKeys()) {
            builder.define(symbol, resolve(items, shaped.keys().get(symbol)));
        }
        String principalInput = shaped.principalInput();
        builder.unlockedBy(criterionName(principalInput), has(resolve(items, principalInput)));
        // Vanilla-output recipes need a mod-namespaced id (the default would collide
        // with vanilla's); our own outputs must use the default (the generator
        // rejects an explicit id equal to it).
        String alternate = uniqueId(shaped.result(), principalInput);
        if (alternate != null) {
            builder.save(this.output, alternate);
        } else if (shaped.result().startsWith("astronima:")) {
            builder.save(this.output);
        } else {
            builder.save(this.output, modRecipeId(shaped.result()));
        }
    }

    /**
     * Results that more than one recipe produces.
     *
     * <p>Multiple routes to one item is deliberate design rather than a mistake — rod
     * stock can be smelted by anyone with air to burn, or cold worked by anyone
     * without — but Minecraft keys recipes by id, so the second one has to be named
     * for how it is made instead of what it makes.
     */
    private final java.util.Set<String> emittedResults = new java.util.HashSet<>();

    /**
     * A unique id for a recipe, suffixed by its principal input when the result
     * already has one. Deriving the suffix from the input is what makes the id say
     * something: {@code iron_rod_from_metal_billet} rather than {@code iron_rod_2}.
     */
    private String uniqueId(String result, String principalInput) {
        if (emittedResults.add(result)) {
            return null; // first route keeps the default id
        }
        return "astronima:" + path(result) + "_from_" + path(principalInput);
    }

    private static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    private void emitShapeless(Map<String, ItemLike> items, CraftingTree.Shapeless shapeless) {
        ShapelessRecipeBuilder builder = shapeless(RecipeCategory.MISC, resolve(items, shapeless.result()), shapeless.count());
        for (String input : shapeless.orderedInputs()) {
            builder.requires(resolve(items, input), shapeless.inputs().get(input));
        }
        String principalInput = shapeless.principalInput();
        builder.unlockedBy(criterionName(principalInput), has(resolve(items, principalInput)));
        String alternate = uniqueId(shapeless.result(), principalInput);
        if (alternate != null) {
            builder.save(this.output, alternate);
        } else if (shapeless.result().startsWith("astronima:")) {
            builder.save(this.output);
        } else {
            builder.save(this.output, modRecipeId(shapeless.result()));
        }
    }

    private void emitCooking(Map<String, ItemLike> items, CraftingTree.Cooking cooking) {
        ItemLike input = resolve(items, cooking.input());
        ItemLike result = resolve(items, cooking.result());
        // The id is qualified by the INPUT, not just the result: iron comes from both hematite
        // and reduced iron powder, and naming a cooking recipe after its output alone collides
        // the moment two feeds smelt to the same metal.
        String base = modRecipeId(cooking.result()) + "_from_"
                + cooking.input().substring(cooking.input().indexOf(':') + 1);
        SimpleCookingRecipeBuilder.smelting(Ingredient.of(input), RecipeCategory.MISC,
                        CookingBookCategory.MISC, result, cooking.xp(), cooking.timeTicks())
                .unlockedBy(criterionName(cooking.input()), has(input))
                .save(this.output, base + "_smelting");
        SimpleCookingRecipeBuilder.blasting(Ingredient.of(input), RecipeCategory.MISC,
                        CookingBookCategory.MISC, result, cooking.xp(), cooking.timeTicks() / 2)
                .unlockedBy(criterionName(cooking.input()), has(input))
                .save(this.output, base + "_blasting");
    }

    private static ItemLike resolve(Map<String, ItemLike> items, String id) {
        ItemLike item = items.get(id);
        if (item == null) {
            throw new IllegalStateException("CraftingTree id has no item mapping: " + id);
        }
        return item;
    }

    /** Recipe ids live in our namespace so vanilla-output recipes never collide with vanilla's. */
    private static String modRecipeId(String resultId) {
        return "astronima:" + resultId.substring(resultId.indexOf(':') + 1);
    }

    private static String criterionName(String inputId) {
        return "has_" + inputId.substring(inputId.indexOf(':') + 1);
    }

    public static class Runner extends RecipeProvider.Runner {
        public Runner(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> registries) {
            super(packOutput, registries);
        }

        @Override
        protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
            return new ModRecipeProvider(registries, output);
        }

        @Override
        public String getName() {
            return "Astronima recipes";
        }
    }
}
