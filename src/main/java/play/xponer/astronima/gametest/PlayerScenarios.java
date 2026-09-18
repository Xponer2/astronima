package play.xponer.astronima.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import play.xponer.astronima.block.PurgeValveBlock;
import play.xponer.astronima.block.entity.PurgeValveBlockEntity;
import play.xponer.astronima.block.entity.AmmoniaHeatPipeBlockEntity;
import play.xponer.astronima.block.entity.ParaffinThermalMassBlockEntity;
import play.xponer.astronima.sim.thermal.PhaseChangeMaterial;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.Config;
import play.xponer.astronima.airlock.AirlockCommissioning;
import play.xponer.astronima.airlock.AirlockResolver;
import play.xponer.astronima.advancement.AirlockTrigger;
import play.xponer.astronima.registry.ModItems;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.registries.DeferredRegister;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.atmosphere.AtmosphereEvents;
import play.xponer.astronima.atmosphere.SkyExposure;
import play.xponer.astronima.block.HydroponicCropBlock;
import play.xponer.astronima.sim.chem.HydroponicGrowth;
import play.xponer.astronima.physio.CarriedMacronutrition;
import play.xponer.astronima.physio.Nutrition;
import play.xponer.astronima.physio.NutritionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import play.xponer.astronima.block.GasPortBlock;
import play.xponer.astronima.block.GasPumpBlock;
import play.xponer.astronima.block.GasPipeBlock;
import play.xponer.astronima.block.GasValveBlock;
import play.xponer.astronima.pipe.PipeNetworks;
import play.xponer.astronima.pipe.PipeSurvey;
import play.xponer.astronima.sim.pipe.RunDiagnosis;
import play.xponer.astronima.sim.pipe.Valve;
import play.xponer.astronima.block.entity.GasPumpBlockEntity;
import play.xponer.astronima.block.entity.GasTankBlockEntity;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.block.entity.CombustionGeneratorBlockEntity;
import play.xponer.astronima.block.entity.PowerCellBlockEntity;
import play.xponer.astronima.block.entity.SolarArrayBlockEntity;
import play.xponer.astronima.sim.thermal.HeatBalance;
import play.xponer.astronima.block.entity.FuelCellBlockEntity;
import play.xponer.astronima.sim.power.CombustionEngine;
import play.xponer.astronima.sim.power.FuelCell;
import play.xponer.astronima.block.entity.CryoTankBlockEntity;
import play.xponer.astronima.block.entity.CryoCoolerBlockEntity;
import play.xponer.astronima.sim.cryo.Cryogen;
import play.xponer.astronima.sim.cryo.CryoVessel;
import play.xponer.astronima.block.entity.CarbonylRefinerBlockEntity;
import play.xponer.astronima.block.entity.FluidizedBedBlockEntity;
import play.xponer.astronima.item.SpectrographItem;
import play.xponer.astronima.item.WrenchItem;
import play.xponer.astronima.block.entity.TelescopeBlockEntity;
import play.xponer.astronima.telescope.TelescopeMountEntity;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.codex.Calculator;
import play.xponer.astronima.sim.codex.Calculators;
import play.xponer.astronima.sim.machine.Calibration;
import play.xponer.astronima.sim.physio.Hypothermia;
import play.xponer.astronima.block.entity.MagneticSeparatorBlockEntity;
import play.xponer.astronima.block.entity.OreCrusherBlockEntity;
import play.xponer.astronima.block.entity.ProcessingBlockEntity;
import play.xponer.astronima.block.entity.SolarRetortBlockEntity;
import play.xponer.astronima.block.entity.ElectrolysisCellBlockEntity;
import play.xponer.astronima.block.entity.SlsPrinterBlockEntity;
import play.xponer.astronima.block.entity.CrackingTowerBlockEntity;
import play.xponer.astronima.block.entity.PolymerizerBlockEntity;
import play.xponer.astronima.block.entity.WaterElectrolyzerBlockEntity;
import play.xponer.astronima.block.entity.SabatierReactorBlockEntity;
import play.xponer.astronima.sim.metal.LaserSintering;
import play.xponer.astronima.sim.ore.ElectrolysisSpecies;
import play.xponer.astronima.sim.ore.OreGrade;
import play.xponer.astronima.sim.ore.RetortProcess;
import play.xponer.astronima.sim.airlock.AirlockCycle;
import play.xponer.astronima.sim.airlock.DeviceBinding.Role;
import play.xponer.astronima.sim.machine.WorkState;
import play.xponer.astronima.sim.suit.HelmetAtmosphere;
import play.xponer.astronima.sim.magic.CapturedSpectrum;
import play.xponer.astronima.sim.optics.NamedSkyObjects;
import play.xponer.astronima.sim.optics.SkyRotation;
import net.minecraft.commands.arguments.EntityAnchorArgument;

import java.util.List;
import java.util.function.Consumer;

/**
 * Tests written as things a player would actually do, and why (rule 11).
 *
 * <p>Separate from {@link ModTestFunctions} on purpose. The tests there grew up around
 * the simulation and assert on values the code computes — which is how the gas valve
 * shipped completely decorative while a test asserting "a shut valve produces
 * zero-conductance edges" passed the whole time. The number was right; nothing consumed
 * it. A player building six pumps behind six valves found it in a minute.
 *
 * <p>So every test here starts from an intention — "I want to store this room's air
 * before I open the door" — builds it the way the guide says to, and asserts only on
 * what someone standing there would see.
 */
public final class PlayerScenarios {
    public static final DeferredRegister<Consumer<GameTestHelper>> SCENARIOS =
            DeferredRegister.create(Registries.TEST_FUNCTION, Astronima.MODID);

    @SuppressWarnings("unused")
    private static final Object STORE_A_ROOMS_AIR =
            SCENARIOS.register("scenario_store_a_rooms_air",
                    () -> PlayerScenarios::storeARoomsAir);

    @SuppressWarnings("unused")
    private static final Object SHUT_VALVE_STOPS_FILLING =
            SCENARIOS.register("scenario_shut_valve_stops_filling",
                    () -> PlayerScenarios::shutValveStopsFilling);

    @SuppressWarnings("unused")
    private static final Object SPECTROGRAPH_EXPOSES_A_PLATE_UNDER_OPEN_SKY =
            SCENARIOS.register("scenario_spectrograph_exposes_a_plate_under_open_sky",
                    () -> PlayerScenarios::spectrographExposesAPlateUnderOpenSky);

    @SuppressWarnings("unused")
    private static final Object SPECTROGRAPH_REFUSES_WITHOUT_A_BLANK_PLATE =
            SCENARIOS.register("scenario_spectrograph_refuses_without_a_blank_plate",
                    () -> PlayerScenarios::spectrographRefusesWithoutABlankPlate);

    @SuppressWarnings("unused")
    private static final Object SPECTROGRAPH_CAPTURES_THE_NAMED_NEBULA_WHEN_AIMED_AT_IT =
            SCENARIOS.register("scenario_spectrograph_captures_the_named_nebula_when_aimed_at_it",
                    () -> PlayerScenarios::spectrographCapturesTheNamedNebulaWhenAimedAtIt);

    @SuppressWarnings("unused")
    private static final Object TELESCOPE_SEATS_A_PLAYER_WHO_RIGHT_CLICKS_IT =
            SCENARIOS.register("scenario_telescope_seats_a_player_who_right_clicks_it",
                    () -> PlayerScenarios::telescopeSeatsAPlayerWhoRightClicksIt);

    @SuppressWarnings("unused")
    private static final Object TELESCOPE_REFUSES_A_SECOND_RIDER =
            SCENARIOS.register("scenario_telescope_refuses_a_second_rider",
                    () -> PlayerScenarios::telescopeRefusesASecondRider);

    @SuppressWarnings("unused")
    private static final Object BREAKING_AN_OCCUPIED_TELESCOPE_EJECTS_THE_RIDER =
            SCENARIOS.register("scenario_breaking_an_occupied_telescope_ejects_the_rider",
                    () -> PlayerScenarios::breakingAnOccupiedTelescopeEjectsTheRider);

    @SuppressWarnings("unused")
    private static final Object TELESCOPE_REMEMBERS_ITS_LAST_AIM_AFTER_THE_RIDER_LEAVES =
            SCENARIOS.register("scenario_telescope_remembers_its_last_aim_after_the_rider_leaves",
                    () -> PlayerScenarios::telescopeRemembersItsLastAimAfterTheRiderLeaves);

    @SuppressWarnings("unused")
    private static final Object TELESCOPE_SAVED_AIM_UPDATE_TAG_CARRIES_THE_NEW_AIM =
            SCENARIOS.register("scenario_telescope_saved_aim_update_tag_carries_the_new_aim",
                    () -> PlayerScenarios::telescopeSavedAimUpdateTagCarriesTheNewAim);

    @SuppressWarnings("unused")
    private static final Object PUMPING_DOWN_A_ROOM_IMPROVES_ITS_COHERENCE =
            SCENARIOS.register("scenario_pumping_down_a_room_improves_its_coherence",
                    () -> PlayerScenarios::pumpingDownARoomImprovesItsCoherence);

    @SuppressWarnings("unused")
    private static final Object A_RUNNING_MACHINE_MOVES_ONLY_THE_VIBRATION_BAR =
            SCENARIOS.register("scenario_a_running_machine_moves_only_the_vibration_bar",
                    () -> PlayerScenarios::aRunningMachineMovesOnlyTheVibrationBar);

    @SuppressWarnings("unused")
    private static final Object GIVE_THE_AIR_BACK =
            SCENARIOS.register("scenario_give_the_air_back",
                    () -> PlayerScenarios::giveTheAirBack);

    @SuppressWarnings("unused")
    private static final Object PUMPS_IN_SERIES =
            SCENARIOS.register("scenario_pumps_in_series",
                    () -> PlayerScenarios::pumpsInSeries);

    @SuppressWarnings("unused")
    private static final Object BREAKING_A_FULL_TANK =
            SCENARIOS.register("scenario_breaking_a_full_tank",
                    () -> PlayerScenarios::breakingAFullTank);

    @SuppressWarnings("unused")
    private static final Object DIGGING_BEHIND_A_SHUT_BULKHEAD =
            SCENARIOS.register("scenario_digging_behind_a_shut_bulkhead",
                    () -> PlayerScenarios::diggingBehindAShutBulkhead);

    @SuppressWarnings("unused")
    private static final Object CHLORATE_MAKES_OXYGEN =
            SCENARIOS.register("scenario_chlorate_makes_breathable_oxygen",
                    () -> PlayerScenarios::bakingChlorateMakesBreathableOxygen);

    @SuppressWarnings("unused")
    private static final Object ELECTROLYSIS_TARGETS_THE_INSTALLED_ELECTRODE =
            SCENARIOS.register("scenario_electrolysis_cell_targets_the_installed_electrode",
                    () -> PlayerScenarios::electrolysisCellTargetsTheInstalledElectrode);

    @SuppressWarnings("unused")
    private static final Object SLS_PRINTER_FOLLOWS_THE_PLANE =
            SCENARIOS.register("scenario_sls_printer_follows_the_plane",
                    () -> PlayerScenarios::slsPrinterFollowsThePlane);

    @SuppressWarnings("unused")
    private static final Object CRACKING_TOWER_VENTS_ITS_GAS =
            SCENARIOS.register("scenario_cracking_tower_vents_its_gas",
                    () -> PlayerScenarios::crackingTowerVentsIntoItsOwnRoom);

    @SuppressWarnings("unused")
    private static final Object POLYMERIZER_STRINGS_ETHYLENE =
            SCENARIOS.register("scenario_polymerizer_strings_ethylene_into_polyethylene",
                    () -> PlayerScenarios::polymerizerStringsEthyleneIntoPolyethylene);

    @SuppressWarnings("unused")
    private static final Object ELECTROLYZER_SPLITS_WATER =
            SCENARIOS.register("scenario_water_electrolyzer_splits_water",
                    () -> PlayerScenarios::waterElectrolyzerSplitsWaterInARealRatio);

    @SuppressWarnings("unused")
    private static final Object UNPOWERED_ELECTROLYZER_MAKES_NO_PROGRESS =
            SCENARIOS.register("scenario_unpowered_electrolyzer_makes_no_progress",
                    () -> PlayerScenarios::anUnpoweredElectrolyzerMakesNoProgressAtAll);

    @SuppressWarnings("unused")
    private static final Object SABATIER_CLOSES_THE_LOOP =
            SCENARIOS.register("scenario_sabatier_reactor_closes_the_loop",
                    () -> PlayerScenarios::sabatierReactorClosesTheLoop);

    @SuppressWarnings("unused")
    private static final Object BOSCH_KEEPS_CARBON_SOLID =
            SCENARIOS.register("scenario_bosch_reactor_keeps_the_carbon_solid",
                    () -> PlayerScenarios::boschReactorKeepsTheCarbonSolid);

    @SuppressWarnings("unused")
    private static final Object TROILITE_ROASTER_SPENDS_REAL_OXYGEN =
            SCENARIOS.register("scenario_troilite_roaster_spends_real_oxygen",
                    () -> PlayerScenarios::troiliteRoasterSpendsRealOxygen);

    @SuppressWarnings("unused")
    private static final Object DOWNS_CELL_SPLITS_ROCK_SALT =
            SCENARIOS.register("scenario_downs_cell_splits_rock_salt",
                    () -> PlayerScenarios::downsCellSplitsRockSalt);

    @SuppressWarnings("unused")
    private static final Object ZONE_REFINER_PURIFIES_SILICON =
            SCENARIOS.register("scenario_zone_refiner_purifies_silicon",
                    () -> PlayerScenarios::zoneRefinerPurifiesSilicon);

    @SuppressWarnings("unused")
    private static final Object HF_DIGESTER_REACTS_FLUORITE_AND_ACID =
            SCENARIOS.register("scenario_hf_digester_reacts_fluorite_and_acid",
                    () -> PlayerScenarios::hfDigesterReactsFluoriteAndAcid);

    @SuppressWarnings("unused")
    private static final Object CLEANROOM_CONTROLLER_HOLDS_POSITIVE_PRESSURE =
            SCENARIOS.register("scenario_cleanroom_controller_holds_positive_pressure",
                    () -> PlayerScenarios::cleanroomControllerHoldsRealPositivePressureAndRaisesCleanliness);

    @SuppressWarnings("unused")
    private static final Object ETCH_STATION_HAND_LOADS_HF_AND_GATES_ON_A_CLEAN_ROOM =
            SCENARIOS.register("scenario_etch_station_hand_loads_hf_and_gates_on_a_clean_room",
                    () -> PlayerScenarios::etchStationHandLoadsHfAndGatesOnACleanRoom);

    @SuppressWarnings("unused")
    private static final Object HYDROFLUORIC_ACID_BURNS_SKIN_ON_CONTACT =
            SCENARIOS.register("scenario_hydrofluoric_acid_burns_skin_on_contact",
                    () -> PlayerScenarios::hydrofluoricAcidBurnsSkinOnContactRegardlessOfArmor);

    @SuppressWarnings("unused")
    private static final Object STARVING_ONE_MACRO_DEBUFFS_AND_DROPS_IMMUNITY =
            SCENARIOS.register("scenario_starving_one_macro_debuffs_and_drops_immunity",
                    () -> PlayerScenarios::starvingOneMacroDebuffsAndDropsImmunityWithoutTouchingTheOthers);

    @SuppressWarnings("unused")
    private static final Object GRAPHITIZER_BURNS_INSTEAD_OF_GRAPHITIZING_IN_AN_UNPURGED_ROOM =
            SCENARIOS.register("scenario_graphitizer_burns_instead_of_graphitizing_in_an_unpurged_room",
                    () -> PlayerScenarios::graphitizerBurnsInsteadOfGraphitizingInAnUnpurgedRoom);

    @SuppressWarnings("unused")
    private static final Object ALGAE_BIOREACTOR_MAKES_REAL_FOOD_AND_OXYGEN_FROM_REAL_CO2 =
            SCENARIOS.register("scenario_algae_bioreactor_makes_real_food_and_oxygen_from_real_co2",
                    () -> PlayerScenarios::algaeBioreactorMakesRealFoodAndOxygenFromRealCo2);

    @SuppressWarnings("unused")
    private static final Object HYDROPONIC_CROP_GROWS_FROM_REAL_SUNLIGHT_CO2_AND_WATER_VAPOUR =
            SCENARIOS.register("scenario_hydroponic_crop_grows_from_real_sunlight_co2_and_water_vapour",
                    () -> PlayerScenarios::hydroponicCropGrowsFromRealSunlightCo2AndWaterVapour);

    @SuppressWarnings("unused")
    private static final Object ANAEROBIC_DIGESTER_RUNS_WITH_NO_POWER_AT_ALL =
            SCENARIOS.register("scenario_anaerobic_digester_runs_with_no_power_at_all",
                    () -> PlayerScenarios::anaerobicDigesterRunsWithNoPowerAtAllAndMakesRealBiogasAndFertilizer);

    @SuppressWarnings("unused")
    private static final Object DATA_CELL_LOGS_AND_RETURNS_A_REAL_CRATE =
            SCENARIOS.register("scenario_data_cell_logs_and_returns_a_real_crate",
                    () -> PlayerScenarios::dataCellLogsAndReturnsARealCrate);

    @SuppressWarnings("unused")
    private static final Object STORAGE_DRIVE_UNLOCKS_SLOTS_WITH_CONNECTED_FRAME =
            SCENARIOS.register("scenario_storage_drive_unlocks_slots_with_connected_frame",
                    () -> PlayerScenarios::storageDriveUnlocksSlotsWithConnectedFrame);

    @SuppressWarnings("unused")
    private static final Object STORAGE_TERMINAL_BROWSES_WITHDRAWS_AND_DEPOSITS =
            SCENARIOS.register("scenario_storage_terminal_browses_withdraws_and_deposits",
                    () -> PlayerScenarios::storageTerminalBrowsesWithdrawsAndDeposits);

    @SuppressWarnings("unused")
    private static final Object STORAGE_TERMINAL_LIST_SCROLLS_PAST_THE_FIRST_PAGE =
            SCENARIOS.register("scenario_storage_terminal_list_scrolls_past_the_first_page",
                    () -> PlayerScenarios::storageTerminalListScrollsPastTheFirstPage);

    @SuppressWarnings("unused")
    private static final Object STORAGE_TERMINAL_WITHDRAW_GIVES_WHATS_THERE_EVEN_UNDER_A_STACK =
            SCENARIOS.register("scenario_storage_terminal_withdraw_gives_whats_there_even_under_a_stack",
                    () -> PlayerScenarios::storageTerminalWithdrawGivesWhatsThereEvenUnderAStack);

    @SuppressWarnings("unused")
    private static final Object CELL_COMPRESSOR_RAISES_COMPRESSION_KEEPING_CONTENTS =
            SCENARIOS.register("scenario_cell_compressor_raises_compression_keeping_contents",
                    () -> PlayerScenarios::cellCompressorRaisesCompressionKeepingContents);

    @SuppressWarnings("unused")
    private static final Object ACID_PLANT_NEEDS_ALL_THREE_REAGENTS =
            SCENARIOS.register("scenario_acid_plant_needs_all_three_reagents",
                    () -> PlayerScenarios::acidPlantNeedsAllThreeReagents);

    @SuppressWarnings("unused")
    private static final Object BOTTLE_OFF_A_VESSEL =
            SCENARIOS.register("scenario_charge_a_bottle_off_a_vessel",
                    () -> PlayerScenarios::chargingABottleOffAVessel);

    @SuppressWarnings("unused")
    private static final Object BURYING_KEEPS_IT_WARM =
            SCENARIOS.register("scenario_burying_a_habitat_keeps_it_warm",
                    () -> PlayerScenarios::buryingAHabitatKeepsItWarm);

    @SuppressWarnings("unused")
    private static final Object INSULATION_KEEPS_IT_WARM =
            SCENARIOS.register("scenario_insulating_a_habitat_keeps_it_warm",
                    () -> PlayerScenarios::insulatingAHabitatKeepsItWarm);

    @SuppressWarnings("unused")
    private static final Object CRANKING_WARMS_THE_ROOM =
            SCENARIOS.register("scenario_cranking_a_machine_warms_the_room",
                    () -> PlayerScenarios::crankingAMachineWarmsTheRoom);

    @SuppressWarnings("unused")
    private static final Object NEGLECT_GETS_COLD_ENOUGH =
            SCENARIOS.register("scenario_a_neglected_habitat_gets_cold_enough_to_hurt",
                    () -> PlayerScenarios::aNeglectedHabitatGetsColdEnoughToHurt);

    @SuppressWarnings("unused")
    private static final Object PURGING_TAKES_THE_POISON_OUT =
            SCENARIOS.register("scenario_purging_a_room_takes_the_poison_out",
                    () -> PlayerScenarios::purgingARoomTakesThePoisonOut);

    @SuppressWarnings("unused")
    private static final Object PURGE_VALVE_NEEDS_VACUUM =
            SCENARIOS.register("scenario_a_purge_valve_with_no_vacuum_refuses",
                    () -> PlayerScenarios::aPurgeValveWithNoVacuumOutsideRefuses);

    @SuppressWarnings("unused")
    private static final Object PURGE_VALVE_NOT_ONTO_A_HABITAT =
            SCENARIOS.register("scenario_a_purge_valve_onto_another_habitat_refuses",
                    () -> PlayerScenarios::aPurgeValveOntoAnotherHabitatRefuses);

    @SuppressWarnings("unused")
    private static final Object POWER_HEATS_THE_ROOM =
            SCENARIOS.register("scenario_a_powered_machine_heats_the_room",
                    () -> PlayerScenarios::aPoweredMachineHeatsTheRoomItIsIn);

    @SuppressWarnings("unused")
    private static final Object ROOFED_ARRAY_MAKES_NOTHING =
            SCENARIOS.register("scenario_a_roofed_solar_array_makes_nothing",
                    () -> PlayerScenarios::aRoofedSolarArrayMakesNothing);

    @SuppressWarnings("unused")
    private static final Object ARRAY_CHARGES_ITS_OWN_CELL =
            SCENARIOS.register("scenario_an_array_charges_only_the_cell_beside_it",
                    () -> PlayerScenarios::anArrayChargesOnlyTheCellBesideItAndOnlyInSun);

    @SuppressWarnings("unused")
    private static final Object LONG_CABLE_COSTS_SOMETHING =
            SCENARIOS.register("scenario_a_long_cable_run_delivers_less",
                    () -> PlayerScenarios::aLongCableRunDeliversLessAndHeatsTheCorridor);

    @SuppressWarnings("unused")
    private static final Object WIRE_RATED_FOR_WHERE_IT_LIES =
            SCENARIOS.register("scenario_a_wire_is_rated_for_where_it_lies",
                    () -> PlayerScenarios::aWireIsRatedForWhereItLies);

    @SuppressWarnings("unused")
    private static final Object CABLE_OBEYS_THE_LAW =
            SCENARIOS.register("scenario_a_legacy_cable_obeys_the_conductor_law",
                    () -> PlayerScenarios::aLegacyCableObeysTheConductorLaw);

    @SuppressWarnings("unused")
    private static final Object GENERATOR_BURNS_OXYGEN =
            SCENARIOS.register("scenario_a_generator_burns_your_oxygen",
                    () -> PlayerScenarios::aGeneratorBurnsYourOxygenAndReturnsExhaust);

    @SuppressWarnings("unused")
    private static final Object GENERATOR_IS_A_STOVE =
            SCENARIOS.register("scenario_a_generator_is_mostly_a_stove",
                    () -> PlayerScenarios::aGeneratorIsMostlyAStove);

    @SuppressWarnings("unused")
    private static final Object FUEL_CELL_NAMES_ITS_FEED =
            SCENARIOS.register("scenario_a_fuel_cell_names_which_feed_is_empty",
                    () -> PlayerScenarios::aFuelCellNamesWhichFeedIsEmpty);

    @SuppressWarnings("unused")
    private static final Object SOUR_GAS_FOULS_FASTER =
            SCENARIOS.register("scenario_sour_gas_fouls_a_generator_faster",
                    () -> PlayerScenarios::sourGasFoulsAGeneratorFarFaster);

    @SuppressWarnings("unused")
    private static final Object CHOKED_GENERATOR_SAYS_SO =
            SCENARIOS.register("scenario_a_choked_generator_says_so",
                    () -> PlayerScenarios::aChokedGeneratorSaysSoAndAShovelFixesIt);

    @SuppressWarnings("unused")
    private static final Object REFINING_SEPARATES =
            SCENARIOS.register("scenario_refining_separates_nickel_from_iron",
                    () -> PlayerScenarios::refiningSeparatesTheNickelFromTheIron);

    @SuppressWarnings("unused")
    private static final Object FLUIDBED_MATCH_SPIN =
            SCENARIOS.register("scenario_a_fluidized_bed_must_match_the_spin_to_the_grind",
                    () -> PlayerScenarios::theFluidizedBedMatchesItsOwnSpinToTheGrind);

    @SuppressWarnings("unused")
    private static final Object OPENING_A_DOOR_ON_VACUUM =
            SCENARIOS.register("scenario_opening_a_door_on_vacuum",
                    () -> PlayerScenarios::openingADoorOnVacuum);

    @SuppressWarnings("unused")
    private static final Object BUILDING_A_WALL_KEEPS_THE_AIR =
            SCENARIOS.register("scenario_building_a_wall_keeps_the_air",
                    () -> PlayerScenarios::buildingAWallKeepsTheAir);

    @SuppressWarnings("unused")
    private static final Object STORED_AIR_SURVIVES_A_RELOAD =
            SCENARIOS.register("scenario_stored_air_survives_a_reload",
                    () -> PlayerScenarios::storedAirSurvivesAReload);

    @SuppressWarnings("unused")
    private static final Object A_ROOM_CANNOT_PUMP_INTO_ITSELF =
            SCENARIOS.register("scenario_a_room_cannot_pump_into_itself",
                    () -> PlayerScenarios::aRoomCannotPumpIntoItself);

    @SuppressWarnings("unused")
    private static final Object BREAKING_A_PIPE_STOPS_IT =
            SCENARIOS.register("scenario_breaking_a_pipe_stops_it",
                    () -> PlayerScenarios::breakingAPipeStopsIt);

    @SuppressWarnings("unused")
    private static final Object AN_ILLNESS_HIDES_THEN_SHOWS =
            SCENARIOS.register("scenario_an_illness_hides_then_shows",
                    () -> PlayerScenarios::anIllnessHidesThenShows);

    @SuppressWarnings("unused")
    private static final Object PANEL_REPORTS_ITS_SUN =
            SCENARIOS.register("scenario_a_panel_in_daylight_says_so",
                    () -> PlayerScenarios::aPanelInDaylightSaysSo);

    @SuppressWarnings("unused")
    private static final Object WRENCH_TURNS_A_PUMP =
            SCENARIOS.register("scenario_wrench_turns_a_pump",
                    () -> PlayerScenarios::wrenchTurnsAPump);

    @SuppressWarnings("unused")
    private static final Object WRENCH_SETS_A_SEPARATOR =
            SCENARIOS.register("scenario_wrench_sets_a_machine_with_no_dial",
                    () -> PlayerScenarios::theWrenchSetsAMachineWithNoDial);

    @SuppressWarnings("unused")
    private static final Object CALIBRATING_STOPS_THE_MACHINE =
            SCENARIOS.register("scenario_calibrating_stops_the_machine",
                    () -> PlayerScenarios::calibratingStopsTheMachine);

    @SuppressWarnings("unused")
    private static final Object ONLY_WORK_DRIFTS_A_SETTING =
            SCENARIOS.register("scenario_only_work_drifts_a_setting",
                    () -> PlayerScenarios::onlyWorkDriftsASetting);

    @SuppressWarnings("unused")
    private static final Object A_DRIFTING_CRUSHER_STILL_FILLS_A_SACK =
            SCENARIOS.register("scenario_a_drifting_crusher_still_fills_a_sack",
                    () -> PlayerScenarios::aDriftingCrusherStillFillsASack);

    @SuppressWarnings("unused")
    private static final Object COMMINUTION_CALCULATOR_PREDICTS_THE_CRUSHER =
            SCENARIOS.register("scenario_comminution_calculator_predicts_the_crusher",
                    () -> PlayerScenarios::comminutionCalculatorPredictsTheCrusher);

    @SuppressWarnings("unused")
    private static final Object SEPARATION_CALCULATOR_PREDICTS_THE_SEPARATOR =
            SCENARIOS.register("scenario_separation_calculator_predicts_the_separator",
                    () -> PlayerScenarios::separationCalculatorPredictsTheSeparator);

    @SuppressWarnings("unused")
    private static final Object WRENCH_TURNS_AXIS_NOT_SETTING =
            SCENARIOS.register("scenario_wrench_turns_axis_not_setting",
                    () -> PlayerScenarios::wrenchTurnsTheValveAxisNotItsSetting);

    @SuppressWarnings("unused")
    private static final Object WRENCH_WILL_NOT_BREAK_A_DOOR =
            SCENARIOS.register("scenario_a_wrench_will_not_rotate_a_sealing_door",
                    () -> PlayerScenarios::aWrenchWillNotRotateASealingDoor);

    @SuppressWarnings("unused")
    private static final Object HELD_PUMP_DOES_NOT_DRAIN =
            SCENARIOS.register("scenario_held_pump_does_not_drain",
                    () -> PlayerScenarios::aHeldPumpDoesNotDrainItsChamber);

    @SuppressWarnings("unused")
    private static final Object AIRLOCK_RESOLVES = SCENARIOS.register(
            "scenario_airlock_resolves", () -> PlayerScenarios::airlockResolvesAComplete);

    @SuppressWarnings("unused")
    private static final Object AIRLOCK_NO_CHAMBER = SCENARIOS.register(
            "scenario_airlock_no_chamber", () -> PlayerScenarios::airlockReportsNoChamber);

    @SuppressWarnings("unused")
    private static final Object AIRLOCK_NOT_SEALED = SCENARIOS.register(
            "scenario_airlock_not_sealed", () -> PlayerScenarios::airlockReportsNotSealed);

    @SuppressWarnings("unused")
    private static final Object AIRLOCK_NEEDS_TWO_DOORS = SCENARIOS.register(
            "scenario_airlock_needs_two_doors", () -> PlayerScenarios::airlockReportsNeedsTwoDoors);

    @SuppressWarnings("unused")
    private static final Object AIRLOCK_NO_PUMP = SCENARIOS.register(
            "scenario_airlock_no_pump", () -> PlayerScenarios::airlockReportsNoPump);

    @SuppressWarnings("unused")
    private static final Object AIRLOCK_NO_TANK = SCENARIOS.register(
            "scenario_airlock_no_tank", () -> PlayerScenarios::airlockReportsNoTank);

    @SuppressWarnings("unused")
    private static final Object AIRLOCK_TOO_MANY_PUMPS = SCENARIOS.register(
            "scenario_airlock_too_many_pumps", () -> PlayerScenarios::airlockReportsTooManyPumps);

    @SuppressWarnings("unused")
    private static final Object AIRLOCK_PANEL_POINTS_AT_FAULT = SCENARIOS.register(
            "scenario_airlock_panel_points_at_the_broken_piece",
            () -> PlayerScenarios::airlockPanelPointsAtTheBrokenPiece);

    @SuppressWarnings("unused")
    private static final Object CRATE_SURVIVES_RELOAD = SCENARIOS.register(
            "scenario_a_crates_contents_survive_a_reload",
            () -> PlayerScenarios::aCratesContentsSurviveAReload);

    @SuppressWarnings("unused")
    private static final Object BREAKING_A_FULL_MACHINE = SCENARIOS.register(
            "scenario_breaking_a_full_machine_drops_everything",
            () -> PlayerScenarios::breakingAFullMachineDropsEverything);

    @SuppressWarnings("unused")
    private static final Object CRATES_HOLD_PRESSURE = SCENARIOS.register(
            "scenario_a_wall_of_crates_holds_pressure",
            () -> PlayerScenarios::aWallOfCratesHoldsPressure);

    @SuppressWarnings("unused")
    private static final Object WINNOWING_NEEDS_AIR = SCENARIOS.register(
            "scenario_the_winnowing_table_needs_air",
            () -> PlayerScenarios::theWinnowingTableNeedsAir);

    @SuppressWarnings("unused")
    private static final Object WRENCH_SURVEYS_THE_LINE = SCENARIOS.register(
            "scenario_the_wrench_surveys_the_line",
            () -> PlayerScenarios::theWrenchSurveysTheLine);

    @SuppressWarnings("unused")
    private static final Object LOCKED_DOOR_DOES_NOT_OPEN = SCENARIOS.register(
            "scenario_a_latched_door_does_not_open",
            () -> PlayerScenarios::aLockedDoorDoesNotOpen);

    @SuppressWarnings("unused")
    private static final Object REDSTONE_CANNOT_OPEN_LATCH = SCENARIOS.register(
            "scenario_redstone_cannot_open_a_latched_door",
            () -> PlayerScenarios::redstoneCannotOpenALatchedDoor);

    @SuppressWarnings("unused")
    private static final Object LATCH_OUTLIVES_CONTROLLER = SCENARIOS.register(
            "scenario_a_latch_outliving_its_controller_lets_go",
            () -> PlayerScenarios::aLatchOutlivingItsControllerLetsGo);

    @SuppressWarnings("unused")
    private static final Object CONDENSER_GIVES_WATER = SCENARIOS.register(
            "scenario_taking_water_from_the_condenser",
            () -> PlayerScenarios::takingWaterFromTheCondenser);

    @SuppressWarnings("unused")
    private static final Object HOPPER_DRAINS_CONDENSER = SCENARIOS.register(
            "scenario_hopper_drains_the_condenser_without_a_player",
            () -> PlayerScenarios::aHopperDrainsTheCondenserWithoutAPlayer);

    @SuppressWarnings("unused")
    private static final Object AIRLOCK_DOORS_IN_ONE_WALL = SCENARIOS.register(
            "scenario_airlock_both_doors_in_one_wall",
            () -> PlayerScenarios::airlockAcceptsBothDoorsInOneWall);

    @SuppressWarnings("unused")
    private static final Object AIRLOCK_COMMISSIONED_BY_HAND = SCENARIOS.register(
            "scenario_airlock_commissioned_by_hand",
            () -> PlayerScenarios::airlockCommissionedWithTheWrench);

    @SuppressWarnings("unused")
    private static final Object AIRLOCK_NAMES_YOUR_DEVICE = SCENARIOS.register(
            "scenario_airlock_names_the_device_you_bound",
            () -> PlayerScenarios::airlockNamesTheDeviceYouBound);

    @SuppressWarnings("unused")
    private static final Object AIRLOCK_MIGRATES_OLD_BUILD = SCENARIOS.register(
            "scenario_airlock_migrates_an_old_build",
            () -> PlayerScenarios::anAirlockBuiltBeforeCommissioningStillWorks);

    @SuppressWarnings("unused")
    private static final Object AIRLOCK_BROKEN_DOOR_MID_CYCLE = SCENARIOS.register(
            "scenario_airlock_broken_door_mid_cycle",
            () -> PlayerScenarios::breakingABoundDoorMidCycleFailsSafe);

    @SuppressWarnings("unused")
    private static final Object TWO_CONTROLLERS_ONE_PUMP = SCENARIOS.register(
            "scenario_two_controllers_one_pump",
            () -> PlayerScenarios::twoControllersDoNotFightOverOnePump);

    @SuppressWarnings("unused")
    private static final Object TWO_CONTROLLERS_ONE_DOOR = SCENARIOS.register(
            "scenario_two_controllers_one_door",
            () -> PlayerScenarios::twoControllersDoNotFightOverOneDoor);

    @SuppressWarnings("unused")
    private static final Object AIRLOCK_DOES_NOT_CLAIM_UNUSED_AIR = SCENARIOS.register(
            "scenario_airlock_does_not_claim_stored_air_it_never_used",
            () -> PlayerScenarios::airlockDoesNotClaimStoredAirItNeverUsed);

    @SuppressWarnings("unused")
    private static final Object VALVE_CONDUCTS_ALONG_AXIS = SCENARIOS.register(
            "scenario_valve_conducts_along_axis", () -> PlayerScenarios::valveConductsOnlyAlongItsAxis);

    @SuppressWarnings("unused")
    private static final Object VALVE_SNAPS_TO_RUN = SCENARIOS.register(
            "scenario_valve_snaps_to_the_run", () -> PlayerScenarios::valveSnapsToTheRunBuiltAroundIt);

    @SuppressWarnings("unused")
    private static final Object AIRLOCK_FULL_CYCLE = SCENARIOS.register(
            "scenario_airlock_full_cycle", () -> PlayerScenarios::airlockFullCycleKeepsTheAir);

    @SuppressWarnings("unused")
    private static final Object PIPE_JOINS_PUMP_ENDS_ONLY = SCENARIOS.register(
            "scenario_pipe_joins_pump_ends_only", () -> PlayerScenarios::pipeJoinsAPumpOnlyAtItsEnds);

    @SuppressWarnings("unused")
    private static final Object BREAKING_A_PUMP_MID_RUN = SCENARIOS.register(
            "scenario_breaking_a_pump_mid_run", () -> PlayerScenarios::breakingAPumpMidRun);

    @SuppressWarnings("unused")
    private static final Object PORT_INTO_SOLID_ROCK = SCENARIOS.register(
            "scenario_port_into_solid_rock", () -> PlayerScenarios::portIntoSolidRock);

    @SuppressWarnings("unused")
    private static final Object ONE_BLOCK_ROOM = SCENARIOS.register(
            "scenario_one_block_room", () -> PlayerScenarios::oneBlockRoom);

    @SuppressWarnings("unused")
    private static final Object FEED_SLOT_REFUSES = SCENARIOS.register(
            "scenario_feed_slot_refuses_wrong_items",
            () -> PlayerScenarios::feedSlotRefusesWrongItems);

    @SuppressWarnings("unused")
    private static final Object MACHINE_SAYS_WHY_IT_STOPPED = SCENARIOS.register(
            "scenario_machine_says_why_it_stopped",
            () -> PlayerScenarios::aStalledMachineSaysWhichStallItIs);

    @SuppressWarnings("unused")
    private static final Object TWO_ROOMS_STAY_TWO = SCENARIOS.register(
            "scenario_two_sealed_rooms_are_not_one",
            () -> PlayerScenarios::twoSealedRoomsAreNotOneRoom);

    @SuppressWarnings("unused")
    private static final Object AMMONIA_HEAT_PIPE_MOVES_HEAT = SCENARIOS.register(
            "scenario_ammonia_heat_pipe_moves_real_heat",
            () -> PlayerScenarios::ammoniaHeatPipeMovesRealHeatBetweenTwoRooms);

    @SuppressWarnings("unused")
    private static final Object AMMONIA_HEAT_PIPE_STAYS_SEALED = SCENARIOS.register(
            "scenario_ammonia_heat_pipe_does_not_leak_gas",
            () -> PlayerScenarios::ammoniaHeatPipeDoesNotLeakGasBetweenRooms);

    @SuppressWarnings("unused")
    private static final Object PARAFFIN_THERMAL_MASS_HOLDS_THE_ROOM = SCENARIOS.register(
            "scenario_paraffin_thermal_mass_holds_the_room_at_its_melt_point",
            () -> PlayerScenarios::paraffinThermalMassHoldsTheRoomAtItsMeltPoint);

    @SuppressWarnings("unused")
    private static final Object PAINTED_HULL_SETTLES_COOLER_IN_THE_SUN = SCENARIOS.register(
            "scenario_painted_hull_settles_cooler_in_the_sun_than_bare",
            () -> PlayerScenarios::paintedHullSettlesCoolerInTheSunThanBare);

    @SuppressWarnings("unused")
    private static final Object WATER_FROM_ROCK = SCENARIOS.register(
            "scenario_baking_rock_gives_water",
            () -> PlayerScenarios::bakingRockPutsWaterIntoTheAir);

    @SuppressWarnings("unused")
    private static final Object HOPPERS_RESPECT_SLOT_ROLES = SCENARIOS.register(
            "scenario_hoppers_respect_slot_roles",
            () -> PlayerScenarios::hoppersFeedTheTopAndTakeFromTheBottom);

    @SuppressWarnings("unused")
    private static final Object WORKING_COSTS_AIR = SCENARIOS.register(
            "scenario_working_costs_more_air", () -> PlayerScenarios::workingCostsMoreAirThanResting);

    @SuppressWarnings("unused")
    private static final Object AIRLOCK_IS_THE_SAFE_ROUTE = SCENARIOS.register(
            "scenario_airlock_is_safer_than_the_door",
            () -> PlayerScenarios::cyclingIsSafeAndOpeningTheDoorIsNot);

    @SuppressWarnings("unused")
    private static final Object AIRLOCK_SURVIVES_RELOAD = SCENARIOS.register(
            "scenario_airlock_phase_survives_a_reload",
            () -> PlayerScenarios::airlockPhaseSurvivesAReload);

    @SuppressWarnings("unused")
    private static final Object AIRLOCK_SHUT_VALVE_STALLS = SCENARIOS.register(
            "scenario_airlock_shut_valve_stalls_not_hangs",
            () -> PlayerScenarios::airlockShutValveStallsNotHangs);

    @SuppressWarnings("unused")
    private static final Object ASTRA_DEPLETION_SURVIVES_A_RELOAD = SCENARIOS.register(
            "scenario_astra_depletion_survives_a_reload",
            () -> PlayerScenarios::astraDepletionSurvivesAReload);

    @SuppressWarnings("unused")
    private static final Object ASTRA_DRAW_AT_NEVER_TAKES_MORE_THAN_IS_THERE = SCENARIOS.register(
            "scenario_astra_draw_at_never_takes_more_than_is_there",
            () -> PlayerScenarios::astraDrawAtNeverTakesMoreThanIsThere);

    @SuppressWarnings("unused")
    private static final Object ASTRA_COLLECTOR_REALLY_DEPLETES_THE_FIELD = SCENARIOS.register(
            "scenario_astra_collector_really_depletes_the_field",
            () -> PlayerScenarios::astraCollectorReallyDepletesTheField);

    @SuppressWarnings("unused")
    private static final Object ASTRA_COLLECTOR_PRECIPITATES_A_SUFFICIENT_CHARGE = SCENARIOS.register(
            "scenario_astra_collector_precipitates_a_sufficient_charge",
            () -> PlayerScenarios::astraCollectorPrecipitatesASufficientCharge);

    @SuppressWarnings("unused")
    private static final Object ASTRA_ALTAR_REFUSES_AN_INCOMPLETE_FIGURE = SCENARIOS.register(
            "scenario_astra_altar_refuses_an_incomplete_figure",
            () -> PlayerScenarios::astraAltarRefusesAnIncompleteFigure);

    @SuppressWarnings("unused")
    private static final Object ASTRA_ALTAR_ACTIVATES_AND_COMPLETES_A_REAL_RITUAL = SCENARIOS.register(
            "scenario_astra_altar_activates_and_completes_a_real_ritual",
            () -> PlayerScenarios::astraAltarActivatesAndCompletesARealRitual);

    @SuppressWarnings("unused")
    private static final Object ASTRA_FIELD_METER_IDENTIFIES_THE_GRADIENT = SCENARIOS.register(
            "scenario_astra_field_meter_identifies_the_gradient",
            () -> PlayerScenarios::astraFieldMeterIdentifiesTheGradientThroughRealReadings);

    @SuppressWarnings("unused")
    private static final Object ASTRA_GRADIENT_CLAIM_RESOLVES_END_TO_END = SCENARIOS.register(
            "scenario_astra_gradient_claim_resolves_end_to_end",
            () -> PlayerScenarios::astraGradientClaimResolvesEndToEnd);

    @SuppressWarnings("unused")
    private static final Object ASTRA_SOUNDER_REPORTS_KNOWN_MINERALS_AND_NOTHING_ELSE = SCENARIOS.register(
            "scenario_astra_sounder_reports_known_minerals_and_nothing_else",
            () -> PlayerScenarios::astraSounderReportsKnownMineralsAndNothingElse);

    @SuppressWarnings("unused")
    private static final Object CRYOCOOLER_LIQUEFIES_ROOM_GAS = SCENARIOS.register(
            "scenario_a_cryocooler_liquefies_room_gas_into_its_bound_tank",
            () -> PlayerScenarios::aCryocoolerLiquefiesRoomGasIntoItsBoundTank);

    @SuppressWarnings("unused")
    private static final Object NEGLECTED_CRYO_TANK_BLEVES = SCENARIOS.register(
            "scenario_a_neglected_cryo_tank_bleves",
            () -> PlayerScenarios::aNeglectedCryoTankBleves);

    @SuppressWarnings("unused")
    private static final Object RTG_GENERATES_REAL_POWER = SCENARIOS.register(
            "scenario_an_rtg_generates_real_power",
            () -> PlayerScenarios::anRtgGeneratesRealPower);

    @SuppressWarnings("unused")
    private static final Object RADIATION_SOURCES_REAL_SHIELDING = SCENARIOS.register(
            "scenario_radiation_sources_inverse_square_and_real_shielding",
            () -> PlayerScenarios::radiationSourcesComputeRealInverseSquareAndShielding);

    @SuppressWarnings("unused")
    private static final Object A_SECOND_EVA_SUIT_CAN_BE_BUILT_FROM_SCRATCH = SCENARIOS.register(
            "scenario_a_second_eva_suit_can_be_built_from_scratch",
            () -> PlayerScenarios::aSecondEvaSuitCanBeBuiltFromScratchAndStartsJustAsBroken);

    // ------------------------------------------------------------------ scenarios

    /**
     * <em>"I am about to open this room to vacuum. I want its air in a tank first."</em>
     *
     * <p>The whole reason the plumbing tier exists. Asserts what the player watches: the
     * room's pressure falling and the tank's rising.
     */
    private static void storeARoomsAir(GameTestHelper helper) {
        Rig rig = Rig.build(helper, 4);
        double roomBefore = rig.room().pressureKPa();
        double tankBefore = rig.tank.pressureKPa();

        rig.run(200);

        if (!(rig.tank.pressureKPa() > tankBefore + 1)) {
            helper.fail("Followed the guide exactly and the tank did not fill: "
                    + tankBefore + " -> " + rig.tank.pressureKPa() + " kPa");
            return;
        }
        if (!(rig.room().pressureKPa() < roomBefore)) {
            helper.fail("The tank filled but the room did not empty, so gas is being"
                    + " created: room stayed at " + rig.room().pressureKPa() + " kPa");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"Pump the room down before you start a run."</em> The isolation stack's first,
     * currently-buildable stage (design/astra-incognita.md §8.3) needs no new block at all — the
     * gas pump this exact rig already proves in {@link #storeARoomsAir} is the whole mechanism.
     *
     * <p>This is the check that the two systems are actually wired together (rule 13): a
     * Coherence model that improves in a unit test but never moves when a player does the real
     * thing in the real world is a number nothing in the game can actually reach, which is
     * exactly the class of bug the whole demolished magic branch was made of.
     */
    private static void pumpingDownARoomImprovesItsCoherence(GameTestHelper helper) {
        Rig rig = Rig.build(helper, 4);
        double pressureBefore = rig.room().pressureKPa();
        double gasTauBefore = play.xponer.astronima.sim.magic.Coherence.gasSeconds(pressureBefore);

        rig.run(200);

        double pressureAfter = rig.room().pressureKPa();
        double gasTauAfter = play.xponer.astronima.sim.magic.Coherence.gasSeconds(pressureAfter);

        if (!(pressureAfter < pressureBefore)) {
            helper.fail("The pump ran and the room did not empty: stayed at "
                    + pressureAfter + " kPa");
            return;
        }
        if (!(gasTauAfter > gasTauBefore)) {
            helper.fail("The room emptied (" + pressureBefore + " -> " + pressureAfter
                    + " kPa) but the coherence gas term did not improve (" + gasTauBefore
                    + " -> " + gasTauAfter + " s) - the pump and the coherence model are not"
                    + " actually connected");
            return;
        }
        helper.succeed();
    }

    /**
     * design/astra-incognita.md §8.2's own "Proven how": <em>"A scenario that reads a site,
     * starts [noisy machinery] nearby, and asserts the vibration bar — and only the vibration
     * bar — moves."</em>
     *
     * <p>Simplified from the design's own "crusher twenty blocks away" per rule 8: the ore
     * crusher is not yet wired into {@code RoomNoise}'s rated-source list, and {@code
     * Atmosphere.noiseDbAt} is a same-room model (real distance attenuation, but only within
     * one sealed volume) rather than a through-rock long-range one. A wall-mounted scrubber —
     * already rated at 55 dB, already the mod's most common running machine — proves the exact
     * same claim: the six-term breakdown is a real decomposition, not six copies of one number
     * that happen to be labelled differently (rule 28's shape, applied to a readout with six
     * rows instead of one).
     *
     * <p>The 45-tick delay is not padding: {@code Atmosphere}'s noise survey is cached for 40
     * ticks (a room-wide block-walk on every read would be too expensive), so a reading taken
     * before real ticks have passed would still be looking at the pre-scrubber survey.
     */
    private static void aRunningMachineMovesOnlyTheVibrationBar(GameTestHelper helper) {
        BlockPos inside = new BlockPos(2, 2, 2);
        ModTestFunctions.buildBoxAround(helper, inside);
        BlockPos machinePos = inside.offset(2, 0, 0);

        var level = helper.getLevel();
        Atmosphere atmosphere = Atmosphere.get(level);
        BlockPos absoluteInside = helper.absolutePos(inside);
        atmosphere.invalidate(absoluteInside);

        var before = play.xponer.astronima.atmosphere.CoherenceReading.at(level, absoluteInside, false);

        helper.runAfterDelay(45, () -> {
            helper.setBlock(machinePos, ModBlocks.SCRUBBER.get().defaultBlockState());
            var after = play.xponer.astronima.atmosphere.CoherenceReading.at(level, absoluteInside, false);

            double vibrationBefore = before.get(play.xponer.astronima.sim.magic.Coherence.Term.VIBRATION);
            double vibrationAfter = after.get(play.xponer.astronima.sim.magic.Coherence.Term.VIBRATION);
            helper.assertTrue(vibrationAfter < vibrationBefore * 0.5,
                    "a running scrubber should shorten the vibration term, went "
                            + vibrationBefore + " -> " + vibrationAfter + " s");

            for (var term : play.xponer.astronima.sim.magic.Coherence.Term.values()) {
                if (term == play.xponer.astronima.sim.magic.Coherence.Term.VIBRATION) {
                    continue;
                }
                double b = before.get(term);
                double a = after.get(term);
                boolean bothInfinite = Double.isInfinite(b) && Double.isInfinite(a);
                helper.assertTrue(bothInfinite || Math.abs(a - b) < 1e-6,
                        term + " should not have moved when only the noise changed, went "
                                + b + " -> " + a + " s");
            }
            helper.succeed();
        });
    }

    /**
     * <em>"I closed the valve. It should stop."</em>
     *
     * <p>Reported from play: it did not. Asserts the thing the player watches — the tank
     * stops gaining — rather than any number inside the network.
     */
    private static void shutValveStopsFilling(GameTestHelper helper) {
        Rig rig = Rig.build(helper, 4);
        rig.run(60);
        double whileOpen = rig.tank.pressureKPa();

        helper.setBlock(rig.valve, ModBlocks.GAS_VALVE.get().defaultBlockState()
                .setValue(GasValveBlock.SETTING, 0));
        rig.run(200);

        double whileShut = rig.tank.pressureKPa();
        if (whileShut > whileOpen + 0.5) {
            helper.fail("A shut valve did not stop the pump: tank went "
                    + whileOpen + " -> " + whileShut + " kPa with the valve closed");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I stored the air. Now I want it back in the room."</em>
     *
     * <p>Storing is only useful if it comes back. A tank that fills and never returns its
     * contents is a one-way sink, and the whole loop would be pointless — which is the
     * complaint that the setup is "fun but useless".
     */
    private static void giveTheAirBack(GameTestHelper helper) {
        Rig rig = Rig.build(helper, 4);
        rig.run(200);
        double stored = rig.tank.pressureKPa();
        if (stored <= 0) {
            helper.fail("Nothing was stored, so the return trip cannot be tested");
            return;
        }

        // Turn the pump round: tank becomes the source, room the destination.
        helper.setBlock(rig.pump, ModBlocks.GAS_PUMP.get().defaultBlockState()
                .setValue(GasPumpBlock.FACING, Direction.NORTH));
        double roomBefore = rig.room().pressureKPa();
        rig.run(200);

        if (!(rig.room().pressureKPa() > roomBefore)) {
            helper.fail("Reversing the pump did not put air back into the room:"
                    + " it stayed at " + rig.room().pressureKPa() + " kPa");
            return;
        }
        if (!(rig.tank.pressureKPa() < stored)) {
            helper.fail("The room gained air the tank did not lose");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"Something is wrong — I will break a pipe to stop it."</em>
     *
     * <p>Breaking the run must actually stop it. If a severed network kept working, no
     * player could ever isolate a fault, and there would be no way to undo a mistake.
     */
    private static void breakingAPipeStopsIt(GameTestHelper helper) {
        Rig rig = Rig.build(helper, 4);
        rig.run(60);
        double before = rig.tank.pressureKPa();

        helper.setBlock(rig.valve, Blocks.AIR.defaultBlockState());
        rig.run(200);

        if (rig.tank.pressureKPa() > before + 0.5) {
            helper.fail("Breaking the run did not stop it: tank went " + before
                    + " -> " + rig.tank.pressureKPa() + " kPa through a gap");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I stored my air, quit for the night, and came back."</em>
     *
     * <p>Test plan row 4.1, ranked first because it has the highest cost of failure on
     * the page: a tank that quietly forgets its contents destroys work the player did,
     * with no error and no way to notice until they need the air.
     *
     * <p>Saving is exercised by writing the tank out and reading it back into a fresh
     * entity, which is the same path a world reload takes.
     */
    private static void storedAirSurvivesAReload(GameTestHelper helper) {
        Rig rig = Rig.build(helper, 4);
        rig.run(200);

        double stored = rig.tank.pressureKPa();
        if (stored <= 0) {
            helper.fail("Nothing was stored, so the reload cannot be tested");
            return;
        }

        // Round-trip through the save format the world uses.
        var registries = helper.getLevel().registryAccess();
        try (var scope = new net.minecraft.util.ProblemReporter.ScopedCollector(
                com.mojang.logging.LogUtils.getLogger())) {
            var output = net.minecraft.world.level.storage.TagValueOutput
                    .createWithContext(scope, registries);
            rig.tank.saveWithoutMetadata(output);
            var input = net.minecraft.world.level.storage.TagValueInput
                    .create(scope, registries, output.buildResult());

            GasTankBlockEntity reloaded = new GasTankBlockEntity(
                    helper.absolutePos(rig.tankPos()),
                    helper.getBlockState(rig.tankPos()));
            reloaded.loadWithComponents(input);

            if (Math.abs(reloaded.pressureKPa() - stored) > stored * 1e-6) {
                helper.fail("A tank lost its contents across a save: " + stored
                        + " kPa became " + reloaded.pressureKPa() + " kPa");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * <em>"I will put a second port on the same room and pump between them."</em>
     *
     * <p>Test plan row 2, "ports a room into itself". A room pumping into itself would
     * be a perpetual motion machine — the pump would report running forever while
     * nothing changed, or worse, compress gas out of nowhere.
     */
    private static void aRoomCannotPumpIntoItself(GameTestHelper helper) {
        BlockPos inside = new BlockPos(2, 2, 2);
        ModTestFunctions.buildBoxAround(helper, inside);
        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        if (room == null) {
            helper.fail("The sealed box did not become a room");
            return;
        }
        room.addGasAt(Gas.OXYGEN, 200, 293.15);

        // Two ports into the SAME room, with a pump between them.
        BlockPos north = inside.offset(0, 0, -2);
        BlockPos south = inside.offset(0, 0, 2);
        helper.setBlock(north, ModBlocks.GAS_PORT.get().defaultBlockState()
                .setValue(GasPortBlock.FACING, Direction.SOUTH));
        helper.setBlock(south, ModBlocks.GAS_PORT.get().defaultBlockState()
                .setValue(GasPortBlock.FACING, Direction.NORTH));
        BlockPos pump = south.offset(0, 0, 1);
        helper.setBlock(pump, ModBlocks.GAS_PUMP.get().defaultBlockState()
                .setValue(GasPumpBlock.FACING, Direction.SOUTH));
        atmosphere.invalidate(helper.absolutePos(north));
        atmosphere.invalidate(helper.absolutePos(south));

        double before = room.gases().get(Gas.OXYGEN);
        for (int i = 0; i < 200; i++) {
            GasPumpBlockEntity motor = helper.getBlockEntity(pump, GasPumpBlockEntity.class);
            if (motor == null) {
                break;
            }
            BlockPos absolute = helper.absolutePos(pump);
            motor.pumpOnce(helper.getLevel(), absolute,
                    helper.getLevel().getBlockState(absolute));
        }

        RoomState after = atmosphere.roomAt(helper.absolutePos(inside));
        double now = after == null ? 0 : after.gases().get(Gas.OXYGEN);
        if (Math.abs(now - before) > Math.max(1e-6, before * 1e-6)) {
            helper.fail("A room pumped into itself and its gas changed from " + before
                    + " to " + now + " mol - that is gas from nowhere");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I will put a partition wall through this room."</em>
     *
     * <p>Test plan row 4.2. Splitting and merging rooms happens during ordinary
     * building — one placed block — so getting the division wrong creates or destroys
     * air on an action nobody would think of as dangerous. Nothing else in the mod
     * changes topology this casually.
     *
     * <p>Asserts what an observer reads: total air before equals total after, in both
     * directions. The split additionally must not change either half's <em>pressure</em>
     * — gas divides by volume, so two halves of one room start at the pressure the
     * whole room had.
     */
    private static void buildingAWallKeepsTheAir(GameTestHelper helper) {
        BlockPos inside = new BlockPos(2, 2, 2);
        ModTestFunctions.buildBoxAround(helper, inside);

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        RoomState whole = atmosphere.roomAt(helper.absolutePos(inside));
        if (whole == null) {
            helper.fail("The sealed box did not become a room");
            return;
        }
        whole.addGasAt(Gas.OXYGEN, 270, 293.15);
        double before = whole.gases().get(Gas.OXYGEN);
        double pressureBefore = whole.pressureKPa();

        // A partition straight through the middle: the interior is 3x3x3, so walling
        // the centre plane leaves two 3x3x1 halves.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                helper.setBlock(inside.offset(dx, dy, 0),
                        ModBlocks.HULL_PLATE.get().defaultBlockState());
            }
        }
        atmosphere.invalidate(helper.absolutePos(inside));

        RoomState north = atmosphere.roomAt(helper.absolutePos(inside.offset(0, 0, -1)));
        RoomState south = atmosphere.roomAt(helper.absolutePos(inside.offset(0, 0, 1)));
        if (north == null || south == null) {
            helper.fail("Walling the middle did not leave two rooms: north="
                    + (north != null) + " south=" + (south != null));
            return;
        }
        if (north == south) {
            helper.fail("The partition did not actually separate the halves");
            return;
        }

        double split = north.gases().get(Gas.OXYGEN) + south.gases().get(Gas.OXYGEN);
        if (Math.abs(split - before) > before * 1e-6) {
            helper.fail("Building a partition changed the air from " + before
                    + " to " + split + " mol");
            return;
        }
        // Halves of one room start at the pressure the whole room had.
        if (Math.abs(north.pressureKPa() - pressureBefore) > pressureBefore * 0.02) {
            helper.fail("A half of the room came out at " + north.pressureKPa()
                    + " kPa when the whole was at " + pressureBefore + " kPa");
            return;
        }

        // And breaking the partition again must put it back without inventing any.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                helper.setBlock(inside.offset(dx, dy, 0), Blocks.AIR.defaultBlockState());
            }
        }
        atmosphere.invalidate(helper.absolutePos(inside));

        RoomState merged = atmosphere.roomAt(helper.absolutePos(inside));
        if (merged == null) {
            helper.fail("Breaking the partition left no room at all");
            return;
        }
        double after = merged.gases().get(Gas.OXYGEN);
        if (Math.abs(after - before) > before * 1e-6) {
            helper.fail("Breaking a partition changed the air from " + before
                    + " to " + after + " mol");
            return;
        }
        helper.succeed();
    }


    /**
     * <em>«с каждым поломаным блоком падает kPa… после 10 блоков дышать невозможно».</em>
     *
     * <p><strong>The reported bug, as one experiment.</strong> A mined block adds a cubic
     * metre and no gas comes with it, so extending a drift that is still joined to the
     * habitat dilutes the air the player is breathing — measured at 6.6 mol of oxygen per
     * block at the breathable floor, against fifteen blocks per oxygen candle. Cut your own
     * way out of a sealed module and you suffocate long before you reach anything.
     *
     * <p>The fix is not a bigger number, it is a shut door, which is what the crew module now
     * spawns with. So this drives the identical dig twice, and <strong>the only difference
     * between the two runs is the bulkhead</strong> — same block broken, same drift, same
     * habitat. Shut, the habitat must not notice. Open, it must.
     *
     * <p>The second half is not decoration: without it the first half passes just as well
     * when the carve did nothing at all, which is the vacuous-scenario trap this file has
     * already been caught by twice.
     */
    private static void diggingBehindAShutBulkhead(GameTestHelper helper) {
        BlockPos habitat = new BlockPos(2, 2, 2);
        ModTestFunctions.buildBoxAround(helper, habitat);

        // Rock to dig into, kept inside the area a gametest actually owns (rule 21).
        for (int x = 5; x <= 7; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = 0; z <= 4; z++) {
                    helper.setBlock(new BlockPos(x, y, z),
                            ModBlocks.HULL_PLATE.get().defaultBlockState());
                }
            }
        }

        BlockPos doorFoot = new BlockPos(4, 2, 2);
        helper.setBlock(doorFoot, ModBlocks.BULKHEAD_DOOR.get().defaultBlockState());
        helper.setBlock(doorFoot.above(), ModBlocks.BULKHEAD_DOOR.get().defaultBlockState()
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));

        // The vestibule beyond it: two cells, already cut, the way the module spawns.
        BlockPos vestibule = new BlockPos(5, 2, 2);
        helper.setBlock(vestibule, Blocks.AIR.defaultBlockState());
        helper.setBlock(vestibule.above(), Blocks.AIR.defaultBlockState());

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(doorFoot));

        RoomState room = atmosphere.roomAt(helper.absolutePos(habitat));
        if (room == null) {
            helper.fail("Setup failed: no habitat room");
            return;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        room.addGasAt(Gas.OXYGEN, 200, 293.15);

        // ---- the same block, broken behind a SHUT door
        BlockPos face = new BlockPos(6, 2, 2);
        double shutBefore = pressureAt(atmosphere, helper, habitat);
        helper.setBlock(face, Blocks.AIR.defaultBlockState());
        atmosphere.invalidate(helper.absolutePos(face));
        double shutAfter = pressureAt(atmosphere, helper, habitat);

        if (Math.abs(shutAfter - shutBefore) > 0.01) {
            helper.fail("Extending a drift behind a SHUT bulkhead moved the habitat from "
                    + shutBefore + " to " + shutAfter + " kPa - the player still cannot dig"
                    + " their way out without spending the air they breathe");
            return;
        }

        // ---- and again with the door open, which must hurt
        helper.setBlock(face, ModBlocks.HULL_PLATE.get().defaultBlockState());
        for (BlockPos half : new BlockPos[] {doorFoot, doorFoot.above()}) {
            helper.setBlock(half, helper.getBlockState(half)
                    .setValue(BlockStateProperties.OPEN, true));
        }
        atmosphere.invalidate(helper.absolutePos(doorFoot));
        atmosphere.invalidate(helper.absolutePos(face));

        double openBefore = pressureAt(atmosphere, helper, habitat);
        helper.setBlock(face, Blocks.AIR.defaultBlockState());
        atmosphere.invalidate(helper.absolutePos(face));
        double openAfter = pressureAt(atmosphere, helper, habitat);

        if (openAfter >= openBefore - 0.01) {
            helper.fail("Extending the drift with the bulkhead OPEN left the habitat at "
                    + openAfter + " kPa against " + openBefore + " - mining does not dilute"
                    + " a joined room at all, so the shut-door half of this test proves"
                    + " nothing");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"Swap the electrode, not the wiring."</em>
     *
     * <p>The row {@code design/electrolysis.md} §6 calls "the row that matters most": what a
     * cell reaches is set by which electrode is installed, and power never enters the
     * decision. This drives the same cell through two identical, un-powered batches — same
     * feed, same idle crank cadence, same sealed room — and only swaps the electrode between
     * them. If the metal that comes out ever tracked anything about how fast the batch ran
     * instead of what was in the electrode slot, this is where it would show up.
     *
     * <p>Also the first real exercise of {@code ElectrolysisCellBlockEntity#finishBatch()}
     * against a live world: free iron with no electrode at all (the "a fresh cell works the
     * moment it is fed" promise in §2), oxygen actually landing in the sealed room next door,
     * and the installed electrode surviving its own batch untouched — {@code design/
     * electrolysis.md} §7's "no electrode wear," which only a live batch can actually prove.
     */
    private static void electrolysisCellTargetsTheInstalledElectrode(GameTestHelper helper) {
        BlockPos cellPos = new BlockPos(2, 3, 2);
        sealPocketUnder(helper, cellPos);
        helper.setBlock(cellPos, ModBlocks.ELECTROLYSIS_CELL.get().defaultBlockState());

        var cell = helper.getBlockEntity(cellPos, ElectrolysisCellBlockEntity.class);
        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(cellPos.below()));
        RoomState room = atmosphere.roomAt(helper.absolutePos(cellPos.below()));
        if (cell == null || room == null) {
            helper.fail("Setup failed: cell or receiving room missing");
            return;
        }

        // No electrode at all: the cell's own free default.
        if (cell.target() != ElectrolysisSpecies.IRON) {
            helper.fail("An empty electrode slot targets " + cell.target()
                    + " instead of iron, so a fresh cell no longer works the moment it is fed");
            return;
        }
        cell.setItem(ElectrolysisCellBlockEntity.SLOT_INPUT, new ItemStack(ModItems.TAILINGS.get()));
        double o2BeforeIron = room.gases().get(Gas.OXYGEN);
        runMachine(cell, ElectrolysisCellBlockEntity.BATCH_WORK + 40);

        ItemStack ironOut = cell.getItem(ElectrolysisCellBlockEntity.SLOT_OUTPUT);
        if (!ironOut.is(net.minecraft.world.item.Items.IRON_INGOT) || ironOut.getCount() <= 0) {
            helper.fail("An idle, electrode-free cell fed tailings produced " + ironOut
                    + " instead of iron ingots");
            return;
        }
        if (!(room.gases().get(Gas.OXYGEN) > o2BeforeIron + 1e-9)) {
            helper.fail("A finished batch put no oxygen into the sealed room next door");
            return;
        }

        // Swap the electrode - nothing about the crank cadence changes - and the target
        // must follow the electrode alone.
        cell.setItem(ElectrolysisCellBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
        cell.setItem(ElectrolysisCellBlockEntity.SLOT_ELECTRODE,
                new ItemStack(ModItems.SILICON_ELECTRODE.get()));
        if (cell.target() != ElectrolysisSpecies.SILICON) {
            helper.fail("Installing a silicon_electrode left the cell targeting "
                    + cell.target() + " instead of silicon");
            return;
        }
        cell.setItem(ElectrolysisCellBlockEntity.SLOT_INPUT, new ItemStack(ModItems.TAILINGS.get()));
        double o2BeforeSilicon = room.gases().get(Gas.OXYGEN);
        runMachine(cell, ElectrolysisCellBlockEntity.BATCH_WORK + 40);

        ItemStack siliconOut = cell.getItem(ElectrolysisCellBlockEntity.SLOT_OUTPUT);
        if (!siliconOut.is(ModItems.SILICON.get()) || siliconOut.getCount() <= 0) {
            helper.fail("The exact same idle cell, only its electrode swapped, produced "
                    + siliconOut + " instead of silicon - the target followed something other"
                    + " than the electrode");
            return;
        }
        if (!(room.gases().get(Gas.OXYGEN) > o2BeforeSilicon + 1e-9)) {
            helper.fail("The silicon batch put no oxygen into the sealed room next door");
            return;
        }

        ItemStack electrodeAfter = cell.getItem(ElectrolysisCellBlockEntity.SLOT_ELECTRODE);
        if (!electrodeAfter.is(ModItems.SILICON_ELECTRODE.get()) || electrodeAfter.getCount() != 1) {
            helper.fail("The installed electrode did not survive its own batch untouched: "
                    + electrodeAfter + " - electrodes are equipment, not a reagent");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I fed the tower tholins and left it running - did the ethylene actually turn up next
     * door, or is the tower just eating my feedstock?"</em>
     *
     * <p>The same {@code receivingRoom()}/{@code WorkState.BACKPRESSURE} shape
     * {@code ElectrolysisCellBlockEntity}'s own gametest already proves, for a machine whose
     * whole product is gas: no solid output slot at all, so this is the only place that shape
     * gets exercised end to end (design/petrochemicals.md §2).
     */
    private static void crackingTowerVentsIntoItsOwnRoom(GameTestHelper helper) {
        BlockPos towerPos = new BlockPos(2, 3, 2);
        sealPocketUnder(helper, towerPos);
        helper.setBlock(towerPos, ModBlocks.CRACKING_TOWER.get().defaultBlockState());

        var tower = helper.getBlockEntity(towerPos, CrackingTowerBlockEntity.class);
        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(towerPos.below()));
        RoomState room = atmosphere.roomAt(helper.absolutePos(towerPos.below()));
        if (tower == null || room == null) {
            helper.fail("Setup failed: tower or receiving room missing");
            return;
        }

        double ethyleneBefore = room.gases().get(Gas.ETHYLENE);
        double methaneBefore = room.gases().get(Gas.METHANE);
        tower.setItem(CrackingTowerBlockEntity.SLOT_FEED_ONLY,
                new ItemStack(ModItems.THOLIN_CLUMP.get()));
        runMachine(tower, CrackingTowerBlockEntity.BATCH_WORK + 40);

        if (!(room.gases().get(Gas.ETHYLENE) > ethyleneBefore + 1e-9)) {
            helper.fail("A finished cracking batch put no ethylene into the sealed room next"
                    + " door");
            return;
        }
        if (!(room.gases().get(Gas.METHANE) > methaneBefore + 1e-9)) {
            helper.fail("A finished cracking batch put no methane into the sealed room next"
                    + " door - only ethylene, when both should come off the same charge");
            return;
        }
        if (!tower.getItem(CrackingTowerBlockEntity.SLOT_FEED_ONLY).isEmpty()) {
            helper.fail("The tholin clump survived a finished batch - the feed was never"
                    + " actually consumed");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I loaded titania and piped ethylene into the room - does the polymerizer actually
     * spend both, or is polyethylene coming from nowhere?"</em>
     *
     * <p>Mirrors {@code theFluidizedBedMatchesItsOwnSpinToTheGrind}'s own shape: a room reagent
     * read the same way the bed reads its hydrogen, drawn down rather than conjured - the
     * assertion that matters most for a machine whose catalyst is deliberately consumed rather
     * than merely installed (design/petrochemicals.md §2).
     */
    private static void polymerizerStringsEthyleneIntoPolyethylene(GameTestHelper helper) {
        BlockPos inside = new BlockPos(3, 2, 3);
        BlockPos polymerizerPos = inside.offset(1, 0, 0);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int ring = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                    helper.setBlock(inside.offset(dx, dy, dz), ring <= 1
                            ? Blocks.AIR.defaultBlockState()
                            : ModBlocks.HULL_PLATE.get().defaultBlockState());
                }
            }
        }
        helper.setBlock(polymerizerPos, ModBlocks.POLYMERIZER.get().defaultBlockState());

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(inside));
        Atmosphere.RoomReading reading = atmosphere.readingAt(helper.absolutePos(inside));
        if (reading == null || !reading.sealed()) {
            helper.fail("Setup failed: the polymerizer's room is not sealed, so its ethylene is"
                    + " really venting to space");
            return;
        }
        var polymerizer = helper.getBlockEntity(polymerizerPos, PolymerizerBlockEntity.class);
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        if (polymerizer == null || room == null) {
            helper.fail("Setup failed: polymerizer or room missing");
            return;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        room.addGasAt(Gas.ETHYLENE, 40, 293.15);

        polymerizer.setItem(PolymerizerBlockEntity.SLOT_INPUT, new ItemStack(ModItems.TITANIA.get()));
        double ethyleneBefore = room.gases().get(Gas.ETHYLENE);
        // The panel's own reagent-stock reading (design/machines.md's own Update section) has to
        // clamp to a full 100% here: the room holds far more ethylene than one batch needs.
        if (polymerizer.ethyleneFraction() < 0.999) {
            helper.fail("The reagent-stock reading disagreed with the real room gas it is supposed"
                    + " to reflect: ethyleneFraction=" + polymerizer.ethyleneFraction()
                    + " (expected ~1.0, the room holds far more than one batch needs)");
            return;
        }
        runMachine(polymerizer, PolymerizerBlockEntity.BATCH_WORK + 40);

        if (!(room.gases().get(Gas.ETHYLENE) < ethyleneBefore)) {
            helper.fail("the polymerizer did not draw its ethylene down from the room ("
                    + ethyleneBefore + " -> " + room.gases().get(Gas.ETHYLENE) + ") - the"
                    + " monomer is being conjured rather than consumed");
            return;
        }
        ItemStack polyethyleneOut = polymerizer.getItem(PolymerizerBlockEntity.SLOT_OUTPUT);
        if (!polyethyleneOut.is(ModItems.POLYETHYLENE.get()) || polyethyleneOut.getCount() <= 0) {
            helper.fail("A finished batch with titania loaded and ethylene in the room produced "
                    + polyethyleneOut + " instead of polyethylene");
            return;
        }
        ItemStack titaniaAfter = polymerizer.getItem(PolymerizerBlockEntity.SLOT_INPUT);
        if (titaniaAfter.getCount() >= 1) {
            helper.fail("The titania catalyst survived a finished batch untouched (" + titaniaAfter
                    + ") - a real catalyst here is deliberately spent in small amounts, not merely"
                    + " installed (design/petrochemicals.md §2)");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I fed the electrolyzer a water bottle - does it actually split into hydrogen and
     * oxygen in a real 2:1 ratio, or just some gas or other?"</em>
     *
     * <p>The same {@code receivingRoom()}/{@code BACKPRESSURE} shape as the cracking tower's own
     * gametest, for the electrolyzer's own reaction (design/chemistry-loop.md §2). Wires a
     * charged power cell in first (game-design audit #2, finding C:
     * {@code WaterElectrolyzerBlockEntity.canRunWithoutPower()} is now {@code false}) - before
     * that fix this scenario ran the batch to completion on {@code IDLE_RATE} alone with nothing
     * powering it at all, which was exactly the free-energy loop the audit found.
     */
    private static void waterElectrolyzerSplitsWaterInARealRatio(GameTestHelper helper) {
        BlockPos machinePos = new BlockPos(2, 3, 2);
        BlockPos cellPos = machinePos.offset(1, 0, 0);
        sealPocketUnder(helper, machinePos);
        helper.setBlock(machinePos, ModBlocks.WATER_ELECTROLYZER.get().defaultBlockState());
        helper.setBlock(cellPos, ModBlocks.POWER_CELL.get().defaultBlockState());

        var electrolyzer = helper.getBlockEntity(machinePos, WaterElectrolyzerBlockEntity.class);
        var cell = helper.getBlockEntity(cellPos, PowerCellBlockEntity.class);
        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(machinePos.below()));
        RoomState room = atmosphere.roomAt(helper.absolutePos(machinePos.below()));
        if (electrolyzer == null || cell == null || room == null) {
            helper.fail("Setup failed: electrolyzer, cell or receiving room missing");
            return;
        }
        cell.charge(PowerCellBlockEntity.CAPACITY_J);

        double h2Before = room.gases().get(Gas.HYDROGEN);
        double o2Before = room.gases().get(Gas.OXYGEN);
        electrolyzer.setItem(WaterElectrolyzerBlockEntity.SLOT_FEED_ONLY,
                net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                        net.minecraft.world.item.Items.POTION,
                        net.minecraft.world.item.alchemy.Potions.WATER));
        runMachine(electrolyzer, WaterElectrolyzerBlockEntity.BATCH_WORK + 40);

        double h2Made = room.gases().get(Gas.HYDROGEN) - h2Before;
        double o2Made = room.gases().get(Gas.OXYGEN) - o2Before;
        if (h2Made <= 0 || o2Made <= 0) {
            helper.fail("A finished electrolysis batch made " + h2Made + " mol H2 and "
                    + o2Made + " mol O2 - both should be positive");
            return;
        }
        if (Math.abs(h2Made - 2 * o2Made) > 1e-6) {
            helper.fail("Hydrogen and oxygen did not come off in a 2:1 ratio (" + h2Made + " : "
                    + o2Made + ") - real electrolysis is 2 H2O -> 2 H2 + O2");
            return;
        }
        if (!electrolyzer.getItem(WaterElectrolyzerBlockEntity.SLOT_FEED_ONLY).isEmpty()) {
            helper.fail("The water bottle survived a finished batch - the feed was never"
                    + " actually consumed");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"What if I never wire the electrolyzer to anything at all - does it still split water
     * for free, just slowly?"</em>
     *
     * <p>Game-design audit #2, finding C: chained to a fuel cell and a dehumidifier, an
     * electrolyzer that ran on {@code IDLE_RATE} with no power at all closed a genuine
     * perpetual-motion loop - water in, free electricity out, water back - that
     * {@code FuelCellBlockEntity}'s own doc comment says was deliberately kept out of the fuel
     * cell for exactly this reason. Unlike every other machine (which idles along unattended,
     * machines.md §7), this is the one reaction with no honest hand-crank equivalent
     * (design/oxygen.md §1). No power cell in this build at all - not even an uncharged one - so
     * there is nothing to draw from by design, not by omission.
     */
    private static void anUnpoweredElectrolyzerMakesNoProgressAtAll(GameTestHelper helper) {
        BlockPos machinePos = new BlockPos(2, 3, 2);
        sealPocketUnder(helper, machinePos);
        helper.setBlock(machinePos, ModBlocks.WATER_ELECTROLYZER.get().defaultBlockState());

        var electrolyzer = helper.getBlockEntity(machinePos, WaterElectrolyzerBlockEntity.class);
        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(machinePos.below()));
        RoomState room = atmosphere.roomAt(helper.absolutePos(machinePos.below()));
        if (electrolyzer == null || room == null) {
            helper.fail("Setup failed: electrolyzer or receiving room missing");
            return;
        }

        double h2Before = room.gases().get(Gas.HYDROGEN);
        double o2Before = room.gases().get(Gas.OXYGEN);
        electrolyzer.setItem(WaterElectrolyzerBlockEntity.SLOT_FEED_ONLY,
                net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                        net.minecraft.world.item.Items.POTION,
                        net.minecraft.world.item.alchemy.Potions.WATER));
        // Several batches' worth of ticks at the old IDLE_RATE - if even IDLE_RATE=1 were still
        // reachable here, this would easily finish two batches over.
        runMachine(electrolyzer, (WaterElectrolyzerBlockEntity.BATCH_WORK + 40) * 3);

        double h2Made = room.gases().get(Gas.HYDROGEN) - h2Before;
        double o2Made = room.gases().get(Gas.OXYGEN) - o2Before;
        if (h2Made != 0 || o2Made != 0) {
            helper.fail("An electrolyzer with no power at all still made " + h2Made + " mol H2 "
                    + "and " + o2Made + " mol O2 - electrolysis must not run for free");
            return;
        }
        if (electrolyzer.getItem(WaterElectrolyzerBlockEntity.SLOT_FEED_ONLY).isEmpty()) {
            helper.fail("The water bottle was consumed with no power ever supplied");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I feed the algae bioreactor a real water bottle, wire it to a charged cell, and
     * breathe in the room - does it actually make real food and real oxygen matching the CO2 I
     * gave up, or is this a black box making air and food from nothing? And with the power cell
     * pulled, does it just sit there instead of splitting water for free?"</em>
     *
     * <p>The same real reasoning {@code WaterElectrolyzerBlockEntity}'s own two scenarios already
     * proved for its own reaction: nothing about photosynthesis has a manual-labour equivalent
     * either, so an unpowered reactor must make real zero progress, not a slow trickle
     * (design/hydroponics.md §1.1, §3).
     */
    private static void algaeBioreactorMakesRealFoodAndOxygenFromRealCo2(GameTestHelper helper) {
        BlockPos machinePos = new BlockPos(2, 3, 2);
        BlockPos cellPos = machinePos.offset(1, 0, 0);
        sealPocketUnder(helper, machinePos);
        helper.setBlock(machinePos,
                ModBlocks.ALGAE_BIOREACTOR.get().defaultBlockState());
        helper.setBlock(cellPos, ModBlocks.POWER_CELL.get().defaultBlockState());

        var reactor = helper.getBlockEntity(machinePos,
                play.xponer.astronima.block.entity.AlgaeBioreactorBlockEntity.class);
        var cell = helper.getBlockEntity(cellPos, PowerCellBlockEntity.class);
        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(machinePos.below()));
        RoomState room = atmosphere.roomAt(helper.absolutePos(machinePos.below()));
        if (reactor == null || cell == null || room == null) {
            helper.fail("Setup failed: reactor, cell or receiving room missing");
            return;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        room.addGasAt(Gas.CARBON_DIOXIDE,
                play.xponer.astronima.sim.chem.Photosynthesis.CO2_PER_BOTTLE_MOL * 2, 293.15);
        cell.charge(PowerCellBlockEntity.CAPACITY_J);

        double co2Before = room.gases().get(Gas.CARBON_DIOXIDE);
        double o2Before = room.gases().get(Gas.OXYGEN);
        reactor.setItem(
                play.xponer.astronima.block.entity.AlgaeBioreactorBlockEntity.SLOT_INPUT,
                net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                        net.minecraft.world.item.Items.POTION,
                        net.minecraft.world.item.alchemy.Potions.WATER));
        runMachine(reactor,
                play.xponer.astronima.block.entity.AlgaeBioreactorBlockEntity.BATCH_WORK + 40);

        double co2Spent = co2Before - room.gases().get(Gas.CARBON_DIOXIDE);
        double o2Made = room.gases().get(Gas.OXYGEN) - o2Before;
        if (co2Spent <= 0 || o2Made <= 0) {
            helper.fail("A powered, fed batch spent " + co2Spent + " mol CO2 and made " + o2Made
                    + " mol O2 - both should be positive");
            return;
        }
        if (Math.abs(co2Spent - o2Made) > 1e-6) {
            helper.fail("CO2 spent and O2 made were not equal (" + co2Spent + " vs " + o2Made
                    + ") - real photosynthesis is 1:1");
            return;
        }
        ItemStack biomassOut = reactor.getItem(
                play.xponer.astronima.block.entity.AlgaeBioreactorBlockEntity.SLOT_OUTPUT);
        if (!biomassOut.is(ModItems.ALGAE_BIOMASS.get()) || biomassOut.getCount() <= 0) {
            helper.fail("A powered, fed, CO2-ample batch produced " + biomassOut
                    + " instead of real algae biomass");
            return;
        }
        if (!reactor.getItem(
                play.xponer.astronima.block.entity.AlgaeBioreactorBlockEntity.SLOT_INPUT).isEmpty()) {
            helper.fail("The water bottle survived a finished batch");
            return;
        }

        // Second: the same real feed and CO2, this time with no power cell at all.
        helper.setBlock(cellPos, Blocks.AIR.defaultBlockState());
        room.addGasAt(Gas.CARBON_DIOXIDE,
                play.xponer.astronima.sim.chem.Photosynthesis.CO2_PER_BOTTLE_MOL * 2, 293.15);
        double co2BeforeUnpowered = room.gases().get(Gas.CARBON_DIOXIDE);
        double o2BeforeUnpowered = room.gases().get(Gas.OXYGEN);
        reactor.setItem(
                play.xponer.astronima.block.entity.AlgaeBioreactorBlockEntity.SLOT_INPUT,
                net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                        net.minecraft.world.item.Items.POTION,
                        net.minecraft.world.item.alchemy.Potions.WATER));
        runMachine(reactor,
                (play.xponer.astronima.block.entity.AlgaeBioreactorBlockEntity.BATCH_WORK + 40) * 3);

        if (room.gases().get(Gas.CARBON_DIOXIDE) != co2BeforeUnpowered
                || room.gases().get(Gas.OXYGEN) != o2BeforeUnpowered) {
            helper.fail("An unpowered reactor still changed the room's own gases - photosynthesis"
                    + " must not run for free");
            return;
        }
        if (reactor.getItem(
                play.xponer.astronima.block.entity.AlgaeBioreactorBlockEntity.SLOT_INPUT).isEmpty()) {
            helper.fail("The water bottle was consumed with no power ever supplied");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I feed the digester real crop waste with no power hooked up at all - does it actually
     * digest a real batch anyway, put real biogas at the real 60/40 methane/CO2 split into the
     * room, and give me real fertilizer back? And if I leave it empty, does it just sit there?"</em>
     *
     * <p>The row that matters most (design/anaerobic-digestion.md §5): unlike every other machine
     * this session has built, this one's own real claim is that it does <em>not</em> need power -
     * so the only positive case worth proving is the unpowered one, not a powered one first.
     */
    private static void anaerobicDigesterRunsWithNoPowerAtAllAndMakesRealBiogasAndFertilizer(
            GameTestHelper helper) {
        BlockPos machinePos = new BlockPos(2, 3, 2);
        sealPocketUnder(helper, machinePos);
        helper.setBlock(machinePos, ModBlocks.ANAEROBIC_DIGESTER.get().defaultBlockState());

        var digester = helper.getBlockEntity(machinePos,
                play.xponer.astronima.block.entity.AnaerobicDigesterBlockEntity.class);
        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(machinePos.below()));
        RoomState room = atmosphere.roomAt(helper.absolutePos(machinePos.below()));
        if (digester == null || room == null) {
            helper.fail("Setup failed: digester or receiving room missing");
            return;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }

        double methaneBefore = room.gases().get(Gas.METHANE);
        double co2Before = room.gases().get(Gas.CARBON_DIOXIDE);
        digester.setItem(
                play.xponer.astronima.block.entity.AnaerobicDigesterBlockEntity.SLOT_INPUT,
                new ItemStack(ModItems.CROP_WASTE.get()));
        // No power cell anywhere near it - the whole point of this scenario.
        runMachine(digester,
                play.xponer.astronima.block.entity.AnaerobicDigesterBlockEntity.BATCH_WORK + 40);

        double methaneMade = room.gases().get(Gas.METHANE) - methaneBefore;
        double co2Made = room.gases().get(Gas.CARBON_DIOXIDE) - co2Before;
        if (methaneMade <= 0 || co2Made <= 0) {
            helper.fail("An unpowered, fed digester made " + methaneMade + " mol methane and "
                    + co2Made + " mol CO2 - both should be positive with no power at all");
            return;
        }
        double totalBiogas = methaneMade + co2Made;
        if (Math.abs(methaneMade / totalBiogas - play.xponer.astronima.sim.chem.AnaerobicDigestion
                .METHANE_FRACTION) > 1e-6) {
            helper.fail("Real biogas should be " + play.xponer.astronima.sim.chem.AnaerobicDigestion
                    .METHANE_FRACTION + " methane by mole fraction - got " + (methaneMade / totalBiogas));
            return;
        }
        ItemStack fertilizerOut = digester.getItem(
                play.xponer.astronima.block.entity.AnaerobicDigesterBlockEntity.SLOT_OUTPUT);
        if (!fertilizerOut.is(ModItems.FERTILIZER.get()) || fertilizerOut.getCount() <= 0) {
            helper.fail("A fed, unpowered batch produced " + fertilizerOut
                    + " instead of real fertilizer");
            return;
        }
        if (!digester.getItem(
                play.xponer.astronima.block.entity.AnaerobicDigesterBlockEntity.SLOT_INPUT).isEmpty()) {
            helper.fail("The crop waste survived a finished batch");
            return;
        }

        // Second: empty, it makes no progress at all.
        BlockPos emptyPos = new BlockPos(6, 3, 6);
        sealPocketUnder(helper, emptyPos);
        helper.setBlock(emptyPos, ModBlocks.ANAEROBIC_DIGESTER.get().defaultBlockState());
        var emptyDigester = helper.getBlockEntity(emptyPos,
                play.xponer.astronima.block.entity.AnaerobicDigesterBlockEntity.class);
        if (emptyDigester == null) {
            helper.fail("The second digester has no block entity");
            return;
        }
        runMachine(emptyDigester,
                (play.xponer.astronima.block.entity.AnaerobicDigesterBlockEntity.BATCH_WORK + 40) * 3);
        if (!emptyDigester.getItem(
                play.xponer.astronima.block.entity.AnaerobicDigesterBlockEntity.SLOT_OUTPUT).isEmpty()) {
            helper.fail("An empty digester produced fertilizer from nothing");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I plant a seedling on a lit hull plate with real CO2 and water vapour in the air
     * around it - does it actually grow through all four real stages, spending real CO2 and
     * releasing real O2 1:1 the same way A1's own photosynthesis does? If I roof it over, does
     * it just hold rather than die? And if I harvest it at the top, do I get real lettuce back
     * and a plant that keeps growing instead of one that is gone?"</em>
     *
     * <p>Through the real door, {@link HydroponicCropBlock#randomTick} via its own
     * {@link HydroponicCropBlock#simulateTick} debug hook (the same one {@code MoldBlock} already
     * opens for its own random-tick-paced block, design/hydroponics.md §4.5) - not the sim class
     * directly, since the row that matters most here is whether the real block reads a real
     * {@code RoomState} and a real {@link SkyExposure} value correctly, not just whether
     * {@link HydroponicGrowth#favorable} is correct in isolation (that claim already has its own
     * unit tests). The crop cannot itself seal a room the way a solid machine can (rule 2: checked
     * this session - {@code noCollision()} classifies it {@code BlockKind.OPEN}, the same as air,
     * in {@code AirBlockKinds}), so unlike the solar retort's own {@code sealPocketUnder} scenarios
     * this one does not attempt to keep the room sealed at all: it plants the crop in the open,
     * under real sky, and reads whatever room {@code roomTouching} actually finds there - open air
     * still resolves to a real, gas-settable {@code RoomState} (only {@code Atmosphere.tick()}'s
     * own periodic vent/reset touches it, never a direct {@code addGasAt} call), so the CO2 and
     * water vapour breathing would really put there are set directly, the same shortcut A1's own
     * scenario already takes for its water bottle's CO2.
     */
    private static void hydroponicCropGrowsFromRealSunlightCo2AndWaterVapour(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Atmosphere atmosphere = Atmosphere.get(level);

        // Phase 1: real sky, real CO2, real water vapour - all three favourable.
        BlockPos floorPos = new BlockPos(2, 2, 2);
        BlockPos cropPos = floorPos.above();
        BlockPos absCrop = helper.absolutePos(cropPos);
        helper.setBlock(floorPos, ModBlocks.HULL_PLATE.get().defaultBlockState());
        helper.setBlock(cropPos, ModBlocks.HYDROPONIC_CROP.get().defaultBlockState());

        if (SkyExposure.sunlightAt(level, absCrop) <= 0) {
            helper.fail("No sunlight on a crop under open sky - the crop can never grow and"
                    + " every other assertion here is vacuous");
            return;
        }
        atmosphere.invalidate(absCrop);
        RoomState room = atmosphere.roomTouching(absCrop);
        if (room == null) {
            helper.fail("Setup failed: no room touches the crop, so nothing here can be tested");
            return;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        room.addGasAt(Gas.CARBON_DIOXIDE, HydroponicGrowth.CO2_PER_GROWTH_MOL * 50, 293.15);
        room.addGasAt(Gas.WATER_VAPOR, 10.0, 293.15);

        // Growing the crop repeatedly calls setBlockAndUpdate on its own position, which
        // dirties and rescans the room (gas carries forward through the rescan, see
        // Atmosphere#inheritGas - but the OLD RoomState object does not, so it must be
        // re-fetched fresh, not read off a reference held from before growth started).
        double co2Before = atmosphere.roomTouching(absCrop).gases().get(Gas.CARBON_DIOXIDE);
        double o2Before = atmosphere.roomTouching(absCrop).gases().get(Gas.OXYGEN);
        RandomSource random = level.getRandom();
        for (int i = 0; i < 400
                && level.getBlockState(absCrop).getValue(BlockStateProperties.AGE_3) < HydroponicGrowth.MAX_AGE;
                i++) {
            HydroponicCropBlock.simulateTick(level, absCrop, random);
        }
        int matureAge = level.getBlockState(absCrop).getValue(BlockStateProperties.AGE_3);
        if (matureAge != HydroponicGrowth.MAX_AGE) {
            helper.fail("400 favourable random ticks did not mature the crop - stuck at age "
                    + matureAge);
            return;
        }
        RoomState roomAfterGrowth = atmosphere.roomTouching(absCrop);
        if (roomAfterGrowth == null) {
            helper.fail("The room touching the crop vanished after growth");
            return;
        }
        double co2Spent = co2Before - roomAfterGrowth.gases().get(Gas.CARBON_DIOXIDE);
        double o2Made = roomAfterGrowth.gases().get(Gas.OXYGEN) - o2Before;
        double expectedSpent = HydroponicGrowth.MAX_AGE * HydroponicGrowth.CO2_PER_GROWTH_MOL;
        if (Math.abs(co2Spent - expectedSpent) > 1e-9) {
            helper.fail("Growing through all " + HydroponicGrowth.MAX_AGE + " real stages should"
                    + " spend " + expectedSpent + " mol CO2 - spent " + co2Spent);
            return;
        }
        if (Math.abs(co2Spent - o2Made) > 1e-9) {
            helper.fail("CO2 spent and O2 made were not equal (" + co2Spent + " vs " + o2Made
                    + ") - real photosynthesis is 1:1");
            return;
        }

        // Phase 2: roofed over - light missing holds the stage, no loss, exactly like the
        // solar retort's own "roofed over" negative test.
        BlockPos floorPos2 = new BlockPos(6, 2, 6);
        BlockPos cropPos2 = floorPos2.above();
        BlockPos absCrop2 = helper.absolutePos(cropPos2);
        helper.setBlock(floorPos2, ModBlocks.HULL_PLATE.get().defaultBlockState());
        helper.setBlock(cropPos2, ModBlocks.HYDROPONIC_CROP.get().defaultBlockState());
        helper.setBlock(cropPos2.above(), ModBlocks.HULL_PLATE.get().defaultBlockState());
        if (SkyExposure.sunlightAt(level, absCrop2) > 0) {
            helper.fail("A crop roofed directly over should have no sunlight to read");
            return;
        }
        atmosphere.invalidate(absCrop2);
        RoomState room2 = atmosphere.roomTouching(absCrop2);
        if (room2 == null) {
            helper.fail("Setup failed: no room touches the roofed crop");
            return;
        }
        for (Gas gas : Gas.values()) {
            room2.removeGas(gas, room2.gases().get(gas));
        }
        room2.addGasAt(Gas.CARBON_DIOXIDE, HydroponicGrowth.CO2_PER_GROWTH_MOL * 50, 293.15);
        room2.addGasAt(Gas.WATER_VAPOR, 10.0, 293.15);
        double co2BeforeRoofed = atmosphere.roomTouching(absCrop2).gases().get(Gas.CARBON_DIOXIDE);
        for (int i = 0; i < 200; i++) {
            HydroponicCropBlock.simulateTick(level, absCrop2, random);
        }
        int ageUnderRoof = level.getBlockState(absCrop2).getValue(BlockStateProperties.AGE_3);
        if (ageUnderRoof != 0) {
            helper.fail("A crop with no sunlight grew anyway - age " + ageUnderRoof);
            return;
        }
        double co2AfterRoofed = atmosphere.roomTouching(absCrop2).gases().get(Gas.CARBON_DIOXIDE);
        if (co2AfterRoofed != co2BeforeRoofed) {
            helper.fail("A held crop still spent real CO2 - a missing condition must hold,"
                    + " not partially run");
            return;
        }

        // Phase 3: harvest at maturity - real pick-and-eat, not destroy-and-replant.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        level.getBlockState(absCrop).useWithoutItem(level, player,
                new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(absCrop), Direction.UP, absCrop, false));
        int ageAfterHarvest = level.getBlockState(absCrop).getValue(BlockStateProperties.AGE_3);
        if (ageAfterHarvest != HydroponicGrowth.HARVESTED_AGE) {
            helper.fail("Harvesting a mature plant should return it to age "
                    + HydroponicGrowth.HARVESTED_AGE + ", not " + ageAfterHarvest);
            return;
        }
        helper.assertItemEntityCountIs(ModItems.LETTUCE.get(), cropPos, 2.0, 1);
        // Real crop waste, Part E's own real feedstock (design/anaerobic-digestion.md §0) - the
        // same real harvest, a second real fact about it.
        helper.assertItemEntityCountIs(ModItems.CROP_WASTE.get(), cropPos, 2.0, 1);
        helper.succeed();
    }

    /**
     * <em>"I set up both machines in one sealed room - loaded nickel, filled the room with CO2
     * and hydrogen the way exhaling and an electrolyzer would - does the Sabatier reactor
     * actually draw both down and make methane and water, or does it run on just one?"</em>
     *
     * <p>The whole point of {@code design/chemistry-loop.md} in one gametest: this is the first
     * machine in the mod that needs two room reagents at once, and the row that matters most is
     * that dropping either one below the batch requirement holds the reactor rather than letting
     * it run on half a reaction.
     */
    private static void sabatierReactorClosesTheLoop(GameTestHelper helper) {
        BlockPos inside = new BlockPos(3, 2, 3);
        BlockPos reactorPos = inside.offset(1, 0, 0);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int ring = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                    helper.setBlock(inside.offset(dx, dy, dz), ring <= 1
                            ? Blocks.AIR.defaultBlockState()
                            : ModBlocks.HULL_PLATE.get().defaultBlockState());
                }
            }
        }
        helper.setBlock(reactorPos, ModBlocks.SABATIER_REACTOR.get().defaultBlockState());

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(inside));
        Atmosphere.RoomReading reading = atmosphere.readingAt(helper.absolutePos(inside));
        if (reading == null || !reading.sealed()) {
            helper.fail("Setup failed: the reactor's room is not sealed");
            return;
        }
        var reactor = helper.getBlockEntity(reactorPos, SabatierReactorBlockEntity.class);
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        if (reactor == null || room == null) {
            helper.fail("Setup failed: reactor or room missing");
            return;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        // Enough CO2, but hydrogen short of what one batch needs: the reactor must hold, not
        // react on half of what the equation demands.
        room.addGasAt(Gas.CARBON_DIOXIDE, SabatierReactorBlockEntity.CO2_PER_BATCH_MOL * 3, 293.15);
        room.addGasAt(Gas.HYDROGEN, SabatierReactorBlockEntity.H2_PER_BATCH_MOL * 0.5, 293.15);
        // The panel's own reagent-stock reading (design/machines.md's own Update section) has to
        // agree with the room state that just staged this exact "hold" case: CO2 clamped to a
        // full 100% (there is three times what one batch needs), H2 reading the real 50% short.
        if (!(reactor.co2Fraction() > 0.999) || Math.abs(reactor.h2Fraction() - 0.5) > 1e-9) {
            helper.fail("The reagent-stock reading disagreed with the real room gas it is supposed"
                    + " to reflect: co2Fraction=" + reactor.co2Fraction() + " (expected ~1.0),"
                    + " h2Fraction=" + reactor.h2Fraction() + " (expected 0.5)");
            return;
        }
        reactor.setItem(SabatierReactorBlockEntity.SLOT_CATALYST,
                new ItemStack(ModItems.PURE_NICKEL.get()));
        runMachine(reactor, SabatierReactorBlockEntity.BATCH_WORK + 40);

        if (room.gases().get(Gas.METHANE) > 1e-9) {
            helper.fail("The reactor made methane with hydrogen short of what the batch needs -"
                    + " it should hold on the missing reagent, not react on half an equation");
            return;
        }

        // Top up the hydrogen: now both reagents clear the batch requirement, and the reactor
        // must actually run.
        room.addGasAt(Gas.HYDROGEN, SabatierReactorBlockEntity.H2_PER_BATCH_MOL, 293.15);
        double co2Before = room.gases().get(Gas.CARBON_DIOXIDE);
        double h2Before = room.gases().get(Gas.HYDROGEN);
        runMachine(reactor, SabatierReactorBlockEntity.BATCH_WORK + 40);

        if (!(room.gases().get(Gas.CARBON_DIOXIDE) < co2Before)
                || !(room.gases().get(Gas.HYDROGEN) < h2Before)) {
            helper.fail("The reactor did not draw both CO2 and H2 down once both were plentiful ("
                    + co2Before + " -> " + room.gases().get(Gas.CARBON_DIOXIDE) + " CO2, "
                    + h2Before + " -> " + room.gases().get(Gas.HYDROGEN) + " H2)");
            return;
        }
        if (!(room.gases().get(Gas.METHANE) > 1e-9) || !(room.gases().get(Gas.WATER_VAPOR) > 1e-9)) {
            helper.fail("A finished batch with both reagents plentiful made no methane or no"
                    + " water vapor");
            return;
        }
        ItemStack nickelAfter = reactor.getItem(SabatierReactorBlockEntity.SLOT_CATALYST);
        if (nickelAfter.getCount() >= 1) {
            helper.fail("The nickel catalyst survived a finished batch untouched (" + nickelAfter
                    + ") - the same token-consumption simplification the polymerizer's titania"
                    + " already carries");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I set up the Bosch reactor with the exact same room CO2 as the Sabatier reactor's own
     * test, but only half the hydrogen Sabatier needed - does it still run, or does it need the
     * same 8 mol H2 the other one does?"</em>
     *
     * <p>The row that actually distinguishes this machine from Sabatier: real Bosch stoichiometry
     * needs half the hydrogen per CO2, and the carbon comes out as a real item instead of a gas
     * (design/chemistry-loop.md §2.5).
     */
    private static void boschReactorKeepsTheCarbonSolid(GameTestHelper helper) {
        BlockPos inside = new BlockPos(3, 2, 3);
        BlockPos reactorPos = inside.offset(1, 0, 0);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int ring = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                    helper.setBlock(inside.offset(dx, dy, dz), ring <= 1
                            ? Blocks.AIR.defaultBlockState()
                            : ModBlocks.HULL_PLATE.get().defaultBlockState());
                }
            }
        }
        helper.setBlock(reactorPos, ModBlocks.BOSCH_REACTOR.get().defaultBlockState());

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(inside));
        Atmosphere.RoomReading reading = atmosphere.readingAt(helper.absolutePos(inside));
        if (reading == null || !reading.sealed()) {
            helper.fail("Setup failed: the reactor's room is not sealed");
            return;
        }
        var reactor = helper.getBlockEntity(reactorPos, play.xponer.astronima.block.entity
                .BoschReactorBlockEntity.class);
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        if (reactor == null || room == null) {
            helper.fail("Setup failed: reactor or room missing");
            return;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        // Exactly the batch requirement, at real Bosch stoichiometry - half of what the
        // Sabatier test needed for the same CO2.
        room.addGasAt(Gas.CARBON_DIOXIDE,
                play.xponer.astronima.block.entity.BoschReactorBlockEntity.CO2_PER_BATCH_MOL, 293.15);
        room.addGasAt(Gas.HYDROGEN,
                play.xponer.astronima.block.entity.BoschReactorBlockEntity.H2_PER_BATCH_MOL, 293.15);
        reactor.setItem(play.xponer.astronima.block.entity.BoschReactorBlockEntity.SLOT_CATALYST,
                new ItemStack(ModItems.IRON_POWDER.get()));

        double co2Before = room.gases().get(Gas.CARBON_DIOXIDE);
        double h2Before = room.gases().get(Gas.HYDROGEN);
        // The panel's own reagent-stock reading has to agree: exactly the batch requirement of
        // each gas is present, so both fractions read a full 100% (design/machines.md's own
        // Update section).
        if (reactor.co2Fraction() < 0.999 || reactor.h2Fraction() < 0.999) {
            helper.fail("The reagent-stock reading disagreed with the real room gas it is supposed"
                    + " to reflect: co2Fraction=" + reactor.co2Fraction() + ", h2Fraction="
                    + reactor.h2Fraction() + " (both expected ~1.0)");
            return;
        }
        runMachine(reactor, play.xponer.astronima.block.entity.BoschReactorBlockEntity.BATCH_WORK + 40);

        if (!(room.gases().get(Gas.CARBON_DIOXIDE) < co2Before)
                || !(room.gases().get(Gas.HYDROGEN) < h2Before)) {
            helper.fail("The reactor did not draw CO2 and H2 down at the batch's own"
                    + " requirement (" + co2Before + " -> " + room.gases().get(Gas.CARBON_DIOXIDE)
                    + " CO2, " + h2Before + " -> " + room.gases().get(Gas.HYDROGEN) + " H2)");
            return;
        }
        if (!(room.gases().get(Gas.WATER_VAPOR) > 1e-9)) {
            helper.fail("A finished batch made no water vapor");
            return;
        }
        ItemStack carbonOut = reactor.getItem(
                play.xponer.astronima.block.entity.BoschReactorBlockEntity.SLOT_CARBON);
        if (!carbonOut.is(ModItems.CARBON_POWDER.get()) || carbonOut.getCount() <= 0) {
            helper.fail("A finished batch with iron loaded and both reagents plentiful produced "
                    + carbonOut + " instead of solid carbon");
            return;
        }
        ItemStack ironAfter = reactor.getItem(
                play.xponer.astronima.block.entity.BoschReactorBlockEntity.SLOT_CATALYST);
        if (ironAfter.getCount() >= 1) {
            helper.fail("The iron catalyst survived a finished batch untouched (" + ironAfter
                    + ") - the same token-consumption simplification the Sabatier reactor's"
                    + " nickel already carries");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I feed the troilite roaster a fresh batch of ordinary chondrite dust, but the room
     * around it barely has any oxygen - does it try to roast anyway, or does it wait like every
     * other reagent-gated machine in this loop? And once I top the room back up, does it actually
     * spend that oxygen and hand back real hematite and a whiff of SO2, not just consume the ore
     * for nothing?"</em>
     *
     * <p>The row that distinguishes this machine from every other one in the loop: it reads its
     * own feed item's {@code OreGrade} to work out how much troilite - and therefore how much
     * oxygen - one batch actually needs, rather than a single fixed constant
     * (design/chemistry-loop.md's sulfur-chain section).
     */
    private static void troiliteRoasterSpendsRealOxygen(GameTestHelper helper) {
        BlockPos inside = new BlockPos(3, 2, 3);
        BlockPos roasterPos = inside.offset(1, 0, 0);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int ring = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                    helper.setBlock(inside.offset(dx, dy, dz), ring <= 1
                            ? Blocks.AIR.defaultBlockState()
                            : ModBlocks.HULL_PLATE.get().defaultBlockState());
                }
            }
        }
        helper.setBlock(roasterPos, ModBlocks.TROILITE_ROASTER.get().defaultBlockState());

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(inside));
        Atmosphere.RoomReading reading = atmosphere.readingAt(helper.absolutePos(inside));
        if (reading == null || !reading.sealed()) {
            helper.fail("Setup failed: the roaster's room is not sealed");
            return;
        }
        var roaster = helper.getBlockEntity(roasterPos,
                play.xponer.astronima.block.entity.TroiliteRoasterBlockEntity.class);
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        if (roaster == null || room == null) {
            helper.fail("Setup failed: roaster or room missing");
            return;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }

        ItemStack ore = new ItemStack(ModItems.CRUSHED_ORE.get());
        ore.set(play.xponer.astronima.registry.ModDataComponents.ORE_BATCH.get(),
                play.xponer.astronima.sim.ore.OreGrade.pack(
                        play.xponer.astronima.sim.ore.OreGrade.CHONDRITE, 1.0));
        roaster.setItem(play.xponer.astronima.block.entity.TroiliteRoasterBlockEntity.SLOT_INPUT,
                ore.copy());

        play.xponer.astronima.sim.ore.TroiliteRoasting.Step step =
                play.xponer.astronima.sim.ore.TroiliteRoasting.roast(
                        play.xponer.astronima.sim.ore.OreGrade.CHONDRITE.body(
                                play.xponer.astronima.block.entity.TroiliteRoasterBlockEntity
                                        .CHARGE_GRAMS));
        if (step.o2ConsumedMol() <= 0) {
            helper.fail("Setup failed: ordinary chondrite dust carries no troilite in this test's"
                    + " own sim - the scenario would prove nothing about real oxygen consumption");
            return;
        }

        // Starved: half of what the batch actually needs.
        room.addGasAt(Gas.OXYGEN, step.o2ConsumedMol() * 0.5, 293.15);
        // The panel's own reagent-stock reading has to track this specific ore's own real O2
        // need, not a fixed constant like every sibling machine's reading - exactly 50% here,
        // computed from this ore's own grade (design/machines.md's own Update section).
        if (Math.abs(roaster.oxygenFraction() - 0.5) > 1e-9) {
            helper.fail("The reagent-stock reading disagreed with the real, ore-dependent oxygen"
                    + " need it is supposed to reflect: oxygenFraction=" + roaster.oxygenFraction()
                    + " (expected 0.5)");
            return;
        }
        runMachine(roaster,
                play.xponer.astronima.block.entity.TroiliteRoasterBlockEntity.BATCH_WORK + 40);

        if (room.gases().get(Gas.SULFUR_DIOXIDE) > 1e-9) {
            helper.fail("The roaster produced SO2 with the room's oxygen starved at half the"
                    + " batch's own requirement - it should hold on the missing reagent, not"
                    + " roast on a shortfall");
            return;
        }

        // Top up past the batch's own requirement: now it must actually run.
        room.addGasAt(Gas.OXYGEN, step.o2ConsumedMol(), 293.15);
        double o2Before = room.gases().get(Gas.OXYGEN);
        runMachine(roaster,
                play.xponer.astronima.block.entity.TroiliteRoasterBlockEntity.BATCH_WORK + 40);

        if (!(room.gases().get(Gas.OXYGEN) < o2Before)) {
            helper.fail("The roaster did not draw the room's oxygen down once it was plentiful ("
                    + o2Before + " -> " + room.gases().get(Gas.OXYGEN) + ")");
            return;
        }
        if (!(room.gases().get(Gas.SULFUR_DIOXIDE) > 1e-9)) {
            helper.fail("A finished batch with oxygen plentiful vented no SO2 into the room");
            return;
        }
        ItemStack hematiteOut = roaster.getItem(
                play.xponer.astronima.block.entity.TroiliteRoasterBlockEntity.SLOT_OUTPUT);
        if (!hematiteOut.is(ModItems.HEMATITE_ORE.get()) || hematiteOut.getCount() <= 0) {
            helper.fail("A finished batch with oxygen plentiful produced " + hematiteOut
                    + " instead of real hematite ore");
            return;
        }
        ItemStack oreAfter = roaster.getItem(
                play.xponer.astronima.block.entity.TroiliteRoasterBlockEntity.SLOT_INPUT);
        if (oreAfter.getCount() >= 1) {
            helper.fail("The crushed-ore feed survived a finished batch untouched (" + oreAfter
                    + ")");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I anneal a charge of carbon powder in a Graphitizer, in a room I forgot to purge -
     * does it actually just sit there and eventually make graphite anyway, or does the real
     * hazard bite: does hot carbon genuinely burn in the room's own air, spending real oxygen and
     * making real CO2, and come out with nothing to show for the batch? And in a room I actually
     * purged, does the same charge come out clean, with no gas spent at all?"</em>
     *
     * <p>The row design/carbon-fiber.md §6 calls out as the one that matters most: without it,
     * the Graphitizer's whole "oxygen at the high setpoint is a real reaction, not a friendly
     * stall" claim is unproven, and a player would never learn why the room has to be purged
     * before the batch finishes.
     */
    private static void graphitizerBurnsInsteadOfGraphitizingInAnUnpurgedRoom(GameTestHelper helper) {
        BlockPos inside = new BlockPos(3, 2, 3);
        BlockPos graphitizerPos = inside.offset(1, 0, 0);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int ring = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                    helper.setBlock(inside.offset(dx, dy, dz), ring <= 1
                            ? Blocks.AIR.defaultBlockState()
                            : ModBlocks.HULL_PLATE.get().defaultBlockState());
                }
            }
        }
        helper.setBlock(graphitizerPos, ModBlocks.GRAPHITIZER.get().defaultBlockState());

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(inside));
        Atmosphere.RoomReading reading = atmosphere.readingAt(helper.absolutePos(inside));
        if (reading == null || !reading.sealed()) {
            helper.fail("Setup failed: the Graphitizer's room is not sealed");
            return;
        }
        var graphitizer = helper.getBlockEntity(graphitizerPos,
                play.xponer.astronima.block.entity.GraphitizerBlockEntity.class);
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        if (graphitizer == null || room == null) {
            helper.fail("Setup failed: Graphitizer or room missing");
            return;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }

        // First: an unpurged room, real oxygen left breathable. The batch must not stall - it
        // must run and burn the charge to nothing.
        room.addGasAt(Gas.OXYGEN, 50.0, 293.15);
        graphitizer.setItem(
                play.xponer.astronima.block.entity.GraphitizerBlockEntity.SLOT_INPUT,
                new ItemStack(ModItems.CARBON_POWDER.get()));
        double o2Before = room.gases().get(Gas.OXYGEN);
        runMachine(graphitizer,
                play.xponer.astronima.block.entity.GraphitizerBlockEntity.BATCH_WORK + 40);

        if (!(room.gases().get(Gas.OXYGEN) < o2Before)) {
            helper.fail("The Graphitizer did not draw the room's oxygen down while running hot in"
                    + " a breathable room (" + o2Before + " -> " + room.gases().get(Gas.OXYGEN)
                    + ")");
            return;
        }
        if (!(room.gases().get(Gas.CARBON_DIOXIDE) > 1e-9)) {
            helper.fail("A batch run hot with oxygen plentiful made no CO2 - the combustion branch"
                    + " never ran");
            return;
        }
        ItemStack burnedOutput = graphitizer.getItem(
                play.xponer.astronima.block.entity.GraphitizerBlockEntity.SLOT_OUTPUT);
        if (!burnedOutput.isEmpty()) {
            helper.fail("A charge burned away in an unpurged room still produced " + burnedOutput
                    + " - it should have nothing to show for it");
            return;
        }

        // Second: the same real feed, this time in a room actually purged. The batch must come
        // out clean, spending no gas at all.
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        graphitizer.setItem(
                play.xponer.astronima.block.entity.GraphitizerBlockEntity.SLOT_INPUT,
                new ItemStack(ModItems.CARBON_POWDER.get()));
        runMachine(graphitizer,
                play.xponer.astronima.block.entity.GraphitizerBlockEntity.BATCH_WORK + 40);

        if (room.gases().get(Gas.CARBON_DIOXIDE) > 1e-9) {
            helper.fail("A batch run in a purged room made CO2 - it should have annealed cleanly,"
                    + " not burned");
            return;
        }
        ItemStack cleanOutput = graphitizer.getItem(
                play.xponer.astronima.block.entity.GraphitizerBlockEntity.SLOT_OUTPUT);
        if (!cleanOutput.is(ModItems.GRAPHITE_POWDER.get()) || cleanOutput.getCount() <= 0) {
            helper.fail("A batch run in a purged room produced " + cleanOutput
                    + " instead of real graphite powder");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I feed the Downs cell a piece of rock salt in a sealed room. Does it actually melt and
     * electrolyze it - real sodium in the output slot, real chlorine in the room's own air - or
     * does it just sit there consuming ore for nothing? And with no sealed room to vent the
     * chlorine into, does it hold rather than making the gas disappear?"</em>
     *
     * <p>No reagent-starvation branch, unlike the roaster next door: the Downs process needs only
     * power and a sealed room to vent into, not a second room gas to react against.
     */
    private static void downsCellSplitsRockSalt(GameTestHelper helper) {
        BlockPos inside = new BlockPos(3, 2, 3);
        BlockPos cellPos = inside.offset(1, 0, 0);

        // Before any walls exist, the cell stands in open air: no room to vent into, so it must
        // hold rather than make the chlorine disappear.
        helper.setBlock(cellPos, ModBlocks.DOWNS_CELL.get().defaultBlockState());
        var strandedCell = helper.getBlockEntity(cellPos,
                play.xponer.astronima.block.entity.DownsCellBlockEntity.class);
        if (strandedCell == null) {
            helper.fail("Setup failed: cell has no block entity");
            return;
        }
        strandedCell.setItem(play.xponer.astronima.block.entity.DownsCellBlockEntity.SLOT_INPUT,
                new ItemStack(ModBlocks.HALITE_ORE.get()));
        runMachine(strandedCell,
                play.xponer.astronima.block.entity.DownsCellBlockEntity.BATCH_WORK + 40);
        ItemStack strandedOutput = strandedCell.getItem(
                play.xponer.astronima.block.entity.DownsCellBlockEntity.SLOT_OUTPUT);
        if (!strandedOutput.isEmpty()) {
            helper.fail("A Downs cell with no sealed room to vent chlorine into still produced "
                    + strandedOutput + " - it should hold rather than make the gas disappear");
            return;
        }

        // Now seal the room around it and confirm the same cell actually runs.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int ring = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                    BlockPos at = inside.offset(dx, dy, dz);
                    if (at.equals(cellPos)) {
                        continue; // leave the cell itself in place
                    }
                    helper.setBlock(at, ring <= 1
                            ? Blocks.AIR.defaultBlockState()
                            : ModBlocks.HULL_PLATE.get().defaultBlockState());
                }
            }
        }

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(inside));
        Atmosphere.RoomReading reading = atmosphere.readingAt(helper.absolutePos(inside));
        if (reading == null || !reading.sealed()) {
            helper.fail("Setup failed: the cell's room is not sealed");
            return;
        }

        var cell = helper.getBlockEntity(cellPos,
                play.xponer.astronima.block.entity.DownsCellBlockEntity.class);
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        if (cell == null || room == null) {
            helper.fail("Setup failed: cell or room missing");
            return;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }

        cell.setItem(play.xponer.astronima.block.entity.DownsCellBlockEntity.SLOT_INPUT,
                new ItemStack(ModBlocks.HALITE_ORE.get()));
        runMachine(cell, play.xponer.astronima.block.entity.DownsCellBlockEntity.BATCH_WORK + 40);

        ItemStack sodiumOut = cell.getItem(
                play.xponer.astronima.block.entity.DownsCellBlockEntity.SLOT_OUTPUT);
        if (!sodiumOut.is(ModItems.SODIUM.get()) || sodiumOut.getCount() <= 0) {
            helper.fail("A finished batch with a sealed room produced " + sodiumOut
                    + " instead of real sodium");
            return;
        }
        if (!(room.gases().get(Gas.CHLORINE) > 1e-9)) {
            helper.fail("A finished batch with a sealed room vented no chlorine into it");
            return;
        }
        ItemStack oreAfterCell = cell.getItem(
                play.xponer.astronima.block.entity.DownsCellBlockEntity.SLOT_INPUT);
        if (oreAfterCell.getCount() >= 1) {
            helper.fail("The rock-salt feed survived a finished batch untouched (" + oreAfterCell
                    + ")");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I feed a zone refiner ten items of electrolytic silicon. Does it actually purify the
     * whole batch at once - real wafer-grade silicon out, the real yield fraction and nothing
     * more - or does it just sit there consuming silicon for nothing?"</em>
     *
     * <p>No room, no reagent, unlike the Downs cell next door: real segregation needs only the
     * feed itself (design/halogens.md §9-10).
     */
    private static void zoneRefinerPurifiesSilicon(GameTestHelper helper) {
        BlockPos refinerPos = new BlockPos(2, 3, 2);
        helper.setBlock(refinerPos, ModBlocks.ZONE_REFINER.get().defaultBlockState());
        var refiner = helper.getBlockEntity(refinerPos,
                play.xponer.astronima.block.entity.ZoneRefinerBlockEntity.class);
        if (refiner == null) {
            helper.fail("Setup failed: refiner block entity missing");
            return;
        }

        refiner.setItem(play.xponer.astronima.block.entity.ZoneRefinerBlockEntity.SLOT_FEED,
                new ItemStack(ModItems.SILICON.get(),
                        play.xponer.astronima.block.entity.ZoneRefinerBlockEntity.FEED_PER_BATCH));
        runMachine(refiner,
                play.xponer.astronima.block.entity.ZoneRefinerBlockEntity.BATCH_WORK + 40);

        ItemStack wafers = refiner.getItem(
                play.xponer.astronima.block.entity.ZoneRefinerBlockEntity.SLOT_OUTPUT);
        int expectedYield = play.xponer.astronima.sim.ore.ZoneRefining.wafersPerBatch(
                play.xponer.astronima.block.entity.ZoneRefinerBlockEntity.FEED_PER_BATCH);
        if (!wafers.is(ModItems.WAFER_SILICON.get()) || wafers.getCount() != expectedYield) {
            helper.fail("A finished batch produced " + wafers + " instead of " + expectedYield
                    + " real wafer-grade silicon");
            return;
        }
        ItemStack feedAfter = refiner.getItem(
                play.xponer.astronima.block.entity.ZoneRefinerBlockEntity.SLOT_FEED);
        if (feedAfter.getCount() >= 1) {
            helper.fail("The silicon feed survived a finished batch untouched (" + feedAfter + ")");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I load an HF digester with fluorite ore and three sulfuric acid. Does it actually react
     * the real 1:1 stoichiometry - real hydrofluoric acid and real gypsum out of both output slots
     * - or does it just sit there consuming reagents for nothing? And if I only give it one of the
     * two, does it correctly refuse to run at all, since neither reagent alone can react?"</em>
     *
     * <p>No room, no reagent read off the air, unlike the acid plant next door: this reaction
     * needs only its own two feed items (design/halogens.md §15-17).
     */
    private static void hfDigesterReactsFluoriteAndAcid(GameTestHelper helper) {
        BlockPos digesterPos = new BlockPos(2, 3, 2);
        helper.setBlock(digesterPos, ModBlocks.HF_DIGESTER.get().defaultBlockState());
        var digester = helper.getBlockEntity(digesterPos,
                play.xponer.astronima.block.entity.HfDigesterBlockEntity.class);
        if (digester == null) {
            helper.fail("Setup failed: digester block entity missing");
            return;
        }

        // Fluorite alone: the real reaction needs sulfuric acid too, so it must not run.
        digester.setItem(play.xponer.astronima.block.entity.HfDigesterBlockEntity.SLOT_FLUORITE,
                new ItemStack(ModBlocks.FLUORITE_ORE.get()));
        runMachine(digester,
                play.xponer.astronima.block.entity.HfDigesterBlockEntity.BATCH_WORK + 40);
        if (!digester.getItem(play.xponer.astronima.block.entity.HfDigesterBlockEntity.SLOT_HF)
                .isEmpty()) {
            helper.fail("A digester with fluorite but no sulfuric acid still produced HF - real"
                    + " 1:1 stoichiometry means neither reagent alone should react");
            return;
        }

        // Now give it the real, full charge of both reagents.
        digester.setItem(play.xponer.astronima.block.entity.HfDigesterBlockEntity.SLOT_ACID,
                new ItemStack(ModItems.SULFURIC_ACID.get(),
                        play.xponer.astronima.block.entity.HfDigesterBlockEntity
                                .SULFURIC_ACID_PER_CHARGE));
        runMachine(digester,
                play.xponer.astronima.block.entity.HfDigesterBlockEntity.BATCH_WORK + 40);

        ItemStack hf = digester.getItem(
                play.xponer.astronima.block.entity.HfDigesterBlockEntity.SLOT_HF);
        ItemStack gypsum = digester.getItem(
                play.xponer.astronima.block.entity.HfDigesterBlockEntity.SLOT_GYPSUM);
        int expectedHf = (int) Math.floor(play.xponer.astronima.sim.chem.FluoriteDigestion
                .hfMolFrom(play.xponer.astronima.block.entity.HfDigesterBlockEntity
                        .FLUORITE_MOL_PER_CHARGE));
        int expectedGypsum = (int) Math.floor(play.xponer.astronima.sim.chem.FluoriteDigestion
                .gypsumMolFrom(play.xponer.astronima.block.entity.HfDigesterBlockEntity
                        .FLUORITE_MOL_PER_CHARGE));
        if (!hf.is(ModItems.HYDROFLUORIC_ACID.get()) || hf.getCount() != expectedHf) {
            helper.fail("A finished batch produced " + hf + " instead of " + expectedHf
                    + " real hydrofluoric acid");
            return;
        }
        if (!gypsum.is(ModItems.GYPSUM.get()) || gypsum.getCount() != expectedGypsum) {
            helper.fail("A finished batch produced " + gypsum + " instead of " + expectedGypsum
                    + " real gypsum");
            return;
        }
        ItemStack fluoriteAfter = digester.getItem(
                play.xponer.astronima.block.entity.HfDigesterBlockEntity.SLOT_FLUORITE);
        if (fluoriteAfter.getCount() >= 1) {
            helper.fail("The fluorite feed survived a finished batch untouched (" + fluoriteAfter
                    + ")");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I hand-load an etch station with HF - does it actually hurt me, the real contact event
     * HydrofluoricAcidItem#use itself triggers, and does the bottle actually land in the slot
     * rather than just vanish? If the room is not clean enough does the station correctly refuse
     * to run even with both feeds loaded, and once it is clean enough, does it etch the real 1:6
     * ratio into a real die and real fluorosilicic acid?"</em>
     *
     * <p>Through the real door: {@code EtchStationBlock#useItemOn} for the hazard, the station's
     * own registered {@code ProcessingBlockEntity} ticker for the reaction (design/halogens.md
     * §42-44, Part C4). The cleanroom's own real ~20-real-minute climb is C3's own scenario's job
     * (§35), not re-proven here - this scenario sets the adjacent controller's cleanliness
     * directly to prove the gate itself, the seam named in the design doc's own §44.
     */
    private static void etchStationHandLoadsHfAndGatesOnACleanRoom(GameTestHelper helper) {
        BlockPos stationPos = new BlockPos(2, 3, 2);
        BlockPos controllerPos = stationPos.offset(1, 0, 0);
        helper.setBlock(stationPos, ModBlocks.ETCH_STATION.get().defaultBlockState());
        helper.setBlock(controllerPos, ModBlocks.CLEANROOM_CONTROLLER.get().defaultBlockState());

        var station = helper.getBlockEntity(stationPos,
                play.xponer.astronima.block.entity.EtchStationBlockEntity.class);
        var controller = helper.getBlockEntity(controllerPos,
                play.xponer.astronima.block.entity.CleanroomControllerBlockEntity.class);
        if (station == null || controller == null) {
            helper.fail("Setup failed: etch station or cleanroom controller missing");
            return;
        }

        // The wafer loads through the ordinary door - no hazard, no event.
        station.setItem(play.xponer.astronima.block.entity.EtchStationBlockEntity.SLOT_WAFER,
                new ItemStack(ModItems.WAFER_SILICON.get()));

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        double before = player.getData(play.xponer.astronima.registry.ModAttachments.CHEMICAL_BURN_DOSE);
        if (before != 0.0) {
            helper.fail("Setup failed: a fresh player already carries a chemical-burn dose");
            return;
        }

        // Hand-load six HF - the real door, through the block's own useItemOn.
        ItemStack heldHf = new ItemStack(ModItems.HYDROFLUORIC_ACID.get(),
                play.xponer.astronima.block.entity.EtchStationBlockEntity.HF_PER_CHARGE);
        BlockPos absoluteStation = helper.absolutePos(stationPos);
        helper.getLevel().getBlockState(absoluteStation).useItemOn(heldHf, helper.getLevel(),
                player, InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(absoluteStation), Direction.NORTH,
                        absoluteStation, false));

        if (!heldHf.isEmpty()) {
            helper.fail("Hand-loading HF did not empty the held stack - " + heldHf + " remains");
            return;
        }
        ItemStack loadedHf = station.getItem(
                play.xponer.astronima.block.entity.EtchStationBlockEntity.SLOT_HF);
        if (!loadedHf.is(ModItems.HYDROFLUORIC_ACID.get())
                || loadedHf.getCount() != play.xponer.astronima.block.entity
                        .EtchStationBlockEntity.HF_PER_CHARGE) {
            helper.fail("Hand-loading HF did not land it in the station's own slot - got "
                    + loadedHf);
            return;
        }
        double afterHandLoad = player.getData(
                play.xponer.astronima.registry.ModAttachments.CHEMICAL_BURN_DOSE);
        double expectedDose = play.xponer.astronima.item.HydrofluoricAcidItem.CONTACT_GRAMS_PER_ITEM;
        if (Math.abs(afterHandLoad - expectedDose) > 0.001) {
            helper.fail("Hand-loading HF at the etch station should register the same real "
                    + expectedDose + " g contact dose handling the raw bottle does - got "
                    + afterHandLoad);
            return;
        }

        // Room not clean enough yet: both feeds loaded, but the station must not run.
        controller.setCleanlinessForScenario(0.5);
        runMachine(station,
                play.xponer.astronima.block.entity.EtchStationBlockEntity.BATCH_WORK + 40);
        ItemStack dieBefore = station.getItem(
                play.xponer.astronima.block.entity.EtchStationBlockEntity.SLOT_DIE);
        if (!dieBefore.isEmpty()) {
            helper.fail("An etch station with both feeds loaded ran despite a room only 50% "
                    + "clean, real gate at 90%");
            return;
        }

        // Now certify the room and let it run.
        controller.setCleanlinessForScenario(1.0);
        runMachine(station,
                play.xponer.astronima.block.entity.EtchStationBlockEntity.BATCH_WORK + 40);

        ItemStack die = station.getItem(
                play.xponer.astronima.block.entity.EtchStationBlockEntity.SLOT_DIE);
        ItemStack acid = station.getItem(
                play.xponer.astronima.block.entity.EtchStationBlockEntity.SLOT_FLUOROSILICIC_ACID);
        int expectedDie = (int) Math.floor(play.xponer.astronima.sim.chem.WaferEtching.dieMolFrom(
                play.xponer.astronima.block.entity.EtchStationBlockEntity.WAFER_MOL_PER_CHARGE));
        int expectedAcid = (int) Math.floor(
                play.xponer.astronima.sim.chem.WaferEtching.fluorosilicicAcidMolFrom(
                play.xponer.astronima.block.entity.EtchStationBlockEntity.WAFER_MOL_PER_CHARGE));
        if (!die.is(ModItems.ETCHED_DIE.get()) || die.getCount() != expectedDie) {
            helper.fail("A finished batch in a certified-clean room produced " + die
                    + " instead of " + expectedDie + " real etched die");
            return;
        }
        if (!acid.is(ModItems.FLUOROSILICIC_ACID.get()) || acid.getCount() != expectedAcid) {
            helper.fail("A finished batch in a certified-clean room produced " + acid
                    + " instead of " + expectedAcid + " real fluorosilicic acid");
            return;
        }
        ItemStack waferAfter = station.getItem(
                play.xponer.astronima.block.entity.EtchStationBlockEntity.SLOT_WAFER);
        if (waferAfter.getCount() >= 1) {
            helper.fail("The wafer feed survived a finished batch untouched (" + waferAfter + ")");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I build a small sealed room, place a cleanroom controller with a fresh filter and real
     * power, and run it forward - does it actually push the room above standard atmosphere, spend
     * real filter capacity doing it, and start raising the room's own tracked cleanliness?"</em>
     *
     * <p>Through the real door: the controller's own registered {@code BlockEntityTicker}
     * (design/halogens.md §33), the same ticker the server calls every game tick in real play -
     * nothing here calls {@code serverTick} directly. The room is preseeded with ordinary
     * sea-level air ({@code Atmosphere.pressurizeWithEarthAir}) rather than left at vacuum,
     * matching the realistic case (nobody installs a cleanroom controller into a hard vacuum) and
     * keeping this scenario's own tick budget honest rather than merely generous.
     */
    private static void cleanroomControllerHoldsRealPositivePressureAndRaisesCleanliness(
            GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(2, 3, 2);
        BlockPos pocketPos = controllerPos.below();
        BlockPos cellPos = controllerPos.offset(1, 0, 0);
        sealPocketUnder(helper, controllerPos);
        helper.setBlock(controllerPos,
                ModBlocks.CLEANROOM_CONTROLLER.get().defaultBlockState());
        helper.setBlock(cellPos, ModBlocks.POWER_CELL.get().defaultBlockState());

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.pressurizeWithEarthAir(helper.absolutePos(pocketPos));

        var controller = helper.getBlockEntity(controllerPos,
                play.xponer.astronima.block.entity.CleanroomControllerBlockEntity.class);
        var cell = helper.getBlockEntity(cellPos, PowerCellBlockEntity.class);
        if (controller == null || cell == null) {
            helper.fail("Setup failed: controller or power cell missing");
            return;
        }
        cell.charge(PowerCellBlockEntity.CAPACITY_J);
        controller.tryLoadFilter(null);

        helper.runAfterDelay(300, () -> {
            RoomState room = atmosphere.roomAt(helper.absolutePos(pocketPos));
            if (room == null) {
                helper.fail("Setup failed: the sealed pocket vanished");
                return;
            }
            if (!play.xponer.astronima.sim.Cleanroom.isPositivelyPressurized(room.pressureKPa())) {
                helper.fail("A powered controller with a fresh filter did not reach real positive"
                        + " pressure in a small sealed room after 300 real ticks - got "
                        + room.pressureKPa() + " kPa");
                return;
            }
            if (controller.chargeFraction() >= 1.0f) {
                helper.fail("Reaching real positive pressure spent no filter capacity at all,"
                        + " which cannot be true - the gauge should read below full");
                return;
            }
            if (controller.cleanliness() <= 0.0) {
                helper.fail("The room reached real positive pressure but cleanliness never"
                        + " started rising");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * <em>"I hold a bottle of real HF and right-click it - does it actually hurt me the moment I
     * touch it, whatever I am wearing, and is the dose it registers a real number tied to the mass
     * I just handled rather than an invented one?"</em>
     *
     * <p>Through the real door, {@code HydrofluoricAcidItem#use} (design/halogens.md §22-26, Part
     * C2) - the same method a right-click calls. Damage actually landing on a real player cannot
     * be staged in this harness (PLAN.md rule 14: {@code hurtServer} needs a {@code ServerPlayer}
     * the harness cannot join to the world); what this scenario proves instead is everything that
     * *can* be staged: the real door consumes the one item that touched skin, the dose it
     * registers is the real mass a single item carries - not an invented number - and that real
     * mass already classifies CRITICAL, the whole point of "the mod's most feared liquid."
     */
    private static void hydrofluoricAcidBurnsSkinOnContactRegardlessOfArmor(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.HYDROFLUORIC_ACID.get(), 2));

        double before = player.getData(
                play.xponer.astronima.registry.ModAttachments.CHEMICAL_BURN_DOSE);
        if (before != 0.0) {
            helper.fail("Setup failed: a fresh player already carries a chemical-burn dose");
            return;
        }

        ModItems.HYDROFLUORIC_ACID.get().use(level, player, InteractionHand.MAIN_HAND);

        ItemStack afterUse = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (afterUse.getCount() != 1) {
            helper.fail("Handling the bottle did not consume the one item that touched skin - "
                    + afterUse + " remains");
            return;
        }

        double doseGrams = player.getData(
                play.xponer.astronima.registry.ModAttachments.CHEMICAL_BURN_DOSE);
        double expected = play.xponer.astronima.item.HydrofluoricAcidItem.CONTACT_GRAMS_PER_ITEM;
        if (Math.abs(doseGrams - expected) > 0.001) {
            helper.fail("A single real contact should register " + expected + " g of real HF"
                    + " absorbed - got " + doseGrams);
            return;
        }
        play.xponer.astronima.sim.physio.ChemicalBurn.Severity severity =
                play.xponer.astronima.sim.physio.ChemicalBurn.Severity.classify(doseGrams);
        if (severity != play.xponer.astronima.sim.physio.ChemicalBurn.Severity.CRITICAL) {
            helper.fail("One full item's own real mass should already classify CRITICAL - got "
                    + severity);
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I starve for a real while, eat real food, and starve again of just one macro while the
     * other two stay full - does the right real food raise the right real reserve, does the right
     * real symptom show up for the right deficiency, does immunity actually read the scarcest
     * reserve rather than an average, and does feeding back up stop the debuff from renewing?"</em>
     *
     * <p>Through the real door {@link Nutrition#consumed}/{@link Nutrition#tick} already are -
     * {@link NutritionEvents} is pure wiring onto those two (design/macronutrients.md §3e), so
     * this is the real decision, not a shortcut around it.
     */
    private static void starvingOneMacroDebuffsAndDropsImmunityWithoutTouchingTheOthers(
            GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        // A real, long stretch of real time - enough to hit hard zero on every reserve, at the
        // real default METABOLISM_SCALE the tick call itself applies.
        Nutrition.tick(player, 400.0 * 24.0 * 3600.0);
        var starved = Nutrition.of(player);
        if (starved.protein() > 0 || starved.carbohydrate() > 0 || starved.fat() > 0) {
            helper.fail("400 real days of fasting should hit hard zero on every reserve - got "
                    + starved);
            return;
        }

        // Real algae biomass is protein-heavy; real lettuce is low across the board - eating
        // each should raise the reserves in that real, distinct shape.
        Nutrition.consumed(player, new ItemStack(ModItems.ALGAE_BIOMASS.get()));
        var afterAlgae = Nutrition.of(player);
        if (!(afterAlgae.protein() > afterAlgae.carbohydrate()
                && afterAlgae.carbohydrate() > afterAlgae.fat() && afterAlgae.fat() > 0)) {
            helper.fail("Real algae biomass should raise protein most, then carbohydrate, then"
                    + " fat least, all three above zero - got " + afterAlgae);
            return;
        }
        Nutrition.consumed(player, new ItemStack(ModItems.LETTUCE.get()));
        var afterLettuce = Nutrition.of(player);
        if (!(afterLettuce.protein() > afterAlgae.protein())) {
            helper.fail("Real lettuce should still raise protein by some real amount, however"
                    + " small - it did not move at all");
            return;
        }

        // Starve carbohydrate alone this time, holding protein and fat full by hand - the row
        // that matters most (design/macronutrients.md §5 test 5): a severe fat deficiency must
        // never mask an equally-real carbohydrate one, and immunity must read the true minimum.
        var carbOnly = new play.xponer.astronima.sim.physio.Macronutrition.State(1.0, 0.0, 1.0);
        player.setData(play.xponer.astronima.registry.ModAttachments.MACRONUTRITION.get(),
                CarriedMacronutrition.of(carbOnly));
        // Both real symptoms fired during the earlier hard-zero phase above and have not had a
        // real tick to expire since - clear them so this phase's own checks are not reading a
        // stale instance from before this player was fed at all.
        player.removeEffect(net.minecraft.world.effect.MobEffects.WEAKNESS);
        player.removeEffect(net.minecraft.world.effect.MobEffects.MINING_FATIGUE);
        Nutrition.tick(player, 1.0);
        if (!player.hasEffect(net.minecraft.world.effect.MobEffects.MINING_FATIGUE)) {
            helper.fail("Starving of carbohydrate alone should apply real Mining Fatigue"
                    + " (hypoglycaemic fatigue's own real mechanical analogue), even with protein"
                    + " and fat both full");
            return;
        }
        if (player.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS)) {
            helper.fail("A full protein reserve should not itself earn real Weakness");
            return;
        }
        // Immunity.of has its own real floor (SPENT, 0.15) - even a totally spent body keeps
        // some immune function - so zero sufficiency reads as that floor, not absolute zero.
        // The claim under test is still real: it must sit at that floor, not somewhere higher
        // an average with the two full reserves would put it.
        double immunityWhileStarved = play.xponer.astronima.physio.Infections.immunityOf(player);
        double expectedFloor = play.xponer.astronima.sim.pathogen.Immunity.SPENT;
        if (Math.abs(immunityWhileStarved - expectedFloor) > 1e-9) {
            helper.fail("Immunity should read the scarcest real reserve (zero carbohydrate) and"
                    + " sit at Immunity's own real floor of " + expectedFloor + ", not an average"
                    + " with the two full reserves - got " + immunityWhileStarved);
            return;
        }

        // Feed carbohydrate back up past the real recovery threshold - the state itself must
        // show real recovery, the claim this design actually makes (design/macronutrients.md §3f).
        // Algae biomass's own real carbohydrate share (0.20) at its own real nutrition (3, so a
        // 0.15 feeding scale) is +0.03 carbohydrate per feeding - 20 feedings clears 0.4 with
        // real headroom rather than landing right on the boundary.
        for (int i = 0; i < 20; i++) {
            Nutrition.consumed(player, new ItemStack(ModItems.ALGAE_BIOMASS.get()));
        }
        double recoveredCarb = Nutrition.of(player).carbohydrate();
        if (!(recoveredCarb > 0.4)) {
            helper.fail("Twenty real feedings of a real carbohydrate-bearing food should have"
                    + " recovered carbohydrate past the real clear threshold - got " + recoveredCarb);
            return;
        }
        double immunityAfterRecovery = play.xponer.astronima.physio.Infections.immunityOf(player);
        if (!(immunityAfterRecovery > immunityWhileStarved)) {
            helper.fail("Immunity should recover once the scarcest reserve does - stayed at "
                    + immunityAfterRecovery);
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I right-click a cargo crate full of real items with a data cell - does it actually log
     * everything into the cell's own real manifest and empty the crate? And if I sneak-right-click
     * that loaded cell back onto the now-empty crate, does everything real come back out?"</em>
     *
     * <p>The two verbs {@code design/data-cells.md} §3 names, exercised through the real door a
     * player uses (rule 13) rather than calling {@code DataLedger} directly.
     */
    private static void dataCellLogsAndReturnsARealCrate(GameTestHelper helper) {
        BlockPos cratePos = new BlockPos(1, 1, 1);
        helper.setBlock(cratePos, ModBlocks.CARGO_CRATE.get().defaultBlockState());
        var crate = helper.getBlockEntity(cratePos,
                play.xponer.astronima.block.entity.CargoCrateBlockEntity.class);
        if (crate == null) {
            helper.fail("Setup failed: crate block entity missing");
            return;
        }
        crate.setItem(0, new ItemStack(ModItems.HULL_PLATE.get(), 10));
        crate.setItem(1, new ItemStack(ModItems.METAL_BILLET.get(), 5));
        crate.setItem(2, new ItemStack(ModItems.IRON_ROD.get(), 3));

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(
                net.minecraft.world.phys.Vec3.atBottomCenterOf(new BlockPos(0, 1, 1))));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DATA_CELL.get()));

        // Sneaking is not a modifier here, it is the only door in: a plain right-click on any
        // real container opens that container's own menu before this item's useOn ever runs
        // (vanilla's own convention). GameTestHelper.useBlock's own simplified dispatch does not
        // model that sneak-bypass at all (it always tries the block's own use first regardless of
        // sneak state) - a real harness limit, not a bug in the item - so this calls useOn
        // directly, the same real door a sneaking client's own interaction pipeline would open.
        player.setShiftKeyDown(true);
        BlockPos crateAbsolute = helper.absolutePos(cratePos);
        var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(crateAbsolute), Direction.NORTH,
                crateAbsolute, true);
        InteractionResult vacuumResult = ModItems.DATA_CELL.get().useOn(
                new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND,
                        hit));
        if (vacuumResult != InteractionResult.SUCCESS) {
            helper.fail("The cell refused to vacuum the crate while sneaking: " + vacuumResult);
            return;
        }

        ItemStack heldCell = player.getItemInHand(InteractionHand.MAIN_HAND);
        var ledger = play.xponer.astronima.item.DataCellItem.ledgerOf(heldCell);
        if (ledger.typesUsed() != 3) {
            helper.fail("Vacuuming a crate with three real item types logged " + ledger.typesUsed()
                    + " types instead of 3");
            return;
        }
        long plates = ledger.countOf("astronima:hull_plate");
        long billets = ledger.countOf("astronima:metal_billet");
        long rods = ledger.countOf("astronima:iron_rod");
        if (plates != 10 || billets != 5 || rods != 3) {
            helper.fail("The logged real counts were wrong: plates=" + plates + " (expected 10),"
                    + " billets=" + billets + " (expected 5), rods=" + rods + " (expected 3)");
            return;
        }
        for (int slot = 0; slot < crate.getContainerSize(); slot++) {
            if (!crate.getItem(slot).isEmpty()) {
                helper.fail("The crate still holds " + crate.getItem(slot) + " after a real"
                        + " vacuum - everything logged should have left the world's own container");
                return;
            }
        }

        // The reverse: use the loaded cell in open air (no container to argue with) - every
        // logged item must come back out into the real world, not vanish.
        InteractionResult used = ModItems.DATA_CELL.get()
                .use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        if (used != InteractionResult.SUCCESS) {
            helper.fail("The loaded cell refused to dispense in open air: " + used);
            return;
        }

        ItemStack cellAfter = player.getItemInHand(InteractionHand.MAIN_HAND);
        var ledgerAfter = play.xponer.astronima.item.DataCellItem.ledgerOf(cellAfter);
        if (!ledgerAfter.entries().isEmpty()) {
            helper.fail("The cell still holds " + ledgerAfter.entries() + " after dispensing -"
                    + " it should have emptied completely");
            return;
        }
        long platesOnGround = 0;
        long billetsOnGround = 0;
        long rodsOnGround = 0;
        BlockPos areaMin = helper.absolutePos(new BlockPos(0, 0, 0));
        BlockPos areaMax = helper.absolutePos(new BlockPos(4, 4, 4));
        for (var itemEntity : helper.getLevel().getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(areaMin.getX(), areaMin.getY(), areaMin.getZ(),
                        areaMax.getX(), areaMax.getY(), areaMax.getZ()))) {
            ItemStack dropped = itemEntity.getItem();
            if (dropped.is(ModItems.HULL_PLATE.get())) {
                platesOnGround += dropped.getCount();
            } else if (dropped.is(ModItems.METAL_BILLET.get())) {
                billetsOnGround += dropped.getCount();
            } else if (dropped.is(ModItems.IRON_ROD.get())) {
                rodsOnGround += dropped.getCount();
            }
        }
        if (platesOnGround != 10 || billetsOnGround != 5 || rodsOnGround != 3) {
            helper.fail("Dispensing in open air produced plates=" + platesOnGround
                    + " (expected 10), billets=" + billetsOnGround + " (expected 5), rods="
                    + rodsOnGround + " (expected 3) dropped in the world");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I place a bare storage_drive - one usable slot, the free one every drive gets. I build
     * an L-shaped run of three storage_frame blocks against it, not a straight line, because that
     * is the shape a shallow flood fill gets wrong. The drive should now report four usable slots,
     * and a real data_cell should be accepted into the fourth (index 3) and refused at the fifth
     * (index 4) - the exact boundary. Then I break one connected frame: the drive should notice on
     * its own, without anyone telling it, and drop back to three."</em>
     *
     * <p>See {@code design/data-cells.md} §9. Exercises the real
     * {@code play.xponer.astronima.menu.StorageDriveMenu}'s own gated slot, not just the block
     * entity's {@code canPlaceItem} in isolation - that class exists specifically because a plain
     * vanilla {@code Slot} never consults it at all.
     */
    private static void storageDriveUnlocksSlotsWithConnectedFrame(GameTestHelper helper) {
        BlockPos drivePos = new BlockPos(1, 1, 1);
        helper.setBlock(drivePos, ModBlocks.STORAGE_DRIVE.get().defaultBlockState());
        var drive = helper.getBlockEntity(drivePos,
                play.xponer.astronima.block.entity.StorageDriveBlockEntity.class);
        if (drive == null) {
            helper.fail("Setup failed: storage drive block entity missing");
            return;
        }

        int freeSlots = play.xponer.astronima.block.entity.StorageDriveBlockEntity.FREE_SLOTS;
        if (drive.usableSlots() != freeSlots) {
            helper.fail("A bare drive reported " + drive.usableSlots() + " usable slots, expected "
                    + freeSlots);
            return;
        }

        BlockPos[] frames = {
                drivePos.relative(Direction.NORTH),
                drivePos.relative(Direction.NORTH).relative(Direction.NORTH),
                drivePos.relative(Direction.NORTH).relative(Direction.NORTH).relative(Direction.UP),
        };
        for (BlockPos frame : frames) {
            helper.setBlock(frame, ModBlocks.STORAGE_FRAME.get().defaultBlockState());
        }

        int expectedUsable = freeSlots + frames.length;
        if (drive.usableSlots() != expectedUsable) {
            helper.fail("Three connected frame blocks (an L-shape, not a line) gave "
                    + drive.usableSlots() + " usable slots, expected " + expectedUsable);
            return;
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        // The real LDLib2 menu, built server-side exactly the way opening the block does
        // (StorageDriveBlock's own openMenu call) - the regression guard for the trap that
        // IContainerUIHolder#createUI runs synchronously inside this constructor, on the server
        // too, per PlayerScenarios#feedSlotRefusesWrongItems.
        var holder = new play.xponer.astronima.menu.StorageDriveUiHolder(drive, helper.absolutePos(drivePos));
        var menu = new com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu(
                play.xponer.astronima.registry.ModMenus.STORAGE_DRIVE.get(), 1,
                player.getInventory(), holder);
        ItemStack cell = new ItemStack(ModItems.DATA_CELL.get());

        int lastUnlocked = expectedUsable - 1;
        if (!menu.slots.get(lastUnlocked).mayPlace(cell)) {
            helper.fail("Slot " + lastUnlocked + " should be unlocked with " + expectedUsable
                    + " usable slots, but the drive's own menu refused a real cell there");
            return;
        }
        int firstLocked = expectedUsable;
        if (menu.slots.get(firstLocked).mayPlace(cell)) {
            helper.fail("Slot " + firstLocked + " should still be locked with only " + expectedUsable
                    + " usable slots, but the drive's own menu accepted a real cell there");
            return;
        }

        helper.destroyBlock(frames[2]);
        int expectedAfterBreak = expectedUsable - 1;
        if (drive.usableSlots() != expectedAfterBreak) {
            helper.fail("Breaking one connected frame left " + drive.usableSlots()
                    + " usable slots, expected " + expectedAfterBreak
                    + " - the count should be live, not cached from when the structure was built");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I build a drive holding two loaded data cells - one with hull plates and billets, one
     * with more hull plates - and set a terminal against the drive. Opening the terminal should
     * show both real totals combined, sorted by name: hull_plate first, metal_billet second. I
     * click the hull_plate row to withdraw a stack, and the real total across both cells should
     * drop by exactly that much - not vanish, not double. Dropping a different item onto that
     * same row while it's still showing hull_plate should do nothing at all. Shift-clicking the
     * metal_billet row should hand me the real amount in one go, with nothing left double-counted
     * in the drive. There is no separate input slot anymore - dropping a stack of a brand new
     * item straight onto whichever row is now empty should auto-distribute into the drive's
     * cells and show up in the browsed list on its own, with nothing lost."</em>
     *
     * <p>See {@code design/data-cells.md} §25/§27. Display rows are real, plain {@code Slot}s now
     * (the vanilla-parity rework) - real single-click pickup (left click takes the whole shown
     * total, capped to one real stack by {@code withdraw}; here exercised via {@code Slot#remove},
     * the same method vanilla's own {@code tryRemove} calls) and real placement
     * ({@code Slot#safeInsert}, vanilla's own deposit-by-click/drag path) both reach the block
     * entity's own real {@code removeItem}/{@code setItem} directly - the same real call a deposit
     * onto an empty row (no input slot needed, §27) or a mismatched-item deposit (refused by
     * {@code Slot#safeInsert}'s own item-identity check) both go through. Shift-click
     * ({@code menu.quickMoveStack}) is the one path {@code StorageTerminalMenu} still has to
     * intercept explicitly - the generic implementation mutates a slot's {@code ItemStack}
     * directly (PLAN.md rule 131/133's own finding), which would duplicate a computed row's total
     * if it were ever allowed to reach it unintercepted.
     */
    private static void storageTerminalBrowsesWithdrawsAndDeposits(GameTestHelper helper) {
        BlockPos drivePos = new BlockPos(1, 1, 1);
        BlockPos terminalPos = drivePos.relative(Direction.EAST);
        helper.setBlock(drivePos, ModBlocks.STORAGE_DRIVE.get().defaultBlockState());
        helper.setBlock(terminalPos, ModBlocks.STORAGE_TERMINAL.get().defaultBlockState());

        var drive = helper.getBlockEntity(drivePos,
                play.xponer.astronima.block.entity.StorageDriveBlockEntity.class);
        var terminal = helper.getBlockEntity(terminalPos,
                play.xponer.astronima.block.entity.StorageTerminalBlockEntity.class);
        if (drive == null || terminal == null) {
            helper.fail("Setup failed: drive or terminal block entity missing");
            return;
        }

        ItemStack cellA = new ItemStack(ModItems.DATA_CELL.get());
        var ledgerA = play.xponer.astronima.sim.storage.DataLedger.empty()
                .withAdded("astronima:hull_plate", 10,
                        play.xponer.astronima.sim.storage.DataLedger.SLOT_CAPACITY)
                .withAdded("astronima:metal_billet", 5,
                        play.xponer.astronima.sim.storage.DataLedger.SLOT_CAPACITY);
        cellA.set(play.xponer.astronima.registry.ModDataComponents.DATA_CELL_LEDGER.get(), ledgerA);
        drive.setItem(0, cellA);

        ItemStack cellB = new ItemStack(ModItems.DATA_CELL.get());
        var ledgerB = play.xponer.astronima.sim.storage.DataLedger.empty()
                .withAdded("astronima:hull_plate", 20,
                        play.xponer.astronima.sim.storage.DataLedger.SLOT_CAPACITY);
        cellB.set(play.xponer.astronima.registry.ModDataComponents.DATA_CELL_LEDGER.get(), ledgerB);
        drive.setItem(1, cellB);

        terminal.refreshView();
        ItemStack row0 = terminal.getItem(
                play.xponer.astronima.block.entity.StorageTerminalBlockEntity.FIRST_DISPLAY_SLOT);
        ItemStack row1 = terminal.getItem(
                play.xponer.astronima.block.entity.StorageTerminalBlockEntity.FIRST_DISPLAY_SLOT + 1);
        if (!row0.is(ModItems.HULL_PLATE.get()) || row0.getCount() != 30) {
            helper.fail("Browsing the terminal's first row showed " + row0
                    + ", expected 30 real hull_plate (the union of both cells)");
            return;
        }
        if (!row1.is(ModItems.METAL_BILLET.get()) || row1.getCount() != 5) {
            helper.fail("Browsing the terminal's second row showed " + row1
                    + ", expected 5 real metal_billet");
            return;
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        // The real LDLib2 menu, built server-side exactly the way opening the block does
        // (StorageTerminalBlock's own openMenu call) - the same trap guard as the drive's own
        // scenario above.
        var holder = new play.xponer.astronima.menu.StorageTerminalUiHolder(
                terminal, helper.absolutePos(terminalPos));
        var menu = new play.xponer.astronima.menu.StorageTerminalMenu(
                play.xponer.astronima.registry.ModMenus.STORAGE_TERMINAL.get(), 1,
                player.getInventory(), holder);
        // Menu-local slot index matches the container's own index 1:1 now (§27 - no separate
        // input slot to offset display rows past). Sorted by NAME: "hull_plate" < "metal_billet",
        // so row 0 is hull_plate, row 1 is metal_billet, for as long as hull_plate still has
        // anything logged at all.
        var hullPlateSlot = menu.slots.get(0);
        var metalBilletSlot = menu.slots.get(1);
        if (!hullPlateSlot.mayPlace(new ItemStack(ModItems.HULL_PLATE.get()))) {
            helper.fail("A display row is a plain, ordinary Slot now (§25's rework) - it must "
                    + "accept real vanilla placement, the same as any real slot");
            return;
        }
        if (!hullPlateSlot.mayPickup(player)) {
            helper.fail("A display row is a plain, ordinary Slot now - it must allow real "
                    + "vanilla pickup, the same as any real slot");
            return;
        }

        // Real vanilla single-click pickup: Slot#remove, exactly what Slot#tryRemove calls for a
        // real left-click. Only 10 of the real 30 is taken, deliberately, so the row still shows
        // hull_plate afterward (sorted position unchanged) rather than the aggregate re-sorting
        // metal_billet into row 0 once hull_plate's own entry disappeared entirely.
        ItemStack pickedUp = hullPlateSlot.remove(10);
        if (!pickedUp.is(ModItems.HULL_PLATE.get()) || pickedUp.getCount() != 10) {
            helper.fail("Picking up 10 from the hull_plate row returned " + pickedUp
                    + ", expected a real stack of 10");
            return;
        }
        long afterPickup = play.xponer.astronima.sim.storage.TerminalView
                .of(terminal.connectedLedgers()).countOf("astronima:hull_plate");
        if (afterPickup != 20) {
            helper.fail("After picking up 10 of 30 real hull_plate, the drive's own cells total "
                    + afterPickup + ", expected 20");
            return;
        }

        // Real vanilla placement: Slot#safeInsert, exactly what a real click-to-deposit or a
        // drag-and-drop reaches. Depositing the same 10 back onto the same (still hull_plate)
        // row must merge cleanly, with nothing left over and nothing invented.
        ItemStack leftover = hullPlateSlot.safeInsert(pickedUp.copy());
        if (!leftover.isEmpty()) {
            helper.fail("Depositing 10 real hull_plate back onto its own row left " + leftover
                    + " behind - it should have merged completely");
            return;
        }
        long afterDeposit = play.xponer.astronima.sim.storage.TerminalView
                .of(terminal.connectedLedgers()).countOf("astronima:hull_plate");
        if (afterDeposit != 30) {
            helper.fail("After depositing the 10 back, the drive's own cells total " + afterDeposit
                    + " real hull_plate, expected the full 30 again");
            return;
        }

        // Mismatched deposit: Slot#safeInsert computes a transferable amount before it ever checks
        // item identity, but only actually merges through Slot#isEmpty() or
        // ItemStack.isSameItemSameComponents(...) - a mismatched item hits neither branch, so the
        // slot's own contents are left untouched and the full input stack is handed back exactly
        // as given. This is the same real vanilla safety a normal sorted chest relies on; nothing
        // custom is needed to keep hovering a mismatched item from taking or adding anything.
        ItemStack mismatched = new ItemStack(ModItems.METAL_BILLET.get(), 5);
        ItemStack mismatchLeftover = hullPlateSlot.safeInsert(mismatched);
        if (mismatchLeftover.getCount() != 5 || !mismatchLeftover.is(ModItems.METAL_BILLET.get())) {
            helper.fail("Depositing metal_billet onto the hull_plate row returned " + mismatchLeftover
                    + ", expected the full 5 refused and handed back untouched");
            return;
        }
        long hullPlateAfterMismatch = play.xponer.astronima.sim.storage.TerminalView
                .of(terminal.connectedLedgers()).countOf("astronima:hull_plate");
        if (hullPlateAfterMismatch != 30) {
            helper.fail("Attempting a mismatched deposit changed the hull_plate row's own total to "
                    + hullPlateAfterMismatch + ", expected it to stay at 30 (untouched)");
            return;
        }
        long metalBilletAfterMismatch = play.xponer.astronima.sim.storage.TerminalView
                .of(terminal.connectedLedgers()).countOf("astronima:metal_billet");
        if (metalBilletAfterMismatch != 5) {
            helper.fail("Attempting a mismatched deposit changed the drive's own metal_billet total"
                    + " to " + metalBilletAfterMismatch + ", expected it to stay at 5 (nothing"
                    + " absorbed)");
            return;
        }

        // Shift-click: StorageTerminalMenu#quickMoveStack must intercept this display row
        // explicitly rather than fall through to the generic, direct-mutation implementation -
        // exactly the LDLib2 duplication risk PLAN.md rule 133 found. A real withdrawal (capped
        // to one real stack) should land in the player's own inventory, and the ledger should
        // lose precisely that much - nothing invented, nothing left double-counted.
        ItemStack metalBilletBeforeShiftClick = metalBilletSlot.getItem().copy();
        menu.quickMoveStack(player, 1);
        long metalBilletInInventory = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.METAL_BILLET.get())) {
                metalBilletInInventory += stack.getCount();
            }
        }
        if (metalBilletInInventory != metalBilletBeforeShiftClick.getCount()) {
            helper.fail("Shift-clicking the metal_billet row put " + metalBilletInInventory
                    + " into the player's inventory, expected the real "
                    + metalBilletBeforeShiftClick.getCount() + " that were actually logged");
            return;
        }
        long remainingBillet = play.xponer.astronima.sim.storage.TerminalView
                .of(terminal.connectedLedgers()).countOf("astronima:metal_billet");
        if (remainingBillet != 0) {
            helper.fail("After shift-clicking away all 5 real metal_billet, the drive's own cells"
                    + " still total " + remainingBillet + " - the shift-click duplicated it"
                    + " instead of actually withdrawing it");
            return;
        }

        // No separate input slot anymore (§27) - a brand new item type deposits the same way an
        // existing row's own top-up does, straight onto whichever display row is currently empty.
        // metalBilletSlot's own row is exactly that now: the shift-click above withdrew every
        // real metal_billet, so refreshView() (run inside withdraw() itself) already cleared it.
        // Real Slot#safeInsert again, not a block-entity API call - the same real gesture a
        // drag-and-drop deposit onto an empty row produces.
        ItemStack deposit = new ItemStack(ModItems.IRON_ROD.get(), 50);
        ItemStack depositLeftover = metalBilletSlot.safeInsert(deposit);
        if (!depositLeftover.isEmpty()) {
            helper.fail("Depositing 50 iron_rod onto an empty display row left " + depositLeftover
                    + " behind - the drive's cells had ample room and should have absorbed all of it");
            return;
        }
        long depositedRods = play.xponer.astronima.sim.storage.TerminalView
                .of(terminal.connectedLedgers()).countOf("astronima:iron_rod");
        if (depositedRods != 50) {
            helper.fail("After depositing 50 iron_rod through an empty display row, the drive's"
                    + " own cells total " + depositedRods + ", expected 50");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I log more distinct items into a cell than the terminal's 54 real slots can show at
     * once. Scrolling down should reveal the rest, not just leave them invisible - the terminal
     * used to have no way to reach anything past row 54 at all."</em>
     *
     * <p>See {@code design/data-cells.md} §18. Exercises
     * {@link play.xponer.astronima.block.entity.StorageTerminalBlockEntity#setScrollRow} directly
     * against a real 63-distinct-item cell (one full row of 9 more than the 54-slot/6-row
     * viewport shows), the same real block-entity method {@code TerminalScrollPayload}'s own
     * server handler calls.
     */
    private static void storageTerminalListScrollsPastTheFirstPage(GameTestHelper helper) {
        BlockPos drivePos = new BlockPos(1, 1, 1);
        BlockPos terminalPos = drivePos.relative(Direction.EAST);
        helper.setBlock(drivePos, ModBlocks.STORAGE_DRIVE.get().defaultBlockState());
        helper.setBlock(terminalPos, ModBlocks.STORAGE_TERMINAL.get().defaultBlockState());

        var drive = helper.getBlockEntity(drivePos,
                play.xponer.astronima.block.entity.StorageDriveBlockEntity.class);
        var terminal = helper.getBlockEntity(terminalPos,
                play.xponer.astronima.block.entity.StorageTerminalBlockEntity.class);
        if (drive == null || terminal == null) {
            helper.fail("Setup failed: drive or terminal block entity missing");
            return;
        }

        int columns = play.xponer.astronima.block.entity.StorageTerminalBlockEntity.COLUMNS;
        int visibleRows = play.xponer.astronima.block.entity.StorageTerminalBlockEntity.VISIBLE_ROWS;
        int distinctItems = (visibleRows + 1) * columns; // one full row more than fits at once
        List<String> itemIds = net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet().stream()
                .filter(id -> id.getNamespace().equals(Astronima.MODID))
                .map(Object::toString)
                .sorted()
                .limit(distinctItems)
                .toList();
        if (itemIds.size() < distinctItems) {
            helper.fail("Only " + itemIds.size() + " real astronima: items exist, need at least "
                    + distinctItems + " to exercise a second scroll page");
            return;
        }

        var ledger = play.xponer.astronima.sim.storage.DataLedger.empty();
        for (String itemId : itemIds) {
            ledger = ledger.withAdded(itemId, 1,
                    play.xponer.astronima.sim.storage.DataLedger.SLOT_CAPACITY);
        }
        ItemStack cell = new ItemStack(ModItems.DATA_CELL.get());
        cell.set(play.xponer.astronima.registry.ModDataComponents.DATA_CELL_LEDGER.get(), ledger);
        drive.setItem(0, cell);
        terminal.refreshView();

        int expectedTotalRows = visibleRows + 1;
        if (terminal.totalRows() != expectedTotalRows) {
            helper.fail(distinctItems + " distinct logged items should need " + expectedTotalRows
                    + " rows of " + columns + ", terminal reports " + terminal.totalRows());
            return;
        }
        if (terminal.scrollRow() != 0) {
            helper.fail("A freshly refreshed terminal should start at scroll row 0, was at "
                    + terminal.scrollRow());
            return;
        }

        // Sorted by NAME (the default sort): entry (visibleRows*columns) is the first one that
        // belongs on the second page and must not be visible anywhere before scrolling.
        String firstOffPageItem = itemIds.get(visibleRows * columns);
        boolean visibleBeforeScrolling = false;
        for (int slot = 0; slot < visibleRows * columns; slot++) {
            ItemStack shown = terminal.getItem(
                    play.xponer.astronima.block.entity.StorageTerminalBlockEntity.FIRST_DISPLAY_SLOT
                            + slot);
            if (idOf(shown).equals(firstOffPageItem)) {
                visibleBeforeScrolling = true;
            }
        }
        if (visibleBeforeScrolling) {
            helper.fail("Item " + firstOffPageItem + " (the first one past row " + visibleRows
                    + ") was already visible before scrolling down");
            return;
        }

        // scrollRow moves one real grid row (columns entries) per step, and is clamped to
        // totalRows - visibleRows - the last row can always be scrolled fully into view, and no
        // further (999 asks for far more than that, on purpose, to prove the clamp holds).
        terminal.setScrollRow(999);
        int maxScrollRow = expectedTotalRows - visibleRows;
        if (terminal.scrollRow() != maxScrollRow) {
            helper.fail("Scrolling past the end should clamp to " + maxScrollRow + ", landed at "
                    + terminal.scrollRow());
            return;
        }
        // At the real max scroll, the window ends exactly on the very last logged item - the one
        // that was flatly unreachable before this feature existed.
        String lastItem = itemIds.get(distinctItems - 1);
        int lastSlotIndex = play.xponer.astronima.block.entity.StorageTerminalBlockEntity
                .FIRST_DISPLAY_SLOT + visibleRows * columns - 1;
        ItemStack lastSlot = terminal.getItem(lastSlotIndex);
        if (!idOf(lastSlot).equals(lastItem)) {
            helper.fail("Scrolled to the bottom, the last slot showed " + idOf(lastSlot)
                    + ", expected the very last logged item " + lastItem);
            return;
        }
        // The item that was invisible before scrolling is now visible somewhere on screen.
        boolean visibleAfterScrolling = false;
        for (int slot = 0; slot < visibleRows * columns; slot++) {
            ItemStack shown = terminal.getItem(
                    play.xponer.astronima.block.entity.StorageTerminalBlockEntity.FIRST_DISPLAY_SLOT
                            + slot);
            if (idOf(shown).equals(firstOffPageItem)) {
                visibleAfterScrolling = true;
            }
        }
        if (!visibleAfterScrolling) {
            helper.fail("Item " + firstOffPageItem + " is still not visible even scrolled to the"
                    + " bottom - it should be reachable somewhere now");
            return;
        }

        terminal.setScrollRow(-50);
        if (terminal.scrollRow() != 0) {
            helper.fail("Scrolling before the start should clamp to 0, landed at "
                    + terminal.scrollRow());
            return;
        }

        helper.succeed();
    }

    /**
     * <em>"I shift-click a row that only has 30 logged, expecting to get all 30 - not zero
     * because I asked for a full stack and there was not quite a full stack there."</em>
     *
     * <p>See {@code design/data-cells.md} §22. {@link play.xponer.astronima.sim.storage.TerminalView
     * #withdrawalPlan} is deliberately all-or-nothing (refuses outright rather than a silent
     * short-count) - correct for that pure function's own contract, but wrong for a caller asking
     * "give me up to a stack." Reported directly: shift-click on anything under a full stack
     * withdrew nothing at all.
     */
    private static void storageTerminalWithdrawGivesWhatsThereEvenUnderAStack(GameTestHelper helper) {
        BlockPos drivePos = new BlockPos(1, 1, 1);
        BlockPos terminalPos = drivePos.relative(Direction.EAST);
        helper.setBlock(drivePos, ModBlocks.STORAGE_DRIVE.get().defaultBlockState());
        helper.setBlock(terminalPos, ModBlocks.STORAGE_TERMINAL.get().defaultBlockState());

        var drive = helper.getBlockEntity(drivePos,
                play.xponer.astronima.block.entity.StorageDriveBlockEntity.class);
        var terminal = helper.getBlockEntity(terminalPos,
                play.xponer.astronima.block.entity.StorageTerminalBlockEntity.class);
        if (drive == null || terminal == null) {
            helper.fail("Setup failed: drive or terminal block entity missing");
            return;
        }

        ItemStack cell = new ItemStack(ModItems.DATA_CELL.get());
        var ledger = play.xponer.astronima.sim.storage.DataLedger.empty()
                .withAdded("astronima:hull_plate", 30,
                        play.xponer.astronima.sim.storage.DataLedger.SLOT_CAPACITY);
        cell.set(play.xponer.astronima.registry.ModDataComponents.DATA_CELL_LEDGER.get(), ledger);
        drive.setItem(0, cell);
        terminal.refreshView();

        // A full-stack request (what a shift-click always asks for) against a row with only 30
        // logged must give the real 30, not refuse because 30 != 64.
        long withdrawn = terminal.withdraw("astronima:hull_plate",
                new ItemStack(ModItems.HULL_PLATE.get()).getMaxStackSize());
        if (withdrawn != 30) {
            helper.fail("Asking for a full stack against a row with only 30 logged returned "
                    + withdrawn + ", expected all 30 - not zero, and not more than what was there");
            return;
        }
        long remaining = play.xponer.astronima.sim.storage.TerminalView
                .of(terminal.connectedLedgers()).countOf("astronima:hull_plate");
        if (remaining != 0) {
            helper.fail("After withdrawing all 30 logged hull_plate, the drive's own cells still"
                    + " total " + remaining + ", expected 0");
            return;
        }
        helper.succeed();
    }

    /** The real item id behind a shown stack, or {@code "minecraft:air"} for an empty one — the
     *  same id {@link play.xponer.astronima.block.entity.StorageTerminalBlockEntity} itself keys
     *  every entry by. */
    private static String idOf(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /**
     * <em>"I log 65 of one real item into a fresh cell - one more than a single 64-item slot
     * holds, so it costs 2 real slots. I hold the cell in one hand and a compressor in the
     * other and use it: the cell should now hold 128 items per slot, the same 65 items should
     * still be logged - same item, same real count - and they should now cost only 1 slot. Doing
     * it again should climb another tier; doing it enough times should eventually refuse, cell
     * and compressor both untouched, once the cell is already at its densest tier."</em>
     *
     * <p>See {@code design/data-cells.md} §15. Exercises {@code CellUpgradeItem} directly against
     * a real held cell, the same "hold the target, use the part" verb {@code SuitRepairItem}
     * already established for this mod.
     */
    private static void cellCompressorRaisesCompressionKeepingContents(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack cell = new ItemStack(ModItems.DATA_CELL.get());
        var ledger = play.xponer.astronima.sim.storage.DataLedger.empty()
                .withAdded("astronima:hull_plate", 65,
                        play.xponer.astronima.sim.storage.DataLedger.SLOT_CAPACITY);
        cell.set(play.xponer.astronima.registry.ModDataComponents.DATA_CELL_LEDGER.get(), ledger);
        if (ledger.slotsUsed() != 2) {
            helper.fail("Setup failed: 65 items at the base 64-per-slot tier should cost 2 slots,"
                    + " not " + ledger.slotsUsed());
            return;
        }

        player.setItemInHand(InteractionHand.MAIN_HAND, cell);
        player.setItemInHand(InteractionHand.OFF_HAND,
                new ItemStack(ModItems.CELL_COMPRESSOR.get(), 4));

        InteractionResult first = ModItems.CELL_COMPRESSOR.get()
                .use(helper.getLevel(), player, InteractionHand.OFF_HAND);
        if (first != InteractionResult.SUCCESS) {
            helper.fail("The compressor refused a real cell holding 65 real items: " + first);
            return;
        }
        ItemStack cellAfter = player.getItemInHand(InteractionHand.MAIN_HAND);
        var ledgerAfter = play.xponer.astronima.item.DataCellItem.ledgerOf(cellAfter);
        if (ledgerAfter.itemsPerSlot() != 128) {
            helper.fail("After one compression the cell reports " + ledgerAfter.itemsPerSlot()
                    + " items per slot, expected 128");
            return;
        }
        if (ledgerAfter.countOf("astronima:hull_plate") != 65) {
            helper.fail("Compressing changed the real logged count to "
                    + ledgerAfter.countOf("astronima:hull_plate") + ", expected 65 unchanged");
            return;
        }
        if (ledgerAfter.slotsUsed() != 1) {
            helper.fail("65 items at the new 128-per-slot tier should cost 1 slot, not "
                    + ledgerAfter.slotsUsed());
            return;
        }
        if (player.getItemInHand(InteractionHand.OFF_HAND).getCount() != 3) {
            helper.fail("One real compressor should have been consumed, leaving 3, but the hand"
                    + " holds " + player.getItemInHand(InteractionHand.OFF_HAND).getCount());
            return;
        }

        // Climb every remaining real tier (256, 512, 1024), then confirm the top tier refuses.
        for (int i = 0; i < 3; i++) {
            ModItems.CELL_COMPRESSOR.get().use(helper.getLevel(), player, InteractionHand.OFF_HAND);
        }
        var ledgerMaxed = play.xponer.astronima.item.DataCellItem
                .ledgerOf(player.getItemInHand(InteractionHand.MAIN_HAND));
        if (ledgerMaxed.itemsPerSlot() != 1024) {
            helper.fail("After climbing every real tier the cell reports "
                    + ledgerMaxed.itemsPerSlot() + " items per slot, expected the real ceiling"
                    + " of 1024");
            return;
        }
        InteractionResult refused = ModItems.CELL_COMPRESSOR.get()
                .use(helper.getLevel(), player, InteractionHand.OFF_HAND);
        if (refused != InteractionResult.FAIL) {
            helper.fail("A cell already at the real 1024-per-slot ceiling should refuse another"
                    + " compressor, got " + refused);
            return;
        }
        if (player.getItemInHand(InteractionHand.OFF_HAND).getCount() != 0) {
            helper.fail("The refused application should not have consumed a compressor, but the"
                    + " hand now holds " + player.getItemInHand(InteractionHand.OFF_HAND).getCount());
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I load the acid plant with hematite and give it two of its three reagents - SO2 and
     * oxygen, but no water vapor yet. Does it react on two-thirds of the equation? And once I
     * pipe in the room's own water vapor - the exact gas the Sabatier or Bosch reactor next door
     * already vents - does it finally close the sulfur chain into real, bottled sulfuric acid?"
     * </em>
     *
     * <p>The first machine in this mod to need three room reagents at once
     * (design/chemistry-loop.md §2.7) - this is the first real test of that three-way gate, not
     * just a wider copy of Sabatier's or Bosch's own two-reagent check.
     */
    private static void acidPlantNeedsAllThreeReagents(GameTestHelper helper) {
        BlockPos inside = new BlockPos(3, 2, 3);
        BlockPos plantPos = inside.offset(1, 0, 0);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int ring = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                    helper.setBlock(inside.offset(dx, dy, dz), ring <= 1
                            ? Blocks.AIR.defaultBlockState()
                            : ModBlocks.HULL_PLATE.get().defaultBlockState());
                }
            }
        }
        helper.setBlock(plantPos, ModBlocks.SULFURIC_ACID_PLANT.get().defaultBlockState());

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(inside));
        Atmosphere.RoomReading reading = atmosphere.readingAt(helper.absolutePos(inside));
        if (reading == null || !reading.sealed()) {
            helper.fail("Setup failed: the plant's room is not sealed");
            return;
        }
        var plant = helper.getBlockEntity(plantPos,
                play.xponer.astronima.block.entity.SulfuricAcidPlantBlockEntity.class);
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        if (plant == null || room == null) {
            helper.fail("Setup failed: plant or room missing");
            return;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }

        plant.setItem(play.xponer.astronima.block.entity.SulfuricAcidPlantBlockEntity.SLOT_CATALYST,
                new ItemStack(ModBlocks.HEMATITE_ORE.get()));

        // Two of three: SO2 and oxygen, but no water vapor.
        room.addGasAt(Gas.SULFUR_DIOXIDE,
                play.xponer.astronima.block.entity.SulfuricAcidPlantBlockEntity.SO2_PER_BATCH_MOL,
                293.15);
        room.addGasAt(Gas.OXYGEN,
                play.xponer.astronima.block.entity.SulfuricAcidPlantBlockEntity.O2_PER_BATCH_MOL,
                293.15);
        // The panel's own reagent-stock reading (design/machines.md's own Update section) has to
        // agree with exactly this "two of three" room state: SO2 and O2 both full, water vapor
        // genuinely at zero - the one machine in the mod reading three room gases at once.
        if (plant.so2Fraction() < 0.999 || plant.oxygenFraction() < 0.999
                || plant.waterVaporFraction() > 1e-9) {
            helper.fail("The reagent-stock reading disagreed with the real room gas it is supposed"
                    + " to reflect: so2Fraction=" + plant.so2Fraction() + ", oxygenFraction="
                    + plant.oxygenFraction() + " (both expected ~1.0), waterVaporFraction="
                    + plant.waterVaporFraction() + " (expected 0.0)");
            return;
        }
        runMachine(plant,
                play.xponer.astronima.block.entity.SulfuricAcidPlantBlockEntity.BATCH_WORK + 40);

        if (room.gases().get(Gas.SULFUR_DIOXIDE)
                < play.xponer.astronima.block.entity.SulfuricAcidPlantBlockEntity.SO2_PER_BATCH_MOL
                        - 1e-6) {
            helper.fail("The plant reacted with only two of its three reagents present - it"
                    + " should hold on the missing water vapor, not react on two-thirds of the"
                    + " equation");
            return;
        }

        // All three: top up water vapor, the exact gas Sabatier and Bosch already vent.
        room.addGasAt(Gas.WATER_VAPOR,
                play.xponer.astronima.block.entity.SulfuricAcidPlantBlockEntity.H2O_PER_BATCH_MOL,
                293.15);
        double so2Before = room.gases().get(Gas.SULFUR_DIOXIDE);
        double o2Before = room.gases().get(Gas.OXYGEN);
        double h2oBefore = room.gases().get(Gas.WATER_VAPOR);
        runMachine(plant,
                play.xponer.astronima.block.entity.SulfuricAcidPlantBlockEntity.BATCH_WORK + 40);

        if (!(room.gases().get(Gas.SULFUR_DIOXIDE) < so2Before)
                || !(room.gases().get(Gas.OXYGEN) < o2Before)
                || !(room.gases().get(Gas.WATER_VAPOR) < h2oBefore)) {
            helper.fail("The plant did not draw down all three reagents once all three were"
                    + " plentiful (SO2 " + so2Before + " -> " + room.gases().get(Gas.SULFUR_DIOXIDE)
                    + ", O2 " + o2Before + " -> " + room.gases().get(Gas.OXYGEN)
                    + ", H2O " + h2oBefore + " -> " + room.gases().get(Gas.WATER_VAPOR) + ")");
            return;
        }
        ItemStack acidOut = plant.getItem(
                play.xponer.astronima.block.entity.SulfuricAcidPlantBlockEntity.SLOT_ACID);
        if (!acidOut.is(ModItems.SULFURIC_ACID.get()) || acidOut.getCount() <= 0) {
            helper.fail("A finished batch with all three reagents plentiful produced " + acidOut
                    + " instead of real sulfuric acid");
            return;
        }
        ItemStack hematiteAfter = plant.getItem(
                play.xponer.astronima.block.entity.SulfuricAcidPlantBlockEntity.SLOT_CATALYST);
        if (hematiteAfter.getCount() >= 1) {
            helper.fail("The hematite catalyst survived a finished batch untouched ("
                    + hematiteAfter + ") - the same token-consumption simplification every"
                    + " catalyst in this loop already carries");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I dragged the marker somewhere sound, printed a part - then dragged it into the
     * keyhole corner and printed another, same crank, same everything else."</em>
     *
     * <p>The first real exercise of {@code SlsPrinterBlockEntity#finishBatch()} against a live
     * world, and the row {@code design/sls.md} §5 names as the one that matters: the printed
     * part's own soundness follows wherever the plane was set, at the moment the batch
     * finished, and nothing else about how the batch ran changes it. Both batches here run
     * through the identical idle crank cadence {@code runMachine} already gives every machine
     * in this file - the only thing that changes between them is where the marker sat on the
     * plane, which is exactly the thing this design puts in the player's hand and nothing
     * else's.
     */
    private static void slsPrinterFollowsThePlane(GameTestHelper helper) {
        BlockPos printerPos = new BlockPos(2, 3, 2);
        helper.setBlock(printerPos, ModBlocks.SLS_PRINTER.get().defaultBlockState());
        var printer = helper.getBlockEntity(printerPos, SlsPrinterBlockEntity.class);
        if (printer == null) {
            helper.fail("Setup failed: printer block entity missing");
            return;
        }

        // A fresh printer's own default is already a sound, working point - no crafting
        // prerequisite gatekeeps its most basic job, the same promise every other machine in
        // this tier keeps for its own free default.
        if (LaserSintering.regime(printer.powerW(), printer.speedMmS())
                != LaserSintering.Regime.SOUND) {
            helper.fail("A fresh printer's own default (power=" + printer.powerW() + " W, speed="
                    + printer.speedMmS() + " mm/s) is not in the sound pocket, so a player who"
                    + " never touches the plane cannot print a working part");
            return;
        }
        printer.setItem(SlsPrinterBlockEntity.SLOT_INPUT,
                new ItemStack(ModItems.IRON_POWDER.get(), SlsPrinterBlockEntity.FEED_PER_BATCH));
        runMachine(printer, SlsPrinterBlockEntity.BATCH_WORK + 40);

        ItemStack soundPart = printer.getItem(SlsPrinterBlockEntity.SLOT_OUTPUT);
        if (!soundPart.is(ModItems.SINTERED_FRAME.get()) || soundPart.getCount() <= 0) {
            helper.fail("A batch run at the printer's own sound default produced " + soundPart
                    + " instead of a sintered frame");
            return;
        }
        float soundSoundness = soundPart.getOrDefault(ModDataComponents.SINTER_SOUNDNESS.get(), 0f);
        if (!(soundSoundness >= LaserSintering.SOUND_THRESHOLD)) {
            helper.fail("A part printed in the sound pocket came back " + soundSoundness
                    + " sound, under the " + LaserSintering.SOUND_THRESHOLD + " threshold - the"
                    + " pocket is not where the model says it is");
            return;
        }

        // Drag the marker into the keyhole corner: maximum power, a speed slow enough that
        // track stability is not also the reason it fails. Nothing about the crank changes.
        printer.setFromDials(1.0, 0.0);
        if (LaserSintering.regime(printer.powerW(), printer.speedMmS())
                != LaserSintering.Regime.KEYHOLING) {
            helper.fail("Dragging the marker to (power=" + printer.powerW() + " W, speed="
                    + printer.speedMmS() + " mm/s) did not land in keyholing - the test's own"
                    + " corner is wrong, not the machine");
            return;
        }
        printer.setItem(SlsPrinterBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
        printer.setItem(SlsPrinterBlockEntity.SLOT_INPUT,
                new ItemStack(ModItems.IRON_POWDER.get(), SlsPrinterBlockEntity.FEED_PER_BATCH));
        runMachine(printer, SlsPrinterBlockEntity.BATCH_WORK + 40);

        ItemStack keyholedPart = printer.getItem(SlsPrinterBlockEntity.SLOT_OUTPUT);
        if (!keyholedPart.is(ModItems.SINTERED_FRAME.get())) {
            helper.fail("A batch run outside the sound pocket produced " + keyholedPart
                    + " instead of a part - it must still print, only weaker, not fail to appear"
                    + " at all");
            return;
        }
        float keyholedSoundness = keyholedPart.getOrDefault(
                ModDataComponents.SINTER_SOUNDNESS.get(), 1f);
        if (!(keyholedSoundness < soundSoundness)) {
            helper.fail("The exact same idle printer, only its marker dragged, printed a part"
                    + " just as sound (" + keyholedSoundness + ") as the one from the sound"
                    + " pocket (" + soundSoundness + ") - the part followed something other than"
                    + " the plane");
            return;
        }
        helper.succeed();
    }

    /** Pressure of whatever room owns this cell, or 0 when it is not a room at all. */
    private static double pressureAt(Atmosphere atmosphere, GameTestHelper helper,
                                     BlockPos local) {
        RoomState room = atmosphere.roomAt(helper.absolutePos(local));
        return room == null ? 0 : room.pressureKPa();
    }


    /**
     * <em>"Can I stop making candles yet?"</em>
     *
     * <p>The retort's second job, driven the way a player drives it: chlorate in the feed
     * slot, the dial hunted onto the chlorate window, and the room next door is what you
     * breathe. Before this, {@code OxygenCandleBlockEntity} was the only thing in the whole
     * mod that put oxygen into the world - every other part of the oxygen system moves gas
     * that already exists - so the game had a battery and no charger.
     *
     * <p>And it has to beat the candle by a real margin per powder, not by a few percent, or
     * the machine, the sun and the sealed room buy nothing and the treadmill stands.
     */
    private static void bakingChlorateMakesBreathableOxygen(GameTestHelper helper) {
        BlockPos retortPos = new BlockPos(2, 3, 2);
        sealPocketUnder(helper, retortPos);
        helper.setBlock(retortPos, ModBlocks.SOLAR_RETORT.get().defaultBlockState());

        var retort = helper.getBlockEntity(retortPos, SolarRetortBlockEntity.class);
        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(retortPos.below()));
        RoomState room = atmosphere.roomAt(helper.absolutePos(retortPos.below()));
        if (retort == null || room == null) {
            helper.fail("Setup failed: retort or receiving room missing");
            return;
        }
        if (retort.sunlight() <= 0) {
            helper.fail("No sunlight on the mirror, so every assertion here is vacuous");
            return;
        }

        retort.setItem(SolarRetortBlockEntity.SLOT_INPUT,
                new ItemStack(ModItems.CHLORATE_POWDER.get(), 4));
        // The panel has to be describing chlorate now, not still describing rock - it is
        // what paints the window the player is about to aim at.
        if (retort.process() != RetortProcess.CHLORATE) {
            helper.fail("A retort loaded with chlorate still reports the " + retort.process()
                    + " window, so the marked band on the dial points at the wrong"
                    + " temperature");
            return;
        }

        double o2Before = room.gases().get(Gas.OXYGEN);
        double cl2Before = room.gases().get(Gas.CHLORINE);
        runMachine(retort, SolarRetortBlockEntity.BATCH_WORK + 40);
        double made = room.gases().get(Gas.OXYGEN) - o2Before;

        if (!(made > 1e-6)) {
            helper.fail("A chlorate charge baked in its own window put no oxygen into the"
                    + " room: state=" + retort.workState() + ", vessel at "
                    + retort.temperatureK() + " K");
            return;
        }
        if (room.gases().get(Gas.CHLORINE) > cl2Before + 1e-9) {
            helper.fail("Holding the window still released chlorine - then there is no"
                    + " window and the dial is a slider again");
            return;
        }
        if (retort.getItem(SolarRetortBlockEntity.SLOT_OUTPUT).isEmpty()) {
            helper.fail("The chlorate gave up its oxygen but left no salt behind");
            return;
        }

        // A candle is 100 mol for four powders. One powder through here has to be worth
        // more than one powder through a candle, and visibly so.
        if (!(made > 2 * 100.0 / 4)) {
            helper.fail("One powder gave " + made + " mol through the retort against 25 as a"
                    + " candle - not worth the sun, the sealed room, or the chlorine");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"Fill my bottle off the tank, not out of the room I am breathing."</em>
     *
     * <p>The change that makes working in vacuum survivable. A suit bottle used to be charged
     * only by {@code EmptyOxygenTankItem}, which takes its 12 mol straight out of the
     * habitat's air - so every hour outside was paid for in the air indoors, and mining, the
     * one job that must be done in vacuum, was cheaper to do inside at 6.6 mol of oxygen per
     * block. A charged vessel is oxygen that is <em>not</em> also your atmosphere, and this
     * asserts exactly that separation.
     */
    private static void chargingABottleOffAVessel(GameTestHelper helper) {
        BlockPos inside = new BlockPos(2, 2, 2);
        ModTestFunctions.buildBoxAround(helper, inside);
        BlockPos tankPos = inside.offset(1, 0, 0);
        helper.setBlock(tankPos, ModBlocks.GAS_TANK.get().defaultBlockState());

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(tankPos));
        GasTankBlockEntity vessel = helper.getBlockEntity(tankPos, GasTankBlockEntity.class);
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        if (vessel == null || room == null) {
            helper.fail("Setup failed: vessel or room missing");
            return;
        }
        vessel.contents().addGasAt(Gas.OXYGEN, 400, 293.15);

        Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.OXYGEN_TANK_EMPTY.get()));

        double roomBefore = room.gases().get(Gas.OXYGEN);
        double vesselBefore = vessel.contents().gases().get(Gas.OXYGEN);

        helper.useBlock(tankPos, player);

        double roomAfter = room.gases().get(Gas.OXYGEN);
        double drawn = vesselBefore - vessel.contents().gases().get(Gas.OXYGEN);

        if (!(drawn > 1e-6)) {
            helper.fail("Holding an empty bottle against a charged vessel drew nothing from"
                    + " it, so there is still no way to charge one without spending the"
                    + " habitat's air");
            return;
        }
        if (Math.abs(roomAfter - roomBefore) > 1e-6) {
            helper.fail("Charging off the vessel also took " + (roomBefore - roomAfter)
                    + " mol out of the room - which is the whole thing this was meant to stop"
                    + " doing");
            return;
        }
        if (!hasFilledBottle(player)) {
            helper.fail("The vessel gave up " + drawn + " mol and the player got no charged"
                    + " bottle for it");
            return;
        }
        helper.succeed();
    }

    private static boolean hasFilledBottle(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(ModItems.OXYGEN_TANK.get())) {
                return true;
            }
        }
        return false;
    }


    /**
     * <em>"Everyone says dig in. Does it actually do anything?"</em>
     *
     * <p><strong>The row the whole thermal phase turns on.</strong> A face with rock behind it
     * conducts into an asteroid at 213 K; a face with nothing behind it radiates against a
     * 2.7 K sky, to the fourth power. Those differ by an order of magnitude, so if the scan
     * reads a room's walls backwards then burying a base buys nothing - and it would read as
     * a balance complaint rather than as the bug it is, because every number on screen stays
     * plausible.
     *
     * <p><strong>Both runs happen in the same cubic metres, one after the other.</strong> The
     * first draft built two boxes side by side and they overlapped: the second one's shell was
     * written through the first one's interior, so it compared a 27-cell room against an
     * 18-cell one and passed for a reason that had nothing to do with burying anything. Same
     * space twice is the only arrangement where the size, the shape and the position cannot
     * differ - and it fits inside the area this test owns (rule 21), which two boxes did not.
     */
    private static void buryingAHabitatKeepsItWarm(GameTestHelper helper) {
        double exposedDrop = coolOneHabitat(helper, false, false);
        double buriedDrop = coolOneHabitat(helper, true, false);

        if (!(exposedDrop > 0.001)) {
            helper.fail("A habitat standing in vacuum lost " + exposedDrop + " K - it is not"
                    + " cooling at all, so nothing below this test means anything");
            return;
        }
        if (!(buriedDrop < exposedDrop)) {
            helper.fail("Burying a habitat did not slow its cooling: buried lost "
                    + buriedDrop + " K against the exposed one's " + exposedDrop
                    + " - the shape of a base is not a decision after all");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I re-skinned it in insulated plate. Did that help?"</em>
     *
     * <p>The same buried box twice, and the only difference is what the walls are made of.
     * Insulated plate seals exactly as plain plate does, so the room is the same size and
     * shape both times and there is nothing else left for the difference to come from.
     */
    private static void insulatingAHabitatKeepsItWarm(GameTestHelper helper) {
        double plainDrop = coolOneHabitat(helper, true, false);
        double laggedDrop = coolOneHabitat(helper, true, true);

        if (!(plainDrop > 0.001)) {
            helper.fail("The bare-plate room lost " + plainDrop + " K, so there is nothing"
                    + " for insulation to improve on and this test is vacuous");
            return;
        }
        if (!(laggedDrop < plainDrop)) {
            helper.fail("Insulated plate did not hold heat any better than bare: "
                    + laggedDrop + " K against " + plainDrop + " - the wall the player spent"
                    + " eight tailings a block on does nothing");
            return;
        }
        helper.succeed();
    }

    /**
     * Builds one 3x3x3 habitat in the fixed test spot, warms it to shirtsleeve, lets it sit
     * with nobody in it, and returns how many kelvin it lost.
     *
     * <p>Always the same cells, so successive calls differ only in what is asked for here.
     *
     * @param buried    pack solid rock behind every face
     * @param insulated build the shell out of insulated plate rather than plain
     */
    private static double coolOneHabitat(GameTestHelper helper, boolean buried,
                                         boolean insulated) {
        BlockPos seed = new BlockPos(3, 2, 3);
        // A real roof, high above and nowhere near the habitat itself, so this stays a "pure
        // loss" comparison the way it always was: design/albedo-paint.md's own solar-gain term
        // now reads the sky from Atmosphere.SOLAR_REFERENCE_MARGIN (200) blocks above whichever
        // cell the room's own iterator happens to pick, so a plate wide enough to cover every
        // cell this room could ever contain, placed comfortably inside that reference's own
        // 64-block "is anything in the way" scan, blocks the query outright - dimension- and
        // API-agnostic, unlike forcing night or weather would be.
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                helper.setBlock(seed.offset(dx, 205, dz), Blocks.STONE.defaultBlockState());
            }
        }
        BlockState wall = (insulated ? ModBlocks.INSULATED_HULL_PLATE.get()
                : ModBlocks.HULL_PLATE.get()).defaultBlockState();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int ring = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                    BlockState here;
                    if (ring <= 1) {
                        here = Blocks.AIR.defaultBlockState();
                    } else if (ring == 2) {
                        here = wall;
                    } else {
                        // The layer that decides exposed or buried, and the only thing
                        // these runs disagree about besides the wall material.
                        here = buried ? ModBlocks.HULL_PLATE.get().defaultBlockState()
                                : Blocks.AIR.defaultBlockState();
                    }
                    helper.setBlock(seed.offset(dx, dy, dz), here);
                }
            }
        }

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(seed));
        RoomState room = atmosphere.roomAt(helper.absolutePos(seed));
        if (room == null) {
            helper.fail("Setup failed: the habitat is not a room (buried=" + buried
                    + ", insulated=" + insulated + ")");
            return 0;
        }
        if (room.volumeBlocks() != 27) {
            helper.fail("The habitat came out " + room.volumeBlocks() + " cells rather than"
                    + " 27, so this run is not comparable with the other one");
            return 0;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        room.addGasAt(Gas.OXYGEN, 200, 293.15);
        room.setTemperatureK(293.15);

        // Nobody in it and nothing running: pure loss, which is what is being compared - and
        // since design/albedo-paint.md, "pure" now has to say so explicitly. A real exposed
        // wall in real daylight gains real watts from the sun (that mechanic's own point), which
        // this helper's two callers were never built to account for and do not need to; the roof
        // placed above at setup keeps that real, unrelated to whatever the two conditions being
        // compared here are.
        for (int i = 0; i < 200; i++) {
            atmosphere.tick();
        }
        return 293.15 - room.temperatureK();
    }

    /**
     * <em>"Why is the workshop warm and the store room freezing?"</em>
     *
     * <p>Because you are in the workshop, working. There is no heater in this tier and there
     * is not meant to be: a sealed can with people and machines in it is warmed by the people
     * and machines, which is why the ISS carries radiators rather than heaters.
     *
     * <p><strong>And the half that makes it a mechanic rather than a block:</strong> an idle
     * machine must not warm anything. Heat is charged for work being done, so the answer to
     * cold is to go and do something in there - not to place another machine and walk away.
     */
    private static void crankingAMachineWarmsTheRoom(GameTestHelper helper) {
        BlockPos seed = new BlockPos(2, 2, 2);
        ModTestFunctions.buildBoxAround(helper, seed);
        BlockPos crusherPos = seed.offset(1, 0, 0);
        helper.setBlock(crusherPos, ModBlocks.ORE_CRUSHER.get().defaultBlockState());

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(crusherPos));
        RoomState room = atmosphere.roomAt(helper.absolutePos(seed));
        var crusher = helper.getBlockEntity(crusherPos, OreCrusherBlockEntity.class);
        if (room == null || crusher == null) {
            helper.fail("Setup failed: room=" + room + " crusher=" + crusher);
            return;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        room.addGasAt(Gas.OXYGEN, 200, 293.15);
        crusher.setItem(0, new ItemStack(ModItems.METAL_RICH_ORE.get(), 64));

        // Loaded and legal, but nobody is turning the handle.
        room.setTemperatureK(293.15);
        for (int i = 0; i < 100; i++) {
            crusher.serverTick();
            atmosphere.tick();
        }
        double idleDrop = 293.15 - room.temperatureK();

        // Now somebody is.
        room.setTemperatureK(293.15);
        for (int i = 0; i < 100; i++) {
            crusher.crank();
            crusher.serverTick();
            atmosphere.tick();
        }
        double crankedDrop = 293.15 - room.temperatureK();

        if (!(idleDrop > 0.0)) {
            helper.fail("A room with an idle machine in it did not cool at all (" + idleDrop
                    + " K), so there is nothing for cranking to improve on");
            return;
        }
        if (!(crankedDrop < idleDrop)) {
            helper.fail("Cranking the crusher put no warmth into the room: it still lost "
                    + crankedDrop + " K against " + idleDrop + " idle - there is no counter"
                    + " to cold in this tier and nothing a player does changes it");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I built a workshop, dropped a block of this in it, and kept working. Does the room
     * actually end up cooler than it would have with nothing there - or does the block just sit
     * in the corner looking like wax?"</em>
     *
     * <p>The same room, fed the same steady stream of heat (a stand-in for machines actually
     * being worked - the shape of heat this block exists to answer, not an instant, arbitrary
     * jump) and driven twice: once with nobody ticking the block, once with its own {@code
     * serverTick} actually running. A one-time forced temperature spike was tried first and
     * rejected — a single 2 kg block's whole latent capacity (400 kJ) is spent by an overshoot of
     * well under one kelvin against this room's own real capacity, so a large one-off jump just
     * melts the block instantly and proves nothing about holding a room during real, sustained
     * work. If the fed run does not end up measurably cooler than the unfed one, the block is
     * decoration; if {@code meltFraction} never moves, whatever held the room did not do it by
     * storing latent heat, which is not the mechanism {@code design/phase-change-blocks.md}
     * describes.
     */
    private static void paraffinThermalMassHoldsTheRoomAtItsMeltPoint(GameTestHelper helper) {
        BlockPos seed = new BlockPos(2, 2, 2);
        ModTestFunctions.buildBoxAround(helper, seed);
        BlockPos massPos = seed.offset(1, 0, 0);
        helper.setBlock(massPos, ModBlocks.PARAFFIN_THERMAL_MASS.get().defaultBlockState());

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        BlockPos absoluteMassPos = helper.absolutePos(massPos);
        atmosphere.invalidate(absoluteMassPos);
        RoomState room = atmosphere.roomAt(helper.absolutePos(seed));
        var mass = helper.getBlockEntity(massPos, ParaffinThermalMassBlockEntity.class);
        if (room == null || mass == null) {
            helper.fail("Setup failed: room=" + room + " mass=" + mass);
            return;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        room.addGasAt(Gas.OXYGEN, 200, 293.15);

        double startK = PhaseChangeMaterial.MELT_POINT_K - 3.0;
        // A deliberately strong, steady load - not one real machine's own wattage, but a
        // controlled rig chosen to push this specific room's own real loss well past the melt
        // point inside a short loop, the same way ammoniaHeatPipeMovesRealHeatBetweenTwoRooms
        // forces its own two rooms to real, controlled starting temperatures rather than
        // deriving them from one machine's realistic draw.
        double joulesPerCycle = 50_000.0;
        int cycles = 100;

        // One real tick to let the setup above settle, then land exactly on the boundary the
        // block's own serverTick shares with Atmosphere's relaxTemperature - the same alignment
        // ammoniaHeatPipeMovesRealHeatBetweenTwoRooms already needs and for the same reason
        // (game time does not advance between the direct calls in the loop below, so the gate
        // has to already be satisfied before that loop starts).
        helper.runAfterDelay(1, () -> {
            long gt = helper.getLevel().getGameTime();
            long toBoundary = (Atmosphere.TICK_INTERVAL - gt % Atmosphere.TICK_INTERVAL)
                    % Atmosphere.TICK_INTERVAL;
            if (toBoundary == 0) {
                toBoundary = Atmosphere.TICK_INTERVAL;
            }
            helper.runAfterDelay(toBoundary, () -> {
                // Baseline: steady heat, cooling on its own with nobody minding the block.
                room.setTemperatureK(startK);
                for (int i = 0; i < cycles; i++) {
                    atmosphere.addHeatJoules(absoluteMassPos, joulesPerCycle);
                    atmosphere.tick();
                }
                double baselineFinal = room.temperatureK();

                // Same room, same steady heat - the only difference is the block's own tick
                // actually running this time, reading the room after Atmosphere's own step the
                // same way it does in the real world (design/phase-change-blocks.md §4).
                room.setTemperatureK(startK);
                mass.setMeltFraction(0.0);
                for (int i = 0; i < cycles; i++) {
                    atmosphere.addHeatJoules(absoluteMassPos, joulesPerCycle);
                    atmosphere.tick();
                    mass.serverTick(helper.getLevel(), absoluteMassPos,
                            helper.getLevel().getBlockState(absoluteMassPos));
                }
                double heldFinal = room.temperatureK();

                if (!(baselineFinal > PhaseChangeMaterial.MELT_POINT_K)) {
                    helper.fail("The steady heat never even pushed the unbuffered room past the "
                            + "melt point (" + baselineFinal + " K) - there is no real excess "
                            + "here for the thermal mass to hold against");
                    return;
                }
                if (!(heldFinal < baselineFinal)) {
                    helper.fail("The thermal mass made no difference: held=" + heldFinal
                            + " K, baseline=" + baselineFinal + " K with nobody ticking it");
                    return;
                }
                if (!(mass.meltFraction() > 0.0)) {
                    helper.fail("The room ended up cooler with the block ticking, but its own "
                            + "meltFraction never moved (" + mass.meltFraction() + ") - "
                            + "something else held the room, not this block storing latent heat");
                    return;
                }
                helper.succeed();
            });
        });
    }

    /** A sealed 5x5x5 shell of one specific wall block, centred on {@code inside} - the same
     *  shape {@code ModTestFunctions.buildBoxAround} already builds, parameterised on the wall
     *  material for this test's own bare-vs-painted comparison. */
    private static void buildBoxOf(GameTestHelper helper, BlockPos inside, BlockState wall) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean shell = Math.abs(dx) == 2 || Math.abs(dy) == 2 || Math.abs(dz) == 2;
                    helper.setBlock(inside.offset(dx, dy, dz), shell ? wall
                            : Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /**
     * <em>"I painted one workshop and left the other bare. Same machines, same sun - does the
     * painted one actually run cooler?"</em>
     *
     * <p>Two real, separate rooms, otherwise identical, both exposed and both fed the same
     * steady heat under whatever sun this test batch is running in (real, not forced - {@code
     * aRoofedSolarArrayMakesNothing} already leans on a gametest running in daylight by
     * default). {@code SkyEventOverride}'s occultation control is not used here: it only ever
     * applies inside {@code ModDimensions.ASTEROID_LEVEL} (architecture rule 74), and this
     * batch's own dimension is not it, so there is no random sky event here to pin down in the
     * first place. If painted hull is not measurably cooler here, the whole mechanic is a
     * number nothing in the world ever reads.
     */
    private static void paintedHullSettlesCoolerInTheSunThanBare(GameTestHelper helper) {
        // Seven apart along Z: each 5x5x5 shell spans 5 blocks, leaving two clear blocks of
        // vacuum between them so they read as two real, separate rooms rather than merging into
        // one - and the whole layout stays inside the same footprint width the ammonia heat
        // pipe's own two-room test already uses without needing extra padding.
        BlockPos bareSeed = new BlockPos(2, 2, 2);
        BlockPos paintedSeed = new BlockPos(2, 2, 9);
        buildBoxOf(helper, bareSeed, ModBlocks.HULL_PLATE.get().defaultBlockState());
        buildBoxOf(helper, paintedSeed, ModBlocks.PAINTED_HULL_PLATE.get().defaultBlockState());

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        BlockPos absoluteBare = helper.absolutePos(bareSeed);
        BlockPos absolutePainted = helper.absolutePos(paintedSeed);
        atmosphere.invalidate(absoluteBare);
        atmosphere.invalidate(absolutePainted);
        RoomState bareRoom = atmosphere.roomAt(absoluteBare);
        RoomState paintedRoom = atmosphere.roomAt(absolutePainted);
        if (bareRoom == null || paintedRoom == null) {
            helper.fail("Setup failed: bare=" + bareRoom + " painted=" + paintedRoom);
            return;
        }

        for (Gas gas : Gas.values()) {
            bareRoom.removeGas(gas, bareRoom.gases().get(gas));
            paintedRoom.removeGas(gas, paintedRoom.gases().get(gas));
        }
        bareRoom.addGasAt(Gas.OXYGEN, 200, 293.15);
        paintedRoom.addGasAt(Gas.OXYGEN, 200, 293.15);

        double startK = 293.15;
        bareRoom.setTemperatureK(startK);
        paintedRoom.setTemperatureK(startK);

        // A deliberately strong, steady load on both, identical in every way except which
        // room it lands in - the same controlled-rig reasoning
        // paraffinThermalMassHoldsTheRoomAtItsMeltPoint already uses, so a real difference
        // between the two rooms can only be the wall they are built from.
        double joulesPerCycle = 20_000.0;
        for (int i = 0; i < 200; i++) {
            atmosphere.addHeatJoules(absoluteBare, joulesPerCycle);
            atmosphere.addHeatJoules(absolutePainted, joulesPerCycle);
            atmosphere.tick();
        }

        if (!(bareRoom.temperatureK() > startK)) {
            helper.fail("The bare room did not even warm up under real sun and steady heat ("
                    + bareRoom.temperatureK() + " K) - there is nothing here for paint to "
                    + "improve on, and this test proves nothing either way");
            return;
        }
        if (!(paintedRoom.temperatureK() < bareRoom.temperatureK())) {
            helper.fail("Paint made no real difference: painted room settled at "
                    + paintedRoom.temperatureK() + " K, bare room at "
                    + bareRoom.temperatureK() + " K - identical or worse");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I left the base for a couple of hours. Why am I shivering when I get back?"</em>
     *
     * <p><strong>The join between the two halves of this phase.</strong> T1-T5 make a room
     * cool; T4 makes a body cool in it. Neither is a hazard on its own — a room that gets cold
     * and a person who chills in cold rooms only add up to a mechanic if the temperature the
     * room actually reaches is below the temperature a person can actually sustain. That is
     * one number against another and nothing in either model checks it.
     *
     * <p>So this drives the real room down with nobody in it and asserts it crosses the line
     * `Hypothermia.sustainableAmbientK` draws. Above the line the whole of T4 is dead code
     * that never fires; far below it, stepping outdoors is instant death.
     *
     * <p><strong>What could not be staged, and where the assertion went instead</strong>
     * (rule 14): the player half. A harness player cannot be given a suit and cannot be hurt,
     * so nothing here can watch a core temperature fall or a damage tick land. The falling
     * core, the timescale, the suit's effect and the exertion remedy are all asserted in
     * {@code HypothermiaTest}; that the tick calls the model at all is asserted by
     * {@code PhysiologyWiringTest}. This scenario owns the one claim neither of those can
     * make: that the world really produces the temperatures the model is written about.
     */
    private static void aNeglectedHabitatGetsColdEnoughToHurt(GameTestHelper helper) {
        double sustainable = Hypothermia.sustainableAmbientK(false, 1.0);
        BlockPos seed = new BlockPos(3, 2, 3);

        // Buried and bare-plated: the middle row of the design's table, and the build a
        // player most plausibly ends up living in.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int ring = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                    helper.setBlock(seed.offset(dx, dy, dz), ring <= 1
                            ? Blocks.AIR.defaultBlockState()
                            : ModBlocks.HULL_PLATE.get().defaultBlockState());
                }
            }
        }

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(seed));
        RoomState room = atmosphere.roomAt(helper.absolutePos(seed));
        if (room == null) {
            helper.fail("Setup failed: the habitat is not a room");
            return;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        room.addGasAt(Gas.OXYGEN, 200, 293.15);
        room.setTemperatureK(293.15);

        if (!(room.temperatureK() > sustainable)) {
            helper.fail("A habitat at shirtsleeve temperature is already below the "
                    + sustainable + " K a clothed person can sustain, so this test starts"
                    + " where it was meant to finish");
            return;
        }

        // Nobody home and nothing running. Long enough to be neglect rather than a moment.
        for (int i = 0; i < 4000; i++) {
            atmosphere.tick();
        }

        if (!(room.temperatureK() < sustainable)) {
            helper.fail("An abandoned bare-plate habitat only fell to " + room.temperatureK()
                    + " K against the " + sustainable + " K a clothed person can sustain"
                    + " indefinitely - so cold never reaches the player and the whole"
                    + " hypothermia model is unreachable code");
            return;
        }
        if (room.temperatureK() < play.xponer.astronima.sim.thermal.HeatBalance.SKY_K + 1) {
            helper.fail("The room fell to " + room.temperatureK() + " K, which is the sky"
                    + " itself - something is not conserving anything");
            return;
        }
        helper.succeed();
    }


    /**
     * <em>"I overcooked the chlorate and now my habitat is poisoned forever."</em>
     *
     * <p><strong>The row an entire audit was opened for.</strong> The mod models eleven gases
     * and, before this valve, exactly two of them could be got back out of a room — carbon
     * dioxide by the scrubber and water by the dehumidifier. Carbon monoxide, methane, hydrogen
     * sulfide, sulfur dioxide, ammonia, hydrogen and chlorine all had toxicity thresholds, an
     * ailment naming them, and a readout on the analyzer, and no remedy whatever. Rule 7 in the
     * letter and broken in the spirit: told exactly what is killing you, given nothing to do.
     *
     * <p>So the claim is narrow and total: <strong>opening the valve takes the poison out.</strong>
     * The price is asserted beside it, because a remedy that cost nothing would not be a
     * decision — the oxygen goes with it, and after the oxygen tier an atmosphere is worth
     * roughly twenty chlorate crystals.
     *
     * <p><strong>Driven by the world's own ticker, not by calling the block entity.</strong>
     * The first draft looped {@code serverTick} four hundred times inside one game tick, and
     * the block entity — like every machine here — only acts on the atmosphere's cadence, so
     * every one of those four hundred calls returned immediately. It failed looking exactly
     * like a broken valve. Rule 13's second obligation is <em>same rate</em>, and this is what
     * it costs to ignore it.
     */
    private static void purgingARoomTakesThePoisonOut(GameTestHelper helper) {
        BlockPos inside = new BlockPos(2, 2, 2);
        ModTestFunctions.buildBoxAround(helper, inside);

        BlockPos valvePos = inside.offset(0, 0, -2);
        helper.setBlock(valvePos, ModBlocks.PURGE_VALVE.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(PurgeValveBlock.OPEN, false));

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(inside));
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        if (room == null) {
            helper.fail("Setup failed: the box is not a room");
            return;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        room.addGasAt(Gas.OXYGEN, 200, 293.15);
        room.addGasAt(Gas.CHLORINE, 2.0, 293.15);
        double poisonBefore = room.gases().get(Gas.CHLORINE);
        double airBefore = room.gases().get(Gas.OXYGEN);

        // A shut valve must hold. Checked after real ticks have passed, so "nothing happened"
        // means the valve did nothing rather than that nothing ran.
        helper.runAfterDelay(40, () -> {
            if (Math.abs(room.gases().get(Gas.CHLORINE) - poisonBefore) > 1e-9) {
                helper.fail("A SHUT purge valve emptied the room by itself, so a habitat"
                        + " cannot hold pressure with one fitted");
                return;
            }
            helper.setBlock(valvePos, helper.getBlockState(valvePos)
                    .setValue(PurgeValveBlock.OPEN, true));
        });

        helper.succeedWhen(() -> {
            double poison = room.gases().get(Gas.CHLORINE);
            double air = room.gases().get(Gas.OXYGEN);
            helper.assertTrue(poison < poisonBefore * 0.05,
                    "purging left " + poison + " mol of chlorine of the original "
                            + poisonBefore + " - the only remedy in the game for nine of"
                            + " eleven gases does not actually remove them");
            helper.assertTrue(air < airBefore * 0.05,
                    "the poison went and " + air + " mol of oxygen stayed, out of " + airBefore
                            + " - a purge that keeps your air is not a decision, it is a"
                            + " button");
        });
    }

    /**
     * <em>"I fitted it in an inside wall and it does nothing."</em>
     *
     * <p>Rule 18: the error path must be the loudest path. A valve with no vacuum on its far
     * side can do nothing whatever, and one that sat there reading <em>open</em> while the
     * pressure did not move would be indistinguishable from a broken block — sending the
     * player to look for the fault anywhere except the wall they chose.
     *
     * <p>This asserts the half a gametest can reach: that it refuses to vent, over real ticks,
     * with a valve that is genuinely open. That it also <em>says</em> so is a chat message on
     * the interaction path, which needs a real player (rule 14) and is covered by the language
     * key plus `runClient`.
     */
    private static void aPurgeValveWithNoVacuumOutsideRefuses(GameTestHelper helper) {
        BlockPos inside = new BlockPos(2, 2, 2);
        ModTestFunctions.buildBoxAround(helper, inside);
        BlockPos valvePos = inside.offset(0, 0, -2);
        helper.setBlock(valvePos, ModBlocks.PURGE_VALVE.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(PurgeValveBlock.OPEN, true));
        // Solid rock right up against its outward face: nowhere to dump to.
        helper.setBlock(valvePos.north(), ModBlocks.HULL_PLATE.get().defaultBlockState());

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(inside));
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        if (room == null) {
            helper.fail("Setup failed: the box is not a room");
            return;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        room.addGasAt(Gas.OXYGEN, 200, 293.15);
        double before = room.gases().get(Gas.OXYGEN);

        // The sibling scenario proves an open valve on vacuum empties a room in far less than
        // this, so an unchanged room here is a refusal rather than a slow start.
        helper.runAfterDelay(200, () -> {
            double now = room.gases().get(Gas.OXYGEN);
            if (Math.abs(now - before) > 1e-9) {
                helper.fail("A purge valve opening onto solid rock vented " + (before - now)
                        + " mol anyway - so the air went somewhere that is not space, and an"
                        + " interior valve is a hole in the game");
                return;
            }
            helper.succeed();
        });
    }


    /**
     * <em>"I put the vent in the wall between the two modules."</em>
     *
     * <p>The other way to get this wrong, and the worse one. A valve opening onto rock does
     * nothing, which is merely useless; a valve opening onto <strong>the room next door</strong>
     * would work perfectly and move the poison into the habitat where the rest of your things
     * are. That is the failure that turns a remedy into a way of losing twice.
     *
     * <p>Written because the sibling scenario does not reach this branch: a rock face is caught
     * by the block in front being solid, long before anything asks the atmosphere what is
     * behind it. Mutating the reading check produced no failure at all until this existed —
     * which is the vacuous-coverage trap rule 12 keeps catching in this file.
     */
    private static void aPurgeValveOntoAnotherHabitatRefuses(GameTestHelper helper) {
        // Two boxes sharing one wall, so the valve's outward face opens into a sealed room.
        BlockPos left = new BlockPos(2, 2, 2);
        BlockPos right = left.offset(0, 0, 4);
        ModTestFunctions.buildBoxAround(helper, left);
        ModTestFunctions.buildBoxAround(helper, right);

        // The shared wall, with the valve in it facing the neighbour.
        BlockPos valvePos = left.offset(0, 0, 2);
        helper.setBlock(valvePos, ModBlocks.PURGE_VALVE.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH)
                .setValue(PurgeValveBlock.OPEN, true));

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(left));
        atmosphere.invalidate(helper.absolutePos(right));
        RoomState here = atmosphere.roomAt(helper.absolutePos(left));
        RoomState neighbour = atmosphere.roomAt(helper.absolutePos(right));
        if (here == null || neighbour == null || here.id() == neighbour.id()) {
            helper.fail("Setup failed: expected two separate rooms, got here=" + here
                    + " neighbour=" + neighbour);
            return;
        }
        for (RoomState room : new RoomState[] {here, neighbour}) {
            for (Gas gas : Gas.values()) {
                room.removeGas(gas, room.gases().get(gas));
            }
            room.addGasAt(Gas.OXYGEN, 200, 293.15);
        }
        here.addGasAt(Gas.CHLORINE, 2.0, 293.15);
        double poisonHere = here.gases().get(Gas.CHLORINE);
        double poisonNext = neighbour.gases().get(Gas.CHLORINE);

        helper.runAfterDelay(200, () -> {
            if (neighbour.gases().get(Gas.CHLORINE) > poisonNext + 1e-9) {
                helper.fail("Purging pushed " + (neighbour.gases().get(Gas.CHLORINE)
                        - poisonNext) + " mol of chlorine into the habitat next door - the"
                        + " valve is not a remedy, it is a way of poisoning two rooms");
                return;
            }
            if (Math.abs(here.gases().get(Gas.CHLORINE) - poisonHere) > 1e-9) {
                helper.fail("A valve opening onto a sealed room vented anyway; the poison"
                        + " went somewhere, and the only somewhere is a place people live");
                return;
            }
            helper.succeed();
        });
    }


    /**
     * <em>"I put the crusher on power and the habitat started cooking."</em>
     *
     * <p><strong>The row this whole tier turns on.</strong> On Earth waste heat is ignorable
     * because air carries it away; in vacuum the only way out is radiating, which is why the
     * ISS carries acres of radiator and why its hard problem is cooling rather than power.
     *
     * <p>So v0.4 spent itself teaching the player to fight <em>for</em> heat — bury it,
     * insulate it, work in it — and this tier inverts that fight without changing a single
     * instrument. If drawing power did not heat the room, this would be Forge Energy with SI
     * labels on it, and every one of those decisions would stay pointed one way.
     *
     * <p>Two runs in the same cubic metres, one machine, one difference: whether there is a
     * charged cell against it.
     */
    private static void aPoweredMachineHeatsTheRoomItIsIn(GameTestHelper helper) {
        double unpowered = runCrusherFor(helper, false);
        double powered = runCrusherFor(helper, true);

        if (!(powered > unpowered)) {
            helper.fail("A crusher running on a cell left the room at " + powered
                    + " K against " + unpowered + " K unpowered - power arrives with no heat,"
                    + " so the thermal tier never inverts and this is a resource bar");
            return;
        }
        helper.succeed();
    }

    /**
     * Builds the same buried, insulated room and crusher, runs it, and returns the room's
     * temperature.
     *
     * <p><strong>Buried and insulated on purpose, and the first draft was neither.</strong> An
     * exposed 3×3×3 box radiates about eight kilowatts at shirtsleeve temperature, against
     * which a 250 W machine moves the temperature by four hundredths of a kelvin — so the
     * first version of this scenario compared two numbers that were the same to five decimal
     * places and passed whatever the code did. Mutating the waste heat to zero produced no
     * failure at all, which is how it was caught.
     *
     * <p>Buried and insulated the room loses 216 W, so the machine is more than the whole
     * budget — which is not a contrivance to make the test work. It is the design's actual
     * claim: <em>a well-insulated habitat is the worst possible place to run a machine.</em>
     *
     * @param powered whether a charged cell is bolted to the machine
     */
    private static double runCrusherFor(GameTestHelper helper, boolean powered) {
        BlockPos inside = new BlockPos(3, 2, 3);
        // Interior, insulated shell, then rock behind it: the warmest build in the game.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int ring = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                    helper.setBlock(inside.offset(dx, dy, dz), ring <= 1
                            ? Blocks.AIR.defaultBlockState()
                            : ring == 2
                                    ? ModBlocks.INSULATED_HULL_PLATE.get().defaultBlockState()
                                    : ModBlocks.HULL_PLATE.get().defaultBlockState());
                }
            }
        }
        BlockPos crusherPos = inside.offset(1, 0, 0);
        BlockPos cellPos = inside.offset(1, 1, 0);
        helper.setBlock(crusherPos, ModBlocks.ORE_CRUSHER.get().defaultBlockState());
        // The cell is present in BOTH runs and only charged in one. It is a solid block, so
        // placing it in only the powered run made that room one cell smaller - less thermal
        // mass, faster cooling - and the powered room came out *colder*. Same trap as the
        // thermal phase's overlapping boxes: identical geometry, one difference.
        helper.setBlock(cellPos, ModBlocks.POWER_CELL.get().defaultBlockState());

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(inside));
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        var crusher = helper.getBlockEntity(crusherPos, OreCrusherBlockEntity.class);
        if (room == null || crusher == null) {
            helper.fail("Setup failed: room=" + room + " crusher=" + crusher);
            return 0;
        }
        var cell = helper.getBlockEntity(cellPos, PowerCellBlockEntity.class);
        if (cell == null) {
            helper.fail("Setup failed: no power cell");
            return 0;
        }
        cell.draw(cell.storedJ());
        if (powered) {
            cell.charge(PowerCellBlockEntity.CAPACITY_J);
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        room.addGasAt(Gas.OXYGEN, 200, 293.15);
        room.setTemperatureK(293.15);
        crusher.setItem(0, new ItemStack(ModItems.METAL_RICH_ORE.get(), 64));

        // Nobody cranking: the only work being done is whatever the cell pays for.
        for (int i = 0; i < 600; i++) {
            crusher.serverTick();
            atmosphere.tick();
        }
        return room.temperatureK();
    }

    /**
     * <em>"Why is the array making nothing?"</em>
     *
     * <p>Because something is over it. A panel's placement problem is the retort's — open sky —
     * and deliberately the same one, because it is the same physical fact. A player who roofs
     * one over should not be surprised by the other.
     *
     * <p>Also asserts the number, because the number <em>is</em> the phase: a panel makes about
     * as much as a bright lamp, and if one panel ever runs a machine then the belt's feebleness
     * has gone and with it the whole motivation for fuel cells and an RTG.
     */
    private static void aRoofedSolarArrayMakesNothing(GameTestHelper helper) {
        BlockPos openPos = new BlockPos(1, 3, 1);
        BlockPos roofedPos = new BlockPos(5, 3, 1);
        helper.setBlock(openPos, ModBlocks.SOLAR_ARRAY.get().defaultBlockState());
        helper.setBlock(roofedPos, ModBlocks.SOLAR_ARRAY.get().defaultBlockState());
        helper.setBlock(roofedPos.above(), ModBlocks.HULL_PLATE.get().defaultBlockState());

        var open = helper.getBlockEntity(openPos, SolarArrayBlockEntity.class);
        var roofed = helper.getBlockEntity(roofedPos, SolarArrayBlockEntity.class);
        if (open == null || roofed == null) {
            helper.fail("Setup failed: open=" + open + " roofed=" + roofed);
            return;
        }

        helper.runAfterDelay(20, () -> {
            if (!(open.watts() > 0)) {
                helper.fail("A panel under open sky made " + open.watts() + " W - either it is"
                        + " night in this test or the array never asks about the sun, and"
                        + " nothing below means anything");
                return;
            }
            if (roofed.watts() > 0) {
                helper.fail("A panel with a hull plate on top of it made " + roofed.watts()
                        + " W - roofing an array over costs nothing, so its placement is not a"
                        + " decision");
                return;
            }
            if (open.watts() > HeatBalance.WORKED_MACHINE_W / 4) {
                helper.fail("One panel makes " + open.watts() + " W against a machine's "
                        + HeatBalance.WORKED_MACHINE_W + " - solar in the belt is not feeble"
                        + " any more, and the reason for the rest of the power chain has gone");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * <em>"The cell charged itself off a panel that was in the dark."</em>
     *
     * <p>Joules have to come from somewhere, and this is the shape of bug that ends with a base
     * running forever on one panel. Asserts the whole chain a player builds — sun into panel,
     * panel into cell — and that a cell with no lit panel beside it gains nothing.
     */
    private static void anArrayChargesOnlyTheCellBesideItAndOnlyInSun(GameTestHelper helper) {
        BlockPos arrayPos = new BlockPos(1, 3, 1);
        BlockPos wiredCell = arrayPos.east();
        // Four blocks away and squarely in line with the array, so a lookup that searched
        // along an axis instead of at the neighbour would find it. Placed off to one side
        // the first time, where no plausible mistake reached it - and mutating the lookup
        // produced no failure at all, which is what a scenario proving nothing looks like.
        BlockPos loneCell = arrayPos.south(4);
        helper.setBlock(arrayPos, ModBlocks.SOLAR_ARRAY.get().defaultBlockState());
        helper.setBlock(wiredCell, ModBlocks.POWER_CELL.get().defaultBlockState());
        helper.setBlock(loneCell, ModBlocks.POWER_CELL.get().defaultBlockState());

        var wired = helper.getBlockEntity(wiredCell, PowerCellBlockEntity.class);
        var lone = helper.getBlockEntity(loneCell, PowerCellBlockEntity.class);
        if (wired == null || lone == null) {
            helper.fail("Setup failed: wired=" + wired + " lone=" + lone);
            return;
        }

        helper.runAfterDelay(60, () -> {
            if (!(wired.storedJ() > 0)) {
                helper.fail("A cell touching a lit array gained nothing, so an array with"
                        + " nowhere to put its energy is the only kind there is");
                return;
            }
            if (lone.storedJ() > 0) {
                helper.fail("A cell with no array anywhere near it gained "
                        + lone.storedJ() + " J - energy is arriving from nowhere");
                return;
            }
            helper.succeed();
        });
    }


    /**
     * <em>"I ran the cable across the surface and the cell fills half as fast."</em>
     *
     * <p><strong>The row P7 turns on, and both halves of it matter.</strong> A cable that simply
     * delivered less would be a tax — a number shaved off for no reason a player can point at.
     * What makes it a mechanic is that the difference is not gone: it is <em>heat, in the
     * corridor the cable runs through</em>, which is the same law this whole tier is built on
     * applied to a wire. And in a game about fighting cold, that makes "where do I want the
     * loss" a placement decision rather than a penalty.
     *
     * <p>Two runs from the same array, one block against fifteen, into identical cells.
     */
    private static void aLongCableRunDeliversLessAndHeatsTheCorridor(GameTestHelper helper) {
        double shortRun = chargeThroughCable(helper, 1);
        double longRun = chargeThroughCable(helper, 15);

        if (!(shortRun > 0)) {
            helper.fail("A cell on a one-block run gained nothing, so nothing below this"
                    + " means anything - is the array lit?");
            return;
        }
        if (!(longRun < shortRun)) {
            helper.fail("A fifteen-block run delivered " + longRun + " J against a one-block"
                    + " run's " + shortRun + " J - distance costs nothing, so where the array"
                    + " goes is not a decision");
            return;
        }
        if (!(longRun > shortRun * 0.5)) {
            helper.fail("A fifteen-block run delivered only " + longRun + " J of the short"
                    + " run's " + shortRun + " - that is punitive rather than a trade, and"
                    + " nobody will ever lay a cable");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"The same wire carries more inside than out."</em>
     *
     * <p><strong>The proof {@code design/electrical.md} names for E7</strong>, and the claim the
     * whole asteroid identity of this tier rests on (§2.1): a wire sheds heat by radiating and —
     * only if there is any air — by convection, so <em>a run through your workshop and the
     * identical run across the surface are different components.</em> No other mod can say that
     * because no other mod knows where the air is.
     *
     * <p>Driven from a <strong>real sealed habitat</strong> rather than from two numbers, because
     * the half that can rot is the seam: the room has to exist, hold pressure, and report it. The
     * arithmetic on top of that is {@code CoolingTest}'s job.
     *
     * <p><strong>What is deliberately not staged (rule 14):</strong> the meter itself. The goggles
     * are a client HUD reading a packet, and no gametest draws a frame — so *that the numbers are
     * shown* is owed to {@code runClient} and listed in PLAYTEST. What is mechanised here is that
     * the numbers differ at all, which is the part that would silently stop being true.
     */
    private static void aWireIsRatedForWhereItLies(GameTestHelper helper) {
        BlockPos inside = new BlockPos(2, 2, 2);
        ModTestFunctions.buildBoxAround(helper, inside);

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        if (room == null) {
            helper.fail("the sealed box did not become a room, so there is no air to test with");
            return;
        }
        // Enough to actually pressurise it: 27 m3 at 293 K needs about 1120 mol for one
        // atmosphere, and a habitat at a quarter of one would not be a fair test of air.
        room.addGasAt(Gas.OXYGEN, 1120, 293.15);
        if (room.pressureKPa() < 50) {
            helper.fail("the habitat only reached " + Math.round(room.pressureKPa())
                    + " kPa - a wire in it would barely be better cooled than one outside");
            return;
        }

        // One metre of the wire the whole tier is costed against, at a current a real machine run
        // reaches, in both places.
        var metre = new play.xponer.astronima.sim.circuit.Conductor(
                play.xponer.astronima.sim.circuit.ConductorMaterial.IRON,
                play.xponer.astronima.sim.circuit.Conductor.mm2(
                        play.xponer.astronima.sim.circuit.Conductor.STANDARD_MM2), 1.0);
        double amps = 10;
        var indoors = play.xponer.astronima.sim.circuit.Cooling.of(
                metre, amps, room.pressureKPa(), room.temperatureK());
        var outdoors = play.xponer.astronima.sim.circuit.Cooling.inVacuum(metre, amps);

        // What the meter puts on its own line: how much this wire may carry *here*.
        if (!(indoors.ratingAmps() > outdoors.ratingAmps() * 1.3)) {
            helper.fail("the habitat rates the wire at " + String.format("%.1f", indoors.ratingAmps())
                    + " A and vacuum at " + String.format("%.1f", outdoors.ratingAmps())
                    + " - air has stopped being worth anything, and with it the one thing that"
                    + " makes this an asteroid's electrical system");
            return;
        }

        // And the temperature, compared LIKE WITH LIKE.
        //
        // This is where the scenario caught its own author. Asked against the real surroundings
        // the two came out at the same 320 K, and that is not a bug: the asteroid outside is 200 K
        // and the habitat is 293 K, so the better cooling indoors is very nearly cancelled by the
        // colder start outdoors. Holding the ambient equal is what isolates the thing being
        // claimed — the same trap PLAN rule 13 names as "symmetry in measurements", which had
        // already produced one test that was flaky one run in three.
        var sameAmbientInAir = play.xponer.astronima.sim.circuit.Cooling.of(
                metre, amps, room.pressureKPa(), room.temperatureK());
        var sameAmbientInVacuum = play.xponer.astronima.sim.circuit.Cooling.of(
                metre, amps, 0, room.temperatureK());
        if (!(sameAmbientInAir.wireK() < sameAmbientInVacuum.wireK() - 20)) {
            helper.fail("at " + amps + " A and the same surroundings the wire reads "
                    + Math.round(sameAmbientInAir.wireK()) + " K in air and "
                    + Math.round(sameAmbientInVacuum.wireK()) + " K in vacuum - it cannot tell"
                    + " whether there is anything around it to carry the heat away");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"What does a run of cable actually resist?"</em>
     *
     * <p><strong>The number, not the shape of the number.</strong> {@code design/electrical.md}
     * §3.1 publishes it: iron at the standard 4 mm² gauge is <strong>0.0243 Ω per block</strong>,
     * which is ρL/A and nothing else. The legacy cable used to give up a flat one per cent per
     * block instead — the figure that document opens by calling <em>"a game decision instead of a
     * law"</em> and promising to delete.
     *
     * <p>Asserted against the published figure rather than against the code's own expression of
     * it, because the two have to be checked against <em>something outside themselves</em>: a test
     * that recomputed the answer the same way would pass just as happily on a percentage renamed.
     * That is exactly what happened — the first guard here only checked that the old class was
     * gone, and a mutation putting {@code 0.01 * length} back stayed green.
     */
    private static void aLegacyCableObeysTheConductorLaw(GameTestHelper helper) {
        BlockPos start = new BlockPos(1, 1, 1);
        int blocks = 8;
        BlockPos cursor = start;
        for (int i = 0; i < blocks; i++) {
            helper.setBlock(cursor, ModBlocks.POWER_CABLE.get().defaultBlockState());
            cursor = cursor.east();
        }
        helper.setBlock(cursor, ModBlocks.POWER_CELL.get().defaultBlockState());

        var run = play.xponer.astronima.power.CableNetworks.resolveFrom(
                helper.getLevel(), helper.absolutePos(start));
        if (run == null || run.length() != blocks) {
            helper.fail("the test could not lay its own cable run: " + run);
            return;
        }

        double ohms = play.xponer.astronima.power.PowerRun.resistanceOhms(run);
        double published = 0.0243 * blocks;      // design/electrical.md §3.1, iron at 4 mm²
        if (Math.abs(ohms - published) > published * 0.03) {
            helper.fail("a " + blocks + "-block run resists " + String.format("%.4f", ohms)
                    + " ohms; iron at the standard gauge is " + String.format("%.4f", published)
                    + ". Either the metal, the area or the length has stopped being real - and a"
                    + " percentage wearing a new name would read exactly like this");
            return;
        }
        helper.succeed();
    }

    /**
     * Lays an array, {@code blocks} of cable and a cell in a sealed corridor, runs it, and
     * returns how much energy reached the cell.
     */
    private static double chargeThroughCable(GameTestHelper helper, int blocks) {
        BlockPos arrayPos = new BlockPos(1, 4, 1);
        helper.setBlock(arrayPos, ModBlocks.SOLAR_ARRAY.get().defaultBlockState());
        BlockPos cursor = arrayPos;
        for (int i = 0; i < blocks; i++) {
            cursor = cursor.east();
            helper.setBlock(cursor, ModBlocks.POWER_CABLE.get().defaultBlockState());
        }
        BlockPos cellPos = cursor.east();
        helper.setBlock(cellPos, ModBlocks.POWER_CELL.get().defaultBlockState());
        // Anything beyond, from the previous run, must not still be wired in.
        helper.setBlock(cellPos.east(), Blocks.AIR.defaultBlockState());

        var cell = helper.getBlockEntity(cellPos, PowerCellBlockEntity.class);
        var array = helper.getBlockEntity(arrayPos, SolarArrayBlockEntity.class);
        if (cell == null || array == null) {
            helper.fail("Setup failed: cell=" + cell + " array=" + array);
            return 0;
        }
        cell.draw(cell.storedJ());
        for (int i = 0; i < 100; i++) {
            array.serverTick(helper.getLevel(), helper.absolutePos(arrayPos));
        }
        return cell.storedJ();
    }


    /**
     * <em>"I ran the generator overnight and woke up unable to breathe."</em>
     *
     * <p><strong>The row that makes this a machine to plan around rather than a fuel-to-watts
     * converter.</strong> It takes two moles of your oxygen per mole of fuel and hands back
     * carbon dioxide for the scrubber to eat — and after the oxygen tier both of those are
     * expensive. An engine that made power out of methane and returned nothing would have a
     * chemistry-flavoured name and no consequences.
     */
    private static void aGeneratorBurnsYourOxygenAndReturnsExhaust(GameTestHelper helper) {
        BlockPos inside = new BlockPos(3, 2, 3);
        // Every block placed BEFORE the room is scanned. Placing the cell afterwards rescanned
        // the room and left the captured RoomState an orphan: the new room took its gas in
        // proportion, so both species fell by the same 96 % and it read exactly like an engine
        // burning furiously. Two scenarios failed blaming the engine before that was spotted.
        BlockPos cellPos = inside.offset(1, 1, 0);
        BlockPos generatorPos = sealedGeneratorRoom(helper, inside);

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        var generator = helper.getBlockEntity(generatorPos,
                CombustionGeneratorBlockEntity.class);
        var cell = helper.getBlockEntity(cellPos, PowerCellBlockEntity.class);
        if (room == null || generator == null || cell == null) {
            helper.fail("Setup failed: room=" + room + " generator=" + generator
                    + " cell=" + cell);
            return;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        room.addGasAt(Gas.OXYGEN, 200, 293.15);
        room.addGasAt(Gas.METHANE, 20, 293.15);
        cell.draw(cell.storedJ());

        long roomId = room.id();
        double fuelBefore = room.gases().get(Gas.METHANE);
        double oxygenBefore = room.gases().get(Gas.OXYGEN);
        double co2Before = room.gases().get(Gas.CARBON_DIOXIDE);

        helper.runAfterDelay(100, () -> {
            // Asked for again rather than held, so a rescan cannot leave this reading a room
            // that no longer exists.
            RoomState now = atmosphere.roomAt(helper.absolutePos(inside));
            if (now == null || now.id() != roomId) {
                helper.fail("The room was replaced mid-scenario (was #" + roomId + ", now "
                        + (now == null ? "gone" : "#" + now.id()) + ") - anything measured"
                        + " across that is two different rooms compared with each other");
                return;
            }
            double burnt = fuelBefore - now.gases().get(Gas.METHANE);
            double breathed = oxygenBefore - now.gases().get(Gas.OXYGEN);
            double exhaled = now.gases().get(Gas.CARBON_DIOXIDE) - co2Before;

            if (!(burnt > 0)) {
                helper.fail("The generator burnt no methane at all in a room full of it,"
                        + " state=" + generator.stall());
                return;
            }
            if (!(cell.storedJ() > 0)) {
                helper.fail("It burnt " + burnt + " mol of fuel and the cell beside it gained"
                        + " nothing - the power goes nowhere");
                return;
            }
            if (Math.abs(breathed - burnt * 2) > burnt * 0.1) {
                helper.fail("It burnt " + burnt + " mol of methane on " + breathed
                        + " mol of oxygen - CH4 + 2 O2 needs twice the fuel, so the"
                        + " stoichiometry is not being charged for");
                return;
            }
            if (Math.abs(exhaled - burnt) > burnt * 0.1) {
                helper.fail("It burnt " + burnt + " mol of fuel and returned " + exhaled
                        + " mol of carbon dioxide - the exhaust does not match the fuel, so"
                        + " the scrubber is being told the wrong story");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * <em>"Why is the generator room the hottest place on the rock?"</em>
     *
     * <p>Because three quarters of any fuel's energy comes out as heat — 750 W at full output
     * against a machine's 250. That is not a balance choice; it is why cogeneration exists, and
     * it puts this tier's law at its most extreme. In a cold habitat the generator answers two
     * problems at once; in a warm one it is the thing that cooks you.
     *
     * <p>Buried and insulated, because that is where the claim is legible — and because it is
     * the design's actual sentence: <em>put it where you want the warmth.</em>
     */
    private static void aGeneratorIsMostlyAStove(GameTestHelper helper) {
        BlockPos inside = new BlockPos(3, 2, 3);
        BlockPos generatorPos = sealedGeneratorRoom(helper, inside);

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        if (room == null) {
            helper.fail("Setup failed: the box is not a room");
            return;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        room.addGasAt(Gas.OXYGEN, 400, 293.15);
        room.setTemperatureK(293.15);

        // Unfuelled first, then fuelled, in the same cubic metres and on the world's own
        // ticker. Driving the block entity by hand would run every call inside one game tick
        // and be gated out by its own cadence - the mistake the purge valve already taught.
        var generator = helper.getBlockEntity(inside.offset(1, 0, 0),
                CombustionGeneratorBlockEntity.class);
        if (generator == null) {
            helper.fail("Setup failed: no generator");
            return;
        }
        BlockPos absolute = helper.absolutePos(inside.offset(1, 0, 0));

        // Driven by hand, like the crusher's heat scenario: a habitat's shell is 632 kJ/K, so
        // 750 W moves it a kelvin in fourteen minutes and no amount of *real* ticking inside a
        // gametest's twenty-second budget will ever show it. Stepping the atmosphere directly
        // buys three hundred seconds of simulated time in a few hundred iterations.
        for (int i = 0; i < 600; i++) {
            generator.serverTick(helper.getLevel(), absolute);
            atmosphere.tick();
        }
        double idle = room.temperatureK();

        room.setTemperatureK(293.15);
        room.addGasAt(Gas.METHANE, 40, 293.15);
        for (int i = 0; i < 600; i++) {
            generator.serverTick(helper.getLevel(), absolute);
            atmosphere.tick();
        }
        double burning = room.temperatureK();

        if (!(burning > idle + 0.01)) {
            helper.fail("A generator burning at full output left the room at " + burning
                    + " K against " + idle + " K idle - three quarters of the fuel's energy"
                    + " is going nowhere, and the machine is a fuel-to-watts converter");
            return;
        }
        helper.succeed();
    }

    /**
     * A buried, insulated room with a generator in it, and the assertion that it is sealed.
     *
     * <p>The sealing check is not ceremony. The first draft of these scenarios used an ordinary
     * shelled box that turned out to be venting, and the symptom was <strong>every mole of fuel
     * and oxygen vanishing in five seconds</strong> — which reads exactly like a generator
     * burning furiously rather than like a room emptying into space. Both scenarios failed
     * blaming the engine.
     *
     * @return where the generator was placed
     */
    private static BlockPos sealedGeneratorRoom(GameTestHelper helper, BlockPos inside) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int ring = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                    helper.setBlock(inside.offset(dx, dy, dz), ring <= 1
                            ? Blocks.AIR.defaultBlockState()
                            : ring == 2
                                    ? ModBlocks.INSULATED_HULL_PLATE.get().defaultBlockState()
                                    : ModBlocks.HULL_PLATE.get().defaultBlockState());
                }
            }
        }
        BlockPos generatorPos = inside.offset(1, 0, 0);
        helper.setBlock(generatorPos,
                ModBlocks.COMBUSTION_GENERATOR.get().defaultBlockState());
        // The cell goes in here too, before the scan: placing it afterwards rescanned the room
        // and orphaned the RoomState the scenario was holding.
        helper.setBlock(inside.offset(1, 1, 0),
                ModBlocks.POWER_CELL.get().defaultBlockState());

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(inside));
        Atmosphere.RoomReading reading =
                atmosphere.readingAt(helper.absolutePos(inside));
        if (reading == null || !reading.sealed()) {
            helper.fail("Setup failed: the generator room is not sealed, so anything it"
                    + " appears to consume is really venting to space");
        }
        return generatorPos;
    }


    /**
     * <em>"It is full of hydrogen and it will not start."</em>
     *
     * <p>Then it is short of oxygen — and those are <strong>completely different journeys</strong>.
     * One is a trip into the deep rock for a pocket that is 12 % of what is down there; the other
     * is a valve on a tank you already own. Rule 18: a machine that says only "stopped" sends the
     * player on the wrong one, and this one is a long walk.
     *
     * <p>The same run also proves the exchange itself: both gases come out of the vessels, water
     * goes into the room for the dehumidifier, and the power reaches a cell.
     */
    private static void aFuelCellNamesWhichFeedIsEmpty(GameTestHelper helper) {
        BlockPos inside = new BlockPos(3, 2, 3);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int ring = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                    helper.setBlock(inside.offset(dx, dy, dz), ring <= 1
                            ? Blocks.AIR.defaultBlockState()
                            : ModBlocks.HULL_PLATE.get().defaultBlockState());
                }
            }
        }
        BlockPos cellPos = inside.offset(1, 0, 0);
        BlockPos fuelTank = inside.offset(1, 1, 0);
        BlockPos airTank = inside.offset(1, -1, 0);
        // Bolted to the cell, not merely near it: there are no cables in this scenario, so a
        // diagonal neighbour is not connected to anything.
        BlockPos batteryPos = cellPos.south();
        helper.setBlock(cellPos, ModBlocks.FUEL_CELL.get().defaultBlockState());
        helper.setBlock(fuelTank, ModBlocks.GAS_TANK.get().defaultBlockState());
        helper.setBlock(airTank, ModBlocks.GAS_TANK.get().defaultBlockState());
        helper.setBlock(batteryPos, ModBlocks.POWER_CELL.get().defaultBlockState());

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(inside));
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        var cell = helper.getBlockEntity(cellPos, FuelCellBlockEntity.class);
        var fuel = helper.getBlockEntity(fuelTank, GasTankBlockEntity.class);
        var air = helper.getBlockEntity(airTank, GasTankBlockEntity.class);
        var battery = helper.getBlockEntity(batteryPos, PowerCellBlockEntity.class);
        if (room == null || cell == null || fuel == null || air == null || battery == null) {
            helper.fail("Setup failed: room=" + room + " cell=" + cell + " fuel=" + fuel
                    + " air=" + air + " battery=" + battery);
            return;
        }
        BlockPos absolute = helper.absolutePos(cellPos);

        // Hydrogen only: it must say so, and must not run.
        fuel.contents().addGasAt(Gas.HYDROGEN, 20, 293.15);
        for (int i = 0; i < 40; i++) {
            cell.serverTick(helper.getLevel(), absolute);
        }
        if (cell.stall() != FuelCell.Stall.NO_OXYGEN) {
            helper.fail("A cell with hydrogen and no oxygen reported " + cell.stall()
                    + " - the player is sent into the deep rock for a gas they already have");
            return;
        }

        // Now give it air. Same machine, one valve.
        air.contents().addGasAt(Gas.OXYGEN, 40, 293.15);
        double hydrogenBefore = fuel.contents().gases().get(Gas.HYDROGEN);
        double oxygenBefore = air.contents().gases().get(Gas.OXYGEN);
        double waterBefore = room.gases().get(Gas.WATER_VAPOR);
        battery.draw(battery.storedJ());

        for (int i = 0; i < 200; i++) {
            cell.serverTick(helper.getLevel(), absolute);
        }

        double usedFuel = hydrogenBefore - fuel.contents().gases().get(Gas.HYDROGEN);
        double usedAir = oxygenBefore - air.contents().gases().get(Gas.OXYGEN);
        double madeWater = room.gases().get(Gas.WATER_VAPOR) - waterBefore;

        if (cell.stall() != FuelCell.Stall.RUNNING || !(usedFuel > 0)) {
            helper.fail("With both feeds full the cell reported " + cell.stall()
                    + " and used " + usedFuel + " mol of hydrogen");
            return;
        }
        if (!(battery.storedJ() > 0)) {
            helper.fail("It consumed " + usedFuel + " mol of hydrogen and the battery bolted"
                    + " to it gained nothing - the power goes nowhere");
            return;
        }
        if (Math.abs(usedAir - usedFuel * 0.5) > usedFuel * 0.1) {
            helper.fail("H2 + 1/2 O2: it used " + usedFuel + " mol of hydrogen on " + usedAir
                    + " mol of oxygen, so the stoichiometry is not being charged for");
            return;
        }
        if (Math.abs(madeWater - usedFuel) > usedFuel * 0.1) {
            helper.fail("It made " + madeWater + " mol of water from " + usedFuel
                    + " mol of hydrogen - the exhaust does not match, so the dehumidifier is"
                    + " being told the wrong story");
            return;
        }
        helper.succeed();
    }


    /**
     * <em>"Why does mine need digging out and yours does not?"</em>
     *
     * <p><strong>The row that decides whether fouling is a mechanic or a chore.</strong> Taken
     * literally, PLAN's line — <em>machines accumulate waste and jam</em> — is a durability bar
     * with a shovel. What makes it something else is that the rate is set by decisions the
     * player already made: how much air they gave it, and which pocket they tapped. If a
     * careful engine fouls as fast as a careless one, the sludge carries no information.
     *
     * <p>Same engine, same fuel, same time. The only difference is a sulfide pocket in the
     * second room's air.
     */
    private static void sourGasFoulsAGeneratorFarFaster(GameTestHelper helper) {
        double clean = foulingAfterRunning(helper, false);
        double sour = foulingAfterRunning(helper, true);

        if (!(clean > 0)) {
            helper.fail("A generator running on clean gas fouled not at all, so maintenance is"
                    + " optional and only careless players ever meet the shovel");
            return;
        }
        if (!(sour > clean * 2)) {
            helper.fail("Sour gas fouled the engine to " + sour + " against clean gas's "
                    + clean + " - the sludge says nothing about how it was fed, so it is a"
                    + " timer rather than a consequence");
            return;
        }
        helper.succeed();
    }

    /**
     * Runs a generator in a sealed room for a fixed time and returns how choked it ended up.
     *
     * @param sour whether the room's air carries hydrogen sulfide, as a sulfide pocket would
     */
    private static float foulingAfterRunning(GameTestHelper helper, boolean sour) {
        BlockPos inside = new BlockPos(3, 2, 3);
        BlockPos generatorPos = sealedGeneratorRoom(helper, inside);

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        var generator = helper.getBlockEntity(generatorPos,
                CombustionGeneratorBlockEntity.class);
        if (room == null || generator == null) {
            helper.fail("Setup failed: room=" + room + " generator=" + generator);
            return 0;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        // Plenty of air in both, so richness is not what separates them - the only variable
        // is the sulfur.
        room.addGasAt(Gas.OXYGEN, 600, 293.15);
        room.addGasAt(Gas.METHANE, 40, 293.15);
        if (sour) {
            room.addGasAt(Gas.HYDROGEN_SULFIDE, 40, 293.15);
        }
        generator.shovelOut();

        BlockPos absolute = helper.absolutePos(generatorPos);
        for (int i = 0; i < 400; i++) {
            generator.serverTick(helper.getLevel(), absolute);
        }
        return generator.fouling();
    }

    /**
     * <em>"It has fuel, it has air, and it will not start."</em>
     *
     * <p>Then it is packed solid, and the fix is a shovel rather than a pipe. Rule 18: a machine
     * reporting the same "stopped" for all three faults sends the player to check plumbing that
     * is perfectly fine — and here that is a wasted trip to a gas tank across the base.
     */
    private static void aChokedGeneratorSaysSoAndAShovelFixesIt(GameTestHelper helper) {
        BlockPos inside = new BlockPos(3, 2, 3);
        BlockPos generatorPos = sealedGeneratorRoom(helper, inside);

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        var generator = helper.getBlockEntity(generatorPos,
                CombustionGeneratorBlockEntity.class);
        if (room == null || generator == null) {
            helper.fail("Setup failed: room=" + room + " generator=" + generator);
            return;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        // Everything it needs, and the filthiest charge there is.
        room.addGasAt(Gas.OXYGEN, 900, 293.15);
        room.addGasAt(Gas.METHANE, 200, 293.15);
        room.addGasAt(Gas.HYDROGEN_SULFIDE, 1800, 293.15);

        // Long, because the mechanic is meant to be: even at its filthiest an engine takes
        // about half an hour of running to choke, which is a maintenance interval rather than
        // a nuisance. Stepping the block directly buys that in a few tens of thousands of
        // iterations, none of which touch the world.
        BlockPos absolute = helper.absolutePos(generatorPos);
        for (int i = 0; i < 90_000; i++) {
            generator.serverTick(helper.getLevel(), absolute);
        }

        if (generator.stall() != CombustionEngine.Stall.FOULED) {
            helper.fail("After a long run on filthy gas the generator reports "
                    + generator.stall() + " at " + generator.fouling() + " fouling - a player"
                    + " with full tanks is being sent to check the plumbing");
            return;
        }
        int dug = generator.shovelOut();
        if (dug <= 0) {
            helper.fail("A generator choked solid yielded nothing to a shovel, so the fault it"
                    + " reports has no remedy at all");
            return;
        }
        if (generator.fouling() >= 1.0f) {
            helper.fail("Shovelling took " + dug + " units out and left it just as choked");
            return;
        }
        helper.succeed();
    }


    /**
     * <em>"Where did my iron go?"</em>
     *
     * <p>Nowhere — it never left. Iron pentacarbonyl needs far higher pressure to form, so at
     * these conditions the nickel goes and the iron stays, which is exactly why the Mond process
     * exists industrially. This machine is a <em>separator</em>, and both piles come out.
     *
     * <p>Driven with no setpoint call at all now, since the vessel chooses its own temperature
     * (see {@code CarbonylRefinerBlockEntity#setpointK()}). The old hazard this gametest used to
     * also drive by hand — a dial parked between the two temperatures, filling the room with
     * nickel carbonyl — cannot happen through the real machine any more: there is no dial. That
     * chemistry, and the claim that a clean cycle hands the carbon monoxide back, is still
     * proven directly, Minecraft-free and at a pace that does not fight the work-counter's own
     * clock, by {@code CarbonylTest}.
     */
    private static void refiningSeparatesTheNickelFromTheIron(GameTestHelper helper) {
        BlockPos inside = new BlockPos(3, 2, 3);
        BlockPos refinerPos = sealedRefinerRoom(helper, inside);

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        var refiner = helper.getBlockEntity(refinerPos, CarbonylRefinerBlockEntity.class);
        if (room == null || refiner == null) {
            helper.fail("Setup failed: room=" + room + " refiner=" + refiner);
            return;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        room.addGasAt(Gas.CARBON_MONOXIDE, 400, 293.15);
        refiner.setItem(CarbonylRefinerBlockEntity.SLOT_INPUT,
                new ItemStack(ModItems.IRON_NICKEL_GRAINS.get(), 8));

        for (int i = 0; i < CarbonylRefinerBlockEntity.BATCH_WORK * 3; i++) {
            refiner.crank();
            refiner.serverTick();
        }

        boolean nickel = false;
        boolean iron = false;
        for (int slot = 0; slot < refiner.getContainerSize(); slot++) {
            ItemStack stack = refiner.getItem(slot);
            nickel |= stack.is(ModItems.PURE_NICKEL.get());
            iron |= stack.is(net.minecraft.world.item.Items.IRON_INGOT);
        }
        if (!nickel || !iron) {
            helper.fail("A finished charge gave nickel=" + nickel + " iron=" + iron
                    + " - the Mond process is a separator, and both piles have to come out or"
                    + " the player has no idea what is in what");
            return;
        }
        helper.succeed();
    }

    /** A sealed room with a refiner in it, and the assertion that it really is sealed. */
    /**
     * The reactor's whole lesson, driven through the block the player operates (rule 13): the
     * drum has to actually reduce the charge it was given.
     *
     * <p>No spin call at all any more — {@code FluidizedBedBlockEntity#setting()} now matches its
     * own drum speed to whatever grind is in the feed
     * ({@code FluidizedBedControl#matchedDialFor}) — so this asserts the automatic run does what
     * a correctly-matched manual one used to: makes iron powder and titania <strong>and draws its
     * hydrogen down out of the room</strong>, the assertion that the reagent is spent, not
     * conjured, which is the contrast with the refiner one bench over. The old three-spin
     * comparison this gametest used to drive by hand — matched vs. too slow vs. too fast — can no
     * longer be forced through the real machine; that physics is still proven directly,
     * Minecraft-free, in {@code FluidizedBedTest} (including the new
     * {@code matchedDialAlwaysLandsInTheBoilingBand} guarding the automatic policy itself).
     */
    private static void theFluidizedBedMatchesItsOwnSpinToTheGrind(GameTestHelper helper) {
        BlockPos inside = new BlockPos(3, 2, 3);
        BlockPos bedPos = sealedBedRoom(helper, inside);

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        var bed = helper.getBlockEntity(bedPos, FluidizedBedBlockEntity.class);
        if (room == null || bed == null) {
            helper.fail("Setup failed: room=" + room + " bed=" + bed);
            return;
        }
        // A room full of hydrogen for the bed to reduce with, and nothing else.
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        room.addGasAt(Gas.HYDROGEN, 200, 293.15);

        double h2Before = room.gases().get(Gas.HYDROGEN);
        int[] made = runBedBatch(bed);
        double h2After = room.gases().get(Gas.HYDROGEN);

        if (!(h2After < h2Before)) {
            helper.fail("the bed did not draw its hydrogen down from the room (" + h2Before
                    + " -> " + h2After + ") - the reagent is being conjured rather than"
                    + " consumed, so the contrast with the refiner's returned carrier is gone");
            return;
        }
        if (made[0] <= 0 || made[1] <= 0) {
            helper.fail("the auto-matched drum made " + made[0] + " iron and " + made[1]
                    + " titania - a boiling bed is not reducing the charge");
            return;
        }
        helper.succeed();
    }

    /** ~120 µm feed, as a {@code GRIND_FINENESS} permille. */
    private static final int GRIND_120_MICRONS_PERMILLE = 611;

    /**
     * Runs one batch and returns how many iron-powder and titania items it added, exactly as a
     * hopper-fed player would: put crushed ilmenite in the feed and leave it to tick — no spin
     * call at all, since the drum now matches itself.
     */
    private static int[] runBedBatch(FluidizedBedBlockEntity bed) {
        int ironBefore = countIn(bed, FluidizedBedBlockEntity.SLOT_IRON, ModItems.IRON_POWDER.get());
        int titaniaBefore = countIn(bed, FluidizedBedBlockEntity.SLOT_TITANIA, ModItems.TITANIA.get());

        ItemStack feed = new ItemStack(ModItems.CRUSHED_ILMENITE.get());
        feed.set(ModDataComponents.GRIND_FINENESS.get(), GRIND_120_MICRONS_PERMILLE);
        bed.setItem(FluidizedBedBlockEntity.SLOT_INPUT, feed);
        // A batch is BATCH_WORK ticks at the idle rate; 500 clears it with headroom.
        for (int i = 0; i < 500; i++) {
            bed.serverTick();
        }
        return new int[] {
                countIn(bed, FluidizedBedBlockEntity.SLOT_IRON, ModItems.IRON_POWDER.get()) - ironBefore,
                countIn(bed, FluidizedBedBlockEntity.SLOT_TITANIA, ModItems.TITANIA.get()) - titaniaBefore};
    }

    private static int countIn(FluidizedBedBlockEntity bed, int slot,
                               net.minecraft.world.item.Item expected) {
        ItemStack stack = bed.getItem(slot);
        return stack.is(expected) ? stack.getCount() : 0;
    }

    private static BlockPos sealedBedRoom(GameTestHelper helper, BlockPos inside) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int ring = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                    helper.setBlock(inside.offset(dx, dy, dz), ring <= 1
                            ? Blocks.AIR.defaultBlockState()
                            : ModBlocks.HULL_PLATE.get().defaultBlockState());
                }
            }
        }
        BlockPos bedPos = inside.offset(1, 0, 0);
        helper.setBlock(bedPos, ModBlocks.FLUIDIZED_BED.get().defaultBlockState());

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(inside));
        Atmosphere.RoomReading reading = atmosphere.readingAt(helper.absolutePos(inside));
        if (reading == null || !reading.sealed()) {
            helper.fail("Setup failed: the reactor room is not sealed, so its hydrogen and its"
                    + " water are really venting to space");
        }
        return bedPos;
    }

    private static BlockPos sealedRefinerRoom(GameTestHelper helper, BlockPos inside) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int ring = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                    helper.setBlock(inside.offset(dx, dy, dz), ring <= 1
                            ? Blocks.AIR.defaultBlockState()
                            : ModBlocks.HULL_PLATE.get().defaultBlockState());
                }
            }
        }
        BlockPos refinerPos = inside.offset(1, 0, 0);
        helper.setBlock(refinerPos, ModBlocks.CARBONYL_REFINER.get().defaultBlockState());

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(inside));
        Atmosphere.RoomReading reading = atmosphere.readingAt(helper.absolutePos(inside));
        if (reading == null || !reading.sealed()) {
            helper.fail("Setup failed: the refiner room is not sealed, so anything it appears"
                    + " to consume or emit is really venting to space");
        }
        return refinerPos;
    }

    /**
     * <em>"I will just mine this tank out of the way."</em>
     *
     * <p>Test plan row 4.5. Whether a broken vessel vents into the room or simply
     * ceases to exist is a design choice; <strong>duplicating</strong> its contents
     * never is. A tank whose gas both leaves with the block and stays in the world is
     * an infinite air machine, findable by anyone who breaks a tank twice.
     */
    private static void breakingAFullTank(GameTestHelper helper) {
        // The tank sits INSIDE the sealed room, which is the only arrangement where
        // breaking it could put gas anywhere. An earlier version of this test placed it
        // outside on the end of the pipe run, where a broken tank has no room to vent
        // into - so it could not have caught duplication even in principle, and a
        // mutant that tripled the contents on removal passed it untouched.
        BlockPos inside = new BlockPos(2, 2, 2);
        ModTestFunctions.buildBoxAround(helper, inside);
        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());

        BlockPos tankPos = inside.offset(1, 0, 0);
        helper.setBlock(tankPos, ModBlocks.GAS_TANK.get().defaultBlockState());
        atmosphere.invalidate(helper.absolutePos(tankPos));

        GasTankBlockEntity vessel = helper.getBlockEntity(tankPos, GasTankBlockEntity.class);
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        if (vessel == null || room == null) {
            helper.fail("Setup failed: tank entity or room missing");
            return;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        room.addGasAt(Gas.OXYGEN, 120, 293.15);
        vessel.contents().addGasAt(Gas.OXYGEN, 400, 293.15);

        double inRoomBefore = room.gases().get(Gas.OXYGEN);
        double inTank = vessel.contents().gases().get(Gas.OXYGEN);

        helper.setBlock(tankPos, Blocks.AIR.defaultBlockState());
        atmosphere.invalidate(helper.absolutePos(tankPos));

        RoomState after = atmosphere.roomAt(helper.absolutePos(inside));
        if (after == null) {
            helper.fail("The room stopped existing when the tank was broken");
            return;
        }
        double gained = after.gases().get(Gas.OXYGEN) - inRoomBefore;
        if (gained > inTank + 1e-6) {
            helper.fail("Breaking a tank holding " + inTank + " mol released " + gained
                    + " mol into the room - more than it contained, which is an"
                    + " infinite air machine");
            return;
        }
        if (gained < -1e-6) {
            helper.fail("Breaking a tank also removed " + (-gained)
                    + " mol the room already had");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I opened the door and all my air went out."</em>
     *
     * <p>Test plan row 4.6 — the airlock problem, and the reason to store gas at all.
     * A closed bulkhead must separate two rooms and an open one must join them, with
     * every mole accounted for either way. If a door leaked while shut there would be
     * no way to hold pressure; if opening one lost air rather than moving it, storing
     * gas beforehand would not help.
     */
    private static void openingADoorOnVacuum(GameTestHelper helper) {
        BlockPos left = new BlockPos(2, 2, 2);
        BlockPos right = left.offset(0, 0, 4);
        ModTestFunctions.buildBoxAround(helper, left);
        ModTestFunctions.buildBoxAround(helper, right);

        // A bulkhead in the shared wall, both halves of it.
        BlockPos doorFoot = left.offset(0, -1, 2);
        helper.setBlock(doorFoot, ModBlocks.BULKHEAD_DOOR.get().defaultBlockState());
        helper.setBlock(doorFoot.above(), ModBlocks.BULKHEAD_DOOR.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties
                        .BlockStateProperties.DOUBLE_BLOCK_HALF,
                        net.minecraft.world.level.block.state.properties
                                .DoubleBlockHalf.UPPER));

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(doorFoot));

        RoomState pressurised = atmosphere.roomAt(helper.absolutePos(left));
        RoomState empty = atmosphere.roomAt(helper.absolutePos(right));
        if (pressurised == null || empty == null) {
            helper.fail("Two boxes with a shut bulkhead between them should be two rooms");
            return;
        }
        if (pressurised == empty) {
            helper.fail("A SHUT bulkhead did not separate the rooms - pressure could"
                    + " never be held against vacuum");
            return;
        }

        for (Gas gas : Gas.values()) {
            pressurised.removeGas(gas, pressurised.gases().get(gas));
            empty.removeGas(gas, empty.gases().get(gas));
        }
        pressurised.addGasAt(Gas.OXYGEN, 240, 293.15);

        // Measured across every distinct room the door touches, by exactly the same
        // reckoning as afterwards. The first version compared a hard-coded 240 against
        // Math.max over two probes, and that asymmetry made it flaky: a sliver of gas
        // inherited from a neighbouring room on rescan counted only on the "after" side,
        // so the test failed about one run in three for a reason that was not the door.
        // Summing the same set both times cancels anything that was already there.
        BlockPos[] probes = {left, right, doorFoot, doorFoot.above()};
        double before = totalOxygenAcrossRooms(helper, atmosphere, probes);

        // Open it, the way a player does.
        for (BlockPos half : new BlockPos[] {doorFoot, doorFoot.above()}) {
            helper.setBlock(half, helper.getBlockState(half).setValue(
                    net.minecraft.world.level.block.state.properties
                            .BlockStateProperties.OPEN, true));
        }
        atmosphere.invalidate(helper.absolutePos(doorFoot));

        double after = totalOxygenAcrossRooms(helper, atmosphere, probes);
        if (Math.abs(after - before) > Math.max(1e-6, before * 1e-6)) {
            helper.fail("Opening a bulkhead onto vacuum changed the air from " + before
                    + " to " + after + " mol - it should only have spread out");
            return;
        }

        // And the door must actually have joined them. Without this the conservation
        // check above passes just as well when nothing happened at all, which is the
        // vacuous-scenario trap the broken-tank test already taught us once.
        RoomState leftAfter = atmosphere.roomAt(helper.absolutePos(left));
        RoomState rightAfter = atmosphere.roomAt(helper.absolutePos(right));
        if (leftAfter == null || rightAfter == null || leftAfter.id() != rightAfter.id()) {
            helper.fail("An OPEN bulkhead did not join the two rooms, so this test never"
                    + " exercised a door opening onto vacuum");
            return;
        }
        helper.succeed();
    }

    /**
     * Total oxygen across the <em>distinct</em> rooms the given positions resolve to.
     *
     * <p>Distinct by room id, so probes that land in the same room are not counted twice —
     * which is exactly what happens once a door joins two of them.
     */
    private static double totalOxygenAcrossRooms(GameTestHelper helper, Atmosphere atmosphere,
                                                 BlockPos... probes) {
        java.util.Map<Long, RoomState> distinct = new java.util.HashMap<>();
        for (BlockPos probe : probes) {
            RoomState room = atmosphere.roomAt(helper.absolutePos(probe));
            if (room != null) {
                distinct.putIfAbsent(room.id(), room);
            }
        }
        double total = 0;
        for (RoomState room : distinct.values()) {
            total += room.gases().get(Gas.OXYGEN);
        }
        return total;
    }

    /**
     * <em>"Two pumps in a row should be twice as good."</em>
     *
     * <p>Test plan row 2. Chaining is a thing people try, and the danger is not that
     * it fails but that it multiplies: each pump moving the same gas onward would make
     * a line of pumps an air factory. Whether the chain works at all is a design
     * question. That it conserves is not.
     */
    private static void pumpsInSeries(GameTestHelper helper) {
        BlockPos inside = new BlockPos(2, 2, 2);
        ModTestFunctions.buildBoxAround(helper, inside);
        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        if (room == null) {
            helper.fail("The sealed box did not become a room");
            return;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        room.addGasAt(Gas.OXYGEN, 300, 293.15);

        BlockPos port = inside.offset(0, 0, 2);
        BlockPos pumpA = port.offset(0, 0, 1);
        BlockPos tankA = pumpA.offset(0, 0, 1);
        BlockPos pumpB = tankA.offset(0, 0, 1);
        BlockPos tankB = pumpB.offset(0, 0, 1);

        helper.setBlock(port, ModBlocks.GAS_PORT.get().defaultBlockState()
                .setValue(GasPortBlock.FACING, Direction.NORTH));
        for (BlockPos pump : new BlockPos[] {pumpA, pumpB}) {
            helper.setBlock(pump, ModBlocks.GAS_PUMP.get().defaultBlockState()
                    .setValue(GasPumpBlock.FACING, Direction.SOUTH));
        }
        for (BlockPos tank : new BlockPos[] {tankA, tankB}) {
            helper.setBlock(tank, ModBlocks.GAS_TANK.get().defaultBlockState());
        }
        atmosphere.invalidate(helper.absolutePos(port));

        double before = totalIn(helper, inside, tankA, tankB);
        for (int i = 0; i < 200; i++) {
            for (BlockPos pump : new BlockPos[] {pumpA, pumpB}) {
                GasPumpBlockEntity motor =
                        helper.getBlockEntity(pump, GasPumpBlockEntity.class);
                if (motor != null) {
                    BlockPos absolute = helper.absolutePos(pump);
                    motor.pumpOnce(helper.getLevel(), absolute,
                            helper.getLevel().getBlockState(absolute));
                }
            }
        }
        // Conservation holds trivially if neither pump ran, and the broken-tank test
        // already proved a scenario can look right while checking nothing. So the chain
        // has to be shown to have actually moved gas down it before the conservation
        // assertion means anything.
        GasTankBlockEntity far = helper.getBlockEntity(tankB, GasTankBlockEntity.class);
        if (far == null || far.contents().gases().get(Gas.OXYGEN) <= 1e-6) {
            helper.fail("No gas reached the far tank, so this test is not exercising"
                    + " a chain and its conservation check proves nothing");
            return;
        }

        double after = totalIn(helper, inside, tankA, tankB);
        if (Math.abs(after - before) > Math.max(1e-6, before * 1e-6)) {
            helper.fail("Two pumps in series changed the total gas from " + before
                    + " to " + after + " mol - a chain of pumps must not be a factory");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I placed the pump facing the wrong way. I don't want to break it to turn it."</em>
     *
     * <p>The reason the wrench exists. Asserts the observable thing — the pump's facing
     * advances on a click, and again on the next, so any orientation is reachable — rather
     * than any internal value.
     */
    private static void wrenchTurnsAPump(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.GAS_PUMP.get().defaultBlockState()
                .setValue(GasPumpBlock.FACING, Direction.NORTH));
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH.get()));

        helper.useBlock(pos, player);
        Direction after = helper.getBlockState(pos).getValue(GasPumpBlock.FACING);
        if (after != Direction.SOUTH) {
            helper.fail("A wrench click on a north-facing pump should have turned it to"
                    + " south; it reads " + after);
            return;
        }
        // A second click carries on, so repeated clicks reach every facing.
        helper.useBlock(pos, player);
        if (helper.getBlockState(pos).getValue(GasPumpBlock.FACING) == after) {
            helper.fail("A second wrench click did not advance the facing past " + after);
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"Where did the slider go?"</em>
     *
     * <p>The separator has no handle in its window any more - the field is set on the machine with
     * a wrench, one notch a click, and sneaking turns it the other way. Asserts the thing a player
     * can see: the field the drum is actually running at moves, and moves back.
     *
     * <p>The failure this forbids is the whole mechanic quietly evaporating. Deleting the dial and
     * forgetting to wire the wrench would leave a machine whose setting nobody in the game can
     * change, and every test of what a good field does would still pass.
     */
    private static void theWrenchSetsAMachineWithNoDial(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.MAGNETIC_SEPARATOR.get().defaultBlockState());
        var separator = helper.getBlockEntity(pos, MagneticSeparatorBlockEntity.class);
        if (separator == null) {
            helper.fail("Setup failed: no separator");
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH.get()));

        double before = separator.field();
        rightClick(helper, pos, player);
        double wound = separator.field();
        if (!(wound > before + 1e-4)) {
            helper.fail("A wrench click did not wind the separator field up: it read " + before
                    + " and still reads " + wound + ", so the machine has no control at all now");
            return;
        }

        // Sneaking turns it the other way, which is the idiom this tool already uses elsewhere.
        player.setShiftKeyDown(true);
        rightClick(helper, pos, player);
        rightClick(helper, pos, player);
        player.setShiftKeyDown(false);
        double unwound = separator.field();
        if (!(unwound < before - 1e-4)) {
            helper.fail("Sneak-wrenching should turn the field down past where it started; from "
                    + wound + " two clicks left it at " + unwound);
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"Why did my crusher stop?"</em> - because you are holding a wrench in it.
     *
     * <p>The entire price of calibration is minutes, never a resource, and this is where the
     * minutes are charged: the machine does no work while the shims settle. Without this the
     * wrench would be a free click and "set, and come back" would cost nothing at all.
     */
    private static void calibratingStopsTheMachine(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.ORE_CRUSHER.get().defaultBlockState());
        var crusher = helper.getBlockEntity(pos, OreCrusherBlockEntity.class);
        if (crusher == null) {
            helper.fail("Setup failed: no crusher");
            return;
        }
        crusher.setItem(0, new ItemStack(ModItems.METAL_RICH_ORE.get(), 64));

        // Cranked hard for a moment with nobody adjusting it: this is the machine working.
        int freely = crankFor(crusher, 40);
        if (freely <= 0) {
            helper.fail("A fed, cranked crusher did nothing in 40 ticks, so there is no work for"
                    + " calibrating to interrupt");
            return;
        }

        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH.get()));
        rightClick(helper, pos, player);
        // The old size band goes out of the way first. Re-setting the jaws changes the grade
        // coming out, so the product slot would stop the machine on its own - and a test that
        // cannot tell "stopped by the calibration" from "stopped by a full slot" proves neither.
        crusher.setItem(1, ItemStack.EMPTY);

        int whileSettling = crankFor(crusher, 40);
        if (whileSettling != 0) {
            helper.fail("A crusher with a wrench in it worked on " + whileSettling + " of 40"
                    + " ticks; calibrating has to stop the machine or it costs the player"
                    + " nothing");
            return;
        }

        // And it comes back on its own once the shims have settled - three seconds, no second
        // click, because a machine you had to switch on again would be a trap rather than a job.
        crankFor(crusher, 40);
        if (crankFor(crusher, 40) <= 0) {
            helper.fail("The crusher never restarted after its calibration settled, so a wrench"
                    + " click is a machine switched off forever [still calibrating="
                    + crusher.isBeingCalibrated() + ", can run=" + crusher.canRun() + "]");
            return;
        }
        helper.succeed();
    }

    /**
     * A right-click on a block, in the order the real game performs one.
     *
     * <p>{@code helper.useBlock} goes straight to the block and then to the item, and skips the
     * phase in between them: a tool's chance to act <em>before</em> the block does. That phase is
     * the only way a wrench can reach a machine at all, because every one of these machines opens
     * a menu on right-click and consumes the interaction doing it.
     *
     * <p>So the ordering has to be reproduced here rather than borrowed. It is also the thing
     * being asserted: on a drifting machine the wrench takes the click and the panel does not
     * open, and on every other block the wrench never sees it.
     */
    private static void rightClick(GameTestHelper helper, BlockPos pos, Player player) {
        BlockPos absolute = helper.absolutePos(pos);
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        net.minecraft.world.item.context.UseOnContext context =
                new net.minecraft.world.item.context.UseOnContext(
                        player, InteractionHand.MAIN_HAND, hit);
        if (player.getItemInHand(InteractionHand.MAIN_HAND)
                .onItemUseFirst(context).consumesAction()) {
            return;
        }
        helper.useBlock(pos, player, hit);
    }

    /**
     * Cranks a machine for this many ticks and reports on how many of them it did anything.
     *
     * <p>Counted as ticks that moved rather than as work accumulated, because work resets to zero
     * when a batch lands: a machine that ran perfectly for forty ticks and finished on the last one
     * reads as having done no work at all, and the first version of this helper duly reported a
     * healthy crusher as switched off.
     */
    private static int crankFor(ProcessingBlockEntity machine, int ticks) {
        int moved = 0;
        int previous = machine.work();
        for (int i = 0; i < ticks; i++) {
            machine.crank();
            machine.serverTick();
            if (machine.work() != previous) {
                moved++;
            }
            previous = machine.work();
        }
        return moved;
    }

    /**
     * <em>"I left for the night and my crusher had ruined itself."</em> - it must not have.
     *
     * <p>Drift is charged against work done, not against the clock. A machine standing idle keeps
     * its setting; one that has been grinding all afternoon does not. The clock version of this
     * would punish a player for logging off, which is the opposite of maintenance being a job.
     */
    private static void onlyWorkDriftsASetting(GameTestHelper helper) {
        BlockPos idlePos = new BlockPos(1, 1, 1);
        BlockPos busyPos = new BlockPos(4, 1, 1);
        helper.setBlock(idlePos, ModBlocks.ORE_CRUSHER.get().defaultBlockState());
        helper.setBlock(busyPos, ModBlocks.ORE_CRUSHER.get().defaultBlockState());
        var idle = helper.getBlockEntity(idlePos, OreCrusherBlockEntity.class);
        var busy = helper.getBlockEntity(busyPos, OreCrusherBlockEntity.class);
        if (idle == null || busy == null) {
            helper.fail("Setup failed: idle=" + idle + " busy=" + busy);
            return;
        }
        double started = idle.setting();
        busy.setItem(0, new ItemStack(ModItems.METAL_RICH_ORE.get(), 64));

        for (int i = 0; i < 400; i++) {
            idle.serverTick();
            busy.crank();
            busy.serverTick();
        }

        if (Math.abs(idle.setting() - started) > 1e-6) {
            helper.fail("A crusher that ground nothing for 400 ticks drifted from " + started
                    + " to " + idle.setting() + "; drift is charged for work, not for time");
            return;
        }
        if (!(busy.setting() > started + 1e-4)) {
            helper.fail("A crusher that ground for 400 ticks is still set at " + busy.setting()
                    + ", so the liners never wear and calibration is a mechanic with no clock");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"My crusher stops after one batch and I have not touched it."</em>
     *
     * <p>Where two correct systems broke each other. The crusher stamps its product with the gap it
     * was ground at; drift makes every batch a hair different; an exactly-stamped product will not
     * stack with the one before it, so the machine fills its single output slot and stops. Nothing
     * failed and nothing logged.
     *
     * <p>Material is graded into size <em>bands</em> for exactly this reason in the real world, and
     * a band has to be wider than the drift the panel is willing to tolerate. So: run a crusher
     * hard, but not far enough to earn a service, and it should have filled a sack rather than
     * jammed on the second batch.
     */
    private static void aDriftingCrusherStillFillsASack(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.ORE_CRUSHER.get().defaultBlockState());
        var crusher = helper.getBlockEntity(pos, OreCrusherBlockEntity.class);
        if (crusher == null) {
            helper.fail("Setup failed: no crusher");
            return;
        }
        crusher.setItem(0, new ItemStack(ModItems.METAL_RICH_ORE.get(), 64));

        // Enough for several batches, and short of the work that earns a wrench.
        crankFor(crusher, 150);

        if (crusher.isWorthRecalibrating()) {
            helper.fail("This run was meant to stay inside one service interval and did not, so it"
                    + " proves nothing about a machine nobody has neglected");
            return;
        }
        ItemStack sack = crusher.getItem(1);
        if (sack.getCount() < 3) {
            helper.fail("A crusher that has not yet earned a wrench put " + sack.getCount()
                    + " item(s) in its output slot before stopping - it is stamping every batch"
                    + " with a different grind, so the product will not stack and the machine jams"
                    + " itself");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"The book says my crusher will do this — does it?"</em>
     *
     * <p>codex-calculator.md §2.3's own guard, staged for real, and codex-calculator-c4a.md §3's
     * "Proven how" row: build the machine, set it with the wrench, crank it through real work,
     * then feed the comminution calculator the crusher's own {@code calibratedTo()}/
     * {@code workSinceCalibration()} and assert its prediction matches what the crusher itself
     * reports. Checked twice — freshly calibrated, and again after real cranking has pushed it
     * past {@code Drift.CRUSHER.workToService()} — because a calculator that only agrees with a
     * pristine machine has proven nothing about the drift mechanic the whole page is about.
     *
     * <p><strong>What this cannot prove, and does not claim to</strong> (rule 8's honesty
     * clause): both the crusher and the calculator call the same {@code Comminution}/
     * {@code Calibration} functions by design (rule 46 — one model, not two), so a mutation
     * inside those functions moves both sides together and this test stays green, correctly.
     * What it does catch is the calculator's own wiring being wrong — the wrong {@code Drift}
     * enum, a swapped argument, an inverted threshold — which is the actual failure a freshly
     * written calculator is likely to contain and the crusher's already-proven behaviour is not.
     */
    private static void comminutionCalculatorPredictsTheCrusher(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.ORE_CRUSHER.get().defaultBlockState());
        var crusher = helper.getBlockEntity(pos, OreCrusherBlockEntity.class);
        if (crusher == null) {
            helper.fail("Setup failed: no crusher");
            return;
        }

        // A known dial, reached with the wrench the way a player actually sets one - not written
        // to the field directly, which would prove nothing about the wrench's own path.
        for (int i = 0; i < 3; i++) {
            crusher.turnTheAdjuster(1);
        }
        if (!predictionMatchesTheCrusher(helper, crusher, "freshly calibrated")) {
            return;
        }

        crusher.setItem(0, new ItemStack(ModItems.METAL_RICH_ORE.get(), 64));
        for (int i = 0; i < 300; i++) {
            crusher.crank();
            crusher.serverTick();
        }
        if (crusher.workSinceCalibration() < Calibration.Drift.CRUSHER.workToService()) {
            helper.fail("This run was meant to cross the service threshold and did not (reached "
                    + crusher.workSinceCalibration() + " work) - it proves nothing about a"
                    + " machine that has actually drifted");
            return;
        }
        if (!predictionMatchesTheCrusher(helper, crusher, "worked past its service interval")) {
            return;
        }

        helper.succeed();
    }

    /** Feeds the calculator the crusher's own real numbers and checks its prediction against
     *  what the crusher itself reports. Fails loudly, naming the mismatch, rather than letting
     *  a silent disagreement pass — this IS the claim, not a formality around it. */
    private static boolean predictionMatchesTheCrusher(GameTestHelper helper,
                                                        OreCrusherBlockEntity crusher,
                                                        String label) {
        Calculator calculator = Calculators.get("ore/comminution");
        if (calculator == null) {
            helper.fail("ore/comminution is not registered");
            return false;
        }
        List<Calculator.Output> outputs = calculator.compute(
                List.of(crusher.calibratedTo(), crusher.workSinceCalibration()));
        Calculator.Output actual = outputById(outputs, "actual");
        Calculator.Output liberation = outputById(outputs, "liberation");
        if (actual == null || liberation == null) {
            helper.fail("[" + label + "] the comminution calculator did not return its expected"
                    + " outputs: " + outputs);
            return false;
        }

        double realActual = crusher.calibratedSetting();
        if (Math.abs(realActual - actual.value()) > 1e-6) {
            helper.fail("[" + label + "] the calculator predicted the jaws at " + actual.value()
                    + " but the real crusher's own calibratedSetting() is " + realActual);
            return false;
        }

        double realLiberation = crusher.liberation();
        if (Math.abs(realLiberation - liberation.value()) > 1e-6) {
            helper.fail("[" + label + "] the calculator predicted liberation " + liberation.value()
                    + " but the real crusher's own liberation() is " + realLiberation);
            return false;
        }

        boolean realWorthResetting = crusher.isWorthRecalibrating();
        boolean predictedMarginal = actual.verdict() == Calculator.Verdict.MARGINAL;
        if (realWorthResetting != predictedMarginal) {
            helper.fail("[" + label + "] the real crusher's isWorthRecalibrating()="
                    + realWorthResetting + " but the calculator's actual-output verdict was "
                    + actual.verdict());
            return false;
        }
        return true;
    }

    private static Calculator.Output outputById(List<Calculator.Output> outputs, String id) {
        for (Calculator.Output output : outputs) {
            if (output.id().equals(id)) {
                return output;
            }
        }
        return null;
    }

    /**
     * <em>"The book says my separator will do this — does it?"</em>
     *
     * <p>The separator's own version of {@link #comminutionCalculatorPredictsTheCrusher}: build
     * one, wrench it to a known field, crank it past {@code Drift.SEPARATOR.workToService()},
     * and assert the separation calculator's prediction matches {@code field()}/
     * {@code isWorthRecalibrating()}. What this does <em>not</em> check (rule 8's honesty
     * clause): a completed batch's {@code lastRecovery()}/{@code lastGrade()} against the
     * calculator's own {@code recovery}/{@code grade} outputs — correlating those to a specific
     * fineness would mean chasing which exact tick a batch happened to finish on, which is a
     * timing detail this scenario does not need in order to prove the calculator is wired to
     * the same calibration mechanic the machine actually runs.
     */
    private static void separationCalculatorPredictsTheSeparator(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.MAGNETIC_SEPARATOR.get().defaultBlockState());
        var separator = helper.getBlockEntity(pos, MagneticSeparatorBlockEntity.class);
        if (separator == null) {
            helper.fail("Setup failed: no separator");
            return;
        }

        // A known dial, reached with the wrench the way a player actually sets one.
        for (int i = 0; i < 3; i++) {
            separator.turnTheAdjuster(1);
        }
        if (!separationPredictionMatches(helper, separator, "freshly calibrated")) {
            return;
        }

        int packed = OreGrade.pack(OreGrade.CHONDRITE, 0.5);
        ItemStack feed = new ItemStack(ModItems.CRUSHED_ORE.get(), 64);
        feed.set(ModDataComponents.ORE_BATCH.get(), packed);
        separator.setItem(MagneticSeparatorBlockEntity.SLOT_INPUT, feed);
        for (int i = 0; i < 400; i++) {
            separator.crank();
            separator.serverTick();
        }
        if (separator.workSinceCalibration() < Calibration.Drift.SEPARATOR.workToService()) {
            helper.fail("This run was meant to cross the service threshold and did not (reached "
                    + separator.workSinceCalibration() + " work) - it proves nothing about a"
                    + " machine that has actually drifted");
            return;
        }
        if (!separationPredictionMatches(helper, separator, "worked past its service interval")) {
            return;
        }

        helper.succeed();
    }

    private static boolean separationPredictionMatches(GameTestHelper helper,
                                                        MagneticSeparatorBlockEntity separator,
                                                        String label) {
        Calculator calculator = Calculators.get("ore/separation");
        if (calculator == null) {
            helper.fail("ore/separation is not registered");
            return false;
        }
        // fineness is fixed at 0.5 here to match the feed packed above - it does not affect
        // calibratedTo()/field(), which is the whole claim this checks.
        List<Calculator.Output> outputs = calculator.compute(
                List.of(0.5, separator.calibratedTo(), separator.workSinceCalibration()));
        Calculator.Output field = outputById(outputs, "field");
        if (field == null) {
            helper.fail("[" + label + "] the separation calculator did not return a field output: "
                    + outputs);
            return false;
        }

        double realField = separator.field();
        if (Math.abs(realField - field.value()) > 1e-6) {
            helper.fail("[" + label + "] the calculator predicted field " + field.value()
                    + " but the real separator's own field() is " + realField);
            return false;
        }

        Calculator.Output actual = outputById(outputs, "actual");
        boolean realWorthResetting = separator.isWorthRecalibrating();
        boolean predictedMarginal = actual != null && actual.verdict() == Calculator.Verdict.MARGINAL;
        if (realWorthResetting != predictedMarginal) {
            helper.fail("[" + label + "] the real separator's isWorthRecalibrating()="
                    + realWorthResetting + " but the calculator's actual-output verdict was "
                    + (actual == null ? "missing" : actual.verdict()));
            return false;
        }
        return true;
    }

    /**
     * <em>"I want to re-aim this valve, not open it."</em>
     *
     * <p>With the wrench in hand a click must turn the valve's axis and leave its setting
     * alone — a valve stepped with an empty hand still changes its setting, but wrenched it
     * only turns. Asserts both halves: the axis moved, the setting did not.
     */
    private static void wrenchTurnsTheValveAxisNotItsSetting(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.GAS_VALVE.get().defaultBlockState()
                .setValue(GasValveBlock.AXIS, Direction.Axis.Z)
                .setValue(GasValveBlock.SETTING, 2));
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH.get()));

        helper.useBlock(pos, player);
        var after = helper.getBlockState(pos);
        if (after.getValue(GasValveBlock.AXIS) == Direction.Axis.Z) {
            helper.fail("Wrenching the valve did not turn its axis; it is still Z");
            return;
        }
        if (after.getValue(GasValveBlock.SETTING) != 2) {
            helper.fail("Wrenching the valve changed its setting from 2 to "
                    + after.getValue(GasValveBlock.SETTING)
                    + " — the wrench must turn it, not operate it");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I turned the door with the wrench and now it looks open but the game says it is shut."</em>
     *
     * <p>Rule 22, and the reported bug that produced it. A bulkhead door carries
     * {@code HORIZONTAL_FACING} exactly like a pump, so the old "turn whatever has an
     * orientation" wrench rotated one half of it — desyncing the two halves so the door read
     * physically open while its {@code OPEN} stayed false, leaving a chamber standing open that
     * the controller believed sealed. A wrench must refuse it.
     *
     * <p>Two assertions, because they guard different things. First, deterministically, that the
     * wrench's own rule says a pump turns and a door does not — this is the mutation guard, and it
     * does not depend on how the harness routes a sneak-click. Then, end to end, that a sneaking
     * wrench click (the gesture that reaches the item on a door) leaves both halves of the door
     * with the same, unchanged facing — a door the wrench half-turned would fail this.
     */
    private static void aWrenchWillNotRotateASealingDoor(GameTestHelper helper) {
        BlockPos pumpPos = new BlockPos(1, 1, 1);
        helper.setBlock(pumpPos, ModBlocks.GAS_PUMP.get().defaultBlockState());
        BlockPos doorFoot = new BlockPos(3, 1, 1);
        helper.setBlock(doorFoot, ModBlocks.BULKHEAD_DOOR.get().defaultBlockState());
        helper.setBlock(doorFoot.above(), ModBlocks.BULKHEAD_DOOR.get().defaultBlockState()
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));

        // The rule itself: a pump is turnable, a door is not.
        if (!WrenchItem.rotates(helper.getBlockState(pumpPos))) {
            helper.fail("The wrench refuses to turn a pump — it must turn the blocks whose"
                    + " orientation is the point of them");
            return;
        }
        if (WrenchItem.rotates(helper.getBlockState(doorFoot))) {
            helper.fail("The wrench says it will turn a bulkhead door — turning it desyncs the"
                    + " halves and leaves the chamber open while the controller reads sealed (rule 22)");
            return;
        }

        // End to end: a sneaking wrench click on the door must not change either half's facing.
        Direction footBefore = helper.getBlockState(doorFoot)
                .getValue(BlockStateProperties.HORIZONTAL_FACING);
        Direction topBefore = helper.getBlockState(doorFoot.above())
                .getValue(BlockStateProperties.HORIZONTAL_FACING);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH.get()));
        player.setShiftKeyDown(true); // the gesture that reaches the item's useOn on a door
        helper.useBlock(doorFoot, player);
        helper.useBlock(doorFoot.above(), player);

        Direction footAfter = helper.getBlockState(doorFoot)
                .getValue(BlockStateProperties.HORIZONTAL_FACING);
        Direction topAfter = helper.getBlockState(doorFoot.above())
                .getValue(BlockStateProperties.HORIZONTAL_FACING);
        if (footAfter != footBefore || topAfter != topBefore) {
            helper.fail("The wrench turned the door: foot " + footBefore + "->" + footAfter
                    + ", top " + topBefore + "->" + topAfter);
            return;
        }
        if (footAfter != topAfter) {
            helper.fail("The door's halves face different ways (" + footAfter + " vs " + topAfter
                    + ") — a half-turned door is exactly the broken state this guards");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"My airlock's pump must not empty the chamber while I'm standing in it."</em>
     *
     * <p>The pump enable gate (L5): a controller holds a claimed pump shut except while
     * pumping the chamber down. Asserts the held pump does not fill the tank, then — the
     * self-check the broken-tank test taught us to include — that opening the gate <em>does</em>
     * let it fill, so a passing first half cannot be a rig that never worked.
     */
    private static void aHeldPumpDoesNotDrainItsChamber(GameTestHelper helper) {
        Rig rig = Rig.build(helper, 4);
        BlockPos controller = new BlockPos(0, 0, 0); // an identity; no real block needed
        GasPumpBlockEntity pump = helper.getBlockEntity(rig.pump, GasPumpBlockEntity.class);
        if (pump == null) {
            helper.fail("The pump has no block entity");
            return;
        }
        if (!pump.claim(controller, helper.getLevel().getGameTime())) {
            helper.fail("Could not claim an unclaimed pump");
            return;
        }
        pump.setGate(controller, false);

        double before = rig.tank.pressureKPa();
        rig.run(200);
        if (rig.tank.pressureKPa() > before + 1) {
            helper.fail("A pump held shut still filled the tank: " + before + " -> "
                    + rig.tank.pressureKPa() + " kPa");
            return;
        }
        // Open the gate — now it fills, so it was the gate that stopped it, not a dead rig.
        pump.setGate(controller, true);
        rig.run(200);
        if (!(rig.tank.pressureKPa() > before + 1)) {
            helper.fail("Opening the gate did not let the pump fill the tank");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"Ограничьте физическую активность"</em> — the very first log the player finds,
     * at the crashed capsule, tells them effort costs air.
     *
     * <p>It did not. The helmet had always burned more air under exertion, but a player
     * breathing room air got one fixed rate whether they were asleep or sprinting uphill
     * with ore — so the lore taught a rule the game did not have, which is worse than
     * teaching nothing: the player rations effort for no reason and eventually learns the
     * game lies. Asserts the thing the note actually promises, on the room's own air.
     */
    private static void workingCostsMoreAirThanResting(GameTestHelper helper) {
        // Driven through the game's own path — a real player whose sprint flag decides the
        // rate — not by handing Breathing a factor directly. The direct version passed even
        // with the call site reverted to a fixed rate, which is the whole bug.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setSprinting(false);
        double resting = oxygenBurnedBy(helper, player);
        player.setSprinting(true);
        double working = oxygenBurnedBy(helper, player);

        if (resting <= 0) {
            helper.fail("A resting player burned no oxygen at all, so this test compares"
                    + " nothing");
            return;
        }
        if (!(working > resting)) {
            helper.fail("Working (" + working + " mol) cost no more air than resting ("
                    + resting + " mol) - the first log in the game tells the player to"
                    + " ration effort");
            return;
        }
        // And by the physiological factor, not merely "a bit more": the whole point is
        // that effort is expensive enough to plan around.
        double ratio = working / resting;
        double expected = HelmetAtmosphere.WORKING_ACTIVITY / HelmetAtmosphere.RESTING_ACTIVITY;
        if (Math.abs(ratio - expected) > expected * 0.05) {
            helper.fail("Working cost " + ratio + "x resting; the physiology says "
                    + expected + "x, and the two must agree");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"Why would I build an airlock when I can just open the door?"</em>
     *
     * <p>This is the pair that gives the whole plumbing tier its point, so both halves are
     * asserted together: opening a bulkhead straight onto vacuum injures, and a chamber
     * cycled down through the <em>real</em> controller, pump and tank does not. Either half
     * alone proves nothing — the first would pass for a mechanic that hurts you constantly,
     * and the second for one that never fires at all.
     *
     * <p>The airlock half used to hand the damage curve an invented, uniform 20-second ramp
     * of its own devising rather than driving the real machinery — so it proved the damage
     * function is safe against numbers this test made up, and nothing about whether the
     * airlock the player actually builds produces anything like them. In particular it never
     * touched VENTING's own instantaneous dump of the last few kPa
     * ({@code AirlockControllerBlockEntity#mediateGas}). Now the damage curve is asked the
     * real question every real controller tick, exactly as the per-player monitor does
     * (rule 13): was the ambient pressure change this tick, at this tick's real duration, one
     * that hurts.
     *
     * <p>This first caught a real bug rather than confirming the mechanic (PLAN.md): driving
     * {@link GasPumpBlockEntity#pumpOnce} once every {@code INTERVAL_TICKS} — the old
     * production schedule — dumps a whole interval's worth of pressure change into one tick,
     * which this per-tick check correctly read as an instantaneous collapse. Fixed at the
     * source: the pump now spreads that same total transfer across every real tick instead of
     * one lump (see {@code GasPumpBlockEntity#serverTick}'s own doc). This drives the same two
     * halves {@code serverTick} does — {@link GasPumpBlockEntity#resolveRouteOn} every
     * {@code INTERVAL_TICKS}th iteration, {@link GasPumpBlockEntity#applyOneTick} every
     * iteration — rather than {@code serverTick} itself, because {@code serverTick} gates its
     * own resolve on the world's real game-time clock, which this manually-driven loop never
     * advances (the exact trap {@code pumpOnce}'s own doc already names for the coarse
     * schedule, now also true of the fine one — see {@code resolveRouteOn}'s doc).
     */
    private static void cyclingIsSafeAndOpeningTheDoorIsNot(GameTestHelper helper) {
        // An unsuited player, asked through the game's own decision — not the curve.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        // The airlock's way: the real controller, the real pump, the real chamber - not an
        // invented ramp. The oxygen partial pressure passed below is inert either way: this
        // mock player can never hold a suit (BarotraumaTest's own doc comment explains why),
        // so the seal check is false regardless of ambient composition — the fraction is
        // still passed as pressure * 0.21, matching real air's O2 share, so a reader is not
        // left wondering why it is zero.
        buildChamber(helper, 2, true, true);
        helper.setBlock(CONTROLLER, ModBlocks.AIRLOCK_CONTROLLER.get().defaultBlockState()
                .setValue(play.xponer.astronima.block.AirlockControllerBlock.FACING,
                        Direction.SOUTH));
        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(CHAMBER));
        RoomState chamber = atmosphere.roomAt(helper.absolutePos(CHAMBER));
        var controller = helper.getBlockEntity(CONTROLLER,
                play.xponer.astronima.block.entity.AirlockControllerBlockEntity.class);
        if (chamber == null || controller == null || !commission(helper, controller)) {
            helper.fail("Setup failed");
            return;
        }
        chamber.addGasAt(Gas.OXYGEN, 1200, 293.15);

        BlockPos controllerAbs = helper.absolutePos(CONTROLLER);
        BlockPos pumpAbs = helper.absolutePos(PUMP);
        BlockPos chamberAbs = helper.absolutePos(CHAMBER);
        controller.command();
        double previousKPa = atmosphere.roomAt(chamberAbs).pressureKPa();
        float worstWhileCycling = 0;
        for (int tick = 0; tick < AirlockCycle.MAX_PUMP_TICKS + 200; tick++) {
            controller.tick(helper.getLevel(), controllerAbs, helper.getBlockState(CONTROLLER));
            // The real per-tick schedule, driven by this loop's own counter rather than
            // serverTick's world-clock gate - see resolveRouteOn's own doc for why.
            GasPumpBlockEntity pump = helper.getBlockEntity(PUMP, GasPumpBlockEntity.class);
            if (pump != null) {
                BlockState pumpState = helper.getLevel().getBlockState(pumpAbs);
                if (tick % GasPumpBlockEntity.INTERVAL_TICKS == 0) {
                    pump.resolveRouteOn(helper.getLevel(), pumpAbs, pumpState);
                }
                pump.applyOneTick(helper.getLevel(), pumpAbs, pumpState);
            }
            RoomState now = atmosphere.roomAt(chamberAbs);
            double currentKPa = now != null ? now.pressureKPa() : 0.0;
            // The real per-tick question, asked with the real delta this tick produced -
            // whatever shape it turns out to have, smooth or a once-every-twenty-ticks step.
            worstWhileCycling = Math.max(worstWhileCycling, AtmosphereEvents.barotraumaDamage(
                    player, previousKPa, currentKPa, previousKPa * 0.21, 0.05));
            previousKPa = currentKPa;
            if (controller.phase() == AirlockCycle.Phase.VACUUM) {
                break;
            }
        }
        if (controller.phase() != AirlockCycle.Phase.VACUUM) {
            helper.fail("The cycle never reached vacuum; stuck in " + controller.phase());
            return;
        }
        if (worstWhileCycling > 0) {
            helper.fail("Cycling a real airlock injured the person inside it ("
                    + worstWhileCycling + " damage), which would make the safe route as bad"
                    + " as the unsafe one");
            return;
        }

        // The door's way: the same atmosphere lost in a single tick.
        float slammed = AtmosphereEvents.barotraumaDamage(player, 101.0, 0.0, 101.0 * 0.21, 0.05);
        if (slammed <= 0) {
            helper.fail("Opening a bulkhead onto vacuum did no harm at all, so nothing"
                    + " makes the airlock worth building");
            return;
        }
        if (slammed < 10f) {
            helper.fail("Losing a whole atmosphere in a tick did only " + slammed
                    + " damage - it has to read as a catastrophe, not a scratch");
            return;
        }

        // The suit's half of this — that a sealed wearer is untouched — cannot be staged
        // here: equipment slots are granted on join, and no harness player joins, so every
        // one of them reports an empty suit however it is fitted. It is asserted in
        // AtmosphereEventsBarotraumaTest instead.
        helper.succeed();
    }

    /** Oxygen a sealed room loses to one breathing step by this player, as they are now. */
    private static double oxygenBurnedBy(GameTestHelper helper, Player player) {
        BlockPos inside = new BlockPos(2, 2, 2);
        ModTestFunctions.buildBoxAround(helper, inside);
        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(inside));
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        if (room == null) {
            helper.fail("The sealed box did not become a room");
            throw new IllegalStateException("no room");
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        room.addGasAt(Gas.OXYGEN, 500, 293.15);
        return play.xponer.astronima.atmosphere.AtmosphereEvents.breatheFor(player, room, 1.0);
    }

    /**
     * <em>"Hoppers put the item in the feed slot and then take it straight back out of the
     * same slot, instead of taking the product."</em>
     *
     * <p>Reported from play, and it made automation impossible: a plain {@code Container}
     * has no sides, so a hopper could touch any slot — it fed the machine and immediately
     * stole the feed back, and no amount of correct processing logic survives that. Asserts
     * the rules a hopper actually obeys, in the vanilla furnace's grammar: in from the top,
     * out from the bottom, and the feed slot is never extractable.
     */
    private static void hoppersFeedTheTopAndTakeFromTheBottom(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.ORE_CRUSHER.get().defaultBlockState());
        var crusher = helper.getBlockEntity(pos,
                play.xponer.astronima.block.entity.OreCrusherBlockEntity.class);
        if (crusher == null) {
            helper.fail("The crusher has no block entity");
            return;
        }
        ItemStack ore = new ItemStack(ModItems.METAL_RICH_ORE.get());
        ItemStack product = new ItemStack(ModItems.CRUSHED_ORE.get());
        int feed = play.xponer.astronima.block.entity.ProcessingBlockEntity.SLOT_FEED;

        // A hopper above may insert ore into the feed, and nothing else.
        if (!crusher.canPlaceItemThroughFace(feed, ore, Direction.UP)) {
            helper.fail("A hopper on top cannot feed the crusher its ore");
            return;
        }
        if (crusher.canPlaceItemThroughFace(feed, new ItemStack(ModItems.THOLIN_CLUMP.get()),
                Direction.UP)) {
            helper.fail("A hopper could push tholin into the crusher, which cannot crush it");
            return;
        }

        // The bug itself: nothing may pull the feed back out, from any side.
        for (Direction side : Direction.values()) {
            if (crusher.canTakeItemThroughFace(feed, ore, side)) {
                helper.fail("A hopper on the " + side + " side could drain the feed slot -"
                        + " it would steal the ore straight back out again");
                return;
            }
        }

        // And the bottom offers the product, not the feed.
        int[] below = crusher.getSlotsForFace(Direction.DOWN);
        if (below.length == 0 || java.util.Arrays.stream(below).anyMatch(s -> s == feed)) {
            helper.fail("The underside exposes " + java.util.Arrays.toString(below)
                    + " - it must offer product slots and never the feed");
            return;
        }
        for (int slot : below) {
            if (!crusher.canTakeItemThroughFace(slot, product, Direction.DOWN)) {
                helper.fail("A hopper underneath cannot take the product from slot " + slot);
                return;
            }
        }
        helper.succeed();
    }

    /**
     * <em>"I shift-clicked my tholin into the crusher and it vanished into the slot,
     * and the machine just sat there."</em>
     *
     * <p>machine-io.md M3: the feed slot refuses what the machine cannot use, so a wrong
     * item stays with the player instead of sitting in a machine that will never touch it
     * — a silent refusal indistinguishable from a broken machine. Both halves asserted:
     * the wrong item stays put, the right one goes in.
     */
    private static void feedSlotRefusesWrongItems(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.ORE_CRUSHER.get().defaultBlockState());
        var crusher = helper.getBlockEntity(pos,
                play.xponer.astronima.block.entity.OreCrusherBlockEntity.class);
        if (crusher == null) {
            helper.fail("The crusher has no block entity");
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var holder = new play.xponer.astronima.menu.ProcessingUiHolder(crusher, helper.absolutePos(pos));
        var menu = new com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu(
                play.xponer.astronima.registry.ModMenus.PROCESSING.get(), 1,
                player.getInventory(), holder);

        // A wrong item: shift-click must bounce it, not swallow it.
        int firstInventorySlot = 2; // after the crusher's two machine slots
        menu.slots.get(firstInventorySlot).set(new ItemStack(ModItems.THOLIN_CLUMP.get()));
        menu.quickMoveStack(player, firstInventorySlot);
        if (!crusher.getItem(0).isEmpty()) {
            helper.fail("The crusher's feed slot accepted tholin, which it cannot crush");
            return;
        }
        if (menu.slots.get(firstInventorySlot).getItem().isEmpty()) {
            helper.fail("The refused item vanished instead of staying with the player");
            return;
        }
        // The right item goes straight in.
        menu.slots.get(firstInventorySlot).set(new ItemStack(ModItems.METAL_RICH_ORE.get()));
        menu.quickMoveStack(player, firstInventorySlot);
        if (crusher.getItem(0).isEmpty()) {
            helper.fail("The feed slot refused the ore it exists to take");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I took the ore out and the bar stopped. Is it broken?"</em>
     *
     * <p>The reported behaviour is correct — half-crushed rock does not un-crush itself, so
     * the batch is held rather than thrown away — but a frozen bar with no explanation is
     * indistinguishable from a fault, and that made a kindness read as a bug.
     *
     * <p>Driven through the menu's synced data, which is the exact channel the screen draws
     * from (rule 13). Asking the block entity directly would prove the machine knows, and
     * the complaint was never that the machine did not know.
     */
    private static void aStalledMachineSaysWhichStallItIs(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.ORE_CRUSHER.get().defaultBlockState());
        var crusher = helper.getBlockEntity(pos, OreCrusherBlockEntity.class);
        if (crusher == null) {
            helper.fail("The crusher has no block entity");
            return;
        }
        var panel = play.xponer.astronima.menu.ProcessingMenu.dataFor(crusher);

        // Fed, free to run, nobody at the handle: it creeps, and says so.
        crusher.setItem(OreCrusherBlockEntity.SLOT_INPUT,
                new ItemStack(ModItems.METAL_RICH_ORE.get()));
        if (reportedState(panel) != WorkState.CREEPING) {
            helper.fail("A crusher with ore in it and a free output reported "
                    + reportedState(panel) + " instead of running");
            return;
        }

        // Let it get part way, then take the ore back out — the reported action.
        for (int tick = 0; tick < 40; tick++) {
            crusher.serverTick();
        }
        int held = crusher.work();
        if (held <= 0) {
            helper.fail("Forty ticks of crushing produced no progress to hold on to");
            return;
        }
        crusher.setItem(OreCrusherBlockEntity.SLOT_INPUT, ItemStack.EMPTY);
        crusher.serverTick();

        if (crusher.work() != held) {
            helper.fail("Taking the ore out threw away " + (held - crusher.work())
                    + " work - the batch is supposed to be held, not lost");
            return;
        }
        if (reportedState(panel) != WorkState.STARVED) {
            helper.fail("The panel reported " + reportedState(panel)
                    + " with an empty feed slot, so the player is not told why it stopped");
            return;
        }

        // And the other instrument — the lamp on the block and the readout by the
        // crosshair — must say the same thing, or a player who never opens the menu is
        // back where they started: a machine, not moving, for no stated reason. Both ask
        // BlockReadings, so this is the assertion that they are two views of one answer
        // rather than two answers.
        var lamp = asTheClientSeesIt(helper, crusher);
        if (lamp == null) {
            helper.fail("A stalled crusher has no gauge at all - its stall is visible only"
                    + " to a player who opens the menu");
            return;
        }
        if (!lamp.label().contains(WorkState.STARVED.label())) {
            helper.fail("The gauge says '" + lamp.label() + "' while the panel says "
                    + reportedState(panel) + " - two instruments, two answers");
            return;
        }
        if (!lamp.label().contains("held")) {
            helper.fail("The gauge does not say the progress was kept ('" + lamp.label()
                    + "'), which is the half of this that made a kindness read as a bug");
            return;
        }

        // The other stall, which needs the opposite action from the player.
        crusher.setItem(OreCrusherBlockEntity.SLOT_INPUT,
                new ItemStack(ModItems.METAL_RICH_ORE.get()));
        ItemStack full = new ItemStack(ModItems.CRUSHED_ORE.get());
        full.setCount(full.getMaxStackSize());
        crusher.setItem(OreCrusherBlockEntity.SLOT_OUTPUT, full);
        if (reportedState(panel) != WorkState.BLOCKED) {
            helper.fail("A full product slot reported " + reportedState(panel)
                    + " - the player would go looking for ore they already have");
            return;
        }

        // Emptying it must let the machine go again, or the state is a dead end.
        crusher.setItem(OreCrusherBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
        if (!reportedState(panel).isWorking()) {
            helper.fail("Clearing the product slot did not get the crusher going again;"
                    + " it reported " + reportedState(panel));
            return;
        }
        helper.succeed();
    }

    /** What the screen would draw, read from the channel the screen reads. */
    /**
     * <em>"I felt fine for ages and then everything went wrong at once."</em>
     *
     * <p><strong>The opening move of the medical tier.</strong> An illness that announced itself
     * would hand the player the diagnosis: they would know exactly what they had just done. So it
     * hides — and by the time anything shows, they have done a hundred other things and working out
     * which one it was is the game.
     *
     * <p>Three claims, in the order a player meets them. It is <strong>silent</strong> while it
     * incubates, including on the instrument. It then <strong>shows through the same symptom list
     * the room uses</strong>, so it first reads as the environment doing something. And a
     * <strong>rested body throws off the mild one unaided</strong>, which is the first time the
     * correct answer in this mod is to do nothing.
     */
    private static void anIllnessHidesThenShows(GameTestHelper helper) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.getFoodData().setFoodLevel(20);
        // immunityOf reads real macronutrient sufficiency now, not vanilla hunger
        // (design/macronutrients.md §3g) - the attachment already defaults full, named here so
        // a reader does not go looking for what setFoodLevel(20) above still does (nothing, to
        // immunity specifically; it is kept for the vanilla starvation/regen this test does not
        // otherwise touch).

        if (!play.xponer.astronima.physio.Infections.infect(player,
                play.xponer.astronima.sim.pathogen.Strain.Source.CRYOPHILIC)) {
            helper.fail("could not catch an infection at all");
            return;
        }
        var strain = play.xponer.astronima.sim.pathogen.Pathogens.CRYOPHILE;

        // Halfway through incubation: present, and showing nothing whatsoever.
        java.util.Map<play.xponer.astronima.sim.physio.Ailment, play.xponer.astronima.sim.physio.Ailment.Severity> hidden = new java.util.EnumMap<>(play.xponer.astronima.sim.physio.Ailment.class);
        drive(player, strain.incubationSeconds() / 2, hidden);
        if (!hidden.isEmpty()) {
            helper.fail("an infection that is still incubating put " + hidden.keySet()
                    + " on the readout, so the player can name the cause by remembering what they"
                    + " just touched");
            return;
        }
        if (!player.getData(play.xponer.astronima.registry.ModAttachments.INFECTION).isIll()) {
            helper.fail("the infection vanished while it was incubating");
            return;
        }

        // An exhausted body, past incubation: it shows, and it shows a borrowed symptom.
        // A real, long stretch of real fasting - the real way to starve immunity now
        // (design/macronutrients.md §3g), not vanilla hunger.
        Nutrition.tick(player, 400.0 * 24.0 * 3600.0);
        java.util.Map<play.xponer.astronima.sim.physio.Ailment, play.xponer.astronima.sim.physio.Ailment.Severity> showing = new java.util.EnumMap<>(play.xponer.astronima.sim.physio.Ailment.class);
        // Long, because a cryophilic strain is chronic by design: slow, and hard to shift. A
        // scenario that only waited a stage's worth would be measuring impatience.
        drive(player, strain.incubationSeconds() + strain.stageSeconds() * 4, showing);
        if (showing.isEmpty()) {
            helper.fail("a starving player carried an infection past its incubation and the"
                    + " biomonitor showed nothing at all - the hazard has no instrument");
            return;
        }
        if (!showing.containsKey(play.xponer.astronima.sim.physio.Ailment.UNKNOWN_INFECTION)) {
            helper.fail("it showed " + showing.keySet() + " and never said an infection was"
                    + " unidentified, so the player has nothing to chase");
            return;
        }

        // Fed and rested, the mild one loses. Sometimes the correct answer is nothing.
        player.getFoodData().setFoodLevel(20);
        player.setData(play.xponer.astronima.registry.ModAttachments.MACRONUTRITION.get(),
                CarriedMacronutrition.FULL);
        java.util.Map<play.xponer.astronima.sim.physio.Ailment, play.xponer.astronima.sim.physio.Ailment.Severity> beaten = new java.util.EnumMap<>(play.xponer.astronima.sim.physio.Ailment.class);
        drive(player, strain.stageSeconds() * 30, beaten);
        if (player.getData(play.xponer.astronima.registry.ModAttachments.INFECTION).isIll()) {
            helper.fail("a well-fed player could not shake a mild infection, so the only answer to"
                    + " any illness is a dose and the immune system is decoration");
            return;
        }
        helper.succeed();
    }

    /** Runs an illness forward the way the atmosphere tick does, collecting what it shows. */
    private static void drive(net.minecraft.world.entity.player.Player player, double seconds,
                              java.util.Map<play.xponer.astronima.sim.physio.Ailment, play.xponer.astronima.sim.physio.Ailment.Severity> diagnosis) {
        for (double at = 0; at < seconds; at += 1) {
            diagnosis.clear();
            play.xponer.astronima.physio.Infections.tick(player, 1, diagnosis);
        }
    }

    /**
     * <em>"The solar panel does not work at all, it just says no sun."</em>
     *
     * <p><strong>It worked.</strong> It made power the whole time — and its gauge was reading a
     * field that never left the server, so a panel standing in full daylight reported {@code no
     * sun} for ever. Every machine in the mod had it; this is the one where the default value has a
     * name a player reads as a fault, because a solar array has no screen and the lamp is the only
     * instrument it has.
     *
     * <p>Asserted through the packet rather than off the block entity, which is the whole point:
     * reading the server's own field is what let this pass unnoticed for the life of the mod
     * (rules 13 and 26).
     */
    private static void aPanelInDaylightSaysSo(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at, play.xponer.astronima.registry.ModBlocks.SOLAR_ARRAY.get());

        // A tick, so the panel has looked up once. The gauge is published from setChanged(), which
        // is what the panel calls the moment the sun on it changes.
        helper.runAfterDelay(2, () -> {
            var array = helper.getLevel().getBlockEntity(helper.absolutePos(at))
                    instanceof play.xponer.astronima.block.entity.SolarArrayBlockEntity found
                    ? found : null;
            if (array == null) {
                helper.fail("the solar array has no block entity - the test's own layout is wrong");
                return;
            }
            if (array.sunlight() <= 0) {
                helper.fail("the panel really is in the dark, so this scenario is measuring the"
                        + " test arena rather than the instrument");
                return;
            }

            var lamp = asTheClientSeesIt(helper, array);
            if (lamp == null) {
                helper.fail("a panel in daylight sends the player no gauge at all - the machine"
                        + " works and every instrument it has is blank");
                return;
            }
            if (lamp.label().equals("no sun")) {
                helper.fail("the panel is in full sun, is making " + Math.round(array.watts())
                        + " W, and tells the player 'no sun' - which is exactly how a working"
                        + " machine comes to be reported as broken");
                return;
            }
            if (lamp.band() == play.xponer.astronima.client.indicator.Reading.Band.IDLE) {
                helper.fail("the panel's lamp reads idle while it is generating - the colour and"
                        + " the label have to agree, or one of them is decoration");
                return;
            }

            // And the other half of rule 26, which is the half that hid the fault: a block entity
            // that has been told NOTHING must show nothing. A copy nobody has sent a packet to has
            // a panel's default fields, and computing a gauge from those is precisely how "no sun"
            // came to be drawn on a panel standing in full daylight. There must be no fallback.
            var untold = array.getType().create(array.getBlockPos(), array.getBlockState());
            if (untold != null
                    && play.xponer.astronima.client.indicator.BlockReadings.shown(untold) != null) {
                helper.fail("a block entity the server has told nothing still shows a gauge - that"
                        + " number is computed from an uninitialised copy and drawn as a"
                        + " measurement, which is the whole fault this scenario exists for");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * A block's gauge <strong>as the client gets it</strong> — through the packet, not the field.
     *
     * <p><strong>Rule 26, and rule 13 wearing a new face.</strong> Two scenarios used to read a
     * lamp by calling {@code BlockReadings.of(theServerObject)}, which is not the door a player
     * looks through: a block entity's fields do not cross to the client, so that assertion passed
     * for the whole of this mod's life while every indicator in the game drew a default value. A
     * solar array in full daylight said {@code no sun}.
     *
     * <p>So this does what the client does, exactly: takes the block entity's update tag — the
     * bytes {@code ClientboundBlockEntityDataPacket} carries — builds a fresh block entity of the
     * same type, loads the tag into it, and reads the gauge off <em>that</em>. Anything the server
     * failed to send is missing here too, which is the entire point.
     */
    private static play.xponer.astronima.client.indicator.@org.jetbrains.annotations.Nullable Reading asTheClientSeesIt(
            GameTestHelper helper, net.minecraft.world.level.block.entity.BlockEntity source) {
        var registries = helper.getLevel().registryAccess();
        var copy = source.getType().create(source.getBlockPos(), source.getBlockState());
        if (copy == null) {
            return null;
        }
        try (var reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(
                copy.problemPath(), org.slf4j.LoggerFactory.getLogger(PlayerScenarios.class))) {
            copy.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                    reporter, registries, source.getUpdateTag(registries)));
        }
        return play.xponer.astronima.client.indicator.BlockReadings.shown(copy);
    }

    private static WorkState reportedState(net.minecraft.world.inventory.ContainerData panel) {
        return WorkState.values()[panel.get(
                play.xponer.astronima.menu.ProcessingMenu.DATA_WORK_STATE)];
    }



    /**
     * <em>"Two rooms, all doors shut, and the analyzer says they are both room #13."</em>
     *
     * <p>Reported from play, along with everything it causes: air draining out of a sealed
     * habitat with nothing running, a chamber that reads empty and then finds 0.1 kPa from
     * nowhere, and a repressurise that stops at a quarter of an atmosphere. All one bug —
     * two separate volumes sharing a single {@code RoomState}, so gas taken from one is
     * taken from both.
     *
     * <p>The size relationship is the whole test. A tiny chamber off a big habitat is fine;
     * it is when the two halves are <em>comparable</em> that each one in turn can claim to
     * have retained "most" of what was there and both walk away with the same id. That is
     * exactly the shape of an airlock built onto a starter base, which is why it was found
     * in play and not here.
     */
    private static void twoSealedRoomsAreNotOneRoom(GameTestHelper helper) {
        // One box, split down the middle by a door: two halves of deliberately similar
        // size, which is the case the identity rule gets wrong.
        // Widths chosen so the split is UNEVEN: the big side keeps most of the original
        // room (so it inherits the id), and the small side is still more than half of what
        // the big side became (so it inherits the id too). That is the whole trap, and an
        // even split does not reach it - which is why an earlier version of this test
        // passed against the broken code.
        BlockPos origin = new BlockPos(1, 1, 1);
        int width = 8;
        int depth = 3;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < depth + 2; y++) {
                for (int z = 0; z < depth + 2; z++) {
                    boolean shell = y == 0 || y == depth + 1 || z == 0 || z == depth + 1
                            || x == 0 || x == width - 1;
                    helper.setBlock(origin.offset(x, y, z), shell
                            ? ModBlocks.HULL_PLATE.get().defaultBlockState()
                            : Blocks.AIR.defaultBlockState());
                }
            }
        }
        // It has to be ONE room first. That is the sequence a player actually performs -
        // you build the habitat, then you wall an airlock off the end of it - and the
        // identity rule only goes wrong on a room that already existed.
        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        BlockPos leftSeed = origin.offset(1, 1, 2);
        BlockPos rightSeed = origin.offset(width - 2, 1, 2);
        atmosphere.invalidate(helper.absolutePos(leftSeed));
        RoomState whole = atmosphere.roomAt(helper.absolutePos(leftSeed));
        if (whole == null) {
            helper.fail("The undivided box is not a room, so there is nothing to split");
            return;
        }

        // Now wall it down the middle, with a shut bulkhead door in the wall.
        int divider = 4;
        for (int y = 1; y <= depth; y++) {
            for (int z = 1; z <= depth; z++) {
                helper.setBlock(origin.offset(divider, y, z),
                        ModBlocks.HULL_PLATE.get().defaultBlockState());
            }
        }
        BlockPos doorBottom = origin.offset(divider, 1, 2);
        helper.setBlock(doorBottom, ModBlocks.BULKHEAD_DOOR.get().defaultBlockState()
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(doorBottom.above(), ModBlocks.BULKHEAD_DOOR.get().defaultBlockState()
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));

        atmosphere.invalidate(helper.absolutePos(leftSeed));
        atmosphere.invalidate(helper.absolutePos(rightSeed));

        RoomState left = atmosphere.roomAt(helper.absolutePos(leftSeed));
        RoomState right = atmosphere.roomAt(helper.absolutePos(rightSeed));
        if (left == null || right == null) {
            helper.fail("One of the two halves is not a room at all: left=" + left
                    + " right=" + right);
            return;
        }

        if (left.id() == right.id()) {
            helper.fail("Two rooms either side of a shut door are both room #" + left.id()
                    + " - they share one air supply, so venting either one empties both");
            return;
        }

        // The consequence, asserted rather than assumed: emptying one must not touch the
        // other. Sharing an id is only a bug because of this, so this is what is checked.
        double rightBefore = right.gases().totalMoles();
        for (Gas gas : Gas.values()) {
            left.removeGas(gas, left.gases().get(gas));
        }
        double rightAfter = atmosphere.roomAt(helper.absolutePos(rightSeed))
                .gases().totalMoles();
        if (Math.abs(rightAfter - rightBefore) > 1e-6) {
            helper.fail("Emptying one sealed room changed the other's air from "
                    + rightBefore + " to " + rightAfter + " mol - they are the same volume");
            return;
        }

        // The other half of the same rule, and the reason the fix is "release what you did
        // not take" rather than "refuse the id unless you took all of it": a room has to
        // keep its identity through ordinary building. Simply demanding the whole room
        // would mint a fresh id every time somebody set a torch down, and nothing here
        // noticed that when it was tried - so it is asserted rather than assumed.
        long rightId = right.id();
        helper.setBlock(origin.offset(width - 2, 1, 1),
                ModBlocks.HULL_PLATE.get().defaultBlockState());
        atmosphere.invalidate(helper.absolutePos(origin.offset(width - 2, 1, 1)));
        RoomState afterBuilding = atmosphere.roomAt(helper.absolutePos(rightSeed));
        if (afterBuilding == null || afterBuilding.id() != rightId) {
            helper.fail("Putting one block down inside a room renamed it from #" + rightId
                    + " to #" + (afterBuilding == null ? "gone" : afterBuilding.id())
                    + " - every readout about it would start over");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I built two sealed rooms sharing a wall - one running hot from a machine, one
     * exposed and cold - and put an ammonia heat pipe through the wall between them. Does
     * heat actually move from the hot side to the cold side over real time, the way the
     * design doc says a real ammonia heat pipe does - or does the block just sit there?"</em>
     *
     * <p>The real geometric case {@code design/ammonia-heat-pipes.md} §2 names: two distinct
     * rooms on either end of the pipe's own axis, a real temperature difference, and heat
     * genuinely flowing from the hotter room into the colder one - never the reverse, never
     * conjured from nothing.
     */
    private static void ammoniaHeatPipeMovesRealHeatBetweenTwoRooms(GameTestHelper helper) {
        // Same "one box, walled down the middle" shape twoSealedRoomsAreNotOneRoom already
        // proved builds two real rooms - a heat pipe takes the door's own place in the wall.
        BlockPos origin = new BlockPos(1, 1, 1);
        int width = 8;
        int depth = 3;
        // Solid rock one layer past every face first: Shell.skyFraction() is a purely local
        // check ("is the cell just past this wall solid"), nothing to do with real sunlight -
        // so an exposed shell here reads real radiative loss to the 2.7 K sky, a quartic term
        // easily a kilowatt for a room this size, which would swamp the pipe's own few hundred
        // watts regardless of what the pipe does. Buried on every face makes this test actually
        // about the pipe.
        for (int x = -1; x <= width; x++) {
            for (int y = -1; y <= depth + 2; y++) {
                for (int z = -1; z <= depth + 2; z++) {
                    helper.setBlock(origin.offset(x, y, z), Blocks.STONE.defaultBlockState());
                }
            }
        }
        // Insulated plate, not bare: this test isolates the pipe's own transfer, and even a
        // fully buried BARE_HULL_U shell conducts real kilowatts into the 213 K rock - more
        // than the pipe itself moves. Insulated is the physically honest way to get a base
        // quiet enough to see the pipe do something, and it is what the design (bury,
        // insulate, and be in there working) already asks a player to build anyway.
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < depth + 2; y++) {
                for (int z = 0; z < depth + 2; z++) {
                    boolean shell = y == 0 || y == depth + 1 || z == 0 || z == depth + 1
                            || x == 0 || x == width - 1;
                    helper.setBlock(origin.offset(x, y, z), shell
                            ? ModBlocks.INSULATED_HULL_PLATE.get().defaultBlockState()
                            : Blocks.AIR.defaultBlockState());
                }
            }
        }
        // Two plates thick, not one - Shell's own documented exception ("a two-block-thick
        // wall reads as buried, because the cell past the first plate is the second plate")
        // is exactly the tool needed here. A single-thick divider sits between two rooms'
        // *air*, not rock, so RoomScanner.isBuried reads every one of its faces as exposed
        // to the 2.7 K sky on both sides - a phantom radiative loss between two interior
        // rooms that would swamp the pipe just as badly as the outer shell did unburied.
        // A second layer behind the first gives each room a real solid neighbour again.
        // The pipe itself must stay a single-block bridge to touch both rooms' air directly,
        // so its one row is tunnelled through both layers - the two faces touching the pipe
        // stay honestly exposed (the pipe is not rock either), but that is 1 of ~50 faces
        // per room rather than all 9.
        int dividerNear = 4;
        int dividerFar = 5;
        int pipeY = 1;
        int pipeZ = 2;
        for (int y = 1; y <= depth; y++) {
            for (int z = 1; z <= depth; z++) {
                boolean pipeRow = y == pipeY && z == pipeZ;
                helper.setBlock(origin.offset(dividerNear, y, z), pipeRow
                        ? Blocks.AIR.defaultBlockState()
                        : ModBlocks.INSULATED_HULL_PLATE.get().defaultBlockState());
                helper.setBlock(origin.offset(dividerFar, y, z), pipeRow
                        ? Blocks.AIR.defaultBlockState()
                        : ModBlocks.INSULATED_HULL_PLATE.get().defaultBlockState());
            }
        }
        BlockPos pipePos = origin.offset(dividerNear, pipeY, pipeZ);
        helper.setBlock(pipePos, ModBlocks.AMMONIA_HEAT_PIPE.get().defaultBlockState()
                .setValue(play.xponer.astronima.block.AmmoniaHeatPipeBlock.AXIS, Direction.Axis.X));

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        BlockPos hotSeed = origin.offset(1, 1, 2);
        BlockPos coldSeed = origin.offset(width - 2, 1, 2);

        // One tick to let the flood of setBlock calls above finish settling, then drive the
        // whole measurement synchronously (no further real-tick waiting) rather than reading
        // back after a real-time delay. A batch runs many gametests sharing one dimension and
        // therefore one Atmosphere; another test's own calibration loop (a raw, unthrottled
        // "for (i<200) atmosphere.tick()", the same pattern used a few methods up to measure
        // passive loss in isolation) touches every room in the shared map, not just its own -
        // over a multi-tick real-time window that is real contamination, not a bug in the pipe
        // itself (HeatPipeTransfer's own numbers were hand-verified correct via a debug trace
        // before this rewrite). Doing our own ticking with direct calls, all within one
        // callback, removes the window such contamination could land in.
        helper.runAfterDelay(1, () -> {
            // AmmoniaHeatPipeBlockEntity.serverTick only acts when the world's own game time
            // is a multiple of Atmosphere.TICK_INTERVAL - real, since it must share that
            // cadence with relaxTemperature. Landing on that boundary once, then driving every
            // cycle from here with direct calls (game time does not advance between them),
            // means the pipe's own gate never has to be fought or bypassed.
            long gt = helper.getLevel().getGameTime();
            long toBoundary = (Atmosphere.TICK_INTERVAL - gt % Atmosphere.TICK_INTERVAL)
                    % Atmosphere.TICK_INTERVAL;
            // Never 0: GameTestInfo.tickInternal() checks its scheduled callbacks with a live
            // iterator over the SAME map a nested runAfterDelay call (like this one, called from
            // inside another runAfterDelay's own callback) inserts into - the new entry is not
            // visible to that iterator until the NEXT tick's fresh pass, so a target equal to the
            // tick being processed right now actually fires one tick late. Every other target
            // value is unaffected (rule: see PLAN.md's own entry for this).
            if (toBoundary == 0) {
                toBoundary = Atmosphere.TICK_INTERVAL;
            }
            helper.runAfterDelay(toBoundary, () -> {
                atmosphere.invalidate(helper.absolutePos(hotSeed));
                atmosphere.invalidate(helper.absolutePos(coldSeed));
                RoomState hot = atmosphere.roomAt(helper.absolutePos(hotSeed));
                RoomState cold = atmosphere.roomAt(helper.absolutePos(coldSeed));
                if (hot == null || cold == null || hot.id() == cold.id()) {
                    helper.fail("Setup failed: the two sides of the pipe are not two distinct "
                            + "rooms (hot=" + hot + ", cold=" + cold + ")");
                    return;
                }

                hot.setTemperatureK(350.0);
                cold.setTemperatureK(250.0);
                double hotBefore = hot.temperatureK();
                double coldBefore = cold.temperatureK();

                // Twenty-five cycles, not three: the real per-cycle signal here is small (order
                // 0.0007 K for the cold room) next to a shared, batch-wide Atmosphere where the
                // ordinary per-tick handler can legitimately apply one more or one fewer cycle
                // than this loop's own count depending on exactly where this callback's boundary-
                // aligned tick lands relative to that handler's own firing within the same tick.
                // Twenty-five cycles of a real, steady, reliably-signed effect swamps that one-
                // cycle ambiguity; three did not, and one real run measured a false negative of
                // barely a thousandth of a kelvin from exactly this edge.
                BlockPos absolutePipePos = helper.absolutePos(pipePos);
                for (int cycle = 0; cycle < 25; cycle++) {
                    BlockEntity entity = helper.getLevel().getBlockEntity(absolutePipePos);
                    if (entity instanceof AmmoniaHeatPipeBlockEntity pipeEntity) {
                        pipeEntity.serverTick(helper.getLevel(), absolutePipePos,
                                helper.getLevel().getBlockState(absolutePipePos));
                    }
                    atmosphere.tick();
                }

                RoomState hotNow = atmosphere.roomAt(helper.absolutePos(hotSeed));
                RoomState coldNow = atmosphere.roomAt(helper.absolutePos(coldSeed));
                if (hotNow == null || coldNow == null) {
                    helper.fail("One of the two rooms stopped existing while the pipe was working");
                    return;
                }
                if (!(hotNow.temperatureK() < hotBefore)) {
                    helper.fail("The hot room did not cool at all through the pipe: "
                            + hotBefore + " -> " + hotNow.temperatureK());
                    return;
                }
                if (!(coldNow.temperatureK() > coldBefore)) {
                    helper.fail("The cold room did not warm at all through the pipe: "
                            + coldBefore + " -> " + coldNow.temperatureK());
                    return;
                }
                if (hotNow.temperatureK() < coldNow.temperatureK()) {
                    helper.fail("The pipe overshot equilibrium: hot room is now "
                            + hotNow.temperatureK() + " K, colder than the cold room's "
                            + coldNow.temperatureK() + " K");
                    return;
                }
                helper.succeed();
            });
        });
    }

    /**
     * Regression guard for architecture rule 68: {@code AmmoniaHeatPipeBlock}'s slim body
     * ({@code design/wire-parts.md}'s "visible and slim") falls through
     * {@code AirBlockKinds.classify}'s generic shape rule into {@code LEAKY} unless explicitly
     * overridden — and a leaky boundary is a real hole {@code Atmosphere.tick} pumps gas through
     * every interval, defeating the whole point of a <em>sealed</em> conductor. Two rooms with
     * very different gas fills, bridged only by the pipe: if either room's own gas total drifts
     * at all, the pipe is leaking pressure it has no business touching.
     */
    private static void ammoniaHeatPipeDoesNotLeakGasBetweenRooms(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        int width = 6;
        int depth = 3;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < depth + 2; y++) {
                for (int z = 0; z < depth + 2; z++) {
                    boolean shell = y == 0 || y == depth + 1 || z == 0 || z == depth + 1
                            || x == 0 || x == width - 1;
                    helper.setBlock(origin.offset(x, y, z), shell
                            ? ModBlocks.HULL_PLATE.get().defaultBlockState()
                            : Blocks.AIR.defaultBlockState());
                }
            }
        }
        int divider = 3;
        for (int y = 1; y <= depth; y++) {
            for (int z = 1; z <= depth; z++) {
                helper.setBlock(origin.offset(divider, y, z),
                        ModBlocks.HULL_PLATE.get().defaultBlockState());
            }
        }
        BlockPos pipePos = origin.offset(divider, 1, 1);
        helper.setBlock(pipePos, ModBlocks.AMMONIA_HEAT_PIPE.get().defaultBlockState()
                .setValue(play.xponer.astronima.block.AmmoniaHeatPipeBlock.AXIS, Direction.Axis.X));

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        BlockPos roomASeed = origin.offset(1, 1, 1);
        BlockPos roomBSeed = origin.offset(width - 2, 1, 1);

        helper.runAfterDelay(1, () -> {
            atmosphere.invalidate(helper.absolutePos(roomASeed));
            atmosphere.invalidate(helper.absolutePos(roomBSeed));
            RoomState roomA = atmosphere.roomAt(helper.absolutePos(roomASeed));
            RoomState roomB = atmosphere.roomAt(helper.absolutePos(roomBSeed));
            if (roomA == null || roomB == null || roomA.id() == roomB.id()) {
                helper.fail("Setup failed: the two sides of the pipe are not two distinct rooms "
                        + "(roomA=" + roomA + ", roomB=" + roomB + ")");
                return;
            }

            for (Gas gas : Gas.values()) {
                roomA.removeGas(gas, roomA.gases().get(gas));
                roomB.removeGas(gas, roomB.gases().get(gas));
            }
            roomA.addGasAt(Gas.OXYGEN, 100.0, 293.15);
            roomB.addGasAt(Gas.NITROGEN, 40.0, 293.15);
            double oxygenBefore = roomA.gases().get(Gas.OXYGEN);
            double nitrogenBefore = roomB.gases().get(Gas.NITROGEN);

            BlockPos absolutePipePos = helper.absolutePos(pipePos);
            for (int cycle = 0; cycle < 3; cycle++) {
                BlockEntity entity = helper.getLevel().getBlockEntity(absolutePipePos);
                if (entity instanceof AmmoniaHeatPipeBlockEntity pipeEntity) {
                    pipeEntity.serverTick(helper.getLevel(), absolutePipePos,
                            helper.getLevel().getBlockState(absolutePipePos));
                }
                atmosphere.tick();
            }

            RoomState roomANow = atmosphere.roomAt(helper.absolutePos(roomASeed));
            RoomState roomBNow = atmosphere.roomAt(helper.absolutePos(roomBSeed));
            if (roomANow == null || roomBNow == null) {
                helper.fail("One of the two rooms stopped existing while the pipe was sealed");
                return;
            }
            if (roomANow.gases().get(Gas.NITROGEN) > 1e-9) {
                helper.fail("Nitrogen crossed from the far room into the oxygen room through a "
                        + "supposedly sealed pipe: " + roomANow.gases().get(Gas.NITROGEN) + " mol");
                return;
            }
            if (roomBNow.gases().get(Gas.OXYGEN) > 1e-9) {
                helper.fail("Oxygen crossed from the near room into the nitrogen room through a "
                        + "supposedly sealed pipe: " + roomBNow.gases().get(Gas.OXYGEN) + " mol");
                return;
            }
            if (Math.abs(roomANow.gases().get(Gas.OXYGEN) - oxygenBefore) > 1e-6) {
                helper.fail("The oxygen room's own oxygen changed with nothing to explain it: "
                        + oxygenBefore + " -> " + roomANow.gases().get(Gas.OXYGEN));
                return;
            }
            if (Math.abs(roomBNow.gases().get(Gas.NITROGEN) - nitrogenBefore) > 1e-6) {
                helper.fail("The nitrogen room's own nitrogen changed with nothing to explain it: "
                        + nitrogenBefore + " -> " + roomBNow.gases().get(Gas.NITROGEN));
                return;
            }
            helper.succeed();
        });
    }

    /**
     * <em>"There is no ice anywhere. Where does water come from?"</em>
     *
     * <p>Out of the rock, which is more than half hydrated silicate. This is the whole
     * point of the retort and it is asserted the way a player would see it: the humidity of
     * the room next door goes up. Not the block's internal counter — the air, which is what
     * the analyzer reads and what a dehumidifier turns into bottles.
     *
     * <p>Two halves rather than one, because either alone would pass for a broken machine: it
     * produces <em>in the window</em>, and it refuses to run at all with nowhere to send the
     * steam. (The mirror's own overheating-ruins-the-charge physics — the reason the window
     * exists at all — is proven directly against {@code Dehydroxylation.bake} in
     * {@code DehydroxylationTest#fullFocusRuinsTheChargeRatherThanBeingTheBestPlay}, since the
     * mirror now aims itself and no player action can drive it to full focus any more.)
     */
    private static void bakingRockPutsWaterIntoTheAir(GameTestHelper helper) {
        BlockPos retortPos = new BlockPos(2, 3, 2);
        sealPocketUnder(helper, retortPos);
        helper.setBlock(retortPos, ModBlocks.SOLAR_RETORT.get().defaultBlockState());

        var retort = helper.getBlockEntity(retortPos, SolarRetortBlockEntity.class);
        if (retort == null) {
            helper.fail("The retort has no block entity");
            return;
        }
        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(retortPos.below()));

        RoomState room = atmosphere.roomAt(helper.absolutePos(retortPos.below()));
        if (room == null) {
            helper.fail("The pocket under the retort is not a room, so this scenario"
                    + " cannot test anything about where steam goes");
            return;
        }
        if (retort.sunlight() <= 0) {
            helper.fail("No sunlight on a retort under open sky at noon - the mirror can"
                    + " never work and every other assertion here is vacuous");
            return;
        }

        // No focus call: the mirror aims itself (SolarRetortBlockEntity#focus()) from
        // whatever process() the loaded charge asks for.
        retort.setItem(SolarRetortBlockEntity.SLOT_INPUT,
                new ItemStack(ModItems.TAILINGS.get(), 4));

        double before = room.gases().get(Gas.WATER_VAPOR);
        runMachine(retort, SolarRetortBlockEntity.BATCH_WORK + 40);
        double after = room.gases().get(Gas.WATER_VAPOR);

        if (!(after > before + 1e-6)) {
            helper.fail("A full batch of tailings baked in the window put no water into"
                    + " the room: " + before + " -> " + after + " mol, state="
                    + retort.workState() + " " + whereTheSteamWouldGo(helper, retortPos));
            return;
        }
        if (retort.getItem(SolarRetortBlockEntity.SLOT_OUTPUT).isEmpty()) {
            helper.fail("The retort gave up its water but left no dried rock behind");
            return;
        }

        // And with nowhere for the steam to go it must stall and say so, rather than
        // quietly baking water into a vacuum where the player never finds out.
        BlockPos exposed = new BlockPos(6, 3, 6);
        helper.setBlock(exposed, ModBlocks.SOLAR_RETORT.get().defaultBlockState());
        var orphan = helper.getBlockEntity(exposed, SolarRetortBlockEntity.class);
        if (orphan == null) {
            helper.fail("The second retort has no block entity");
            return;
        }
        orphan.setItem(SolarRetortBlockEntity.SLOT_INPUT,
                new ItemStack(ModItems.TAILINGS.get(), 4));

        // Roofed over first: a mirror with no sky is not a heater, and the machine has to
        // say *that* rather than blame the feed - the remedy is a placement, and a player
        // told "no feed" would keep shovelling rock into a machine that can never run.
        helper.setBlock(exposed.above(), ModBlocks.HULL_PLATE.get().defaultBlockState());
        runMachine(orphan, 5);
        if (orphan.workState() != WorkState.UNLIT) {
            helper.fail("A retort with a roof over its mirror reported "
                    + orphan.workState() + " instead of saying it has no sunlight (sun="
                    + orphan.sunlight() + ", canSeeSky="
                    + helper.getLevel().canSeeSky(helper.absolutePos(exposed).above())
                    + ", above=" + helper.getLevel().getBlockState(helper.absolutePos(exposed).above())
                    + ", skyLight=" + helper.getLevel().getBrightness(
                        net.minecraft.world.level.LightLayer.SKY, helper.absolutePos(exposed).above()) + ")");
            return;
        }
        helper.setBlock(exposed.above(), Blocks.AIR.defaultBlockState());

        runMachine(orphan, 40);
        if (orphan.workState() != WorkState.BACKPRESSURE) {
            helper.fail("A retort with no sealed room to vent into reported "
                    + orphan.workState() + " instead of saying its steam has nowhere to go");
            return;
        }
        if (orphan.work() > 0) {
            helper.fail("A retort with nowhere to vent still advanced its batch, so the"
                    + " player would watch a bar fill and receive nothing");
            return;
        }
        helper.succeed();
    }

    /** Runs a machine's own server tick, at the cadence the game runs it. */
    /**
     * Every room the retort could be venting into, and what each one says.
     *
     * <p>Written to chase a failure that appeared roughly one run in three with the same
     * message every time — which is the least useful shape a report can have, because
     * "no water and BACKPRESSURE" is consistent with the pocket being gone, the pocket
     * being unsealed, or the machine having picked a completely different room next door.
     */
    private static String whereTheSteamWouldGo(GameTestHelper helper, BlockPos retortPos) {
        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        BlockPos retortAbs = helper.absolutePos(retortPos);
        StringBuilder report = new StringBuilder("[near=" + describe(
                atmosphere.readingNear(retortAbs)));
        for (Direction dir : Direction.values()) {
            report.append(' ').append(dir).append('=')
                    .append(describe(atmosphere.readingAt(retortAbs.relative(dir))));
        }
        return report.append(']').toString();
    }

    private static String describe(Atmosphere.@org.jspecify.annotations.Nullable
            RoomReading reading) {
        if (reading == null) {
            return "-";
        }
        return "#" + reading.state().id() + "/v" + reading.state().volumeBlocks()
                + (reading.sealed() ? "/sealed" : "/leaky")
                + (reading.openToSpace() ? "/toSpace" : "")
                + String.format("/o2=%.1f", reading.state().partialPressureKPa(Gas.OXYGEN));
    }

    private static void runMachine(ProcessingBlockEntity machine, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            machine.serverTick();
        }
    }

    /** A sealed one-block pocket of air directly under {@code pos}, shelled in hull plate. */
    private static void sealPocketUnder(GameTestHelper helper, BlockPos pos) {
        BlockPos air = pos.below();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos at = air.offset(dx, dy, dz);
                    helper.setBlock(at, dx == 0 && dy == 0 && dz == 0
                            ? Blocks.AIR.defaultBlockState()
                            : ModBlocks.HULL_PLATE.get().defaultBlockState());
                }
            }
        }
    }

    /**
     * <em>"I mined the pump out while it was running."</em>
     *
     * <p>Test plan §1.4: a pump broken mid-run stops, leaves no orphaned state acting on
     * the world, and every mole is where it was — in the room or the tank, not vanished
     * with the block. The fill must have genuinely started first, or "nothing broke"
     * proves nothing (the broken-tank lesson).
     */
    private static void breakingAPumpMidRun(GameTestHelper helper) {
        Rig rig = Rig.build(helper, 4);
        rig.run(60);
        double tankMid = rig.tank.pressureKPa();
        if (!(tankMid > 1)) {
            helper.fail("The rig never started filling, so breaking it proves nothing");
            return;
        }
        double totalMid = totalIn(helper, rig.inside(), rig.tankPos());

        helper.setBlock(rig.pump, Blocks.AIR.defaultBlockState());
        Atmosphere.get(helper.getLevel()).invalidate(helper.absolutePos(rig.pump));
        // The block is gone; nothing should keep pumping. Tick the world's own paths a
        // few times rather than the pump's (it no longer exists to tick).
        for (int i = 0; i < 5; i++) {
            Atmosphere.get(helper.getLevel()).tick();
        }
        double tankAfter = rig.tank.pressureKPa();
        if (Math.abs(tankAfter - tankMid) > tankMid * 0.02) {
            helper.fail("A broken pump kept changing its tank: " + tankMid + " -> "
                    + tankAfter + " kPa");
            return;
        }
        double totalAfter = totalIn(helper, rig.inside(), rig.tankPos());
        if (Math.abs(totalAfter - totalMid) > Math.max(1e-6, totalMid * 0.02)) {
            helper.fail("Breaking a pump changed the total gas from " + totalMid
                    + " to " + totalAfter + " mol");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I set the port into the wall the wrong way, facing the rock."</em>
     *
     * <p>Test plan §2: it reads no room, resolves no network, and nothing crashes — the
     * port is simply not connected, which is what the indicator will say.
     */
    private static void portIntoSolidRock(GameTestHelper helper) {
        BlockPos rock = new BlockPos(2, 2, 2);
        BlockPos port = new BlockPos(2, 2, 3);
        helper.setBlock(rock, Blocks.STONE.defaultBlockState());
        helper.setBlock(port, ModBlocks.GAS_PORT.get().defaultBlockState()
                .setValue(GasPortBlock.FACING, Direction.NORTH)); // opens into the stone
        helper.setBlock(port.relative(Direction.SOUTH), ModBlocks.GAS_PIPE.get().defaultBlockState());

        PipeNetworks.Resolved run = PipeNetworks.resolveSide(
                helper.getLevel(), helper.absolutePos(port));
        if (run != null) {
            helper.fail("A port facing solid rock still resolved " + run.roomCount()
                    + " room(s) - it should be simply not connected");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I sealed myself into a coffin exactly one block big."</em>
     *
     * <p>Test plan §1.1: the smallest possible room works — it holds gas, reads a finite
     * pressure, and nothing divides by zero. Grim, but legal.
     */
    private static void oneBlockRoom(GameTestHelper helper) {
        BlockPos inside = new BlockPos(2, 2, 2);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = inside.offset(dx, dy, dz);
                    helper.setBlock(pos, pos.equals(inside)
                            ? Blocks.AIR.defaultBlockState()
                            : ModBlocks.HULL_PLATE.get().defaultBlockState());
                }
            }
        }
        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(inside));
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        if (room == null) {
            helper.fail("A one-block enclosure did not become a room");
            return;
        }
        if (room.volumeBlocks() != 1) {
            helper.fail("A one-block room reads volume " + room.volumeBlocks());
            return;
        }
        room.addGasAt(Gas.OXYGEN, 5, 293.15);
        double pressure = room.pressureKPa();
        if (!Double.isFinite(pressure) || pressure <= 0) {
            helper.fail("A one-block room read a pressure of " + pressure);
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I ran my pipe past the pump and it drew an arm into its side."</em>
     *
     * <p>A pump has exactly one suction and one discharge, along its facing. A pipe next
     * to its flank must not join it — an arm into a face that takes nothing reads as
     * plumbing and works as decoration, the same lie the valve's side used to tell.
     */
    private static void pipeJoinsAPumpOnlyAtItsEnds(GameTestHelper helper) {
        BlockPos pump = new BlockPos(2, 2, 2);
        BlockPos flankPipe = pump.relative(Direction.EAST);
        BlockPos endPipe = pump.relative(Direction.NORTH); // the suction face
        // Pipes first, pump second: placing the pump notifies its neighbours, which is
        // the update path a player's build actually exercises (setBlock skips the
        // placement logic that would have computed the arms on the pipe itself).
        helper.setBlock(flankPipe, ModBlocks.GAS_PIPE.get().defaultBlockState());
        helper.setBlock(endPipe, ModBlocks.GAS_PIPE.get().defaultBlockState());
        helper.setBlock(pump, ModBlocks.GAS_PUMP.get().defaultBlockState()
                .setValue(GasPumpBlock.FACING, Direction.SOUTH)); // flow along Z

        if (helper.getBlockState(flankPipe).getValue(GasPipeBlock.WEST)) {
            helper.fail("A pipe on the pump's flank drew an arm into a face that"
                    + " takes nothing");
            return;
        }
        if (!helper.getBlockState(endPipe).getValue(GasPipeBlock.SOUTH)) {
            helper.fail("A pipe on the pump's suction face did not join it");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I put the valve down first, then built the line through it, and the pump saw
     * nothing."</em>
     *
     * <p>Reported from play. A valve placed in open air takes the axis the player faced;
     * building the run across it later left it broadside and severed, with nothing saying
     * so. It must snap into line when the run appears — but a valve deliberately turned
     * with the wrench and actually carrying gas must never be overruled. Both halves here.
     */
    private static void valveSnapsToTheRunBuiltAroundIt(GameTestHelper helper) {
        // A stranded valve: axis X, nothing joined to it.
        BlockPos valve = new BlockPos(3, 2, 3);
        helper.setBlock(valve, ModBlocks.GAS_VALVE.get().defaultBlockState()
                .setValue(GasValveBlock.AXIS, Direction.Axis.X)
                .setValue(GasValveBlock.SETTING, Valve.SETTINGS - 1));

        // Now build the run through it along Z — the way a player extends a line.
        helper.setBlock(valve.relative(Direction.NORTH), ModBlocks.GAS_PIPE.get().defaultBlockState());
        helper.setBlock(valve.relative(Direction.SOUTH), ModBlocks.GAS_PIPE.get().defaultBlockState());

        Direction.Axis snapped = helper.getBlockState(valve).getValue(GasValveBlock.AXIS);
        if (snapped != Direction.Axis.Z) {
            helper.fail("A stranded valve did not snap into the run built through it;"
                    + " axis is still " + snapped);
            return;
        }

        // A valve already carrying the run must keep the axis it was given: build a second
        // one in line on Z, then add a pipe on its X flank. Z is still joined, so it stays.
        BlockPos held = new BlockPos(3, 2, 8);
        helper.setBlock(held, ModBlocks.GAS_VALVE.get().defaultBlockState()
                .setValue(GasValveBlock.AXIS, Direction.Axis.Z)
                .setValue(GasValveBlock.SETTING, Valve.SETTINGS - 1));
        helper.setBlock(held.relative(Direction.NORTH), ModBlocks.GAS_PIPE.get().defaultBlockState());
        helper.setBlock(held.relative(Direction.EAST), ModBlocks.GAS_PIPE.get().defaultBlockState());
        if (helper.getBlockState(held).getValue(GasValveBlock.AXIS) != Direction.Axis.Z) {
            helper.fail("A valve already in line was overruled by a pipe on its flank");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"What happens if I stick a pipe on the side of a valve, not its end?"</em>
     *
     * <p>A valve is an inline fitting: it should conduct only along its axis, so a run
     * reaching its flank must not pass through. Builds one port into a room and puts a valve
     * against it — in line, the room is reachable; turned across the run, it is not, which
     * is the whole point of the valve having an axis.
     */
    private static void valveConductsOnlyAlongItsAxis(GameTestHelper helper) {
        BlockPos inside = new BlockPos(2, 2, 2);
        ModTestFunctions.buildBoxAround(helper, inside);
        BlockPos port = new BlockPos(2, 2, 4);   // south wall
        BlockPos valve = new BlockPos(2, 2, 5);  // just outside, on the port's back face
        helper.setBlock(port, ModBlocks.GAS_PORT.get().defaultBlockState()
                .setValue(GasPortBlock.FACING, Direction.NORTH));
        Atmosphere.get(helper.getLevel()).invalidate(helper.absolutePos(port));

        // In line with the run (axis Z): the room is reachable through the valve.
        helper.setBlock(valve, openValve(Direction.Axis.Z));
        PipeNetworks.Resolved inLine =
                PipeNetworks.resolveSide(helper.getLevel(), helper.absolutePos(valve));
        if (inLine == null || inLine.roomCount() != 1) {
            helper.fail("A valve in line with the port did not reach the room: "
                    + (inLine == null ? "no network" : inLine.roomCount() + " rooms"));
            return;
        }
        // Turned across the run (axis X): the port is on the valve's flank, so nothing
        // conducts and the room is out of reach.
        helper.setBlock(valve, openValve(Direction.Axis.X));
        PipeNetworks.Resolved across =
                PipeNetworks.resolveSide(helper.getLevel(), helper.absolutePos(valve));
        if (across != null) {
            helper.fail("A port on the valve's side conducted through it: "
                    + across.roomCount() + " rooms reached across the axis");
            return;
        }
        helper.succeed();
    }

    private static net.minecraft.world.level.block.state.BlockState openValve(Direction.Axis axis) {
        return ModBlocks.GAS_VALVE.get().defaultBlockState()
                .setValue(GasValveBlock.AXIS, axis)
                .setValue(GasValveBlock.SETTING, play.xponer.astronima.sim.pipe.Valve.SETTINGS - 1);
    }

    /**
     * <em>"I press the panel, step out into vacuum, come back, press it again — and the
     * habitat's air is still in the tank, not in space."</em>
     *
     * <p>The whole airlock, driven through the real controller block against the real
     * chamber, pump, tank and doors. Asserts the three things the player cares about:
     * going out reaches vacuum with the air <em>recovered</em> (tank gained, only the
     * residue lost); coming home refills the chamber from the tank; and across the whole
     * trip gas is conserved except the vented residue. The door-safety invariant is
     * unit-tested exhaustively in AirlockCycleTest; here we watch it end to end.
     */
    private static void airlockFullCycleKeepsTheAir(GameTestHelper helper) {
        buildChamber(helper, 2, true, true);
        helper.setBlock(CONTROLLER, ModBlocks.AIRLOCK_CONTROLLER.get().defaultBlockState()
                .setValue(play.xponer.astronima.block.AirlockControllerBlock.FACING,
                        Direction.SOUTH));
        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(CHAMBER));

        RoomState chamber = atmosphere.roomAt(helper.absolutePos(CHAMBER));
        GasTankBlockEntity tank = helper.getBlockEntity(TANK, GasTankBlockEntity.class);
        var controller = helper.getBlockEntity(CONTROLLER,
                play.xponer.astronima.block.entity.AirlockControllerBlockEntity.class);
        if (chamber == null || tank == null || controller == null) {
            helper.fail("Setup failed: chamber, tank or controller missing");
            return;
        }
        for (Gas gas : Gas.values()) {
            chamber.removeGas(gas, chamber.gases().get(gas));
        }
        chamber.addGasAt(Gas.OXYGEN, 1200, 293.15);
        double startTotal = 1200;
        if (!commission(helper, controller)) {
            helper.fail("SCAN could not propose bindings for a complete airlock");
            return;
        }

        // Press the panel and let the sequence run: pump-down, vent, outer door releases.
        // Held to a time a player will actually stand through — this is the bar the old
        // driver hid by pumping twenty times faster than the server does.
        controller.command();
        int outTicks = runUntil(helper, controller, AirlockCycle.Phase.VACUUM,
                AirlockCycle.MAX_PUMP_TICKS + 200);
        if (outTicks < 0) {
            helper.fail("The cycle never reached vacuum; stuck in " + controller.phase()
                    + " chamber=" + controller.chamberFault()
                    + " cycle=" + controller.cycleFault());
            return;
        }
        if (outTicks > CYCLE_OUT_TICK_BUDGET) {
            helper.fail("Going out took " + outTicks + " ticks (" + (outTicks / 20)
                    + " s) — over the " + (CYCLE_OUT_TICK_BUDGET / 20)
                    + " s a player will stand through for one airlock cycle");
            return;
        }
        double inTank = tank.contents().gases().totalMoles();
        if (inTank < 1000) {
            helper.fail("Going out recovered only " + inTank
                    + " mol of 1200 - the air went somewhere other than the tank");
            return;
        }
        RoomState atVacuum = atmosphere.roomAt(helper.absolutePos(CHAMBER));
        if (atVacuum == null || atVacuum.pressureKPa() > 1.0) {
            helper.fail("The outer door released with the chamber still at "
                    + (atVacuum == null ? "?" : atVacuum.pressureKPa()) + " kPa");
            return;
        }

        // Press it again to come home: the chamber refills from the tank, for free — so
        // this leg must be visibly quicker than going out, which is the whole asymmetry.
        controller.command();
        var homeLegPhases = java.util.EnumSet.noneOf(AirlockCycle.Phase.class);
        int homeTicks = runUntil(helper, controller, AirlockCycle.Phase.SEALED, 600,
                homeLegPhases);
        if (homeTicks >= 0 && !homeLegPhases.contains(AirlockCycle.Phase.REPRESSURIZING)) {
            // Reaching SEALED is not the same claim as coming home. "Come Home on Stored
            // Air" fires on the REPRESSURIZING -> SEALED transition, so a return that got
            // to SEALED by any other route would pass the phase check and award nothing.
            helper.fail("The return reached SEALED without ever repressurising (saw "
                    + homeLegPhases + ") - the chamber was never refilled from the tank,"
                    + " and the advancement for it can never fire");
            return;
        }
        if (homeTicks < 0) {
            helper.fail("The return never completed; stuck in " + controller.phase()
                    + " cycle=" + controller.cycleFault());
            return;
        }
        if (homeTicks >= outTicks) {
            helper.fail("Coming home took " + homeTicks + " ticks against " + outTicks
                    + " going out — repressurising is meant to be the fast, free half");
            return;
        }
        RoomState home = atmosphere.roomAt(helper.absolutePos(CHAMBER));
        if (home == null) {
            helper.fail("The chamber stopped being a room during the return");
            return;
        }
        if (home.pressureKPa() < 80) {
            helper.fail("Coming home left the chamber at " + home.pressureKPa()
                    + " kPa - the stored air did not come back");
            return;
        }
        // Conserved across the trip, less only the vented residue.
        double endTotal = home.gases().totalMoles() + tank.contents().gases().totalMoles();
        double lost = startTotal - endTotal;
        if (lost < -1e-6) {
            helper.fail("The cycle created " + (-lost) + " mol of air from nothing");
            return;
        }
        if (lost > 90) {
            helper.fail("The cycle lost " + lost
                    + " mol - far more than the vented residue can explain");
            return;
        }
        helper.succeed();
    }

    /** Drives controller and pump ticks together until the phase, or gives up. */
    /**
     * Drives the airlock at the cadence the server really uses, and reports how many game
     * ticks it took to reach {@code target} (-1 if it never did).
     *
     * <p><strong>The pump is stepped once every {@code GasPumpBlockEntity.INTERVAL_TICKS},
     * not once per controller tick.</strong> The first version of this driver called
     * {@code pumpOnce} every iteration — twenty times faster than the game ever does — so
     * the cycle "completed" in the test while a player stood watching a chamber that took
     * over a minute and then tripped the backstop. A driver that runs the machinery faster
     * than the world does cannot answer the only question that matters here, which is
     * whether the thing is usable; ticks returned so the caller can hold it to a time.
     */
    private static int runUntil(GameTestHelper helper,
            play.xponer.astronima.block.entity.AirlockControllerBlockEntity controller,
            AirlockCycle.Phase target, int maxTicks) {
        return runUntil(helper, controller, target, maxTicks,
                java.util.EnumSet.noneOf(AirlockCycle.Phase.class));
    }

    /**
     * As above, but recording every phase it passed through into {@code seen}.
     *
     * <p>Needed because "ended up in SEALED" is not the same claim as "came home": the
     * advancement for returning on stored air fires on the REPRESSURIZING → SEALED
     * transition specifically, so a cycle that reached SEALED by some other route would
     * satisfy a naive assertion and award nothing.
     */
    private static int runUntil(GameTestHelper helper,
            play.xponer.astronima.block.entity.AirlockControllerBlockEntity controller,
            AirlockCycle.Phase target, int maxTicks,
            java.util.Set<AirlockCycle.Phase> seen) {
        BlockPos controllerAbs = helper.absolutePos(CONTROLLER);
        BlockPos pumpAbs = helper.absolutePos(PUMP);
        for (int tick = 0; tick < maxTicks; tick++) {
            seen.add(controller.phase());
            controller.tick(helper.getLevel(), controllerAbs,
                    helper.getLevel().getBlockState(controllerAbs));
            if (tick % GasPumpBlockEntity.INTERVAL_TICKS == 0) {
                GasPumpBlockEntity pump = helper.getBlockEntity(PUMP, GasPumpBlockEntity.class);
                if (pump != null) {
                    pump.pumpOnce(helper.getLevel(), pumpAbs,
                            helper.getLevel().getBlockState(pumpAbs));
                }
            }
            if (controller.phase() == target) {
                return tick;
            }
        }
        return -1;
    }

    /**
     * <em>"The server restarted while my airlock was at vacuum."</em>
     *
     * <p>L7 row: a phase is real state, so it must survive a save — an airlock that
     * reloads into SEALED with its chamber at vacuum would unlock the inner door onto the
     * habitat. Round-trips the controller mid-cycle through the world's save format and
     * asserts the phase and its door locks come back exactly.
     */
    private static void airlockPhaseSurvivesAReload(GameTestHelper helper) {
        buildChamber(helper, 2, true, true);
        helper.setBlock(CONTROLLER, ModBlocks.AIRLOCK_CONTROLLER.get().defaultBlockState()
                .setValue(play.xponer.astronima.block.AirlockControllerBlock.FACING,
                        Direction.SOUTH));
        Atmosphere.get(helper.getLevel()).invalidate(helper.absolutePos(CHAMBER));
        RoomState chamber = Atmosphere.get(helper.getLevel())
                .roomAt(helper.absolutePos(CHAMBER));
        var controller = helper.getBlockEntity(CONTROLLER,
                play.xponer.astronima.block.entity.AirlockControllerBlockEntity.class);
        if (chamber == null || controller == null) {
            helper.fail("Setup failed: chamber or controller missing");
            return;
        }
        chamber.addGasAt(Gas.OXYGEN, 1200, 293.15);
        commission(helper, controller);

        // Drive it into the middle of a pump-down, then to vacuum — the dangerous phase.
        controller.command();
        if (runUntil(helper, controller, AirlockCycle.Phase.VACUUM,
                AirlockCycle.MAX_PUMP_TICKS + 200) < 0) {
            helper.fail("Never reached vacuum; stuck in " + controller.phase());
            return;
        }

        // The reload: the same round-trip the world save does.
        var registries = helper.getLevel().registryAccess();
        try (var scope = new net.minecraft.util.ProblemReporter.ScopedCollector(
                com.mojang.logging.LogUtils.getLogger())) {
            var output = net.minecraft.world.level.storage.TagValueOutput
                    .createWithContext(scope, registries);
            controller.saveWithoutMetadata(output);
            var input = net.minecraft.world.level.storage.TagValueInput
                    .create(scope, registries, output.buildResult());

            var reloaded = new play.xponer.astronima.block.entity.AirlockControllerBlockEntity(
                    helper.absolutePos(CONTROLLER), helper.getBlockState(CONTROLLER));
            reloaded.loadWithComponents(input);

            if (reloaded.phase() != AirlockCycle.Phase.VACUUM) {
                helper.fail("An airlock at vacuum reloaded into " + reloaded.phase()
                        + " - the inner door would unlock onto the habitat");
                return;
            }
            if (!AirlockCycle.innerLocked(reloaded.phase())) {
                helper.fail("The reloaded phase does not keep the inner door locked");
                return;
            }
            // C2: the commissioning is state too. A panel that forgot which door the
            // player called the outer one after a reload would be worse than one that
            // never asked — it would come back blank and refuse to cycle.
            for (Role role : Role.values()) {
                long before = controller.bindings().device(role);
                long after = reloaded.bindings().device(role);
                if (before != after) {
                    helper.fail("The " + role + " terminal did not survive the reload: was "
                            + before + ", came back " + after);
                    return;
                }
                if (after == play.xponer.astronima.sim.airlock.DeviceBinding.NONE) {
                    helper.fail("The " + role + " terminal was never commissioned, so this"
                            + " reload proves nothing about bindings");
                    return;
                }
            }
        }
        helper.succeed();
    }

    /**
     * <em>"I shut the valve in the airlock's line and pressed the button."</em>
     *
     * <p>The valve shipped decorative once, so tying it to the airlock is worth a test: a
     * shut valve severs the pump from the chamber, so the pump-down cannot finish — and the
     * cycle must <strong>stall with a reason</strong> ("pump too slow"), never hang forever
     * and never open the outer door onto a chamber it failed to evacuate.
     */
    private static void airlockShutValveStallsNotHangs(GameTestHelper helper) {
        buildChamber(helper, 2, true, true);
        helper.setBlock(CONTROLLER, ModBlocks.AIRLOCK_CONTROLLER.get().defaultBlockState()
                .setValue(play.xponer.astronima.block.AirlockControllerBlock.FACING,
                        Direction.SOUTH));
        Atmosphere.get(helper.getLevel()).invalidate(helper.absolutePos(CHAMBER));
        RoomState chamber = Atmosphere.get(helper.getLevel())
                .roomAt(helper.absolutePos(CHAMBER));
        var controller = helper.getBlockEntity(CONTROLLER,
                play.xponer.astronima.block.entity.AirlockControllerBlockEntity.class);
        if (chamber == null || controller == null) {
            helper.fail("Setup failed");
            return;
        }
        chamber.addGasAt(Gas.OXYGEN, 1200, 293.15);
        // Commissioned on a working line first, then the valve is shut — which is the real
        // order of events, and the order that matters: a valve is operated after the panel
        // is built, so shutting one must read as a flow problem and never as a mis-bound
        // pump. Commissioning deliberately does not check reachability for exactly this.
        if (!commission(helper, controller)) {
            helper.fail("SCAN could not propose bindings before the valve was shut");
            return;
        }
        helper.setBlock(PIPE, ModBlocks.GAS_VALVE.get().defaultBlockState()
                .setValue(GasValveBlock.AXIS, Direction.Axis.Z)
                .setValue(GasValveBlock.SETTING, 0));

        controller.command();
        // Run past the pump-down backstop; it must never reach vacuum through a shut valve.
        BlockPos controllerAbs = helper.absolutePos(CONTROLLER);
        for (int i = 0; i < AirlockCycle.MAX_PUMP_TICKS + 40; i++) {
            controller.tick(helper.getLevel(), controllerAbs,
                    helper.getLevel().getBlockState(controllerAbs));
            GasPumpBlockEntity pump = helper.getBlockEntity(PUMP, GasPumpBlockEntity.class);
            if (pump != null) {
                pump.pumpOnce(helper.getLevel(), helper.absolutePos(PUMP),
                        helper.getLevel().getBlockState(helper.absolutePos(PUMP)));
            }
            if (controller.phase() == AirlockCycle.Phase.VACUUM) {
                helper.fail("A shut valve let the cycle reach vacuum on a chamber it never"
                        + " evacuated");
                return;
            }
        }
        if (controller.phase() != AirlockCycle.Phase.PUMPING
                || controller.cycleFault() != AirlockCycle.Fault.PUMP_TOO_SLOW) {
            helper.fail("A shut valve should stall the pump-down as 'pump too slow'; got "
                    + controller.phase() + "/" + controller.cycleFault());
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I worked this ground yesterday. Is it still poorer today?"</em>
     *
     * <p>design/astra-extraction-loop.md §4's own headline claim, and astra-core.md §13's
     * "does depletion survive a save/load and a chunk reload" — checkable for real for the first
     * time now that {@code AstraFieldStorage} exists. Round-tripped through the exact save format
     * the world uses, the same way {@code WireScenarios}' own {@code CODEC}/{@code NbtOps} proofs
     * already do for a wire part, and {@code storedAirSurvivesAReload} does for a block entity:
     * encode the real, swept storage, decode a fresh instance from that NBT, and ask the fresh
     * instance the identical question — no reliance on the live {@code DataStorage} cache still
     * holding the original object, which would prove nothing about the save format itself.
     */
    private static void astraDepletionSurvivesAReload(GameTestHelper helper) {
        play.xponer.astronima.astra.AstraFieldStorage storage =
                play.xponer.astronima.astra.AstraFieldStorage.get(helper.getLevel());

        int x = 100;
        int y = 20;
        int z = 100;
        double baseline = 1.0;
        // Deliberately not zero: a mutation that drops the saved timestamp and defaults it to
        // zero on reload must be visible here, not hidden by a coincidental match (rule 12).
        long sweepTime = 12_000L;
        double collected = storage.sweep(x, y, z, baseline, sweepTime);
        if (collected <= 0.0) {
            helper.fail("Nothing was collected, so the reload cannot be tested");
            return;
        }

        long laterTime = sweepTime + 5 * 20L; // five real seconds later, before any reload
        double beforeReload = storage.densityAt(x, y, z, baseline, laterTime);

        var encoded = play.xponer.astronima.astra.AstraFieldStorage.codec()
                .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, storage)
                .result().orElse(null);
        if (!(encoded instanceof net.minecraft.nbt.CompoundTag tag)) {
            helper.fail("could not encode a swept AstraFieldStorage to NBT at all");
            return;
        }

        var reloaded = play.xponer.astronima.astra.AstraFieldStorage.codec()
                .parse(net.minecraft.nbt.NbtOps.INSTANCE, tag)
                .result().orElse(null);
        if (reloaded == null) {
            helper.fail("a swept AstraFieldStorage failed to load back from its own save format");
            return;
        }

        double afterReload = reloaded.densityAt(x, y, z, baseline, laterTime);
        if (Math.abs(afterReload - beforeReload) > 1e-9) {
            helper.fail("depletion did not survive a save/load: " + beforeReload
                    + " became " + afterReload);
            return;
        }
        // And it must be genuinely depleted, not merely "unchanged from baseline" - a codec bug
        // that quietly dropped every cell would pass the line above by both sides reading baseline.
        if (afterReload >= baseline) {
            helper.fail("the reloaded reading was not actually depleted at all - "
                    + afterReload + " at baseline " + baseline);
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"A future ritual asks the ground for more than is really there. It must not get more
     * than the ground actually has."</em>
     *
     * <p>design/astra-ritual-grammar.md's own live draw needs a "draw a specific amount, not a
     * fixed fraction" primitive ({@link play.xponer.astronima.astra.AstraFieldStorage#drawAt}),
     * ahead of the block entity that will call it every real second — proven now, at the storage
     * layer, exactly the way {@link #astraDepletionSurvivesAReload} proved the sweep-based
     * primitive before either instrument existed.
     */
    private static void astraDrawAtNeverTakesMoreThanIsThere(GameTestHelper helper) {
        play.xponer.astronima.astra.AstraFieldStorage storage =
                play.xponer.astronima.astra.AstraFieldStorage.get(helper.getLevel());
        int x = 200;
        int y = 20;
        int z = 200;
        double baseline = 1.0;
        long now = helper.getLevel().getGameTime();

        // Drawing less than what is there takes exactly that much and leaves the rest.
        double firstDraw = storage.drawAt(x, y, z, baseline, 0.3, now);
        if (Math.abs(firstDraw - 0.3) > 1e-9) {
            helper.fail("drawAt(0.3) on a fresh baseline-1.0 cell returned " + firstDraw
                    + ", not exactly 0.3");
            return;
        }
        double afterFirst = storage.densityAt(x, y, z, baseline, now);
        if (Math.abs(afterFirst - 0.7) > 1e-9) {
            helper.fail("after drawing 0.3 from a fresh 1.0 cell the ground reads " + afterFirst
                    + ", not 0.7");
            return;
        }

        // Asking for far more than remains must be clamped to what is actually there, never
        // handed out for free and never driven negative.
        double secondDraw = storage.drawAt(x, y, z, baseline, 5.0, now);
        if (Math.abs(secondDraw - afterFirst) > 1e-9) {
            helper.fail("asking for 5.0 with only " + afterFirst + " left returned " + secondDraw
                    + " - a draw must clamp to what is really there, not invent supply");
            return;
        }
        double afterSecond = storage.densityAt(x, y, z, baseline, now);
        if (afterSecond < 0.0 || afterSecond > 1e-9) {
            helper.fail("the cell reads " + afterSecond + " after being drawn down to nothing - "
                    + "must be exactly (or negligibly above) zero, never negative");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I swept here. Is the ground actually poorer, or did the collector just make up a
     * number?"</em>
     *
     * <p>Rule 13's own trap, named directly in design/astra-extraction-loop.md §3.4's own "Proven
     * how" row: a collector's held amount matching nothing the world actually lost would be
     * exactly the dead valve rule 11 was written after — the right number, produced by a path the
     * game never takes. Entered through the real door: {@link
     * play.xponer.astronima.item.AstraCollectorItem#use}, the same method the game calls when a
     * player right-clicks with one in hand, the same shape
     * {@code spectrographExposesAPlateUnderOpenSky} already uses for the identical reason.
     */
    private static void astraCollectorReallyDepletesTheField(GameTestHelper helper) {
        // Deliberately NOT helper.absoluteVec(...): the gametest harness places each test's own
        // structure at a huge, arbitrary world offset (a real prior run failed at
        // (-1538510, -59, 12570795)), and AstraFieldGeometry's baseline is a function of real
        // distance from the world origin (the core's real position, asteroid-body.md). This
        // scenario places no blocks and never reads the world, so a literal absolute position
        // close to the origin is the correct way to reach real, non-zero density, not a shortcut.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(10.5, 20.0, 10.5);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(play.xponer.astronima.registry.ModItems.ASTRA_COLLECTOR.get()));

        double r = Math.hypot(player.getX(), player.getZ());
        double shellCoordinate = play.xponer.astronima.sim.world.AsteroidBody.shellCoordinate(r, player.getY());
        double baseline = play.xponer.astronima.sim.astra.AstraFieldGeometry.baselineDensity(shellCoordinate, 0.0);
        if (baseline <= 0.0) {
            helper.fail("Test setup produced zero baseline density - pick a different stand"
                    + " position so the sweep has something real to collect");
            return;
        }

        InteractionResult result = play.xponer.astronima.registry.ModItems.ASTRA_COLLECTOR.get()
                .use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        if (result != InteractionResult.SUCCESS) {
            helper.fail("The collector refused to activate: " + result);
            return;
        }

        play.xponer.astronima.sim.astra.AstraCharge charge = player.getMainHandItem().get(
                play.xponer.astronima.registry.ModDataComponents.ASTRA_HELD.get());
        if (charge == null || charge.amount() <= 0.0F) {
            helper.fail("The collector's own buffer shows nothing collected");
            return;
        }
        float held = charge.amount();

        double realAfter = play.xponer.astronima.astra.AstraFieldStorage.get(helper.getLevel())
                .densityAt(player.getBlockX(), player.getBlockY(), player.getBlockZ(),
                        baseline, helper.getLevel().getGameTime());
        if (realAfter >= baseline) {
            helper.fail("The collector reported holding " + held + " but the real field at that"
                    + " position was never actually depleted - baseline " + baseline
                    + ", still reading " + realAfter);
            return;
        }
        // And the two numbers must be the same fact, not two independently-plausible ones -
        // exactly what left the ground is exactly what the item now holds.
        double expectedCollected = play.xponer.astronima.sim.astra.AstraField.swept(baseline);
        if (Math.abs(held - expectedCollected) > 1e-6) {
            helper.fail("Collector held " + held + " but the field model's own swept() for this"
                    + " baseline predicts " + expectedCollected + " - the two have drifted apart");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I have gathered enough. Sneak-click, and I should get real matter, not a scripted
     * amount."</em>
     *
     * <p>design/astra-precipitation.md §5's own headline claim, entered through the real door:
     * {@link play.xponer.astronima.item.AstraCollectorItem#use} with the player sneaking, the
     * same interaction shape a player actually performs. The precondition (a sufficiently charged
     * buffer) is seeded directly rather than replayed through dozens of real sweeps — reaching
     * design/astra-precipitation.md §1's own threshold from one site is bounded by that site's own
     * baseline density and deliberately requires visiting more than one (the "wait or move"
     * mechanic, astra-core.md §4.1), which is a playtest/balance question this scenario is not
     * about. What this scenario is about is proven regardless of how the charge got there: the
     * conversion itself must match {@code AstraPrecipitation}'s own pure prediction exactly.
     */
    private static void astraCollectorPrecipitatesASufficientCharge(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(20.5, 20.0, 20.5);
        ItemStack collector = new ItemStack(play.xponer.astronima.registry.ModItems.ASTRA_COLLECTOR.get());
        long seedTime = helper.getLevel().getGameTime();
        double seededHeld = 5.5; // above design/astra-precipitation.md's own threshold (3.0)
        collector.set(play.xponer.astronima.registry.ModDataComponents.ASTRA_HELD.get(),
                new play.xponer.astronima.sim.astra.AstraCharge((float) seededHeld, seedTime));
        player.setItemInHand(InteractionHand.MAIN_HAND, collector);
        player.setShiftKeyDown(true);

        InteractionResult result = play.xponer.astronima.registry.ModItems.ASTRA_COLLECTOR.get()
                .use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        player.setShiftKeyDown(false);
        if (result != InteractionResult.SUCCESS) {
            helper.fail("The collector refused to precipitate: " + result);
            return;
        }

        int expectedYield = play.xponer.astronima.sim.astra.AstraPrecipitation.itemsYielded(seededHeld);
        double expectedRemainder =
                play.xponer.astronima.sim.astra.AstraPrecipitation.remainingAfterPrecipitation(seededHeld);

        int actuallyGiven = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(play.xponer.astronima.registry.ModItems.ASTERIUM_GRAINS.get())) {
                actuallyGiven += stack.getCount();
            }
        }
        if (actuallyGiven != expectedYield) {
            helper.fail("Precipitating " + seededHeld + " held astra gave the player "
                    + actuallyGiven + " grains, but AstraPrecipitation.itemsYielded predicts "
                    + expectedYield + " - the item and the model have drifted apart");
            return;
        }

        play.xponer.astronima.sim.astra.AstraCharge after = player.getMainHandItem()
                .get(play.xponer.astronima.registry.ModDataComponents.ASTRA_HELD.get());
        double remainderHeld = after == null ? 0.0 : after.amount();
        if (Math.abs(remainderHeld - expectedRemainder) > 1e-4) {
            helper.fail("The collector's remaining charge is " + remainderHeld + " but "
                    + expectedRemainder + " was expected — the fractional remainder was not "
                    + "conserved correctly");
            return;
        }

        // And the refusal path is honest too: a charge below threshold gives nothing and touches
        // nothing.
        ItemStack tooLittle = new ItemStack(play.xponer.astronima.registry.ModItems.ASTRA_COLLECTOR.get());
        tooLittle.set(play.xponer.astronima.registry.ModDataComponents.ASTRA_HELD.get(),
                new play.xponer.astronima.sim.astra.AstraCharge(1.0F, seedTime));
        player.setItemInHand(InteractionHand.OFF_HAND, tooLittle);
        player.setShiftKeyDown(true);
        play.xponer.astronima.registry.ModItems.ASTRA_COLLECTOR.get()
                .use(helper.getLevel(), player, InteractionHand.OFF_HAND);
        player.setShiftKeyDown(false);
        play.xponer.astronima.sim.astra.AstraCharge stillHeld = player.getOffhandItem()
                .get(play.xponer.astronima.registry.ModDataComponents.ASTRA_HELD.get());
        if (stillHeld == null || Math.abs(stillHeld.amount() - 1.0F) > 1e-4) {
            helper.fail("A charge below threshold must be left exactly as it was, not partially "
                    + "consumed by a refused precipitation attempt");
            return;
        }

        helper.succeed();
    }

    /**
     * <em>"I built two arms and forgot the boundary. Nothing should happen — and my anchors
     * should still be sitting there."</em>
     *
     * <p>design/astra-ritual-grammar.md §3's own reachability shape depends on this: a figure is
     * either complete or it costs nothing, never a half-consumed attempt. Entered through the
     * real door — {@code BlockState#useWithoutItem}, the same dispatch a right-click actually
     * uses, the same shape {@code aBulkheadDoorInterlockHoldsAgainstAYank} already uses for a
     * block interaction test.
     */
    private static void astraAltarRefusesAnIncompleteFigure(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        BlockPos altarPos = new BlockPos(5, 18, -50);
        level.setBlock(altarPos, play.xponer.astronima.registry.ModBlocks.ASTRA_ALTAR.get()
                .defaultBlockState(), 3);
        // Two real arms, no boundary at all.
        level.setBlock(altarPos.north(1), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(altarPos.north(2), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(altarPos.north(3), play.xponer.astronima.registry.ModBlocks.ASTERIUM_BLOCK.get()
                .defaultBlockState(), 3);
        level.setBlock(altarPos.east(1), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(altarPos.east(2), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(altarPos.east(3), play.xponer.astronima.registry.ModBlocks.ASTERIUM_BLOCK.get()
                .defaultBlockState(), 3);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        level.getBlockState(altarPos).useWithoutItem(level, player,
                new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(altarPos), Direction.UP, altarPos, false));

        if (!(level.getBlockEntity(altarPos)
                instanceof play.xponer.astronima.block.entity.AstraAltarBlockEntity altar)) {
            helper.fail("The altar has no block entity at all");
            return;
        }
        if (altar.phase() != play.xponer.astronima.block.entity.AstraAltarBlockEntity.Phase.IDLE) {
            helper.fail("An incomplete figure (no boundary) must refuse to activate, but the "
                    + "altar's phase is " + altar.phase());
            return;
        }
        if (!level.getBlockState(altarPos.north(3))
                .is(play.xponer.astronima.registry.ModBlocks.ASTERIUM_BLOCK.get())) {
            helper.fail("A refused activation must not consume any anchor - the north one is gone");
            return;
        }
        if (!level.getBlockState(altarPos.east(3))
                .is(play.xponer.astronima.registry.ModBlocks.ASTERIUM_BLOCK.get())) {
            helper.fail("A refused activation must not consume any anchor - the east one is gone");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I built a real figure over rich ground. It should draw live, for real, and give me
     * something back."</em>
     *
     * <p>design/astra-ritual-grammar.md §2/§4's own headline claim, end to end: activation
     * consumes the anchors, the ritual runs a real minute of real game time (the block's own
     * real {@code BlockEntityTicker}, not a call this test makes directly), and completing it
     * hands over real {@code asterium_grains} — matching {@code AstraPrecipitation}'s own
     * conversion of whatever the field really gave up, not a scripted amount.
     */
    private static void astraAltarActivatesAndCompletesARealRitual(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        // Deliberately near the real world origin - close to the core (0, 16, 0) per
        // asteroid-body.md, so the real baseline here is rich enough for a small figure to
        // actually sustain sixty real seconds of drawing, not merely close enough to test. At
        // CENTRE_Y exactly (y=16) so the vertical term of shellCoordinate drops out entirely, and
        // offset in x/z (not at x=0/z=0) so this figure's tap positions - AstraFieldStorage's
        // grid cells are 32 blocks wide (AstraGrid.CELL_SIZE_BLOCKS) - land in a different cell
        // than astraCollectorReallyDepletesTheField's and astraCollectorPrecipitatesASufficient-
        // Charge's stand positions, which both round down into the cell straddling the origin;
        // sharing a cell with either would silently pre-deplete this ritual's own pool depending
        // on gametest run order.
        // This is also *outside* this test's own gametest structure bounding box (that box lives
        // at whatever huge synthetic offset the test framework tiled it to), so unlike a
        // structure's own chunks it is not force-loaded by the framework - without forcing it
        // here, the block entity ticker below simply never fires and the ritual sits at 0.0s
        // forever. The whole figure (arms + boundary ring) fits inside this one forced chunk.
        BlockPos altarPos = new BlockPos(8, 16, -8);
        level.setChunkForced(altarPos.getX() >> 4, altarPos.getZ() >> 4, true);
        level.setBlock(altarPos, play.xponer.astronima.registry.ModBlocks.ASTRA_ALTAR.get()
                .defaultBlockState(), 3);
        level.setBlock(altarPos.north(1), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(altarPos.north(2), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(altarPos.north(3), play.xponer.astronima.registry.ModBlocks.ASTERIUM_BLOCK.get()
                .defaultBlockState(), 3);
        level.setBlock(altarPos.east(1), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(altarPos.east(2), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(altarPos.east(3), play.xponer.astronima.registry.ModBlocks.ASTERIUM_BLOCK.get()
                .defaultBlockState(), 3);
        // The boundary: a closed square ring at radius 4 (one past the longest arm).
        int radius = 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                    continue;
                }
                level.setBlock(altarPos.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        level.getBlockState(altarPos).useWithoutItem(level, player,
                new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(altarPos), Direction.UP, altarPos, false));

        if (!(level.getBlockEntity(altarPos)
                instanceof play.xponer.astronima.block.entity.AstraAltarBlockEntity altar)) {
            helper.fail("The altar has no block entity at all");
            return;
        }
        if (altar.phase() != play.xponer.astronima.block.entity.AstraAltarBlockEntity.Phase.RUNNING) {
            helper.fail("A real, complete figure over rich ground must start running, but the "
                    + "phase is " + altar.phase());
            return;
        }
        if (level.getBlockState(altarPos.north(3))
                .is(play.xponer.astronima.registry.ModBlocks.ASTERIUM_BLOCK.get())) {
            helper.fail("Activation must consume the anchors immediately, not leave them standing "
                    + "while the ritual runs");
            return;
        }

        // The ritual needs sixty real seconds (1200 ticks). Let the real BlockEntityTicker run it
        // - not a direct call to serverTick(), which would prove the method works without proving
        // the game ever actually calls it (rule 13's own trap).
        helper.runAfterDelay(1210, () -> {
            if (!(level.getBlockEntity(altarPos)
                    instanceof play.xponer.astronima.block.entity.AstraAltarBlockEntity finished)) {
                helper.fail("The altar's block entity disappeared while the ritual was running");
                return;
            }
            if (finished.phase() != play.xponer.astronima.block.entity.AstraAltarBlockEntity.Phase.COMPLETED) {
                helper.fail("A two-arm figure over rich, undisturbed ground should have completed "
                        + "within sixty real seconds, but its phase is " + finished.phase()
                        + " after " + finished.elapsedSeconds() + "s");
                return;
            }
            var drops = level.getEntities(net.minecraft.world.entity.EntityType.ITEM,
                    new net.minecraft.world.phys.AABB(altarPos).inflate(2.0),
                    net.minecraft.world.entity.Entity::isAlive);
            int totalGrains = 0;
            for (var entity : drops) {
                if (entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity
                        && itemEntity.getItem().is(play.xponer.astronima.registry.ModItems.ASTERIUM_GRAINS.get())) {
                    totalGrains += itemEntity.getItem().getCount();
                }
            }
            int expected = play.xponer.astronima.sim.astra.AstraPrecipitation
                    .itemsYieldedFromRitual(finished.totalDrawn());
            if (totalGrains != expected) {
                helper.fail("The completed ritual drew " + finished.totalDrawn() + " total, which "
                        + "AstraPrecipitation predicts should yield " + expected + " grains, but "
                        + totalGrains + " were actually dropped");
                return;
            }
            if (expected <= 0) {
                helper.fail("Test setup produced zero yield - pick a richer site or more arms so "
                        + "this scenario actually exercises a real completion payoff");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * <em>"A single reading tells me nothing about a gradient. Two real ones, far enough apart,
     * should."</em>
     *
     * <p>design/astra-atlas-scope.md's own headline proof, its first half, entered through the
     * real door: {@link play.xponer.astronima.item.AstraFieldMeterItem#use}, the exact method a
     * right-click calls. A reading at ordinary working depth — neither end of the gradient — must
     * identify nothing at all, the discriminating case that would catch a missing or inverted
     * threshold; only a reading at either real end moves the needle, and each only moves its own
     * side.
     */
    private static void astraFieldMeterIdentifiesTheGradientThroughRealReadings(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(play.xponer.astronima.registry.ModItems.ASTRA_FIELD_METER.get()));

        // Ordinary working depth: r=72 at y=16 -> shellCoordinate ~0.30, strictly between
        // AstraEvidence.DEEP_THRESHOLD (0.15) and SHALLOW_THRESHOLD (0.5) - neither end.
        player.setPos(72.5, 16.0, 0.5);
        play.xponer.astronima.registry.ModItems.ASTRA_FIELD_METER.get()
                .use(level, player, InteractionHand.MAIN_HAND);
        play.xponer.astronima.sim.magic.ResearchState afterMiddle =
                player.getData(play.xponer.astronima.registry.ModAttachments.RESEARCH.get());
        if (afterMiddle.identified(play.xponer.astronima.sim.magic.AstraEvidence.SHALLOW_READING)
                || afterMiddle.identified(play.xponer.astronima.sim.magic.AstraEvidence.DEEP_READING)) {
            helper.fail("A reading at ordinary working depth identified something - the gradient "
                    + "claim's evidence must come from a real comparison, not any reading at all");
            return;
        }

        // Close to the core: r=10 at y=16 -> shellCoordinate ~0.042, well under DEEP_THRESHOLD.
        player.setPos(10.5, 16.0, 0.5);
        play.xponer.astronima.registry.ModItems.ASTRA_FIELD_METER.get()
                .use(level, player, InteractionHand.MAIN_HAND);
        play.xponer.astronima.sim.magic.ResearchState afterDeep =
                player.getData(play.xponer.astronima.registry.ModAttachments.RESEARCH.get());
        if (!afterDeep.identified(play.xponer.astronima.sim.magic.AstraEvidence.DEEP_READING)) {
            helper.fail("A reading close to the core did not identify the gradient's deep end");
            return;
        }
        if (afterDeep.identified(play.xponer.astronima.sim.magic.AstraEvidence.SHALLOW_READING)) {
            helper.fail("A single deep reading also identified the shallow end - one reading "
                    + "must only ever prove its own end of the comparison");
            return;
        }

        // Out at working depth or past it: r=150 at y=16 -> shellCoordinate 0.625, at/past
        // SHALLOW_THRESHOLD.
        player.setPos(150.5, 16.0, 0.5);
        play.xponer.astronima.registry.ModItems.ASTRA_FIELD_METER.get()
                .use(level, player, InteractionHand.MAIN_HAND);
        play.xponer.astronima.sim.magic.ResearchState afterBoth =
                player.getData(play.xponer.astronima.registry.ModAttachments.RESEARCH.get());
        if (!afterBoth.identified(play.xponer.astronima.sim.magic.AstraEvidence.SHALLOW_READING)) {
            helper.fail("A reading out at working depth or past it did not identify the "
                    + "gradient's shallow end");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I compared the field at two real sites, own both real instruments, and can afford to
     * spend two grains proving it. The Atlas should let me file the claim, for real matter, not a
     * label."</em>
     *
     * <p>design/astra-atlas-scope.md's own headline proof, end to end: astra's subject resolves
     * through the exact same {@code ClaimStageCompletePayload} handler a sky claim already uses —
     * no second resolve path (§4's own hard requirement) — starting from evidence a real
     * {@code AstraFieldMeterItem} use produced, not an injected flag.
     */
    private static void astraGradientClaimResolvesEndToEnd(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        play.xponer.astronima.sim.magic.Research.Claim claim =
                play.xponer.astronima.sim.magic.Claims.ASTRA_GRADIENT;
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(play.xponer.astronima.registry.ModItems.ASTRA_FIELD_METER.get()));

        // Stage 1's own real evidence: two genuine readings, not injected state.
        player.setPos(10.5, 16.0, 0.5);
        play.xponer.astronima.registry.ModItems.ASTRA_FIELD_METER.get()
                .use(level, player, InteractionHand.MAIN_HAND);
        player.setPos(150.5, 16.0, 0.5);
        play.xponer.astronima.registry.ModItems.ASTRA_FIELD_METER.get()
                .use(level, player, InteractionHand.MAIN_HAND);

        helper.runAfterDelay(2, () -> {
            play.xponer.astronima.network.ClaimStageCompletePayload.handle(player, claim.id(), 0);
            play.xponer.astronima.sim.magic.ResearchState afterStage0 =
                    player.getData(play.xponer.astronima.registry.ModAttachments.RESEARCH.get());
            if (afterStage0.stageOf(claim.id()) != 1) {
                helper.fail("Stage 1 did not complete from two real field-meter readings alone -"
                        + " got stage " + afterStage0.stageOf(claim.id()));
                return;
            }

            // Stage 2: own both real instruments - the meter is already in the main hand slot,
            // which is itself one of the inventory slots ClaimStageCompletePayload scans.
            player.getInventory().add(
                    new ItemStack(play.xponer.astronima.registry.ModItems.ASTRA_COLLECTOR.get()));
            play.xponer.astronima.network.ClaimStageCompletePayload.handle(player, claim.id(), 1);
            play.xponer.astronima.sim.magic.ResearchState afterStage1 =
                    player.getData(play.xponer.astronima.registry.ModAttachments.RESEARCH.get());
            if (afterStage1.stageOf(claim.id()) != 2) {
                helper.fail("Stage 2 did not complete while owning both real instruments - got"
                        + " stage " + afterStage1.stageOf(claim.id()));
                return;
            }

            // Stage 3: hand in two real asterium grains.
            player.getInventory().add(new ItemStack(
                    play.xponer.astronima.registry.ModItems.ASTERIUM_GRAINS.get(), 2));
            play.xponer.astronima.network.ClaimStageCompletePayload.handle(player, claim.id(), 2);
            play.xponer.astronima.sim.magic.ResearchState held =
                    player.getData(play.xponer.astronima.registry.ModAttachments.RESEARCH.get());
            if (!held.holds(claim.id())) {
                helper.fail("The final stage completed without the claim holding - held and"
                        + " stageProgress disagreed");
                return;
            }
            if (!player.getData(play.xponer.astronima.registry.ModAttachments.UNLOCKS.get())
                    .has("research:" + claim.id())) {
                helper.fail("Holding astra_gradient granted nothing - the claim's own real"
                        + " reward never landed");
                return;
            }
            int grainsLeft = 0;
            var inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (inventory.getItem(slot).is(
                        play.xponer.astronima.registry.ModItems.ASTERIUM_GRAINS.get())) {
                    grainsLeft += inventory.getItem(slot).getCount();
                }
            }
            if (grainsLeft != 0) {
                helper.fail("The two grains survived a hand-in that completed - nothing was"
                        + " actually consumed");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * <em>"I sounded a column with a mix of real ore in it. It should name exactly what is really
     * down there, and nothing that is not."</em>
     *
     * <p>astra-systems.md §12's own headline claim for this exact mechanic, entered through the
     * real door: {@code astra.CrustColumn.scanBelow} reads the real, already-placed blocks
     * beneath a real position - the same function {@code AstraSounderItem#use} calls internally
     * (rule 46, one scan, not two) - and the result is checked against {@code OreBody}'s own
     * arithmetic for exactly the blocks placed, never a value this leaf's own code merely asserts
     * about itself. The sounder item is then right-clicked too, proving the real door does not
     * error, not merely that the model behind it is correct.
     */
    private static void astraSounderReportsKnownMineralsAndNothingElse(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(1, 10, 1);
        helper.setBlock(origin.below(1), play.xponer.astronima.registry.ModBlocks.ASTEROID_ROCK.get());
        helper.setBlock(origin.below(2), play.xponer.astronima.registry.ModBlocks.ASTEROID_ROCK.get());
        helper.setBlock(origin.below(3), play.xponer.astronima.registry.ModBlocks.METAL_RICH_ORE.get());
        helper.setBlock(origin.below(4), play.xponer.astronima.registry.ModBlocks.ILMENITE_ORE.get());
        helper.setBlock(origin.below(5), Blocks.STONE);

        BlockPos absoluteOrigin = helper.absolutePos(origin);
        play.xponer.astronima.sim.ore.OreBody scanned =
                play.xponer.astronima.astra.CrustColumn.scanBelow(level, absoluteOrigin);

        play.xponer.astronima.sim.ore.OreBody expected =
                play.xponer.astronima.sim.ore.OreBody.chondrite(4000.0)
                        .plus(play.xponer.astronima.sim.ore.OreBody.chondrite(4000.0))
                        .plus(play.xponer.astronima.sim.ore.OreBody.metalRich(4000.0))
                        .plus(play.xponer.astronima.sim.ore.OreBody.of(4000.0, java.util.Map.of(
                                play.xponer.astronima.sim.ore.Mineral.ILMENITE, 1.0)));

        for (play.xponer.astronima.sim.ore.Mineral mineral
                : play.xponer.astronima.sim.ore.Mineral.values()) {
            double got = scanned.fractionOf(mineral);
            double want = expected.fractionOf(mineral);
            if (Math.abs(got - want) > 1e-9) {
                helper.fail("The real column scan disagreed with OreBody's own arithmetic for "
                        + mineral + ": scanned " + got + ", expected " + want
                        + " - either an ore block was missed, or the plain stone block "
                        + "contributed something it should not have");
                return;
            }
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(absoluteOrigin.getX() + 0.5, absoluteOrigin.getY(), absoluteOrigin.getZ() + 0.5);
        InteractionResult result = play.xponer.astronima.registry.ModItems.ASTRA_SOUNDER.get()
                .use(level, player, InteractionHand.MAIN_HAND);
        if (result != InteractionResult.SUCCESS) {
            helper.fail("The sounder refused to activate: " + result);
            return;
        }
        helper.succeed();
    }

    // ------------------------------------------------- airlock chamber resolution (L2)

    // A chamber the controller sits on: a sealed box with two doors, and a port opening
    // into it that runs out to a pump and a tank — the pieces the resolver must find.
    private static final BlockPos CHAMBER = new BlockPos(4, 4, 4);
    private static final BlockPos CONTROLLER = new BlockPos(4, 4, 2); // north wall
    private static final BlockPos DOOR_A = new BlockPos(2, 3, 4);
    private static final BlockPos DOOR_B = new BlockPos(6, 3, 4);
    private static final BlockPos PORT = new BlockPos(4, 4, 6);
    private static final BlockPos PIPE = new BlockPos(4, 4, 7);
    private static final BlockPos PUMP = new BlockPos(4, 4, 8);
    private static final BlockPos TANK = new BlockPos(4, 4, 9);

    /**
     * How long one trip out may take, in game ticks.
     *
     * <p>Twenty-five seconds for a 3×3×3 chamber. Not a number chosen to make the test
     * pass — it is the bar the machine has to meet: an airlock is a thing you use every
     * time you leave, and past about half a minute of standing still watching a bar,
     * players stop building the airlock and just break a hole in the wall.
     */
    private static final int CYCLE_OUT_TICK_BUDGET = 500;

    /**
     * <em>"I built the chamber, the two doors, and ran a port out to a pump and tank.
     * Does the controller see a complete airlock?"</em>
     *
     * <p>The L2 resolution: it must find the chamber, both doors, and follow the plumbing
     * the player built to the exact pump and tank — not by proximity, but down the run.
     */
    private static void airlockResolvesAComplete(GameTestHelper helper) {
        buildChamber(helper, 2, true, true);
        AirlockResolver.Result r = resolveHere(helper);
        if (!r.ok()) {
            helper.fail("A complete airlock did not resolve: " + r.fault());
            return;
        }
        if (!r.airlock().pump().equals(helper.absolutePos(PUMP))) {
            helper.fail("Resolved the wrong pump: " + r.airlock().pump());
            return;
        }
        if (!r.airlock().tank().equals(helper.absolutePos(TANK))) {
            helper.fail("Resolved the wrong tank: " + r.airlock().tank());
            return;
        }
        if (r.airlock().doorFeet().size() != 2) {
            helper.fail("Expected two doors, found " + r.airlock().doorFeet().size());
            return;
        }
        // The order is part of the answer, because SCAN proposes inner and outer from it,
        // and straight off a hash set it varied between game sessions — so the same chamber
        // could be proposed the other way round tomorrow. Asserted as the property rather
        // than by scanning twice: re-scanning inside one run reproduces nothing, because a
        // hash set iterates the same way every time within a single JVM. That version of
        // this check passed against the unsorted code, which is exactly the shape of test
        // rule 12 exists to catch.
        List<BlockPos> feet = r.airlock().doorFeet();
        if (feet.get(0).asLong() >= feet.get(1).asLong()) {
            helper.fail("The doors came back in " + feet + ", which is not a stable order -"
                    + " SCAN would swap inner and outer between sessions");
            return;
        }
        helper.succeed();
    }

    /** A controller facing solid rock has no chamber, and says so. */
    private static void airlockReportsNoChamber(GameTestHelper helper) {
        // The block the controller faces is solid, so there is no volume to read.
        helper.setBlock(CONTROLLER.relative(Direction.SOUTH),
                ModBlocks.HULL_PLATE.get().defaultBlockState());
        expectFault(helper, AirlockResolver.Fault.NO_CHAMBER);
    }

    private static void airlockReportsNotSealed(GameTestHelper helper) {
        buildChamber(helper, 2, true, true);
        // A chamber the game cannot hold pressure in reads unsealed. Forcing the volume
        // cap below the chamber's size makes it unsealable before its first scan — the same
        // lever unsealable_holds_gas uses — which is a chamber that cannot hold a vacuum.
        int previousCap = Config.MAX_ROOM_VOLUME.get();
        Config.MAX_ROOM_VOLUME.set(8);
        try {
            expectFault(helper, AirlockResolver.Fault.NOT_SEALED);
        } finally {
            Config.MAX_ROOM_VOLUME.set(previousCap);
        }
    }

    private static void airlockReportsNeedsTwoDoors(GameTestHelper helper) {
        buildChamber(helper, 1, true, true);
        expectFault(helper, AirlockResolver.Fault.NEEDS_TWO_DOORS);
    }

    private static void airlockReportsNoPump(GameTestHelper helper) {
        buildChamber(helper, 2, false, false);
        expectFault(helper, AirlockResolver.Fault.NO_PUMP);
    }

    private static void airlockReportsNoTank(GameTestHelper helper) {
        buildChamber(helper, 2, true, false);
        expectFault(helper, AirlockResolver.Fault.NO_TANK);
    }

    /**
     * <em>"What if I put several pumps on it?"</em> — asked directly from play.
     *
     * <p>Two pumps drawing from one chamber has no defined answer, so the controller must
     * refuse and <em>say which problem it is</em>. It used to report "no pump" while two
     * were plumbed in, which sends the player to look in exactly the wrong place.
     */
    private static void airlockReportsTooManyPumps(GameTestHelper helper) {
        buildChamber(helper, 2, true, true);
        // A second pump drawing off the very same run.
        helper.setBlock(PIPE.relative(Direction.UP), ModBlocks.GAS_PUMP.get().defaultBlockState()
                .setValue(GasPumpBlock.FACING, Direction.UP));
        expectFault(helper, AirlockResolver.Fault.TOO_MANY_PUMPS);
    }

    /**
     * <em>"How am I supposed to test this if it never tells me what is wrong?"</em>
     *
     * <p>The panel exists to answer that, so what it would draw has to be right for a real
     * broken build — not just for a hand-made status record. Builds an airlock with the pump
     * left out and asserts the panel reports the chain unbound and points at the <em>pump</em>
     * stage, then puts the pump in and asserts it goes green. Without the second half this
     * would pass just as well for a panel that always cries fault.
     */
    private static void airlockPanelPointsAtTheBrokenPiece(GameTestHelper helper) {
        buildChamber(helper, 2, false, false); // no pump, no tank
        helper.setBlock(CONTROLLER, ModBlocks.AIRLOCK_CONTROLLER.get().defaultBlockState()
                .setValue(play.xponer.astronima.block.AirlockControllerBlock.FACING,
                        Direction.SOUTH));
        Atmosphere.get(helper.getLevel()).invalidate(helper.absolutePos(CHAMBER));
        var controller = helper.getBlockEntity(CONTROLLER,
                play.xponer.astronima.block.entity.AirlockControllerBlockEntity.class);
        if (controller == null) {
            helper.fail("The controller has no block entity");
            return;
        }
        BlockPos controllerAbs = helper.absolutePos(CONTROLLER);
        // The player commissions what they have built: two doors, and nothing to put on
        // the pump and tank terminals yet. That is the real half-built state, and the
        // panel must point at the next thing to do rather than the first thing in a list.
        controller.bind(Role.INNER_DOOR, helper.absolutePos(DOOR_A));
        controller.bind(Role.OUTER_DOOR, helper.absolutePos(DOOR_B));
        controller.tick(helper.getLevel(), controllerAbs,
                helper.getLevel().getBlockState(controllerAbs));

        var broken = controller.status();
        if (broken.commissioned()) {
            helper.fail("A chamber with no pump reported itself as a working airlock");
            return;
        }
        if (broken.faultyStage() != play.xponer.astronima.airlock.AirlockStatus.Stage.PUMP) {
            helper.fail("The panel would light the " + broken.faultyStage()
                    + " stage for a missing pump, sending the player to the wrong block");
            return;
        }
        if (broken.terminal(Role.PUMP).bound()) {
            helper.fail("The panel shows a pump on a build that has none");
            return;
        }

        // Now finish the build, commission it the way the player does, and the same panel
        // must go green — or the check above proves nothing except that it always complains.
        helper.setBlock(PUMP, ModBlocks.GAS_PUMP.get().defaultBlockState()
                .setValue(GasPumpBlock.FACING, Direction.SOUTH));
        helper.setBlock(TANK, ModBlocks.GAS_TANK.get().defaultBlockState());
        Atmosphere.get(helper.getLevel()).invalidate(helper.absolutePos(CHAMBER));
        commission(helper, controller);
        controller.tick(helper.getLevel(), controllerAbs,
                helper.getLevel().getBlockState(controllerAbs));

        var fixed = controller.status();
        if (!fixed.commissioned()) {
            Role faulted = fixed.firstFault();
            helper.fail("A complete, commissioned airlock still reports "
                    + fixed.chamberFault() + " / "
                    + (faulted == null ? "-" : faulted + " " + fixed.problem(faulted)));
            return;
        }
        for (Role role : Role.values()) {
            if (!fixed.terminal(role).bound()) {
                helper.fail("The panel does not show the " + role + " it just bound");
                return;
            }
        }
        if (fixed.chamberVolume() <= 0) {
            helper.fail("The panel shows no chamber volume, so a player cannot tell it"
                    + " found the right room");
            return;
        }
        // The offsets are what tell two identical bulkhead doors apart on the diagram; a
        // panel that showed both as "here" would be no better than the old one.
        String innerAt = play.xponer.astronima.airlock.AirlockStatus.offsetLabel(
                fixed.terminal(Role.INNER_DOOR).offset());
        String outerAt = play.xponer.astronima.airlock.AirlockStatus.offsetLabel(
                fixed.terminal(Role.OUTER_DOOR).offset());
        if (innerAt.isEmpty() || innerAt.equals(outerAt)) {
            helper.fail("The two door terminals read the same place (" + innerAt + " / "
                    + outerAt + ") - a player cannot tell which door a fault is about");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I walked over to the dehumidifier. Did I get water, or did I just get told a
     * percentage again?"</em>
     *
     * <p>The block used to answer a click by printing <em>"Condensate reservoir: 42 % of a
     * bottle"</em> into chat — a gauge reading pretending to be an event, which is what rule
     * 9 refuses: it scrolls away, it cannot be glanced at, and walking across a habitat to
     * be told a number is a poor reward. The charge is a gauge now, and the click is a
     * <em>withdrawal</em>.
     *
     * <p>So this asserts the withdrawal from both ends, because either half alone proves
     * nothing: clicking an empty unit must hand over nothing and leave the reservoir alone,
     * and clicking a full one must put real bottles in the hand and visibly empty it.
     */
    private static void takingWaterFromTheCondenser(GameTestHelper helper) {
        BlockPos inside = new BlockPos(4, 4, 4);
        BlockPos unitPos = new BlockPos(4, 4, 6); // in the wall, touching the room
        ModTestFunctions.buildBoxAround(helper, inside);
        helper.setBlock(unitPos, ModBlocks.DEHUMIDIFIER.get().defaultBlockState());
        Atmosphere.get(helper.getLevel()).invalidate(helper.absolutePos(inside));

        var unit = helper.getBlockEntity(unitPos,
                play.xponer.astronima.block.entity.DehumidifierBlockEntity.class);
        RoomState room = Atmosphere.get(helper.getLevel()).roomAt(helper.absolutePos(inside));
        if (unit == null || room == null) {
            helper.fail("Setup failed: no condenser or no room");
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        // Empty: the click must not conjure a bottle, and must not eat the reservoir.
        helper.useBlock(unitPos, player);
        if (player.getInventory().contains(stack -> stack.is(net.minecraft.world.item.Items.POTION))) {
            helper.fail("An empty condenser handed over a bottle of water");
            return;
        }

        // A room well past saturation: the coil has plenty to take, which is the state a
        // habitat full of people actually reaches.
        room.addGasAt(Gas.WATER_VAPOR, 400, 293.15);

        helper.runAfterDelay(Atmosphere.TICK_INTERVAL * 2L + 2, () -> {
            if (unit.bottlesReady() <= 0) {
                helper.fail("A saturated room left the condenser with nothing after "
                        + (Atmosphere.TICK_INTERVAL * 2 + 2) + " ticks - progress "
                        + unit.bottleProgress());
                return;
            }
            int waiting = unit.bottlesReady();
            helper.useBlock(unitPos, player);

            int held = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.is(net.minecraft.world.item.Items.POTION)) {
                    held += stack.getCount();
                }
            }
            if (held < waiting) {
                helper.fail("The condenser had " + waiting + " bottles ready and handed over "
                        + held);
                return;
            }
            // And the gauge must visibly drop, or the player cannot tell it worked.
            if (unit.bottlesReady() != 0) {
                helper.fail("The reservoir still reads " + unit.bottlesReady()
                        + " whole bottles after they were taken");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * <em>"I built the condenser into the wall, put a hopper under it, and walked away for
     * the rest of the session - do I come back to bottles waiting in a chest, or do I still
     * have to be the one who clicks the block every single time?"</em>
     *
     * <p>Every other machine in the regenerative loop is a real {@code Container} a hopper
     * can feed or drain; this was the one water-related block that was not, and
     * {@code design/water-plumbing.md} is the design that closed it. No player exists in
     * this test at all - proving the automation genuinely does not need one, rather than
     * merely not getting in its way.
     */
    private static void aHopperDrainsTheCondenserWithoutAPlayer(GameTestHelper helper) {
        BlockPos inside = new BlockPos(4, 4, 4);
        BlockPos unitPos = new BlockPos(4, 4, 6); // in the wall, touching the room
        BlockPos hopperPos = unitPos.below();
        ModTestFunctions.buildBoxAround(helper, inside);
        helper.setBlock(unitPos, ModBlocks.DEHUMIDIFIER.get().defaultBlockState());
        helper.setBlock(hopperPos, net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState());
        Atmosphere.get(helper.getLevel()).invalidate(helper.absolutePos(inside));

        var unit = helper.getBlockEntity(unitPos,
                play.xponer.astronima.block.entity.DehumidifierBlockEntity.class);
        var hopper = helper.getBlockEntity(hopperPos,
                net.minecraft.world.level.block.entity.HopperBlockEntity.class);
        RoomState room = Atmosphere.get(helper.getLevel()).roomAt(helper.absolutePos(inside));
        if (unit == null || hopper == null || room == null) {
            helper.fail("Setup failed: no condenser, no hopper, or no room");
            return;
        }

        room.addGasAt(Gas.WATER_VAPOR, 400, 293.15);

        // Time for the coil to materialize a bottle, plus real room for the hopper's own
        // 8-tick pull cadence to have fired at least once - nobody clicks anything here.
        helper.runAfterDelay(Atmosphere.TICK_INTERVAL * 2L + 20, () -> {
            int inHopper = 0;
            for (int slot = 0; slot < hopper.getContainerSize(); slot++) {
                ItemStack stack = hopper.getItem(slot);
                if (stack.is(net.minecraft.world.item.Items.POTION)) {
                    inHopper += stack.getCount();
                }
            }
            if (inHopper <= 0) {
                helper.fail("A hopper under the condenser, with a saturated room and no player "
                        + "ever touching either block, still has no water bottles - the condenser "
                        + "reads " + unit.bottlesReady() + " bottles ready of its own");
                return;
            }
            helper.succeed();
        });
    }

    // ------------------------------------------------------------- commissioning (C)

    /**
     * <em>"Both my doors are in the same wall. Why does it say I have not got two?"</em>
     *
     * <p><strong>The row that matters most</strong> (design/commissioning.md §3): a chamber
     * with both bulkheads in one wall is a perfectly good airlock — an end-loading tambour,
     * and a real one — and the old resolver's opinions about door placement were exactly
     * the kind of guess rule 17 removed. Commissioned by hand, it must run a full cycle out
     * to vacuum. If this fails, the whole change has bought nothing.
     */
    private static void airlockAcceptsBothDoorsInOneWall(GameTestHelper helper) {
        // Both doors in the west wall, side by side, instead of facing each other.
        ModTestFunctions.buildBoxAround(helper, CHAMBER);
        BlockPos doorOne = new BlockPos(2, 3, 3);
        BlockPos doorTwo = new BlockPos(2, 3, 5);
        placeDoor(helper, doorOne);
        placeDoor(helper, doorTwo);
        helper.setBlock(PORT, ModBlocks.GAS_PORT.get().defaultBlockState()
                .setValue(GasPortBlock.FACING, Direction.NORTH));
        helper.setBlock(PIPE, ModBlocks.GAS_PIPE.get().defaultBlockState());
        helper.setBlock(PUMP, ModBlocks.GAS_PUMP.get().defaultBlockState()
                .setValue(GasPumpBlock.FACING, Direction.SOUTH));
        helper.setBlock(TANK, ModBlocks.GAS_TANK.get().defaultBlockState());
        helper.setBlock(CONTROLLER, ModBlocks.AIRLOCK_CONTROLLER.get().defaultBlockState()
                .setValue(play.xponer.astronima.block.AirlockControllerBlock.FACING,
                        Direction.SOUTH));
        Atmosphere.get(helper.getLevel()).invalidate(helper.absolutePos(CHAMBER));

        var controller = helper.getBlockEntity(CONTROLLER,
                play.xponer.astronima.block.entity.AirlockControllerBlockEntity.class);
        RoomState chamber = Atmosphere.get(helper.getLevel())
                .roomAt(helper.absolutePos(CHAMBER));
        if (controller == null || chamber == null) {
            helper.fail("Setup failed: controller or chamber missing");
            return;
        }
        chamber.addGasAt(Gas.OXYGEN, 1200, 293.15);

        // The player says which is which. Nobody has to work out what we would have
        // guessed, because we no longer guess.
        controller.bind(Role.INNER_DOOR, helper.absolutePos(doorOne));
        controller.bind(Role.OUTER_DOOR, helper.absolutePos(doorTwo));
        controller.bind(Role.PUMP, helper.absolutePos(PUMP));
        controller.bind(Role.TANK, helper.absolutePos(TANK));

        BlockPos controllerAbs = helper.absolutePos(CONTROLLER);
        controller.tick(helper.getLevel(), controllerAbs,
                helper.getLevel().getBlockState(controllerAbs));
        var status = controller.status();
        if (!status.commissioned()) {
            Role faulted = status.firstFault();
            helper.fail("An airlock with both doors in one wall was refused: "
                    + status.chamberFault() + " / "
                    + (faulted == null ? "-" : faulted + " " + status.problem(faulted))
                    + " - this is the build the old resolver rejected, and it is legal");
            return;
        }

        // And it does not merely report clean: it cycles.
        controller.command();
        if (runUntil(helper, controller, AirlockCycle.Phase.VACUUM,
                AirlockCycle.MAX_PUMP_TICKS + 200) < 0) {
            helper.fail("An accepted end-loading airlock never reached vacuum; stuck in "
                    + controller.phase() + " cycle=" + controller.cycleFault());
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I clicked the terminal, walked to the door, and clicked it with the wrench."</em>
     *
     * <p>The commissioning loop through the door the player uses (rule 13): a real wrench
     * item, a real {@code useOn}, on real blocks. Also proves the two things that would
     * make the loop unusable in practice — a wrong block <em>refuses</em> rather than
     * binding silently, and clicking a door with an armed wrench binds it instead of
     * swinging it open.
     *
     * <p>Not staged here: the panel click that arms the wrench in the first place. It needs
     * a {@code ServerPlayer} with an open menu, which the harness cannot give (PLAN rule
     * 14) — so the arming is done by setting the component the packet sets, and the packet
     * itself is owed to {@code runClient}, recorded in PLAYTEST.md.
     */
    private static void airlockCommissionedWithTheWrench(GameTestHelper helper) {
        buildChamber(helper, 2, true, true);
        helper.setBlock(CONTROLLER, ModBlocks.AIRLOCK_CONTROLLER.get().defaultBlockState()
                .setValue(play.xponer.astronima.block.AirlockControllerBlock.FACING,
                        Direction.SOUTH));
        Atmosphere.get(helper.getLevel()).invalidate(helper.absolutePos(CHAMBER));
        var controller = helper.getBlockEntity(CONTROLLER,
                play.xponer.astronima.block.entity.AirlockControllerBlockEntity.class);
        if (controller == null) {
            helper.fail("The controller has no block entity");
            return;
        }
        BlockPos controllerAbs = helper.absolutePos(CONTROLLER);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        ItemStack wrench = new ItemStack(ModItems.WRENCH.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);

        // Arm OUTER DOOR, then click the tank by mistake. It must refuse: a click that
        // quietly does nothing is indistinguishable from a click that never registered.
        arm(wrench, controllerAbs, Role.OUTER_DOOR);
        helper.useBlock(TANK, player);
        if (controller.bindings().isBound(Role.OUTER_DOOR)) {
            helper.fail("A gas tank was accepted as the outer door");
            return;
        }
        if (wrench.get(play.xponer.astronima.registry.ModDataComponents.TERMINAL_ARM.get())
                == null) {
            helper.fail("A refused click disarmed the wrench, so the player would have to"
                    + " go back to the panel after every mis-click");
            return;
        }

        // Now the right block. The door must bind — and must not swing open, because the
        // wrench click has to mean 'bind' while it is armed.
        helper.useBlock(DOOR_B, player);
        if (controller.bindings().device(Role.OUTER_DOOR)
                != helper.absolutePos(DOOR_B).asLong()) {
            helper.fail("Clicking the door with an armed wrench did not bind it");
            return;
        }
        if (helper.getBlockState(DOOR_B).getValue(BlockStateProperties.OPEN)) {
            helper.fail("Binding a door opened it - the click must mean bind, not use");
            return;
        }
        if (wrench.get(play.xponer.astronima.registry.ModDataComponents.TERMINAL_ARM.get())
                != null) {
            helper.fail("The wrench stayed armed after a successful bind, so the next"
                    + " click would silently re-bind the same terminal");
            return;
        }

        // Changed my mind: sneak-click gets out of binding mode. Without a way out, a
        // player who armed a terminal by accident would carry a wrench that refuses to
        // rotate anything until they bind something they did not want bound.
        arm(wrench, controllerAbs, Role.PUMP);
        player.setShiftKeyDown(true);
        helper.useBlock(PUMP, player);
        player.setShiftKeyDown(false);
        if (wrench.get(play.xponer.astronima.registry.ModDataComponents.TERMINAL_ARM.get())
                != null) {
            helper.fail("Sneak-clicking did not get the wrench out of binding mode");
            return;
        }
        if (controller.bindings().isBound(Role.PUMP)) {
            helper.fail("Cancelling the binding bound the device anyway");
            return;
        }

        // Fill the rest the same way, and the panel must come up commissioned.
        arm(wrench, controllerAbs, Role.INNER_DOOR);
        helper.useBlock(DOOR_A, player);
        arm(wrench, controllerAbs, Role.PUMP);
        helper.useBlock(PUMP, player);
        arm(wrench, controllerAbs, Role.TANK);
        helper.useBlock(TANK, player);

        controller.tick(helper.getLevel(), controllerAbs,
                helper.getLevel().getBlockState(controllerAbs));
        if (!controller.status().commissioned()) {
            Role faulted = controller.status().firstFault();
            helper.fail("A panel commissioned by hand still reports "
                    + (faulted == null ? controller.chamberFault().toString()
                            : faulted + " " + controller.problem(faulted)));
            return;
        }
        helper.succeed();
    }

    /** Arms a wrench for a terminal — the state the panel's ARM packet leaves it in. */
    private static void arm(ItemStack wrench, BlockPos controller, Role role) {
        wrench.set(play.xponer.astronima.registry.ModDataComponents.TERMINAL_ARM.get(),
                new play.xponer.astronima.item.TerminalArm(controller, role));
    }

    /**
     * <em>"I pulled the doors open and it instantly said it had not got two doors."</em>
     *
     * <p>Reported straight from play, and it was true: the chamber's boundary is made of
     * <em>solid</em> blocks, so an open door stopped being on it and the resolver counted
     * one door, or none. The build had not changed at all — the player had just opened a
     * door, which is what doors are for.
     *
     * <p>Commissioned, a door the player named stays named whether it is open or shut. An
     * open door is then what it really is: a reason this cycle cannot <em>start</em>
     * ({@code DOORS_OPEN}), reported by the cycle, with the build still whole.
     */
    private static void airlockNamesTheDeviceYouBound(GameTestHelper helper) {
        buildChamber(helper, 2, true, true);
        helper.setBlock(CONTROLLER, ModBlocks.AIRLOCK_CONTROLLER.get().defaultBlockState()
                .setValue(play.xponer.astronima.block.AirlockControllerBlock.FACING,
                        Direction.SOUTH));
        Atmosphere.get(helper.getLevel()).invalidate(helper.absolutePos(CHAMBER));
        var controller = helper.getBlockEntity(CONTROLLER,
                play.xponer.astronima.block.entity.AirlockControllerBlockEntity.class);
        if (controller == null || !commission(helper, controller)) {
            helper.fail("Setup failed: could not commission a complete airlock");
            return;
        }
        BlockPos controllerAbs = helper.absolutePos(CONTROLLER);

        // Yank a door open, exactly as reported.
        BlockPos doorAbs = helper.absolutePos(DOOR_A);
        helper.getLevel().setBlock(doorAbs, helper.getLevel().getBlockState(doorAbs)
                .setValue(BlockStateProperties.OPEN, true), 3);
        helper.getLevel().setBlock(doorAbs.above(),
                helper.getLevel().getBlockState(doorAbs.above())
                        .setValue(BlockStateProperties.OPEN, true), 3);
        Atmosphere.get(helper.getLevel()).invalidate(doorAbs);
        controller.tick(helper.getLevel(), controllerAbs,
                helper.getLevel().getBlockState(controllerAbs));

        var opened = controller.status();
        for (Role role : new Role[] {Role.INNER_DOOR, Role.OUTER_DOOR}) {
            if (!opened.terminal(role).ok()) {
                helper.fail("Opening a door made the panel report " + role + " as "
                        + opened.problem(role) + " - the door is exactly where the player"
                        + " put it, and opening it is what doors are for");
                return;
            }
        }
        // The chamber itself is genuinely open now, and saying so is fine — it is true and
        // it names the right thing. "Needs two doors", which is what this used to say, was
        // neither.
        if (controller.chamberFault() == AirlockResolver.Fault.NEEDS_TWO_DOORS) {
            helper.fail("An open door still reports 'needs two doors' - both doors are"
                    + " standing right there");
            return;
        }

        // Shut it again and the panel must come straight back, or "still bound" above
        // proves nothing about being able to use the airlock afterwards.
        helper.getLevel().setBlock(doorAbs, helper.getLevel().getBlockState(doorAbs)
                .setValue(BlockStateProperties.OPEN, false), 3);
        helper.getLevel().setBlock(doorAbs.above(),
                helper.getLevel().getBlockState(doorAbs.above())
                        .setValue(BlockStateProperties.OPEN, false), 3);
        Atmosphere.get(helper.getLevel()).invalidate(doorAbs);
        controller.tick(helper.getLevel(), controllerAbs,
                helper.getLevel().getBlockState(controllerAbs));
        if (!controller.status().commissioned()) {
            Role faulted = controller.status().firstFault();
            helper.fail("Shutting the door again left the panel broken: "
                    + controller.chamberFault() + " / "
                    + (faulted == null ? "-" : faulted + " " + controller.problem(faulted)));
            return;
        }

        // And the wrong kind of block, bound on purpose, is named on the terminal the
        // player filled — the whole gain of commissioning over searching.
        controller.bind(Role.OUTER_DOOR, helper.absolutePos(TANK));
        controller.tick(helper.getLevel(), controllerAbs,
                helper.getLevel().getBlockState(controllerAbs));
        var wrong = controller.status();
        if (wrong.firstFault() != Role.OUTER_DOOR) {
            helper.fail("A tank bound as the outer door was reported as "
                    + wrong.firstFault() + ", not on the terminal the player filled");
            return;
        }
        if (wrong.problem(Role.OUTER_DOOR)
                != play.xponer.astronima.sim.airlock.DeviceBinding.Problem.WRONG_KIND) {
            helper.fail("A tank bound as the outer door reads as "
                    + wrong.problem(Role.OUTER_DOOR) + " rather than the wrong kind");
            return;
        }

        // The mistake nothing on the block can reveal: one door landed on both terminals.
        // It is a perfectly good door and it passes every other check, so if this is not
        // caught the airlock silently locks and unlocks the same door as both ends of
        // itself — an open path from habitat to vacuum wearing the interlock's own colours.
        // Whichever door SCAN put on the inner terminal — asking the panel rather than
        // assuming, because which of two equivalent doors it proposed is its business.
        controller.bind(Role.OUTER_DOOR,
                BlockPos.of(controller.bindings().device(Role.INNER_DOOR)));
        controller.tick(helper.getLevel(), controllerAbs,
                helper.getLevel().getBlockState(controllerAbs));
        for (Role role : new Role[] {Role.INNER_DOOR, Role.OUTER_DOOR}) {
            if (controller.problem(role)
                    != play.xponer.astronima.sim.airlock.DeviceBinding.Problem.SAME_DOOR) {
                helper.fail("One door bound to both terminals reads as "
                        + controller.problem(role) + " on " + role
                        + " - the interlock would hold one door as both ends of the airlock");
                return;
            }
        }

        // A real bulkhead door, in the wrong place: the fault the whole change is named
        // after. "OUTER DOOR - not on the chamber" points at one block the player set on
        // purpose, where "needs two doors" pointed at nothing.
        BlockPos stray = new BlockPos(1, 1, 1);
        placeDoor(helper, stray);
        controller.bind(Role.OUTER_DOOR, helper.absolutePos(stray));
        controller.tick(helper.getLevel(), controllerAbs,
                helper.getLevel().getBlockState(controllerAbs));
        if (controller.problem(Role.OUTER_DOOR)
                != play.xponer.astronima.sim.airlock.DeviceBinding.Problem.NOT_ON_CHAMBER) {
            helper.fail("A door nowhere near the chamber was accepted as the outer door: "
                    + controller.problem(Role.OUTER_DOOR));
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"My airlock worked yesterday and I have not touched it."</em>
     *
     * <p>C6, and the reason the old resolver is kept rather than deleted. Every controller
     * written to disk before commissioning existed has no bindings and no
     * {@code commissioned} flag — so it loads as un-commissioned, runs the resolver once on
     * its first tick, and writes what it finds in as bindings. The player sees a
     * commissioned panel and never knows anything changed.
     *
     * <p>Staged by round-tripping a controller through the save format with that key
     * <em>stripped</em>, which is precisely what an old save looks like.
     */
    private static void anAirlockBuiltBeforeCommissioningStillWorks(GameTestHelper helper) {
        buildChamber(helper, 2, true, true);
        helper.setBlock(CONTROLLER, ModBlocks.AIRLOCK_CONTROLLER.get().defaultBlockState()
                .setValue(play.xponer.astronima.block.AirlockControllerBlock.FACING,
                        Direction.SOUTH));
        Atmosphere.get(helper.getLevel()).invalidate(helper.absolutePos(CHAMBER));
        BlockPos controllerAbs = helper.absolutePos(CONTROLLER);

        var registries = helper.getLevel().registryAccess();
        try (var scope = new net.minecraft.util.ProblemReporter.ScopedCollector(
                com.mojang.logging.LogUtils.getLogger())) {
            var output = net.minecraft.world.level.storage.TagValueOutput
                    .createWithContext(scope, registries);
            var fresh = helper.getBlockEntity(CONTROLLER,
                    play.xponer.astronima.block.entity.AirlockControllerBlockEntity.class);
            if (fresh == null) {
                helper.fail("The controller has no block entity");
                return;
            }
            fresh.saveWithoutMetadata(output);
            var tag = output.buildResult();
            // What an old save is: no commissioning flag, no bound terminals.
            tag.remove("commissioned");
            var input = net.minecraft.world.level.storage.TagValueInput
                    .create(scope, registries, tag);

            var old = new play.xponer.astronima.block.entity.AirlockControllerBlockEntity(
                    controllerAbs, helper.getBlockState(CONTROLLER));
            old.setLevel(helper.getLevel());
            old.loadWithComponents(input);
            if (!old.bindings().empty()) {
                helper.fail("The staged 'old' controller already had bindings, so this"
                        + " proves nothing about migration");
                return;
            }

            old.tick(helper.getLevel(), controllerAbs, helper.getBlockState(CONTROLLER));
            if (!old.status().commissioned()) {
                Role faulted = old.status().firstFault();
                helper.fail("An airlock built before commissioning did not migrate: "
                        + old.chamberFault() + " / "
                        + (faulted == null ? "-" : faulted + " " + old.problem(faulted))
                        + " - it worked yesterday and now it does not");
                return;
            }

            // The other half: a controller the player placed today must NOT commission
            // itself, or the migration has quietly reinstated the silent resolution rule
            // 17 removed. Without this check, "always migrate" would pass the test above.
            var placedToday = new play.xponer.astronima.block.entity
                    .AirlockControllerBlockEntity(controllerAbs, helper.getBlockState(CONTROLLER));
            placedToday.setLevel(helper.getLevel());
            placedToday.tick(helper.getLevel(), controllerAbs, helper.getBlockState(CONTROLLER));
            if (placedToday.status().commissioned()) {
                helper.fail("A freshly placed controller commissioned itself - the player"
                        + " never said which door was the outer one, and we guessed again");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * <em>"Somebody mined the outer door while I was pumping down."</em>
     *
     * <p>The second row the plan calls out. It must fail <strong>safe</strong> — never
     * leave a person sealed in a chamber nobody can open — and it must say
     * <em>which door</em>, which it can only do because the player named it.
     */
    private static void breakingABoundDoorMidCycleFailsSafe(GameTestHelper helper) {
        buildChamber(helper, 2, true, true);
        helper.setBlock(CONTROLLER, ModBlocks.AIRLOCK_CONTROLLER.get().defaultBlockState()
                .setValue(play.xponer.astronima.block.AirlockControllerBlock.FACING,
                        Direction.SOUTH));
        Atmosphere.get(helper.getLevel()).invalidate(helper.absolutePos(CHAMBER));
        RoomState chamber = Atmosphere.get(helper.getLevel())
                .roomAt(helper.absolutePos(CHAMBER));
        var controller = helper.getBlockEntity(CONTROLLER,
                play.xponer.astronima.block.entity.AirlockControllerBlockEntity.class);
        if (chamber == null || controller == null || !commission(helper, controller)) {
            helper.fail("Setup failed");
            return;
        }
        chamber.addGasAt(Gas.OXYGEN, 1200, 293.15);
        BlockPos controllerAbs = helper.absolutePos(CONTROLLER);

        // Into a pump-down, then the door goes.
        controller.command();
        for (int i = 0; i < 40; i++) {
            controller.tick(helper.getLevel(), controllerAbs, helper.getBlockState(CONTROLLER));
        }
        if (controller.phase() != AirlockCycle.Phase.PUMPING) {
            helper.fail("Expected to be pumping down before breaking the door; phase is "
                    + controller.phase());
            return;
        }
        Role broken = controller.bindings().device(Role.OUTER_DOOR)
                == helper.absolutePos(DOOR_B).asLong() ? Role.OUTER_DOOR : Role.INNER_DOOR;
        helper.setBlock(DOOR_B, Blocks.AIR.defaultBlockState());
        helper.setBlock(DOOR_B.above(), Blocks.AIR.defaultBlockState());
        Atmosphere.get(helper.getLevel()).invalidate(helper.absolutePos(DOOR_B));
        controller.tick(helper.getLevel(), controllerAbs, helper.getBlockState(CONTROLLER));

        var status = controller.status();
        if (status.problem(broken)
                != play.xponer.astronima.sim.airlock.DeviceBinding.Problem.MISSING) {
            helper.fail("A door mined mid-cycle reads as " + status.problem(broken)
                    + " on the " + broken + " terminal, not as missing");
            return;
        }
        // Fail safe: back to rest, nothing held shut. A player standing in the chamber
        // must be able to walk out of the door that is left.
        if (controller.phase() != AirlockCycle.Phase.SEALED) {
            helper.fail("A broken door left the cycle running in " + controller.phase()
                    + " - the chamber can be pumped down with nobody able to leave it");
            return;
        }
        if (AirlockCycle.innerLocked(controller.phase())) {
            helper.fail("The remaining door is still held shut after the other was broken");
            return;
        }
        // And the pump is let go, or the player's own pump stays gated off for good with
        // nothing left to explain why.
        GasPumpBlockEntity pump = helper.getBlockEntity(PUMP, GasPumpBlockEntity.class);
        if (pump != null && controllerAbs.equals(pump.heldBy())) {
            helper.fail("The controller kept its claim on the pump after failing safe");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"The pipe is right there and nothing is happening. What is wrong with it?"</em>
     *
     * <p>Asked three different ways from play, and every time the plumbing was doing exactly
     * what it was built to do with no way to see it. Every fitting has a gauge; the
     * <strong>line</strong> had none, and a line that goes nowhere looks precisely like a
     * line that works — the same pipe.
     *
     * <p>So the wrench surveys it, and this drives the real item through the real click on
     * real blocks (rule 13). Three builds, because a survey that only ever says one thing is
     * no better than the silence it replaced: a run to nowhere, the same run once it has two
     * ends, and the same run again with the valve shut.
     */
    private static void theWrenchSurveysTheLine(GameTestHelper helper) {
        BlockPos leftInside = new BlockPos(2, 2, 2);
        ModTestFunctions.buildBoxAround(helper, leftInside);
        BlockPos port = leftInside.offset(0, 0, 2);      // in the left box's south wall
        BlockPos pipe = port.south();                    // the one block between the boxes
        helper.setBlock(port, ModBlocks.GAS_PORT.get().defaultBlockState()
                .setValue(GasPortBlock.FACING, Direction.NORTH));
        helper.setBlock(pipe, ModBlocks.GAS_PIPE.get().defaultBlockState());
        Atmosphere.get(helper.getLevel()).invalidate(helper.absolutePos(leftInside));

        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH.get()));

        // One room and a bare pipe end: the commonest first build, and it does nothing.
        var stranded = PipeSurvey.of(helper.getLevel(), helper.absolutePos(pipe));
        if (stranded == null
                || RunDiagnosis.verdict(stranded) != RunDiagnosis.Verdict.GOES_NOWHERE) {
            helper.fail("A pipe running out of one room and stopping was not reported as"
                    + " going nowhere: " + (stranded == null ? "not plumbing at all"
                            : RunDiagnosis.verdict(stranded).toString()));
            return;
        }

        // The wrench must actually be what asks, or the survey exists and no player will
        // ever reach it. A pipe has nothing to rotate, so this click used to pass straight
        // through; asserted on the result the game itself consumes, since the survey's own
        // output is an action-bar line and the harness has no player that can receive one
        // (PLAN rule 14).
        ItemStack wrench = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (useWrenchOn(helper, player, wrench, pipe) != InteractionResult.SUCCESS) {
            helper.fail("A wrench click on a pipe passed straight through - the survey is"
                    + " unreachable, which is the same as it not existing");
            return;
        }
        if (!(helper.getBlockState(pipe).getBlock() instanceof GasPipeBlock)) {
            helper.fail("Surveying the line changed the block it was asked about");
            return;
        }
        // And the other half: on something that is not plumbing the wrench must still keep
        // out of the way, or it has started swallowing every click in the world.
        helper.setBlock(new BlockPos(1, 1, 1), ModBlocks.HULL_PLATE.get().defaultBlockState());
        if (useWrenchOn(helper, player, wrench, new BlockPos(1, 1, 1))
                != InteractionResult.PASS) {
            helper.fail("The wrench consumed a click on a plain wall - it must pass"
                    + " through anything it has nothing to say about");
            return;
        }

        // Give it a second end. Same wrench, same pipe, different answer — without this
        // half, a survey that always cried fault would pass the check above.
        BlockPos rightInside = leftInside.offset(0, 0, 6);
        ModTestFunctions.buildBoxAround(helper, rightInside);
        BlockPos farPort = rightInside.offset(0, 0, -2);
        helper.setBlock(farPort, ModBlocks.GAS_PORT.get().defaultBlockState()
                .setValue(GasPortBlock.FACING, Direction.SOUTH));
        Atmosphere.get(helper.getLevel()).invalidate(helper.absolutePos(rightInside));

        var joined = PipeSurvey.of(helper.getLevel(), helper.absolutePos(pipe));
        if (joined == null || RunDiagnosis.verdict(joined).isFault()) {
            helper.fail("A line joining two sealed rooms still reports a fault: "
                    + (joined == null ? "no run" : RunDiagnosis.verdict(joined).toString())
                    + " - the survey cries wolf on a correct build");
            return;
        }

        // And now shut a valve in that line. This is the fault a player cannot see from the
        // fitting they are standing at, and the reason for the whole feature — asked from
        // the *port*, on the far side of the valve from the rest of the run, because that
        // is where a confused player is standing.
        helper.setBlock(pipe, ModBlocks.GAS_VALVE.get().defaultBlockState()
                .setValue(GasValveBlock.AXIS, Direction.Axis.Z)
                .setValue(GasValveBlock.SETTING, 0));
        var shut = PipeSurvey.of(helper.getLevel(), helper.absolutePos(port));
        if (shut == null || RunDiagnosis.verdict(shut) != RunDiagnosis.Verdict.SHUT_VALVE) {
            helper.fail("A shut valve on the line was not named: "
                    + (shut == null ? "no run" : RunDiagnosis.verdict(shut).toString())
                    + " - the walk stops dead at a shut valve, so from here the line looks"
                    + " like one that merely goes nowhere");
            return;
        }
        helper.succeed();
    }

    /** Uses the wrench on a block exactly as the game does, and reports what it answered. */
    private static InteractionResult useWrenchOn(GameTestHelper helper, Player player,
                                                 ItemStack wrench, BlockPos relative) {
        BlockPos at = helper.absolutePos(relative);
        return wrench.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(
                helper.getLevel(), player, InteractionHand.MAIN_HAND, wrench,
                new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(at), Direction.UP, at, false)));
    }

    /**
     * <em>"I ground it fine, put it on the table, and carried it outside."</em>
     *
     * <p>The winnowing table is the only machine in the mod whose working fluid is the
     * habitat's own air, so the lesson it teaches is a <em>placement</em>: indoors it
     * separates, outdoors it cannot, and it must say which. Both halves asserted, because a
     * machine that never runs would pass the vacuum check on its own.
     *
     * <p>Driven through the same {@code WorkState} the panel and the block's lamp both read,
     * so this is the channel the player actually sees rather than an internal flag.
     */
    private static void theWinnowingTableNeedsAir(GameTestHelper helper) {
        BlockPos inside = new BlockPos(3, 3, 3);
        ModTestFunctions.buildBoxAround(helper, inside);
        // In the wall, touching the room's air — the way every machine here reads a room.
        BlockPos tablePos = inside.offset(0, 0, 2);
        helper.setBlock(tablePos, ModBlocks.WINNOWING_TABLE.get().defaultBlockState());
        Atmosphere.get(helper.getLevel()).invalidate(helper.absolutePos(inside));

        var table = helper.getBlockEntity(tablePos,
                play.xponer.astronima.block.entity.WinnowingTableBlockEntity.class);
        RoomState room = Atmosphere.get(helper.getLevel()).roomAt(helper.absolutePos(inside));
        if (table == null || room == null) {
            helper.fail("Setup failed: no table or no room");
            return;
        }
        // A lived-in habitat. A freshly cut room starts empty, and a table in a vacuum is
        // the second half of this test rather than its starting condition.
        room.addGasAt(Gas.OXYGEN, 1200, 293.15);
        if (room.pressureKPa() < play.xponer.astronima.sim.ore.Elutriation.MINIMUM_PRESSURE_KPA) {
            helper.fail("The staged habitat is only at " + room.pressureKPa()
                    + " kPa, which is below the table's own threshold - this scenario would"
                    + " prove nothing about being indoors");
            return;
        }

        // A batch ground as fine as the crusher goes.
        ItemStack fine = new ItemStack(ModItems.CRUSHED_ORE.get());
        fine.set(play.xponer.astronima.registry.ModDataComponents.ORE_BATCH.get(),
                play.xponer.astronima.sim.ore.OreGrade.pack(
                        play.xponer.astronima.sim.ore.OreGrade.CHONDRITE, 1.0));
        table.setItem(play.xponer.astronima.block.entity.WinnowingTableBlockEntity.SLOT_INPUT,
                fine.copy());

        if (!table.workState().isWorking()) {
            helper.fail("A fed table in a pressurised room reported " + table.workState()
                    + " - it should be getting on with it");
            return;
        }

        // Now take the air away, which is what carrying it outside amounts to.
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        if (table.workState() != WorkState.UNPRESSURISED) {
            helper.fail("With the room evacuated the table reported " + table.workState()
                    + " - a player is told to check the slots when the answer is that there"
                    + " is no gas to lift anything with");
            return;
        }
        // And the same answer must reach the lamp, not only the machine's own head.
        var lamp = asTheClientSeesIt(helper, table);
        if (lamp == null || !lamp.label().contains(WorkState.UNPRESSURISED.label())) {
            helper.fail("The table's gauge says '" + (lamp == null ? "nothing" : lamp.label())
                    + "' while it is stalled for want of air");
            return;
        }

        // Air back, and it must pick up again — a stall that cannot be cleared is a
        // different bug wearing this one's clothes.
        room.addGasAt(Gas.OXYGEN, 600, 293.15);
        if (!table.workState().isWorking()) {
            helper.fail("Re-pressurising the room did not start the table again; it reports "
                    + table.workState());
            return;
        }

        // The separation itself, through the same entry point the machine uses: fine feed
        // in this air must beat the same feed ground coarse. Without this the test proves
        // only that a machine can stall.
        ItemStack coarse = new ItemStack(ModItems.CRUSHED_ORE.get());
        coarse.set(play.xponer.astronima.registry.ModDataComponents.ORE_BATCH.get(),
                play.xponer.astronima.sim.ore.OreGrade.pack(
                        play.xponer.astronima.sim.ore.OreGrade.CHONDRITE, 0.0));
        double pressure = table.roomPressureKPa();
        double fineGrade = play.xponer.astronima.block.entity.WinnowingTableBlockEntity
                .run(fine, pressure).grade();
        double coarseGrade = play.xponer.astronima.block.entity.WinnowingTableBlockEntity
                .run(coarse, pressure).grade();
        if (!(fineGrade > coarseGrade)) {
            helper.fail("A fine grind classified no better than a coarse one (" + fineGrade
                    + " vs " + coarseGrade + ") - the crusher's dial does nothing here, and"
                    + " the whole reason to run two grinds is gone");
            return;
        }

        // And the tier gate, in items: a table may not hand over more metal than the same
        // rock through the magnet, because the extra it concentrates is sulfide and oxide
        // and neither is metal until the chemical tier exists. The first version of this
        // machine converted its whole heavy stream into grains and skipped two tiers.
        var byTable = play.xponer.astronima.block.entity.WinnowingTableBlockEntity
                .run(fine, pressure).heavies();
        var byMagnet = play.xponer.astronima.block.entity.MagneticSeparatorBlockEntity
                .run(fine, 0.9).grains();
        if (byTable.getCount() > byMagnet.getCount()) {
            helper.fail("The table produced " + byTable.getCount() + " grains against the"
                    + " magnet's " + byMagnet.getCount() + " from the same rock - density"
                    + " concentrates sulfide, it does not reduce it");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I built my store room out of crates and now it will not hold pressure."</em>
     *
     * <p>It must, and that is the only thing separating this block from a box. A chest is
     * fourteen-sixteenths of a block, so the room scan reads it as a leak — a hole in the
     * hull you cannot see. A crate is a full cube and seals, which is what lets storage be
     * part of the structure.
     *
     * <p>Rule 15: the failure here is geometric and invisible. A crate whose collision box
     * was inset by one pixel for looks would render identically and vent the habitat, and
     * nobody would connect the two. So it is checked as data.
     *
     * <p>The other half is the standing rule that nothing eats a player's items: break a
     * full crate and everything comes back.
     */
    private static void aWallOfCratesHoldsPressure(GameTestHelper helper) {
        // Two habitats sharing a wall, with a crate set into it. Two rooms rather than one
        // box, because a leaky block does not un-seal a room — it opens a path between the
        // rooms on either side of it, and that exchange is the thing a player would see as
        // "my store room's air bled into the corridor".
        BlockPos leftInside = new BlockPos(2, 2, 2);
        BlockPos rightInside = leftInside.offset(0, 0, 4);
        ModTestFunctions.buildBoxAround(helper, leftInside);
        ModTestFunctions.buildBoxAround(helper, rightInside);
        BlockPos inWall = leftInside.offset(0, 0, 2); // the shared plane
        helper.setBlock(inWall, ModBlocks.CARGO_CRATE.get().defaultBlockState());

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(leftInside));
        atmosphere.invalidate(helper.absolutePos(rightInside));

        RoomState store = atmosphere.roomAt(helper.absolutePos(leftInside));
        RoomState corridor = atmosphere.roomAt(helper.absolutePos(rightInside));
        if (store == null || corridor == null) {
            helper.fail("A box with a crate in its wall stopped being a room at all");
            return;
        }
        if (store.id() == corridor.id()) {
            helper.fail("The crate did not divide the two rooms at all - they scanned as one");
            return;
        }

        // Charge the store room only, and empty the corridor, so any gas that turns up
        // next door came through the crate.
        for (Gas gas : Gas.values()) {
            corridor.removeGas(gas, corridor.gases().get(gas));
            store.removeGas(gas, store.gases().get(gas));
        }
        store.addGasAt(Gas.OXYGEN, 1200, 293.15);

        helper.runAfterDelay(Atmosphere.TICK_INTERVAL * 4L, () -> {
            RoomState nextDoor = atmosphere.roomAt(helper.absolutePos(rightInside));
            if (nextDoor == null) {
                helper.fail("The corridor stopped being a room");
                return;
            }
            if (nextDoor.pressureKPa() > 1.0) {
                helper.fail("Air bled through the crate into the next room ("
                        + nextDoor.pressureKPa() + " kPa) - a crate in a wall is a hole in"
                        + " the hull, which is the one thing that makes it not a chest");
                return;
            }
            breakAFullCrate(helper, inWall);
        });
    }

    /**
     * <em>"I broke the crusher with ore still in it."</em>
     *
     * <p>The standing rule that nothing in this mod ever eats a player's things, asserted
     * across <strong>every</strong> block that holds items rather than one at a time. Each of
     * them carries a hand-written drop-on-break override, and the crate's mutation showed
     * that such an override never actually runs — {@code BlockEntity.preRemoveSideEffects}
     * drops the contents of any {@code Container} first, and detaches the block entity before
     * the block's own hook fires.
     *
     * <p>So this exists to make that guarantee real rather than assumed: it is what allows
     * five pieces of look-alike dead code to be deleted without the behaviour going with
     * them. One test over a list, per rule 20 — naming the machines one by one is how the
     * next one added gets missed.
     */
    private static void breakingAFullMachineDropsEverything(GameTestHelper helper) {
        record Case(String name, net.minecraft.world.level.block.Block block,
                    net.minecraft.world.item.Item cargo) {}
        var cases = java.util.List.of(
                new Case("crusher", ModBlocks.ORE_CRUSHER.get(), ModItems.CRUSHED_ORE.get()),
                new Case("separator", ModBlocks.MAGNETIC_SEPARATOR.get(),
                        ModItems.IRON_NICKEL_GRAINS.get()),
                new Case("winnowing table", ModBlocks.WINNOWING_TABLE.get(),
                        ModItems.TAILINGS.get()),
                new Case("cold forge", ModBlocks.COLD_FORGE.get(), ModItems.METAL_BILLET.get()),
                new Case("solar retort", ModBlocks.SOLAR_RETORT.get(),
                        ModItems.BAKED_SILICATE.get()),
                new Case("cargo crate", ModBlocks.CARGO_CRATE.get(), ModItems.IRON_ROD.get()));

        // Laid out in a tight cluster, and that matters more than it looks: the first
        // version put the six machines five blocks apart in a line, which ran twenty-six
        // blocks out of this test's own area and into the next test's — where its setup
        // overwrote them with its own walls. The symptom was two machines "eating a
        // player's items", consistently, for entirely imaginary reasons.
        //
        // Counts stay honest because every machine is given a *different* item, not because
        // of the spacing: dropped stacks scatter, so any search wide enough to find a
        // machine's own spill reaches its neighbours' too.
        java.util.List<BlockPos> spots = new java.util.ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            BlockPos at = new BlockPos(1 + (i % 3) * SPILL_SPACING, 1,
                    1 + (i / 3) * SPILL_SPACING);
            spots.add(at);
            helper.setBlock(at, cases.get(i).block().defaultBlockState());
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(at))
                    instanceof net.minecraft.world.Container container)) {
                helper.fail(cases.get(i).name() + " has no container block entity, so this"
                        + " scenario would silently skip it");
                return;
            }
            container.setItem(0, new ItemStack(cases.get(i).cargo(), 4));
        }

        // The stacks have to actually be in there, or "it dropped nothing" would be a
        // report about the setup rather than about the machine.
        for (int i = 0; i < cases.size(); i++) {
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(spots.get(i)))
                    instanceof net.minecraft.world.Container container)
                    || container.getItem(0).getCount() != 4) {
                helper.fail("Could not load the " + cases.get(i).name() + " with 4 "
                        + cases.get(i).cargo() + " - it holds "
                        + (helper.getLevel().getBlockEntity(helper.absolutePos(spots.get(i)))
                                instanceof net.minecraft.world.Container c
                                ? c.getItem(0).toString() : "no container"));
                return;
            }
        }

        for (BlockPos at : spots) {
            helper.destroyBlock(at);
        }

        // A short settle before counting: the drops are thrown out with a random velocity
        // and this keeps the search radius honest rather than racing them.
        helper.runAfterDelay(10, () -> {
            for (int i = 0; i < cases.size(); i++) {
                int dropped = countDropped(helper, spots.get(i), cases.get(i).cargo());
                if (dropped != 4) {
                    helper.fail("Breaking the " + cases.get(i).name() + " dropped " + dropped
                            + " of the 4 items it was holding - it ate a player's things");
                    return;
                }
            }
            helper.succeed();
        });
    }

    /**
     * <em>"I logged out with my ore in the crate."</em>
     *
     * <p>Contents are real state, so they have to survive the save — and a store block that
     * forgot what was in it would be the single worst bug this mod could ship. Round-tripped
     * through the world's own save format, the same way the airlock's phase is, rather than
     * trusting that a container base class does the right thing.
     */
    private static void aCratesContentsSurviveAReload(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        helper.setBlock(at, ModBlocks.CARGO_CRATE.get().defaultBlockState());
        var crate = helper.getBlockEntity(at,
                play.xponer.astronima.block.entity.CargoCrateBlockEntity.class);
        if (crate == null) {
            helper.fail("The crate has no block entity");
            return;
        }
        crate.setItem(0, new ItemStack(ModItems.IRON_NICKEL_GRAINS.get(), 9));
        crate.setItem(26, new ItemStack(ModItems.BAKED_SILICATE.get(), 2));

        var registries = helper.getLevel().registryAccess();
        try (var scope = new net.minecraft.util.ProblemReporter.ScopedCollector(
                com.mojang.logging.LogUtils.getLogger())) {
            var output = net.minecraft.world.level.storage.TagValueOutput
                    .createWithContext(scope, registries);
            crate.saveWithoutMetadata(output);
            var input = net.minecraft.world.level.storage.TagValueInput
                    .create(scope, registries, output.buildResult());

            var reloaded = new play.xponer.astronima.block.entity.CargoCrateBlockEntity(
                    helper.absolutePos(at), helper.getBlockState(at));
            reloaded.loadWithComponents(input);

            ItemStack first = reloaded.getItem(0);
            ItemStack last = reloaded.getItem(26);
            if (!first.is(ModItems.IRON_NICKEL_GRAINS.get()) || first.getCount() != 9) {
                helper.fail("The first slot came back as " + first + " instead of 9 grains");
                return;
            }
            // The last slot specifically: an off-by-one in the container size would lose
            // exactly this one and nothing else, which is invisible until someone fills a
            // crate to the end.
            if (!last.is(ModItems.BAKED_SILICATE.get()) || last.getCount() != 2) {
                helper.fail("The last slot came back as " + last + " instead of 2 baked"
                        + " silicate - the far end of the crate is not being saved");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * The other standing rule: nothing in this mod eats a player's things.
     *
     * <p>Broken out so the seal check above reads as one thought. {@code destroyBlock},
     * not {@code setBlock}: breaking is what a player does and it is the path that runs the
     * block's removal hook, so replacing the block outright would assert something no player
     * can reach (rule 13).
     */
    private static void breakAFullCrate(GameTestHelper helper, BlockPos inWall) {
        var crate = helper.getBlockEntity(inWall,
                play.xponer.astronima.block.entity.CargoCrateBlockEntity.class);
        if (crate == null) {
            helper.fail("The crate has no block entity");
            return;
        }
        crate.setItem(0, new ItemStack(ModItems.IRON_NICKEL_GRAINS.get(), 7));
        crate.setItem(5, new ItemStack(ModItems.TAILINGS.get(), 3));

        helper.destroyBlock(inWall);
        helper.runAfterDelay(2, () -> {
            int grains = countDropped(helper, inWall, ModItems.IRON_NICKEL_GRAINS.get());
            int tailings = countDropped(helper, inWall, ModItems.TAILINGS.get());
            if (grains != 7 || tailings != 3) {
                helper.fail("Breaking a full crate dropped " + grains + " grains and "
                        + tailings + " tailings instead of 7 and 3 - it ate the difference");
                return;
            }
            helper.succeed();
        });
    }

    /** Blocks between machines in the spill test, kept small enough to stay in bounds. */
    private static final int SPILL_SPACING = 1;

    /**
     * How far to look for a broken block's drops.
     *
     * <p>Generous on purpose. {@code Containers.dropContents} throws stacks out with a
     * random velocity, so a tight radius misses them — which is how the first version of
     * the spill test reported a separator that had in fact dropped everything. Overlapping
     * a neighbour is harmless because each machine in that test holds a different item.
     */
    private static final double SPILL_RADIUS = 8.0;

    /** How many of an item are lying on the floor near {@code around}. */
    private static int countDropped(GameTestHelper helper, BlockPos around,
                                    net.minecraft.world.item.Item item) {
        int total = 0;
        // getEntities(EntityType.ITEM, ...) rather than getEntitiesOfClass: it is the call
        // GameTestHelper's own item assertions use, and the one that reliably sees freshly
        // spawned drops. getEntitiesOfClass found four machines' spill and missed two, every
        // run, which reads exactly like two machines eating a player's items.
        for (var entity : helper.getLevel().getEntities(
                net.minecraft.world.entity.EntityType.ITEM,
                new net.minecraft.world.phys.AABB(helper.absolutePos(around))
                        .inflate(SPILL_RADIUS),
                net.minecraft.world.entity.Entity::isAlive)) {
            if (entity.getItem().is(item)) {
                total += entity.getItem().getCount();
            }
        }
        return total;
    }

    // --------------------------------------------------------- door interlock (K)

    /**
     * <em>"I can yank the doors and nothing happens."</em>
     *
     * <p>Reported from play, and it was true. The controller did not <em>lock</em> the door,
     * it force-shut it again on its next tick — so the door opened, gas moved, and it was
     * pulled back up to a tick later. The invariant the whole block exists to provide was
     * being repaired rather than enforced.
     *
     * <p>Both halves asserted, because either alone proves nothing: the locked door must not
     * move when a player uses it, and the <em>other</em> door must still open normally, or
     * the "fix" is just a door that never works.
     */
    private static void aLockedDoorDoesNotOpen(GameTestHelper helper) {
        buildChamber(helper, 2, true, true);
        helper.setBlock(CONTROLLER, ModBlocks.AIRLOCK_CONTROLLER.get().defaultBlockState()
                .setValue(play.xponer.astronima.block.AirlockControllerBlock.FACING,
                        Direction.SOUTH));
        Atmosphere.get(helper.getLevel()).invalidate(helper.absolutePos(CHAMBER));
        var controller = helper.getBlockEntity(CONTROLLER,
                play.xponer.astronima.block.entity.AirlockControllerBlockEntity.class);
        RoomState chamber = Atmosphere.get(helper.getLevel())
                .roomAt(helper.absolutePos(CHAMBER));
        if (controller == null || chamber == null || !commission(helper, controller)) {
            helper.fail("Setup failed");
            return;
        }
        chamber.addGasAt(Gas.OXYGEN, 1200, 293.15);
        BlockPos controllerAbs = helper.absolutePos(CONTROLLER);

        // At rest the outer door is the one held: the chamber is at habitat pressure and
        // the only door that must not open is the one onto space.
        controller.tick(helper.getLevel(), controllerAbs, helper.getBlockState(CONTROLLER));
        BlockPos outer = BlockPos.of(controller.bindings()
                .device(play.xponer.astronima.sim.airlock.DeviceBinding.Role.OUTER_DOOR));
        BlockPos inner = BlockPos.of(controller.bindings()
                .device(play.xponer.astronima.sim.airlock.DeviceBinding.Role.INNER_DOOR));
        if (!play.xponer.astronima.block.BulkheadDoorBlock.isLocked(
                helper.getLevel().getBlockState(outer))) {
            helper.fail("The outer door is not latched at rest, so it can be opened onto"
                    + " vacuum from a pressurised chamber");
            return;
        }

        // Now yank it, exactly as reported.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.getLevel().getBlockState(outer).useWithoutItem(helper.getLevel(), player,
                new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(outer), Direction.NORTH,
                        outer, false));
        if (helper.getLevel().getBlockState(outer)
                .getValue(BlockStateProperties.OPEN)) {
            helper.fail("A latched bulkhead door opened when the player used it - the"
                    + " interlock is decoration");
            return;
        }

        // And the inner door, which is free at rest, must still work.
        helper.getLevel().getBlockState(inner).useWithoutItem(helper.getLevel(), player,
                new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(inner), Direction.NORTH,
                        inner, false));
        if (!helper.getLevel().getBlockState(inner).getValue(BlockStateProperties.OPEN)) {
            helper.fail("The unlatched inner door would not open either - an airlock whose"
                    + " doors never work is not an improvement");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I wired a lever to the airlock door."</em>
     *
     * <p>The route that was never covered at all: force-shutting only ran on the
     * controller's own tick, so redstone drove the door straight through the "interlock".
     * Wiring a base up is exactly what a player does once the airlock works, and it would
     * have opened a chamber onto vacuum with the controller none the wiser.
     */
    private static void redstoneCannotOpenALatchedDoor(GameTestHelper helper) {
        buildChamber(helper, 2, true, true);
        helper.setBlock(CONTROLLER, ModBlocks.AIRLOCK_CONTROLLER.get().defaultBlockState()
                .setValue(play.xponer.astronima.block.AirlockControllerBlock.FACING,
                        Direction.SOUTH));
        Atmosphere.get(helper.getLevel()).invalidate(helper.absolutePos(CHAMBER));
        var controller = helper.getBlockEntity(CONTROLLER,
                play.xponer.astronima.block.entity.AirlockControllerBlockEntity.class);
        if (controller == null || !commission(helper, controller)) {
            helper.fail("Setup failed");
            return;
        }
        BlockPos controllerAbs = helper.absolutePos(CONTROLLER);
        controller.tick(helper.getLevel(), controllerAbs, helper.getBlockState(CONTROLLER));

        BlockPos outerAbs = BlockPos.of(controller.bindings()
                .device(play.xponer.astronima.sim.airlock.DeviceBinding.Role.OUTER_DOOR));
        BlockPos outer = helper.relativePos(outerAbs);

        if (!play.xponer.astronima.block.BulkheadDoorBlock.isLocked(
                helper.getLevel().getBlockState(outerAbs))) {
            helper.fail("The outer door was never latched, so nothing here tests redstone"
                    + " against an interlock");
            return;
        }

        // A block of redstone on top of the door: the simplest possible always-on signal,
        // and the one an actual base is most likely to end up with. Placed in absolute
        // coordinates against the door we actually bound, rather than round-tripping
        // through the test's relative frame — the door's position is the only thing here
        // that has to be right.
        BlockPos source = outerAbs.above(2);
        helper.getLevel().setBlockAndUpdate(source, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.getLevel().updateNeighborsAt(source, Blocks.REDSTONE_BLOCK);
        helper.getLevel().updateNeighborsAt(outerAbs, Blocks.REDSTONE_BLOCK);

        // Both halves. A door is two blocks, redstone reaches whichever half is beside the
        // signal, and checking only the foot is how this check passed against a door that
        // was in fact swinging open — the first version of this test did exactly that.
        for (BlockPos half : new BlockPos[] {outerAbs, outerAbs.above()}) {
            if (helper.getLevel().getBlockState(half).getValue(BlockStateProperties.OPEN)) {
                helper.fail("Redstone opened the " + (half.equals(outerAbs) ? "lower" : "upper")
                        + " half of a latched bulkhead door - the chamber is now open to"
                        + " space with the controller still sequencing it");
                return;
            }
        }

        // The self-check, and it is not optional: a signal that could never have opened
        // this door proves nothing about the latch stopping it. Release the latch, poke the
        // same wiring, and the same door must swing. Without this half the test passes
        // just as well against a rig where the redstone was never connected — which is
        // exactly what the first version of it was.
        play.xponer.astronima.block.BulkheadDoorBlock.setLocked(
                helper.getLevel(), outerAbs, false);
        helper.getLevel().updateNeighborsAt(source, Blocks.REDSTONE_BLOCK);
        helper.getLevel().updateNeighborsAt(outerAbs, Blocks.REDSTONE_BLOCK);
        helper.getLevel().updateNeighborsAt(outerAbs.above(), Blocks.REDSTONE_BLOCK);

        boolean opened = helper.getLevel().getBlockState(outerAbs)
                        .getValue(BlockStateProperties.OPEN)
                || helper.getLevel().getBlockState(outerAbs.above())
                        .getValue(BlockStateProperties.OPEN);
        if (!opened) {
            helper.fail("The same redstone did not open the door once the latch was"
                    + " released, so this scenario never tested the latch at all"
                    + " [signal at the door: " + helper.getLevel().hasNeighborSignal(outerAbs)
                    + "/" + helper.getLevel().hasNeighborSignal(outerAbs.above()) + "]");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I mined the controller while I was standing in the chamber."</em>
     *
     * <p>The softlock rule, and the reason the latch is a lease rather than a flag. A
     * controller can vanish in ways nothing is told about — mined, exploded, chunk
     * unloaded, the server killed — so none of them is handled: they all simply stop the
     * renewals, and a latch nobody renews opens by itself.
     */
    private static void aLatchOutlivingItsControllerLetsGo(GameTestHelper helper) {
        buildChamber(helper, 2, true, true);
        helper.setBlock(CONTROLLER, ModBlocks.AIRLOCK_CONTROLLER.get().defaultBlockState()
                .setValue(play.xponer.astronima.block.AirlockControllerBlock.FACING,
                        Direction.SOUTH));
        Atmosphere.get(helper.getLevel()).invalidate(helper.absolutePos(CHAMBER));
        var controller = helper.getBlockEntity(CONTROLLER,
                play.xponer.astronima.block.entity.AirlockControllerBlockEntity.class);
        if (controller == null || !commission(helper, controller)) {
            helper.fail("Setup failed");
            return;
        }
        BlockPos controllerAbs = helper.absolutePos(CONTROLLER);
        controller.tick(helper.getLevel(), controllerAbs, helper.getBlockState(CONTROLLER));
        BlockPos outer = BlockPos.of(controller.bindings()
                .device(play.xponer.astronima.sim.airlock.DeviceBinding.Role.OUTER_DOOR));
        if (!play.xponer.astronima.block.BulkheadDoorBlock.isLocked(
                helper.getLevel().getBlockState(outer))) {
            helper.fail("The door was never latched, so this proves nothing about releasing");
            return;
        }

        // The controller goes. Nobody tells the door.
        helper.setBlock(CONTROLLER, Blocks.AIR.defaultBlockState());

        long lease = play.xponer.astronima.airlock.DoorLatches.LEASE_TICKS;
        helper.runAfterDelay(lease + play.xponer.astronima.airlock.DoorLatches
                .CHECK_INTERVAL_TICKS * 2L + 5, () -> {
            if (play.xponer.astronima.block.BulkheadDoorBlock.isLocked(
                    helper.getLevel().getBlockState(outer))) {
                helper.fail("The door is still latched " + lease + "+ ticks after its"
                        + " controller was destroyed - a player in the chamber is sealed in");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * <em>"What if I put several controllers?"</em> — the other half of the same question.
     *
     * <p>Two controllers must not both drive one pump, flipping its gate against each
     * other every tick. The first to claim it holds it; the second reports the pump in use
     * and does nothing, which is the same refusal the pump already makes elsewhere rather
     * than a new rule invented for this case.
     */
    private static void twoControllersDoNotFightOverOnePump(GameTestHelper helper) {
        Rig rig = Rig.build(helper, 4);
        GasPumpBlockEntity pump = helper.getBlockEntity(rig.pump, GasPumpBlockEntity.class);
        if (pump == null) {
            helper.fail("The pump has no block entity");
            return;
        }
        long now = helper.getLevel().getGameTime();
        BlockPos first = new BlockPos(0, 0, 0);
        BlockPos second = new BlockPos(0, 0, 1);

        if (!pump.claim(first, now)) {
            helper.fail("The first controller could not claim a free pump");
            return;
        }
        if (pump.claim(second, now)) {
            helper.fail("A second controller claimed a pump the first already holds -"
                    + " they would flip its gate against each other every tick");
            return;
        }
        // The holder still commands it, and the loser's orders are ignored outright.
        pump.setGate(first, false);
        pump.setGate(second, true);
        double before = rig.tank.pressureKPa();
        rig.run(200);
        if (rig.tank.pressureKPa() > before + 1) {
            helper.fail("The second controller's order opened a gate it does not hold");
            return;
        }
        // Release, and the pump is claimable again — a controller broken mid-cycle must
        // not leave its pump locked to a block that no longer exists.
        pump.release(first);
        if (!pump.claim(second, now)) {
            helper.fail("A released pump could not be claimed by the other controller");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"What if I put a second controller on the same door?"</em> — the pump already
     * refuses exactly this ({@code twoControllersDoNotFightOverOnePump}); a door had no
     * equivalent at all.
     *
     * <p>Nothing stopped two controllers from both binding the same physical bulkhead door:
     * each would lock and unlock it from its own, unrelated cycle, and whichever one ticks
     * last on a given tick decides whether the door is actually locked — the interlock's
     * real state becomes a coin flip rather than a guarantee. Checked directly against the
     * real commissioning validation and the real claim registry, the same way the pump
     * scenario above drives {@code GasPumpBlockEntity} directly rather than building a
     * second physical airlock next to the first.
     */
    private static void twoControllersDoNotFightOverOneDoor(GameTestHelper helper) {
        buildChamber(helper, 2, true, true);
        helper.setBlock(CONTROLLER, ModBlocks.AIRLOCK_CONTROLLER.get().defaultBlockState()
                .setValue(play.xponer.astronima.block.AirlockControllerBlock.FACING,
                        Direction.SOUTH));
        Atmosphere.get(helper.getLevel()).invalidate(helper.absolutePos(CHAMBER));
        var controller = helper.getBlockEntity(CONTROLLER,
                play.xponer.astronima.block.entity.AirlockControllerBlockEntity.class);
        if (controller == null || !commission(helper, controller)) {
            helper.fail("Setup failed");
            return;
        }
        BlockPos controllerAbs = helper.absolutePos(CONTROLLER);
        Direction facing = helper.getLevel().getBlockState(controllerAbs)
                .getValue(play.xponer.astronima.block.AirlockControllerBlock.FACING);
        // Claims both doors for real — the same call a live cycle makes every tick.
        controller.tick(helper.getLevel(), controllerAbs, helper.getBlockState(CONTROLLER));

        BlockPos outerAbs = BlockPos.of(controller.bindings()
                .device(play.xponer.astronima.sim.airlock.DeviceBinding.Role.OUTER_DOOR));

        // A second, unrelated controller tries to commission the very same door.
        var rivalBindings = new play.xponer.astronima.sim.airlock.DeviceBinding();
        rivalBindings.bind(play.xponer.astronima.sim.airlock.DeviceBinding.Role.OUTER_DOOR,
                outerAbs.asLong());
        BlockPos rivalIdentity = controllerAbs.above(30); // stands in for "somewhere else"
        var rivalCheck = AirlockCommissioning.validate(
                helper.getLevel(), controllerAbs, facing, rivalBindings, rivalIdentity);
        if (rivalCheck.problem(play.xponer.astronima.sim.airlock.DeviceBinding.Role.OUTER_DOOR)
                != play.xponer.astronima.sim.airlock.DeviceBinding.Problem.IN_USE) {
            helper.fail("A second controller was allowed to commission a door the first"
                    + " controller already holds (reads as "
                    + rivalCheck.problem(play.xponer.astronima.sim.airlock.DeviceBinding.Role.OUTER_DOOR)
                    + ") - the two would fight over its latch every tick");
            return;
        }

        // Release, exactly as a controller does when its own commissioning stops checking
        // out - and the door becomes claimable by whoever asks next.
        AirlockCommissioning.releaseDoor(helper.getLevel(), outerAbs, controllerAbs);
        var afterRelease = AirlockCommissioning.validate(
                helper.getLevel(), controllerAbs, facing, rivalBindings, rivalIdentity);
        if (afterRelease.problem(play.xponer.astronima.sim.airlock.DeviceBinding.Role.OUTER_DOOR)
                == play.xponer.astronima.sim.airlock.DeviceBinding.Problem.IN_USE) {
            helper.fail("A released door claim still reports in use - a controller that"
                    + " failed safe would leave every other airlock locked out of a door it"
                    + " no longer uses");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"Come home on stored air" should mean the tank actually gave something back.</em>
     *
     * <p>{@code AirlockCycle} completes REPRESSURIZING two real ways: the chamber reaches
     * its target, or the tank runs dry — both fine for the cycle itself. But the controller
     * used to award CAME_HOME on every REPRESSURIZING → SEALED transition regardless of
     * which one happened, so a return on a tank that was already empty for the whole leg —
     * zero moles ever moved — still earned the advancement named for stored air it never
     * drew on.
     *
     * <p>Both halves asserted, because either alone proves nothing: a real trip must still
     * earn the moment, or "fixing" this would just be an advancement that never fires.
     * {@code lastLegMoment()} stands in for the grant itself, which the harness has no way
     * to observe (its own AABB scan finds no player — reference-gametest-harness-limits).
     */
    private static void airlockDoesNotClaimStoredAirItNeverUsed(GameTestHelper helper) {
        buildChamber(helper, 2, true, true);
        helper.setBlock(CONTROLLER, ModBlocks.AIRLOCK_CONTROLLER.get().defaultBlockState()
                .setValue(play.xponer.astronima.block.AirlockControllerBlock.FACING,
                        Direction.SOUTH));
        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(CHAMBER));
        RoomState chamber = atmosphere.roomAt(helper.absolutePos(CHAMBER));
        GasTankBlockEntity tank = helper.getBlockEntity(TANK, GasTankBlockEntity.class);
        var controller = helper.getBlockEntity(CONTROLLER,
                play.xponer.astronima.block.entity.AirlockControllerBlockEntity.class);
        if (chamber == null || tank == null || controller == null
                || !commission(helper, controller)) {
            helper.fail("Setup failed");
            return;
        }
        chamber.addGasAt(Gas.OXYGEN, 1200, 293.15);

        // The real trip: come home with a tank the pump-down actually filled. This must
        // still earn the moment, or the fix broke the case it has to keep working for.
        controller.command();
        if (runUntil(helper, controller, AirlockCycle.Phase.VACUUM,
                AirlockCycle.MAX_PUMP_TICKS + 200) < 0) {
            helper.fail("Never reached vacuum; stuck in " + controller.phase());
            return;
        }
        if (tank.contents().gases().totalMoles() < 1.0) {
            helper.fail("Setup failed: the pump-down did not stock the tank, so this"
                    + " scenario cannot tell a real trip from an empty one");
            return;
        }
        controller.command();
        if (runUntil(helper, controller, AirlockCycle.Phase.SEALED, 600) < 0) {
            helper.fail("The real return never completed; stuck in " + controller.phase());
            return;
        }
        if (controller.lastLegMoment() != AirlockTrigger.Moment.CAME_HOME) {
            helper.fail("A return that genuinely drew on the tank did not earn CAME_HOME"
                    + " (decided " + controller.lastLegMoment() + ") - the fix broke the"
                    + " case it is supposed to still work for");
            return;
        }

        // Out again, then the tank is drained completely — by another machine on the same
        // line, say — before coming home a second time.
        RoomState chamber2 = atmosphere.roomAt(helper.absolutePos(CHAMBER));
        if (chamber2 == null) {
            helper.fail("The chamber stopped being a room between trips");
            return;
        }
        for (Gas gas : Gas.values()) {
            chamber2.removeGas(gas, chamber2.gases().get(gas));
        }
        chamber2.addGasAt(Gas.OXYGEN, 1200, 293.15);
        controller.command();
        if (runUntil(helper, controller, AirlockCycle.Phase.VACUUM,
                AirlockCycle.MAX_PUMP_TICKS + 200) < 0) {
            helper.fail("The second trip out never reached vacuum; stuck in "
                    + controller.phase());
            return;
        }
        RoomState store = tank.contents();
        for (Gas gas : Gas.values()) {
            store.removeGas(gas, store.gases().get(gas));
        }
        // Empty on purpose: this leg must complete via "the tank ran dry" (a real, legal
        // completion), not by reaching the repressurise target, and it must not earn the
        // advancement it is named for.
        controller.command();
        if (runUntil(helper, controller, AirlockCycle.Phase.SEALED, 600) < 0) {
            helper.fail("The dry return never completed; stuck in " + controller.phase());
            return;
        }
        if (controller.lastLegMoment() == AirlockTrigger.Moment.CAME_HOME) {
            helper.fail("Coming home on a tank that was empty for the whole leg still"
                    + " earned CAME_HOME - the advancement fired without the stored air it"
                    + " is named for");
            return;
        }
        helper.succeed();
    }

    /**
     * Commissions a controller the way the player does: the panel's SCAN button.
     *
     * <p>Since PLAN rule 17 an airlock does <em>not</em> commission itself — a fresh
     * controller is meant to sit there with four empty terminals until someone fills them,
     * and a scenario that skipped this step and still cycled would be proving the exact
     * silent resolution the rule removed. This is the same call the SCAN packet makes, so
     * these scenarios go through the player's door (rule 13) and not round the back.
     */
    private static boolean commission(GameTestHelper helper,
            play.xponer.astronima.block.entity.AirlockControllerBlockEntity controller) {
        return controller.scan(helper.getLevel());
    }

    private static void expectFault(GameTestHelper helper, AirlockResolver.Fault expected) {
        AirlockResolver.Result r = resolveHere(helper);
        if (r.ok() || r.fault() != expected) {
            helper.fail("Expected " + expected + " but got "
                    + (r.ok() ? "a complete airlock" : r.fault()));
            return;
        }
        helper.succeed();
    }

    /** Resolves the airlock at the fixed controller position, re-scanning the chamber first. */
    private static AirlockResolver.Result resolveHere(GameTestHelper helper) {
        Atmosphere.get(helper.getLevel()).invalidate(
                helper.absolutePos(CHAMBER.relative(Direction.NORTH))); // (4,4,3), the interior
        return AirlockResolver.resolve(helper.getLevel(),
                helper.absolutePos(CONTROLLER), Direction.SOUTH);
    }

    private static void buildChamber(GameTestHelper helper, int doors, boolean pump, boolean tank) {
        ModTestFunctions.buildBoxAround(helper, CHAMBER);
        placeDoor(helper, DOOR_A);
        if (doors >= 2) {
            placeDoor(helper, DOOR_B);
        }
        helper.setBlock(PORT, ModBlocks.GAS_PORT.get().defaultBlockState()
                .setValue(GasPortBlock.FACING, Direction.NORTH));
        helper.setBlock(PIPE, ModBlocks.GAS_PIPE.get().defaultBlockState());
        if (pump) {
            helper.setBlock(PUMP, ModBlocks.GAS_PUMP.get().defaultBlockState()
                    .setValue(GasPumpBlock.FACING, Direction.SOUTH));
        }
        if (tank) {
            helper.setBlock(TANK, ModBlocks.GAS_TANK.get().defaultBlockState());
        }
    }

    /** Both halves of a closed bulkhead door, foot and upper. */
    private static void placeDoor(GameTestHelper helper, BlockPos foot) {
        helper.setBlock(foot, ModBlocks.BULKHEAD_DOOR.get().defaultBlockState());
        helper.setBlock(foot.above(), ModBlocks.BULKHEAD_DOOR.get().defaultBlockState()
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
    }

    private static double totalIn(GameTestHelper helper, BlockPos inside, BlockPos... tanks) {
        double total = 0;
        RoomState room = Atmosphere.get(helper.getLevel())
                .roomAt(helper.absolutePos(inside));
        if (room != null) {
            for (Gas gas : Gas.values()) {
                total += room.gases().get(gas);
            }
        }
        for (BlockPos tank : tanks) {
            GasTankBlockEntity vessel = helper.getBlockEntity(tank, GasTankBlockEntity.class);
            if (vessel != null) {
                for (Gas gas : Gas.values()) {
                    total += vessel.contents().gases().get(gas);
                }
            }
        }
        return total;
    }
    // ---------------------------------------------------------------------- rig

    /** The layout the Russian guide tells a player to build, and nothing more. */
    private record Rig(GameTestHelper helper, BlockPos inside, GasTankBlockEntity tank,
                       BlockPos pump, BlockPos valve) {

        BlockPos tankPos() {
            return pump.offset(0, 0, 1);
        }

        /**
         * The room as it is now, not as it was when the rig was built.
         *
         * <p>Room states are re-created by the atmosphere scan, so a held reference goes
         * stale and reads values nothing is writing to any more. The pump already looks
         * its room up fresh every tick; a test that does not would be watching a
         * different object than the one being filled.
         */
        RoomState room() {
            RoomState current = Atmosphere.get(helper.getLevel())
                    .roomAt(helper.absolutePos(inside));
            if (current == null) {
                helper.fail("The room stopped existing during the scenario");
                throw new IllegalStateException("no room");
            }
            return current;
        }

        static Rig build(GameTestHelper helper, int oxygenMoles) {
            BlockPos inside = new BlockPos(2, 2, 2);
            ModTestFunctions.buildBoxAround(helper, inside);

            Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
            RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
            if (room == null) {
                helper.fail("The sealed box did not become a room");
                throw new IllegalStateException("no room");
            }
            room.addGasAt(Gas.OXYGEN, oxygenMoles * 25.0, 293.15);

            BlockPos port = inside.offset(0, 0, 2);
            BlockPos valve = port.offset(0, 0, 1);
            BlockPos pump = valve.offset(0, 0, 1);
            BlockPos tank = pump.offset(0, 0, 1);

            helper.setBlock(port, ModBlocks.GAS_PORT.get().defaultBlockState()
                    .setValue(GasPortBlock.FACING, Direction.NORTH));
            helper.setBlock(valve, ModBlocks.GAS_VALVE.get().defaultBlockState());
            helper.setBlock(pump, ModBlocks.GAS_PUMP.get().defaultBlockState()
                    .setValue(GasPumpBlock.FACING, Direction.SOUTH));
            helper.setBlock(tank, ModBlocks.GAS_TANK.get().defaultBlockState());
            atmosphere.invalidate(helper.absolutePos(port));

            GasTankBlockEntity vessel = helper.getBlockEntity(tank, GasTankBlockEntity.class);
            GasPumpBlockEntity motor = helper.getBlockEntity(pump, GasPumpBlockEntity.class);
            if (vessel == null || motor == null) {
                helper.fail("The tank or pump has no block entity");
                throw new IllegalStateException("no block entity");
            }
            return new Rig(helper, inside, vessel, pump, valve);
        }

        /**
         * Ticks the pump the way the world would, so nothing is bypassed.
         *
         * <p>The block entity is fetched fresh each call rather than held: changing a
         * block's state destroys and recreates its entity, so a cached reference would
         * quietly become a detached object that ticks and affects nothing — which is
         * exactly what made the reversed-pump scenario look broken when it was not.
         */
        void run(int ticks) {
            BlockPos absolute = helper.absolutePos(pump);
            for (int i = 0; i < ticks; i++) {
                GasPumpBlockEntity live =
                        helper.getBlockEntity(pump, GasPumpBlockEntity.class);
                if (live == null) {
                    helper.fail("The pump lost its block entity mid-run");
                    return;
                }
                live.pumpOnce(helper.getLevel(), absolute,
                        helper.getLevel().getBlockState(absolute));
            }
        }
    }

    /**
     * The golden path for Astra Incognita's first real content (design/astra-incognita.md §8.1):
     * a blank plate in the off hand, the spectrograph in the main hand, open sky — one right
     * click captures the Sun.
     *
     * <p>Calls {@code Item#use} directly rather than driving a click through the block-targeted
     * path {@code rightClick} exists for (rule 40): this interaction has no block to target, so
     * {@code use} <em>is</em> the door the game itself calls for it — there is no earlier phase
     * to skip past.
     */
    private static void spectrographExposesAPlateUnderOpenSky(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos standPos = new BlockPos(1, 2, 1); // the same open-sky column the solar array
                                                    // scenario already proved has no roof over it
        player.setPos(helper.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(standPos)));
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.SPECTROGRAPH.get()));
        player.setItemInHand(InteractionHand.OFF_HAND,
                new ItemStack(ModItems.SPECTRAL_PLATE.get()));

        InteractionResult result = ModItems.SPECTROGRAPH.get()
                .use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        if (result != InteractionResult.SUCCESS) {
            helper.fail("Spectrograph refused under open sky in daylight: " + result);
            return;
        }
        if (!play.xponer.astronima.item.SpectralPlateItem.isExposed(player.getOffhandItem())) {
            helper.fail("Off hand still holds a blank plate after a successful exposure");
            return;
        }
        // A real capture registers the same fact the telescope's own capture does (rule 46) -
        // without this, ResearchReachabilityTest's own graph is a lie for anyone using this
        // instrument: Claims.ALL gates every stage-1 requirement on Requirement.Identified, which
        // SpectrumDecodeAttemptPayload only ever grants once ResearchState.captured(objectId) is
        // already true. A player who only ever used the spectrograph made zero real progress and
        // was never told.
        play.xponer.astronima.sim.magic.ResearchState afterCapture =
                player.getData(play.xponer.astronima.registry.ModAttachments.RESEARCH.get());
        String sunId = play.xponer.astronima.sim.magic.Claims.objectId(
                play.xponer.astronima.sim.magic.ObservationTarget.SUN);
        if (!afterCapture.captured(sunId)) {
            helper.fail("The spectrograph exposed a real plate but ResearchState never heard "
                    + "about the capture - the telescope's own capture writes this fact and "
                    + "nothing downstream can tell the two instruments apart");
            return;
        }
        helper.succeed();
    }

    /**
     * The refusal half of the same scenario: nothing to expose means nothing happens, and the
     * game says so rather than silently consuming a click.
     */
    private static void spectrographRefusesWithoutABlankPlate(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos standPos = new BlockPos(1, 2, 1);
        player.setPos(helper.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(standPos)));
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.SPECTROGRAPH.get()));
        // Off hand deliberately empty: nothing to expose.

        InteractionResult result = ModItems.SPECTROGRAPH.get()
                .use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        if (result == InteractionResult.SUCCESS) {
            helper.fail("Spectrograph reported success with nothing in the off hand to expose");
            return;
        }
        helper.succeed();
    }

    /**
     * design/sky.md L4's first real content: aimed at {@link NamedSkyObjects#ORION_NEBULA}'s
     * own current direction — computed here the same way {@link SpectrographItem} computes it
     * internally, via {@link SkyRotation}, rather than a direction chosen by eye — one right
     * click captures that target, not the Sun, and does so with no daylight requirement at all
     * (the scenario deliberately does not check or set the world's time, unlike the Sun's own
     * scenario above, because a real vacuum sky has no reason to hide a deep-sky object at
     * night either).
     */
    private static void spectrographCapturesTheNamedNebulaWhenAimedAtIt(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos standPos = new BlockPos(1, 2, 1);
        player.setPos(helper.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(standPos)));
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.SPECTROGRAPH.get()));
        player.setItemInHand(InteractionHand.OFF_HAND,
                new ItemStack(ModItems.SPECTRAL_PLATE.get()));

        long gameTime = helper.getLevel().getGameTime();
        SkyRotation.Vec3 currentDirection = SkyRotation.currentDirection(
                NamedSkyObjects.ORION_NEBULA.fixedDirection(), gameTime);
        net.minecraft.world.phys.Vec3 lookTarget = player.getEyePosition().add(
                currentDirection.x(), currentDirection.y(), currentDirection.z());
        player.lookAt(EntityAnchorArgument.Anchor.EYES, lookTarget);

        InteractionResult result = ModItems.SPECTROGRAPH.get()
                .use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        if (result != InteractionResult.SUCCESS) {
            helper.fail("Spectrograph refused while aimed at the named nebula: " + result);
            return;
        }
        ItemStack exposedPlate = player.getOffhandItem();
        CapturedSpectrum captured = exposedPlate.get(ModDataComponents.CAPTURED_SPECTRUM.get());
        if (captured == null) {
            helper.fail("Off hand still holds a blank plate after a successful exposure");
            return;
        }
        if (captured.target() != NamedSkyObjects.ORION_NEBULA.target()) {
            helper.fail("Captured " + captured.target() + " while aimed at the nebula, not it");
            return;
        }
        helper.succeed();
    }

    /**
     * design/astra-telescope.md §2.1 v3 — right-clicking an unoccupied telescope starts a real,
     * server-tracked observation session on a {@link TelescopeMountEntity} without moving or
     * repossessing the player's own entity at all (PLAN.md rule 79's correction away from riding).
     * Entered through the same door the player uses (rule 13) — {@code rightClick}, not a direct
     * call into the block's own Java method.
     */
    private static void telescopeSeatsAPlayerWhoRightClicksIt(GameTestHelper helper) {
        BlockPos telescopePos = new BlockPos(1, 2, 1);
        helper.setBlock(telescopePos, ModBlocks.TELESCOPE.get().defaultBlockState());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(
                net.minecraft.world.phys.Vec3.atBottomCenterOf(telescopePos.south(2))));

        rightClick(helper, telescopePos, player);

        TelescopeMountEntity mount = TelescopeMountEntity.findAt(helper.getLevel(), helper.absolutePos(telescopePos));
        if (mount == null) {
            helper.fail("Right-clicking an unoccupied telescope did not start a session");
            return;
        }
        if (!mount.isObservedBy(player)) {
            helper.fail("The mount exists but does not consider the clicking player its observer");
            return;
        }
        helper.succeed();
    }

    /**
     * §8's T1 test plan: "one already occupied by another player" is refused, not queued — the
     * second player never becomes the observer of a session that is not theirs.
     */
    private static void telescopeRefusesASecondRider(GameTestHelper helper) {
        BlockPos telescopePos = new BlockPos(1, 2, 1);
        helper.setBlock(telescopePos, ModBlocks.TELESCOPE.get().defaultBlockState());
        Player first = helper.makeMockPlayer(GameType.SURVIVAL);
        first.setPos(helper.absoluteVec(
                net.minecraft.world.phys.Vec3.atBottomCenterOf(telescopePos.south(2))));
        rightClick(helper, telescopePos, first);
        TelescopeMountEntity mount = TelescopeMountEntity.findAt(helper.getLevel(), helper.absolutePos(telescopePos));
        if (mount == null || !mount.isObservedBy(first)) {
            helper.fail("Setup failed: the first player's session was never established");
            return;
        }

        Player second = helper.makeMockPlayer(GameType.SURVIVAL);
        second.setPos(helper.absoluteVec(
                net.minecraft.world.phys.Vec3.atBottomCenterOf(telescopePos.north(2))));
        rightClick(helper, telescopePos, second);

        if (mount.isObservedBy(second)) {
            helper.fail("A second player took over an already-occupied telescope's session");
            return;
        }
        if (!mount.isObservedBy(first)) {
            helper.fail("The first player's own session was disturbed by a refused second click");
            return;
        }
        helper.succeed();
    }

    /**
     * §8's T1 test plan: breaking a telescope out from under an active observation discards the
     * mount immediately, rather than leaving a session pointed at nothing. {@code tick()} is
     * called directly rather than waited for, the same shape {@code crankFor} already uses
     * elsewhere in this file for a machine's own {@code serverTick}.
     */
    private static void breakingAnOccupiedTelescopeEjectsTheRider(GameTestHelper helper) {
        BlockPos telescopePos = new BlockPos(1, 2, 1);
        helper.setBlock(telescopePos, ModBlocks.TELESCOPE.get().defaultBlockState());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(
                net.minecraft.world.phys.Vec3.atBottomCenterOf(telescopePos.south(2))));
        rightClick(helper, telescopePos, player);

        TelescopeMountEntity mount = TelescopeMountEntity.findAt(helper.getLevel(), helper.absolutePos(telescopePos));
        if (mount == null) {
            helper.fail("Setup failed: the session was never established");
            return;
        }

        helper.setBlock(telescopePos, Blocks.AIR.defaultBlockState());
        mount.tick();

        if (!mount.isRemoved()) {
            helper.fail("The abandoned mount was not discarded after its telescope was broken");
            return;
        }
        helper.succeed();
    }

    /**
     * Direct playtest request, reversing this document's own original assumption (design/
     * astra-telescope.md §9 open question 4): "хочу что-бы телескоп сохранял позицию в которой
     * его оставили когда вышли." {@code setClampedAim} is called directly rather than driven
     * through real mouse input — the same shape of substitution {@code crankFor} already uses for
     * a machine's own {@code serverTick} — because it is the exact method both the server (from
     * {@code TelescopeAimUpdatePayload}) and the client's own per-frame aim push
     * ({@code client.TelescopeCamera}) call, not a re-implementation of it.
     */
    private static void telescopeRemembersItsLastAimAfterTheRiderLeaves(GameTestHelper helper) {
        BlockPos telescopePos = new BlockPos(1, 2, 1);
        helper.setBlock(telescopePos, ModBlocks.TELESCOPE.get().defaultBlockState());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(
                net.minecraft.world.phys.Vec3.atBottomCenterOf(telescopePos.south(2))));

        rightClick(helper, telescopePos, player);
        TelescopeMountEntity mount = TelescopeMountEntity.findAt(helper.getLevel(), helper.absolutePos(telescopePos));
        if (mount == null) {
            helper.fail("Setup failed: the session was never established");
            return;
        }

        mount.setClampedAim(mount.getYRot() + 40.0F, mount.getXRot());
        float aimedYaw = mount.getYRot();
        float aimedPitch = mount.getXRot();

        // Leave through the same door a player uses: right-click the same telescope again.
        rightClick(helper, telescopePos, player);
        if (!mount.isRemoved()) {
            helper.fail("Setup failed: the second right-click did not end the session");
            return;
        }

        rightClick(helper, telescopePos, player);
        TelescopeMountEntity secondMount =
                TelescopeMountEntity.findAt(helper.getLevel(), helper.absolutePos(telescopePos));
        if (secondMount == null) {
            helper.fail("Reseating after leaving failed");
            return;
        }
        if (Math.abs(secondMount.getYRot() - aimedYaw) > 0.01F
                || Math.abs(secondMount.getXRot() - aimedPitch) > 0.01F) {
            helper.fail("A fresh mount did not start at the previously saved aim: expected ("
                    + aimedYaw + ", " + aimedPitch + ") got (" + secondMount.getYRot() + ", "
                    + secondMount.getXRot() + ")");
            return;
        }
        helper.succeed();
    }

    /**
     * PLAN.md rule 80: {@code setChanged()} alone never tells an already-connected client anything
     * changed — only {@code getUpdatePacket()} (built from {@code getUpdateTag()}) reaching a real
     * client does that, and nothing gametests can exercise proves a packet actually left the
     * server. What this *can* prove: {@code saveAim} causes the tag {@code getUpdateTag} builds to
     * actually carry the new aim, rather than the empty tag {@code BlockEntity}'s own default
     * would silently keep returning if the override were ever missing or reverted.
     */
    private static void telescopeSavedAimUpdateTagCarriesTheNewAim(GameTestHelper helper) {
        BlockPos telescopePos = new BlockPos(1, 2, 1);
        helper.setBlock(telescopePos, ModBlocks.TELESCOPE.get().defaultBlockState());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(
                net.minecraft.world.phys.Vec3.atBottomCenterOf(telescopePos.south(2))));

        rightClick(helper, telescopePos, player);
        TelescopeMountEntity mount = TelescopeMountEntity.findAt(helper.getLevel(), helper.absolutePos(telescopePos));
        if (mount == null) {
            helper.fail("Setup failed: the session was never established");
            return;
        }
        mount.setClampedAim(mount.getYRot() + 25.0F, mount.getXRot());
        float aimedYaw = mount.getYRot();

        // Ending the session triggers TelescopeBlockEntity#saveAim.
        rightClick(helper, telescopePos, player);
        if (!mount.isRemoved()) {
            helper.fail("Setup failed: ending the session did not work");
            return;
        }

        TelescopeBlockEntity blockEntity = helper.getBlockEntity(telescopePos, TelescopeBlockEntity.class);
        net.minecraft.nbt.CompoundTag tag = blockEntity.getUpdateTag(helper.getLevel().registryAccess());
        if (!tag.getBooleanOr("has_saved_aim", false)) {
            helper.fail("getUpdateTag() did not report a saved aim at all — this is exactly the "
                    + "tag a real client receives, and an unaware client falls back to the "
                    + "telescope's neutral rest angle forever: " + tag);
            return;
        }
        float tagYaw = tag.getFloatOr("saved_yaw", Float.NaN);
        if (Math.abs(tagYaw - aimedYaw) > 0.01F) {
            helper.fail("getUpdateTag() carried the wrong yaw: expected " + aimedYaw + " got " + tagYaw);
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I bolted a cryocooler to a dewar and a battery. Does it actually make liquid nitrogen?"</em>
     *
     * <p>Enters through the same door the player does (rule 13): the cooler's own
     * {@code serverTick}, called at whatever cadence the game drives it — not
     * {@code Liquefaction.molesLiquefiedFor} called directly, which would prove the physics but
     * say nothing about whether the machine actually calls it.
     */
    private static void aCryocoolerLiquefiesRoomGasIntoItsBoundTank(GameTestHelper helper) {
        BlockPos inside = new BlockPos(3, 2, 3);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean shell = dx == -2 || dx == 2 || dy == -1 || dy == 2 || dz == -2 || dz == 2;
                    helper.setBlock(inside.offset(dx, dy, dz), shell
                            ? ModBlocks.HULL_PLATE.get().defaultBlockState()
                            : Blocks.AIR.defaultBlockState());
                }
            }
        }
        BlockPos coolerPos = inside.offset(1, 0, 0);
        BlockPos tankPos = coolerPos.relative(Direction.NORTH);
        BlockPos cellPos = coolerPos.relative(Direction.UP);
        helper.setBlock(coolerPos, ModBlocks.CRYO_COOLER.get().defaultBlockState());
        helper.setBlock(tankPos, ModBlocks.CRYO_TANK.get().defaultBlockState());
        helper.setBlock(cellPos, ModBlocks.POWER_CELL.get().defaultBlockState());

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        atmosphere.invalidate(helper.absolutePos(inside));
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        var cooler = helper.getBlockEntity(coolerPos, CryoCoolerBlockEntity.class);
        var tank = helper.getBlockEntity(tankPos, CryoTankBlockEntity.class);
        var cell = helper.getBlockEntity(cellPos, PowerCellBlockEntity.class);
        if (room == null || cooler == null || tank == null || cell == null) {
            helper.fail("Setup failed: room=" + room + " cooler=" + cooler + " tank=" + tank
                    + " cell=" + cell);
            return;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        room.addGasAt(Gas.NITROGEN, 500.0, 293.15);
        room.setTemperatureK(293.15);
        cell.draw(cell.storedJ());
        cell.charge(PowerCellBlockEntity.CAPACITY_J);

        double nitrogenBefore = room.gases().get(Gas.NITROGEN);
        BlockPos absoluteCoolerPos = helper.absolutePos(coolerPos);
        for (int i = 0; i < Atmosphere.TICK_INTERVAL + 1; i++) {
            cooler.serverTick(helper.getLevel(), absoluteCoolerPos);
        }

        if (cooler.stall() != CryoCoolerBlockEntity.Stall.RUNNING) {
            helper.fail("Cooler did not run: stall=" + cooler.stall());
            return;
        }
        if (tank.liquidMoles() <= 0) {
            helper.fail("The bound tank has no liquid nitrogen after the cooler ran");
            return;
        }
        if (tank.cryogen() != Cryogen.LN2) {
            helper.fail("The tank committed to the wrong species: " + tank.cryogen());
            return;
        }
        double nitrogenAfter = room.gases().get(Gas.NITROGEN);
        if (nitrogenAfter >= nitrogenBefore) {
            helper.fail("The room's own nitrogen did not fall even though the cooler liquefied"
                    + " some of it — " + nitrogenBefore + " -> " + nitrogenAfter);
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I filled a dewar and never plumbed a vent. What happens if I just leave it?"</em>
     *
     * <p>The BLEVE hazard end to end, through the tank's own {@code serverTick} — not
     * {@code Bleve.explosionEnergyJ} called directly. Seeds the headspace pressure straight to
     * burst rather than waiting out the real boil-off time (design/cryogenics.md's own numbers
     * put that around six hours of real cadence for a bare dewar), which is the same "enter
     * through the same door, at whatever state that door can reach" the airlock's own
     * mid-cycle-fault scenario already established — the setup skips ahead, the assertion is
     * still made through the real tick.
     */
    private static void aNeglectedCryoTankBleves(GameTestHelper helper) {
        BlockPos tankPos = new BlockPos(3, 2, 3);
        helper.setBlock(tankPos, ModBlocks.CRYO_TANK.get().defaultBlockState());
        var tank = helper.getBlockEntity(tankPos, CryoTankBlockEntity.class);
        if (tank == null) {
            helper.fail("Setup failed: no cryo tank");
            return;
        }
        double accepted = tank.receiveLiquid(Cryogen.LN2, 5000.0);
        if (accepted <= 0) {
            helper.fail("Setup failed: the tank refused liquid nitrogen");
            return;
        }
        double moles = 50.0;
        for (int i = 0; i < 30 && tank.pressureKPa() < CryoVessel.BURST_KPA; i++) {
            tank.contents().addGasAt(Gas.NITROGEN, moles, Cryogen.LN2.boilingPointK());
            moles *= 1.6;
        }
        if (tank.pressureKPa() < CryoVessel.BURST_KPA) {
            helper.fail("Setup failed: could not seed the headspace to burst pressure, got "
                    + tank.pressureKPa() + " kPa");
            return;
        }

        BlockPos absoluteTankPos = helper.absolutePos(tankPos);
        for (int i = 0; i < Atmosphere.TICK_INTERVAL + 1; i++) {
            if (!helper.getBlockState(tankPos).is(ModBlocks.CRYO_TANK.get())) {
                break;
            }
            tank.serverTick(helper.getLevel(), absoluteTankPos);
        }

        if (helper.getBlockState(tankPos).is(ModBlocks.CRYO_TANK.get())) {
            helper.fail("An unvented dewar at burst pressure did not BLEVE — the tank is still"
                    + " standing at " + tank.pressureKPa() + " kPa");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I bolted an RTG to a battery. Does it actually make power, with no fuel at all?"</em>
     *
     * <p>Enters through the same door the player does (rule 13): the block's own
     * {@code serverTick}, driven directly the way every generator in this mod already is in its
     * own scenarios — not {@code RtgBlockEntity.ELECTRICAL_W} read and trusted on its own.
     */
    private static void anRtgGeneratesRealPower(GameTestHelper helper) {
        BlockPos rtgPos = new BlockPos(3, 2, 3);
        BlockPos cellPos = rtgPos.relative(Direction.NORTH);
        helper.setBlock(rtgPos, ModBlocks.RTG.get().defaultBlockState());
        helper.setBlock(cellPos, ModBlocks.POWER_CELL.get().defaultBlockState());

        var rtg = helper.getBlockEntity(rtgPos, play.xponer.astronima.block.entity.RtgBlockEntity.class);
        var cell = helper.getBlockEntity(cellPos, PowerCellBlockEntity.class);
        if (rtg == null || cell == null) {
            helper.fail("Setup failed: rtg=" + rtg + " cell=" + cell);
            return;
        }
        cell.draw(cell.storedJ());

        BlockPos absoluteRtgPos = helper.absolutePos(rtgPos);
        int ticks = 40;
        for (int i = 0; i < ticks; i++) {
            rtg.serverTick(helper.getLevel(), absoluteRtgPos);
        }

        double expected = play.xponer.astronima.block.entity.RtgBlockEntity.ELECTRICAL_W
                * (ticks / 20.0);
        double stored = cell.storedJ();
        if (stored <= 0) {
            helper.fail("An RTG bolted to an empty cell made no power at all after " + ticks
                    + " ticks");
            return;
        }
        if (stored > expected * 1.01) {
            helper.fail("The cell holds more than the RTG's own rated output could have made: "
                    + stored + " J against an expected ceiling of " + expected + " J");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"Does the same code the Geiger counter and the physiology tick both call actually see
     * real blocks — real inverse-square falloff, and a real wall stopping it completely?"</em>
     *
     * <p>Calls {@code RadiationSources.totalGammaSvPerH} directly — the one shared authority
     * (design/radiation.md §4, rule 16) both the held instrument and the per-tick hazard read,
     * so there is exactly one place this could disagree with itself. A full player-tick
     * accumulation is not staged here, the same honest gap {@code sim.tox.GasToxicity}'s own CO
     * dose already carries in this harness (design/radiation.md §5's own "deliberately left
     * uncovered" note) — what a gametest can prove, and what matters most, is that the line-of-
     * sight walk sees <em>real placed blocks</em>, not a boolean a unit test could only assert by
     * asserting its own assumption.
     */
    private static void radiationSourcesComputeRealInverseSquareAndShielding(GameTestHelper helper) {
        BlockPos rtgPos = new BlockPos(3, 2, 3);
        helper.setBlock(rtgPos, ModBlocks.RTG.get().defaultBlockState());
        // A roof over the whole sample area, so hasClearSky is false and the flare term of
        // RadiationSources contributes exactly zero regardless of the schedule's own jitter at
        // whatever game time this test happens to run at (rule 19 — the RTG's own inverse-square
        // claim must not be able to vary with something unrelated to it).
        for (int dx = 0; dx <= 4; dx++) {
            helper.setBlock(rtgPos.offset(dx, 1, 0), ModBlocks.HULL_PLATE.get().defaultBlockState());
        }
        net.minecraft.world.phys.Vec3 source = net.minecraft.world.phys.Vec3.atCenterOf(
                helper.absolutePos(rtgPos));

        net.minecraft.world.phys.Vec3 near = net.minecraft.world.phys.Vec3.atCenterOf(
                helper.absolutePos(rtgPos.offset(2, 0, 0)));
        net.minecraft.world.phys.Vec3 far = net.minecraft.world.phys.Vec3.atCenterOf(
                helper.absolutePos(rtgPos.offset(4, 0, 0)));
        double rateNear = play.xponer.astronima.atmosphere.RadiationSources.totalGammaSvPerH(
                helper.getLevel(), BlockPos.containing(near), near);
        double rateFar = play.xponer.astronima.atmosphere.RadiationSources.totalGammaSvPerH(
                helper.getLevel(), BlockPos.containing(far), far);
        if (rateNear <= 0) {
            helper.fail("No dose at all two blocks from an unshielded RTG");
            return;
        }
        // Real inverse-square: double the distance (2 -> 4 blocks from the source, so the ratio
        // of true distances is 2x) should quarter the rate, with room for the block-vs-precise
        // position rounding either sample point picks up.
        double ratio = rateNear / rateFar;
        if (ratio < 3.5 || ratio > 4.5) {
            helper.fail("Doubling the distance should roughly quarter the rate (inverse-square) —"
                    + " got a ratio of " + ratio + " (near=" + rateNear + " far=" + rateFar + ")");
            return;
        }

        // Now wall it off between the RTG and the near point - a single real placed block.
        BlockPos wallPos = rtgPos.offset(1, 0, 0);
        helper.setBlock(wallPos, ModBlocks.HULL_PLATE.get().defaultBlockState());
        double rateShielded = play.xponer.astronima.atmosphere.RadiationSources.totalGammaSvPerH(
                helper.getLevel(), BlockPos.containing(near), near);
        if (rateShielded != 0.0) {
            helper.fail("A single real wall between the RTG and the observer should fully block"
                    + " it (design/radiation.md §1.1) — got " + rateShielded + " Sv/h");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"The wreck's own suit is gone for good. I have hull plate, a circuit, some rod - can I
     * build another one?"</em>
     *
     * <p>Closes a real single point of failure found auditing for cross-system softlocks:
     * {@code astronima:eva_suit} used to exist only as a {@code CraftingTree.WorldSource} — "you
     * are already wearing it" — with no recipe at all, so a genuinely lost original (burned,
     * dropped in the void, drifted away untethered, or simply not recovered before the item
     * despawned) was permanently unrecoverable: the entire EVA half of the game, closed for the
     * rest of the save, with no verb anywhere to reopen it (PLAN.md rule 118).
     *
     * <p>Enters through the real door twice over: {@code ItemStack(ModItems.EVA_SUIT.get())} is
     * exactly what the real {@link CraftingTree.Shaped} recipe assembles into (a plain result
     * stack, no special data-component wiring anywhere in {@code ModRecipeProvider} for this
     * item) - not a shortcut around what a real craft produces - and {@link EvaSuitItem#repair}
     * is the same static verb {@code SuitRepairHandler} calls for a real player's real repair
     * click, not a hand-rolled equivalent.
     */
    private static void aSecondEvaSuitCanBeBuiltFromScratchAndStartsJustAsBroken(
            GameTestHelper helper) {
        ItemStack fresh = new ItemStack(ModItems.EVA_SUIT.get());

        if (play.xponer.astronima.sim.suit.SuitCondition.isFullyRepaired(
                play.xponer.astronima.item.EvaSuitItem.conditionOf(fresh))) {
            helper.fail("a freshly built suit came out already repaired - that would make the "
                    + "repair loop this whole tier is built around optional the moment a player "
                    + "builds their second suit");
            return;
        }
        int faultsBeforeRepair = play.xponer.astronima.sim.suit.SuitCondition.faults(
                play.xponer.astronima.item.EvaSuitItem.conditionOf(fresh)).size();
        if (faultsBeforeRepair != play.xponer.astronima.sim.suit.SuitSubsystem.values().length) {
            helper.fail("a freshly built suit should start with every one of its "
                    + play.xponer.astronima.sim.suit.SuitSubsystem.values().length
                    + " subsystems broken, the same as the wreck's own suit, but only "
                    + faultsBeforeRepair + " were broken");
            return;
        }

        // The same real verb SuitRepairHandler calls for a player's own repair click - not a
        // hand-rolled equivalent - fixing the one subsystem that actually gates pressure first.
        boolean repaired = play.xponer.astronima.item.EvaSuitItem.repair(
                fresh, play.xponer.astronima.sim.suit.SuitSubsystem.HELMET_SEAL, 1.0f);
        if (!repaired) {
            helper.fail("repairing a freshly built suit's helmet seal refused, as if it were "
                    + "already working - a second suit should need the identical real repair "
                    + "path the first one does");
            return;
        }
        if (!play.xponer.astronima.sim.suit.SuitCondition.isWorking(
                play.xponer.astronima.item.EvaSuitItem.conditionOf(fresh),
                play.xponer.astronima.sim.suit.SuitSubsystem.HELMET_SEAL)) {
            helper.fail("the real repair verb ran but the subsystem still does not read as "
                    + "working on a freshly built suit");
            return;
        }
        helper.succeed();
    }

    private PlayerScenarios() {}
}
