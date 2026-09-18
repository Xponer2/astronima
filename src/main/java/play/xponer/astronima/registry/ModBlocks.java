package play.xponer.astronima.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.block.AlarmBlock;
import play.xponer.astronima.block.EnvironmentSensorBlock;
import play.xponer.astronima.block.PresenceSensorBlock;
import play.xponer.astronima.block.BulkheadDoorBlock;
import play.xponer.astronima.block.GasPipeBlock;
import play.xponer.astronima.block.GrabRailBlock;
import play.xponer.astronima.block.CarbonylRefinerBlock;
import play.xponer.astronima.block.ElectrolysisCellBlock;
import play.xponer.astronima.block.FluidizedBedBlock;
import play.xponer.astronima.block.CombustionGeneratorBlock;
import play.xponer.astronima.block.FuelCellBlock;
import play.xponer.astronima.block.PowerCableBlock;
import play.xponer.astronima.block.PowerCellBlock;
import play.xponer.astronima.block.PurgeValveBlock;
import play.xponer.astronima.block.SolarArrayBlock;
import play.xponer.astronima.block.GasPumpBlock;
import play.xponer.astronima.block.GasTankBlock;
import play.xponer.astronima.block.GasValveBlock;
import play.xponer.astronima.block.AirlockControllerBlock;
import play.xponer.astronima.block.GasPortBlock;
import play.xponer.astronima.block.DehumidifierBlock;
import play.xponer.astronima.block.GasPocketCoreBlock;
import play.xponer.astronima.block.GlowStickBlock;
import play.xponer.astronima.block.MoldBlock;
import play.xponer.astronima.block.OxygenCandleBlock;
import play.xponer.astronima.block.PreBreatheStationBlock;
import play.xponer.astronima.block.ColdForgeBlock;
import play.xponer.astronima.block.CargoCrateBlock;
import play.xponer.astronima.block.MagneticSeparatorBlock;
import play.xponer.astronima.block.WinnowingTableBlock;
import play.xponer.astronima.block.OreCrusherBlock;
import play.xponer.astronima.block.ScrubberBlock;
import play.xponer.astronima.block.SolarRetortBlock;
import play.xponer.astronima.block.UnlitTorchBlock;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Astronima.MODID);

    /** Carbonaceous asteroid bedrock-equivalent: the bulk material of the world. */
    public static final DeferredBlock<Block> ASTEROID_ROCK = BLOCKS.registerSimpleBlock("asteroid_rock",
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(2.5f, 8.0f).sound(SoundType.STONE));

    /** Loose impact-gardened surface rubble; fast to dig, useless for sealing until sintered. */
    public static final DeferredBlock<Block> REGOLITH = BLOCKS.registerSimpleBlock("regolith",
            p -> p.mapColor(MapColor.COLOR_GRAY).strength(0.6f).sound(SoundType.GRAVEL));

    /** Subsurface water-ice lens: the strategic resource (drinking, electrolysis, hydroponics). */
    public static final DeferredBlock<Block> WATER_ICE = BLOCKS.registerSimpleBlock("water_ice",
            p -> p.mapColor(MapColor.ICE).strength(1.0f).sound(SoundType.GLASS).friction(0.98f));

    /** Sodium chlorate (NaClO3) crystal vein — the raw material of oxygen candles. */
    public static final DeferredBlock<Block> CHLORATE_ORE = BLOCKS.registerSimpleBlock("chlorate_ore",
            p -> p.mapColor(MapColor.SNOW).strength(2.0f, 4.0f).sound(SoundType.STONE));

    /** Hematite (Fe2O3): iron and — via hydrogen reduction, later — oxygen. */
    /**
     * A seam of native iron-nickel. The prospecting target of the whole first tier:
     * chondrites carry a few percent metal everywhere, but a seam carries enough that
     * one afternoon of crushing is worth a day of walking.
     */
    public static final DeferredBlock<Block> METAL_RICH_ORE = BLOCKS.registerSimpleBlock("metal_rich_ore",
            p -> p.mapColor(MapColor.METAL).strength(3.4f, 6.0f).sound(SoundType.STONE));

    /**
     * Nine `asterium_grains` compressed — design/astra-ritual-grammar.md §1's anchor material,
     * the same "raw material into a solid block" step every metal in this mod already has, given
     * to the tier's own first precipitated matter (design/astra-precipitation.md). Dense and
     * strong, matching astra-core.md §2.1's own description of asterium itself.
     */
    public static final DeferredBlock<Block> ASTERIUM_BLOCK = BLOCKS.registerSimpleBlock("asterium_block",
            p -> p.mapColor(MapColor.COLOR_BLUE).strength(4.0f, 8.0f).sound(SoundType.METAL));

    /** The ritual's focus — design/astra-ritual-grammar.md §1/§2. See
     *  {@code AstraAltarBlockEntity} for the real structure detection and live draw. */
    public static final DeferredBlock<play.xponer.astronima.block.AstraAltarBlock> ASTRA_ALTAR =
            BLOCKS.registerBlock("astra_altar", play.xponer.astronima.block.AstraAltarBlock::new,
                    p -> p.mapColor(MapColor.COLOR_BLUE).strength(3.5f, 6.0f).sound(SoundType.STONE));

    /** Hand jaw crusher: the first machine, and the first real decision. */
    public static final DeferredBlock<OreCrusherBlock> ORE_CRUSHER = BLOCKS.registerBlock("ore_crusher",
            OreCrusherBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0f, 8.0f).sound(SoundType.METAL));

    /**
     * A cold forge. Not a furnace: there is no fire on this rock, and none is needed —
     * in vacuum, clean metal cold welds to itself.
     */
    public static final DeferredBlock<ColdForgeBlock> COLD_FORGE =
            BLOCKS.registerBlock("cold_forge", ColdForgeBlock::new,
                    p -> p.mapColor(MapColor.METAL).strength(3.5f, 9.0f).sound(SoundType.METAL));

    /**
     * Solar retort: bakes the bound water out of hydrated rock with a concentrating mirror.
     *
     * <p>No power and no oxygen, which is what makes it a tier-1 machine — see
     * design/water-from-rock.md. Set into a roof, because the mirror needs sky and the
     * steam needs a sealed room on the other side.
     */
    public static final DeferredBlock<SolarRetortBlock> SOLAR_RETORT =
            BLOCKS.registerBlock("solar_retort", SolarRetortBlock::new,
                    p -> p.mapColor(MapColor.METAL).strength(3.0f, 8.0f).sound(SoundType.METAL));

    /** Hand drum magnet: costs nothing but motion, which is why it works out here. */
    public static final DeferredBlock<MagneticSeparatorBlock> MAGNETIC_SEPARATOR =
            BLOCKS.registerBlock("magnetic_separator", MagneticSeparatorBlock::new,
                    p -> p.mapColor(MapColor.METAL).strength(3.0f, 8.0f).sound(SoundType.METAL));

    /**
     * Density separation by gas stream: the second axis tier 1 can reach, and the one that
     * recovers the nickel ore a magnet cannot touch. Needs air, so it is an indoor machine.
     */
    public static final DeferredBlock<WinnowingTableBlock> WINNOWING_TABLE =
            BLOCKS.registerBlock("winnowing_table", WinnowingTableBlock::new,
                    p -> p.mapColor(MapColor.METAL).strength(3.0f, 8.0f).sound(SoundType.METAL));

    /**
     * Storage for a world with no wood — and a full cube, so a wall of crates seals.
     */
    public static final DeferredBlock<CargoCrateBlock> CARGO_CRATE =
            BLOCKS.registerBlock("cargo_crate", CargoCrateBlock::new,
                    p -> p.mapColor(MapColor.METAL).strength(2.5f, 6.0f).sound(SoundType.METAL));

    public static final DeferredBlock<Block> HEMATITE_ORE = BLOCKS.registerSimpleBlock("hematite_ore",
            p -> p.mapColor(MapColor.COLOR_RED).strength(3.0f, 6.0f).sound(SoundType.STONE));

    /**
     * Rock salt — near-pure NaCl, the Downs cell's own feedstock. See
     * {@code design/halogens.md} §2. Scattered near the ice lenses it evaporated out of.
     */
    public static final DeferredBlock<Block> HALITE_ORE = BLOCKS.registerSimpleBlock("halite_ore",
            p -> p.mapColor(MapColor.SNOW).strength(2.0f, 6.0f).sound(SoundType.AMETHYST_CLUSTER));

    /**
     * Fluorite (CaF2) — a real hydrothermal vein mineral, the HF digester's own feedstock. See
     * {@code design/halogens.md} §16. Deep rock, alongside ilmenite's own vein-mineral band, not
     * the ice/salt band halite shares.
     */
    public static final DeferredBlock<Block> FLUORITE_ORE = BLOCKS.registerSimpleBlock(
            "fluorite_ore",
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(2.5f, 6.0f).sound(SoundType.AMETHYST_CLUSTER));

    /**
     * Rammed separator spoil. Dense, dull and genuinely useful: crushed rock is what
     * real habitat designs put between people and radiation, because shielding is mass
     * and mass is the one thing an asteroid has in abundance.
     */
    public static final DeferredBlock<Block> PACKED_TAILINGS =
            BLOCKS.registerSimpleBlock("packed_tailings",
                    p -> p.mapColor(MapColor.STONE).strength(2.2f, 8.0f).sound(SoundType.STONE));

    /** Fabricated airtight plating — the standard wall of a built habitat. */
    public static final DeferredBlock<Block> HULL_PLATE = BLOCKS.registerSimpleBlock("hull_plate",
            p -> p.mapColor(MapColor.METAL).strength(3.0f, 12.0f).sound(SoundType.METAL));

    /**
     * Hull plate with an evacuated powder jacket - the answer to a habitat that radiates.
     *
     * <p>Real, and older than multi-layer insulation: a jacket packed with fine powder and
     * pumped down is one of the best insulators there is, and it is how cryogenic vessels
     * were built before MLI. Vacuum kills convection, the powder scatters the radiation that
     * would otherwise cross the gap, and the grains touch each other so sparsely that
     * conduction has almost no path. It is also the one insulator an asteroid can actually
     * make: no polymers, no foam, just fine rock and the vacuum already outside the door.
     *
     * <p>So the feedstock is <strong>tailings</strong> - the waste stream of the whole ore
     * chain, which until now only fed the retort. The finer the grind, the better real
     * powder insulation performs, which is the crusher's dial earning a second meaning the
     * way the winnowing table gave it one.
     */
    public static final DeferredBlock<Block> INSULATED_HULL_PLATE =
            BLOCKS.registerSimpleBlock("insulated_hull_plate",
                    p -> p.mapColor(MapColor.METAL).strength(3.0f, 12.0f)
                            .sound(SoundType.METAL));

    /**
     * Hull plate finished with a white TiO2 pigment — see {@code design/albedo-paint.md}. A
     * real, independent property from insulation, so this and {@link #PAINTED_INSULATED_HULL_PLATE}
     * are additive alongside the two above, not replacements for them.
     */
    public static final DeferredBlock<Block> PAINTED_HULL_PLATE =
            BLOCKS.registerSimpleBlock("painted_hull_plate",
                    p -> p.mapColor(MapColor.SNOW).strength(3.0f, 12.0f).sound(SoundType.METAL));

    /** {@link #INSULATED_HULL_PLATE}, painted — both real properties of the same wall at once. */
    public static final DeferredBlock<Block> PAINTED_INSULATED_HULL_PLATE =
            BLOCKS.registerSimpleBlock("painted_insulated_hull_plate",
                    p -> p.mapColor(MapColor.SNOW).strength(3.0f, 12.0f).sound(SoundType.METAL));

    /**
     * A handrail — the only thing that stops someone already travelling.
     *
     * <p>Iron rod bent onto a wall. Cheap on purpose: its cost is not the metal, it is having
     * lined the shaft <em>before</em> you jumped down it.
     */
    public static final DeferredBlock<GrabRailBlock> GRAB_RAIL = BLOCKS.registerBlock("grab_rail",
            GrabRailBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(1.5f, 6.0f).sound(SoundType.CHAIN)
                    .noOcclusion().forceSolidOff());

    /**
     * The emergency vent: the only way to get a poison back out of a habitat.
     *
     * <p>Nine of the mod's eleven gases had no removal path at all before this — see
     * {@code design/contamination.md}. Real crews do not filter a spill; they dump it.
     */
    /** A square metre of photovoltaic: 37 W in full sun, and nothing at night. */
    public static final DeferredBlock<SolarArrayBlock> SOLAR_ARRAY =
            BLOCKS.registerBlock("solar_array", SolarArrayBlock::new,
                    p -> p.mapColor(MapColor.COLOR_BLUE).strength(2.0f, 6.0f)
                            .sound(SoundType.METAL));

    /**
     * Burns methane for power, and mostly for heat.
     *
     * <p>Three quarters of the fuel's energy comes out warm, so this is a stove that also makes
     * electricity — which is what a small generator actually is, and why cogeneration exists.
     */
    public static final DeferredBlock<CombustionGeneratorBlock> COMBUSTION_GENERATOR =
            BLOCKS.registerBlock("combustion_generator", CombustionGeneratorBlock::new,
                    p -> p.mapColor(MapColor.COLOR_GRAY).strength(3.5f, 10.0f)
                            .sound(SoundType.METAL));

    /**
     * Hydrogen and oxygen straight to electricity: 60 % efficient, and its exhaust is water.
     *
     * <p>Three times cheaper in oxygen per megajoule than the burner and a fifth as hot — the
     * machine you build once the habitat is already too warm.
     */
    /**
     * The Mond process: nickel walks into a gas at 50 °C and back out pure at 230 °C.
     *
     * <p>Turns carbon monoxide from a pure hazard into a permanent tool — it is a carrier, not
     * a fuel, and a completed cycle hands it back.
     */
    public static final DeferredBlock<CarbonylRefinerBlock> CARBONYL_REFINER =
            BLOCKS.registerBlock("carbonyl_refiner", CarbonylRefinerBlock::new,
                    p -> p.mapColor(MapColor.TERRACOTTA_GREEN).strength(3.5f, 10.0f)
                            .sound(SoundType.METAL));

    /**
     * The centrifugal fluidized-bed reactor: spin the drum to make the artificial gravity that
     * lets a fixed hydrogen flow fluidize a bed of ilmenite and reduce it. See
     * {@code design/fluidized-bed.md}.
     */
    public static final DeferredBlock<FluidizedBedBlock> FLUIDIZED_BED =
            BLOCKS.registerBlock("fluidized_bed", FluidizedBedBlock::new,
                    p -> p.mapColor(MapColor.COLOR_GRAY).strength(3.5f, 10.0f)
                            .sound(SoundType.METAL));

    /**
     * Ilmenite ore: iron-titanium oxide in deep rock, the fluidized-bed reactor's feedstock.
     * Non-magnetic and oxide-locked, so no tier-1 machine gets metal out of it — only reduction.
     */
    public static final DeferredBlock<Block> ILMENITE_ORE = BLOCKS.registerSimpleBlock("ilmenite_ore",
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(3.2f, 6.0f).sound(SoundType.STONE));

    /**
     * Molten-oxide electrolysis: splits whatever oxide its installed electrode targets into
     * metal and free oxygen, straight into the sealed room it sits in. See
     * {@code design/electrolysis.md}.
     */
    public static final DeferredBlock<ElectrolysisCellBlock> ELECTROLYSIS_CELL =
            BLOCKS.registerBlock("electrolysis_cell", ElectrolysisCellBlock::new,
                    p -> p.mapColor(MapColor.COLOR_ORANGE).strength(3.5f, 10.0f)
                            .sound(SoundType.METAL).lightLevel(state -> 6));

    /** Selective laser sintering: fuses iron powder into a printed part. See {@code design/sls.md}. */
    public static final DeferredBlock<play.xponer.astronima.block.SlsPrinterBlock> SLS_PRINTER =
            BLOCKS.registerBlock("sls_printer", play.xponer.astronima.block.SlsPrinterBlock::new,
                    p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(4.0f, 10.0f)
                            .sound(SoundType.METAL).lightLevel(state -> 5));

    /**
     * Thermal cracking: tholins or sludge into ethylene and methane, vented into the sealed room
     * this tower sits in. See {@code design/petrochemicals.md}.
     */
    public static final DeferredBlock<play.xponer.astronima.block.CrackingTowerBlock> CRACKING_TOWER =
            BLOCKS.registerBlock("cracking_tower", play.xponer.astronima.block.CrackingTowerBlock::new,
                    p -> p.mapColor(MapColor.COLOR_ORANGE).strength(3.5f, 8.0f)
                            .sound(SoundType.METAL).lightLevel(state -> 7));

    /**
     * Addition polymerization: titania catalyst plus the room's own ethylene, strung into
     * polyethylene. See {@code design/petrochemicals.md}.
     */
    public static final DeferredBlock<play.xponer.astronima.block.PolymerizerBlock> POLYMERIZER =
            BLOCKS.registerBlock("polymerizer", play.xponer.astronima.block.PolymerizerBlock::new,
                    p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(3.0f, 8.0f)
                            .sound(SoundType.METAL));

    /**
     * Polyethylene panelling. A real interior wall covering today; the block-aware sound
     * absorption it is named for is a separate acoustics feature, named rather than built
     * half-sized here — see {@code design/petrochemicals.md} §3 and {@code BACKLOG.md}.
     */
    public static final DeferredBlock<Block> ACOUSTIC_FOAM = BLOCKS.registerSimpleBlock("acoustic_foam",
            p -> p.mapColor(MapColor.COLOR_GRAY).strength(0.4f).sound(SoundType.WOOL).noOcclusion());

    /**
     * The mod's first respawn point. See {@link play.xponer.astronima.block.SleepingBagBlock}.
     */
    public static final DeferredBlock<play.xponer.astronima.block.SleepingBagBlock> SLEEPING_BAG =
            BLOCKS.registerBlock("sleeping_bag", play.xponer.astronima.block.SleepingBagBlock::new,
                    p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.2f)
                            .sound(SoundType.WOOL).noOcclusion());

    /**
     * Water electrolysis: {@code 2 H2O -> 2 H2 + O2}, vented into the sealed room this sits in.
     * See {@code design/chemistry-loop.md}.
     */
    public static final DeferredBlock<play.xponer.astronima.block.WaterElectrolyzerBlock> WATER_ELECTROLYZER =
            BLOCKS.registerBlock("water_electrolyzer",
                    play.xponer.astronima.block.WaterElectrolyzerBlock::new,
                    p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(3.0f, 8.0f)
                            .sound(SoundType.METAL));

    /**
     * The Sabatier reaction: {@code CO2 + 4 H2 -> CH4 + 2 H2O} over a nickel catalyst — the real
     * ISS technology this closes the loop with. See {@code design/chemistry-loop.md}.
     */
    public static final DeferredBlock<play.xponer.astronima.block.SabatierReactorBlock> SABATIER_REACTOR =
            BLOCKS.registerBlock("sabatier_reactor",
                    play.xponer.astronima.block.SabatierReactorBlock::new,
                    p -> p.mapColor(MapColor.COLOR_ORANGE).strength(3.0f, 8.0f)
                            .sound(SoundType.METAL).lightLevel(state -> 4));

    /**
     * The Bosch reaction: {@code CO2 + 2 H2 -> C(s) + 2 H2O} over an iron catalyst — the real
     * alternative to the Sabatier reactor, keeping carbon as a solid instead of spending it as
     * fuel gas. See {@code design/chemistry-loop.md} §2.5.
     */
    public static final DeferredBlock<play.xponer.astronima.block.BoschReactorBlock> BOSCH_REACTOR =
            BLOCKS.registerBlock("bosch_reactor", play.xponer.astronima.block.BoschReactorBlock::new,
                    p -> p.mapColor(MapColor.COLOR_BROWN).strength(3.0f, 8.0f)
                            .sound(SoundType.METAL));

    /**
     * Roasts crushed ore for its troilite: {@code 4 FeS + 7 O2 -> 2 Fe2O3 + 4 SO2}, real oxygen
     * spent, hematite and SO2 out. See {@code design/chemistry-loop.md}.
     */
    public static final DeferredBlock<play.xponer.astronima.block.TroiliteRoasterBlock> TROILITE_ROASTER =
            BLOCKS.registerBlock("troilite_roaster",
                    play.xponer.astronima.block.TroiliteRoasterBlock::new,
                    p -> p.mapColor(MapColor.COLOR_RED).strength(3.2f, 8.0f)
                            .sound(SoundType.METAL).lightLevel(state -> 6));

    /**
     * Real high-temperature carbon chemistry, run in a box: graphite off carbon powder, real
     * carbon fiber off a stabilized pitch fiber (design/carbon-fiber.md). No dial - it self-heats
     * to whichever real setpoint its own feed needs, ~2500 °C at the high one, hot enough to earn
     * a brighter glow than the troilite roaster's own oxidation heat.
     */
    public static final DeferredBlock<play.xponer.astronima.block.GraphitizerBlock> GRAPHITIZER =
            BLOCKS.registerBlock("graphitizer", play.xponer.astronima.block.GraphitizerBlock::new,
                    p -> p.mapColor(MapColor.COLOR_BLACK).strength(3.5f, 10.0f)
                            .sound(SoundType.METAL).lightLevel(state -> 13));

    /**
     * Real photosynthesis, run in a box: a water bottle and the room's own CO2 in, this mod's
     * first real food and real O2 out (design/hydroponics.md). Needs real electrical power to run
     * at all - nothing about photosynthesis has a manual-labour equivalent.
     */
    public static final DeferredBlock<play.xponer.astronima.block.AlgaeBioreactorBlock> ALGAE_BIOREACTOR =
            BLOCKS.registerBlock("algae_bioreactor",
                    play.xponer.astronima.block.AlgaeBioreactorBlock::new,
                    p -> p.mapColor(MapColor.COLOR_GREEN).strength(3.0f, 8.0f)
                            .sound(SoundType.METAL));

    /**
     * Real red romaine lettuce, grown hydroponically (design/hydroponics.md). No item of its own -
     * planted from {@code lettuce_seedling} and harvested by hand, the same standing vanilla's
     * own wheat crop has.
     */
    public static final DeferredBlock<play.xponer.astronima.block.HydroponicCropBlock> HYDROPONIC_CROP =
            BLOCKS.registerBlock("hydroponic_crop",
                    play.xponer.astronima.block.HydroponicCropBlock::new,
                    p -> p.mapColor(MapColor.PLANT).noCollision().instabreak()
                            .sound(SoundType.CROP).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

    /**
     * Real anaerobic digestion, run in a sealed box: real crop waste in, real biogas (into the
     * room) and real fertilizer out (design/anaerobic-digestion.md). Runs without power - real
     * digestion is bacterial metabolism on the waste's own chemical potential, the opposite of
     * the algae bioreactor's own real electrical-light requirement.
     */
    public static final DeferredBlock<play.xponer.astronima.block.AnaerobicDigesterBlock> ANAEROBIC_DIGESTER =
            BLOCKS.registerBlock("anaerobic_digester",
                    play.xponer.astronima.block.AnaerobicDigesterBlock::new,
                    p -> p.mapColor(MapColor.COLOR_BROWN).strength(2.5f, 6.0f)
                            .sound(SoundType.METAL));

    /**
     * The Contact Process, folded into one net equation: {@code 2 SO2 + O2 + 2 H2O -> 2 H2SO4}
     * over a hematite catalyst, bottled sulfuric acid out. See {@code design/chemistry-loop.md}
     * §2.7.
     */
    public static final DeferredBlock<play.xponer.astronima.block.SulfuricAcidPlantBlock>
            SULFURIC_ACID_PLANT = BLOCKS.registerBlock("sulfuric_acid_plant",
                    play.xponer.astronima.block.SulfuricAcidPlantBlock::new,
                    p -> p.mapColor(MapColor.COLOR_YELLOW).strength(3.2f, 8.0f)
                            .sound(SoundType.METAL));

    /**
     * One electrolytic D2O enrichment stage — a water bottle in, the same bottle out one stage
     * further up {@code HeavyWaterCascade}'s curve. See {@code design/heavy-water.md}.
     */
    public static final DeferredBlock<play.xponer.astronima.block.HeavyWaterCellBlock>
            HEAVY_WATER_CELL = BLOCKS.registerBlock("heavy_water_cell",
                    play.xponer.astronima.block.HeavyWaterCellBlock::new,
                    p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(3.0f, 8.0f)
                            .sound(SoundType.METAL));

    /**
     * FFC-Cambridge titanium reduction: {@code TiO2 -> Ti + O2}, a solid titania cathode in a
     * molten CaCl2 bath. See {@code design/titanium-reduction.md}.
     */
    public static final DeferredBlock<play.xponer.astronima.block.TitaniumCellBlock>
            TITANIUM_CELL = BLOCKS.registerBlock("titanium_cell",
                    play.xponer.astronima.block.TitaniumCellBlock::new,
                    p -> p.mapColor(MapColor.COLOR_PURPLE).strength(3.0f, 8.0f)
                            .sound(SoundType.METAL));

    /**
     * Melts iron powder without touching the room's air — the oxygen-free counterpart to a
     * vanilla furnace. See {@code design/induction-furnace.md}.
     */
    public static final DeferredBlock<play.xponer.astronima.block.InductionFurnaceBlock>
            INDUCTION_FURNACE = BLOCKS.registerBlock("induction_furnace",
                    play.xponer.astronima.block.InductionFurnaceBlock::new,
                    p -> p.mapColor(MapColor.COLOR_ORANGE).strength(3.5f, 9.0f)
                            .sound(SoundType.METAL).lightLevel(state -> 8));

    /**
     * Real fluxing and slagging: hematite ore and a real MgO flux in, iron and real slag out.
     * See {@code design/iron-smelter.md}.
     */
    public static final DeferredBlock<play.xponer.astronima.block.IronSmelterBlock>
            IRON_SMELTER = BLOCKS.registerBlock("iron_smelter",
                    play.xponer.astronima.block.IronSmelterBlock::new,
                    p -> p.mapColor(MapColor.COLOR_ORANGE).strength(3.5f, 9.0f)
                            .sound(SoundType.METAL).lightLevel(state -> 8));

    /**
     * The gateway to the void, design/vr-simulation-pod.md. A safe place to rehearse a dangerous
     * build before risking it for real — the same reasoning {@code TelescopeBlock}'s own doc gives
     * for a real, physical fixture rather than a menu.
     */
    public static final DeferredBlock<play.xponer.astronima.block.VrSimulationPodBlock>
            VR_SIMULATION_POD = BLOCKS.registerBlock("vr_simulation_pod",
                    play.xponer.astronima.block.VrSimulationPodBlock::new,
                    p -> p.mapColor(MapColor.COLOR_CYAN).strength(3.5f, 9.0f)
                            .sound(SoundType.METAL).lightLevel(state -> 10));

    public static final DeferredBlock<FuelCellBlock> FUEL_CELL =
            BLOCKS.registerBlock("fuel_cell", FuelCellBlock::new,
                    p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(3.0f, 9.0f)
                            .sound(SoundType.METAL));

    /**
     * A sealed, passive conductor between whatever two rooms sit on either end of its own
     * axis — see {@code design/ammonia-heat-pipes.md}. Slim like the gas fittings it shares
     * an axis-property shape with, not a solid cube.
     */
    public static final DeferredBlock<play.xponer.astronima.block.AmmoniaHeatPipeBlock>
            AMMONIA_HEAT_PIPE = BLOCKS.registerBlock("ammonia_heat_pipe",
                    play.xponer.astronima.block.AmmoniaHeatPipeBlock::new,
                    p -> p.mapColor(MapColor.METAL).strength(1.6f, 6.0f).sound(SoundType.METAL)
                            .noOcclusion());

    /**
     * A run of conductor: it loses a hundredth of what it carries per block, as heat.
     *
     * <p>{@code noOcclusion} because the model is a slim wire, not a full cube — a neighbour
     * that culled its face against a cable would delete a face the cable never covers, and the
     * gap would show. It is the pipe's reason, and the pipe's shape, with a different physics.
     */
    public static final DeferredBlock<PowerCableBlock> POWER_CABLE =
            BLOCKS.registerBlock("power_cable", PowerCableBlock::new,
                    p -> p.mapColor(MapColor.COLOR_BROWN).strength(1.0f, 3.0f)
                            .sound(SoundType.WOOL).noOcclusion());

    /** Joules kept for the half of the day the array makes none. */
    public static final DeferredBlock<PowerCellBlock> POWER_CELL =
            BLOCKS.registerBlock("power_cell", PowerCellBlock::new,
                    p -> p.mapColor(MapColor.METAL).strength(2.5f, 8.0f)
                            .sound(SoundType.METAL));

    public static final DeferredBlock<PurgeValveBlock> PURGE_VALVE =
            BLOCKS.registerBlock("purge_valve", PurgeValveBlock::new,
                    p -> p.mapColor(MapColor.METAL).strength(3.0f, 10.0f)
                            .sound(SoundType.METAL));

    public static final DeferredBlock<BulkheadDoorBlock> BULKHEAD_DOOR = BLOCKS.registerBlock("bulkhead_door",
            BulkheadDoorBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(4.0f, 12.0f).sound(SoundType.METAL).noOcclusion());

    public static final DeferredBlock<OxygenCandleBlock> OXYGEN_CANDLE = BLOCKS.registerBlock("oxygen_candle",
            OxygenCandleBlock::new,
            p -> p.mapColor(MapColor.TERRACOTTA_ORANGE).strength(0.5f).sound(SoundType.METAL)
                    .noCollision().lightLevel(state -> state.getValue(OxygenCandleBlock.LIT) ? 13 : 0));

    /** A run of gas line. Conducts; it cannot lift pressure. */
    public static final DeferredBlock<GasPipeBlock> GAS_PIPE = BLOCKS.registerBlock("gas_pipe",
            GasPipeBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(1.2f, 4.0f).sound(SoundType.METAL)
                    .noOcclusion());

    /** The instrument, design/astra-telescope.md. {@code noOcclusion} because its own shape is a
     * plinth and column, not a full cube. */
    public static final DeferredBlock<play.xponer.astronima.block.TelescopeBlock> TELESCOPE =
            BLOCKS.registerBlock("telescope",
                    play.xponer.astronima.block.TelescopeBlock::new,
                    p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f).sound(SoundType.METAL)
                            .noOcclusion());

    /**
     * Lifts pressure: the one part that moves gas against a gradient.
     *
     * <p>{@code noOcclusion} because its outlet face is an inset panel inside a frame, not
     * a flat side. Occluding, the block behind it deletes the face it is touching — and the
     * recess then looks straight through into geometry that is no longer drawn.
     */
    public static final DeferredBlock<GasPumpBlock> GAS_PUMP = BLOCKS.registerBlock("gas_pump",
            GasPumpBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(2.5f, 8.0f).sound(SoundType.METAL)
                    .noOcclusion());

    /**
     * A pressure vessel: gas you have stored, not gas you are breathing.
     *
     * <p>{@code noOcclusion} because the modelled vessel is slimmer than the block, so
     * neighbours must not cull their faces against it. Its <em>collision</em> stays the
     * full cube deliberately: {@code AirBlockKinds} classifies a full collision cube as
     * airtight, and a tank set into a wall has always sealed — slimming the collision
     * would quietly turn every such wall into a leak.
     */
    public static final DeferredBlock<GasTankBlock> GAS_TANK = BLOCKS.registerBlock("gas_tank",
            GasTankBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0f, 12.0f).sound(SoundType.METAL)
                    .noOcclusion());

    /** A bore you can narrow. Half open is a sixteenth of the flow. */
    public static final DeferredBlock<GasValveBlock> GAS_VALVE = BLOCKS.registerBlock("gas_valve",
            GasValveBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(1.6f, 6.0f).sound(SoundType.METAL)
                    .noOcclusion());

    /**
     * The opening between a run and a room. A boundary, not a mover.
     *
     * <p>{@code noOcclusion} for the same reason as the pump: the room-facing side is a
     * grille recessed behind a frame, so a neighbour that culls against it leaves a hole.
     * Collision stays the full cube, which is what keeps a port set into a wall airtight.
     */
    public static final DeferredBlock<GasPortBlock> GAS_PORT = BLOCKS.registerBlock("gas_port",
            GasPortBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(1.6f, 6.0f).sound(SoundType.METAL)
                    .noOcclusion());

    /** Sequences a two-door airlock chamber, driving the player's own pump and doors. */
    public static final DeferredBlock<AirlockControllerBlock> AIRLOCK_CONTROLLER =
            BLOCKS.registerBlock("airlock_controller", AirlockControllerBlock::new,
                    p -> p.mapColor(MapColor.METAL).strength(2.5f, 8.0f).sound(SoundType.METAL));

    /** Fills a blank vial with whichever drug you tell it to - including the wrong one. */
    public static final DeferredBlock<play.xponer.astronima.block.SynthesiserBlock> SYNTHESISER =
            BLOCKS.registerBlock("synthesiser",
                    play.xponer.astronima.block.SynthesiserBlock::new,
                    p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f)
                            .sound(SoundType.METAL));

    /** A stage for one slide and a fine focus you have to work. */
    public static final DeferredBlock<play.xponer.astronima.block.MicroscopeBlock> MICROSCOPE =
            BLOCKS.registerBlock("microscope",
                    play.xponer.astronima.block.MicroscopeBlock::new,
                    p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f)
                            .sound(SoundType.METAL));

    /** A warm box that holds one Petri dish at a temperature you set. */
    public static final DeferredBlock<play.xponer.astronima.block.IncubatorBlock> INCUBATOR =
            BLOCKS.registerBlock("incubator",
                    play.xponer.astronima.block.IncubatorBlock::new,
                    p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f)
                            .sound(SoundType.METAL));

    /** Ultraviolet for what it can see, then water for what it cannot. */
    public static final DeferredBlock<play.xponer.astronima.block.DeconStationBlock> DECON_STATION =
            BLOCKS.registerBlock("decon_station",
                    play.xponer.astronima.block.DeconStationBlock::new,
                    p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f)
                            .sound(SoundType.METAL).lightLevel(state ->
                                    state.getValue(play.xponer.astronima.block.DeconStationBlock
                                            .RUNNING) ? 12 : 0));

    public static final DeferredBlock<ScrubberBlock> SCRUBBER = BLOCKS.registerBlock("scrubber",
            ScrubberBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(2.5f, 8.0f).sound(SoundType.METAL));

    public static final DeferredBlock<play.xponer.astronima.block.CleanroomControllerBlock>
            CLEANROOM_CONTROLLER = BLOCKS.registerBlock("cleanroom_controller",
                    play.xponer.astronima.block.CleanroomControllerBlock::new,
                    p -> p.mapColor(MapColor.METAL).strength(2.5f, 8.0f).sound(SoundType.METAL));

    public static final DeferredBlock<play.xponer.astronima.block.EtchStationBlock>
            ETCH_STATION = BLOCKS.registerBlock("etch_station",
                    play.xponer.astronima.block.EtchStationBlock::new,
                    p -> p.mapColor(MapColor.METAL).strength(2.5f, 8.0f).sound(SoundType.METAL));

    /** Invisible worldgen marker for trapped-volatile pockets; consumed on room scan. */
    public static final DeferredBlock<GasPocketCoreBlock> GAS_POCKET_CORE = BLOCKS.registerBlock("gas_pocket_core",
            GasPocketCoreBlock::new,
            p -> p.mapColor(MapColor.NONE).noCollision().instabreak().noLootTable().air());

    /** A mat that closes a contact while somebody stands on it. */
    public static final DeferredBlock<PresenceSensorBlock> PRESENCE_SENSOR =
            BLOCKS.registerBlock("presence_sensor", PresenceSensorBlock::new,
                    p -> p.mapColor(MapColor.COLOR_GRAY).strength(1.0f, 4.0f)
                            .sound(SoundType.METAL));

    /** Alarms that put the atmosphere simulation onto a wire. */
    public static final DeferredBlock<EnvironmentSensorBlock> VACUUM_SENSOR = BLOCKS.registerBlock(
            "vacuum_sensor", p -> new EnvironmentSensorBlock(p, EnvironmentSensorBlock.Watches.VACUUM),
            p -> p.mapColor(MapColor.COLOR_GRAY).strength(1.0f, 4.0f).sound(SoundType.METAL));

    public static final DeferredBlock<EnvironmentSensorBlock> STARVING_SENSOR = BLOCKS.registerBlock(
            "oxygen_sensor", p -> new EnvironmentSensorBlock(p, EnvironmentSensorBlock.Watches.STARVING),
            p -> p.mapColor(MapColor.COLOR_GRAY).strength(1.0f, 4.0f).sound(SoundType.METAL));

    public static final DeferredBlock<EnvironmentSensorBlock> FREEZING_SENSOR = BLOCKS.registerBlock(
            "frost_sensor", p -> new EnvironmentSensorBlock(p, EnvironmentSensorBlock.Watches.FREEZING),
            p -> p.mapColor(MapColor.COLOR_GRAY).strength(1.0f, 4.0f).sound(SoundType.METAL));

    public static final DeferredBlock<AlarmBlock> ALARM = BLOCKS.registerBlock("alarm",
            AlarmBlock::new,
            p -> p.mapColor(MapColor.COLOR_RED).strength(1.5f, 6.0f).sound(SoundType.METAL));

    /** Cold chemical light: works in vacuum, where a flame cannot (see GlowStickBlock). */
    public static final DeferredBlock<GlowStickBlock> GLOW_STICK = BLOCKS.registerBlock("glow_stick",
            GlowStickBlock::new,
            p -> p.mapColor(MapColor.COLOR_GREEN).strength(0.1f).noCollision()
                    .randomTicks().sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(GlowStickBlock::lightFor));

    /** Grows in damp, warm rooms; dies back when the air dries (see MoldBlock). */
    public static final DeferredBlock<MoldBlock> MOLD = BLOCKS.registerBlock("mold",
            MoldBlock::new,
            p -> p.mapColor(MapColor.COLOR_GREEN).strength(0.1f).noCollision()
                    .randomTicks().sound(SoundType.SLIME_BLOCK).noOcclusion());

    public static final DeferredBlock<DehumidifierBlock> DEHUMIDIFIER = BLOCKS.registerBlock("dehumidifier",
            DehumidifierBlock::new,
            p -> p.mapColor(MapColor.COLOR_CYAN).strength(2.5f, 8.0f).sound(SoundType.METAL));

    public static final DeferredBlock<PreBreatheStationBlock> PRE_BREATHE_STATION =
            BLOCKS.registerBlock("pre_breathe_station", PreBreatheStationBlock::new,
                    p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.0f, 6.0f).sound(SoundType.METAL));

    /** A torch that has gone out: same shape, no light, relightable. */
    public static final DeferredBlock<UnlitTorchBlock> UNLIT_TORCH = BLOCKS.registerBlock("unlit_torch",
            UnlitTorchBlock::new,
            p -> p.mapColor(MapColor.WOOD).noCollision().instabreak().sound(SoundType.WOOD).noOcclusion());

    public static final DeferredBlock<UnlitTorchBlock.Wall> UNLIT_WALL_TORCH =
            BLOCKS.registerBlock("unlit_wall_torch", UnlitTorchBlock.Wall::new,
                    p -> p.mapColor(MapColor.WOOD).noCollision().instabreak()
                            .sound(SoundType.WOOD).noOcclusion());

    /**
     * A real thermal mass: melts at a fixed point, absorbing a room's own heat swing as latent
     * heat instead of temperature — see {@code design/phase-change-blocks.md}. Furniture, not
     * plumbing, so a plain cube like {@code ACOUSTIC_FOAM} rather than the pipe shape "visible
     * and slim" reserves for flow-carrying fittings.
     */
    public static final DeferredBlock<play.xponer.astronima.block.ParaffinThermalMassBlock>
            PARAFFIN_THERMAL_MASS = BLOCKS.registerBlock("paraffin_thermal_mass",
                    play.xponer.astronima.block.ParaffinThermalMassBlock::new,
                    p -> p.mapColor(MapColor.COLOR_YELLOW).strength(1.0f, 3.0f)
                            .sound(SoundType.HONEY_BLOCK));

    /** A dewar: {@code CryoTankBlockEntity} — see design/cryogenics.md §4. Same footprint and
     * strength as a gas tank, since it is one, plus a liquid phase. */
    public static final DeferredBlock<play.xponer.astronima.block.CryoTankBlock> CRYO_TANK =
            BLOCKS.registerBlock("cryo_tank", play.xponer.astronima.block.CryoTankBlock::new,
                    p -> p.mapColor(MapColor.METAL).strength(3.0f, 12.0f).sound(SoundType.METAL));

    /** A Stirling cryocooler — see design/cryogenics.md §4. */
    public static final DeferredBlock<play.xponer.astronima.block.CryoCoolerBlock> CRYO_COOLER =
            BLOCKS.registerBlock("cryo_cooler", play.xponer.astronima.block.CryoCoolerBlock::new,
                    p -> p.mapColor(MapColor.METAL).strength(3.5f, 10.0f).sound(SoundType.METAL));

    /** Silica aerogel by freeze-drying — see design/cryogenics.md §5. */
    public static final DeferredBlock<play.xponer.astronima.block.FreezeDryerBlock> FREEZE_DRYER =
            BLOCKS.registerBlock("freeze_dryer", play.xponer.astronima.block.FreezeDryerBlock::new,
                    p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(3.0f, 8.0f)
                            .sound(SoundType.METAL));

    /** A radioisotope thermoelectric generator — see design/radiation.md. */
    public static final DeferredBlock<play.xponer.astronima.block.RtgBlock> RTG =
            BLOCKS.registerBlock("rtg", play.xponer.astronima.block.RtgBlock::new,
                    p -> p.mapColor(MapColor.COLOR_YELLOW).strength(4.0f, 20.0f)
                            .sound(SoundType.METAL).lightLevel(state -> 3));

    /**
     * The Downs process: molten rock salt electrolyzed into real sodium and chlorine — see
     * {@code design/halogens.md}.
     */
    public static final DeferredBlock<play.xponer.astronima.block.DownsCellBlock> DOWNS_CELL =
            BLOCKS.registerBlock("downs_cell", play.xponer.astronima.block.DownsCellBlock::new,
                    p -> p.mapColor(MapColor.COLOR_YELLOW).strength(3.2f, 8.0f)
                            .sound(SoundType.METAL).lightLevel(state -> 6));

    /**
     * Zone refining: a molten zone dragged along electrolytic silicon, rejecting real metallic
     * contamination into the melt it leaves behind — see {@code design/halogens.md} §9-10.
     */
    public static final DeferredBlock<play.xponer.astronima.block.ZoneRefinerBlock> ZONE_REFINER =
            BLOCKS.registerBlock("zone_refiner", play.xponer.astronima.block.ZoneRefinerBlock::new,
                    p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(3.0f, 8.0f)
                            .sound(SoundType.METAL));

    /**
     * Real fluorite digestion: {@code CaF2 + H2SO4 -> CaSO4 + 2 HF}. See
     * {@code design/halogens.md} §15-17 (Part C1).
     */
    public static final DeferredBlock<play.xponer.astronima.block.HfDigesterBlock> HF_DIGESTER =
            BLOCKS.registerBlock("hf_digester", play.xponer.astronima.block.HfDigesterBlock::new,
                    p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(3.0f, 8.0f)
                            .sound(SoundType.METAL));

    /**
     * The buildable shell of a storage structure — a plain block with no entity of its own.
     * {@code StorageDriveBlockEntity} counts how much of it is connected to a drive to decide
     * how many real cell slots that drive actually unlocks. See
     * {@code design/data-cells.md} §9.
     */
    public static final DeferredBlock<Block> STORAGE_FRAME = BLOCKS.registerSimpleBlock(
            "storage_frame",
            p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(2.0f, 6.0f).sound(SoundType.METAL));

    /**
     * Where real {@code data_cell} items go in — a 54-slot chest-shaped drive whose real
     * capacity grows with however much connected {@link #STORAGE_FRAME} the player has built
     * around it. See {@code design/data-cells.md} §9.
     */
    public static final DeferredBlock<play.xponer.astronima.block.StorageDriveBlock> STORAGE_DRIVE =
            BLOCKS.registerBlock("storage_drive", play.xponer.astronima.block.StorageDriveBlock::new,
                    p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(3.0f, 8.0f)
                            .sound(SoundType.METAL).lightLevel(state -> 3));

    /**
     * The real ME-style browsing screen for whatever drives it directly touches — see
     * {@code design/data-cells.md} §12.
     */
    public static final DeferredBlock<play.xponer.astronima.block.StorageTerminalBlock>
            STORAGE_TERMINAL = BLOCKS.registerBlock("storage_terminal",
                    play.xponer.astronima.block.StorageTerminalBlock::new,
                    p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(3.0f, 8.0f)
                            .sound(SoundType.METAL).lightLevel(state -> 5));

    private ModBlocks() {}
}
