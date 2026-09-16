package play.xponer.astronima.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A chemical light stick — light without fire.
 *
 * <p>Chemiluminescence is a reaction between two liquids that emits photons directly;
 * it needs no oxygen and produces no heat, which is exactly why glow sticks work
 * underwater and why divers and aircrews carry them. On an airless rock it is the
 * only lighting available before electricity, since a flame simply goes out.
 *
 * <p>The reaction is consumed as it runs: the stick fades through {@value #MAX_AGE}
 * stages and finally goes dark. That is honest chemistry and a steady pull toward
 * electric lamps.
 */
public class GlowStickBlock extends Block {
    /** 0 = freshly cracked, MAX_AGE = spent. */
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 4);
    public static final int MAX_AGE = 4;

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    public GlowStickBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    /** Bright when fresh, dimming as the reaction is used up, dark when spent. */
    public static int lightFor(BlockState state) {
        int age = state.getValue(AGE);
        return age >= MAX_AGE ? 0 : Math.max(2, 12 - age * 3);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) < MAX_AGE;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        if (age < MAX_AGE) {
            level.setBlockAndUpdate(pos, state.setValue(AGE, age + 1));
        }
    }
}
