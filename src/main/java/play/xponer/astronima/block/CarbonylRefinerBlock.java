package play.xponer.astronima.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.entity.CarbonylRefinerBlockEntity;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.power.PowerConnectable;
import play.xponer.astronima.registry.ModBlockEntities;

/**
 * The Mond process in a box: carbon monoxide carries nickel out of a charge and puts it back
 * down pure, handing the gas back.
 *
 * <p>See {@link CarbonylRefinerBlockEntity} for why the temperature <em>between</em> its two
 * working points is the most dangerous setting in the game.
 */
public class CarbonylRefinerBlock extends BaseEntityBlock implements PowerConnectable, play.xponer.astronima.wire.Terminated,
        play.xponer.astronima.wire.SignalSource {

    /**
     * Drives its status stud while it is actually turning.
     *
     * <p>"Working" rather than "done", because working is the state a machine <em>has</em> — a
     * one-shot "finished" pulse would need somewhere to remember that it had fired. An idle
     * signal is this one through a NOT gate, which is exactly what the NOT gate is for.
     *
     * <p>And a second, separate line for <em>held</em> — out of feed, product backed up, work
     * ruined. Folding that into "not working" would tell a base that a finished machine and a
     * jammed one are the same event, and they are not: one wants patience and the other wants a
     * person.
     */
    @Override
    public boolean isDriving(net.minecraft.server.level.ServerLevel level,
                             net.minecraft.core.BlockPos pos,
                             play.xponer.astronima.wire.Terminal terminal) {
        if (terminal.kind() != play.xponer.astronima.wire.Terminal.Kind.SIGNAL_OUT) {
            return false;
        }
        if (!(level.getBlockEntity(pos)
                instanceof play.xponer.astronima.block.entity.ProcessingBlockEntity machine)) {
            return false;
        }
        // Two outputs, told apart by which row the stud is on: the working line while it turns,
        // and the held line while it is stopped for a reason a person has to fix.
        // Told apart by the name on the stud, not by which row it is on. The row was a second
        // way of saying the same thing, and it stopped being unambiguous the moment another stud
        // moved into that row - which is precisely how the servo arrived (PLAN rule 44).
        //
        // Both named, and anything else drives nothing. An `else` covering the working line would
        // also cover every output added after this was written, and it would report them all as
        // "the machine is running" - plausibly, and wrongly.
        String stud = terminal.label();
        if (play.xponer.astronima.wire.Terminated.STUCK.equals(stud)) {
            return machine.workState().isHolding();
        }
        if (play.xponer.astronima.wire.Terminated.WORKING.equals(stud)) {
            return machine.workState().isWorking();
        }
        return false;
    }

    /** A terminal strip on every side: enable in, power, status out. */
    @Override
    public java.util.List<play.xponer.astronima.wire.Terminal> terminals(
            net.minecraft.world.level.block.state.BlockState state) {
        return play.xponer.astronima.wire.Terminated.machineStrip();
    }

    public static final MapCodec<CarbonylRefinerBlock> CODEC =
            simpleCodec(CarbonylRefinerBlock::new);

    public CarbonylRefinerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /**
     * Opens the vessel's own panel.
     *
     * <p>Missing until this rework — the refiner had a menu type, a screen, and a whole picture
     * of its own column drawn for it, but nothing that ever right-clicked it open. Every other
     * processing machine wires this the same way, one file each; this one had simply never been
     * given its own copy.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof CarbonylRefinerBlockEntity machine) {
            serverPlayer.openMenu(new play.xponer.astronima.menu.ProcessingUiHolder(machine, pos),
                    buffer -> {
                buffer.writeVarInt(ProcessingMenu.Kind.REFINER.ordinal());
                buffer.writeVarInt(machine.getContainerSize());
                buffer.writeBlockPos(pos);
            });
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CarbonylRefinerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.CARBONYL_REFINER.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((CarbonylRefinerBlockEntity) blockEntity).serverTick();
    }
}
