package play.xponer.astronima.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import play.xponer.astronima.block.entity.StorageTerminalBlockEntity;

/**
 * Client → server: the player scrolled the terminal's own list — mouse wheel over the grid, or
 * the scrollbar overlay dragged/clicked (both in {@code StorageTerminalUi}). Carries an absolute
 * target row rather than a relative delta so a drag that jumps several rows in one update (fast
 * mouse movement) lands exactly where the thumb was dropped, not accumulated one step at a time.
 * The server re-clamps regardless — see {@link StorageTerminalBlockEntity#setScrollRow}.
 */
public record TerminalScrollPayload(BlockPos pos, int row) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TerminalScrollPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("astronima", "terminal_scroll"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalScrollPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBlockPos(payload.pos);
                        buffer.writeVarInt(payload.row);
                    },
                    buffer -> new TerminalScrollPayload(buffer.readBlockPos(), buffer.readVarInt()));

    public static void apply(TerminalScrollPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.blockPosition().closerThan(payload.pos(), 8.0)) {
            return;
        }
        if (player.level().getBlockEntity(payload.pos())
                instanceof StorageTerminalBlockEntity terminal) {
            terminal.setScrollRow(payload.row());
        }
    }

    @Override
    public CustomPacketPayload.Type<TerminalScrollPayload> type() {
        return TYPE;
    }
}
