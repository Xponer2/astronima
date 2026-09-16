package play.xponer.astronima.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import play.xponer.astronima.block.entity.AirlockControllerBlockEntity;

/**
 * Client → server: the player pressed Cycle on an airlock panel.
 *
 * <p>Its own packet rather than a writable data slot, for the reason
 * {@link MachineSettingPayload} gives: this is an <em>instruction</em>, and an instruction
 * that starts a chamber depressurising has to be checked rather than trusted. The position
 * arrives from the client, so it is reach-checked and type-checked before anything happens
 * — a menu being open is not a licence to cycle airlocks across the map.
 *
 * <p>It carries no phase and no target: the cycle machine decides what pressing the control
 * means from where it currently is, so there is nothing here for a hostile client to lie
 * about beyond the position.
 */
public record AirlockCommandPayload(BlockPos pos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AirlockCommandPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("astronima", "airlock_command"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AirlockCommandPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeBlockPos(payload.pos),
                    buffer -> new AirlockCommandPayload(buffer.readBlockPos()));

    public static void apply(AirlockCommandPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.blockPosition().closerThan(payload.pos(), 8.0)) {
            return;
        }
        if (player.level().getBlockEntity(payload.pos())
                instanceof AirlockControllerBlockEntity controller) {
            controller.command();
        }
    }

    @Override
    public CustomPacketPayload.Type<AirlockCommandPayload> type() {
        return TYPE;
    }
}
