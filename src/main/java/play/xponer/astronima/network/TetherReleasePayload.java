package play.xponer.astronima.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.gravity.TetherState;

/**
 * Client to server: let go of the active tether — design/eva-mobility.md §3.2.
 *
 * <p>Carries nothing at all, deliberately — the server already knows whether this player has an
 * active tether, and a packet that named one would be a client asserting a fact the server itself
 * owns. Matches {@code PartRotatePayload}'s own shape.
 */
public record TetherReleasePayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TetherReleasePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("astronima", "tether_release"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TetherReleasePayload> STREAM_CODEC =
            StreamCodec.unit(new TetherReleasePayload());

    public static void apply(TetherReleasePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        player.setData(ModAttachments.TETHER.get(), TetherState.NONE);
    }

    @Override
    public CustomPacketPayload.Type<TetherReleasePayload> type() {
        return TYPE;
    }
}
