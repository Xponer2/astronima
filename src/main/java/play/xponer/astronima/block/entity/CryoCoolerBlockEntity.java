package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.power.CableNetworks;
import play.xponer.astronima.power.PowerRun;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.cryo.Cryogen;
import play.xponer.astronima.sim.cryo.Liquefaction;

/**
 * A Stirling cryocooler: a reversed heat engine, drawing gas from the room it stands in and
 * electricity from whatever cable network reaches it, and liquefying the one into the other
 * inside a bound {@link CryoTankBlockEntity} (design/cryogenics.md §1.1/§4).
 *
 * <p><strong>The first machine in this mod for which power is not optional.</strong> Every other
 * machine can be hand-cranked, slower — a human supplies the same kind of work a motor does.
 * Sustained refrigeration has no honest hand-cranked equivalent (design/cryogenics.md §1.1), so
 * this machine simply does not run without a cable reaching it, and says so rather than quietly
 * pretending a "faster, never better" bonus exists here.
 */
public class CryoCoolerBlockEntity extends ReadableBlockEntity {

    public enum Stall { RUNNING, NO_TANK, NO_GAS, NO_POWER }

    private Stall stall = Stall.NO_TANK;
    private double lastWatts;

    /** See {@link FuelCellBlockEntity#sinceRun} — self-owned so a hand-driven gametest can
     * actually reach this tick. */
    private int sinceRun;

    public CryoCoolerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYO_COOLER.get(), pos, state);
    }

    public Stall stall() {
        return stall;
    }

    /** What it drew on the last run, W. */
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
        double dtSeconds = Atmosphere.TICK_INTERVAL / 20.0;

        CryoTankBlockEntity tank = boundTank(level, pos);
        if (tank == null) {
            fail(Stall.NO_TANK);
            return;
        }

        Atmosphere atmosphere = Atmosphere.get(serverLevel);
        RoomState room = atmosphere.roomTouching(pos);
        if (room == null) {
            fail(Stall.NO_GAS);
            return;
        }

        Cryogen target = tank.cryogen() != null ? tank.cryogen() : mostAbundantCryogen(room);
        if (target == null || room.gases().get(target.gas()) <= 0) {
            fail(Stall.NO_GAS);
            return;
        }

        double delivered = draw(serverLevel, pos, Liquefaction.RATED_ELECTRICAL_W * dtSeconds, dtSeconds);
        if (delivered <= 0) {
            fail(Stall.NO_POWER);
            return;
        }

        double available = room.gases().get(target.gas());
        double moles = Math.min(available,
                Liquefaction.molesLiquefiedFor(target, room.temperatureK(), delivered));
        double accepted = tank.receiveLiquid(target, moles);
        if (accepted > 0) {
            room.removeGas(target.gas(), accepted);
        }

        // Every watt drawn becomes heat somewhere (design/power.md's own law): the gas's own
        // heat content is not separately tracked room thermal mass in this model (T1 — the
        // shell, not the gas, is the room's heat capacity), so the cooler's net effect on its
        // room is exactly its electrical draw, the same "waste heat into the room" every other
        // machine already deposits.
        atmosphere.addHeatJoules(pos, delivered);

        stall = Stall.RUNNING;
        lastWatts = delivered / dtSeconds;
        setChanged();
    }

    private void fail(Stall reason) {
        stall = reason;
        lastWatts = 0;
        setChanged();
    }

    /** The one {@link CryoTankBlockEntity} bolted to this block — the same "vessels touching it"
     * convention {@code design/power.md} §P9 already uses for the fuel cell. */
    private static CryoTankBlockEntity boundTank(Level level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            if (level.getBlockEntity(pos.relative(side)) instanceof CryoTankBlockEntity tank) {
                return tank;
            }
        }
        return null;
    }

    /** When the tank has not committed to a species yet, liquefy whichever cryogen-capable gas
     * is most abundant in the room — a harmless bootstrap (there is nothing to get wrong about
     * intent here, unlike rule 17's multi-device commissioning) rather than refusing to ever
     * start. */
    private static Cryogen mostAbundantCryogen(RoomState room) {
        Cryogen best = null;
        double bestMoles = 0;
        for (Cryogen cryogen : Cryogen.values()) {
            double moles = room.gases().get(cryogen.gas());
            if (moles > bestMoles) {
                bestMoles = moles;
                best = cryogen;
            }
        }
        return best;
    }

    private double draw(ServerLevel level, BlockPos pos, double wantedJoules, double dtSeconds) {
        CableNetworks.Resolved run = CableNetworks.resolveFrom(level, pos);
        if (run == null || run.isEmpty()) {
            return 0;
        }
        double taken = 0;
        for (PowerCellBlockEntity cell : run.cells()) {
            taken += cell.draw(wantedJoules - taken);
            if (taken >= wantedJoules) {
                break;
            }
        }
        if (taken <= 0) {
            return 0;
        }
        return PowerRun.sendAlong(level, run, taken, dtSeconds);
    }
}
