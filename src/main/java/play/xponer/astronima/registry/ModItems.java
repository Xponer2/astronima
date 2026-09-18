package play.xponer.astronima.registry;

import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.item.EmptyOxygenTankItem;
import play.xponer.astronima.item.PryBarToolMaterial;
import play.xponer.astronima.item.WearingToolItem;
import play.xponer.astronima.item.EvaSuitItem;
import play.xponer.astronima.item.CrushedIlmeniteItem;
import play.xponer.astronima.item.CrushedOreItem;
import play.xponer.astronima.item.LiohCartridgeItem;
import play.xponer.astronima.item.MeteoricToolMaterial;
import play.xponer.astronima.item.SuitRepairItem;
import play.xponer.astronima.sim.suit.SuitSubsystem;
import play.xponer.astronima.item.GasAnalyzerItem;
import play.xponer.astronima.item.OxygenTanks;
import play.xponer.astronima.item.SeismicProbeItem;
import play.xponer.astronima.item.LogicPartItem;
import play.xponer.astronima.sim.logic.PartType;
import play.xponer.astronima.item.WireCoilItem;
import play.xponer.astronima.item.WireCutterItem;
import play.xponer.astronima.item.WrenchItem;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Astronima.MODID);

    // Block items
    public static final DeferredItem<?> ASTEROID_ROCK = ITEMS.registerSimpleBlockItem(ModBlocks.ASTEROID_ROCK);
    public static final DeferredItem<?> REGOLITH = ITEMS.registerSimpleBlockItem(ModBlocks.REGOLITH);
    public static final DeferredItem<?> WATER_ICE = ITEMS.registerSimpleBlockItem(ModBlocks.WATER_ICE);
    public static final DeferredItem<?> CHLORATE_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.CHLORATE_ORE);
    public static final DeferredItem<?> ASTERIUM_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.ASTERIUM_BLOCK);
    public static final DeferredItem<?> ASTRA_ALTAR = ITEMS.registerSimpleBlockItem(ModBlocks.ASTRA_ALTAR);
    public static final DeferredItem<?> HEMATITE_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.HEMATITE_ORE);
    public static final DeferredItem<?> METAL_RICH_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.METAL_RICH_ORE);
    public static final DeferredItem<?> ORE_CRUSHER = ITEMS.registerSimpleBlockItem(ModBlocks.ORE_CRUSHER);
    public static final DeferredItem<?> MAGNETIC_SEPARATOR = ITEMS.registerSimpleBlockItem(ModBlocks.MAGNETIC_SEPARATOR);
    public static final DeferredItem<?> WINNOWING_TABLE = ITEMS.registerSimpleBlockItem(ModBlocks.WINNOWING_TABLE);
    public static final DeferredItem<?> CARGO_CRATE = ITEMS.registerSimpleBlockItem(ModBlocks.CARGO_CRATE);
    public static final DeferredItem<?> SOLAR_RETORT = ITEMS.registerSimpleBlockItem(ModBlocks.SOLAR_RETORT);
    public static final DeferredItem<?> COLD_FORGE = ITEMS.registerSimpleBlockItem(ModBlocks.COLD_FORGE);
    public static final DeferredItem<?> PACKED_TAILINGS = ITEMS.registerSimpleBlockItem(ModBlocks.PACKED_TAILINGS);
    public static final DeferredItem<?> HULL_PLATE = ITEMS.registerSimpleBlockItem(ModBlocks.HULL_PLATE);
    public static final DeferredItem<?> INSULATED_HULL_PLATE =
            ITEMS.registerSimpleBlockItem(ModBlocks.INSULATED_HULL_PLATE);
    public static final DeferredItem<?> PAINTED_HULL_PLATE =
            ITEMS.registerSimpleBlockItem(ModBlocks.PAINTED_HULL_PLATE);
    public static final DeferredItem<?> PAINTED_INSULATED_HULL_PLATE =
            ITEMS.registerSimpleBlockItem(ModBlocks.PAINTED_INSULATED_HULL_PLATE);
    public static final DeferredItem<?> GRAB_RAIL = ITEMS.registerSimpleBlockItem(ModBlocks.GRAB_RAIL);
    public static final DeferredItem<?> PURGE_VALVE = ITEMS.registerSimpleBlockItem(ModBlocks.PURGE_VALVE);
    public static final DeferredItem<?> SOLAR_ARRAY = ITEMS.registerSimpleBlockItem(ModBlocks.SOLAR_ARRAY);
    public static final DeferredItem<?> POWER_CELL = ITEMS.registerSimpleBlockItem(ModBlocks.POWER_CELL);
    public static final DeferredItem<?> COMBUSTION_GENERATOR =
            ITEMS.registerSimpleBlockItem(ModBlocks.COMBUSTION_GENERATOR);
    public static final DeferredItem<?> FUEL_CELL = ITEMS.registerSimpleBlockItem(ModBlocks.FUEL_CELL);

    /**
     * Soot and sulfur dug out of a generator.
     *
     * <p>Carbon and sulfur compounds, which is what an engine actually leaves behind — and
     * which PLAN already schedules as the v0.58 petrochemical feedstock. Waste becomes a
     * resource for the third time in this tier: the gas pocket was a hazard that became fuel,
     * and its residue is stock.
     */
    public static final DeferredItem<Item> SLUDGE = ITEMS.registerSimpleItem("sludge");

    /**
     * Nickel with nothing else in it.
     *
     * <p>What the Mond process is for. Its value is not "purity is nice" — it is that
     * {@code ColdWorking.hardeningRate} makes nickel content a real property of a billet, and
     * until now the player took whatever the rock contained. This is the dial.
     */
    public static final DeferredItem<Item> PURE_NICKEL = ITEMS.registerSimpleItem("pure_nickel");
    /** Real metal, off the titanium cell — see {@code design/titanium-reduction.md}. */
    public static final DeferredItem<Item> TITANIUM = ITEMS.registerSimpleItem("titanium");
    /** Zone-refined electrolytic silicon — real purity, not a new element (design/halogens.md
     *  §9-10 Part B). */
    public static final DeferredItem<Item> WAFER_SILICON = ITEMS.registerSimpleItem("wafer_silicon");

    public static final DeferredItem<?> CARBONYL_REFINER =
            ITEMS.registerSimpleBlockItem(ModBlocks.CARBONYL_REFINER);

    public static final DeferredItem<?> FLUIDIZED_BED =
            ITEMS.registerSimpleBlockItem(ModBlocks.FLUIDIZED_BED);

    public static final DeferredItem<?> ILMENITE_ORE =
            ITEMS.registerSimpleBlockItem(ModBlocks.ILMENITE_ORE);

    public static final DeferredItem<?> ELECTROLYSIS_CELL =
            ITEMS.registerSimpleBlockItem(ModBlocks.ELECTROLYSIS_CELL);

    public static final DeferredItem<?> SLS_PRINTER =
            ITEMS.registerSimpleBlockItem(ModBlocks.SLS_PRINTER);

    public static final DeferredItem<?> CRACKING_TOWER =
            ITEMS.registerSimpleBlockItem(ModBlocks.CRACKING_TOWER);

    public static final DeferredItem<?> POLYMERIZER =
            ITEMS.registerSimpleBlockItem(ModBlocks.POLYMERIZER);

    /**
     * The polymerizer's one product: addition-polymerized ethylene. The mod's one general
     * engineering-plastic stock — mylar and kapton tape are shaped from it rather than each
     * getting their own machine step; see {@code design/petrochemicals.md} §2/§6.
     */
    public static final DeferredItem<Item> POLYETHYLENE = ITEMS.registerSimpleItem("polyethylene");

    /** A second path to the suit's thermal layer, alongside {@link #INSULATION_WEAVE}. */
    public static final DeferredItem<play.xponer.astronima.item.SuitRepairItem> MYLAR =
            repairPart("mylar", SuitSubsystem.THERMAL_LAYER);

    /** A second path to the suit's helmet seal, alongside {@link #SEALANT_PATCH} — "finally the
     * sealant" {@code PLAN.md}'s v0.58 line points at. */
    public static final DeferredItem<play.xponer.astronima.item.SuitRepairItem> KAPTON_TAPE =
            repairPart("kapton_tape", SuitSubsystem.HELMET_SEAL);

    public static final DeferredItem<?> ACOUSTIC_FOAM =
            ITEMS.registerSimpleBlockItem(ModBlocks.ACOUSTIC_FOAM);

    public static final DeferredItem<?> SLEEPING_BAG =
            ITEMS.registerSimpleBlockItem(ModBlocks.SLEEPING_BAG);

    public static final DeferredItem<?> WATER_ELECTROLYZER =
            ITEMS.registerSimpleBlockItem(ModBlocks.WATER_ELECTROLYZER);

    public static final DeferredItem<?> SABATIER_REACTOR =
            ITEMS.registerSimpleBlockItem(ModBlocks.SABATIER_REACTOR);

    public static final DeferredItem<?> BOSCH_REACTOR =
            ITEMS.registerSimpleBlockItem(ModBlocks.BOSCH_REACTOR);

    /** {@code CO2 + 2 H2 -> C(s) + 2 H2O}: the Bosch reactor's own solid product. Real amorphous
     * soot, annealed to graphite by the Graphitizer (design/carbon-fiber.md) — the honest next
     * step for a "not yet" this item carried since v0.6. */
    public static final DeferredItem<Item> CARBON_POWDER = ITEMS.registerSimpleItem("carbon_powder");

    /** Crystalline carbon, annealed from {@link #CARBON_POWDER} at ~2500 °C (the real Acheson
     * process) — the Graphitizer's own high-setpoint product when the room's oxygen is purged.
     * See design/carbon-fiber.md. */
    public static final DeferredItem<Item> GRAPHITE_POWDER = ITEMS.registerSimpleItem("graphite_powder");

    /** Green, unstabilized pitch fiber — real melt-spun {@link #SLUDGE}, shaped at a bench, not
     * reacted (design/carbon-fiber.md §2). Will fuse rather than carbonize if heated before it is
     * stabilized. */
    public static final DeferredItem<Item> PITCH_FIBER = ITEMS.registerSimpleItem("pitch_fiber");

    /** {@link #PITCH_FIBER}, oxidatively cross-linked at the Graphitizer's low setpoint (real
     * stabilization, ~300 °C, needs the room's own oxygen) — infusible, and the Graphitizer's own
     * high-setpoint feed for real carbon fiber. See design/carbon-fiber.md. */
    public static final DeferredItem<Item> STABILIZED_FIBER = ITEMS.registerSimpleItem("stabilized_fiber");

    /** Real carbon fiber: {@link #STABILIZED_FIBER} carbonized/graphitized at the Graphitizer's
     * high setpoint, the same real anneal {@link #GRAPHITE_POWDER} gets, off a genuinely different
     * real precursor (design/carbon-fiber.md §1's own honesty about "not graphite respun"). */
    public static final DeferredItem<Item> CARBON_FIBER = ITEMS.registerSimpleItem("carbon_fiber");

    /** Real carbon-carbon composite: graphite powder as the matrix, carbon fiber as the
     * reinforcement, one crafting-table step (a named simplification for real multi-cycle
     * densification — design/carbon-fiber.md §3). Honest {@code USE_PENDING} against
     * "v0.8 — The Hostile Rock II"'s own structural-reinforcement line until that phase exists. */
    public static final DeferredItem<Item> CARBON_COMPOSITE_PLATE =
            ITEMS.registerSimpleItem("carbon_composite_plate");

    public static final DeferredItem<?> GRAPHITIZER =
            ITEMS.registerSimpleBlockItem(ModBlocks.GRAPHITIZER);

    /** This mod's first real, edible food — real Chlorella/Spirulina, off the algae bioreactor.
     * A real nutrient-dense supplement (>60% protein by mass in the real organism), not a full
     * meal: modest nutrition, high saturation per point. See design/hydroponics.md §1.3. */
    public static final DeferredItem<Item> ALGAE_BIOMASS = ITEMS.registerSimpleItem("algae_biomass",
            p -> p.food(new net.minecraft.world.food.FoodProperties.Builder()
                    .nutrition(3).saturationModifier(0.8f).build()));

    public static final DeferredItem<?> ALGAE_BIOREACTOR =
            ITEMS.registerSimpleBlockItem(ModBlocks.ALGAE_BIOREACTOR);

    /** A real seed bank surviving a real crash - the same "just have it, salvaged from the wreck"
     * standing asteroid_rock/tholin_clump already carry (design/hydroponics.md §4.3). A real
     * {@code BlockItem} under its own name, the same "seed places the crop" shape vanilla's own
     * wheat seeds already are - places {@link ModBlocks#HYDROPONIC_CROP}. */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> LETTUCE_SEEDLING =
            ITEMS.registerSimpleBlockItem("lettuce_seedling", ModBlocks.HYDROPONIC_CROP);

    /** Real red romaine lettuce - the 'Outredgeous' cultivar NASA's own Veg-01/03/05 ISS
     * experiments grow. A real second food, larger and less nutrient-dense than algae_biomass
     * (design/hydroponics.md §4.6). */
    public static final DeferredItem<Item> LETTUCE = ITEMS.registerSimpleItem("lettuce",
            p -> p.food(new net.minecraft.world.food.FoodProperties.Builder()
                    .nutrition(2).saturationModifier(0.3f).build()));

    /** Real inedible harvest residue - the same real plant lettuce comes off, a second real fact
     *  about it the mod never modelled until now (design/anaerobic-digestion.md §0). */
    public static final DeferredItem<Item> CROP_WASTE = ITEMS.registerSimpleItem("crop_waste");

    public static final DeferredItem<?> ANAEROBIC_DIGESTER =
            ITEMS.registerSimpleBlockItem(ModBlocks.ANAEROBIC_DIGESTER);

    /** Real digestate: nitrogen/phosphorus/potassium-bearing fertilizer, the digester's own real
     *  second output. No real consumer yet - honest {@code USE_PENDING} against "v0.75 —
     *  Bioregeneration & Genetics" itself, the same phase that built the hydroponic system this
     *  should eventually feed (design/anaerobic-digestion.md §1). */
    public static final DeferredItem<Item> FERTILIZER = ITEMS.registerSimpleItem("fertilizer");

    public static final DeferredItem<?> TROILITE_ROASTER =
            ITEMS.registerSimpleBlockItem(ModBlocks.TROILITE_ROASTER);

    public static final DeferredItem<?> SULFURIC_ACID_PLANT =
            ITEMS.registerSimpleBlockItem(ModBlocks.SULFURIC_ACID_PLANT);

    public static final DeferredItem<?> HEAVY_WATER_CELL =
            ITEMS.registerSimpleBlockItem(ModBlocks.HEAVY_WATER_CELL);

    public static final DeferredItem<?> TITANIUM_CELL =
            ITEMS.registerSimpleBlockItem(ModBlocks.TITANIUM_CELL);

    public static final DeferredItem<?> INDUCTION_FURNACE =
            ITEMS.registerSimpleBlockItem(ModBlocks.INDUCTION_FURNACE);

    public static final DeferredItem<?> IRON_SMELTER =
            ITEMS.registerSimpleBlockItem(ModBlocks.IRON_SMELTER);

    public static final DeferredItem<?> VR_SIMULATION_POD =
            ITEMS.registerSimpleBlockItem(ModBlocks.VR_SIMULATION_POD);

    /** {@code 2 SO2 + O2 + 2 H2O -> 2 H2SO4}: the acid plant's own bottled product. A real
     * industrial acid with an honest "not yet" — see design/chemistry-loop.md §2.7. */
    public static final DeferredItem<Item> SULFURIC_ACID = ITEMS.registerSimpleItem("sulfuric_acid");

    public static final DeferredItem<?> AMMONIA_HEAT_PIPE =
            ITEMS.registerSimpleBlockItem(ModBlocks.AMMONIA_HEAT_PIPE);

    /** Compressed ammonia off a mined gas pocket — the real crafting ingredient that charges
     * an ammonia heat pipe. See design/ammonia-heat-pipes.md §3. */
    public static final DeferredItem<Item> AMMONIA_CANISTER = ITEMS.registerSimpleItem("ammonia_canister");

    public static final DeferredItem<play.xponer.astronima.item.AmmoniaCanisterItem>
            AMMONIA_CANISTER_EMPTY = ITEMS.registerItem("ammonia_canister_empty",
                    play.xponer.astronima.item.AmmoniaCanisterItem::new, p -> p.stacksTo(1));

    /** Rendered off {@code sludge} well below cracking temperature — the tower's own reaction
     *  needs enough heat to break carbon-carbon bonds, and this needs far less. See
     *  design/phase-change-blocks.md §3. */
    public static final DeferredItem<Item> PARAFFIN_WAX = ITEMS.registerSimpleItem("paraffin_wax");

    public static final DeferredItem<?> PARAFFIN_THERMAL_MASS =
            ITEMS.registerSimpleBlockItem(ModBlocks.PARAFFIN_THERMAL_MASS);

    /**
     * A printed part off the SLS printer, carrying its own persisted soundness — the same "the
     * item remembers how it was made" shape {@code METAL_QUALITY} already uses for a forged tool
     * head. Present but weak when printed outside the sound pocket, exactly as
     * {@code design/sls.md} §S1 describes.
     */
    public static final DeferredItem<Item> SINTERED_FRAME = ITEMS.registerSimpleItem("sintered_frame");

    /**
     * Ferromagnetic soles. Worn, and useless off a metal deck.
     *
     * <p>They work here for a reason three tiers upstream: this mod's metallurgy is
     * iron-nickel, so its hull plate is ferrous. Real magnetic boots are shipyard equipment
     * and do <em>not</em> work on the ISS, which is built of aluminium.
     */
    public static final DeferredItem<Item> MAGNETIC_BOOTS =
            ITEMS.registerSimpleItem("magnetic_boots", p -> p.stacksTo(1));
    public static final DeferredItem<?> OXYGEN_CANDLE = ITEMS.registerSimpleBlockItem(ModBlocks.OXYGEN_CANDLE);
    /** One dose of one antimicrobial; which drug rides on the stack. */
    public static final DeferredItem<play.xponer.astronima.item.DoseItem> DOSE =
            ITEMS.registerItem("dose", play.xponer.astronima.item.DoseItem::new,
                    p -> p.stacksTo(8));

    public static final DeferredItem<?> SYNTHESISER =
            ITEMS.registerSimpleBlockItem(ModBlocks.SYNTHESISER);

    public static final DeferredItem<?> MICROSCOPE =
            ITEMS.registerSimpleBlockItem(ModBlocks.MICROSCOPE);

    public static final DeferredItem<?> INCUBATOR =
            ITEMS.registerSimpleBlockItem(ModBlocks.INCUBATOR);

    public static final DeferredItem<?> DECON_STATION =
            ITEMS.registerSimpleBlockItem(ModBlocks.DECON_STATION);
    public static final DeferredItem<?> SCRUBBER = ITEMS.registerSimpleBlockItem(ModBlocks.SCRUBBER);

    // useBlockDescriptionPrefix is what registerSimpleBlockItem does for us; a
    // hand-registered block item must ask for it or it looks up item.* and shows
    // its raw id instead of the block's name.
    public static final DeferredItem<DoubleHighBlockItem> BULKHEAD_DOOR = ITEMS.registerItem("bulkhead_door",
            p -> new DoubleHighBlockItem(ModBlocks.BULKHEAD_DOOR.get(), p),
            p -> p.useBlockDescriptionPrefix());

    // Tools & consumables
    public static final DeferredItem<GasAnalyzerItem> GAS_ANALYZER = ITEMS.registerItem("gas_analyzer",
            GasAnalyzerItem::new, p -> p.stacksTo(1));

    /**
     * Fitted into the suit or a room scrubber and consumed in place, so a partly used
     * cartridge is a real object rather than a lost one. Durability is its remaining
     * CO2 capacity, sized in sealed-suit seconds (design/suit.md §3.4).
     */
    public static final DeferredItem<LiohCartridgeItem> LITHIUM_HYDROXIDE_CARTRIDGE =
            ITEMS.registerItem("lithium_hydroxide_cartridge", LiohCartridgeItem::new,
                    p -> p.stacksTo(1).durability(1200));

    /** Crushed NaClO3 — crafting input for oxygen candles; drops from chlorate ore. */
    public static final DeferredItem<Item> CHLORATE_POWDER = ITEMS.registerSimpleItem("chlorate_powder");

    /**
     * Evaporite salts sifted from regolith. Carbonaceous chondrites carry brine
     * residues rich in alkali salts — the mod's lithium source for scrubber chemistry.
     */
    public static final DeferredItem<Item> MINERAL_SALTS = ITEMS.registerSimpleItem("mineral_salts");

    /** Compressed breathing oxygen; damage tracks remaining gas (see OxygenTanks). */
    public static final DeferredItem<Item> OXYGEN_TANK = ITEMS.registerItem("oxygen_tank",
            Item::new, p -> p.stacksTo(1).durability(OxygenTanks.TANK_MAX_DAMAGE));

    public static final DeferredItem<EmptyOxygenTankItem> OXYGEN_TANK_EMPTY = ITEMS.registerItem("oxygen_tank_empty",
            EmptyOxygenTankItem::new, p -> p.stacksTo(1));

    /**
     * Combustible organic aggregate from the carbonaceous rock (real: C-chondrites
     * hold a few percent organic carbon). The asteroid's coal; furnace fuel via the
     * neoforge:furnace_fuels data map.
     */
    public static final DeferredItem<Item> THOLIN_CLUMP = ITEMS.registerSimpleItem("tholin_clump");

    /** Stick-substitute for tool handles — there is no wood on an asteroid. */
    /**
     * Crushed ore. Carries the grade it came from and the setting it was ground at,
     * because both decide what the separator can get out of it.
     */
    public static final DeferredItem<CrushedOreItem> CRUSHED_ORE =
            ITEMS.registerItem("crushed_ore", CrushedOreItem::new, p -> p.stacksTo(32));

    /**
     * Crushed ilmenite: the fluidized bed's feed. Carries the grind it was ground at (a
     * {@code GRIND_FINENESS} component), because the drum speed that fluidizes it slides with the
     * grain size — a batch whose only difference is a hidden component would be the invisible
     * processing rule 9 exists for, so its tooltip shows the µm and the spin hint.
     */
    public static final DeferredItem<CrushedIlmeniteItem> CRUSHED_ILMENITE =
            ITEMS.registerItem("crushed_ilmenite", CrushedIlmeniteItem::new, p -> p.stacksTo(32));

    /** Reduced iron powder off the fluidized bed. Sinters/smelts to an iron ingot. */
    public static final DeferredItem<Item> IRON_POWDER = ITEMS.registerSimpleItem("iron_powder");

    /** Titania (TiO₂): the ilmenite's titanium half, left behind by the reduction. */
    public static final DeferredItem<Item> TITANIA = ITEMS.registerSimpleItem("titania");

    /** MgO: breunnerite's real calcination residue off the solar retort, and the real flux
     *  gangue-removal chemistry needs (design/carbonate-calcination.md). */
    public static final DeferredItem<Item> MAGNESIUM_OXIDE = ITEMS.registerSimpleItem("magnesium_oxide");

    /** Real magnesium silicate (MgSiO3) off the iron smelter's own fluxing reaction — the
     *  "slagging" PLAN.md's own v0.33 line names (design/iron-smelter.md). */
    public static final DeferredItem<Item> SLAG = ITEMS.registerSimpleItem("slag");

    /**
     * Retunes an electrolysis cell to silicon dioxide's own decomposition voltage.
     *
     * <p>An installed, swappable component — not a reagent the cell burns through. See
     * {@code design/electrolysis.md} §2: the cell's own default (no electrode at all)
     * already reaches iron; this is what a player crafts to reach past it.
     */
    public static final DeferredItem<Item> SILICON_ELECTRODE =
            ITEMS.registerSimpleItem("silicon_electrode");

    /** As {@link #SILICON_ELECTRODE}, tuned to aluminium oxide's harder voltage. */
    public static final DeferredItem<Item> ALUMINUM_ELECTRODE =
            ITEMS.registerSimpleItem("aluminum_electrode");

    /** Pure silicon, off the electrolysis cell's cathode with a silicon electrode installed. */
    public static final DeferredItem<Item> SILICON = ITEMS.registerSimpleItem("silicon");

    /** Pure aluminium, off the electrolysis cell's cathode with an aluminum electrode installed. */
    public static final DeferredItem<Item> ALUMINUM = ITEMS.registerSimpleItem("aluminum");

    /**
     * Native iron-nickel grains: metal already, no smelting involved.
     *
     * <p>This is the item the whole mechanical tier exists to produce, and the reason
     * a castaway with no oxygen can still make tools. Meteoric iron was worked
     * millennia before anyone could smelt anything.
     */
    public static final DeferredItem<Item> IRON_NICKEL_GRAINS =
            ITEMS.registerSimpleItem("iron_nickel_grains");

    /**
     * Platinum, palladium, iridium, osmium, ruthenium and rhodium, as one item — real iron
     * meteorites report them as a group, native alloy inclusions in the metal phase rather than
     * six separately findable minerals ({@code sim/ore/Mineral#PLATINUM_GROUP}).
     *
     * <p><strong>Registered ahead of its own extraction path, deliberately.</strong>
     * {@code design/platinum-group.md} works out the real winnowing-table mechanic this is meant
     * for, but wiring it hits a live, already-known GUI gap
     * ({@code menu/ProcessingMenu}'s winnower slot count) that this mod's own interface rework
     * is the right place to fix, not a side effect of adding one item. Honestly unreachable for
     * now — see {@code JeiCoverageTest.DELIBERATELY_BARE} — the same state {@code aluminum} and
     * {@code water_ice} already sit in for their own pending uses.
     */
    public static final DeferredItem<Item> PLATINUM_GROUP_GRAINS =
            ITEMS.registerSimpleItem("platinum_group_grains");

    /**
     * Grains cold welded into a solid piece. Only makeable in vacuum, which is the
     * whole reason this metallurgy works on an airless rock.
     */
    public static final DeferredItem<Item> METAL_BILLET =
            ITEMS.registerSimpleItem("metal_billet", p -> p.stacksTo(16));

    /** A forged head, carrying how well it was worked. Fit it to a haft. */
    public static final DeferredItem<Item> TOOL_HEAD =
            ITEMS.registerSimpleItem("tool_head", p -> p.stacksTo(16));

    /**
     * The first real tool, from native meteoric iron worked cold.
     *
     * <p>Durability comes from how well the head was forged rather than from a fixed
     * number, so a careless smith gets a worse pickaxe out of the same metal.
     */
    public static final DeferredItem<WearingToolItem> METEORIC_PICKAXE =
            ITEMS.registerItem("meteoric_pickaxe", WearingToolItem::new,
                    p -> p.pickaxe(MeteoricToolMaterial.METEORIC, 1.0F, -2.8F));

    /**
     * Grains pressed into a head and hafted, with no forging at all.
     *
     * <p>A green compact: the metal is touching rather than continuous, so it is soft
     * and short-lived. It exists because it is reachable within minutes of the first
     * magnet pull, and because having one is what makes the forge worth building.
     */
    public static final DeferredItem<WearingToolItem> IMPROVISED_PICKAXE =
            ITEMS.registerItem("improvised_pickaxe", WearingToolItem::new,
                    p -> p.pickaxe(MeteoricToolMaterial.METEORIC, 0.5F, -3.0F));

    /** Meteoric shovel, so regolith no longer needs a vanilla tool. */
    public static final DeferredItem<WearingToolItem> METEORIC_SHOVEL =
            ITEMS.registerItem("meteoric_shovel", WearingToolItem::new,
                    p -> p.shovel(MeteoricToolMaterial.METEORIC, 1.0F, -3.0F));

    /** Meteoric axe, likewise. */
    public static final DeferredItem<WearingToolItem> METEORIC_AXE =
            ITEMS.registerItem("meteoric_axe", WearingToolItem::new,
                    p -> p.axe(MeteoricToolMaterial.METEORIC, 5.0F, -3.1F));

    /**
     * What a crash survivor actually has: something wedged out of the wreckage.
     *
     * <p>Deliberately not a mining tool. It opens panels and works the weakest rock,
     * slowly enough that you want something better immediately — which is the point,
     * because wanting a better tool is what sends you looking for metal.
     *
     * <p>It never wears out. A lever has no edge to lose, so there is nothing for
     * abrasive wear to take, and it is the last thing standing between a stripped
     * player and being unable to mine at all.
     */
    public static final DeferredItem<Item> PRY_BAR =
            ITEMS.registerSimpleItem("pry_bar",
                    p -> p.pickaxe(PryBarToolMaterial.SALVAGE, 0.5F, -3.2F)
                            .component(net.minecraft.core.component.DataComponents.UNBREAKABLE,
                                    net.minecraft.util.Unit.INSTANCE));

    /**
     * The bootstrap tool. Crude and quick to wear out, but it needs no metal — which
     * it cannot, because it is what you use to get the first metal.
     */
    public static final DeferredItem<Item> HAMMER_STONE =
            ITEMS.registerSimpleItem("hammer_stone", p -> p.durability(96));

    /** Spoil. Worthless now; the later tiers can still get things out of it. */
    public static final DeferredItem<Item> TAILINGS = ITEMS.registerSimpleItem("tailings");

    /**
     * Tailings with the water baked out of them: anhydrous silicate.
     *
     * <p>The retort's residue, and deliberately still useful — it packs into shielding
     * exactly as wet tailings do, so no branch of the chain is a dead end. It is also the
     * feedstock the electrolytic tier will want, which is why it is a distinct item rather
     * than "tailings again".
     */
    public static final DeferredItem<Item> BAKED_SILICATE = ITEMS.registerSimpleItem("baked_silicate");

    public static final DeferredItem<Item> IRON_ROD = ITEMS.registerSimpleItem("iron_rod");

    /**
     * A baked-silicate lining bonded to a worked-metal shell — the tier's answer to "the wall
     * that has to survive real heat," rather than the same plate every unheated build already
     * uses. Ceramic on the inside, metal on the outside: real refractory brick construction, not
     * a reskinned billet.
     */
    public static final DeferredItem<Item> REFRACTORY_LINING = ITEMS.registerSimpleItem("refractory_lining");

    /** A machined race running on seam-grade nickel rather than raw billet — what a real spinning
     *  shaft or a precision gantry actually rides on, distinct from the structural metal around
     *  it. */
    public static final DeferredItem<Item> PRECISION_BEARING = ITEMS.registerSimpleItem("precision_bearing");

    /** A salvaged circuit re-laid on a baked-silicate substrate — a real board, not loose
     *  salvage, for the handful of machines whose job is precise control rather than a simple
     *  on/off contact. */
    public static final DeferredItem<Item> CONTROL_BOARD = ITEMS.registerSimpleItem("control_board");

    /** A polyethylene gasket clamped in a worked-metal ring — the tier's actual answer to "this
     *  machine holds a gas nobody wants loose," reused everywhere that is literally true instead
     *  of asking a structural billet to also be an elastomer. */
    public static final DeferredItem<Item> GAS_SEAL = ITEMS.registerSimpleItem("gas_seal");

    /** Two plates and two rods worked into one stiffer unit — what a build under real ongoing
     *  mechanical or thermal stress needs instead of a bare hull plate. */
    public static final DeferredItem<Item> REINFORCED_FRAME = ITEMS.registerSimpleItem("reinforced_frame");

    /** Acoustic sounding tool: reports cavities (and gas signatures) behind rock faces. */
    public static final DeferredItem<SeismicProbeItem> SEISMIC_PROBE = ITEMS.registerItem("seismic_probe",
            SeismicProbeItem::new, p -> p.stacksTo(1));

    /** Rotates a directional block in place, so a pump or port need not be broken to re-aim. */
    public static final DeferredItem<WrenchItem> WRENCH = ITEMS.registerItem("wrench",
            WrenchItem::new, p -> p.stacksTo(1));

    /** Pulls wire: click a surface to take the end, click again to lay the leg to there. */
    public static final DeferredItem<WireCoilItem> WIRE_COIL = ITEMS.registerItem("wire_coil",
            WireCoilItem::new, p -> p.stacksTo(1));

    /** Lays four or eight lanes at once, as one gesture — a bus, not a repeated coil. */
    public static final DeferredItem<play.xponer.astronima.item.WireRibbonItem> WIRE_RIBBON =
            ITEMS.registerItem("wire_ribbon", play.xponer.astronima.item.WireRibbonItem::new,
                    p -> p.stacksTo(1));

    /**
     * Worn eyewear that reads a circuit off the wire you are looking at.
     *
     * <p>Rule 9's order of preference says a block shows its own state — but a <em>trace</em> has
     * no block to put a gauge on, and one pixel of conductor cannot carry a readout. So the
     * instrument goes on the engineer instead, which is also where a real multimeter lives.
     */
    public static final DeferredItem<Item> DIAGNOSTIC_GOGGLES =
            ITEMS.registerSimpleItem("diagnostic_goggles", () -> new Item.Properties().stacksTo(1));

    /**
     * The biomonitor chip ladder's first rung, design/biomonitor-chips.md. A plain data-carrier
     * item, exactly like {@link #DIAGNOSTIC_GOGGLES} and {@link #MAGNETIC_BOOTS} — nothing about
     * it ticks or has server state of its own; the biomonitor simply reads whether one is fitted
     * in the {@code implant} Curios slot at draw time.
     */
    public static final DeferredItem<Item> BASIC_BIOMONITOR_CHIP =
            ITEMS.registerSimpleItem("basic_biomonitor_chip", p -> p.stacksTo(1));

    /** The ladder's second rung: reveals a working designator for an otherwise-unidentified
     *  infection, and nothing else — see design/biomonitor-chips.md §2/§4. */
    public static final DeferredItem<Item> PATHOGEN_ANALYZER_CHIP =
            ITEMS.registerSimpleItem("pathogen_analyzer_chip", p -> p.stacksTo(1));

    /**
     * Every logic part, registered from one set rather than one line each (rule 20).
     *
     * <p>Eight near-identical registrations is a checklist, and this project has three separate
     * records of a checklist being missed — a screen, a gametest's own registration file, a
     * machine's lamp — each time in the one place no gate looked at. {@code PartType} publishes
     * the set; registration, the creative tab, datagen, JEI and the language file all walk it.
     *
     * <p>A {@code LinkedHashMap} rather than an array so lookup is by type and the order is still
     * the enum's, which is what the creative tab and JEI want.
     */
    public static final Map<PartType, DeferredItem<LogicPartItem>> PARTS = registerParts();

    private static Map<PartType, DeferredItem<LogicPartItem>> registerParts() {
        Map<PartType, DeferredItem<LogicPartItem>> found = new LinkedHashMap<>();
        for (PartType type : PartType.values()) {
            found.put(type, ITEMS.registerItem(type.id(),
                    properties -> new LogicPartItem(properties, type),
                    properties -> properties.stacksTo(type == PartType.PLATE || type == PartType.MACRO_PLATE ? 1 : 16)));
        }
        return found;
    }

    /** The item for a part, for dropping one back when its wall comes down. */
    public static Item part(PartType type) {
        return PARTS.get(type).get();
    }

    /**
     * A Petri dish, and the notebook the whole laboratory writes in.
     *
     * <p><strong>Blank dishes stack; cultured ones do not</strong>, and that falls out rather than
     * being enforced: two stacks with different components never merge, so the moment something is
     * streaked onto a plate it becomes its own item. Forcing a stack size of one would have made
     * carrying a box of clean glassware as awkward as carrying a box of samples, for no reason —
     * and a guard caught it as soon as the recipe made four at a time.
     */
    public static final DeferredItem<play.xponer.astronima.item.PetriDishItem> PETRI_DISH =
            ITEMS.registerItem("petri_dish",
                    play.xponer.astronima.item.PetriDishItem::new, p -> p.stacksTo(16));

    /** Takes wire back out, and the metal with it. */
    public static final DeferredItem<WireCutterItem> WIRE_CUTTERS = ITEMS.registerItem(
            "wire_cutters", WireCutterItem::new, p -> p.stacksTo(1));

    /** Takes one length out of a run without tearing out the whole circuit. */
    public static final DeferredItem<play.xponer.astronima.item.WireSnipsItem> WIRE_SNIPS =
            ITEMS.registerItem("wire_snips", play.xponer.astronima.item.WireSnipsItem::new,
                    p -> p.stacksTo(1));

    public static final DeferredItem<?> PRESENCE_SENSOR =
            ITEMS.registerSimpleBlockItem(ModBlocks.PRESENCE_SENSOR);
    public static final DeferredItem<?> VACUUM_SENSOR =
            ITEMS.registerSimpleBlockItem(ModBlocks.VACUUM_SENSOR);
    public static final DeferredItem<?> STARVING_SENSOR =
            ITEMS.registerSimpleBlockItem(ModBlocks.STARVING_SENSOR);
    public static final DeferredItem<?> FREEZING_SENSOR =
            ITEMS.registerSimpleBlockItem(ModBlocks.FREEZING_SENSOR);
    public static final DeferredItem<?> ALARM = ITEMS.registerSimpleBlockItem(ModBlocks.ALARM);
    public static final DeferredItem<?> PRE_BREATHE_STATION =
            ITEMS.registerSimpleBlockItem(ModBlocks.PRE_BREATHE_STATION);
    public static final DeferredItem<?> GAS_PIPE = ITEMS.registerSimpleBlockItem(ModBlocks.GAS_PIPE);
    public static final DeferredItem<?> TELESCOPE = ITEMS.registerSimpleBlockItem(ModBlocks.TELESCOPE);
    public static final DeferredItem<?> GAS_PORT = ITEMS.registerSimpleBlockItem(ModBlocks.GAS_PORT);
    public static final DeferredItem<?> GAS_PUMP = ITEMS.registerSimpleBlockItem(ModBlocks.GAS_PUMP);
    public static final DeferredItem<?> GAS_TANK = ITEMS.registerSimpleBlockItem(ModBlocks.GAS_TANK);
    public static final DeferredItem<?> GAS_VALVE = ITEMS.registerSimpleBlockItem(ModBlocks.GAS_VALVE);
    public static final DeferredItem<?> AIRLOCK_CONTROLLER =
            ITEMS.registerSimpleBlockItem(ModBlocks.AIRLOCK_CONTROLLER);
    public static final DeferredItem<?> DEHUMIDIFIER = ITEMS.registerSimpleBlockItem(ModBlocks.DEHUMIDIFIER);
    public static final DeferredItem<?> GLOW_STICK = ITEMS.registerSimpleBlockItem(ModBlocks.GLOW_STICK);

    public static final DeferredItem<?> UNLIT_TORCH = ITEMS.registerSimpleBlockItem(ModBlocks.UNLIT_TORCH);

    /**
     * Ferrocerium striker: scraping it throws sparks hot enough to light a flame,
     * with no flint required — there is none on an asteroid, and no fuel to burn in
     * a match. The igniter for torches and oxygen candles.
     */
    public static final DeferredItem<Item> STRIKER = ITEMS.registerItem("striker",
            Item::new, p -> p.stacksTo(1).durability(128));

    /** The suit itself; its repair state rides on the stack. */
    public static final DeferredItem<EvaSuitItem> EVA_SUIT = ITEMS.registerItem("eva_suit",
            EvaSuitItem::new, p -> p.stacksTo(1));

    // One part per subsystem: using a part on the suit fixes exactly that fault.
    public static final DeferredItem<SuitRepairItem> SEALANT_PATCH = repairPart("sealant_patch",
            SuitSubsystem.HELMET_SEAL);
    public static final DeferredItem<SuitRepairItem> LATCH_SET = repairPart("latch_set",
            SuitSubsystem.TANK_MOUNT);
    public static final DeferredItem<SuitRepairItem> CALIBRATED_VALVE = repairPart("calibrated_valve",
            SuitSubsystem.REGULATOR);
    // Also mends sealed gloves: the same fabric weave patches the thermal lining and a
    // torn glove, so this is one item, not two — it opens the bench for whichever of
    // the two is still broken (PLAN.md rule 57).
    public static final DeferredItem<SuitRepairItem> INSULATION_WEAVE = repairPart("insulation_weave",
            SuitSubsystem.THERMAL_LAYER, SuitSubsystem.SEALED_GLOVES);
    public static final DeferredItem<SuitRepairItem> SALVAGED_CIRCUIT = repairPart("salvaged_circuit",
            SuitSubsystem.STATUS_DISPLAY);

    private static DeferredItem<SuitRepairItem> repairPart(String name, SuitSubsystem... subsystems) {
        return ITEMS.registerItem(name, p -> new SuitRepairItem(p, subsystems), p -> p.stacksTo(16));
    }

    public static final DeferredItem<play.xponer.astronima.item.SpectrographItem> SPECTROGRAPH =
            ITEMS.registerItem("spectrograph", play.xponer.astronima.item.SpectrographItem::new,
                    p -> p.stacksTo(1));
    public static final DeferredItem<play.xponer.astronima.item.SpectralPlateItem> SPECTRAL_PLATE =
            ITEMS.registerItem("spectral_plate", play.xponer.astronima.item.SpectralPlateItem::new,
                    p -> p.stacksTo(16));
    public static final DeferredItem<play.xponer.astronima.item.CoherenceMeterItem> COHERENCE_METER =
            ITEMS.registerItem("coherence_meter", play.xponer.astronima.item.CoherenceMeterItem::new,
                    p -> p.stacksTo(1));
    public static final DeferredItem<play.xponer.astronima.item.AstraFieldMeterItem> ASTRA_FIELD_METER =
            ITEMS.registerItem("astra_field_meter", play.xponer.astronima.item.AstraFieldMeterItem::new,
                    p -> p.stacksTo(1));
    public static final DeferredItem<play.xponer.astronima.item.AstraCollectorItem> ASTRA_COLLECTOR =
            ITEMS.registerItem("astra_collector", play.xponer.astronima.item.AstraCollectorItem::new,
                    p -> p.stacksTo(1));
    public static final DeferredItem<play.xponer.astronima.item.AstraSounderItem> ASTRA_SOUNDER =
            ITEMS.registerItem("astra_sounder", play.xponer.astronima.item.AstraSounderItem::new,
                    p -> p.stacksTo(1));

    /**
     * The materials roster's root node (design/astra-core.md §6, resolved 2026-08-30) —
     * precipitated from concentrated astra (design/astra-precipitation.md), never mined. Its own
     * consumers (astra-bearing alloys) are named but not designed yet — see
     * {@code NoDeadEndsTest.USE_PENDING}.
     */
    public static final DeferredItem<Item> ASTERIUM_GRAINS =
            ITEMS.registerSimpleItem("asterium_grains");

    /**
     * Filter tokens, one per real {@link play.xponer.astronima.sim.magic.SpectralLine} catalogue
     * entry (design/astra-research-m4b.md §1/§3) — registered from the enum itself, the same
     * checklist reasoning {@link #PARTS} is already given: a ninth catalogue line should not be
     * able to ship without its own token because a ninth hand-typed registration was forgotten.
     */
    public static final Map<play.xponer.astronima.sim.magic.SpectralLine,
            DeferredItem<play.xponer.astronima.item.FilterTokenItem>> FILTER_TOKENS = registerFilterTokens();

    private static Map<play.xponer.astronima.sim.magic.SpectralLine,
            DeferredItem<play.xponer.astronima.item.FilterTokenItem>> registerFilterTokens() {
        Map<play.xponer.astronima.sim.magic.SpectralLine,
                DeferredItem<play.xponer.astronima.item.FilterTokenItem>> found = new LinkedHashMap<>();
        for (play.xponer.astronima.sim.magic.SpectralLine line
                : play.xponer.astronima.sim.magic.SpectralLine.values()) {
            found.put(line, ITEMS.registerItem(line.id(),
                    properties -> new play.xponer.astronima.item.FilterTokenItem(properties, line),
                    properties -> properties.stacksTo(16)));
        }
        return found;
    }

    /** The filter token item for a line, for anything that needs to look one up (§3's decode
     *  payload: reading which line the player's held filter is ground for). */
    public static Item filterToken(play.xponer.astronima.sim.magic.SpectralLine line) {
        return FILTER_TOKENS.get(line).get();
    }

    /**
     * The tier-2 lens (design/astra-research.md §2b, built design/astra-atlas-s3d-unlocks.md
     * §4.1): a bigger objective, real optics — more aperture is more gathered light, not more
     * magnification, which is why this makes a genuinely faint object resolvable rather than a
     * captured one merely bigger. Gated on {@code same_elements} in {@code CraftingTree}, not
     * {@code metal_assay} — {@code metal_assay}'s own two objects (Andromeda, M32) are both
     * tier 2 themselves, so gating this lens behind that claim would make it permanently
     * unreachable the moment lens-tier is actually enforced; {@code same_elements} needs only
     * tier-1 objects and is reachable from an empty world, which is what
     * {@code ResearchReachabilityTest} now proves rather than assumes.
     */
    public static final DeferredItem<Item> WIDE_APERTURE_LENS =
            ITEMS.registerSimpleItem("wide_aperture_lens", p -> p.stacksTo(1));

    // No recipe (design/astra-research.md's ritual note): earned in-world once the ritual
    // system exists, not craftable in the meantime.
    public static final DeferredItem<play.xponer.astronima.item.CelestialAtlasItem> CELESTIAL_ATLAS =
            ITEMS.registerItem("celestial_atlas", play.xponer.astronima.item.CelestialAtlasItem::new,
                    p -> p.stacksTo(1));

    public static final DeferredItem<?> CRYO_TANK = ITEMS.registerSimpleBlockItem(ModBlocks.CRYO_TANK);
    public static final DeferredItem<?> CRYO_COOLER =
            ITEMS.registerSimpleBlockItem(ModBlocks.CRYO_COOLER);
    public static final DeferredItem<?> FREEZE_DRYER =
            ITEMS.registerSimpleBlockItem(ModBlocks.FREEZE_DRYER);

    /** The freeze dryer's product, and the cryo tank's own insulation upgrade (design/cryogenics.md
     * §5) — right-clicked against a placed {@code CryoTankBlock} to wrap it, consumed once. */
    public static final DeferredItem<Item> SILICA_AEROGEL = ITEMS.registerSimpleItem("silica_aerogel");

    /** A sealed radioisotope capsule, salvaged rather than manufactured (design/radiation.md §6)
     * — a rare find in metal_rich_ore, wreck debris fused into the seam on impact. */
    public static final DeferredItem<Item> RTG_CORE = ITEMS.registerSimpleItem("rtg_core");

    public static final DeferredItem<?> RTG = ITEMS.registerSimpleBlockItem(ModBlocks.RTG);

    /** A held instrument reading real gamma dose rate and accumulated body dose
     * (design/radiation.md §4) — mirrors {@code GasAnalyzerItem} exactly. */
    public static final DeferredItem<play.xponer.astronima.item.GeigerCounterItem> GEIGER_COUNTER =
            ITEMS.registerItem("geiger_counter",
                    play.xponer.astronima.item.GeigerCounterItem::new, p -> p.stacksTo(1));

    public static final DeferredItem<?> HALITE_ORE =
            ITEMS.registerSimpleBlockItem(ModBlocks.HALITE_ORE);

    public static final DeferredItem<?> DOWNS_CELL =
            ITEMS.registerSimpleBlockItem(ModBlocks.DOWNS_CELL);

    /** Real, reactive sodium metal off the Downs cell — design/halogens.md §1.3/§4. */
    public static final DeferredItem<play.xponer.astronima.item.SodiumItem> SODIUM =
            ITEMS.registerItem("sodium", play.xponer.astronima.item.SodiumItem::new, p -> p);

    public static final DeferredItem<?> ZONE_REFINER =
            ITEMS.registerSimpleBlockItem(ModBlocks.ZONE_REFINER);

    /** A real digital manifest, not a bag of holding — design/data-cells.md §1-3. */
    public static final DeferredItem<play.xponer.astronima.item.DataCellItem> DATA_CELL =
            ITEMS.registerItem("data_cell", play.xponer.astronima.item.DataCellItem::new,
                    p -> p.stacksTo(1));

    public static final DeferredItem<?> FLUORITE_ORE =
            ITEMS.registerSimpleBlockItem(ModBlocks.FLUORITE_ORE);

    public static final DeferredItem<?> HF_DIGESTER =
            ITEMS.registerSimpleBlockItem(ModBlocks.HF_DIGESTER);

    /** Real hydrofluoric acid off the digester — design/halogens.md §15/§17 (Part C1) for its
     *  chemistry, §22-25 (Part C2) for the real contact hazard it now carries. */
    public static final DeferredItem<play.xponer.astronima.item.HydrofluoricAcidItem> HYDROFLUORIC_ACID =
            ITEMS.registerItem("hydrofluoric_acid", play.xponer.astronima.item.HydrofluoricAcidItem::new, p -> p);

    /** Real gypsum (CaSO4), the digester's own byproduct. */
    public static final DeferredItem<Item> GYPSUM = ITEMS.registerSimpleItem("gypsum");

    /** Real HEPA blower/positive-pressure unit — design/halogens.md §33 (Part C3). */
    public static final DeferredItem<?> CLEANROOM_CONTROLLER =
            ITEMS.registerSimpleBlockItem(ModBlocks.CLEANROOM_CONTROLLER);

    /** Fed whole into the controller and consumed on the spot — design/halogens.md §33. Unlike
     *  {@link #LITHIUM_HYDROXIDE_CARTRIDGE} this has exactly one job, so it never exists half-used
     *  in an inventory and needs no durability component. */
    public static final DeferredItem<Item> HEPA_FILTER = ITEMS.registerSimpleItem("hepa_filter");

    /** Real wet oxide etching — design/halogens.md §40/§42 (Part C4). */
    public static final DeferredItem<?> ETCH_STATION =
            ITEMS.registerSimpleBlockItem(ModBlocks.ETCH_STATION);

    /** This mod's real electronics gate — design/halogens.md §45 (Part C4). Inert until v0.81's
     *  smart visor / v0.82's SCADA give it a real forward consumer; not a decoration. */
    public static final DeferredItem<Item> ETCHED_DIE = ITEMS.registerSimpleItem("etched_die");

    /** Real H2SiF6, the etch station's own real byproduct — design/halogens.md §40/§45. */
    public static final DeferredItem<Item> FLUOROSILICIC_ACID =
            ITEMS.registerSimpleItem("fluorosilicic_acid");

    public static final DeferredItem<?> STORAGE_FRAME =
            ITEMS.registerSimpleBlockItem(ModBlocks.STORAGE_FRAME);

    public static final DeferredItem<?> STORAGE_DRIVE =
            ITEMS.registerSimpleBlockItem(ModBlocks.STORAGE_DRIVE);

    public static final DeferredItem<?> STORAGE_TERMINAL =
            ITEMS.registerSimpleBlockItem(ModBlocks.STORAGE_TERMINAL);

    /** Applied directly to a loaded data_cell to raise its own compression tier in place — see
     *  design/data-cells.md §15. */
    public static final DeferredItem<play.xponer.astronima.item.CellUpgradeItem> CELL_COMPRESSOR =
            ITEMS.registerItem("cell_compressor", play.xponer.astronima.item.CellUpgradeItem::new, p -> p);

    private ModItems() {}
}
