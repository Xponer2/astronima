package play.xponer.astronima.datagen;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.client.ModKeybinds;
import play.xponer.astronima.crafting.CraftingTree;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.physio.Ailment;
import play.xponer.astronima.sim.physio.BodyRegion;
import play.xponer.astronima.sim.physio.BodySystem;

public class ModLanguageProvider extends LanguageProvider {
    public ModLanguageProvider(PackOutput output) {
        super(output, Astronima.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("itemGroup.astronima", "Astronima");

        add(ModBlocks.ASTEROID_ROCK.get(), "Asteroid Rock");
        add(ModBlocks.REGOLITH.get(), "Regolith");
        add(ModBlocks.WATER_ICE.get(), "Water Ice");
        add(ModBlocks.CHLORATE_ORE.get(), "Chlorate Crystal Vein");
        add(ModBlocks.HEMATITE_ORE.get(), "Hematite Ore");
        add(ModBlocks.HULL_PLATE.get(), "Hull Plate");
        add(ModBlocks.INSULATED_HULL_PLATE.get(), "Insulated Hull Plate");
        add(ModBlocks.GRAB_RAIL.get(), "Grab Rail");
        add(ModBlocks.PURGE_VALVE.get(), "Purge Valve");
        add(ModBlocks.SOLAR_ARRAY.get(), "Solar Array");
        add(ModBlocks.POWER_CELL.get(), "Power Cell");
        add(ModBlocks.POWER_CABLE.get(), "Power Cable (legacy)");
        add(ModBlocks.COMBUSTION_GENERATOR.get(), "Combustion Generator");
        add(ModBlocks.FUEL_CELL.get(), "Fuel Cell");
        add(ModItems.SLUDGE.get(), "Sludge");
        add(ModItems.PURE_NICKEL.get(), "Pure Nickel");
        add(ModBlocks.CARBONYL_REFINER.get(), "Carbonyl Refiner");
        add(ModBlocks.FLUIDIZED_BED.get(), "Fluidized-Bed Reactor");
        add(ModBlocks.ILMENITE_ORE.get(), "Ilmenite Ore");
        add(ModItems.CRUSHED_ILMENITE.get(), "Crushed Ilmenite");
        add(ModItems.IRON_POWDER.get(), "Iron Powder");
        add(ModItems.TITANIA.get(), "Titania");
        add(ModBlocks.ELECTROLYSIS_CELL.get(), "Electrolysis Cell");
        add(ModItems.SILICON_ELECTRODE.get(), "Silicon Electrode");
        add(ModItems.ALUMINUM_ELECTRODE.get(), "Aluminum Electrode");
        add(ModItems.SILICON.get(), "Silicon");
        add(ModItems.ALUMINUM.get(), "Aluminum");
        add(ModBlocks.SLS_PRINTER.get(), "SLS Printer");
        add(ModItems.SINTERED_FRAME.get(), "Sintered Frame");
        add(ModBlocks.CRACKING_TOWER.get(), "Cracking Tower");
        add(ModBlocks.POLYMERIZER.get(), "Polymerizer");
        add(ModItems.POLYETHYLENE.get(), "Polyethylene");
        add(ModItems.MYLAR.get(), "Mylar");
        add(ModItems.KAPTON_TAPE.get(), "Kapton Tape");
        add(ModBlocks.ACOUSTIC_FOAM.get(), "Acoustic Foam");
        add(ModBlocks.SLEEPING_BAG.get(), "Sleeping Bag");
        add(ModBlocks.WATER_ELECTROLYZER.get(), "Water Electrolyzer");
        add(ModBlocks.SABATIER_REACTOR.get(), "Sabatier Reactor");
        add(ModBlocks.BOSCH_REACTOR.get(), "Bosch Reactor");
        add(ModBlocks.TROILITE_ROASTER.get(), "Troilite Roaster");
        add(ModBlocks.SULFURIC_ACID_PLANT.get(), "Sulfuric Acid Plant");
        add(ModBlocks.HEAVY_WATER_CELL.get(), "Heavy Water Cell");
        add(ModBlocks.CRYO_TANK.get(), "Cryo Tank");
        add(ModBlocks.CRYO_COOLER.get(), "Cryocooler");
        add(ModBlocks.FREEZE_DRYER.get(), "Freeze Dryer");
        add(ModItems.SILICA_AEROGEL.get(), "Silica Aerogel");
        add(ModBlocks.RTG.get(), "Radioisotope Thermoelectric Generator");
        add(ModItems.RTG_CORE.get(), "Sealed Radioisotope Capsule");
        add(ModItems.GEIGER_COUNTER.get(), "Geiger Counter");
        add(ModBlocks.HALITE_ORE.get(), "Halite Ore");
        add(ModBlocks.DOWNS_CELL.get(), "Downs Cell");
        add(ModItems.SODIUM.get(), "Sodium");
        add(ModBlocks.ZONE_REFINER.get(), "Zone Refiner");
        add(ModItems.WAFER_SILICON.get(), "Wafer-Grade Silicon");
        add(ModBlocks.FLUORITE_ORE.get(), "Fluorite Ore");
        add(ModBlocks.HF_DIGESTER.get(), "HF Digester");
        add(ModItems.HYDROFLUORIC_ACID.get(), "Hydrofluoric Acid");
        add(ModItems.GYPSUM.get(), "Gypsum");
        add(ModBlocks.CLEANROOM_CONTROLLER.get(), "Cleanroom Controller");
        add(ModItems.HEPA_FILTER.get(), "HEPA Filter");
        add(ModBlocks.ETCH_STATION.get(), "Etch Station");
        add(ModItems.ETCHED_DIE.get(), "Etched Die");
        add(ModItems.FLUOROSILICIC_ACID.get(), "Fluorosilicic Acid");
        add(ModItems.DATA_CELL.get(), "Data Cell");
        add("astronima.data_cell.empty", "No data logged");
        add("astronima.data_cell.entry", "  %s x%s (%s slot(s))");
        add("astronima.data_cell.more", "...and %s more type(s)");
        add("astronima.data_cell.capacity", "%s/%s slots used (%s per slot)");
        add(ModBlocks.STORAGE_FRAME.get(), "Storage Frame");
        add(ModBlocks.STORAGE_DRIVE.get(), "Storage Drive");
        add(ModBlocks.STORAGE_TERMINAL.get(), "Storage Terminal");
        add(ModItems.CELL_COMPRESSOR.get(), "Cell Compressor");
        add("astronima.cell_upgrade.maxed", "This cell is already at its densest compression");
        add("astronima.cell_upgrade.upgraded", "Cell compressed - now %s items per slot");
        add(ModBlocks.TITANIUM_CELL.get(), "Titanium Cell");
        add(ModItems.TITANIUM.get(), "Titanium");
        add(ModBlocks.INDUCTION_FURNACE.get(), "Induction Furnace");
        add(ModBlocks.IRON_SMELTER.get(), "Iron Smelter");
        add(ModBlocks.VR_SIMULATION_POD.get(), "VR Simulation Pod");
        add(ModBlocks.AMMONIA_HEAT_PIPE.get(), "Ammonia Heat Pipe");
        add(ModItems.CARBON_POWDER.get(), "Carbon Powder");
        add(ModItems.SULFURIC_ACID.get(), "Sulfuric Acid");
        add(ModItems.AMMONIA_CANISTER.get(), "Ammonia Canister");
        add(ModItems.AMMONIA_CANISTER_EMPTY.get(), "Empty Ammonia Canister");
        add(ModItems.PARAFFIN_WAX.get(), "Paraffin Wax");
        add(ModBlocks.PARAFFIN_THERMAL_MASS.get(), "Paraffin Thermal Mass");
        add(ModBlocks.PAINTED_HULL_PLATE.get(), "Painted Hull Plate");
        add(ModBlocks.PAINTED_INSULATED_HULL_PLATE.get(), "Painted Insulated Hull Plate");
        add(ModItems.MAGNETIC_BOOTS.get(), "Magnetic Boots");
        add(ModBlocks.BULKHEAD_DOOR.get(), "Bulkhead Door");
        add(ModBlocks.OXYGEN_CANDLE.get(), "Oxygen Candle");
        add(ModBlocks.SCRUBBER.get(), "CO2 Scrubber");

        add(ModItems.GAS_ANALYZER.get(), "Gas Analyzer");
        add(ModItems.LITHIUM_HYDROXIDE_CARTRIDGE.get(), "LiOH Scrubber Cartridge");
        add(ModBlocks.METAL_RICH_ORE.get(), "Metal-Rich Seam");
        add(ModBlocks.ORE_CRUSHER.get(), "Jaw Crusher");
        add(ModBlocks.MAGNETIC_SEPARATOR.get(), "Magnetic Separator");
        add(ModBlocks.WINNOWING_TABLE.get(), "Winnowing Table");
        add(ModBlocks.CARGO_CRATE.get(), "Cargo Crate");
        add(ModBlocks.SOLAR_RETORT.get(), "Solar Retort");
        add(ModItems.CRUSHED_ORE.get(), "Crushed Ore");
        add("astronima.airlock.no_suit",
                "No pressure suit — depressurising will kill you. Cycling anyway.");
        // Commissioning: every one of these is said at the moment of the click, because a
        // click that quietly does nothing is the failure that makes a player give up.
        add("astronima.airlock.need_wrench",
                "Hold a wrench to commission a terminal.");
        add("astronima.airlock.armed",
                "%s armed — now click the device with the wrench.");
        add("astronima.airlock.bound", "%s = %s");
        add("astronima.airlock.wrong_kind", "A %s cannot be the %s.");
        add("astronima.airlock.too_far", "Too far from the panel to bind that.");
        add("astronima.airlock.disarmed", "Binding cancelled.");
        add("astronima.airlock.panel_gone", "That panel is gone.");
        add("astronima.airlock.scan_proposed",
                "Scan proposed bindings for the empty terminals — check them.");
        add("astronima.airlock.scan_found_nothing",
                "Scan found no complete chain to propose. Bind the terminals by hand.");
        add("astronima.wrench.armed", "Binding: %s — click the device");
        add("astronima.wrench.armed_cancel", "Sneak-click to cancel");
        add("astronima.wrench.survey", "Line: %s");
        add("astronima.crushed.unprocessed", "Unprocessed");
        add("astronima.crushed.size", "Ground to %s um");
        add("astronima.crushed.liberation", "Liberation %s%%");
        add("astronima.crushed.hint", "Higher liberation recovers more metal");
        add("astronima.crushed_ilmenite.fine", "Fine grind — spin the drum fast to pin it");
        add("astronima.crushed_ilmenite.coarse", "Coarse grind — spin the drum gently");
        add("astronima.crushed_ilmenite.hint", "Match the drum speed to the grind");
        add("astronima.chem.formula", "%s — %s g/mol");
        add("astronima.chem.composition", "Composition: %s");
        add("astronima.chem.organics", "organics");
        add("astronima.chem.mixture", "Complex mixture — no single formula");
        add("astronima.chem.magic", "Not real chemistry — astral in origin");
        add(ModItems.IRON_NICKEL_GRAINS.get(), "Iron-Nickel Grains");
        add(ModItems.PLATINUM_GROUP_GRAINS.get(), "Platinum-Group Grains");
        add(ModItems.TAILINGS.get(), "Tailings");
        add(ModItems.BAKED_SILICATE.get(), "Baked Silicate");
        add(ModItems.MAGNESIUM_OXIDE.get(), "Magnesium Oxide");
        add(ModItems.SLAG.get(), "Slag");
        add(ModBlocks.COLD_FORGE.get(), "Cold Forge");
        add(ModBlocks.PACKED_TAILINGS.get(), "Packed Tailings");
        add(ModItems.METAL_BILLET.get(), "Metal Billet");
        add(ModItems.TOOL_HEAD.get(), "Forged Tool Head");
        add(ModItems.HAMMER_STONE.get(), "Hammer Stone");
        add(ModItems.METEORIC_PICKAXE.get(), "Meteoric Pickaxe");
        add(ModItems.PRY_BAR.get(), "Pry Bar");
        add(ModItems.IMPROVISED_PICKAXE.get(), "Improvised Pickaxe");
        add(ModItems.METEORIC_SHOVEL.get(), "Meteoric Shovel");
        add(ModItems.METEORIC_AXE.get(), "Meteoric Axe");
        add(ModBlocks.GAS_PIPE.get(), "Gas Pipe");
        add(ModBlocks.TELESCOPE.get(), "Telescope");
        add(ModBlocks.GAS_PORT.get(), "Gas Port");
        add(ModKeybinds.TOGGLE_SNAP.getName(), "Toggle Wire Snapping");
        add(ModBlocks.GAS_PUMP.get(), "Gas Pump");
        add(ModBlocks.GAS_TANK.get(), "Gas Tank");
        add(ModBlocks.GAS_VALVE.get(), "Gas Valve");

        // The pump's tooltip is the answer to "why is nothing happening", so it names
        // the face at fault and says what to put there rather than reporting a state.
        add("astronima.jade.pump_no_inlet",
                "INTAKE (big mouth): nothing connected — needs a gas port or tank");
        add("astronima.jade.pump_no_outlet",
                "OUTLET (small spout): nothing connected — needs a tank or gas port");
        add("astronima.jade.pump_many_inlet",
                "INTAKE: %s things connected — a pump takes from one only");
        add("astronima.jade.pump_many_outlet",
                "OUTLET: %s things connected — a pump feeds one only");
        add("astronima.jade.pump_running", "Running");
        add("astronima.jade.pump_stalled", "Stalled — outlet is full. This is normal");
        add("astronima.jade.pump_self_routed",
                "Inlet and outlet reach the same room — nowhere to pump gas to");
        add("astronima.jade.tank", "%s / %s kPa");
        add("astronima.crusher.setting",
                "Jaw gap %s um - liberation %s%% - %s turns per batch");
        add("astronima.separator.result",
                "Recovery %s%% - grade %s%% - %s grains");
        add("astronima.ore.batch", "%s, ground to %s um");

        add("astronima.suit.not_fitted", "--");

        // Jade look-at lines. Machines report themselves; the atmosphere needs the analyzer.
        // Jade asserts a config translation for every provider UID it is given, and
        // the failure is not a crash — it drops every resource pack instead, which is
        // far harder to trace back to a missing lang key.
        add("config.jade.plugin_astronima", "Astronima");
        add("config.jade.plugin_astronima.machine_state", "Machine state");
        add("config.jade.plugin_astronima.atmosphere", "Atmosphere (needs gas analyzer)");

        add("astronima.jade.cartridge", "Cartridge: %s");
        add("astronima.jade.candle_burning", "Burning - %s left");
        add("astronima.jade.candle_unlit", "Unlit");
        add("astronima.jade.candle_spent", "Spent");
        add("astronima.jade.alarm_latched", "ALARM LATCHED");
        add("astronima.jade.vacuum", "Vacuum");
        add("astronima.jade.ppo2", "O2: %s kPa");
        add("astronima.jade.pressure", "Pressure: %s kPa");
        add("astronima.jade.temperature", "Temp: %s C");
        // Each repair verb says what to do right now, in the language of the job.
        add("astronima.repair.hint.apply", "Hold SPACE to lay down sealant - stop in the green band");
        add("astronima.repair.hint.watch", "Watch the trace. Click to scrape it back and start over");
        add("astronima.repair.hint.torque_next", "Hold the lit latch to torque it");
        add("astronima.repair.hint.release_at_spec", "Release inside the spec window");
        add("astronima.repair.hint.null_the_needle", "Move the dial to centre the error needle");
        add("astronima.repair.hint.hold_null", "Hold it there");
        add("astronima.repair.hint.push_home", "Hold to push the cartridge in");
        add("astronima.repair.hint.release_at_detent", "Release the moment it seats");
        add("astronima.repair.hint.follow_channel", "Follow the channel with the mouse");
        add("astronima.repair.hint.off_channel", "Off the channel - the thread is slipping");
        add("astronima.repair.hint.solder_pads", "Click each pad. Near misses bridge");
        add("astronima.repair.hint.wick_bridge", "Bridged - click the red joint to wick it clean");
        add("astronima.repair.hint.done", "Seated");

        add("astronima.repair.seal.coverage", "Sealant coverage");
        add("astronima.repair.regulator.dwell", "Calibration held");
        add("astronima.repair.seating.resistance", "Resistance");

        add("astronima.suit.wearing", "%1$s wearing out — %2$s%% left");
        add(ModItems.CHLORATE_POWDER.get(), "Chlorate Powder");
        add(ModItems.MINERAL_SALTS.get(), "Mineral Salts");
        add(ModItems.OXYGEN_TANK.get(), "Oxygen Tank");
        add(ModItems.OXYGEN_TANK_EMPTY.get(), "Oxygen Tank (Empty)");
        add(ModItems.THOLIN_CLUMP.get(), "Tholin Clump");
        add(ModItems.IRON_ROD.get(), "Iron Rod");
        add(ModItems.REFRACTORY_LINING.get(), "Refractory Lining");
        add(ModItems.PRECISION_BEARING.get(), "Precision Bearing");
        add(ModItems.CONTROL_BOARD.get(), "Control Board");
        add(ModItems.GAS_SEAL.get(), "Gas Seal");
        add(ModItems.REINFORCED_FRAME.get(), "Reinforced Frame");
        add(ModItems.SEISMIC_PROBE.get(), "Seismic Probe");
        add(ModItems.WRENCH.get(), "Wrench");
        add(ModItems.WIRE_COIL.get(), "Wire Coil");
        add(ModItems.WIRE_RIBBON.get(), "Wire Ribbon");
        add(ModItems.WIRE_CUTTERS.get(), "Wire Cutters");
        add(ModItems.WIRE_SNIPS.get(), "Wire Snips");
        add(ModBlocks.PRESENCE_SENSOR.get(), "Presence Mat");
        add(ModBlocks.VACUUM_SENSOR.get(), "Vacuum Alarm");
        add(ModBlocks.STARVING_SENSOR.get(), "Oxygen Alarm");
        add(ModBlocks.FREEZING_SENSOR.get(), "Frost Alarm");

        add(ModItems.DIAGNOSTIC_GOGGLES.get(), "Diagnostic Goggles");
        add(ModItems.BASIC_BIOMONITOR_CHIP.get(), "Basic Biomonitor Chip");
        add(ModItems.PATHOGEN_ANALYZER_CHIP.get(), "Pathogen Analyzer Chip");
        add("astronima.circuit.title", "Circuit Plate");
        add("astronima.circuit.blank", "Blank - right-click to design it");
        add("astronima.circuit.gates", "%s gates");
        add("astronima.circuit.name", "Plate name");
        add("astronima.circuit.unnamed", "Unnamed");
        add("astronima.processor.title", "Processor Program");
        add("astronima.processor.placeholder", "; write your program here");
        add("astronima.processor.save", "Save");
        // Every wire-layer part, named from the set that defines them (rule 20). An unnamed
        // item shows its raw registry id, which looks like a bug and is invisible to every
        // other gate in the build.
        for (var entry : ModItems.PARTS.entrySet()) {
            add(entry.getValue().get(), partName(entry.getKey()));
        }
        add("astronima.part.no_room", "No room there - something is in the way");
        add("astronima.part.tip", "Mounts flat on a wall, in the wire layer - R to turn it");
        add("key.astronima.rotate_part", "Turn wire part");

        add("astronima.wire.tip_cutter",
                "Use on wire: cut the whole circuit - Sneak+use: cut just that surface");
        add("astronima.wire.held", "Holding the end at pixel %s - click again to lay the run.");
        add("astronima.wire.laid", "Laid %s pixels (%s m). Still holding the far end.");
        add("astronima.wire.let_go", "Let go of the wire.");
        add("astronima.wire.pulled", "Pulled that pixel of wire out.");
        add("astronima.wire.nothing_here", "No wire of that colour here.");
        add("astronima.wire.no_support", "Nothing sturdy to fasten wire to there.");
        add("astronima.wire.no_route", "No route - nothing to fasten a trace to along the way.");
        add("astronima.wire.no_straight_run", "No straight run gets there. Something is in the way.");
        add("astronima.wire.bad_route", "That route jumps a gap - refused.");
        add("astronima.wire.same_pixel", "That is the pixel you are already holding.");
        add("astronima.wire.mode_pathfind", "Routing: around obstacles.");
        add("astronima.wire.mode_straight", "Routing: straight lines only.");
        add("astronima.wire.colour_set", "Insulation: %s");
        add("astronima.wire.panel_title", "Wire Coil");
        add("astronima.wire.no_stock", "Not enough stock - that run needs %s more.");
        add("astronima.wire.no_stock_kind", "No way to draw %s wire yet.");
        add("astronima.wire.cut_run", "Cut %s pixels of %s wire.");
        add("astronima.wire.cut_face", "Cut %s pixels off that surface.");
        add("astronima.wire.cut_nothing", "No wire there.");
        add("astronima.wire.tip_snips",
                "Use on wire: mark the cut's start / cut to here - Sneak+use: let go");
        add("astronima.wire.tip_snips_holding", "Holding a marked start");
        add("astronima.wire.snip_held", "Marked the cut's start at pixel %s - click the other end to cut.");
        add("astronima.wire.snip_nothing", "No wire there.");
        add("astronima.wire.snip_same_pixel", "That is the pixel already marked.");
        add("astronima.wire.snip_start_gone", "The marked start is not wire any more - marked nothing.");
        add("astronima.wire.snip_not_joined", "Not on the same run - nothing between them to cut.");
        add("astronima.wire.snip_cyclic",
                "That loops back on itself between those two points - cut it in two shorter pieces instead.");
        add("astronima.wire.snip_cut", "Cut %s pixels of %s wire out of the run.");
        add("astronima.wire.snip_let_go", "Let go of the marked start.");
        add("astronima.wire.tip_setting", "%s wire, %s insulation");
        add("astronima.wire.tip_pathfind", "Routes around obstacles");
        add("astronima.wire.tip_straight", "Straight lines only - refuses to detour");
        add("astronima.wire.tip_holding", "Holding the end of a run");
        add("astronima.wire.tip_controls",
                "Use: take/lay - Sneak+use: let go, or pull wire - Use in air: panel - Sneak in air: colour");

        add("astronima.ribbon.held", "Holding lane 0's end at pixel %s - click again to lay the leg.");
        add("astronima.ribbon.laid", "Laid an %s-lane ribbon, %s fresh pixels. Still holding the far end.");
        add("astronima.ribbon.same_pixel", "That is the pixel you are already holding.");
        add("astronima.ribbon.no_support", "Nothing sturdy to fasten a ribbon to there.");
        add("astronima.ribbon.no_room", "No room to fan the lanes out here - try a wider wall.");
        add("astronima.ribbon.blocked", "Something blocks one of the lanes along that route.");
        add("astronima.ribbon.no_pad_group", "Not enough matching pads run on from there for this width.");
        add("astronima.ribbon.misaligned",
                "The ribbon arrives misaligned with those pins - approach from a different angle.");
        add("astronima.ribbon.width_set", "Lanes: %s");
        add("astronima.ribbon.tip_setting", "%s-lane ribbon");
        add("astronima.ribbon.tip_controls",
                "Use: take/lay, or land on a part's pads - Sneak+use: let go, or pull a lane - "
                        + "Use in air: routing mode - Sneak in air: lane count");
        add(ModBlocks.AIRLOCK_CONTROLLER.get(), "Airlock Controller");
        add(ModBlocks.ALARM.get(), "Atmosphere Alarm");
        add(ModBlocks.GAS_POCKET_CORE.get(), "Trapped Volatiles");

        add("astronima.probe.solid", "Solid rock for at least %s m");
        add("astronima.probe.cavity", "Echo: cavity %s m behind this face");
        add("astronima.probe.cavity_gas", "Echo: cavity %s m behind this face — trapped gas signature!");
        add("astronima.gas.smell.h2s", "A smell of rotten eggs...");
        add(ModBlocks.PRE_BREATHE_STATION.get(), "Pre-Breathe Station");
        add(ModBlocks.DEHUMIDIFIER.get(), "Dehumidifier");
        add(ModBlocks.MOLD.get(), "Black Mold");
        add(ModBlocks.GLOW_STICK.get(), "Chemical Light Stick");
        add(ModBlocks.UNLIT_TORCH.get(), "Unlit Torch");
        add(ModBlocks.UNLIT_WALL_TORCH.get(), "Unlit Torch");
        add(ModItems.STRIKER.get(), "Ferrocerium Striker");
        add(ModItems.EVA_SUIT.get(), "EVA Suit");
        add(ModItems.SEALANT_PATCH.get(), "Sealant Patch");
        add(ModItems.LATCH_SET.get(), "Latch Set");
        add(ModItems.CALIBRATED_VALVE.get(), "Calibrated Valve");
        add(ModItems.INSULATION_WEAVE.get(), "Insulation Weave");
        add(ModItems.SALVAGED_CIRCUIT.get(), "Salvaged Circuit");
        add("astronima.suit.intact", "All subsystems operational");
        add("astronima.suit.faults", "%s of %s subsystems faulty:");
        add("astronima.suit.repairs", "Repairs: %s");
        add("astronima.suit.hold_suit", "Hold the EVA suit in your other hand to fit this part");
        add("astronima.suit.already_working", "The %s is already working");
        add("astronima.suit.repaired", "Fitted: %s — %s faults remaining");
        add("astronima.suit.rebuilt", "Suit fully rebuilt — all subsystems operational");
        add("astronima.suit.cartridge_fitted", "Cartridge fitted");
        add("astronima.suit.cartridge_fresh", "The fitted cartridge is still fresh");
        add("astronima.suit.repairing", "Fitting: %s");
        add("astronima.suit.procedure_hint", "Space or click when the needle is in the band");
        add("astronima.suit.procedure_done", "Seated");
        add("astronima.suit.result.perfect", "Perfect");
        add("astronima.suit.result.good", "Seated");
        add("astronima.suit.result.miss", "Slipped — step lost");
        add("astronima.suit.mistakes", "Slips: %s");
        add("astronima.prebreathe.no_supply", "No pressurized oxygen supply here");
        add("astronima.prebreathe.already_clear", "Your tissue nitrogen is already clear");
        add("astronima.prebreathe.purged", "Breathing pure oxygen — tissue N2 now %s kPa");
        add("astronima.hud.n2", "Tissue N2: %s kPa");
        add("death.attack.astronima.decompression", "%1$s decompressed too fast");
        add("death.attack.astronima.barotrauma", "%1$s opened the door onto vacuum");
        add("death.attack.astronima.impact", "%1$s found out how hard it is to stop");

        add("death.attack.astronima.carbon_monoxide", "%1$s never noticed the carbon monoxide");
        add("death.attack.astronima.toxic_gas", "%1$s was overcome by toxic gas");
        add("death.attack.astronima.radiation", "%1$s absorbed more gamma than a body can carry");
        add("death.attack.astronima.chemical_burn", "%1$s felt nothing, until the fluoride reached their heart");

        add("astronima.analyzer.header", "— Gas Analyzer —");
        add("astronima.geiger.header", "— Geiger Counter —");
        add("astronima.analyzer.vacuum", "No atmosphere: hard vacuum");
        add("astronima.analyzer.open_to_outside", "Unsealed: open to outside atmosphere");
        add("astronima.analyzer.room", "Sealed room: %s blocks | %s kPa | %s °C");
        add("astronima.analyzer.unsealable", "Unsealable volume: %s blocks | %s kPa | %s °C");
        add("astronima.analyzer.humidity", "  Relative humidity: %s%%");
        add("astronima.analyzer.ignition.no_oxygen", "  Fuel present, but no oxygen to burn it");
        add("astronima.analyzer.ignition.too_lean", "  Fuel present, too dilute to ignite");
        add("astronima.analyzer.ignition.too_rich", "  Fuel present, too concentrated to ignite");
        add("astronima.analyzer.ignition.explosive", "  EXPLOSIVE MIXTURE — any spark will ignite this");
        add("astronima.analyzer.noise", "  Noise level: %s dB");
        add("astronima.noise.too_loud", "Too loud to sleep here (%s dB) — move away from the machines");
        add("astronima.analyzer.debug.header", "— Analyzer: Room Diagnostics —");
        add("astronima.analyzer.debug.no_room", "No enclosed space at eye level");
        add("astronima.analyzer.debug.info", "Room #%s | %s blocks | sealed=%s overCap=%s | leaks=%s | scanned %s ticks ago");
        add("astronima.analyzer.debug.moles", "Contents: %s mol | %s kPa | %s °C");

        add("astronima.hud.vacuum", "VACUUM — NO ATMOSPHERE");
        add("astronima.hud.open_air", "OPEN AIR");
        add("astronima.hud.sealed", "SEALED  %s m³");
        add("astronima.hud.unsealable", "UNSEALED  %s m³");
        add("astronima.hud.tank", "Tank reserve: %s%%");
        add("astronima.hud.on_tank", "BREATHING FROM TANK — %s%%");

        add("astronima.tank.filled", "Tank charged with compressed oxygen");
        add("astronima.tank.no_air", "No pressurized air here to compress");
        add("astronima.tank.would_deplete", "Refused: filling would deplete this room's oxygen");
        add("astronima.tank.vessel_empty", "Not enough oxygen in this vessel to charge a bottle");
        add("astronima.cryo_tank.wrapped", "Dewar wrapped in silica aerogel — boil-off will run far slower");
        add("astronima.cryo_tank.already_wrapped", "Already wrapped in aerogel");
        add("astronima.ammonia_canister.filled", "Canister charged with compressed ammonia");
        add("astronima.ammonia_canister.no_air", "No sealed room here to compress ammonia from");
        add("astronima.ammonia_canister.not_enough", "Not enough ammonia in this room to fill a canister");
        add("astronima.generator.clean", "Nothing worth digging out of this one yet");
        add("astronima.generator.cleaned", "Scraped %1$s of sludge out of the burner");
        add("astronima.purge.opened", "Purge valve open - the room is dumping to space");
        add("astronima.purge.shut", "Purge valve shut");
        add("astronima.purge.no_vacuum",
                "Refused: nothing but vacuum can be dumped into. This face opens onto"
                        + " solid ground or another room");

        add("astronima.scrubber.cartridge_still_good", "The loaded cartridge still has capacity");
        add("astronima.scrubber.cartridge_loaded", "Fresh LiOH cartridge loaded");
        add("astronima.cleanroom_controller.filter_still_good", "The loaded filter still has capacity");
        add("astronima.cleanroom_controller.filter_loaded", "Fresh HEPA filter loaded");
        add("astronima.etch_station.hand_loaded", "No sting, no warning - hand-loading HF is "
                + "never free, useful or not. It is already working.");

        add("death.attack.astronima.suffocation", "%1$s suffocated in the airless dark");
        add("death.attack.astronima.hypercapnia", "%1$s succumbed to carbon dioxide");
        add("death.attack.astronima.thermal", "%1$s had no insulation left");

        // JEI info pages, generated from the same CraftingTree the recipes and the
        // reachability test use — the tree is the manual.
        for (CraftingTree.Source source : CraftingTree.sources()) {
            if (source instanceof CraftingTree.WorldSource world) {
                add("astronima.jei.source." + path(world.id()), world.how());
            }
        }
        CraftingTree.usageNotes().forEach((id, text) -> add("astronima.jei.usage." + path(id), text));

        // Medical monitor: name and what to do as two separate keys (the biomonitor draws
        // them as two separate lines with two separate widths), straight from the enum so the
        // displayed advice can never drift from the modelled condition.
        for (Ailment ailment : Ailment.values()) {
            add("astronima.ailment." + ailment.key() + ".name", ailment.displayName());
            add("astronima.ailment." + ailment.key() + ".remedy", ailment.remedy());
        }
        for (BodySystem system : BodySystem.values()) {
            add("astronima.body.system." + system.key(), system.displayName());
        }
        for (BodyRegion region : BodyRegion.values()) {
            add("astronima.body.region." + region.key(), region.displayName());
        }
        add("astronima.biomonitor.title", "Biomonitor");
        add("astronima.biomonitor.systems", "BODY SYSTEMS");
        add("astronima.biomonitor.conditions", "CONDITIONS");
        add("astronima.biomonitor.healthy", "All systems nominal");
        add("astronima.biomonitor.no_implant", "No implant fitted - nothing to group by");
        add("astronima.biomonitor.designator", "Designator: %s");
        add("astronima.biomonitor.antibiotic_course", "Course: %s - %ds to next dose");
        add("astronima.biomonitor.suit_wear", "Suit: %s at %d%%");
        add("key.astronima.biomonitor", "Open biomonitor");
        add("key.astronima.codex", "Open the codex");
        add("item.astronima.petri_dish", "Petri Dish");
        add("block.astronima.incubator", "Incubator");
        add("block.astronima.microscope", "Microscope");
        add("block.astronima.synthesiser", "Synthesiser");
        add("item.astronima.dose", "Dose");
        add("astronima.dose.blank", "empty vial - fill it at a synthesiser");
        add("astronima.dose.course", "one dose of a course; keep taking them");
        add("astronima.hydrofluoric_acid.warning", "Crosses skin without pain. Right-clicking this "
                + "will hurt you badly, no matter what you are wearing. No antidote exists yet.");
        add("astronima.synthesiser.empty", "no vial");
        add("astronima.synthesiser.blank", "blank vial - pick a drug");
        add("astronima.synthesiser.filled", "filled");
        add("astronima.microscope.empty", "no slide");
        add("astronima.microscope.nothing", "nothing to see");
        add("astronima.microscope.turn", "turn the fine focus");
        add("astronima.microscope.record", "click the field to record");
        add("astronima.incubator.empty", "no dish");
        add("astronima.incubator.cold", "too cold to grow");
        add("astronima.incubator.cooking", "COOKING THE PLATE");
        add("astronima.incubator.growing", "growing");
        add("astronima.dish.title", "Petri dish");
        add("astronima.dish.hold", "hold the stain button to pour alcohol");
        add("key.astronima.repair_hold", "Suit repair: hold to apply");
        add("astronima.codex.search", "search");
        add("astronima.codex.title", "Codex");
        add("astronima.macro_plate.title", "Macro Circuit Plate");
        add("astronima.codex.empty", "No guides loaded.");
        // The controls-screen heading. 1.26 builds it as key.category.<namespace>.<path>.
        add("key.category.astronima.main", "Astronima");
        add("key.astronima.hud_page", "Next HUD page");
        add("key.astronima.roll_left", "Roll left (zero-g)");
        add("key.astronima.roll_right", "Roll right (zero-g)");
        add("key.astronima.push_off", "Push off targeted surface (zero-g)");
        add("key.astronima.tether", "Fire/release tether (zero-g)");
        add("astronima.biomonitor.contamination", "CONTAMINATION");
        add("astronima.biomonitor.gloves", "gloves");
        add("astronima.biomonitor.skin", "skin");
        add("block.astronima.decon_station", "Decontamination Station");
        add("astronima.decon.idle", "Idle - stand on it and use it");
        add("astronima.decon.lamp", "Ultraviolet - cleaning what it can see");
        add("astronima.decon.rinsing", "Rinsing - reaching what the light could not");
        add("astronima.decon.decades", "%s decades removed");
        add("astronima.decon.water", "Water: %s L");
        add("astronima.biomonitor.chronic", "LASTING DAMAGE");
        for (play.xponer.astronima.sim.physio.Chronic.Condition condition
                : play.xponer.astronima.sim.physio.Chronic.Condition.values()) {
            add("astronima.chronic." + condition.key(), condition.displayName());
            add("astronima.chronic." + condition.key() + ".effect", condition.effect());
        }
        add("astronima.tier", "Tier: %s");
        for (play.xponer.astronima.crafting.Tiers.Tier tier
                : play.xponer.astronima.crafting.Tiers.Tier.values()) {
            add("astronima.tier." + tier.name().toLowerCase(java.util.Locale.ROOT), tier.title());
        }
        add("astronima.dish.sterile_swab", "swabbed - nothing came off");
        add("astronima.goggles.contaminated", "CONTAMINATED SURFACE");
        add("astronima.goggles.do_not_touch", "enough to infect - decontaminate or leave it");
        add("astronima.goggles.traces", "traces - it will weather off, slowly indoors");
        add("astronima.repair.glove.pressure", "PRESSURE");
        add("astronima.repair.glove.patched", "Patched in the wrong place %s time(s)");
        add("astronima.repair.hint.sweep_the_seam_for_bubbles", "Sweep the seam - listen for bubbles");
        add("astronima.repair.hint.patch_where_it_bubbles_loudest", "Patch it where it bubbles loudest");
        add("astronima.repair.hint.reinflate_the_glove", "Almost flat - re-inflate the glove");

        // Astra Incognita
        add("item.astronima.spectrograph", "Spectrograph");
        add("item.astronima.spectral_plate", "Spectral Plate");
        add("astronima.spectral_plate.blank", "Blank");
        add("astronima.spectral_plate.no_lines", "No lines captured");
        add("astronima.spectrograph.need_blank_plate", "Hold a blank spectral plate in the other hand");
        add("astronima.spectrograph.no_target", "No clear sky, or it's night");

        add("item.astronima.coherence_meter", "Coherence Meter");
        for (play.xponer.astronima.sim.magic.SpectralLine line
                : play.xponer.astronima.sim.magic.SpectralLine.values()) {
            add("item.astronima." + line.id(), line.displayName() + " Filter");
        }
        add("astronima.coherence_meter.header", "— Coherence Survey —");
        add("astronima.coherence_meter.total", "Total: %s");

        add("item.astronima.astra_field_meter", "Astra Field Meter");
        add("astronima.astra_field_meter.header", "— Astra Field —");
        add("astronima.astra_field_meter.identified", "Identified: %s");
        add("item.astronima.astra_collector", "Astra Collector");
        add("astronima.astra_collector.collected", "Collected %s");
        add("astronima.astra_collector.nothing", "Nothing here — the ground is already burnt");
        add("astronima.astra_collector.held", "Holding: %s");
        add("astronima.astra_collector.not_enough", "Not enough concentrated yet (holding %s)");
        add("astronima.astra_collector.precipitated", "Precipitated %s raw asterium");
        add("item.astronima.astra_sounder", "Crust Sounder");
        add("astronima.astra_sounder.header", "— Crust Sounding —");
        add("astronima.astra_sounder.unaccounted", "  ??? (unaccounted): %s");
        add("astronima.astra_sounder.nothing", "  nothing recognisable in range");
        add("item.astronima.asterium_grains", "Raw Asterium");
        add(ModBlocks.ASTERIUM_BLOCK.get(), "Asterium Block");
        add(ModBlocks.ASTRA_ALTAR.get(), "Astra Altar");
        add("astronima.astra_altar.no_arms", "No arms found — build at least 2 asterium anchors out from the altar");
        add("astronima.astra_altar.no_boundary", "The boundary ring is not closed");
        add("astronima.astra_altar.running", "The ritual is drawing — %s");
        add("astronima.astra_altar.completed", "The ritual completed — %s raw asterium");
        add("astronima.astra_altar.stalled", "The ritual stalled — the site could not sustain it");
        add("astronima.astra_altar.already_running", "A ritual is already running here");

        add("item.astronima.celestial_atlas", "Celestial Atlas");
        add("item.astronima.wide_aperture_lens", "Wide-Aperture Lens");
        add("astronima.atlas.title", "Celestial Atlas");
        add("astronima.atlas.blank", "The pages are blank.");
        // Claim stages (design/astra-atlas-s3-progression.md): the pane's generated rows.
        add("astronima.atlas.claim.held", "held");
        add("astronima.atlas.claim.complete", "Complete");
        add("astronima.atlas.claim.complete_not_ready", "Complete (not ready)");
        // Found live ("почему нету ру перевода в атласе") - every key below used to be a raw
        // Java String with no translation key at all, in either language.
        add("astronima.atlas.claim.reward", "Unlocked in the codex: %s");
        add("astronima.atlas.requirement.identified", "identified: %s");
        add("astronima.atlas.requirement.holds", "holds: %s");
        add("astronima.atlas.requirement.owns", "carry %s %s (have %s)");
        add("astronima.atlas.requirement.hands_in", "hand in %s %s (have %s)");
        add("astronima.atlas.requirement.unmet", "unmet requirement");
        add("astronima.atlas.kind.emission", "Emission nebula");
        add("astronima.atlas.kind.reflection", "Reflection nebula");
        add("astronima.atlas.kind.planetary", "Planetary nebula");
        add("astronima.atlas.kind.galaxy", "Galaxy");
        add("astronima.atlas.status.identified", "identified");
        add("astronima.atlas.status.captured", "captured, not yet identified");
        add("astronima.atlas.status.not_captured", "not yet captured");
        add("astronima.atlas.filter.arm_first", "Arm a filter from the row below first");
        add("astronima.atlas.filter.none_owned", "No filter tokens in your inventory.");
        add("astronima.atlas.strip.hint_identified", "Every real line here is revealed.");
        add("astronima.atlas.strip.hint_unidentified",
                "Arm a filter, click a tall narrow spike its own colour.");
        add("astronima.atlas.decode.no_match", "No match — %s");
        add("astronima.atlas.decode.no_filter", "you don't have that filter");
        add("astronima.atlas.decode.noise", "that's just noise");
        add("astronima.atlas.decode.wrong_filter", "a real peak, but not this filter's colour");
        add("astronima.atlas.decode.identified", "Identified: %s on %s");

        // Claim stage prose (design/astra-atlas-s2c-writing.md §4.2): the entry's own text
        // changes with the player's real progress — stage 1 is the observation, stage 2 is
        // owning the right instrument, stage 3 is the commitment that actually spends something.
        add("astronima.research.same_elements.stage1",
                "You have photographed the Orion Nebula and the Helix Nebula, but a plate is "
                        + "only a picture until its lines are read. Decode both — match a real "
                        + "peak in each spectrum against a filter's own wavelength — before this "
                        + "stage will move.");
        add("astronima.research.same_elements.stage2",
                "Both nebulae carry H-alpha, hydrogen's own red line, and the forbidden [O III] "
                        + "line besides. Owning ground filters for both means you can find either "
                        + "line on any future target without guessing — equipment, not ammunition, "
                        + "so grinding one costs you nothing to keep.");
        add("astronima.research.same_elements.stage3",
                "The case is made: the identical line, from two unrelated clouds of gas, is how "
                        + "astronomers first learned the universe is built from one chemistry "
                        + "rather than a different recipe per star. Hand in two exposed plates to "
                        + "file this claim for good — the plates themselves are spent making the "
                        + "case, the way a real published result spends its own evidence.");
        add("astronima.research.metal_assay.stage1",
                "Andromeda and its small companion M32 sit side by side in the sky and look "
                        + "almost identical through a plain eyepiece. Identify both — decode a "
                        + "real line in each — before this stage will move.");
        add("astronima.research.metal_assay.stage2",
                "Both galaxies show iron's own forest of lines, Fe I. A ground iron filter lets "
                        + "you find that exact line again on anything else that carries it, "
                        + "without ever touching or breaking a single sample.");
        add("astronima.research.metal_assay.stage3",
                "Matching Fe I between a spiral's old bulge and a wholly separate dwarf galaxy "
                        + "identifies the metal in both without cracking open either one — the "
                        + "same non-destructive assay a heated lab sample and a catalogue match "
                        + "would have shown, done here across two hundred thousand light-years "
                        + "instead of a workbench. Hand in two exposed plates to file the claim.");
        add("astronima.research.astra_gradient.stage1",
                "A single field reading tells you nothing about a gradient — only a real "
                        + "comparison does. Take a reading with the field meter close to the core, "
                        + "and another out at working depth or past it, before this stage will "
                        + "move.");
        add("astronima.research.astra_gradient.stage2",
                "Reading the field and drawing on it are two different acts. Owning both the "
                        + "field meter and the collector means you can find the gradient again "
                        + "and act on it anywhere else on the body, without guessing.");
        add("astronima.research.astra_gradient.stage3",
                "The case is made: the field is not a flat ambient number but a real, finite "
                        + "resource that thickens toward the core and depletes with use, exactly "
                        + "like the ground itself. Hand in two grains of asterium to file this "
                        + "claim for good — spent making the case, the same way a plate is.");

        add("astronima.codex.no_recipe", "No recipe for this item.");
        add("astronima.codex.no_structure", "No structure diagram for \"%s\".");
        add("astronima.codex.no_calculator", "No such calculator: %s");

        add("astronima.calc.comminution.dial", "Jaw gap (set)");
        add("astronima.calc.comminution.work", "Work since calibrated");
        add("astronima.calc.comminution.actual", "Jaws actually at");
        add("astronima.calc.comminution.size", "Product size");
        add("astronima.calc.comminution.liberation", "Liberation");
        add("astronima.calc.comminution.work_per_kg", "Work per kilogram");
        add("astronima.calc.comminution.stamped", "Sack stamped");

        add("astronima.calc.separation.fineness", "Feed fineness (from crusher)");
        add("astronima.calc.separation.dial", "Field strength (set)");
        add("astronima.calc.separation.work", "Work since calibrated");
        add("astronima.calc.separation.actual", "Field actually at");
        add("astronima.calc.separation.field", "Field strength");
        add("astronima.calc.separation.recovery", "Recovery");
        add("astronima.calc.separation.grade", "Concentrate grade");

        add("subtitles.astronima.music.deep_space_drift", "Deep Space Drift plays");
        add("subtitles.astronima.music.quiet_space_void", "Quiet Space Void plays");
        add("subtitles.astronima.music.vast_silence", "Vast Silence plays");
        add("subtitles.astronima.music.cosmic_survival", "Cosmic Survival plays");
        add("subtitles.astronima.music.night_space_ambient", "Night Space Ambient plays");
        add("subtitles.astronima.music.quiet_cosmic_pads", "Quiet Cosmic Pads plays");
        add("subtitles.astronima.music.cosmic_void", "Cosmic Void plays");
        add("subtitles.astronima.music.crystal_cave", "Crystal Cave plays");
    }

    private static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    /**
     * A part's display name.
     *
     * <p>Derived from the gate it is, rather than tabulated, so a seventh gate cannot be added
     * with no name — which is exactly the sort of line that gets left out of a hand-written list.
     */
    private static String partName(play.xponer.astronima.sim.logic.PartType type) {
        if (type.isGate()) {
            return type.gate().label() + " Gate";
        }
        return switch (type) {
            case SWITCH -> "Control Switch";
            case BUTTON -> "Control Button";
            case CLOCK -> "Control Clock";
            case RAM -> "Memory Cell Array";
            case FRAMEBUFFER -> "Video Framebuffer";
            case PROCESSOR -> "Processor";
            case FUSE -> "Power Fuse";
            case BREAKER -> "Circuit Breaker";
            default -> "Circuit Plate";
        };
    }
}
