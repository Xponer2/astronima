package play.xponer.astronima.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import play.xponer.astronima.block.entity.SlsPrinterBlockEntity;

/**
 * Client → server: the player moved the printer's marker in the process plane.
 *
 * <p>A sibling of {@link MachineSettingPayload}, not a second field bolted onto it — the SLS
 * printer is the first machine in the mod whose control has two independent axes
 * ({@code design/sls.md} §2), and a single click or drag sets both power and speed together,
 * the way dragging a point on a real plane always sets both its coordinates in one gesture.
 * Splitting it into two {@code MachineSettingPayload}s would mean two packets for one motion of
 * the mouse and a window where the machine believes a power that pairs with a stale speed.
 *
 * <p>Both fractions 0..1, the same "a fraction rather than a physical unit crosses the wire"
 * reasoning {@link MachineSettingPayload} already uses — the machine owns the real ranges.
 */
public record SlsControlPayload(BlockPos pos, float power01, float speed01)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SlsControlPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("astronima", "sls_control"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SlsControlPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBlockPos(payload.pos);
                        buffer.writeFloat(payload.power01);
                        buffer.writeFloat(payload.speed01);
                    },
                    buffer -> new SlsControlPayload(buffer.readBlockPos(), buffer.readFloat(),
                            buffer.readFloat()));

    public static void apply(SlsControlPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.blockPosition().closerThan(payload.pos(), 8.0)) {
            return;
        }
        if (player.level().getBlockEntity(payload.pos())
                instanceof SlsPrinterBlockEntity printer) {
            printer.setFromDials(payload.power01(), payload.speed01());
        }
    }

    @Override
    public CustomPacketPayload.Type<SlsControlPayload> type() {
        return TYPE;
    }
}
