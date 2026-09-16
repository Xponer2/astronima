package play.xponer.astronima.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import play.xponer.astronima.sim.Gas;

/**
 * Server → client snapshot of the atmosphere the player is breathing, sent on the
 * atmosphere cadence while a gas analyzer is held. Everything the instrument panel
 * draws comes from here; the client never simulates.
 *
 * <p>Partial pressures are carried for every species so the panel can draw a real
 * composition strip rather than a couple of headline numbers.
 */
public record AtmosphereStatusPayload(byte environment, float[] partialPressuresKPa,
                                      float pressureKPa, float temperatureK, int volumeBlocks,
                                      float relativeHumidity, float noiseDb, byte ignition,
                                      float tankFraction, boolean onTank,
                                      float heatLossWatts, float heatSupplyWatts,
                                      float skyFraction, float insulatedFraction)
        implements CustomPacketPayload {
    public static final byte ENV_VACUUM = 0;
    public static final byte ENV_OPEN_AIR = 1;
    public static final byte ENV_SEALED = 2;
    public static final byte ENV_UNSEALABLE = 3;

    public static final CustomPacketPayload.Type<AtmosphereStatusPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("astronima", "atmosphere_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AtmosphereStatusPayload> STREAM_CODEC =
            StreamCodec.of(AtmosphereStatusPayload::write, AtmosphereStatusPayload::read);

    /** Convenience for the client: partial pressure of one species. */
    public float partialPressure(Gas gas) {
        return partialPressuresKPa[gas.ordinal()];
    }

    public boolean hasAtmosphere() {
        return environment == ENV_SEALED || environment == ENV_UNSEALABLE;
    }

    private static void write(RegistryFriendlyByteBuf buf, AtmosphereStatusPayload payload) {
        buf.writeByte(payload.environment);
        for (float partial : payload.partialPressuresKPa) {
            buf.writeFloat(partial);
        }
        buf.writeFloat(payload.pressureKPa);
        buf.writeFloat(payload.temperatureK);
        buf.writeVarInt(payload.volumeBlocks);
        buf.writeFloat(payload.relativeHumidity);
        buf.writeFloat(payload.noiseDb);
        buf.writeByte(payload.ignition);
        buf.writeFloat(payload.tankFraction);
        buf.writeBoolean(payload.onTank);
        buf.writeFloat(payload.heatLossWatts);
        buf.writeFloat(payload.heatSupplyWatts);
        buf.writeFloat(payload.skyFraction);
        buf.writeFloat(payload.insulatedFraction);
    }

    private static AtmosphereStatusPayload read(RegistryFriendlyByteBuf buf) {
        byte environment = buf.readByte();
        float[] partials = new float[Gas.values().length];
        for (int i = 0; i < partials.length; i++) {
            partials[i] = buf.readFloat();
        }
        return new AtmosphereStatusPayload(environment, partials,
                buf.readFloat(), buf.readFloat(), buf.readVarInt(),
                buf.readFloat(), buf.readFloat(), buf.readByte(),
                buf.readFloat(), buf.readBoolean(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    /** An empty snapshot for the environments that have nothing to report. */
    public static AtmosphereStatusPayload environmentOnly(byte environment, float tankFraction, boolean onTank) {
        return new AtmosphereStatusPayload(environment, new float[Gas.values().length],
                0, 0, 0, 0, 0, (byte) 0, tankFraction, onTank, 0, 0, 0, 0);
    }

    @Override
    public CustomPacketPayload.Type<AtmosphereStatusPayload> type() {
        return TYPE;
    }
}
