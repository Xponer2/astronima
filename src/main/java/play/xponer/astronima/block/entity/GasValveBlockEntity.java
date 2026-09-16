package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import play.xponer.astronima.block.GasValveBlock;
import play.xponer.astronima.client.render.MovingParts;
import play.xponer.astronima.registry.ModBlockEntities;

/**
 * An anchor for the handwheel, and nothing more.
 *
 * <p>The valve gained a block entity for one reason: a {@link net.minecraft.client.renderer.blockentity.BlockEntityRenderer}
 * attaches to a block entity and to nothing else, and the handwheel has to turn. That is
 * the entire job.
 *
 * <p><strong>It persists nothing and decides nothing.</strong> {@link GasValveBlock#SETTING}
 * on the blockstate stays the sole source of truth — the plumbing solver reads it, the
 * model reads it, and a save writes it as part of the blockstate. This entity holds one
 * client-only float: where the wheel currently <em>is</em> on screen, chased toward the
 * setting a few degrees a tick. It is never saved, never synced, and never read by the
 * simulation. The gas test plan records the guarantee "valve/pump state is blockstate,
 * not entity", and that guarantee is why {@link #saveAdditional} is deliberately not
 * overridden: default {@link BlockEntity} persistence writes only id and position.
 *
 * <p>If this class ever grows a saved field, that guarantee is gone and the save/reload
 * and shut-valve scenarios begin depending on something new. Stated here so the next
 * person to add one has to break the rule on purpose.
 */
public class GasValveBlockEntity extends ReadableBlockEntity {
    /** Where the wheel is drawn this tick, in degrees. Client-only, unsaved. */
    private double wheelAngle;
    /** Last tick's angle, so the renderer can interpolate between game ticks. */
    private double previousWheelAngle;

    public GasValveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GAS_VALVE.get(), pos, state);
        // Start already at the setting so a freshly loaded valve does not spin up from
        // zero every time its chunk comes into view.
        this.wheelAngle = MovingParts.wheelTarget(setting(state));
        this.previousWheelAngle = this.wheelAngle;
    }

    /**
     * Advances the wheel toward the current setting by one tick.
     *
     * <p>Client-side only — the server neither has nor needs this, because the wheel is a
     * display and the setting it chases already lives on the blockstate the server owns.
     */
    public void clientTick(BlockState state) {
        previousWheelAngle = wheelAngle;
        wheelAngle = MovingParts.stepWheel(wheelAngle, MovingParts.wheelTarget(setting(state)));
    }

    /** The wheel angle for a frame between two ticks. */
    public double wheelAngle(float partialTick) {
        return previousWheelAngle + (wheelAngle - previousWheelAngle) * partialTick;
    }

    private static int setting(BlockState state) {
        return state.getValue(GasValveBlock.SETTING);
    }
}
