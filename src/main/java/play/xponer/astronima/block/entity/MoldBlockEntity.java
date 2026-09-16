package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import play.xponer.astronima.block.MoldBlock;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.sim.MoldBranching;

import java.util.List;

/**
 * Nothing here is saved — every real fact about a colony (its stage, which surface it clings to)
 * already lives on the blockstate. This exists so {@code MoldBlockEntityRenderer} has somewhere
 * to cache the branch shape {@link MoldBranching} computes, instead of re-running the walk every
 * single frame (Gemini's own audit named this cost correctly, even though the rest of its
 * suggested pipeline — a bespoke mesh/shader system — was not the right size for one decorative
 * block; see {@code design/mold-growth.md} §3c), and it is also the one place Minecraft-side
 * enough to ask the world which of this colony's edges have a real neighbour to grow toward
 * (§3d).
 */
public class MoldBlockEntity extends BlockEntity {
    private List<MoldBranching.Segment> cachedTwigs;
    private int cachedForKey = -1;

    public MoldBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOLD.get(), pos, state);
    }

    /** This colony's branching shape at its current age and neighbour arrangement — recomputed
     *  only when either changes, not every frame. */
    public List<MoldBranching.Segment> twigs() {
        BlockState state = getBlockState();
        int age = state.getValue(BlockStateProperties.AGE_3);
        MoldBranching.EdgeBias bias = neighbourBias(state);
        int key = age | (bias.negX() ? 1 << 8 : 0) | (bias.posX() ? 1 << 9 : 0)
                | (bias.negZ() ? 1 << 10 : 0) | (bias.posZ() ? 1 << 11 : 0);
        if (cachedTwigs == null || cachedForKey != key) {
            long seed = Mth.getSeed(getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ());
            cachedTwigs = MoldBranching.forAge(seed, age, bias);
            cachedForKey = key;
        }
        return cachedTwigs;
    }

    /**
     * Which of this colony's four in-plane local edges have <strong>any</strong> mold block on
     * the other side, real-world neighbour, regardless of that neighbour's own attached face —
     * a floor colony's edge toward the base of a wall counts exactly the same as a floor
     * colony's edge toward more floor, because the corner between them is exactly where
     * continuity matters (design/mold-growth.md §3d; reported live as looking like two
     * unrelated colonies, and separately as a colony that could only ever be "on the floor or
     * on the wall", never reaching across the seam between them).
     */
    private MoldBranching.EdgeBias neighbourBias(BlockState state) {
        Level level = getLevel();
        if (level == null) {
            return MoldBranching.EdgeBias.NONE;
        }
        Direction away = MoldBlock.awayDirection(state);
        Direction[] axes = localAxisDirections(away);
        BlockPos pos = getBlockPos();
        return new MoldBranching.EdgeBias(
                hasMold(level, pos.relative(axes[1])),
                hasMold(level, pos.relative(axes[0])),
                hasMold(level, pos.relative(axes[3])),
                hasMold(level, pos.relative(axes[2])));
    }

    private static boolean hasMold(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.MOLD.get());
    }

    /**
     * Which real-world direction each of the two local in-plane axes (+X/+Z in {@link
     * MoldBranching}'s own floor-relative frame) ends up pointing at, once {@code
     * MoldBlockEntityRenderer}'s {@code awayRotation} turns that frame onto the surface named by
     * {@code away}. Derived directly from that same rotation (single-axis 90/180 degree turns
     * never touch the axis they do not rotate about, which is why local X survives every X-axis
     * rotation unchanged and local Z survives every Z-axis rotation unchanged):
     *
     * <pre>
     * away    | local +X | local -X | local +Z | local -Z
     * UP      | EAST     | WEST     | SOUTH    | NORTH
     * DOWN    | EAST     | WEST     | NORTH    | SOUTH
     * NORTH   | EAST     | WEST     | UP       | DOWN
     * SOUTH   | EAST     | WEST     | DOWN     | UP
     * EAST    | DOWN     | UP       | SOUTH    | NORTH
     * WEST    | UP       | DOWN     | SOUTH    | NORTH
     * </pre>
     *
     * @return {posX, negX, posZ, negZ}
     */
    private static Direction[] localAxisDirections(Direction away) {
        return switch (away) {
            case UP -> new Direction[] {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
            case DOWN -> new Direction[] {Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH};
            case NORTH -> new Direction[] {Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN};
            case SOUTH -> new Direction[] {Direction.EAST, Direction.WEST, Direction.DOWN, Direction.UP};
            case EAST -> new Direction[] {Direction.DOWN, Direction.UP, Direction.SOUTH, Direction.NORTH};
            case WEST -> new Direction[] {Direction.UP, Direction.DOWN, Direction.SOUTH, Direction.NORTH};
        };
    }
}
