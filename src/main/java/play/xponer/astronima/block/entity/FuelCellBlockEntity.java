package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.power.CableNetworks;
import play.xponer.astronima.power.PowerRun;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.power.FuelCell;

import java.util.ArrayList;
import java.util.List;

/**
 * The clean generator: hydrogen and oxygen in, electricity and water out.
 *
 * <p><strong>It draws from vessels, not from the room</strong>, and that is real rather than a
 * restriction. A fuel cell needs pure hydrogen and pure oxygen at its electrodes; a trace of
 * hydrogen mixed into cabin air is nothing it can use. So its fuel has to be pumped into a tank
 * and the tank bolted on — which is a third purpose for the pump, the port and the vessel, and
 * the reason this machine feels like plant rather than like a furnace.
 *
 * <p>Its exhaust goes the other way: <strong>water vapour into the room</strong>, where the
 * dehumidifier already bottles it. The engine hands you carbon dioxide for a finite cartridge to
 * absorb; this hands you something you wanted.
 *
 * <p><strong>Running it backwards is not here.</strong> A regenerative cell would electrolyse
 * water into hydrogen and oxygen, which is v0.6's electrolyzer and the payoff
 * {@code design/oxygen.md} §1 explicitly gates behind it. Shipping it now would steal the
 * ending of a tier that was built to earn it.
 */
public class FuelCellBlockEntity extends ReadableBlockEntity {

    private FuelCell.Stall stall = FuelCell.Stall.NO_HYDROGEN;
    private double lastWatts;

    /**
     * Ticks since the last run, counted here rather than off the world clock.
     *
     * <p>A gate of the form {@code getGameTime() % INTERVAL} cannot be satisfied by any driver
     * that does not advance the clock, so every hand-driven scenario silently does nothing and
     * passes. The purge valve and the generator each taught that once.
     */
    private int sinceRun;

    public FuelCellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FUEL_CELL.get(), pos, state);
    }

    /** Which gas it is short of, or that it is running — different journeys, so it must say. */
    public FuelCell.Stall stall() {
        return stall;
    }

    /** What it made on the last run, W. */
    public double watts() {
        return lastWatts;
    }

    public void serverTick(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (++sinceRun < Atmosphere.TICK_INTERVAL) {
            return;
        }
        sinceRun = 0;

        List<GasTankBlockEntity> vessels = vesselsTouching(level, pos);
        double hydrogen = total(vessels, Gas.HYDROGEN);
        double oxygen = total(vessels, Gas.OXYGEN);
        stall = FuelCell.diagnose(hydrogen, oxygen);

        double seconds = Atmosphere.TICK_INTERVAL / 20.0;
        FuelCell.Run run = FuelCell.run(hydrogen, oxygen, seconds);
        if (!run.ran()) {
            lastWatts = 0;
            setChanged();
            return;
        }
        drawFrom(vessels, Gas.HYDROGEN, run.fuelMol());
        drawFrom(vessels, Gas.OXYGEN, run.oxygenMol());

        // Water into the room the cell stands in, at the room's own temperature: the heat is
        // accounted separately and in full, so putting the exhaust in hot as well would warm
        // the habitat by more than the hydrogen contained.
        Atmosphere atmosphere = Atmosphere.get(serverLevel);
        RoomState room = atmosphere.roomTouching(pos);
        if (room != null) {
            room.addGasAt(Gas.WATER_VAPOR, run.waterMol(), room.temperatureK());
        }
        atmosphere.addHeatJoules(pos, run.heatJ());

        lastWatts = run.electricalJ() / seconds;
        deliver(serverLevel, pos, run.electricalJ());
        setChanged();
    }

    /** Every vessel bolted to this block — a cell has two feeds and they may be two tanks. */
    private static List<GasTankBlockEntity> vesselsTouching(Level level, BlockPos pos) {
        List<GasTankBlockEntity> found = new ArrayList<>();
        for (Direction side : Direction.values()) {
            if (level.getBlockEntity(pos.relative(side)) instanceof GasTankBlockEntity tank) {
                found.add(tank);
            }
        }
        return found;
    }

    private static double total(List<GasTankBlockEntity> vessels, Gas gas) {
        double sum = 0;
        for (GasTankBlockEntity vessel : vessels) {
            sum += vessel.contents().gases().get(gas);
        }
        return sum;
    }

    /** Takes {@code wanted} of a gas across the vessels, in whatever order they are found. */
    private static void drawFrom(List<GasTankBlockEntity> vessels, Gas gas, double wanted) {
        double left = wanted;
        for (GasTankBlockEntity vessel : vessels) {
            if (left <= 0) {
                return;
            }
            left -= vessel.contents().removeGas(gas, left);
            vessel.setChanged();
        }
    }

    private void deliver(ServerLevel level, BlockPos pos, double joules) {
        CableNetworks.Resolved run = CableNetworks.resolveFrom(level, pos);
        if (run == null || run.isEmpty()) {
            return;
        }
        double each = PowerRun.sendAlong(level, run, joules, Atmosphere.TICK_INTERVAL / 20.0)
                / run.cells().size();
        for (PowerCellBlockEntity cell : run.cells()) {
            cell.charge(each);
        }
    }
}
