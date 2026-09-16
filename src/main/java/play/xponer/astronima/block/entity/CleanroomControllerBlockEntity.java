package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.power.CableNetworks;
import play.xponer.astronima.power.PowerRun;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.sim.Cleanroom;
import play.xponer.astronima.sim.GasMixture;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.thermal.HeatBalance;

/**
 * A real HEPA blower/positive-pressure unit — design/halogens.md §30-34 (Part C3).
 *
 * <p>Cleanliness lives here, not on {@link RoomState}: this quantity has exactly one producer and
 * one reader, unlike gas or temperature, and teaching {@code Atmosphere.materialize()} to inherit
 * a fourth room quantity correctly across every split/merge case would be a second hard problem
 * this part does not need to solve (§30). The same real reasoning gives this shape to
 * {@link ScrubberBlockEntity#capacityLeftMol} already.
 *
 * <h2>Real power, unlike the scrubber</h2>
 * Moving air against a pressure gradient is real mechanical work — this draws
 * {@link HeatBalance#WORKED_MACHINE_W} through {@link CableNetworks}/{@link PowerRun} exactly the
 * way {@link DeconStationBlockEntity#drawLampPower(ServerLevel, double)} already does, and scales
 * how much air it actually injects by whatever fraction of that it got. Power buys rate, never a
 * free reaction.
 */
public class CleanroomControllerBlockEntity extends ReadableBlockEntity {

    private double capacityLeftMol;
    private double cleanliness;

    public CleanroomControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLEANROOM_CONTROLLER.get(), pos, state);
    }

    /** 0..1 — what the gauge's own bar reads. */
    public double cleanliness() {
        return cleanliness;
    }

    /** Filter remaining, 0..1 — what the gauge's own label/band folds in (§34). */
    public float chargeFraction() {
        return (float) Cleanroom.chargeFraction(capacityLeftMol);
    }

    /**
     * Sets the tracked cleanliness directly. Real gameplay only ever moves this through
     * {@link #serverTick}'s own real rates (§31/§32) — this exists so a scenario proving the
     * etch station's own cleanliness *gate* does not have to first simulate the real ~20-real-
     * minute climb C3's own scenario already proves separately (design/halogens.md §44, rule 14:
     * naming the seam rather than re-proving the whole chain twice).
     */
    public void setCleanlinessForScenario(double value) {
        cleanliness = Math.clamp(value, 0.0, 1.0);
        setChanged();
    }

    public void serverTick(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)
                || level.getGameTime() % Atmosphere.TICK_INTERVAL != 0) {
            return;
        }
        double dtSeconds = Atmosphere.TICK_INTERVAL / 20.0;
        Atmosphere.RoomReading reading = Atmosphere.get(serverLevel).readingNear(pos);
        if (reading == null || reading.openToSpace()) {
            return; // nothing to hold air at all - the honest "holds" shape every gated machine uses
        }
        boolean sealed = reading.sealed();
        RoomState room = reading.state();
        if (sealed && room.pressureKPa() < Cleanroom.TARGET_PRESSURE_KPA && capacityLeftMol > 0) {
            double poweredFraction = drawPower(serverLevel, dtSeconds);
            if (poweredFraction > 0) {
                double wantMol = Cleanroom.airToInjectMol(dtSeconds) * poweredFraction;
                double injected = Math.min(wantMol, capacityLeftMol);
                if (injected > 0) {
                    room.addMixtureAt(GasMixture.earthAirMoles(injected), room.temperatureK());
                    capacityLeftMol -= injected;
                }
            }
        }
        boolean pressurized = sealed && Cleanroom.isPositivelyPressurized(room.pressureKPa());
        double next = Cleanroom.stepCleanliness(cleanliness, sealed, pressurized, dtSeconds);
        if (Math.abs(next - cleanliness) > 1e-9) {
            cleanliness = next;
        }
        setChanged();
    }

    /** What fraction, 0..1, of the blower's wanted draw the network actually delivered this tick —
     *  same {@link CableNetworks}/{@link PowerRun} path every other power consumer in the mod
     *  uses (mirrors {@code DeconStationBlockEntity.drawLampPower} exactly). */
    private double drawPower(ServerLevel level, double seconds) {
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

    /** Loads a fresh filter, refusing while the current one still has real capacity left. */
    public boolean tryLoadFilter(Player player) {
        if (!Cleanroom.needsSwap(capacityLeftMol)) {
            say(player, "astronima.cleanroom_controller.filter_still_good");
            return false;
        }
        capacityLeftMol = Cleanroom.HEPA_CAPACITY_MOL;
        setChanged();
        say(player, "astronima.cleanroom_controller.filter_loaded");
        return true;
    }

    private static void say(Player player, String key) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.translatable(key), true);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("capacity_left_mol", capacityLeftMol);
        output.putDouble("cleanliness", cleanliness);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        capacityLeftMol = input.getDoubleOr("capacity_left_mol", 0.0);
        cleanliness = input.getDoubleOr("cleanliness", 0.0);
    }
}
