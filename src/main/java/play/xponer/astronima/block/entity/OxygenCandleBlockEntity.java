package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.block.OxygenCandleBlock;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;

/**
 * Burn logic for the chlorate candle.
 *
 * <p>Yield is scaled up from the real Vika unit (~25 mol O2/kg) to a base-sized charge:
 * 100 mol over 4 minutes. In a 27 m³ room that raises ppO2 by ~9 kPa; in a small
 * closet it will overshoot toward oxygen toxicity — ventilation is the player's job.
 * The reaction is exothermic: oxygen leaves the canister hot.
 */
public class OxygenCandleBlockEntity extends ReadableBlockEntity {
    /** Total O2 released over one burn. */
    private static final double TOTAL_O2_MOL = 100.0;
    /** Full burn duration in atmosphere steps (4 min = 4800 game ticks / 10). */
    private static final int TOTAL_BURN_STEPS = 480;
    /**
     * The chlorate bed itself burns near 900 K, but a real SFOG canister passes its
     * output through a cooling filter before it reaches the cabin — the canister gets
     * dangerously hot, the delivered oxygen does not. Releasing at flame temperature
     * would cook the room.
     */
    private static final double RELEASE_TEMP_K = 340.0;

    private int burnStepsLeft = TOTAL_BURN_STEPS;

    public OxygenCandleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OXYGEN_CANDLE.get(), pos, state);
    }

    /** Fraction of the candle left, 0..1 — visible as how far it has burned down. */
    public float burnFraction() {
        return (float) burnStepsLeft / TOTAL_BURN_STEPS;
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel) || !state.getValue(OxygenCandleBlock.LIT)) {
            return;
        }
        if (level.getGameTime() % Atmosphere.TICK_INTERVAL != 0) {
            return;
        }

        RoomState room = Atmosphere.get(serverLevel).roomAt(pos);
        if (room != null) {
            // The candle's own cell is open space (no collision), so it sits inside the room.
            room.addGasAt(Gas.OXYGEN, TOTAL_O2_MOL / TOTAL_BURN_STEPS, RELEASE_TEMP_K);
        }
        // Without a room (vacuum, unsealed space) the candle still burns — chlorate
        // carries its own oxidizer — but the oxygen is simply lost to space.

        if (--burnStepsLeft <= 0) {
            level.setBlockAndUpdate(pos, state.setValue(OxygenCandleBlock.LIT, false)
                    .setValue(OxygenCandleBlock.SPENT, true));
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("burn_steps_left", burnStepsLeft);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        burnStepsLeft = input.getIntOr("burn_steps_left", TOTAL_BURN_STEPS);
    }
}
