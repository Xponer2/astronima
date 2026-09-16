package play.xponer.astronima.client.indicator;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.BulkheadDoorBlock;
import play.xponer.astronima.block.GasPumpBlock;
import play.xponer.astronima.block.GasValveBlock;
import play.xponer.astronima.block.entity.AirlockControllerBlockEntity;
import play.xponer.astronima.block.entity.DehumidifierBlockEntity;
import play.xponer.astronima.block.entity.GasPumpBlockEntity;
import play.xponer.astronima.block.entity.GasTankBlockEntity;
import play.xponer.astronima.block.entity.GasValveBlockEntity;
import play.xponer.astronima.block.entity.ProcessingBlockEntity;
import play.xponer.astronima.block.entity.ReadableBlockEntity;
import play.xponer.astronima.block.entity.CombustionGeneratorBlockEntity;
import play.xponer.astronima.block.entity.FuelCellBlockEntity;
import play.xponer.astronima.block.entity.PowerCellBlockEntity;
import play.xponer.astronima.block.entity.SolarArrayBlockEntity;
import play.xponer.astronima.block.OxygenCandleBlock;
import play.xponer.astronima.block.entity.OxygenCandleBlockEntity;
import play.xponer.astronima.block.entity.ScrubberBlockEntity;
import play.xponer.astronima.block.entity.SolarRetortBlockEntity;

/**
 * What any readable block currently says — the single place that decides.
 *
 * <p>Both instruments ask this: the lamp on the block and the readout by the crosshair.
 * They are two views of one answer rather than two answers, which is the failure
 * {@code design/indicators.md} §0 names — an indicator that disagrees with the panel is
 * worse than no indicator, because it is trusted.
 *
 * <p>The dispatch is a switch on the block entity rather than an interface on it because
 * these readings are a <em>client</em> concern: what a gauge says is presentation, and
 * putting {@code Reading} on the server-side block entity would drag the whole indicator
 * vocabulary into the simulation for nothing.
 */
public final class BlockReadings {

    /**
     * <strong>What the player is shown</strong>: the reading the server published, and only that.
     *
     * <p>{@link #of} is the <em>computation</em>, and it runs on the server. This is the
     * <em>delivery</em>, and it is what every drawing site asks — because a block entity's fields
     * do not cross to the client, and for the whole of this mod's life the two indicators were
     * computing honest readings from default values. A solar array in full daylight said
     * {@code no sun}. See {@code design/indicators.md} §7 and rule 26.
     *
     * <p><strong>No fallback to {@link #of}</strong>, deliberately. A fallback is exactly how a
     * client-side guess gets drawn as a measurement, and it would make every guard on this path
     * pass for ever.
     */
    public static @Nullable Reading shown(BlockEntity blockEntity) {
        return blockEntity instanceof ReadableBlockEntity readable ? readable.reading() : null;
    }

    /**
     * The reading for this block, computed — or null when it is not something that reads.
     *
     * <p>The server's half. Drawing code wants {@link #shown} instead.
     */
    public static @Nullable Reading of(BlockEntity blockEntity) {
        return switch (blockEntity) {
            case GasTankBlockEntity tank -> PlumbingReadings.tank(tank.pressureKPa());
            case GasPumpBlockEntity pump -> PlumbingReadings.pump(
                    pump.getBlockState().getValue(GasPumpBlock.RUNNING), pump.isRouted());
            case GasValveBlockEntity valve -> PlumbingReadings.valve(
                    valve.getBlockState().getValue(GasValveBlock.SETTING));
            case AirlockControllerBlockEntity airlock -> airlockReading(airlock);
            case SolarRetortBlockEntity retort -> PlumbingReadings.retort(
                    retort.sunlight(), retort.temperatureK(), retort.process());
            case OxygenCandleBlockEntity candle -> PlumbingReadings.candle(
                    candle.getBlockState().getValue(OxygenCandleBlock.LIT),
                    candle.getBlockState().getValue(OxygenCandleBlock.SPENT),
                    candle.burnFraction());
            case FuelCellBlockEntity cell -> PlumbingReadings.fuelCell(
                    cell.stall(), cell.watts());
            case CombustionGeneratorBlockEntity generator -> PlumbingReadings.generator(
                    generator.stall(), generator.watts());
            case SolarArrayBlockEntity array -> PlumbingReadings.solarArray(
                    array.sunlight(), array.watts());
            case PowerCellBlockEntity cell -> PlumbingReadings.powerCell(
                    cell.charge(), cell.storedJ());
            case DehumidifierBlockEntity condenser -> PlumbingReadings.condenser(
                    condenser.bottlesReady(), condenser.bottleProgress());
            case ScrubberBlockEntity scrubber -> PlumbingReadings.scrubber(
                    scrubber.chargeFraction());
            case play.xponer.astronima.block.entity.CleanroomControllerBlockEntity controller ->
                    PlumbingReadings.cleanroom(controller.cleanliness(), controller.chargeFraction());
            case play.xponer.astronima.block.entity.ParaffinThermalMassBlockEntity mass ->
                    PlumbingReadings.paraffinThermalMass(mass.meltFraction());
            case play.xponer.astronima.block.entity.DeconStationBlockEntity booth ->
                    PlumbingReadings.deconStation(booth.isRunning(), booth.isRinsing(),
                            booth.decadesThisCycle(),
                            booth.water() / play.xponer.astronima.block.entity
                                    .DeconStationBlockEntity.TANK_LITRES);
            case ProcessingBlockEntity machine -> PlumbingReadings.machine(
                    machine.workState(), machine.progress());
            case play.xponer.astronima.block.entity.AstraAltarBlockEntity altar -> altarReading(altar);
            case play.xponer.astronima.block.entity.StorageDriveBlockEntity drive -> {
                int usable = drive.usableSlots();
                int connected = usable
                        - play.xponer.astronima.block.entity.StorageDriveBlockEntity.FREE_SLOTS;
                yield PlumbingReadings.storageDrive(usable,
                        play.xponer.astronima.block.entity.StorageDriveBlockEntity.MAX_CELL_SLOTS,
                        connected);
            }
            default -> null;
        };
    }

    /** design/astra-ritual-grammar.md's own gauge: how far through the required duration a
     *  running ritual is, or the honest terminal states either side of it. */
    private static Reading altarReading(play.xponer.astronima.block.entity.AstraAltarBlockEntity altar) {
        double requiredSeconds = play.xponer.astronima.sim.astra.AstraRitual.requiredDurationSeconds();
        double fraction = requiredSeconds > 0.0 ? altar.elapsedSeconds() / requiredSeconds : 0.0;
        return switch (altar.phase()) {
            case IDLE -> new Reading(0.0, Reading.Band.IDLE, "idle");
            case RUNNING -> new Reading(fraction, Reading.Band.NOMINAL,
                    (int) altar.elapsedSeconds() + "/" + (int) requiredSeconds + "s");
            case COMPLETED -> new Reading(1.0, Reading.Band.NOMINAL, "complete");
            case STALLED -> new Reading(fraction, Reading.Band.CRITICAL, "stalled");
        };
    }

    /**
     * The reading for a block that carries its state in the blockstate and has no block
     * entity to hang a lamp on, or null when it is not something that reads.
     *
     * <p>Only the crosshair readout asks this. A bulkhead door cannot have a block entity —
     * a habitat has dozens of them and they would all tick — so the interlock latch is
     * drawn on the door's own texture, and this supplies the words for it. Same authority
     * as everything else, so the door and the airlock panel cannot disagree about whether
     * it is held.
     */
    public static @Nullable Reading ofState(BlockState state) {
        if (state.getBlock() instanceof BulkheadDoorBlock) {
            return PlumbingReadings.bulkheadDoor(
                    state.getValue(BulkheadDoorBlock.LOCKED),
                    state.getValue(BlockStateProperties.OPEN));
        }
        return null;
    }

    /**
     * The controller's gauge names the first terminal that is wrong, so the lamp on the
     * wall says <em>which device</em> to go and look at rather than that "something" is.
     */
    private static Reading airlockReading(AirlockControllerBlockEntity airlock) {
        var status = airlock.status();
        var role = status.firstFault();
        return PlumbingReadings.airlock(airlock.phase(), airlock.chamberFault(),
                airlock.cycleFault(),
                role == null ? play.xponer.astronima.sim.airlock.DeviceBinding.Role.PUMP : role,
                role == null
                        ? play.xponer.astronima.sim.airlock.DeviceBinding.Problem.NONE
                        : status.problem(role),
                airlock.chamberPressureKPa(), airlock.habitatPressureKPa());
    }

    private BlockReadings() {}
}
