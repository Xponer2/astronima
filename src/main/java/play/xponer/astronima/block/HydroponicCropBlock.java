package play.xponer.astronima.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.atmosphere.SkyExposure;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.chem.HydroponicGrowth;

/**
 * A real hydroponic crop — red romaine lettuce, the same cultivar NASA's own Veg-01/03/05 ISS
 * experiments grow. See {@link HydroponicGrowth}, {@code design/hydroponics.md} §4.
 *
 * <p><strong>Not a {@code CropBlock}.</strong> Checked this session: vanilla's own class gates
 * growth on any block light and real farmland moisture, neither of which this mod's world has
 * (no vanilla farming, by design). This is a from-scratch {@code Block}, the same shape
 * {@link MoldBlock} already is for the same reason, paced by real sunlight, real room CO2 and
 * real room water vapour instead.
 *
 * <p><strong>No die-back.</strong> A missing condition holds the stage rather than retreating it —
 * this is a benefit system a player is trying to keep, not a hazard fought back, the same "kept
 * gentle" ethos {@code PLAN.md}'s own Part B line already commits to.
 */
public class HydroponicCropBlock extends Block {
    public static final MapCodec<HydroponicCropBlock> CODEC = simpleCodec(HydroponicCropBlock::new);

    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 10, 14);
    private static final float GROWTH_CHANCE = 0.3f;

    public HydroponicCropBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.AGE_3, 0));
    }

    @Override
    protected MapCodec<? extends HydroponicCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.AGE_3);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(ModBlocks.HULL_PLATE.get());
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(BlockStateProperties.AGE_3) < HydroponicGrowth.MAX_AGE;
    }

    /** One real growth attempt: all three real conditions favourable, a growth roll hits, and the
     *  room actually has the CO2 this stage needs — spent for real O2 released, 1:1. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(BlockStateProperties.AGE_3);
        if (age >= HydroponicGrowth.MAX_AGE) {
            return;
        }
        RoomState room = Atmosphere.get(level).roomTouching(pos);
        double sunlight = SkyExposure.sunlightAt(level, pos);
        double roomCo2 = room == null ? 0 : room.gases().get(Gas.CARBON_DIOXIDE);
        double roomWater = room == null ? 0 : room.gases().get(Gas.WATER_VAPOR);
        if (!HydroponicGrowth.favorable(sunlight, roomCo2, roomWater)) {
            return; // held, no loss - a benefit system, not a hazard
        }
        if (random.nextFloat() >= GROWTH_CHANCE) {
            return;
        }
        room.removeGas(Gas.CARBON_DIOXIDE, HydroponicGrowth.CO2_PER_GROWTH_MOL);
        room.addGasAt(Gas.OXYGEN, HydroponicGrowth.O2_PER_GROWTH_MOL, room.temperatureK());
        level.setBlockAndUpdate(pos,
                state.setValue(BlockStateProperties.AGE_3, HydroponicGrowth.grown(age)));
    }

    /**
     * Runs one real {@link #randomTick} against whatever is at {@code pos} right now, if it is
     * still this crop — the same debug/test door {@link MoldBlock#simulateFavourableTick} already
     * opens for its own random-tick-paced block: replays the exact same real conditions check and
     * dice roll, any number of times, instead of waiting on vanilla's own random-tick lottery.
     */
    public static void simulateTick(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.HYDROPONIC_CROP.get())) {
            ((HydroponicCropBlock) state.getBlock()).randomTick(state, level, pos, random);
        }
    }

    /**
     * Real "pick-and-eat": harvesting a mature plant drops lettuce and returns it to
     * {@link HydroponicGrowth#HARVESTED_AGE}, not age 0 or removal — the real plant survives its
     * own harvest, the same fact NASA's own Veg-05 names (§4.1). It also drops real
     * {@code crop_waste} — the real inedible plant matter (outer leaves, roots, stem) every real
     * leafy-green harvest leaves behind, Part E's own real feedstock
     * (design/anaerobic-digestion.md §0).
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        int age = state.getValue(BlockStateProperties.AGE_3);
        if (!HydroponicGrowth.isMature(age)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5,
                pos.getZ() + 0.5, new ItemStack(ModItems.LETTUCE.get()));
        net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5,
                pos.getZ() + 0.5, new ItemStack(ModItems.CROP_WASTE.get()));
        level.setBlockAndUpdate(pos,
                state.setValue(BlockStateProperties.AGE_3, HydroponicGrowth.HARVESTED_AGE));
        return InteractionResult.SUCCESS;
    }
}
