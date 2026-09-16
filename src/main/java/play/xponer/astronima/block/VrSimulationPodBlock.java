package play.xponer.astronima.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.vrpod.VrPodReturn;

/**
 * The gateway, design/vr-simulation-pod.md §5 — one symmetric interaction judged only by which
 * level the player is standing in when they click it. No block entity: there is no per-instance
 * state to remember, the same reasoning {@link TelescopeBlock}'s own doc gives for skipping one.
 */
public class VrSimulationPodBlock extends Block {

    public static final MapCodec<VrSimulationPodBlock> CODEC = simpleCodec(VrSimulationPodBlock::new);

    /** The void has no floor (design/vr-simulation-pod.md §3) — the player arrives flying. */
    private static final double VOID_SPAWN_X = 0.5;
    private static final double VOID_SPAWN_Y = 100.0;
    private static final double VOID_SPAWN_Z = 0.5;

    public VrSimulationPodBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (level.dimension().equals(ModDimensions.VR_LEVEL)) {
            exit(serverPlayer);
        } else {
            enter(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    /** Public for {@code /astronima vrpod enter} (the debug-command rule) - the same path a
     *  right-click takes. Returns whether it actually sent the player anywhere. */
    public static boolean enter(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerLevel vrLevel = server == null ? null : server.getLevel(ModDimensions.VR_LEVEL);
        if (vrLevel == null) {
            return false;
        }
        Abilities abilities = player.getAbilities();
        VrPodReturn returnState = new VrPodReturn(
                player.level().dimension().identifier().toString(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(),
                abilities.flying, abilities.mayfly, abilities.instabuild);
        player.setData(ModAttachments.VR_POD_RETURN, Optional.of(returnState));

        // instabuild still needs at least one real item to place - a player who found only one
        // pod and never carried a spare would otherwise have no way to place an exit.
        ItemStack pod = new ItemStack(ModItems.VR_SIMULATION_POD.get());
        if (!player.getInventory().contains(pod)) {
            player.getInventory().add(pod.copy());
        }

        player.teleportTo(vrLevel, VOID_SPAWN_X, VOID_SPAWN_Y, VOID_SPAWN_Z, Set.of(),
                player.getYRot(), player.getXRot(), true);
        abilities.flying = true;
        abilities.mayfly = true;
        abilities.instabuild = true;
        player.onUpdateAbilities();
        return true;
    }

    /** Public for {@code /astronima vrpod exit} (the debug-command rule) - the same path a
     *  right-click takes. Returns whether it actually sent the player anywhere. */
    public static boolean exit(ServerPlayer player) {
        Optional<VrPodReturn> saved = player.getData(ModAttachments.VR_POD_RETURN);
        if (saved.isEmpty()) {
            return false;
        }
        VrPodReturn returnState = saved.get();
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return false;
        }
        ResourceKey<Level> dimensionKey = ResourceKey.create(
                Registries.DIMENSION, Identifier.parse(returnState.dimensionId()));
        ServerLevel targetLevel = server.getLevel(dimensionKey);
        if (targetLevel == null) {
            return false;
        }
        player.teleportTo(targetLevel, returnState.x(), returnState.y(), returnState.z(), Set.of(),
                returnState.yaw(), returnState.pitch(), true);
        Abilities abilities = player.getAbilities();
        abilities.flying = returnState.previousFlying();
        abilities.mayfly = returnState.previousMayFly();
        abilities.instabuild = returnState.previousInstabuild();
        player.onUpdateAbilities();
        player.setData(ModAttachments.VR_POD_RETURN, Optional.empty());
        return true;
    }
}
