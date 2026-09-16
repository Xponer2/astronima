package play.xponer.astronima.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import play.xponer.astronima.block.TelescopeBlock;
import play.xponer.astronima.telescope.TelescopeMountEntity;

/**
 * "Here is what I am currently aimed at" — sent once per client tick while an observation session
 * is active (design/astra-telescope.md §2.1's v3 mechanism, PLAN.md rule 79's own correction away
 * from riding), so the server's own copy of {@code TelescopeMountEntity} stays fresh enough for
 * whatever next needs its rotation: a capture attempt, or the aim saved when the session ends.
 *
 * <p>Never trusted outright — {@link TelescopeMountEntity#setClampedAim} re-derives
 * {@link play.xponer.astronima.sim.optics.TelescopeMount#clamp} itself from the claimed direction,
 * the same trust boundary this design already keeps for occlusion and the target lookup. A player
 * is only ever allowed to move the one mount their own session actually owns
 * ({@link TelescopeMountEntity#isObservedBy}).
 */
public record TelescopeAimUpdatePayload(BlockPos telescopePos, float yaw, float pitch)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TelescopeAimUpdatePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("astronima", "telescope_aim_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TelescopeAimUpdatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBlockPos(payload.telescopePos);
                        buffer.writeFloat(payload.yaw);
                        buffer.writeFloat(payload.pitch);
                    },
                    buffer -> new TelescopeAimUpdatePayload(
                            buffer.readBlockPos(), buffer.readFloat(), buffer.readFloat()));

    public static void apply(TelescopeAimUpdatePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(level.getBlockState(payload.telescopePos()).getBlock() instanceof TelescopeBlock)) {
            return;
        }
        TelescopeMountEntity mount = TelescopeMountEntity.findAt(level, payload.telescopePos());
        if (mount == null || !mount.isObservedBy(player)) {
            return;
        }
        mount.setClampedAim(payload.yaw(), payload.pitch());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
