package play.xponer.astronima.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import play.xponer.astronima.block.entity.StorageTerminalBlockEntity;
import play.xponer.astronima.sim.storage.TerminalView;

/**
 * Client → server: the player pressed one of the terminal's three sort buttons.
 *
 * <p>A packet rather than a client-only re-render, because the sort order is what
 * {@link StorageTerminalBlockEntity#refreshView()} bakes into the real display slots every tick —
 * the server is the one place the live {@code TerminalView} exists at all.
 */
public record TerminalSortPayload(BlockPos pos, int sortOrdinal) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TerminalSortPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("astronima", "terminal_sort"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalSortPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBlockPos(payload.pos);
                        buffer.writeVarInt(payload.sortOrdinal);
                    },
                    buffer -> new TerminalSortPayload(buffer.readBlockPos(), buffer.readVarInt()));

    public static void apply(TerminalSortPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.blockPosition().closerThan(payload.pos(), 8.0)) {
            return;
        }
        TerminalView.Sort[] sorts = TerminalView.Sort.values();
        if (payload.sortOrdinal() < 0 || payload.sortOrdinal() >= sorts.length) {
            return;
        }
        if (player.level().getBlockEntity(payload.pos())
                instanceof StorageTerminalBlockEntity terminal) {
            terminal.setSort(sorts[payload.sortOrdinal()]);
        }
    }

    @Override
    public CustomPacketPayload.Type<TerminalSortPayload> type() {
        return TYPE;
    }
}
