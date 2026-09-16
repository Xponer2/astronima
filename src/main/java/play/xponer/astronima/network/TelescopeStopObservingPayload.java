package play.xponer.astronima.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import play.xponer.astronima.telescope.TelescopeMountEntity;

/**
 * "I am done observing" — sent for every way a session ends that is not itself a right-click on the
 * telescope (design/astra-telescope.md §2.3.3 step 6): the crosshair can never reach the telescope
 * block while its camera is at the eyepiece pointing at the sky (§2.3.0 bug C), so sneaking and
 * walking too far away are the only reachable exits, and neither of those is the ordinary
 * block-interaction path {@code TelescopeBlock.useWithoutItem} already ends a session through.
 *
 * <p>Idempotent by construction: if the session already ended some other way (block broken, or the
 * player who is not the current observer), {@link TelescopeMountEntity#findAt} either finds nothing
 * or finds a mount {@link TelescopeMountEntity#isObservedBy} refuses, and this is a silent no-op —
 * the same shape every other telescope payload already keeps.
 */
public record TelescopeStopObservingPayload(BlockPos telescopePos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TelescopeStopObservingPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("astronima", "telescope_stop_observing"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TelescopeStopObservingPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeBlockPos(payload.telescopePos),
                    buffer -> new TelescopeStopObservingPayload(buffer.readBlockPos()));

    public static void apply(TelescopeStopObservingPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        TelescopeMountEntity mount = TelescopeMountEntity.findAt(level, payload.telescopePos());
        if (mount != null && mount.isObservedBy(player)) {
            mount.stopObserving();
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
