package play.xponer.astronima.atmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.block.BulkheadDoorBlock;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.sim.room.BlockKind;

/**
 * Maps block states to their atmosphere behavior.
 *
 * <p>The general rule is physical: a full collision cube is airtight, no collision at
 * all (air, torches, wiring) is open space gas moves through freely, and anything
 * partial (slabs, fences, vanilla doors) blocks passage imperfectly — a leak. Doors
 * get explicit handling because their sealing depends on their open state, and only
 * the bulkhead door is machined tightly enough to seal at all.
 */
public final class AirBlockKinds {
    public static BlockKind classify(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isAir()) {
            return BlockKind.OPEN;
        }
        if (state.getBlock() instanceof BulkheadDoorBlock) {
            return state.getValue(DoorBlock.OPEN) ? BlockKind.OPEN : BlockKind.SEALED;
        }
        if (state.getBlock() instanceof DoorBlock) {
            // An ordinary door is never airtight: gaps around the frame always leak.
            return state.getValue(DoorBlock.OPEN) ? BlockKind.OPEN : BlockKind.LEAKY;
        }
        if (state.is(ModBlocks.AMMONIA_HEAT_PIPE.get())) {
            // Explicit, not the generic shape rule below: a heat pipe's own doc calls it
            // "a sealed, passive conductor" - the slim body (design/wire-parts.md's "visible
            // and slim", not a full cube) would otherwise fall through to LEAKY the same way
            // a gas valve's slim body correctly does, and Atmosphere.tick()'s leak exchange
            // would mix real gas - and with it, real heat - between the two rooms it bridges,
            // on top of (and far faster than) the wattage HeatPipeTransfer actually computes.
            return BlockKind.SEALED;
        }
        if (state.isCollisionShapeFullBlock(level, pos)) {
            return BlockKind.SEALED;
        }
        return state.getCollisionShape(level, pos).isEmpty() ? BlockKind.OPEN : BlockKind.LEAKY;
    }

    /**
     * Whether this block resists heat as well as pressure.
     *
     * <p>A property of the material, asked separately from {@link #classify} because the two
     * are independent - see {@code BlockAccess.insulatedAt}. Kept here rather than on the
     * block class so that the whole "what does the atmosphere make of this state" question
     * has one home.
     */
    public static boolean insulates(BlockState state) {
        return state.is(ModBlocks.INSULATED_HULL_PLATE.get())
                || state.is(ModBlocks.PAINTED_INSULATED_HULL_PLATE.get());
    }

    /**
     * Whether this block reflects the sun as well as holding pressure.
     *
     * <p>Independent of {@link #insulates}, the same way that is independent of {@link
     * #classify} — see its own doc and {@code BlockAccess.paintedAt}. A plate can be painted,
     * insulated, both, or neither.
     */
    public static boolean paints(BlockState state) {
        return state.is(ModBlocks.PAINTED_HULL_PLATE.get())
                || state.is(ModBlocks.PAINTED_INSULATED_HULL_PLATE.get());
    }

    private AirBlockKinds() {}
}
