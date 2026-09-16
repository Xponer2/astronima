package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import play.xponer.astronima.sim.power.CombustionEngine;
import play.xponer.astronima.sim.power.Fouling;

/**
 * A generator that breathes the room it is in.
 *
 * <p>Solar makes nothing for half the day and a cell holds fifteen minutes; this is what fills
 * the gap, and it fills it at a price the rest of the game has already made expensive. It takes
 * <strong>your oxygen</strong> and hands back <strong>carbon dioxide</strong> for the scrubber
 * to eat — and three quarters of the fuel's energy comes out as heat, which at full output is
 * 750 W into the room. A generator is a stove that also makes power.
 *
 * <p><strong>Breathing the room is deliberate</strong>, and it is what makes the plumbing tier
 * matter again. The fuel is methane, which arrives in this game as a hazard — a gas pocket
 * breached while mining. Getting it from there to here is what pipes, ports, pumps and tanks
 * are for, so an existing system gets a second purpose rather than a new one being invented.
 */
public class CombustionGeneratorBlockEntity extends ReadableBlockEntity {

    /** What the last tick managed, for the block's readout. */
    private CombustionEngine.Stall stall = CombustionEngine.Stall.NO_FUEL;
    private double lastWatts;

    /**
     * Ticks since the last burn.
     *
     * <p>Counted here rather than read off the world clock, and that is a testability decision
     * with teeth: a gate of the form {@code getGameTime() % INTERVAL != 0} cannot be satisfied
     * by any driver that does not advance the clock, so every scenario driving this block by
     * hand silently did nothing. The purge valve taught that once already. A counter of its own
     * behaves identically in the world and is drivable outside it.
     */
    private int sinceBurn;

    /**
     * Soot and sulfur deposited so far, in the same "moles of residue" the model counts in.
     *
     * <p>Sized so a well-fed engine on clean gas runs for hours and a starved one on sour gas
     * needs digging out within a session — the whole point being that the interval is set by
     * how the player chose to feed it rather than by a clock.
     */
    private double sludge;

    /** What it holds before it stops entirely. */
    public static final double SLUDGE_CAPACITY = 12.0;

    public CombustionGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COMBUSTION_GENERATOR.get(), pos, state);
    }

    /** Why it is not running, or that it is — the difference the player has to act on. */
    public CombustionEngine.Stall stall() {
        return stall;
    }

    /** What it made on the last run, W. */
    public double watts() {
        return lastWatts;
    }

    /** How choked it is, 0..1 — what the block draws and what a shovel empties. */
    public float fouling() {
        return (float) Math.clamp(sludge / SLUDGE_CAPACITY, 0.0, 1.0);
    }

    /**
     * Digs it out, returning how many whole units of sludge came free.
     *
     * <p>Rounded down to whole items, and the remainder is left in the machine rather than
     * rounded away: a player who scrapes a barely-dirty engine should get nothing and lose
     * nothing, not launder a fraction into a free item.
     */
    public int shovelOut() {
        int units = (int) Math.floor(sludge);
        if (units > 0) {
            sludge -= units;
            setChanged();
        }
        return units;
    }

    /**
     * Burns a tick's worth, on the atmosphere's cadence.
     *
     * <p>Everything the burn produced goes somewhere: the electricity into the run, the exhaust
     * into the room, and the heat into the room as well. <strong>Nothing is dropped</strong> —
     * an engine that made power and forgot its carbon dioxide would be a fuel-to-watts converter
     * with a chemistry-flavoured name.
     */
    public void serverTick(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (++sinceBurn < Atmosphere.TICK_INTERVAL) {
            return;
        }
        sinceBurn = 0;
        Atmosphere atmosphere = Atmosphere.get(serverLevel);
        RoomState room = atmosphere.roomTouching(pos);
        if (room == null) {
            stall = CombustionEngine.Stall.NO_AIR;
            lastWatts = 0;
            setChanged();
            return;
        }
        double fuel = room.gases().get(Gas.METHANE);
        double oxygen = room.gases().get(Gas.OXYGEN);
        if (Fouling.jammed(sludge, SLUDGE_CAPACITY)) {
            stall = CombustionEngine.Stall.FOULED;
            lastWatts = 0;
            setChanged();
            return;
        }
        stall = CombustionEngine.diagnose(fuel, oxygen);

        double seconds = Atmosphere.TICK_INTERVAL / 20.0;
        CombustionEngine.Burn burn = CombustionEngine.burn(fuel, oxygen, seconds);
        if (!burn.ran()) {
            lastWatts = 0;
            setChanged();
            return;
        }

        // Everything sulfurous in the room reaches the burner with the fuel, which is why
        // which pocket you tapped is a decision the engine remembers.
        double sulfur = room.gases().get(Gas.HYDROGEN_SULFIDE)
                + room.gases().get(Gas.SULFUR_DIOXIDE);
        sludge += Fouling.sludgeFrom(burn.fuelMol(),
                Fouling.richness(oxygen, fuel, CombustionEngine.O2_PER_FUEL),
                Fouling.sourness(sulfur, fuel));

        room.removeGas(Gas.METHANE, burn.fuelMol());
        room.removeGas(Gas.OXYGEN, burn.oxygenMol());
        // Exhaust at the temperature of the room rather than of the flame: the heat is
        // accounted for separately and in full, and adding it twice would be an engine that
        // warmed its room by more than its fuel contained.
        room.addGasAt(Gas.CARBON_DIOXIDE, burn.co2Mol(), room.temperatureK());
        room.addGasAt(Gas.WATER_VAPOR, burn.waterMol(), room.temperatureK());
        atmosphere.addHeatJoules(pos, burn.heatJ());

        // Throttled by how choked it is: a machine that worked perfectly until it stopped
        // could not be scheduled around, and one that visibly gets worse can be.
        double throttle = Fouling.output(sludge, SLUDGE_CAPACITY);
        double delivered = burn.electricalJ() * throttle;
        lastWatts = delivered / seconds;
        deliver(serverLevel, pos, delivered);
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("sludge", sludge);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        sludge = Math.clamp(input.getDoubleOr("sludge", 0.0), 0.0, SLUDGE_CAPACITY);
    }

    /** Pushes what it made down whatever run it is on, losing the run's share as usual. */
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
