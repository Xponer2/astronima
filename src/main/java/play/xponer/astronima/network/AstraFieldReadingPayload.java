package play.xponer.astronima.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client snapshot of the astra field's baseline density at a player's own position, sent
 * on the atmosphere cadence to every player in the asteroid dimension — design/astra-field-live.md
 * §2.2's own answer to the field-query-budget question: one point (the player), not many, so the
 * client never re-derives {@code AstraFieldGeometry} itself. Everything the ambient glow
 * (design/astra-field-live.md §2) draws comes from here, the same "compute server-side, publish,
 * read on the client" shape rule 26 already requires of {@code CoherenceStatusPayload}.
 */
public record AstraFieldReadingPayload(float density) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AstraFieldReadingPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("astronima", "astra_field_reading"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AstraFieldReadingPayload> STREAM_CODEC =
            StreamCodec.of(AstraFieldReadingPayload::write, AstraFieldReadingPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, AstraFieldReadingPayload payload) {
        buf.writeFloat(payload.density);
    }

    private static AstraFieldReadingPayload read(RegistryFriendlyByteBuf buf) {
        return new AstraFieldReadingPayload(buf.readFloat());
    }

    @Override
    public CustomPacketPayload.Type<AstraFieldReadingPayload> type() {
        return TYPE;
    }
}
