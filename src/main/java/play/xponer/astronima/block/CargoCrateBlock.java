package play.xponer.astronima.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.entity.CargoCrateBlockEntity;

/**
 * Storage for a world with no wood.
 *
 * <p>A chest needs planks and an asteroid has no trees, so until now the player had nowhere
 * to put anything. This is a plain box on purpose — the one block in the mod that should hold
 * no surprises.
 *
 * <p>What it does have is a property a chest does not: it is a <strong>full cube</strong>, so
 * {@code AirBlockKinds} classifies it as airtight and a wall of crates holds pressure. A chest
 * is fourteen-sixteenths of a block and would read as a leak — a hole in the hull you cannot
 * see. That is the whole difference between storage and storage you can build with, and it is
 * why the collision shape here is left alone rather than inset for looks (rule 15: the failure
 * would be geometric and invisible).
 */
public class CargoCrateBlock extends Block implements EntityBlock {
    public CargoCrateBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof CargoCrateBlockEntity crate) {
            serverPlayer.openMenu(crate);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CargoCrateBlockEntity(pos, state);
    }

    // No drop-on-break override here, deliberately, and it took a mutation to find out why:
    // BlockEntity.preRemoveSideEffects already drops the contents of *any* block entity that
    // is a Container, and it runs before the block's own removal hook. An override would be
    // dead code that looks load-bearing — the worst kind, because the next person to touch
    // this file would trust it. The behaviour is still guarded by
    // scenario_a_wall_of_crates_holds_pressure, which is what a player would notice.
}
