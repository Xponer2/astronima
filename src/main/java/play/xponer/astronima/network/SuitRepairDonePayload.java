package play.xponer.astronima.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: a repair procedure was completed.
 *
 * <p>The client runs the timing minigame, but it does not decide anything that
 * matters: the server re-checks that the player is actually holding the right part
 * and a suit that actually needs that repair before applying it. A forged packet can
 * therefore skip the minigame, but never conjure a repair out of nothing.
 */
public record SuitRepairDonePayload(int subsystemOrdinal, float quality) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SuitRepairDonePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("astronima", "suit_repair_done"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SuitRepairDonePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeVarInt(payload.subsystemOrdinal);
                        buf.writeFloat(payload.quality);
                    },
                    buf -> new SuitRepairDonePayload(buf.readVarInt(), buf.readFloat()));

    @Override
    public CustomPacketPayload.Type<SuitRepairDonePayload> type() {
        return TYPE;
    }
}
