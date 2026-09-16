package play.xponer.astronima.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.block.entity.AlarmBlockEntity;
import play.xponer.astronima.block.entity.DehumidifierBlockEntity;
import play.xponer.astronima.block.entity.OxygenCandleBlockEntity;
import play.xponer.astronima.block.entity.ColdForgeBlockEntity;
import play.xponer.astronima.block.entity.CargoCrateBlockEntity;
import play.xponer.astronima.block.entity.MagneticSeparatorBlockEntity;
import play.xponer.astronima.block.entity.WinnowingTableBlockEntity;
import play.xponer.astronima.block.entity.OreCrusherBlockEntity;
import play.xponer.astronima.block.entity.SolarRetortBlockEntity;
import play.xponer.astronima.block.entity.GasPortBlockEntity;
import play.xponer.astronima.block.entity.GasPumpBlockEntity;
import play.xponer.astronima.block.entity.GasTankBlockEntity;
import play.xponer.astronima.block.entity.AirlockControllerBlockEntity;
import play.xponer.astronima.block.entity.GasValveBlockEntity;
import play.xponer.astronima.block.entity.CarbonylRefinerBlockEntity;
import play.xponer.astronima.block.entity.FluidizedBedBlockEntity;
import play.xponer.astronima.block.entity.CombustionGeneratorBlockEntity;
import play.xponer.astronima.block.entity.FuelCellBlockEntity;
import play.xponer.astronima.block.entity.PowerCellBlockEntity;
import play.xponer.astronima.block.entity.PurgeValveBlockEntity;
import play.xponer.astronima.block.entity.SolarArrayBlockEntity;
import play.xponer.astronima.block.entity.MoldBlockEntity;
import play.xponer.astronima.block.entity.ScrubberBlockEntity;
import play.xponer.astronima.block.entity.ElectrolysisCellBlockEntity;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Astronima.MODID);

    /** Exists to remember a telescope's last aim across a rider leaving (design/astra-telescope.md
     * §9 open question 4, answered by direct request) — not to host a menu or any per-tick logic. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<play.xponer.astronima.block.entity.TelescopeBlockEntity>> TELESCOPE =
            BLOCK_ENTITIES.register("telescope", () ->
                    new BlockEntityType<>(play.xponer.astronima.block.entity.TelescopeBlockEntity::new,
                            ModBlocks.TELESCOPE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OxygenCandleBlockEntity>> OXYGEN_CANDLE =
            BLOCK_ENTITIES.register("oxygen_candle", () ->
                    new BlockEntityType<>(OxygenCandleBlockEntity::new, ModBlocks.OXYGEN_CANDLE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GasPumpBlockEntity>> GAS_PUMP =
            BLOCK_ENTITIES.register("gas_pump", () ->
                    new BlockEntityType<>(GasPumpBlockEntity::new, ModBlocks.GAS_PUMP.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GasTankBlockEntity>> GAS_TANK =
            BLOCK_ENTITIES.register("gas_tank", () ->
                    new BlockEntityType<>(GasTankBlockEntity::new, ModBlocks.GAS_TANK.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GasPortBlockEntity>> GAS_PORT =
            BLOCK_ENTITIES.register("gas_port", () ->
                    new BlockEntityType<>(GasPortBlockEntity::new, ModBlocks.GAS_PORT.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GasValveBlockEntity>> GAS_VALVE =
            BLOCK_ENTITIES.register("gas_valve", () ->
                    new BlockEntityType<>(GasValveBlockEntity::new, ModBlocks.GAS_VALVE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirlockControllerBlockEntity>>
            AIRLOCK_CONTROLLER = BLOCK_ENTITIES.register("airlock_controller", () ->
                    new BlockEntityType<>(AirlockControllerBlockEntity::new,
                            ModBlocks.AIRLOCK_CONTROLLER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScrubberBlockEntity>> SCRUBBER =
            BLOCK_ENTITIES.register("scrubber", () ->
                    new BlockEntityType<>(ScrubberBlockEntity::new, ModBlocks.SCRUBBER.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.CleanroomControllerBlockEntity>>
            CLEANROOM_CONTROLLER = BLOCK_ENTITIES.register("cleanroom_controller", () ->
                    new BlockEntityType<>(
                            play.xponer.astronima.block.entity.CleanroomControllerBlockEntity::new,
                            ModBlocks.CLEANROOM_CONTROLLER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarArrayBlockEntity>> SOLAR_ARRAY =
            BLOCK_ENTITIES.register("solar_array", () ->
                    new BlockEntityType<>(SolarArrayBlockEntity::new, ModBlocks.SOLAR_ARRAY.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.AstraAltarBlockEntity>> ASTRA_ALTAR =
            BLOCK_ENTITIES.register("astra_altar", () ->
                    new BlockEntityType<>(play.xponer.astronima.block.entity.AstraAltarBlockEntity::new,
                            ModBlocks.ASTRA_ALTAR.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<CarbonylRefinerBlockEntity>> CARBONYL_REFINER =
            BLOCK_ENTITIES.register("carbonyl_refiner", () ->
                    new BlockEntityType<>(CarbonylRefinerBlockEntity::new,
                            ModBlocks.CARBONYL_REFINER.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<FluidizedBedBlockEntity>> FLUIDIZED_BED =
            BLOCK_ENTITIES.register("fluidized_bed", () ->
                    new BlockEntityType<>(FluidizedBedBlockEntity::new,
                            ModBlocks.FLUIDIZED_BED.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<ElectrolysisCellBlockEntity>> ELECTROLYSIS_CELL =
            BLOCK_ENTITIES.register("electrolysis_cell", () ->
                    new BlockEntityType<>(ElectrolysisCellBlockEntity::new,
                            ModBlocks.ELECTROLYSIS_CELL.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.SlsPrinterBlockEntity>> SLS_PRINTER =
            BLOCK_ENTITIES.register("sls_printer", () ->
                    new BlockEntityType<>(play.xponer.astronima.block.entity.SlsPrinterBlockEntity::new,
                            ModBlocks.SLS_PRINTER.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.CrackingTowerBlockEntity>> CRACKING_TOWER =
            BLOCK_ENTITIES.register("cracking_tower", () ->
                    new BlockEntityType<>(play.xponer.astronima.block.entity.CrackingTowerBlockEntity::new,
                            ModBlocks.CRACKING_TOWER.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.PolymerizerBlockEntity>> POLYMERIZER =
            BLOCK_ENTITIES.register("polymerizer", () ->
                    new BlockEntityType<>(play.xponer.astronima.block.entity.PolymerizerBlockEntity::new,
                            ModBlocks.POLYMERIZER.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.WaterElectrolyzerBlockEntity>>
            WATER_ELECTROLYZER = BLOCK_ENTITIES.register("water_electrolyzer", () ->
                    new BlockEntityType<>(
                            play.xponer.astronima.block.entity.WaterElectrolyzerBlockEntity::new,
                            ModBlocks.WATER_ELECTROLYZER.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.SabatierReactorBlockEntity>>
            SABATIER_REACTOR = BLOCK_ENTITIES.register("sabatier_reactor", () ->
                    new BlockEntityType<>(
                            play.xponer.astronima.block.entity.SabatierReactorBlockEntity::new,
                            ModBlocks.SABATIER_REACTOR.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.BoschReactorBlockEntity>>
            BOSCH_REACTOR = BLOCK_ENTITIES.register("bosch_reactor", () ->
                    new BlockEntityType<>(play.xponer.astronima.block.entity.BoschReactorBlockEntity::new,
                            ModBlocks.BOSCH_REACTOR.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.TroiliteRoasterBlockEntity>>
            TROILITE_ROASTER = BLOCK_ENTITIES.register("troilite_roaster", () ->
                    new BlockEntityType<>(
                            play.xponer.astronima.block.entity.TroiliteRoasterBlockEntity::new,
                            ModBlocks.TROILITE_ROASTER.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.HeavyWaterCellBlockEntity>>
            HEAVY_WATER_CELL = BLOCK_ENTITIES.register("heavy_water_cell", () ->
                    new BlockEntityType<>(
                            play.xponer.astronima.block.entity.HeavyWaterCellBlockEntity::new,
                            ModBlocks.HEAVY_WATER_CELL.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.TitaniumCellBlockEntity>>
            TITANIUM_CELL = BLOCK_ENTITIES.register("titanium_cell", () ->
                    new BlockEntityType<>(
                            play.xponer.astronima.block.entity.TitaniumCellBlockEntity::new,
                            ModBlocks.TITANIUM_CELL.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.InductionFurnaceBlockEntity>>
            INDUCTION_FURNACE = BLOCK_ENTITIES.register("induction_furnace", () ->
                    new BlockEntityType<>(
                            play.xponer.astronima.block.entity.InductionFurnaceBlockEntity::new,
                            ModBlocks.INDUCTION_FURNACE.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.IronSmelterBlockEntity>>
            IRON_SMELTER = BLOCK_ENTITIES.register("iron_smelter", () ->
                    new BlockEntityType<>(
                            play.xponer.astronima.block.entity.IronSmelterBlockEntity::new,
                            ModBlocks.IRON_SMELTER.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.SulfuricAcidPlantBlockEntity>>
            SULFURIC_ACID_PLANT = BLOCK_ENTITIES.register("sulfuric_acid_plant", () ->
                    new BlockEntityType<>(
                            play.xponer.astronima.block.entity.SulfuricAcidPlantBlockEntity::new,
                            ModBlocks.SULFURIC_ACID_PLANT.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.AmmoniaHeatPipeBlockEntity>>
            AMMONIA_HEAT_PIPE = BLOCK_ENTITIES.register("ammonia_heat_pipe", () ->
                    new BlockEntityType<>(
                            play.xponer.astronima.block.entity.AmmoniaHeatPipeBlockEntity::new,
                            ModBlocks.AMMONIA_HEAT_PIPE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FuelCellBlockEntity>> FUEL_CELL =
            BLOCK_ENTITIES.register("fuel_cell", () ->
                    new BlockEntityType<>(FuelCellBlockEntity::new, ModBlocks.FUEL_CELL.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.ParaffinThermalMassBlockEntity>>
            PARAFFIN_THERMAL_MASS = BLOCK_ENTITIES.register("paraffin_thermal_mass", () ->
                    new BlockEntityType<>(
                            play.xponer.astronima.block.entity.ParaffinThermalMassBlockEntity::new,
                            ModBlocks.PARAFFIN_THERMAL_MASS.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<CombustionGeneratorBlockEntity>> COMBUSTION_GENERATOR =
            BLOCK_ENTITIES.register("combustion_generator", () ->
                    new BlockEntityType<>(CombustionGeneratorBlockEntity::new,
                            ModBlocks.COMBUSTION_GENERATOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerCellBlockEntity>> POWER_CELL =
            BLOCK_ENTITIES.register("power_cell", () ->
                    new BlockEntityType<>(PowerCellBlockEntity::new, ModBlocks.POWER_CELL.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PurgeValveBlockEntity>> PURGE_VALVE =
            BLOCK_ENTITIES.register("purge_valve", () ->
                    new BlockEntityType<>(PurgeValveBlockEntity::new, ModBlocks.PURGE_VALVE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DehumidifierBlockEntity>> DEHUMIDIFIER =
            BLOCK_ENTITIES.register("dehumidifier", () ->
                    new BlockEntityType<>(DehumidifierBlockEntity::new, ModBlocks.DEHUMIDIFIER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AlarmBlockEntity>> ALARM =
            BLOCK_ENTITIES.register("alarm", () ->
                    new BlockEntityType<>(AlarmBlockEntity::new, ModBlocks.ALARM.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OreCrusherBlockEntity>> ORE_CRUSHER =
            BLOCK_ENTITIES.register("ore_crusher", () ->
                    new BlockEntityType<>(OreCrusherBlockEntity::new, ModBlocks.ORE_CRUSHER.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.SynthesiserBlockEntity>>
            SYNTHESISER = BLOCK_ENTITIES.register("synthesiser", () -> new BlockEntityType<>(
                    play.xponer.astronima.block.entity.SynthesiserBlockEntity::new,
                    ModBlocks.SYNTHESISER.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.MicroscopeBlockEntity>> MICROSCOPE =
            BLOCK_ENTITIES.register("microscope", () -> new BlockEntityType<>(
                    play.xponer.astronima.block.entity.MicroscopeBlockEntity::new,
                    ModBlocks.MICROSCOPE.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.IncubatorBlockEntity>> INCUBATOR =
            BLOCK_ENTITIES.register("incubator", () -> new BlockEntityType<>(
                    play.xponer.astronima.block.entity.IncubatorBlockEntity::new,
                    ModBlocks.INCUBATOR.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.DeconStationBlockEntity>>
            DECON_STATION = BLOCK_ENTITIES.register("decon_station", () -> new BlockEntityType<>(
                    play.xponer.astronima.block.entity.DeconStationBlockEntity::new,
                    ModBlocks.DECON_STATION.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarRetortBlockEntity>> SOLAR_RETORT =
            BLOCK_ENTITIES.register("solar_retort", () ->
                    new BlockEntityType<>(SolarRetortBlockEntity::new, ModBlocks.SOLAR_RETORT.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MagneticSeparatorBlockEntity>>
            MAGNETIC_SEPARATOR = BLOCK_ENTITIES.register("magnetic_separator", () ->
                    new BlockEntityType<>(MagneticSeparatorBlockEntity::new,
                            ModBlocks.MAGNETIC_SEPARATOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WinnowingTableBlockEntity>>
            WINNOWING_TABLE = BLOCK_ENTITIES.register("winnowing_table", () ->
                    new BlockEntityType<>(WinnowingTableBlockEntity::new,
                            ModBlocks.WINNOWING_TABLE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CargoCrateBlockEntity>>
            CARGO_CRATE = BLOCK_ENTITIES.register("cargo_crate", () ->
                    new BlockEntityType<>(CargoCrateBlockEntity::new,
                            ModBlocks.CARGO_CRATE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ColdForgeBlockEntity>>
            COLD_FORGE = BLOCK_ENTITIES.register("cold_forge", () ->
                    new BlockEntityType<>(ColdForgeBlockEntity::new, ModBlocks.COLD_FORGE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MoldBlockEntity>> MOLD =
            BLOCK_ENTITIES.register("mold", () ->
                    new BlockEntityType<>(MoldBlockEntity::new, ModBlocks.MOLD.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.CryoTankBlockEntity>> CRYO_TANK =
            BLOCK_ENTITIES.register("cryo_tank", () ->
                    new BlockEntityType<>(play.xponer.astronima.block.entity.CryoTankBlockEntity::new,
                            ModBlocks.CRYO_TANK.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.CryoCoolerBlockEntity>> CRYO_COOLER =
            BLOCK_ENTITIES.register("cryo_cooler", () ->
                    new BlockEntityType<>(play.xponer.astronima.block.entity.CryoCoolerBlockEntity::new,
                            ModBlocks.CRYO_COOLER.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.FreezeDryerBlockEntity>> FREEZE_DRYER =
            BLOCK_ENTITIES.register("freeze_dryer", () ->
                    new BlockEntityType<>(play.xponer.astronima.block.entity.FreezeDryerBlockEntity::new,
                            ModBlocks.FREEZE_DRYER.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.RtgBlockEntity>> RTG =
            BLOCK_ENTITIES.register("rtg", () ->
                    new BlockEntityType<>(play.xponer.astronima.block.entity.RtgBlockEntity::new,
                            ModBlocks.RTG.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.DownsCellBlockEntity>>
            DOWNS_CELL = BLOCK_ENTITIES.register("downs_cell", () ->
                    new BlockEntityType<>(
                            play.xponer.astronima.block.entity.DownsCellBlockEntity::new,
                            ModBlocks.DOWNS_CELL.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.ZoneRefinerBlockEntity>>
            ZONE_REFINER = BLOCK_ENTITIES.register("zone_refiner", () ->
                    new BlockEntityType<>(
                            play.xponer.astronima.block.entity.ZoneRefinerBlockEntity::new,
                            ModBlocks.ZONE_REFINER.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.HfDigesterBlockEntity>>
            HF_DIGESTER = BLOCK_ENTITIES.register("hf_digester", () ->
                    new BlockEntityType<>(
                            play.xponer.astronima.block.entity.HfDigesterBlockEntity::new,
                            ModBlocks.HF_DIGESTER.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.EtchStationBlockEntity>>
            ETCH_STATION = BLOCK_ENTITIES.register("etch_station", () ->
                    new BlockEntityType<>(
                            play.xponer.astronima.block.entity.EtchStationBlockEntity::new,
                            ModBlocks.ETCH_STATION.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.StorageDriveBlockEntity>>
            STORAGE_DRIVE = BLOCK_ENTITIES.register("storage_drive", () ->
                    new BlockEntityType<>(
                            play.xponer.astronima.block.entity.StorageDriveBlockEntity::new,
                            ModBlocks.STORAGE_DRIVE.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<play.xponer.astronima.block.entity.StorageTerminalBlockEntity>>
            STORAGE_TERMINAL = BLOCK_ENTITIES.register("storage_terminal", () ->
                    new BlockEntityType<>(
                            play.xponer.astronima.block.entity.StorageTerminalBlockEntity::new,
                            ModBlocks.STORAGE_TERMINAL.get()));

    private ModBlockEntities() {}
}
