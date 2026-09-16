package play.xponer.astronima.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import play.xponer.astronima.sim.magic.Coherence;

/**
 * Server → client snapshot of the six-term coherence breakdown at the coherence meter, sent on
 * the atmosphere cadence while one is held. Everything the survey meter's HUD draws comes from
 * here; the client never re-derives the model.
 */
public record CoherenceStatusPayload(float[] secondsByTerm) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CoherenceStatusPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("astronima", "coherence_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CoherenceStatusPayload> STREAM_CODEC =
            StreamCodec.of(CoherenceStatusPayload::write, CoherenceStatusPayload::read);

    public static CoherenceStatusPayload of(java.util.Map<Coherence.Term, Double> breakdown) {
        float[] seconds = new float[Coherence.Term.values().length];
        for (Coherence.Term term : Coherence.Term.values()) {
            seconds[term.ordinal()] = (float) (double) breakdown.get(term);
        }
        return new CoherenceStatusPayload(seconds);
    }

    public float seconds(Coherence.Term term) {
        return secondsByTerm[term.ordinal()];
    }

    private static void write(RegistryFriendlyByteBuf buf, CoherenceStatusPayload payload) {
        for (float seconds : payload.secondsByTerm) {
            buf.writeFloat(seconds);
        }
    }

    private static CoherenceStatusPayload read(RegistryFriendlyByteBuf buf) {
        float[] seconds = new float[Coherence.Term.values().length];
        for (int i = 0; i < seconds.length; i++) {
            seconds[i] = buf.readFloat();
        }
        return new CoherenceStatusPayload(seconds);
    }

    @Override
    public CustomPacketPayload.Type<CoherenceStatusPayload> type() {
        return TYPE;
    }
}
