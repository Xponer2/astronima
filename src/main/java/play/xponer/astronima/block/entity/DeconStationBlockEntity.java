package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import play.xponer.astronima.physio.CarriedContamination;
import play.xponer.astronima.physio.Contaminations;
import play.xponer.astronima.power.CableNetworks;
import play.xponer.astronima.power.PowerRun;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.sim.pathogen.Contamination;
import play.xponer.astronima.sim.pathogen.Decontamination;
import play.xponer.astronima.sim.thermal.HeatBalance;

/**
 * A decon booth: ultraviolet for the bulk of it, then water for what the light could not see.
 *
 * <p>It closes the loop {@code design/transmission.md} left open — there were three ways to pick
 * something up and one way to put it down, which was to stand outside and wait.
 *
 * <p><strong>The lamp visibly stalls.</strong> Light cleans what it can see, and a suit has folds
 * and a boot has a sole, so a fixed share of the load is out of reach of any amount of ultraviolet.
 * The station stops there and says so rather than creeping towards a number it will never make.
 * That is the moment the second control earns its existence, and it has to be seen rather than
 * read in a manual.
 *
 * <p><strong>The lamp costs real power</strong> (game-design audit #2, finding G) — this design's
 * own stated cost for the UV method (design/decontamination.md: "power" vs. the rinse's "water,
 * consumed") was never actually wired to {@link CableNetworks}, the only Power-tier machine in the
 * mod that could never be shut off by a power outage. Draws {@link HeatBalance#WORKED_MACHINE_W}
 * (the same "what a worked machine needs" baseline every other consumer in the mod is sized off)
 * and scales the lamp's effective exposure by whatever fraction of that it actually got — power
 * buys rate here exactly as it does on a {@code ProcessingBlockEntity}, never a cliff. With zero
 * power the lamp phase is simply never "still useful" this tick, so the booth falls straight
 * through to the water rinse it already has — nobody is stranded, water alone still finishes the
 * job, only slower and at a real cost, same as everywhere else power is optional but faster.
 */
public class DeconStationBlockEntity extends ReadableBlockEntity {

    /** Lamp irradiance at the occupant, W/m². A decade in about five seconds. */
    public static final double IRRADIANCE = 6.0;

    /** How much water the booth holds, in litres. Two full cycles from filthy. */
    public static final double TANK_LITRES = 4.0;

    /** Water a running rinse uses per second. */
    public static final double RINSE_LITRES_PER_SECOND = 0.15;

    /** Below this the booth calls it done — the target it quotes and then meets. */
    public static final double TARGET = Contamination.NEGLIGIBLE;

    private double water = TANK_LITRES;
    private boolean running;
    private boolean rinsing;
    private double decadesThisCycle;

    public DeconStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DECON_STATION.get(), pos, state);
    }

    public double water() {
        return water;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isRinsing() {
        return rinsing;
    }

    public double decadesThisCycle() {
        return decadesThisCycle;
    }

    /** Top the tank up from a bottle. */
    public boolean addWater(double litres) {
        if (water >= TANK_LITRES - 1e-6) {
            return false;
        }
        water = Math.min(TANK_LITRES, water + litres);
        setChanged();
        return true;
    }

    public void start() {
        running = true;
        rinsing = false;
        decadesThisCycle = 0;
        setChanged();
    }

    public void stop() {
        running = false;
        rinsing = false;
        setChanged();
    }

    /**
     * One step of a cycle on whoever is standing in the booth.
     *
     * <p>Runs on the occupant rather than on stored state, because the thing being cleaned is a
     * person who can walk out halfway through — and walking out halfway has to leave them
     * halfway clean, not cancel the whole thing.
     */
    public void serverTick(ServerLevel level, double seconds) {
        if (!running) {
            return;
        }
        Player occupant = level.getNearestPlayer(worldPosition.getX() + 0.5,
                worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, 1.5, false);
        if (occupant == null) {
            stop();
            return;
        }
        CarriedContamination carried = Contaminations.of(occupant);
        if (carried.isClean()) {
            stop();
            return;
        }

        double before = Math.max(carried.glove(), carried.skin());
        double glove = carried.glove();
        double skin = carried.skin();

        // The lamp first: it is fast and it costs no stock, but it does cost real power. Once it
        // has taken everything it can see, or it has no power to run at all this tick, the booth
        // falls through to the rinse rather than sitting there looking busy.
        double poweredFraction = drawLampPower(level, seconds);
        double litSeconds = seconds * poweredFraction;
        boolean lampStillUseful = Decontamination.lampShouldRun(glove, litSeconds, IRRADIANCE);
        if (lampStillUseful) {
            rinsing = false;
            glove = Decontamination.afterUltraviolet(glove, litSeconds, IRRADIANCE);
            skin = Decontamination.afterUltraviolet(skin, litSeconds, IRRADIANCE);
        } else if (water > 0) {
            rinsing = true;
            double litres = Math.min(water, RINSE_LITRES_PER_SECOND * seconds);
            water -= litres;
            glove = Decontamination.afterRinse(glove, litres);
            skin = Decontamination.afterRinse(skin, litres);
        } else {
            // Dry, and the lamp has taken everything it can reach. Stopping is the honest state:
            // the player is as clean as this booth can make them without water.
            stop();
            return;
        }

        Contamination gloves = new Contamination(carried.source(), glove);
        Contamination body = new Contamination(carried.source(), skin);
        occupant.setData(play.xponer.astronima.registry.ModAttachments.CONTAMINATION.get(),
                carried.with(gloves, body));

        decadesThisCycle += Decontamination.decadesRemoved(before, Math.max(glove, skin));
        if (gloves.isClean() && body.isClean()) {
            stop();
        }
        setChanged();
    }

    /** What fraction, 0..1, of the lamp's wanted draw the network actually delivered this tick —
     *  same {@link CableNetworks}/{@link PowerRun} path every other power consumer in the mod
     *  uses, not a bespoke shortcut. */
    private double drawLampPower(ServerLevel level, double seconds) {
        double wantedJoules = HeatBalance.WORKED_MACHINE_W * seconds;
        if (wantedJoules <= 0) {
            return 0;
        }
        CableNetworks.Resolved run = CableNetworks.resolveFrom(level, worldPosition);
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
        double delivered = PowerRun.sendAlong(level, run, taken, seconds);
        return Math.min(1.0, delivered / wantedJoules);
    }

    /** What it would take to finish from here, so the player is told before they commit. */
    public String quoteFor(Player player) {
        CarriedContamination carried = Contaminations.of(player);
        double worst = Math.max(carried.glove(), carried.skin());
        if (worst < TARGET) {
            return "already clean";
        }
        double lampSeconds = Decontamination.secondsOfUltravioletToReach(worst,
                worst * Decontamination.SHADOWED_FRACTION * 1.05, IRRADIANCE);
        double litres = Decontamination.litresOfRinseToReach(
                worst * Decontamination.SHADOWED_FRACTION, TARGET);
        return String.format(java.util.Locale.ROOT, "%.0f s lamp + %.1f L rinse",
                Double.isFinite(lampSeconds) ? lampSeconds : 0, litres);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        water = input.getDoubleOr("water", TANK_LITRES);
        running = input.getBooleanOr("running", false);
        rinsing = input.getBooleanOr("rinsing", false);
        decadesThisCycle = input.getDoubleOr("decades", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("water", water);
        output.putBoolean("running", running);
        output.putBoolean("rinsing", rinsing);
        output.putDouble("decades", decadesThisCycle);
    }
}
