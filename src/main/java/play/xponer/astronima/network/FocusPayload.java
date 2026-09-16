package play.xponer.astronima.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import play.xponer.astronima.block.entity.MicroscopeBlockEntity;

/**
 * "I have it in focus — write this down."
 *
 * <p>The knob position crosses rather than the reading, and that is the whole point: the server
 * decides whether the field was sharp enough to be sure. A client that could send the observation
 * itself could send one it never actually resolved.
 */
public record FocusPayload(BlockPos pos, float knob) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FocusPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("astronima", "focus"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FocusPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBlockPos(payload.pos);
                        buffer.writeFloat(payload.knob);
                    },
                    buffer -> new FocusPayload(buffer.readBlockPos(), buffer.readFloat()));

    public static void apply(FocusPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        // Reach check: a menu being open is not a licence to work machines across the map.
        if (!player.blockPosition().closerThan(payload.pos(), 8.0)) {
            return;
        }
        if (player.level().getBlockEntity(payload.pos())
                instanceof MicroscopeBlockEntity microscope) {
            microscope.record(payload.knob());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
