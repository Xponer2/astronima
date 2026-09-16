package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.thermal.HeatBalance;
import play.xponer.astronima.sim.thermal.PhaseChangeMaterial;

/**
 * A real thermal mass: melts or freezes at a fixed point, banking a room's own temperature
 * swing as latent heat instead of letting it through. See {@link PhaseChangeMaterial} for the
 * mechanism and {@code design/phase-change-blocks.md} for why.
 *
 * <p>Reads the room's own temperature <strong>after</strong> {@link Atmosphere} has already
 * stepped it this tick and, if that step crossed the melt point, un-crosses it — the same
 * external-correction shape {@code AmmoniaHeatPipeBlockEntity} already uses to move heat between
 * two rooms rather than owning a second, competing heat model.
 *
 * <p>{@code meltFraction} is real state a player can act on (rule 9) — placing more blocks in a
 * hot room depends on knowing whether the ones already there still have headroom — so this
 * extends {@link ReadableBlockEntity} rather than the plain {@code BlockEntity} the heat pipe
 * gets away with: the pipe has no state of its own to report, only two rooms' temperatures the
 * gas analyzer already shows.
 */
public class ParaffinThermalMassBlockEntity extends ReadableBlockEntity {

    private double meltFraction;

    public ParaffinThermalMassBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PARAFFIN_THERMAL_MASS.get(), pos, state);
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel)
                || level.getGameTime() % Atmosphere.TICK_INTERVAL != 0) {
            return;
        }
        Atmosphere atmosphere = Atmosphere.get(serverLevel);
        // roomAt(pos) would ask about this block's own solid cell, which is never itself part
        // of a scanned room - roomTouching is the real API "how full-block machines find their
        // air" (Atmosphere's own doc), checking whichever of the six faces actually borders one.
        RoomState room = atmosphere.roomTouching(pos);
        if (room == null) {
            return;
        }
        double capacityJPerK = HeatBalance.SHELL_CAPACITY_J_PER_K_PER_M2
                * HeatBalance.hullAreaM2(room.volumeBlocks());
        PhaseChangeMaterial.Step step = PhaseChangeMaterial.step(room.temperatureK(), meltFraction,
                PhaseChangeMaterial.MASS_PER_BLOCK_KG, capacityJPerK);
        if (step.temperatureK() != room.temperatureK()) {
            room.setTemperatureK(step.temperatureK());
        }
        if (step.meltFraction() != meltFraction) {
            meltFraction = step.meltFraction();
            setChanged();
        }
    }

    /** 0 (fully solid) .. 1 (fully liquid) — read by {@code /astronima phasechange status}. */
    public double meltFraction() {
        return meltFraction;
    }

    /** Set directly by {@code /astronima phasechange set} — the debug hook every mechanic in
     *  this mod ships with, so this one's state can be forced without waiting real minutes for
     *  a room to actually swing through the melt point. */
    public void setMeltFraction(double meltFraction) {
        this.meltFraction = Math.clamp(meltFraction, 0.0, 1.0);
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("melt_fraction", meltFraction);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        meltFraction = input.getDoubleOr("melt_fraction", 0.0);
    }
}
